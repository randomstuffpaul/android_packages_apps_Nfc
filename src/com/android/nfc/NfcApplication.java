package com.android.nfc;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.os.Process;
import android.os.UserHandle;

public class NfcApplication extends Application {
    static final String NFC_PROCESS = "com.android.nfc";
    static final String TAG = "NfcApplication";
    NfcService mNfcService;

    public void onCreate() {
        super.onCreate();
        boolean isMainProcess = false;
        for (RunningAppProcessInfo appInfo : ((ActivityManager) getSystemService("activity")).getRunningAppProcesses()) {
            if (appInfo.pid == Process.myPid()) {
                isMainProcess = NFC_PROCESS.equals(appInfo.processName);
                break;
            }
        }
        if (UserHandle.myUserId() == 0 && isMainProcess) {
            this.mNfcService = new NfcService(this);
        }
    }
}
