package com.android.nfc.snep;

import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.handover.HandoverService.Device;
import java.io.IOException;

public final class SnepClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG;
    private static final int DEFAULT_ACCEPTABLE_LENGTH = 102400;
    private static final int DEFAULT_MIU = 128;
    private static final int DEFAULT_RWSIZE = 1;
    private static final int DISCONNECTED = 0;
    private static final String TAG = "SnepClient";
    private final int mAcceptableLength;
    private final int mFragmentLength;
    SnepMessenger mMessenger;
    private final int mMiu;
    private final int mPort;
    private final int mRwSize;
    private final String mServiceName;
    private int mState;
    private final Object mTransmissionLock;

    static {
        DBG = SnepMessenger.DBG;
    }

    public SnepClient() {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = DISCONNECTED;
        this.mServiceName = SnepServer.DEFAULT_SERVICE_NAME;
        this.mPort = 4;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        if ("NXP_PN544C3".equals("CXD2235BGG")) {
            this.mFragmentLength = DEFAULT_MIU;
        } else {
            this.mFragmentLength = -1;
        }
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RWSIZE;
    }

    public SnepClient(String serviceName) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = DISCONNECTED;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = -1;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RWSIZE;
    }

    public SnepClient(int miu, int rwSize) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = DISCONNECTED;
        this.mServiceName = SnepServer.DEFAULT_SERVICE_NAME;
        this.mPort = 4;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        if ("NXP_PN544C3".equals("CXD2235BGG")) {
            this.mFragmentLength = DEFAULT_MIU;
        } else {
            this.mFragmentLength = -1;
        }
        this.mMiu = miu;
        this.mRwSize = rwSize;
    }

    SnepClient(String serviceName, int fragmentLength) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = DISCONNECTED;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = fragmentLength;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RWSIZE;
    }

    SnepClient(String serviceName, int acceptableLength, int fragmentLength) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = DISCONNECTED;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = acceptableLength;
        this.mFragmentLength = fragmentLength;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = DEFAULT_RWSIZE;
    }

    public void put(NdefMessage msg) throws IOException {
        synchronized (this) {
            if (this.mState != CONNECTED) {
                throw new IOException("Socket not connected.");
            }
            SnepMessenger messenger = this.mMessenger;
        }
        synchronized (this.mTransmissionLock) {
            try {
                messenger.sendMessage(SnepMessage.getPutRequest(msg));
                messenger.getMessage();
            } catch (SnepException e) {
                throw new IOException(e);
            }
        }
    }

    public SnepMessage get(NdefMessage msg) throws IOException {
        SnepMessage message;
        synchronized (this) {
            if (this.mState != CONNECTED) {
                throw new IOException("Socket not connected.");
            }
            SnepMessenger messenger = this.mMessenger;
        }
        synchronized (this.mTransmissionLock) {
            try {
                messenger.sendMessage(SnepMessage.getGetRequest(this.mAcceptableLength, msg));
                message = messenger.getMessage();
            } catch (SnepException e) {
                throw new IOException(e);
            }
        }
        return message;
    }

    public void connect() throws IOException {
        synchronized (this) {
            if (this.mState != 0) {
                throw new IOException("Socket already in use.");
            }
            this.mState = DEFAULT_RWSIZE;
        }
        LlcpSocket socket = null;
        try {
            if (DBG) {
                Log.d(TAG, "about to create socket");
            }
            socket = NfcService.getInstance().createLlcpSocket(DISCONNECTED, this.mMiu, this.mRwSize, Device.AUDIO_VIDEO_UNCATEGORIZED);
            if (socket == null) {
                throw new IOException("Could not connect to socket.");
            }
            if (this.mPort == -1) {
                if (DBG) {
                    Log.d(TAG, "about to connect to service " + this.mServiceName);
                }
                socket.connectToService(this.mServiceName);
            } else {
                if (DBG) {
                    Log.d(TAG, "about to connect to port " + this.mPort);
                }
                socket.connectToSap(this.mPort);
            }
            int miu = socket.getRemoteMiu();
            SnepMessenger messenger = new SnepMessenger(true, socket, this.mFragmentLength == -1 ? miu : Math.min(miu, this.mFragmentLength));
            synchronized (this) {
                this.mMessenger = messenger;
                this.mState = CONNECTED;
            }
        } catch (LlcpException e) {
            synchronized (this) {
            }
            this.mState = DISCONNECTED;
            throw new IOException("Could not connect to socket");
        } catch (IOException e2) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e3) {
                }
            }
            synchronized (this) {
            }
            this.mState = DISCONNECTED;
            throw new IOException("Failed to connect to socket");
        }
    }

    public void close() {
        synchronized (this) {
            if (this.mMessenger != null) {
                try {
                    SnepMessenger snepMessenger = this.mMessenger;
                    snepMessenger.close();
                    this.mMessenger = snepMessenger;
                    this.mState = DISCONNECTED;
                } catch (IOException e) {
                    snepMessenger = e;
                    this.mMessenger = snepMessenger;
                    this.mState = DISCONNECTED;
                } finally {
                    this.mMessenger = null;
                    this.mState = DISCONNECTED;
                }
            }
        }
    }
}
