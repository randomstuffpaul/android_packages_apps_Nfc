package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost.NfcDepEndpoint;

public class NativeP2pDevice implements NfcDepEndpoint {
    private byte[] mGeneralBytes;
    private int mHandle;
    private int mMode;

    private native boolean doConnect();

    private native boolean doDisconnect();

    private native byte[] doReceive();

    private native boolean doSend(byte[] bArr);

    public native byte[] doTransceive(byte[] bArr);

    public byte[] receive() {
        return doReceive();
    }

    public boolean send(byte[] data) {
        return doSend(data);
    }

    public boolean connect() {
        return doConnect();
    }

    public boolean disconnect() {
        return doDisconnect();
    }

    public byte[] transceive(byte[] data) {
        return doTransceive(data);
    }

    public int getHandle() {
        return this.mHandle;
    }

    public int getMode() {
        return this.mMode;
    }

    public byte[] getGeneralBytes() {
        return this.mGeneralBytes;
    }
}
