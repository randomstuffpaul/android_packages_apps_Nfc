package com.android.nfc.broadcom;

import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.NfcService;
import com.broadcom.nfc.INfcAdapterBrcmConfig.Stub;

class NativeNfcBrcmConfig extends Stub {
    final String TAG;
    NfcService mService;

    native String doGetConfig(String str);

    native byte[] doGetFirmwareConfig(int i);

    native boolean doSetConfig(String str);

    native boolean doSetFirmwareConfig(int i, byte[] bArr);

    public NativeNfcBrcmConfig(NfcService s) {
        this.TAG = "BrcmCfgServ";
        this.mService = null;
        this.mService = s;
        Log.i("BrcmCfgServ", "NativeNfcBrcmConfig");
    }

    public synchronized boolean setConfig(String pkg, String xmlConfig) throws RemoteException {
        Log.i("BrcmCfgServ", "NfcAdapterBrcmExtrasService; setConfig");
        this.mService.enforceNfceeAdminPerm(pkg);
        return doSetConfig(xmlConfig);
    }

    public synchronized String getConfig(String pkg, String configItem) throws RemoteException {
        Log.i("BrcmCfgServ", "NfcAdapterBrcmExtrasService; getConfig");
        return doGetConfig(configItem);
    }

    public synchronized boolean setFirmwareConfig(String pkg, int paramId, byte[] data) throws RemoteException {
        Log.i("BrcmCfgServ", "NfcAdapterBrcmExtrasService; setFirmwareConfig");
        this.mService.enforceNfceeAdminPerm(pkg);
        return doSetFirmwareConfig(paramId, data);
    }

    public synchronized byte[] getFirmwareConfig(String pkg, int paramId) {
        Log.i("BrcmCfgServ", "NfcAdapterBrcmExtrasService; getFirmwareConfig");
        this.mService.enforceNfceeAdminPerm(pkg);
        return doGetFirmwareConfig(paramId);
    }
}
