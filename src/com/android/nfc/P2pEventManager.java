package com.android.nfc;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.Vibrator;
import android.util.Log;
import com.android.nfc.P2pEventListener.Callback;

public class P2pEventManager implements P2pEventListener, Callback {
    static final boolean DBG;
    static final String TAG = "NfcP2pEventManager";
    static final long[] VIBRATION_PATTERN;
    final String ACTION_P2P_CONNECT;
    final String ACTION_P2P_DISCONNECT;
    final String ACTION_P2P_SEND_COMPLETE;
    final Callback mCallback;
    final Context mContext;
    boolean mInDebounce;
    boolean mNdefReceived;
    boolean mNdefSent;
    final NfcService mNfcService;
    final NotificationManager mNotificationManager;
    public String[] mPacakgeList;
    final SendUi mSendUi;
    boolean mSending;
    final Vibrator mVibrator;

    static {
        DBG = NfcService.DBG;
        VIBRATION_PATTERN = new long[]{0, 100, 10000};
    }

    public P2pEventManager(Context context, Callback callback) {
        this.ACTION_P2P_CONNECT = "android.nfc.action.P2P_CONNECT";
        this.ACTION_P2P_DISCONNECT = "android.nfc.action.P2P_DISCONNECT";
        this.ACTION_P2P_SEND_COMPLETE = "android.nfc.action.P2P_SEND_COMPLETE";
        this.mPacakgeList = new String[]{"com.sec.android.directshare", "com.sec.android.directconnect"};
        this.mNfcService = NfcService.getInstance();
        this.mContext = context;
        this.mCallback = callback;
        this.mVibrator = (Vibrator) context.getSystemService("vibrator");
        this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
        this.mSending = DBG;
        if ((this.mContext.getResources().getConfiguration().uiMode & 15) == 5) {
            this.mSendUi = null;
        } else {
            this.mSendUi = new SendUi(context, this);
        }
    }

    public void onP2pInRange() {
        this.mNfcService.playSound(0);
        this.mNdefSent = DBG;
        this.mNdefReceived = DBG;
        this.mInDebounce = DBG;
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        if (this.mSendUi != null) {
            sendP2pEventBroadcast("android.nfc.action.P2P_CONNECT");
            this.mSendUi.takeScreenshot();
        }
    }

    public void onP2pSendConfirmationRequested() {
        if (this.mSendUi != null) {
            this.mSendUi.showPreSend();
        } else {
            this.mCallback.onP2pSendConfirmed();
        }
    }

    public void sendP2pEventBroadcast(String action) {
        for (int i = 0; i < this.mPacakgeList.length; i++) {
            Intent intent = new Intent(action);
            intent.setPackage(this.mPacakgeList[i]);
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            if (DBG) {
                Log.d(TAG, "send p2p " + action + " event to " + this.mPacakgeList[i]);
            }
        }
    }

    public void onP2pSendComplete() {
        this.mNfcService.playSound(1);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        if (this.mSendUi != null) {
            this.mSendUi.finish(1);
        }
        this.mSending = DBG;
        this.mNdefSent = true;
        sendP2pEventBroadcast("android.nfc.action.P2P_SEND_COMPLETE");
    }

    public void onP2pHandoverNotSupported() {
        this.mNfcService.playSound(2);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        this.mSendUi.finishAndToast(0, this.mContext.getString(C0027R.string.beam_handover_not_supported));
        this.mSending = DBG;
        this.mNdefSent = DBG;
    }

    public void onP2pReceiveComplete(boolean playSound) {
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        if (playSound) {
            this.mNfcService.playSound(1);
        }
        if (this.mSendUi != null) {
            this.mSendUi.finish(0);
        }
        this.mNdefReceived = true;
    }

    public void onP2pOutOfRange() {
        if (this.mSending) {
            this.mNfcService.playSound(2);
            this.mSending = DBG;
        }
        if (!(this.mNdefSent || this.mNdefReceived || this.mSendUi == null)) {
            this.mSendUi.finish(0);
        }
        this.mInDebounce = DBG;
        sendP2pEventBroadcast("android.nfc.action.P2P_DISCONNECT");
    }

    public void onSendConfirmed() {
        if (!this.mSending) {
            if (this.mSendUi != null) {
                this.mSendUi.showStartSend();
            }
            this.mCallback.onP2pSendConfirmed();
        }
        this.mSending = true;
    }

    public void onP2pSendDebounce() {
        this.mInDebounce = true;
        this.mNfcService.playSound(2);
        if (this.mSendUi != null) {
            this.mSendUi.showSendHint();
        }
    }

    public void onP2pResumeSend() {
        if (this.mInDebounce) {
            this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
            this.mNfcService.playSound(0);
            if (this.mSendUi != null) {
                this.mSendUi.showStartSend();
            }
        }
        this.mInDebounce = DBG;
    }
}
