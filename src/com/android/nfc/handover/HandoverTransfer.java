package com.android.nfc.handover;

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import com.android.nfc.C0027R;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public class HandoverTransfer implements OnScanCompletedListener, android.os.Handler.Callback {
    static final int ALIVE_CHECK_MS = 20000;
    static final String BEAM_DIR;
    static final Boolean DBG;
    static final int MSG_NEXT_TRANSFER_TIMER = 0;
    static final int MSG_TRANSFER_TIMEOUT = 1;
    static final int STATE_CANCELLED = 6;
    static final int STATE_FAILED = 4;
    static final int STATE_IN_PROGRESS = 1;
    static final int STATE_NEW = 0;
    static final int STATE_SUCCESS = 5;
    static final int STATE_W4_MEDIA_SCANNER = 3;
    static final int STATE_W4_NEXT_TRANSFER = 2;
    static final String TAG = "HandoverTransfer";
    static final int WAIT_FOR_NEXT_TRANSFER_MS = 4000;
    ArrayList<String> mBtMimeTypes;
    ArrayList<Uri> mBtUris;
    final Callback mCallback;
    boolean mCalledBack;
    final PendingIntent mCancelIntent;
    final Context mContext;
    int mCurrentCount;
    final Handler mHandler;
    final boolean mIncoming;
    Long mLastUpdate;
    HashMap<String, Uri> mMediaUris;
    HashMap<String, String> mMimeTypes;
    final NotificationManager mNotificationManager;
    ArrayList<String> mPaths;
    float mProgress;
    final BluetoothDevice mRemoteDevice;
    int mState;
    int mSuccessCount;
    int mTotalCount;
    final int mTransferId;
    int mUrisScanned;
    long pretime;

    interface Callback {
        void onTransferComplete(HandoverTransfer handoverTransfer, boolean z);
    }

    static {
        DBG = Boolean.valueOf(HandoverManager.DBG);
        BEAM_DIR = Environment.DIRECTORY_DOWNLOADS;
    }

    public HandoverTransfer(Context context, Callback callback, PendingHandoverTransfer pendingTransfer) {
        this.pretime = System.currentTimeMillis();
        this.mContext = context;
        this.mCallback = callback;
        this.mRemoteDevice = pendingTransfer.remoteDevice;
        this.mIncoming = pendingTransfer.incoming;
        this.mTransferId = pendingTransfer.id;
        this.mTotalCount = pendingTransfer.uris != null ? pendingTransfer.uris.length : STATE_NEW;
        this.mLastUpdate = Long.valueOf(SystemClock.elapsedRealtime());
        this.mProgress = 0.0f;
        this.mState = STATE_NEW;
        this.mBtUris = new ArrayList();
        this.mBtMimeTypes = new ArrayList();
        this.mPaths = new ArrayList();
        this.mMimeTypes = new HashMap();
        this.mMediaUris = new HashMap();
        this.mCancelIntent = buildCancelIntent(this.mIncoming);
        this.mUrisScanned = STATE_NEW;
        this.mCurrentCount = STATE_NEW;
        this.mSuccessCount = STATE_NEW;
        this.mHandler = new Handler(Looper.getMainLooper(), this);
        this.mHandler.sendEmptyMessageDelayed(STATE_IN_PROGRESS, 20000);
        this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
    }

    void whitelistOppDevice(BluetoothDevice device) {
        if (DBG.booleanValue()) {
            Log.d(TAG, "Whitelisting " + device + " for BT OPP");
        }
        Intent intent = new Intent("android.btopp.intent.action.WHITELIST_DEVICE");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    public void updateFileProgress(float progress) {
        if (isRunning()) {
            this.mHandler.removeMessages(STATE_NEW);
            this.mProgress = progress;
            if (this.mIncoming) {
                whitelistOppDevice(this.mRemoteDevice);
            }
            updateStateAndNotification(STATE_IN_PROGRESS);
        }
    }

    public void finishTransfer(boolean success, Uri uri, String mimeType) {
        if (isRunning()) {
            this.mCurrentCount += STATE_IN_PROGRESS;
            if (!success || uri == null) {
                Log.e(TAG, "Handover transfer failed");
            } else {
                this.mSuccessCount += STATE_IN_PROGRESS;
                if (DBG.booleanValue()) {
                    Log.d(TAG, "Transfer success, uri " + uri + " mimeType " + mimeType);
                }
                this.mProgress = 0.0f;
                if (mimeType == null) {
                    mimeType = BluetoothOppHandover.getMimeTypeForUri(this.mContext, uri);
                }
                if (mimeType != null) {
                    this.mBtUris.add(uri);
                    this.mBtMimeTypes.add(mimeType);
                } else if (DBG.booleanValue()) {
                    Log.d(TAG, "Could not get mimeType for file.");
                }
            }
            this.mHandler.removeMessages(STATE_NEW);
            if (this.mCurrentCount != this.mTotalCount) {
                this.mHandler.sendEmptyMessageDelayed(STATE_NEW, 4000);
                updateStateAndNotification(STATE_W4_NEXT_TRANSFER);
            } else if (this.mIncoming) {
                processFiles();
            } else {
                updateStateAndNotification(this.mSuccessCount > 0 ? STATE_SUCCESS : STATE_FAILED);
            }
        }
    }

    public boolean isRunning() {
        if (this.mState == 0 || this.mState == STATE_IN_PROGRESS || this.mState == STATE_W4_NEXT_TRANSFER) {
            return true;
        }
        return false;
    }

    public void setObjectCount(int objectCount) {
        this.mTotalCount = objectCount;
    }

    void cancel() {
        if (isRunning()) {
            Iterator i$ = this.mBtUris.iterator();
            while (i$.hasNext()) {
                File file = new File(((Uri) i$.next()).getPath());
                if (file.exists()) {
                    file.delete();
                }
            }
            updateStateAndNotification(STATE_CANCELLED);
        }
    }

    void updateNotification() {
        String beamString;
        int i = 17301634;
        Builder notBuilder = new Builder(this.mContext);
        notBuilder.setWhen(this.pretime);
        if (this.mIncoming) {
            beamString = this.mContext.getString(C0027R.string.beam_progress);
        } else {
            beamString = this.mContext.getString(C0027R.string.beam_outgoing);
        }
        if (this.mState == 0 || this.mState == STATE_IN_PROGRESS || this.mState == STATE_W4_NEXT_TRANSFER || this.mState == STATE_W4_MEDIA_SCANNER) {
            notBuilder.setAutoCancel(false);
            notBuilder.setSmallIcon(this.mIncoming ? 17301633 : 17301640);
            notBuilder.setTicker(beamString);
            notBuilder.setContentTitle(beamString);
            notBuilder.addAction(C0027R.drawable.ic_menu_cancel_holo_dark, this.mContext.getString(C0027R.string.cancel), this.mCancelIntent);
            float progress = 0.0f;
            if (this.mTotalCount > 0) {
                float progressUnit = 1.0f / ((float) this.mTotalCount);
                progress = (((float) this.mCurrentCount) * progressUnit) + (this.mProgress * progressUnit);
            }
            if (this.mTotalCount <= 0 || progress <= 0.0f) {
                notBuilder.setProgress(100, STATE_NEW, true);
            } else {
                notBuilder.setProgress(100, (int) (100.0f * progress), false);
            }
        } else if (this.mState == STATE_SUCCESS) {
            notBuilder.setAutoCancel(true);
            if (!this.mIncoming) {
                i = 17301641;
            }
            notBuilder.setSmallIcon(i);
            notBuilder.setTicker(this.mContext.getString(C0027R.string.beam_complete));
            notBuilder.setContentTitle(this.mContext.getString(C0027R.string.beam_complete));
            if (this.mIncoming) {
                notBuilder.setContentText(this.mContext.getString(C0027R.string.beam_touch_to_view));
                notBuilder.setContentIntent(PendingIntent.getActivity(this.mContext, this.mTransferId, buildViewIntent(), STATE_NEW, null));
            }
        } else if (this.mState == STATE_FAILED) {
            notBuilder.setAutoCancel(false);
            if (!this.mIncoming) {
                i = 17301641;
            }
            notBuilder.setSmallIcon(i);
            notBuilder.setTicker(this.mContext.getString(C0027R.string.beam_failed));
            notBuilder.setContentTitle(this.mContext.getString(C0027R.string.beam_failed));
        } else if (this.mState == STATE_CANCELLED) {
            notBuilder.setAutoCancel(false);
            if (!this.mIncoming) {
                i = 17301641;
            }
            notBuilder.setSmallIcon(i);
            notBuilder.setTicker(this.mContext.getString(C0027R.string.beam_canceled));
            notBuilder.setContentTitle(this.mContext.getString(C0027R.string.beam_canceled));
        } else {
            return;
        }
        this.mNotificationManager.notify(null, this.mTransferId, notBuilder.build());
    }

    void updateStateAndNotification(int newState) {
        boolean z = true;
        this.mState = newState;
        this.mLastUpdate = Long.valueOf(SystemClock.elapsedRealtime());
        this.mHandler.removeMessages(STATE_IN_PROGRESS);
        if (isRunning()) {
            this.mHandler.sendEmptyMessageDelayed(STATE_IN_PROGRESS, 20000);
        }
        updateNotification();
        if ((this.mState == STATE_SUCCESS || this.mState == STATE_FAILED || this.mState == STATE_CANCELLED) && !this.mCalledBack) {
            this.mCalledBack = true;
            Callback callback = this.mCallback;
            if (this.mState != STATE_SUCCESS) {
                z = false;
            }
            callback.onTransferComplete(this, z);
        }
    }

    void processFiles() {
        File beamPath = new File(Environment.getExternalStorageDirectory().getPath() + "/" + BEAM_DIR);
        if (!checkMediaStorage(beamPath) || this.mBtUris.size() == 0) {
            Log.e(TAG, "Media storage not valid or no uris received.");
            updateStateAndNotification(STATE_FAILED);
            return;
        }
        String mimeType;
        int i = STATE_NEW;
        while (i < this.mBtUris.size()) {
            Uri uri = (Uri) this.mBtUris.get(i);
            mimeType = (String) this.mBtMimeTypes.get(i);
            File srcFile = new File(uri.getPath());
            File dstFile = generateUniqueDestination(beamPath.getAbsolutePath(), uri.getLastPathSegment());
            if (srcFile.renameTo(dstFile)) {
                this.mPaths.add(dstFile.getAbsolutePath());
                this.mMimeTypes.put(dstFile.getAbsolutePath(), mimeType);
                if (DBG.booleanValue()) {
                    Log.d(TAG, "Did successful rename from " + srcFile + " to " + dstFile);
                }
                i += STATE_IN_PROGRESS;
            } else {
                if (DBG.booleanValue()) {
                    Log.d(TAG, "Failed to rename from " + srcFile + " to " + dstFile);
                }
                srcFile.delete();
                return;
            }
        }
        mimeType = (String) this.mMimeTypes.get(this.mPaths.get(STATE_NEW));
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            MediaScannerConnection.scanFile(this.mContext, (String[]) this.mPaths.toArray(new String[this.mPaths.size()]), null, this);
            updateStateAndNotification(STATE_W4_MEDIA_SCANNER);
            return;
        }
        updateStateAndNotification(STATE_SUCCESS);
    }

    public int getTransferId() {
        return this.mTransferId;
    }

    public boolean handleMessage(Message msg) {
        int i = STATE_FAILED;
        if (msg.what == 0) {
            if (this.mIncoming) {
                processFiles();
            } else {
                if (this.mSuccessCount > 0) {
                    i = STATE_SUCCESS;
                }
                updateStateAndNotification(i);
            }
            return true;
        }
        if (msg.what == STATE_IN_PROGRESS) {
            if (DBG.booleanValue()) {
                Log.d(TAG, "Transfer timed out for id: " + Integer.toString(this.mTransferId));
            }
            updateStateAndNotification(STATE_FAILED);
        }
        return false;
    }

    public synchronized void onScanCompleted(String path, Uri uri) {
        if (DBG.booleanValue()) {
            Log.d(TAG, "Scan completed, path " + path + " uri " + uri);
        }
        if (uri != null) {
            this.mMediaUris.put(path, uri);
        }
        this.mUrisScanned += STATE_IN_PROGRESS;
        if (this.mUrisScanned == this.mPaths.size()) {
            updateStateAndNotification(STATE_SUCCESS);
        }
    }

    boolean checkMediaStorage(File path) {
        if (!Environment.getExternalStorageState().equals("mounted")) {
            Log.e(TAG, "External storage not mounted, can't store file.");
            return false;
        } else if (path.isDirectory() || path.mkdir()) {
            return true;
        } else {
            Log.e(TAG, "Not dir or not mkdir " + path.getAbsolutePath());
            return false;
        }
    }

    Intent buildViewIntent() {
        if (this.mPaths.size() == 0) {
            return null;
        }
        Intent viewIntent = new Intent("android.intent.action.VIEW");
        String filePath = (String) this.mPaths.get(STATE_NEW);
        Uri mediaUri = (Uri) this.mMediaUris.get(filePath);
        viewIntent.setDataAndTypeAndNormalize(mediaUri != null ? mediaUri : Uri.parse("file://" + filePath), (String) this.mMimeTypes.get(filePath));
        viewIntent.setFlags(268435456);
        return viewIntent;
    }

    PendingIntent buildCancelIntent(boolean incoming) {
        Intent intent = new Intent("com.android.nfc.handover.action.CANCEL_HANDOVER_TRANSFER");
        intent.putExtra("com.android.nfc.handover.extra.SOURCE_ADDRESS", this.mRemoteDevice.getAddress());
        intent.putExtra("com.android.nfc.handover.extra.INCOMING", incoming ? STATE_IN_PROGRESS : STATE_NEW);
        return PendingIntent.getBroadcast(this.mContext, this.mTransferId, intent, 1073741824);
    }

    File generateUniqueDestination(String path, String fileName) {
        String extension;
        String fileNameWithoutExtension;
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex < 0) {
            extension = "";
            fileNameWithoutExtension = fileName;
        } else {
            extension = fileName.substring(dotIndex);
            fileNameWithoutExtension = fileName.substring(STATE_NEW, dotIndex);
        }
        File dstFile = new File(path + File.separator + fileName);
        int count = STATE_NEW;
        while (dstFile.exists()) {
            dstFile = new File(path + File.separator + fileNameWithoutExtension + "-" + Integer.toString(count) + extension);
            count += STATE_IN_PROGRESS;
        }
        return dstFile;
    }

    File generateMultiplePath(String beamRoot) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        File newFile = new File(beamRoot + "beam-" + sdf.format(new Date()));
        int count = STATE_NEW;
        while (newFile.exists()) {
            newFile = new File(beamRoot + "beam-" + sdf.format(new Date()) + "-" + Integer.toString(count));
            count += STATE_IN_PROGRESS;
        }
        return newFile;
    }
}
