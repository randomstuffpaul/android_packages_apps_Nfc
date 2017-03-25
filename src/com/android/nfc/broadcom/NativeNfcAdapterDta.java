package com.android.nfc.broadcom;

import android.os.Debug;
import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.NfcService;
import com.broadcom.nfc.INfcAdapterDta.Stub;

class NativeNfcAdapterDta extends Stub {
    static final boolean DBG;
    final String TAG;
    NfcService mService;

    native boolean doConfigDta(int i, int i2);

    native boolean doDisableDta();

    native boolean doEnableDta(boolean z);

    native boolean doStartDta(int i, byte[] bArr);

    native boolean doStopDta();

    static {
        boolean z = true;
        if (Debug.isProductShip() == 1) {
            z = false;
        }
        DBG = z;
    }

    public NativeNfcAdapterDta(NfcService s) {
        this.TAG = "BrcmDtaSrv";
        this.mService = s;
        Log.d("BrcmDtaSrv", "NativeNfcAdapterDta");
    }

    public synchronized boolean enable(boolean autoStart) throws RemoteException {
        Log.d("BrcmDtaSrv", "enable");
        return doEnableDta(autoStart);
    }

    public synchronized boolean disable() throws RemoteException {
        Log.d("BrcmDtaSrv", "disable");
        return doDisableDta();
    }

    public synchronized boolean stop() throws RemoteException {
        Log.d("BrcmDtaSrv", "stop");
        return doStopDta();
    }

    public synchronized boolean start(int patternNumber, byte[] tlv) throws RemoteException {
        Log.d("BrcmDtaSrv", "start");
        return doStartDta(patternNumber, tlv);
    }

    public synchronized boolean config(int configItem, int configData) throws RemoteException {
        Log.d("BrcmDtaSrv", "config, item=" + configItem + " data=" + configData);
        return doConfigDta(configItem, configData);
    }
}
