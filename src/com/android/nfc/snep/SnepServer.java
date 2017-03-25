package com.android.nfc.snep;

import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;

public final class SnepServer {
    private static final boolean DBG;
    private static final int DEFAULT_MIU = 248;
    public static final int DEFAULT_PORT = 4;
    private static final int DEFAULT_RW_SIZE = 1;
    public static final String DEFAULT_SERVICE_NAME = "urn:nfc:sn:snep";
    private static final String TAG = "SnepServer";
    final Callback mCallback;
    final int mFragmentLength;
    final int mMiu;
    final int mRwSize;
    boolean mServerRunning;
    ServerThread mServerThread;
    final String mServiceName;
    final int mServiceSap;

    public interface Callback {
        SnepMessage doGet(int i, NdefMessage ndefMessage);

        SnepMessage doPut(NdefMessage ndefMessage);
    }

    private class ConnectionThread extends Thread {
        private final SnepMessenger mMessager;
        private final LlcpSocket mSock;

        ConnectionThread(LlcpSocket socket, int fragmentLength) {
            super(SnepServer.TAG);
            this.mSock = socket;
            this.mMessager = new SnepMessenger(SnepServer.DBG, socket, fragmentLength);
        }

        public void run() {
            if (SnepServer.DBG) {
                Log.d(SnepServer.TAG, "starting connection thread");
            }
            try {
                boolean running;
                synchronized (SnepServer.this) {
                    running = SnepServer.this.mServerRunning;
                }
                while (running) {
                    if (SnepServer.handleRequest(this.mMessager, SnepServer.this.mCallback)) {
                        synchronized (SnepServer.this) {
                            running = SnepServer.this.mServerRunning;
                        }
                    }
                }
                try {
                    break;
                    if (SnepServer.DBG) {
                        Log.d(SnepServer.TAG, "about to close");
                    }
                    this.mSock.close();
                } catch (IOException e) {
                }
            } catch (IOException e2) {
                try {
                    if (SnepServer.DBG) {
                        Log.e(SnepServer.TAG, "Closing from IOException");
                    }
                } finally {
                    try {
                        if (SnepServer.DBG) {
                            Log.d(SnepServer.TAG, "about to close");
                        }
                        this.mSock.close();
                    } catch (IOException e3) {
                    }
                }
            }
            if (SnepServer.DBG) {
                Log.d(SnepServer.TAG, "finished connection thread");
            }
        }
    }

    class ServerThread extends Thread {
        LlcpServerSocket mServerSocket;
        private boolean mThreadRunning;

        ServerThread() {
            this.mThreadRunning = true;
        }

