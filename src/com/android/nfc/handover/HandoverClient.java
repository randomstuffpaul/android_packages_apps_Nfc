package com.android.nfc.handover;

import android.util.Log;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;

public final class HandoverClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG;
    private static final int DISCONNECTED = 0;
    private static final int MIU = 128;
    private static final String TAG = "HandoverClient";
    private static final Object mLock;
    LlcpSocket mSocket;
    int mState;

    static {
        DBG = HandoverManager.DBG;
        mLock = new Object();
    }

    public void connect() throws IOException {
        synchronized (mLock) {
            if (this.mState != 0) {
                throw new IOException("Socket in use.");
            }
            this.mState = CONNECTING;
        }
        try {
            LlcpSocket sock = NfcService.getInstance().createLlcpSocket(DISCONNECTED, MIU, CONNECTING, Device.AUDIO_VIDEO_UNCATEGORIZED);
            try {
                if (DBG) {
                    Log.d(TAG, "about to connect to service urn:nfc:sn:handover");
                }
                sock.connectToService(HandoverServer.HANDOVER_SERVICE_NAME);
                synchronized (mLock) {
                    this.mSocket = sock;
                    this.mState = CONNECTED;
                }
            } catch (IOException e) {
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (IOException e2) {
                    }
                }
                synchronized (mLock) {
                }
                this.mState = DISCONNECTED;
                throw new IOException("Could not connect to handover service");
            }
        } catch (LlcpException e3) {
            synchronized (mLock) {
            }
            this.mState = DISCONNECTED;
            throw new IOException("Could not create socket");
        }
    }

    public void close() {
        synchronized (mLock) {
            if (this.mSocket != null) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                }
                this.mSocket = null;
            }
            this.mState = DISCONNECTED;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public android.nfc.NdefMessage sendHandoverRequest(android.nfc.NdefMessage r16) throws java.io.IOException {
        /*
        r15 = this;
        if (r16 != 0) goto L_0x0004;
    L_0x0002:
        r3 = 0;
    L_0x0003:
        return r3;
    L_0x0004:
        r10 = 0;
        r13 = mLock;
        monitor-enter(r13);
        r12 = r15.mState;	 Catch:{ all -> 0x0015 }
        r14 = 2;
        if (r12 == r14) goto L_0x0018;
    L_0x000d:
        r12 = new java.io.IOException;	 Catch:{ all -> 0x0015 }
        r14 = "Socket not connected";
        r12.<init>(r14);	 Catch:{ all -> 0x0015 }
        throw r12;	 Catch:{ all -> 0x0015 }
    L_0x0015:
        r12 = move-exception;
        monitor-exit(r13);	 Catch:{ all -> 0x0015 }
        throw r12;
    L_0x0018:
        r10 = r15.mSocket;	 Catch:{ all -> 0x0015 }
        monitor-exit(r13);	 Catch:{ all -> 0x0015 }
        r6 = 0;
        r0 = r16.toByteArray();
        r1 = new java.io.ByteArrayOutputStream;
        r1.<init>();
        r8 = r10.getRemoteMiu();	 Catch:{ IOException -> 0x00b6 }
        r12 = DBG;	 Catch:{ IOException -> 0x00b6 }
        if (r12 == 0) goto L_0x004c;
    L_0x002d:
        r12 = "HandoverClient";
        r13 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x00b6 }
        r13.<init>();	 Catch:{ IOException -> 0x00b6 }
        r14 = "about to send a ";
        r13 = r13.append(r14);	 Catch:{ IOException -> 0x00b6 }
        r14 = r0.length;	 Catch:{ IOException -> 0x00b6 }
        r13 = r13.append(r14);	 Catch:{ IOException -> 0x00b6 }
        r14 = " byte message";
        r13 = r13.append(r14);	 Catch:{ IOException -> 0x00b6 }
        r13 = r13.toString();	 Catch:{ IOException -> 0x00b6 }
        android.util.Log.d(r12, r13);	 Catch:{ IOException -> 0x00b6 }
    L_0x004c:
        r12 = r0.length;	 Catch:{ IOException -> 0x00b6 }
        if (r6 >= r12) goto L_0x0082;
    L_0x004f:
        r12 = r0.length;	 Catch:{ IOException -> 0x00b6 }
        r12 = r12 - r6;
        r5 = java.lang.Math.min(r12, r8);	 Catch:{ IOException -> 0x00b6 }
        r12 = r6 + r5;
        r11 = java.util.Arrays.copyOfRange(r0, r6, r12);	 Catch:{ IOException -> 0x00b6 }
        r12 = DBG;	 Catch:{ IOException -> 0x00b6 }
        if (r12 == 0) goto L_0x007d;
    L_0x005f:
        r12 = "HandoverClient";
        r13 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x00b6 }
        r13.<init>();	 Catch:{ IOException -> 0x00b6 }
        r14 = "about to send a ";
        r13 = r13.append(r14);	 Catch:{ IOException -> 0x00b6 }
        r13 = r13.append(r5);	 Catch:{ IOException -> 0x00b6 }
        r14 = " byte packet";
        r13 = r13.append(r14);	 Catch:{ IOException -> 0x00b6 }
        r13 = r13.toString();	 Catch:{ IOException -> 0x00b6 }
        android.util.Log.d(r12, r13);	 Catch:{ IOException -> 0x00b6 }
    L_0x007d:
        r10.send(r11);	 Catch:{ IOException -> 0x00b6 }
        r6 = r6 + r5;
        goto L_0x004c;
    L_0x0082:
        r12 = r10.getLocalMiu();	 Catch:{ IOException -> 0x00b6 }
        r7 = new byte[r12];	 Catch:{ IOException -> 0x00b6 }
        r3 = 0;
    L_0x0089:
        r9 = r10.receive(r7);	 Catch:{ IOException -> 0x00b6 }
        if (r9 >= 0) goto L_0x00a7;
    L_0x008f:
        if (r10 == 0) goto L_0x009f;
    L_0x0091:
        r12 = DBG;	 Catch:{ IOException -> 0x00f5 }
        if (r12 == 0) goto L_0x009c;
    L_0x0095:
        r12 = "HandoverClient";
        r13 = "about to close";
        android.util.Log.d(r12, r13);	 Catch:{ IOException -> 0x00f5 }
    L_0x009c:
        r10.close();	 Catch:{ IOException -> 0x00f5 }
    L_0x009f:
        r1.close();	 Catch:{ IOException -> 0x00a4 }
        goto L_0x0003;
    L_0x00a4:
        r12 = move-exception;
        goto L_0x0003;
    L_0x00a7:
        r12 = 0;
        r1.write(r7, r12, r9);	 Catch:{ IOException -> 0x00b6 }
        r4 = new android.nfc.NdefMessage;	 Catch:{ FormatException -> 0x00f7 }
        r12 = r1.toByteArray();	 Catch:{ FormatException -> 0x00f7 }
        r4.<init>(r12);	 Catch:{ FormatException -> 0x00f7 }
        r3 = r4;
        goto L_0x008f;
    L_0x00b6:
        r2 = move-exception;
        r12 = DBG;	 Catch:{ all -> 0x00d8 }
        if (r12 == 0) goto L_0x00c2;
    L_0x00bb:
        r12 = "HandoverClient";
        r13 = "couldn't connect to handover service";
        android.util.Log.d(r12, r13);	 Catch:{ all -> 0x00d8 }
    L_0x00c2:
        if (r10 == 0) goto L_0x00d2;
    L_0x00c4:
        r12 = DBG;	 Catch:{ IOException -> 0x00f3 }
        if (r12 == 0) goto L_0x00cf;
    L_0x00c8:
        r12 = "HandoverClient";
        r13 = "about to close";
        android.util.Log.d(r12, r13);	 Catch:{ IOException -> 0x00f3 }
    L_0x00cf:
        r10.close();	 Catch:{ IOException -> 0x00f3 }
    L_0x00d2:
        r1.close();	 Catch:{ IOException -> 0x00ed }
    L_0x00d5:
        r3 = 0;
        goto L_0x0003;
    L_0x00d8:
        r12 = move-exception;
        if (r10 == 0) goto L_0x00e9;
    L_0x00db:
        r13 = DBG;	 Catch:{ IOException -> 0x00f1 }
        if (r13 == 0) goto L_0x00e6;
    L_0x00df:
        r13 = "HandoverClient";
        r14 = "about to close";
        android.util.Log.d(r13, r14);	 Catch:{ IOException -> 0x00f1 }
    L_0x00e6:
        r10.close();	 Catch:{ IOException -> 0x00f1 }
    L_0x00e9:
        r1.close();	 Catch:{ IOException -> 0x00ef }
    L_0x00ec:
        throw r12;
    L_0x00ed:
        r12 = move-exception;
        goto L_0x00d5;
    L_0x00ef:
        r13 = move-exception;
        goto L_0x00ec;
    L_0x00f1:
        r13 = move-exception;
        goto L_0x00e9;
    L_0x00f3:
        r12 = move-exception;
        goto L_0x00d2;
    L_0x00f5:
        r12 = move-exception;
        goto L_0x009f;
    L_0x00f7:
        r12 = move-exception;
        goto L_0x0089;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.HandoverClient.sendHandoverRequest(android.nfc.NdefMessage):android.nfc.NdefMessage");
    }
}
