package com.android.nfc.dhimpl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class NativeNfcSecureElement {
    static final String PREF_SE_WIRED = "se_wired";
    private final Context mContext;
    SharedPreferences mPrefs;
    Editor mPrefsEditor;

    private native boolean doNativeDisconnectSecureElementConnection(int i);

    private native int doNativeOpenSecureElementConnection();

    public native int[] doGetTechList(int i);

    public native byte[] doGetUid(int i);

    public native byte[] doTransceive(int i, byte[] bArr);

    public NativeNfcSecureElement(Context context) {
        this.mContext = context;
        this.mPrefs = this.mContext.getSharedPreferences("NxpDeviceHost", 0);
        this.mPrefsEditor = this.mPrefs.edit();
    }

    public int doOpenSecureElementConnection() {
        this.mPrefsEditor.putBoolean(PREF_SE_WIRED, true);
        this.mPrefsEditor.apply();
        return doNativeOpenSecureElementConnection();
    }

    public boolean doDisconnect(int handle) {
        this.mPrefsEditor.putBoolean(PREF_SE_WIRED, false);
        this.mPrefsEditor.apply();
        return doNativeDisconnectSecureElementConnection(handle);
    }
}
