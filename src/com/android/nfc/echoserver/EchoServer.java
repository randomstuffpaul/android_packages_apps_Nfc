package com.android.nfc.echoserver;

import android.os.Debug;
import android.os.Handler;
import android.os.Handler.Callback;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpConnectionlessSocket;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class EchoServer {
    static final String CONNECTIONLESS_SERVICE_NAME = "urn:nfc:sn:cl-echo";
    static final String CONNECTION_SERVICE_NAME = "urn:nfc:sn:co-echo";
    static final boolean DBG;
    static final int DEFAULT_CL_SAP = 18;
    static final int DEFAULT_CO_SAP = 17;
    static final int MIU = 128;
    static final String TAG = "EchoServer";
    ConnectionlessServerThread mConnectionlessServerThread;
    ServerThread mServerThread;
    NfcService mService;

    public interface WriteCallback {
        void write(byte[] bArr);
    }

    public class ConnectionlessServerThread extends Thread implements WriteCallback {
        final EchoMachine echoMachine;
        int mRemoteSap;
        boolean mRunning;
        LlcpConnectionlessSocket socket;

        public ConnectionlessServerThread() {
            this.mRunning = true;
            this.echoMachine = new EchoMachine(this, true);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            /*
            r8 = this;
            r0 = 0;
            r5 = com.android.nfc.echoserver.EchoServer.DBG;
            if (r5 == 0) goto L_0x000c;
        L_0x0005:
            r5 = "EchoServer";
            r6 = "about create LLCP connectionless socket";
            android.util.Log.d(r5, r6);
        L_0x000c:
            r5 = com.android.nfc.echoserver.EchoServer.this;	 Catch:{ LlcpException -> 0x00a6 }
            r5 = r5.mService;	 Catch:{ LlcpException -> 0x00a6 }
            r6 = 18;
            r7 = "urn:nfc:sn:cl-echo";
            r5 = r5.createLlcpConnectionLessSocket(r6, r7);	 Catch:{ LlcpException -> 0x00a6 }
            r8.socket = r5;	 Catch:{ LlcpException -> 0x00a6 }
            r5 = r8.socket;	 Catch:{ LlcpException -> 0x00a6 }
            if (r5 != 0) goto L_0x0043;
        L_0x001e:
            r5 = com.android.nfc.echoserver.EchoServer.DBG;	 Catch:{ LlcpException -> 0x00a6 }
            if (r5 == 0) goto L_0x0029;
        L_0x0022:
            r5 = "EchoServer";
            r6 = "failed to create LLCP connectionless socket";
            android.util.Log.d(r5, r6);	 Catch:{ LlcpException -> 0x00a6 }
        L_0x0029:
            r5 = r8.echoMachine;
            r5.shutdown();
            r5 = r8.socket;
            if (r5 == 0) goto L_0x0037;
        L_0x0032:
            r5 = r8.socket;	 Catch:{ IOException -> 0x00d3 }
            r5.close();	 Catch:{ IOException -> 0x00d3 }
        L_0x0037:
            return;
        L_0x0038:
            r5 = r3.getRemoteSap();	 Catch:{ IOException -> 0x0098 }
            r8.mRemoteSap = r5;	 Catch:{ IOException -> 0x0098 }
            r5 = r8.echoMachine;	 Catch:{ IOException -> 0x0098 }
            r5.pushUnit(r1, r4);	 Catch:{ IOException -> 0x0098 }
        L_0x0043:
            r5 = r8.mRunning;	 Catch:{ LlcpException -> 0x00a6 }
            if (r5 == 0) goto L_0x0057;
        L_0x0047:
            if (r0 != 0) goto L_0x0057;
        L_0x0049:
            r5 = r8.socket;	 Catch:{ IOException -> 0x0098 }
            r3 = r5.receive();	 Catch:{ IOException -> 0x0098 }
            if (r3 == 0) goto L_0x0057;
        L_0x0051:
            r5 = r3.getDataBuffer();	 Catch:{ IOException -> 0x0098 }
            if (r5 != 0) goto L_0x0068;
        L_0x0057:
            r5 = r8.echoMachine;
            r5.shutdown();
            r5 = r8.socket;
            if (r5 == 0) goto L_0x0037;
        L_0x0060:
            r5 = r8.socket;	 Catch:{ IOException -> 0x0066 }
            r5.close();	 Catch:{ IOException -> 0x0066 }
            goto L_0x0037;
        L_0x0066:
            r5 = move-exception;
            goto L_0x0037;
        L_0x0068:
            r1 = r3.getDataBuffer();	 Catch:{ IOException -> 0x0098 }
            r4 = r1.length;	 Catch:{ IOException -> 0x0098 }
            r5 = com.android.nfc.echoserver.EchoServer.DBG;	 Catch:{ IOException -> 0x0098 }
            if (r5 == 0) goto L_0x0094;
        L_0x0071:
            r5 = "EchoServer";
            r6 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x0098 }
            r6.<init>();	 Catch:{ IOException -> 0x0098 }
            r7 = "read ";
            r6 = r6.append(r7);	 Catch:{ IOException -> 0x0098 }
            r7 = r3.getDataBuffer();	 Catch:{ IOException -> 0x0098 }
            r7 = r7.length;	 Catch:{ IOException -> 0x0098 }
            r6 = r6.append(r7);	 Catch:{ IOException -> 0x0098 }
            r7 = " bytes";
            r6 = r6.append(r7);	 Catch:{ IOException -> 0x0098 }
            r6 = r6.toString();	 Catch:{ IOException -> 0x0098 }
            android.util.Log.d(r5, r6);	 Catch:{ IOException -> 0x0098 }
        L_0x0094:
            if (r4 >= 0) goto L_0x0038;
        L_0x0096:
            r0 = 1;
            goto L_0x0057;
        L_0x0098:
            r2 = move-exception;
            r0 = 1;
            r5 = com.android.nfc.echoserver.EchoServer.DBG;	 Catch:{ LlcpException -> 0x00a6 }
            if (r5 == 0) goto L_0x0043;
        L_0x009e:
            r5 = "EchoServer";
            r6 = "connection broken by IOException";
            android.util.Log.d(r5, r6, r2);	 Catch:{ LlcpException -> 0x00a6 }
            goto L_0x0043;
        L_0x00a6:
            r2 = move-exception;
            r5 = "EchoServer";
            r6 = "llcp error";
            android.util.Log.e(r5, r6, r2);	 Catch:{ all -> 0x00c1 }
            r5 = r8.echoMachine;
            r5.shutdown();
            r5 = r8.socket;
            if (r5 == 0) goto L_0x0037;
        L_0x00b7:
            r5 = r8.socket;	 Catch:{ IOException -> 0x00be }
            r5.close();	 Catch:{ IOException -> 0x00be }
            goto L_0x0037;
        L_0x00be:
            r5 = move-exception;
            goto L_0x0037;
        L_0x00c1:
            r5 = move-exception;
            r6 = r8.echoMachine;
            r6.shutdown();
            r6 = r8.socket;
            if (r6 == 0) goto L_0x00d0;
        L_0x00cb:
            r6 = r8.socket;	 Catch:{ IOException -> 0x00d1 }
            r6.close();	 Catch:{ IOException -> 0x00d1 }
        L_0x00d0:
            throw r5;
        L_0x00d1:
            r6 = move-exception;
            goto L_0x00d0;
        L_0x00d3:
            r5 = move-exception;
            goto L_0x0037;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.echoserver.EchoServer.ConnectionlessServerThread.run():void");
        }

        public void shutdown() {
            this.mRunning = EchoServer.DBG;
        }

        public void write(byte[] data) {
            try {
                this.socket.send(this.mRemoteSap, data);
            } catch (IOException e) {
                if (EchoServer.DBG) {
                    Log.d(EchoServer.TAG, "Error writing data.");
                }
            }
        }
    }

    static class EchoMachine implements Callback {
        static final int ECHO_DELAY_IN_MS = 2000;
        static final int ECHO_MIU = 128;
        static final int QUEUE_SIZE = 2;
        final WriteCallback callback;
        final BlockingQueue<byte[]> dataQueue;
        final boolean dumpWhenFull;
        final Handler handler;
        boolean shutdown;

        EchoMachine(WriteCallback callback, boolean dumpWhenFull) {
            this.shutdown = EchoServer.DBG;
            this.callback = callback;
            this.dumpWhenFull = dumpWhenFull;
            this.dataQueue = new LinkedBlockingQueue(QUEUE_SIZE);
            this.handler = new Handler(this);
        }

        public void pushUnit(byte[] unit, int size) {
            if (!this.dumpWhenFull || this.dataQueue.remainingCapacity() != 0) {
                int sizeLeft = size;
                int offset = 0;
                try {
                    if (this.dataQueue.isEmpty()) {
                        this.handler.sendMessageDelayed(this.handler.obtainMessage(), 2000);
                    }
                    if (sizeLeft == 0) {
                        this.dataQueue.put(new byte[0]);
                    }
                    while (sizeLeft > 0) {
                        int minSize = Math.min(size, ECHO_MIU);
                        byte[] data = new byte[minSize];
                        System.arraycopy(unit, offset, data, 0, minSize);
                        this.dataQueue.put(data);
                        sizeLeft -= minSize;
                        offset += minSize;
                    }
                } catch (InterruptedException e) {
                }
            } else if (EchoServer.DBG) {
                Log.d(EchoServer.TAG, "Dumping data unit");
            }
        }

        public synchronized void shutdown() {
            this.dataQueue.clear();
            this.shutdown = true;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public synchronized boolean handleMessage(android.os.Message r4) {
            /*
            r3 = this;
            r2 = 1;
            monitor-enter(r3);
            r0 = r3.shutdown;	 Catch:{ all -> 0x001e }
            if (r0 == 0) goto L_0x0008;
        L_0x0006:
            monitor-exit(r3);
            return r2;
        L_0x0008:
            r0 = r3.dataQueue;	 Catch:{ all -> 0x001e }
            r0 = r0.isEmpty();	 Catch:{ all -> 0x001e }
            if (r0 != 0) goto L_0x0006;
        L_0x0010:
            r1 = r3.callback;	 Catch:{ all -> 0x001e }
            r0 = r3.dataQueue;	 Catch:{ all -> 0x001e }
            r0 = r0.remove();	 Catch:{ all -> 0x001e }
            r0 = (byte[]) r0;	 Catch:{ all -> 0x001e }
            r1.write(r0);	 Catch:{ all -> 0x001e }
            goto L_0x0008;
        L_0x001e:
            r0 = move-exception;
            monitor-exit(r3);
            throw r0;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.echoserver.EchoServer.EchoMachine.handleMessage(android.os.Message):boolean");
        }
    }

    public class ServerThread extends Thread implements WriteCallback {
        LlcpSocket clientSocket;
        final EchoMachine echoMachine;
        boolean running;
        LlcpServerSocket serverSocket;

        public ServerThread() {
            this.running = true;
            this.echoMachine = new EchoMachine(this, EchoServer.DBG);
        }

        private void handleClient(LlcpSocket socket) {
            boolean connectionBroken = EchoServer.DBG;
            byte[] dataUnit = new byte[Device.AUDIO_VIDEO_UNCATEGORIZED];
            while (!connectionBroken) {
                try {
                    int size = socket.receive(dataUnit);
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "read " + size + " bytes");
                    }
                    if (size >= 0) {
                        this.echoMachine.pushUnit(dataUnit, size);
                    } else {
                        return;
                    }
                } catch (IOException e) {
                    connectionBroken = true;
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "connection broken by IOException", e);
                    }
                }
            }
        }

        public void run() {
            if (EchoServer.DBG) {
                Log.d(EchoServer.TAG, "about create LLCP service socket");
            }
            try {
                this.serverSocket = EchoServer.this.mService.createLlcpServerSocket(EchoServer.DEFAULT_CO_SAP, EchoServer.CONNECTION_SERVICE_NAME, EchoServer.MIU, 1, Device.AUDIO_VIDEO_UNCATEGORIZED);
                if (this.serverSocket != null) {
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "created LLCP service socket");
                    }
                    while (this.running) {
                        try {
                            if (EchoServer.DBG) {
                                Log.d(EchoServer.TAG, "about to accept");
                            }
                            this.clientSocket = this.serverSocket.accept();
                            if (EchoServer.DBG) {
                                Log.d(EchoServer.TAG, "accept returned " + this.clientSocket);
                            }
                            handleClient(this.clientSocket);
                        } catch (LlcpException e) {
                            Log.e(EchoServer.TAG, "llcp error", e);
                            this.running = EchoServer.DBG;
                        } catch (IOException e2) {
                            Log.e(EchoServer.TAG, "IO error", e2);
                            this.running = EchoServer.DBG;
                        }
                    }
                    this.echoMachine.shutdown();
                    try {
                        this.clientSocket.close();
                    } catch (IOException e3) {
                    }
                    this.clientSocket = null;
                    try {
                        this.serverSocket.close();
                    } catch (IOException e4) {
                    }
                    this.serverSocket = null;
                } else if (EchoServer.DBG) {
                    Log.d(EchoServer.TAG, "failed to create LLCP service socket");
                }
            } catch (LlcpException e5) {
            }
        }

        public void write(byte[] data) {
            if (this.clientSocket != null) {
                try {
                    this.clientSocket.send(data);
                    Log.e(EchoServer.TAG, "Send success!");
                } catch (IOException e) {
                    Log.e(EchoServer.TAG, "Send failed.");
                }
            }
        }

        public void shutdown() {
            this.running = EchoServer.DBG;
            if (this.serverSocket != null) {
                try {
                    this.serverSocket.close();
                } catch (IOException e) {
                }
                this.serverSocket = null;
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

    public EchoServer() {
        this.mService = NfcService.getInstance();
    }

    public void onLlcpActivated() {
        synchronized (this) {
            if (this.mConnectionlessServerThread == null) {
                this.mConnectionlessServerThread = new ConnectionlessServerThread();
                this.mConnectionlessServerThread.start();
            }
        }
    }

    public void onLlcpDeactivated() {
        synchronized (this) {
            if (this.mConnectionlessServerThread != null) {
                this.mConnectionlessServerThread.shutdown();
                this.mConnectionlessServerThread = null;
            }
        }
    }

    public void start() {
        synchronized (this) {
            if (this.mServerThread == null) {
                this.mServerThread = new ServerThread();
                this.mServerThread.start();
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (this.mServerThread != null) {
                this.mServerThread.shutdown();
                this.mServerThread = null;
            }
        }
    }
}
