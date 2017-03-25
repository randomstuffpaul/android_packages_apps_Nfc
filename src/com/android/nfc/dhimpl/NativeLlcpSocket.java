package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost.LlcpSocket;
import java.io.IOException;

public class NativeLlcpSocket implements LlcpSocket {
    private int mHandle;
    private int mLocalMiu;
    private int mLocalRw;
    private int mSap;

    private native boolean doClose();

    private native boolean doConnect(int i);

    private native boolean doConnectBy(String str);

    private native int doGetRemoteSocketMiu();

    private native int doGetRemoteSocketRw();

    private native int doReceive(byte[] bArr);

    private native boolean doSend(byte[] bArr);

    public void connectToSap(int sap) throws IOException {
        if (!doConnect(sap)) {
            throw new IOException();
        }
    }

    public void connectToService(String serviceName) throws IOException {
        if (!doConnectBy(serviceName)) {
            throw new IOException();
        }
    }

    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }

    public void send(byte[] data) throws IOException {
        if (!doSend(data)) {
            throw new IOException();
        }
    }

    public int receive(byte[] recvBuff) throws IOException {
        int receiveLength = doReceive(recvBuff);
        if (receiveLength != -1) {
            return receiveLength;
        }
        throw new IOException();
    }

    public int getRemoteMiu() {
        return doGetRemoteSocketMiu();
    }

    public int getRemoteRw() {
        return doGetRemoteSocketRw();
    }

    public int getLocalSap() {
        return this.mSap;
    }

    public int getLocalMiu() {
        return this.mLocalMiu;
    }

    public int getLocalRw() {
        return this.mLocalRw;
    }
}
