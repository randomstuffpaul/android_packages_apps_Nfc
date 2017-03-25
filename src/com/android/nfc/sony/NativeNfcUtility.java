package com.android.nfc.sony;

import android.nfc.INfcUtilityCallback;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;

public class NativeNfcUtility {
    private static final boolean DBG = false;
    private static final int MSG_WAIT_SIM_BOOT = 1;
    private static final String TAG = "NativeNfcUtility";
    private static boolean mIsLock;
    private static INfcUtilityCallback mStaticCallback;
    final NativeNfcUtilityHandler mHandler;

    final class NativeNfcUtilityHandler extends Handler {
        NativeNfcUtilityHandler() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NativeNfcUtility.MSG_WAIT_SIM_BOOT /*1*/:
                    synchronized (this) {
                        NativeNfcUtility.this.startWaitSimBoot();
                        break;
                    }
                default:
            }
        }
    }

    private native int nativeWaitSimBoot(boolean z);

    static {
        mIsLock = DBG;
    }

    public NativeNfcUtility() {
        this.mHandler = new NativeNfcUtilityHandler();
    }

    public boolean waitSimBoot(INfcUtilityCallback callback, boolean isLock) {
        mIsLock = isLock;
        mStaticCallback = callback;
        this.mHandler.sendEmptyMessage(MSG_WAIT_SIM_BOOT);
        return true;
    }

    void startWaitSimBoot() {
        nativeWaitSimBoot(mIsLock);
        if (mStaticCallback != null) {
            try {
                mStaticCallback.SimBootComplete();
            } catch (RemoteException e) {
            }
        }
    }
}
