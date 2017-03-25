package com.android.nfc.ndefpush;

import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;
import java.util.Arrays;

public class NdefPushClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG;
    private static final int DISCONNECTED = 0;
    private static final int MIU = 128;
    private static final String TAG = "NdefPushClient";
    final Object mLock;
    private LlcpSocket mSocket;
    private int mState;

    public NdefPushClient() {
        this.mLock = new Object();
        this.mState = DISCONNECTED;
    }

    static {
        DBG = NdefPushServer.DBG;
    }

    public void connect() throws IOException {
        synchronized (this.mLock) {
            if (this.mState != 0) {
                throw new IOException("Socket still in use.");
            }
            this.mState = CONNECTING;
        }
        NfcService service = NfcService.getInstance();
        if (DBG) {
            Log.d(TAG, "about to create socket");
        }
        try {
            LlcpSocket sock = service.createLlcpSocket(DISCONNECTED, MIU, CONNECTING, Device.AUDIO_VIDEO_UNCATEGORIZED);
            try {
                if (DBG) {
                    Log.d(TAG, "about to connect to service com.android.npp");
                }
                sock.connectToService("com.android.npp");
                synchronized (this.mLock) {
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
                synchronized (this.mLock) {
                }
                this.mState = DISCONNECTED;
                throw new IOException("Could not connect service.");
            }
        } catch (LlcpException e3) {
            synchronized (this.mLock) {
            }
            this.mState = DISCONNECTED;
            throw new IOException("Could not create socket.");
        }
    }

    public boolean push(NdefMessage msg) {
        LlcpSocket sock;
        synchronized (this.mLock) {
            if (this.mState != CONNECTED) {
                Log.e(TAG, "Not connected to NPP.");
                return DBG;
            }
            sock = this.mSocket;
            byte[] buffer = new NdefPushProtocol(msg, (byte) 1).toByteArray();
            int offset = DISCONNECTED;
            try {
                int remoteMiu = sock.getRemoteMiu();
                if (DBG) {
                    Log.d(TAG, "about to send a " + buffer.length + " byte message");
                }
                while (offset < buffer.length) {
                    int length = Math.min(buffer.length - offset, remoteMiu);
                    byte[] tmpBuffer = Arrays.copyOfRange(buffer, offset, offset + length);
                    if (DBG) {
                        Log.d(TAG, "about to send a " + length + " byte packet");
                    }
                    sock.send(tmpBuffer);
                    offset += length;
                }
                if (sock != null) {
                    try {
                        if (DBG) {
                            Log.d(TAG, "about to close");
                        }
                        sock.close();
                    } catch (IOException e) {
                    }
                }
                return true;
            } catch (IOException e2) {
                Log.e(TAG, "couldn't send tag");
                if (DBG) {
                    Log.d(TAG, "exception:", e2);
                }
                if (sock == null) {
                    return DBG;
                }
                try {
                    if (DBG) {
                        Log.d(TAG, "about to close");
                    }
                    sock.close();
                    return DBG;
                } catch (IOException e3) {
                    return DBG;
                }
            } catch (Throwable th) {
                if (sock != null) {
                    try {
                        if (DBG) {
                            Log.d(TAG, "about to close");
                        }
                        sock.close();
                    } catch (IOException e4) {
                    }
                }
            }
        }
    }

    public void close() {
        synchronized (this.mLock) {
            if (this.mSocket != null) {
                try {
                    if (DBG) {
                        Log.d(TAG, "About to close NPP socket.");
                    }
                    this.mSocket.close();
                } catch (IOException e) {
                }
                this.mSocket = null;
            }
            this.mState = DISCONNECTED;
        }
    }
}
