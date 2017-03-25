package com.android.nfc.ndefpush;

import android.nfc.NdefMessage;
import android.os.Debug;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;

public class NdefPushServer {
    static final boolean DBG;
    private static final int MIU = 248;
    static final String SERVICE_NAME = "com.android.npp";
    private static final String TAG = "NdefPushServer";
    final Callback mCallback;
    int mSap;
    ServerThread mServerThread;
    NfcService mService;

    public interface Callback {
        void onMessageReceived(NdefMessage ndefMessage);
    }

    private class ConnectionThread extends Thread {
        private LlcpSocket mSock;

        public void run() {
            /* JADX: method processing error */
/*
            Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x00bc in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:58)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:281)
	at jadx.api.JavaClass.decompile(JavaClass.java:59)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:161)
*/
            /*
            r9 = this;
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;
            if (r6 == 0) goto L_0x000b;
        L_0x0004:
            r6 = "NdefPushServer";
            r7 = "starting connection thread";
            android.util.Log.d(r6, r7);
        L_0x000b:
            r0 = new java.io.ByteArrayOutputStream;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = 1024; // 0x400 float:1.435E-42 double:5.06E-321;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r0.<init>(r6);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = 1024; // 0x400 float:1.435E-42 double:5.06E-321;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r4 = new byte[r6];	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r1 = 0;
        L_0x0017:
            if (r1 != 0) goto L_0x0044;
        L_0x0019:
            r6 = r9.mSock;	 Catch:{ IOException -> 0x009a }
            r5 = r6.receive(r4);	 Catch:{ IOException -> 0x009a }
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;	 Catch:{ IOException -> 0x009a }
            if (r6 == 0) goto L_0x0041;	 Catch:{ IOException -> 0x009a }
        L_0x0023:
            r6 = "NdefPushServer";	 Catch:{ IOException -> 0x009a }
            r7 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x009a }
            r7.<init>();	 Catch:{ IOException -> 0x009a }
            r8 = "read ";	 Catch:{ IOException -> 0x009a }
            r7 = r7.append(r8);	 Catch:{ IOException -> 0x009a }
            r7 = r7.append(r5);	 Catch:{ IOException -> 0x009a }
            r8 = " bytes";	 Catch:{ IOException -> 0x009a }
            r7 = r7.append(r8);	 Catch:{ IOException -> 0x009a }
            r7 = r7.toString();	 Catch:{ IOException -> 0x009a }
            android.util.Log.d(r6, r7);	 Catch:{ IOException -> 0x009a }
        L_0x0041:
            if (r5 >= 0) goto L_0x0094;
        L_0x0043:
            r1 = 1;
        L_0x0044:
            r3 = new com.android.nfc.ndefpush.NdefPushProtocol;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = r0.toByteArray();	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r3.<init>(r6);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            if (r6 == 0) goto L_0x006d;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
        L_0x0051:
            r6 = "NdefPushServer";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = new java.lang.StringBuilder;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7.<init>();	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r8 = "got message ";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = r7.append(r8);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r8 = r3.toString();	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = r7.append(r8);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = r7.toString();	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            android.util.Log.d(r6, r7);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
        L_0x006d:
            r6 = com.android.nfc.ndefpush.NdefPushServer.this;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = r6.mCallback;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = r3.getImmediate();	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6.onMessageReceived(r7);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;	 Catch:{ IOException -> 0x00d8 }
            if (r6 == 0) goto L_0x0083;	 Catch:{ IOException -> 0x00d8 }
        L_0x007c:
            r6 = "NdefPushServer";	 Catch:{ IOException -> 0x00d8 }
            r7 = "about to close";	 Catch:{ IOException -> 0x00d8 }
            android.util.Log.d(r6, r7);	 Catch:{ IOException -> 0x00d8 }
        L_0x0083:
            r6 = r9.mSock;	 Catch:{ IOException -> 0x00d8 }
            r6.close();	 Catch:{ IOException -> 0x00d8 }
        L_0x0088:
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;
            if (r6 == 0) goto L_0x0093;
        L_0x008c:
            r6 = "NdefPushServer";
            r7 = "finished connection thread";
            android.util.Log.d(r6, r7);
        L_0x0093:
            return;
        L_0x0094:
            r6 = 0;
            r0.write(r4, r6, r5);	 Catch:{ IOException -> 0x009a }
            goto L_0x0017;
        L_0x009a:
            r2 = move-exception;
            r1 = 1;
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            if (r6 == 0) goto L_0x0017;	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
        L_0x00a0:
            r6 = "NdefPushServer";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = "connection broken by IOException";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            android.util.Log.d(r6, r7, r2);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            goto L_0x0017;
        L_0x00a9:
            r2 = move-exception;
            r6 = "NdefPushServer";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r7 = "badly formatted NDEF message, ignoring";	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            android.util.Log.e(r6, r7, r2);	 Catch:{ FormatException -> 0x00a9, all -> 0x00c4 }
            r6 = com.android.nfc.ndefpush.NdefPushServer.DBG;
            if (r6 == 0) goto L_0x00bc;
        L_0x00b5:
            r6 = "NdefPushServer";
            r7 = "about to close";
            android.util.Log.d(r6, r7);
        L_0x00bc:
            r6 = r9.mSock;
            r6.close();
            goto L_0x0088;
        L_0x00c2:
            r6 = move-exception;
            goto L_0x0088;
        L_0x00c4:
            r6 = move-exception;
            r7 = com.android.nfc.ndefpush.NdefPushServer.DBG;	 Catch:{ IOException -> 0x00d6 }
            if (r7 == 0) goto L_0x00d0;	 Catch:{ IOException -> 0x00d6 }
        L_0x00c9:
            r7 = "NdefPushServer";	 Catch:{ IOException -> 0x00d6 }
            r8 = "about to close";	 Catch:{ IOException -> 0x00d6 }
            android.util.Log.d(r7, r8);	 Catch:{ IOException -> 0x00d6 }
        L_0x00d0:
            r7 = r9.mSock;	 Catch:{ IOException -> 0x00d6 }
            r7.close();	 Catch:{ IOException -> 0x00d6 }
        L_0x00d5:
            throw r6;
        L_0x00d6:
            r7 = move-exception;
            goto L_0x00d5;
        L_0x00d8:
            r6 = move-exception;
            goto L_0x0088;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.ndefpush.NdefPushServer.ConnectionThread.run():void");
        }

        ConnectionThread(LlcpSocket sock) {
            super(NdefPushServer.TAG);
            this.mSock = sock;
        }
    }

    class ServerThread extends Thread {
        boolean mRunning;
        LlcpServerSocket mServerSocket;

        ServerThread() {
            this.mRunning = true;
        }

        public void run() {
            synchronized (NdefPushServer.this) {
                boolean threadRunning = this.mRunning;
            }
            while (threadRunning) {
                if (NdefPushServer.DBG) {
                    Log.d(NdefPushServer.TAG, "about create LLCP service socket");
                }
                try {
                    synchronized (NdefPushServer.this) {
                        this.mServerSocket = NdefPushServer.this.mService.createLlcpServerSocket(NdefPushServer.this.mSap, NdefPushServer.SERVICE_NAME, NdefPushServer.MIU, 1, Device.AUDIO_VIDEO_UNCATEGORIZED);
                    }
                    if (this.mServerSocket == null) {
                        if (NdefPushServer.DBG) {
                            Log.d(NdefPushServer.TAG, "failed to create LLCP service socket");
                        }
                        synchronized (NdefPushServer.this) {
                            if (this.mServerSocket != null) {
                                if (NdefPushServer.DBG) {
                                    Log.d(NdefPushServer.TAG, "about to close");
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
                    if (NdefPushServer.DBG) {
                        Log.d(NdefPushServer.TAG, "created LLCP service socket");
                    }
                    synchronized (NdefPushServer.this) {
                        threadRunning = this.mRunning;
                    }
                    while (threadRunning) {
                        LlcpServerSocket serverSocket;
                        synchronized (NdefPushServer.this) {
                            serverSocket = this.mServerSocket;
                        }
                        if (serverSocket == null) {
                            synchronized (NdefPushServer.this) {
                                if (this.mServerSocket != null) {
                                    if (NdefPushServer.DBG) {
                                        Log.d(NdefPushServer.TAG, "about to close");
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
                        if (NdefPushServer.DBG) {
                            Log.d(NdefPushServer.TAG, "about to accept");
                        }
                        LlcpSocket communicationSocket = serverSocket.accept();
                        if (NdefPushServer.DBG) {
                            Log.d(NdefPushServer.TAG, "accept returned " + communicationSocket);
                        }
                        if (communicationSocket != null) {
                            new ConnectionThread(communicationSocket).start();
                        }
                        synchronized (NdefPushServer.this) {
                            threadRunning = this.mRunning;
                        }
                    }
                    if (NdefPushServer.DBG) {
                        Log.d(NdefPushServer.TAG, "stop running");
                    }
                    synchronized (NdefPushServer.this) {
                        if (this.mServerSocket != null) {
                            if (NdefPushServer.DBG) {
                                Log.d(NdefPushServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e3) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    synchronized (NdefPushServer.this) {
                        threadRunning = this.mRunning;
                    }
                } catch (LlcpException e4) {
                    Log.e(NdefPushServer.TAG, "llcp error", e4);
                    synchronized (NdefPushServer.this) {
                    }
                    if (this.mServerSocket != null) {
                        if (NdefPushServer.DBG) {
                            Log.d(NdefPushServer.TAG, "about to close");
                        }
                        try {
                            this.mServerSocket.close();
                        } catch (IOException e5) {
                        }
                        this.mServerSocket = null;
                    }
                } catch (LlcpException e42) {
                    try {
                        Log.e(NdefPushServer.TAG, "IO error", e42);
                        synchronized (NdefPushServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (NdefPushServer.DBG) {
                                Log.d(NdefPushServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    } catch (Throwable th) {
                        synchronized (NdefPushServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (NdefPushServer.DBG) {
                                Log.d(NdefPushServer.TAG, "about to close");
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
            synchronized (NdefPushServer.this) {
                this.mRunning = NdefPushServer.DBG;
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
        boolean z = true;
        if (Debug.isProductShip() == 1) {
            z = DBG;
        }
        DBG = z;
    }

    public NdefPushServer(int sap, Callback callback) {
        this.mService = NfcService.getInstance();
        this.mServerThread = null;
        this.mSap = sap;
        this.mCallback = callback;
    }

    public void start() {
        synchronized (this) {
            if (DBG) {
                Log.d(TAG, "start, thread = " + this.mServerThread);
            }
            if (this.mServerThread == null) {
                if (DBG) {
                    Log.d(TAG, "starting new server thread");
                }
                this.mServerThread = new ServerThread();
                this.mServerThread.start();
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (DBG) {
                Log.d(TAG, "stop, thread = " + this.mServerThread);
            }
            if (this.mServerThread != null) {
                if (DBG) {
                    Log.d(TAG, "shuting down server thread");
                }
                this.mServerThread.shutdown();
                this.mServerThread = null;
            }
        }
    }
}
