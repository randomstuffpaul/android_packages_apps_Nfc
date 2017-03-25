package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import java.io.IOException;

public class NativeLlcpServiceSocket implements LlcpServerSocket {
    private int mHandle;
    private int mLocalLinearBufferLength;
    private int mLocalMiu;
    private int mLocalRw;
    private int mSap;
    private String mServiceName;

    private native NativeLlcpSocket doAccept(int i, int i2, int i3);

    private native boolean doClose();

    public LlcpSocket accept() throws IOException {
        LlcpSocket socket = doAccept(this.mLocalMiu, this.mLocalRw, this.mLocalLinearBufferLength);
        if (socket != null) {
            return socket;
        }
        throw new IOException();
    }

    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }
}
