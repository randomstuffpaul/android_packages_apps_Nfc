package com.android.nfc;

public class LlcpPacket {
    private byte[] mDataBuffer;
    private int mRemoteSap;

    public int getRemoteSap() {
        return this.mRemoteSap;
    }

    public byte[] getDataBuffer() {
        return this.mDataBuffer;
    }
}
