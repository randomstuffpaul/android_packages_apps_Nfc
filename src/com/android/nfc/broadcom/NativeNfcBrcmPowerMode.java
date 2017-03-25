package com.android.nfc.broadcom;

import android.util.Log;

public class NativeNfcBrcmPowerMode {
    public static final int POWER_STATE_OFF = 1;
    public static final int POWER_STATE_SCREEN_OFF = 2;
    public static final int POWER_STATE_SCREEN_ON_LOCKED = 3;
    public static final int POWER_STATE_SCREEN_ON_UNLOCKED = 4;
    public static final int POWER_STATE_UNKNOWN = 0;
    private final String TAG;

    private native boolean doSetPowerMode(int i);

    public NativeNfcBrcmPowerMode() {
        this.TAG = "NativeNfcBrcmPowerMode";
    }

    public boolean setPowerMode(int powerState) {
        Log.d("NativeNfcBrcmPowerMode", "setPowerMode; state=" + powerState);
        return doSetPowerMode(powerState);
    }
}
