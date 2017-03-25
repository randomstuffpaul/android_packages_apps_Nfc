package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost.LlcpConnectionlessSocket;
import com.android.nfc.LlcpPacket;
import java.io.IOException;

public class NativeLlcpConnectionlessSocket implements LlcpConnectionlessSocket {
    private int mHandle;
    private int mLinkMiu;
    private int mSap;

    public native boolean doClose();

    public native LlcpPacket doReceiveFrom(int i);

    public native boolean doSendTo(int i, byte[] bArr);

    public int getLinkMiu() {
        return this.mLinkMiu;
    }

    public int getSap() {
        return this.mSap;
    }

    public void send(int sap, byte[] data) throws IOException {
        if (!doSendTo(sap, data)) {
            throw new IOException();
        }
    }

    public LlcpPacket receive() throws IOException {
        LlcpPacket packet = doReceiveFrom(this.mLinkMiu);
        if (packet != null) {
            return packet;
        }
        throw new IOException();
    }

    public int getHandle() {
        return this.mHandle;
    }

    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }
}
