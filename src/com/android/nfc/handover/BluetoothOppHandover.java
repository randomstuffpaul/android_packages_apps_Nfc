package com.android.nfc.handover;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.util.ArrayList;
import java.util.Arrays;

public class BluetoothOppHandover implements Callback {
    static final String ACTION_HANDOVER_SEND = "android.btopp.intent.action.HANDOVER_SEND";
    static final String ACTION_HANDOVER_SEND_MULTIPLE = "android.btopp.intent.action.HANDOVER_SEND_MULTIPLE";
    static final boolean DBG;
    static final int MSG_START_SEND = 0;
    static final int REMOTE_BT_ENABLE_DELAY_MS = 5000;
    static final int STATE_COMPLETE = 3;
    static final int STATE_INIT = 0;
    static final int STATE_TURNING_ON = 1;
    static final int STATE_WAITING = 2;
    static final String TAG = "BluetoothOppHandover";
    final Context mContext;
    final Long mCreateTime;
    final BluetoothDevice mDevice;
    final Handler mHandler;
    final boolean mRemoteActivating;
    int mState;
    final Uri[] mUris;

    static {
        DBG = HandoverManager.DBG;
    }

    public BluetoothOppHandover(Context context, BluetoothDevice device, Uri[] uris, boolean remoteActivating) {
        this.mContext = context;
        this.mDevice = device;
        this.mUris = uris;
        this.mRemoteActivating = remoteActivating;
        this.mCreateTime = Long.valueOf(SystemClock.elapsedRealtime());
        this.mHandler = new Handler(context.getMainLooper(), this);
        this.mState = STATE_INIT;
    }

    public static String getMimeTypeForUri(Context context, Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            Log.d(TAG, "getMimeTypeForUri :: uri == null or uri.getScheme() == null");
            return null;
        } else if (uri.getScheme().equals("content")) {
            return context.getContentResolver().getType(uri);
        } else {
            if (uri.getScheme().equals("file")) {
                String path = uri.getPath();
                String extension = path.substring(path.lastIndexOf(".") + STATE_TURNING_ON);
                if (DBG) {
                    Log.d(TAG, "uri.getPath() " + uri.getPath());
                    Log.d(TAG, "extension  " + extension);
                }
                if (extension == null) {
                    return null;
                }
                if (extension.equalsIgnoreCase("aac")) {
                    return "audio/aac";
                }
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            Log.d(TAG, "Could not determine mime type for Uri " + uri);
            return null;
        }
    }

    public void start() {
        if (DBG) {
            Log.d(TAG, "start");
        }
        if (this.mRemoteActivating) {
            Long timeElapsed = Long.valueOf(SystemClock.elapsedRealtime() - this.mCreateTime.longValue());
            if (timeElapsed.longValue() < 5000) {
                this.mHandler.sendEmptyMessageDelayed(STATE_INIT, 5000 - timeElapsed.longValue());
                return;
            } else {
                sendIntent();
                return;
            }
        }
        sendIntent();
    }

    void complete() {
        if (DBG) {
            Log.d(TAG, "complete");
        }
        this.mState = STATE_COMPLETE;
        if (DBG) {
            Log.d(TAG, "state : " + this.mState);
        }
    }

    void sendIntent() {
        if (DBG) {
            Log.d(TAG, "sendIntent");
        }
        Intent intent = new Intent();
        intent.setPackage("com.android.bluetooth");
        String mimeType = getMimeTypeForUri(this.mContext, this.mUris[STATE_INIT]);
        if (DBG) {
            Log.d(TAG, "mimeType: " + mimeType);
        }
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        intent.setType(mimeType);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
        if (this.mUris.length == STATE_TURNING_ON) {
            intent.setAction(ACTION_HANDOVER_SEND);
            intent.putExtra("android.intent.extra.STREAM", this.mUris[STATE_INIT]);
        } else {
            ArrayList<Uri> uris = new ArrayList(Arrays.asList(this.mUris));
            intent.setAction(ACTION_HANDOVER_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra("android.intent.extra.STREAM", uris);
        }
        if (DBG) {
            Log.d(TAG, "Handing off outging transfer to BT");
        }
        this.mContext.sendBroadcast(intent);
        complete();
    }

    public boolean handleMessage(Message msg) {
        if (msg.what != 0) {
            return DBG;
        }
        sendIntent();
        return true;
    }
}
