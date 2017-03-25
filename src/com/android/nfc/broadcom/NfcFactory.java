package com.android.nfc.broadcom;

import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.NfcService;
import com.broadcom.nfc.INfcAdapterBrcmConfig;
import com.broadcom.nfc.INfcAdapterDta;
import com.broadcom.nfc.INfcFactory.Stub;

public class NfcFactory extends Stub {
    static final boolean DBG;
    public static final String SERVICE_NAME = "com.broadcom.nfc.Factory";
    static final String TAG = "NfcFacServ";
    static NativeNfcBrcmConfig mConfig;
    static NativeNfcAdapterDta mDta;
    NfcService mNfcService;

    static {
        DBG = NativeNfcAdapterDta.DBG;
    }

    public NfcFactory(NfcService nfcService) {
        if (DBG) {
            Log.d(TAG, "constructor");
        }
        this.mNfcService = nfcService;
        mConfig = new NativeNfcBrcmConfig(this.mNfcService);
        mDta = new NativeNfcAdapterDta(this.mNfcService);
    }

    public synchronized INfcAdapterBrcmConfig getConfigInterface() throws RemoteException {
        if (DBG) {
            Log.d(TAG, "getConfigInterface");
        }
        return mConfig;
    }

    public synchronized INfcAdapterDta getDtaInterface() throws RemoteException {
        if (DBG) {
            Log.d(TAG, "getDtaInterface");
        }
        return mDta;
    }
}