        public void run() {
            synchronized (SnepServer.this) {
                boolean threadRunning = this.mThreadRunning;
            }
            while (threadRunning) {
                if (SnepServer.DBG) {
                    Log.d(SnepServer.TAG, "about create LLCP service socket");
                }
                try {
                    synchronized (SnepServer.this) {
                        this.mServerSocket = NfcService.getInstance().createLlcpServerSocket(SnepServer.this.mServiceSap, SnepServer.this.mServiceName, SnepServer.this.mMiu, SnepServer.this.mRwSize, Device.AUDIO_VIDEO_UNCATEGORIZED);
                    }
                    if (this.mServerSocket == null) {
                        if (SnepServer.DBG) {
                            Log.d(SnepServer.TAG, "failed to create LLCP service socket");
                        }
                        synchronized (SnepServer.this) {
                            if (this.mServerSocket != null) {
                                if (SnepServer.DBG) {
                                    Log.d(SnepServer.TAG, "about to close");
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
                    if (SnepServer.DBG) {
                        Log.d(SnepServer.TAG, "created LLCP service socket");
                    }
                    synchronized (SnepServer.this) {
                        threadRunning = this.mThreadRunning;
                    }
                    while (threadRunning) {
                        LlcpServerSocket serverSocket;
                        synchronized (SnepServer.this) {
                            serverSocket = this.mServerSocket;
                        }
                        if (serverSocket == null) {
                            if (SnepServer.DBG) {
                                Log.d(SnepServer.TAG, "Server socket shut down.");
                            }
                            synchronized (SnepServer.this) {
                                if (this.mServerSocket != null) {
                                    if (SnepServer.DBG) {
                                        Log.d(SnepServer.TAG, "about to close");
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
                        if (SnepServer.DBG) {
                            Log.d(SnepServer.TAG, "about to accept");
                        }
                        LlcpSocket communicationSocket = serverSocket.accept();
                        if (SnepServer.DBG) {
                            Log.d(SnepServer.TAG, "accept returned " + communicationSocket);
                        }
                        if (communicationSocket != null) {
                            int miu = communicationSocket.getRemoteMiu();
                            new ConnectionThread(communicationSocket, SnepServer.this.mFragmentLength == -1 ? miu : Math.min(miu, SnepServer.this.mFragmentLength)).start();
                        }
                        synchronized (SnepServer.this) {
                            threadRunning = this.mThreadRunning;
                        }
                    }
                    if (SnepServer.DBG) {
                        Log.d(SnepServer.TAG, "stop running");
                    }
                    synchronized (SnepServer.this) {
                        if (this.mServerSocket != null) {
                            if (SnepServer.DBG) {
                                Log.d(SnepServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e3) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    synchronized (SnepServer.this) {
                        threadRunning = this.mThreadRunning;
                    }
                } catch (LlcpException e4) {
                    try {
                        Log.e(SnepServer.TAG, "llcp error", e4);
                        synchronized (SnepServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (SnepServer.DBG) {
                                Log.d(SnepServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e5) {
                            }
                            this.mServerSocket = null;
                        }
                    } catch (Throwable th) {
                        synchronized (SnepServer.this) {
                        }
                        if (this.mServerSocket != null) {
                            if (SnepServer.DBG) {
                                Log.d(SnepServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                } catch (LlcpException e42) {
                    Log.e(SnepServer.TAG, "IO error", e42);
                    synchronized (SnepServer.this) {
                    }
                    if (this.mServerSocket != null) {
                        if (SnepServer.DBG) {
                            Log.d(SnepServer.TAG, "about to close");
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

        public void shutdown() {
            synchronized (SnepServer.this) {
                this.mThreadRunning = SnepServer.DBG;
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
        DBG = SnepMessenger.DBG;
    }

    public SnepServer(Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = DEFAULT_SERVICE_NAME;
        this.mServiceSap = DEFAULT_PORT;
        this.mFragmentLength = -1;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RW_SIZE;
    }

    public SnepServer(String serviceName, int serviceSap, Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = serviceName;
        this.mServiceSap = serviceSap;
        this.mFragmentLength = -1;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RW_SIZE;
    }

    public SnepServer(Callback callback, int miu, int rwSize) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = DEFAULT_SERVICE_NAME;
        this.mServiceSap = DEFAULT_PORT;
        this.mFragmentLength = -1;
        this.mMiu = miu;
        this.mRwSize = rwSize;
    }

    SnepServer(String serviceName, int serviceSap, int fragmentLength, Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = serviceName;
        this.mServiceSap = serviceSap;
        this.mFragmentLength = fragmentLength;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RW_SIZE;
    }

    static boolean handleRequest(SnepMessenger messenger, Callback callback) throws IOException {
        try {
            SnepMessage request = messenger.getMessage();
            if (((request.getVersion() & 240) >> DEFAULT_PORT) != DEFAULT_RW_SIZE) {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_UNSUPPORTED_VERSION));
                return true;
            } else if (request.getField() == (byte) 1) {
                messenger.sendMessage(callback.doGet(request.getAcceptableLength(), request.getNdefMessage()));
                return true;
            } else if (request.getField() == 2) {
                if (DBG) {
                    Log.d(TAG, "putting message " + request.toString());
                }
                messenger.sendMessage(callback.doPut(request.getNdefMessage()));
                return true;
            } else {
                if (DBG) {
                    Log.d(TAG, "Unknown request (" + request.getField() + ")");
                }
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
                return true;
            }
        } catch (SnepException e) {
            if (DBG) {
                Log.w(TAG, "Bad snep message", e);
            }
            try {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
            } catch (IOException e2) {
            }
            return DBG;
        }
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
                this.mServerRunning = true;
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
                this.mServerRunning = DBG;
            }
        }
    }
}
