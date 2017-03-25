package com.android.nfc.handover;

import android.util.Log;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;

public final class HandoverServer {
    public static final Boolean DBG;
    public static final String HANDOVER_SERVICE_NAME = "urn:nfc:sn:handover";
    public static final int MIU = 128;
    public static final String TAG = "HandoverServer";
    final Callback mCallback;
    final HandoverManager mHandoverManager;
    final int mSap;
    boolean mServerRunning;
    ServerThread mServerThread;

    public interface Callback {
        void onHandoverRequestReceived();
    }

    private class ConnectionThread extends Thread {
        private final LlcpSocket mSock;

        ConnectionThread(LlcpSocket socket) {
            super(HandoverServer.TAG);
            this.mSock = socket;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            /*
            r18 = this;
            r15 = com.android.nfc.handover.HandoverServer.DBG;
            r15 = r15.booleanValue();
            if (r15 == 0) goto L_0x000f;
        L_0x0008:
            r15 = "HandoverServer";
            r16 = "starting connection thread";
            android.util.Log.d(r15, r16);
        L_0x000f:
            r2 = new java.io.ByteArrayOutputStream;
            r2.<init>();
            r0 = r18;
            r0 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ IOException -> 0x0069 }
            r16 = r0;
            monitor-enter(r16);	 Catch:{ IOException -> 0x0069 }
            r0 = r18;
            r15 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ all -> 0x0066 }
            r12 = r15.mServerRunning;	 Catch:{ all -> 0x0066 }
            monitor-exit(r16);	 Catch:{ all -> 0x0066 }
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x0069 }
            r15 = r15.getLocalMiu();	 Catch:{ IOException -> 0x0069 }
            r9 = new byte[r15];	 Catch:{ IOException -> 0x0069 }
            r5 = 0;
            r6 = r5;
            r3 = r2;
        L_0x002f:
            if (r12 == 0) goto L_0x0136;
        L_0x0031:
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r13 = r15.receive(r9);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            if (r13 >= 0) goto L_0x0095;
        L_0x003b:
            r5 = r6;
        L_0x003c:
            r15 = com.android.nfc.handover.HandoverServer.DBG;	 Catch:{ IOException -> 0x0131 }
            r15 = r15.booleanValue();	 Catch:{ IOException -> 0x0131 }
            if (r15 == 0) goto L_0x004b;
        L_0x0044:
            r15 = "HandoverServer";
            r16 = "about to close";
            android.util.Log.d(r15, r16);	 Catch:{ IOException -> 0x0131 }
        L_0x004b:
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x0131 }
            r15.close();	 Catch:{ IOException -> 0x0131 }
        L_0x0052:
            r3.close();	 Catch:{ IOException -> 0x0123 }
            r2 = r3;
        L_0x0056:
            r15 = com.android.nfc.handover.HandoverServer.DBG;
            r15 = r15.booleanValue();
            if (r15 == 0) goto L_0x0065;
        L_0x005e:
            r15 = "HandoverServer";
            r16 = "finished connection thread";
            android.util.Log.d(r15, r16);
        L_0x0065:
            return;
        L_0x0066:
            r15 = move-exception;
            monitor-exit(r16);	 Catch:{ all -> 0x0066 }
            throw r15;	 Catch:{ IOException -> 0x0069 }
        L_0x0069:
            r4 = move-exception;
        L_0x006a:
            r15 = com.android.nfc.handover.HandoverServer.DBG;	 Catch:{ all -> 0x0106 }
            r15 = r15.booleanValue();	 Catch:{ all -> 0x0106 }
            if (r15 == 0) goto L_0x0079;
        L_0x0072:
            r15 = "HandoverServer";
            r16 = "IOException";
            android.util.Log.d(r15, r16);	 Catch:{ all -> 0x0106 }
        L_0x0079:
            r15 = com.android.nfc.handover.HandoverServer.DBG;	 Catch:{ IOException -> 0x012e }
            r15 = r15.booleanValue();	 Catch:{ IOException -> 0x012e }
            if (r15 == 0) goto L_0x0088;
        L_0x0081:
            r15 = "HandoverServer";
            r16 = "about to close";
            android.util.Log.d(r15, r16);	 Catch:{ IOException -> 0x012e }
        L_0x0088:
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x012e }
            r15.close();	 Catch:{ IOException -> 0x012e }
        L_0x008f:
            r2.close();	 Catch:{ IOException -> 0x0093 }
            goto L_0x0056;
        L_0x0093:
            r15 = move-exception;
            goto L_0x0056;
        L_0x0095:
            r15 = 0;
            r3.write(r9, r15, r13);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r5 = new android.nfc.NdefMessage;	 Catch:{ FormatException -> 0x00bb }
            r15 = r3.toByteArray();	 Catch:{ FormatException -> 0x00bb }
            r5.<init>(r15);	 Catch:{ FormatException -> 0x00bb }
        L_0x00a2:
            if (r5 == 0) goto L_0x0134;
        L_0x00a4:
            r0 = r18;
            r15 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15 = r15.mHandoverManager;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r11 = r15.tryHandoverRequest(r5);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            if (r11 != 0) goto L_0x00be;
        L_0x00b0:
            r15 = "HandoverServer";
            r16 = "Failed to create handover response";
            android.util.Log.e(r15, r16);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            goto L_0x003c;
        L_0x00b8:
            r4 = move-exception;
            r2 = r3;
            goto L_0x006a;
        L_0x00bb:
            r15 = move-exception;
            r5 = r6;
            goto L_0x00a2;
        L_0x00be:
            r8 = 0;
            r1 = r11.toByteArray();	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r10 = r15.getRemoteMiu();	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
        L_0x00cb:
            r15 = r1.length;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            if (r8 >= r15) goto L_0x00e3;
        L_0x00ce:
            r15 = r1.length;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15 = r15 - r8;
            r7 = java.lang.Math.min(r15, r10);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15 = r8 + r7;
            r14 = java.util.Arrays.copyOfRange(r1, r8, r15);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r0 = r18;
            r15 = r0.mSock;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15.send(r14);	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r8 = r8 + r7;
            goto L_0x00cb;
        L_0x00e3:
            r0 = r18;
            r15 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15 = r15.mCallback;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r15.onHandoverRequestReceived();	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r2 = new java.io.ByteArrayOutputStream;	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
            r2.<init>();	 Catch:{ IOException -> 0x00b8, all -> 0x012b }
        L_0x00f1:
            r0 = r18;
            r0 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ IOException -> 0x0069 }
            r16 = r0;
            monitor-enter(r16);	 Catch:{ IOException -> 0x0069 }
            r0 = r18;
            r15 = com.android.nfc.handover.HandoverServer.this;	 Catch:{ all -> 0x0103 }
            r12 = r15.mServerRunning;	 Catch:{ all -> 0x0103 }
            monitor-exit(r16);	 Catch:{ all -> 0x0103 }
            r6 = r5;
            r3 = r2;
            goto L_0x002f;
        L_0x0103:
            r15 = move-exception;
            monitor-exit(r16);	 Catch:{ all -> 0x0103 }
            throw r15;	 Catch:{ IOException -> 0x0069 }
        L_0x0106:
            r15 = move-exception;
        L_0x0107:
            r16 = com.android.nfc.handover.HandoverServer.DBG;	 Catch:{ IOException -> 0x0129 }
            r16 = r16.booleanValue();	 Catch:{ IOException -> 0x0129 }
            if (r16 == 0) goto L_0x0116;
        L_0x010f:
            r16 = "HandoverServer";
            r17 = "about to close";
            android.util.Log.d(r16, r17);	 Catch:{ IOException -> 0x0129 }
        L_0x0116:
            r0 = r18;
            r0 = r0.mSock;	 Catch:{ IOException -> 0x0129 }
            r16 = r0;
            r16.close();	 Catch:{ IOException -> 0x0129 }
        L_0x011f:
            r2.close();	 Catch:{ IOException -> 0x0127 }
        L_0x0122:
            throw r15;
        L_0x0123:
            r15 = move-exception;
            r2 = r3;
            goto L_0x0056;
        L_0x0127:
            r16 = move-exception;
            goto L_0x0122;
        L_0x0129:
            r16 = move-exception;
            goto L_0x011f;
        L_0x012b:
            r15 = move-exception;
            r2 = r3;
            goto L_0x0107;
        L_0x012e:
            r15 = move-exception;
            goto L_0x008f;
        L_0x0131:
            r15 = move-exception;
            goto L_0x0052;
        L_0x0134:
            r2 = r3;
            goto L_0x00f1;
        L_0x0136:
            r5 = r6;
            goto L_0x003c;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.HandoverServer.ConnectionThread.run():void");
        }
    }

    private class ServerThread extends Thread {
        LlcpServerSocket mServerSocket;
        private boolean mThreadRunning;

        private ServerThread() {
            this.mThreadRunning = true;
        }

        public void run() {
            synchronized (HandoverServer.this) {
                boolean threadRunning = this.mThreadRunning;
            }
            while (threadRunning) {
                try {
                    synchronized (HandoverServer.this) {
                        this.mServerSocket = NfcService.getInstance().createLlcpServerSocket(HandoverServer.this.mSap, HandoverServer.HANDOVER_SERVICE_NAME, HandoverServer.MIU, 1, Device.AUDIO_VIDEO_UNCATEGORIZED);
                    }
                    if (this.mServerSocket == null) {
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "failed to create LLCP service socket");
                        }
                        synchronized (HandoverServer.this) {
                            if (this.mServerSocket != null) {
                                if (HandoverServer.DBG.booleanValue()) {
                                    Log.d(HandoverServer.TAG, "about to close");
                                }
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e) {
                                }
                                this.mServerSocket = null;
                            }
                        }
                        return;
                    }
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "created LLCP service socket");
                    }
                    synchronized (HandoverServer.this) {
                        threadRunning = this.mThreadRunning;
                    }
                    while (threadRunning) {
                        LlcpServerSocket serverSocket;
                        synchronized (HandoverServer.this) {
                            serverSocket = this.mServerSocket;
                        }
                        if (serverSocket == null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "Server socket shut down.");
                            }
                            synchronized (HandoverServer.this) {
                                if (this.mServerSocket != null) {
                                    if (HandoverServer.DBG.booleanValue()) {
                                        Log.d(HandoverServer.TAG, "about to close");
                                    }
                                    try {
                                        this.mServerSocket.close();
                                    } catch (IOException e2) {
                                    }
                                    this.mServerSocket = null;
                                }
                            }
                            return;
                        }
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "about to accept");
                        }
                        LlcpSocket communicationSocket = serverSocket.accept();
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "accept returned " + communicationSocket);
                        }
                        if (communicationSocket != null) {
                            new ConnectionThread(communicationSocket).start();
                        }
                        synchronized (HandoverServer.this) {
                            threadRunning = this.mThreadRunning;
                        }
                    }
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "stop running");
                    }
                    synchronized (HandoverServer.this) {
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e3) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    synchronized (HandoverServer.this) {
                        threadRunning = this.mThreadRunning;
                    }
                } catch (LlcpException e4) {
                    Log.e(HandoverServer.TAG, "llcp error", e4);
                    synchronized (HandoverServer.this) {
                    }
                    if (this.mServerSocket != null) {
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "about to close");
                        }
                        try {
                            this.mServerSocket.close();
                        } catch (IOException e5) {
                        }
                        this.mServerSocket = null;
                    }
                } catch (LlcpException e42) {
                    try {
                        Log.e(HandoverServer.TAG, "IO error", e42);
                        synchronized (HandoverServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    } catch (Throwable th) {
                        synchronized (HandoverServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e7) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                }
            }
        }

        public void shutdown() {
            synchronized (HandoverServer.this) {
                this.mThreadRunning = false;
                if (this.mServerSocket != null) {
                    try {
                        this.mServerSocket.close();
                    } catch (IOException e) {
                    }
                    this.mServerSocket = null;
                }
            }
        }
    }

    static {
        DBG = Boolean.valueOf(HandoverManager.DBG);
    }

    public HandoverServer(int sap, HandoverManager manager, Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = false;
        this.mSap = sap;
        this.mHandoverManager = manager;
        this.mCallback = callback;
    }

    public synchronized void start() {
        if (this.mServerThread == null) {
            this.mServerThread = new ServerThread();
            this.mServerThread.start();
            this.mServerRunning = true;
        }
    }

    public synchronized void stop() {
        if (this.mServerThread != null) {
            this.mServerThread.shutdown();
            this.mServerThread = null;
            this.mServerRunning = false;
        }
    }
}
