package com.android.nfc.snep;

import android.nfc.FormatException;
import android.os.Debug;
import android.util.Log;
import com.android.nfc.DeviceHost.LlcpSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

public class SnepMessenger {
    static final boolean DBG;
    private static final int HEADER_LENGTH = 6;
    private static final String TAG = "SnepMessager";
    final int mFragmentLength;
    final boolean mIsClient;
    final LlcpSocket mSocket;

    static {
        boolean z = true;
        if (Debug.isProductShip() == 1) {
            z = DBG;
        }
        DBG = z;
    }

    public SnepMessenger(boolean isClient, LlcpSocket socket, int fragmentLength) {
        this.mSocket = socket;
        this.mFragmentLength = fragmentLength;
        this.mIsClient = isClient;
    }

    public void sendMessage(SnepMessage msg) throws IOException {
        byte remoteContinue;
        byte[] buffer = msg.toByteArray();
        if (this.mIsClient) {
            remoteContinue = SnepMessage.RESPONSE_CONTINUE;
        } else {
            remoteContinue = (byte) 0;
        }
        if (DBG) {
            Log.d(TAG, "about to send a " + buffer.length + " byte message");
        }
        int length = Math.min(buffer.length, this.mFragmentLength);
        byte[] tmpBuffer = Arrays.copyOfRange(buffer, 0, length);
        if (DBG) {
            Log.d(TAG, "about to send a " + length + " byte fragment");
        }
        this.mSocket.send(tmpBuffer);
        if (length != buffer.length) {
            int offset = length;
            byte[] responseBytes = new byte[HEADER_LENGTH];
            this.mSocket.receive(responseBytes);
            try {
                SnepMessage snepResponse = SnepMessage.fromByteArray(responseBytes);
                if (DBG) {
                    Log.d(TAG, "Got response from first fragment: " + snepResponse.getField());
                }
                if (snepResponse.getField() != remoteContinue) {
                    throw new IOException("Invalid response from server (" + snepResponse.getField() + ")");
                }
                while (offset < buffer.length) {
                    length = Math.min(buffer.length - offset, this.mFragmentLength);
                    tmpBuffer = Arrays.copyOfRange(buffer, offset, offset + length);
                    if (DBG) {
                        Log.d(TAG, "about to send a " + length + " byte fragment");
                    }
                    this.mSocket.send(tmpBuffer);
                    offset += length;
                }
            } catch (FormatException e) {
                throw new IOException("Invalid SNEP message", e);
            }
        }
    }

    public SnepMessage getMessage() throws IOException, SnepException {
        byte fieldContinue;
        byte fieldReject;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(this.mFragmentLength);
        byte[] partial = new byte[this.mFragmentLength];
        boolean doneReading = DBG;
        if (this.mIsClient) {
            fieldContinue = (byte) 0;
            fieldReject = (byte) 7;
        } else {
            fieldContinue = SnepMessage.RESPONSE_CONTINUE;
            fieldReject = (byte) -1;
        }
        int size = this.mSocket.receive(partial);
        if (DBG) {
            Log.d(TAG, "read " + size + " bytes");
        }
        if (size < 0) {
            try {
                this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
            } catch (IOException e) {
            }
            throw new IOException("Error reading SNEP message.");
        } else if (size < HEADER_LENGTH) {
            try {
                this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
            } catch (IOException e2) {
            }
            throw new IOException("Invalid fragment from sender.");
        } else {
            int readSize = size - 6;
            buffer.write(partial, 0, size);
            DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(partial));
            byte requestVersion = dataIn.readByte();
            byte requestField = dataIn.readByte();
            int requestSize = dataIn.readInt();
            if (DBG) {
                Log.d(TAG, "read " + readSize + " of " + requestSize);
            }
            if (((requestVersion & 240) >> 4) != 1) {
                return new SnepMessage(requestVersion, requestField, 0, 0, null);
            }
            if (requestSize > readSize) {
                if (DBG) {
                    Log.d(TAG, "requesting continuation");
                }
                this.mSocket.send(SnepMessage.getMessage(fieldContinue).toByteArray());
            } else {
                doneReading = true;
            }
            while (!doneReading) {
                try {
                    size = this.mSocket.receive(partial);
                    if (DBG) {
                        Log.d(TAG, "read " + size + " bytes");
                    }
                    if (size < 0) {
                        try {
                            this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
                        } catch (IOException e3) {
                        }
                        throw new IOException();
                    }
                    readSize += size;
                    buffer.write(partial, 0, size);
                    if (readSize == requestSize) {
                        doneReading = true;
                    }
                } catch (IOException e4) {
                    try {
                        this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
                    } catch (IOException e5) {
                    }
                    throw e4;
                }
            }
            try {
                return SnepMessage.fromByteArray(buffer.toByteArray());
            } catch (Exception e6) {
                Log.e(TAG, "Badly formatted NDEF message, ignoring", e6);
                throw new SnepException(e6);
            }
        }
    }

    public void close() throws IOException {
        this.mSocket.close();
    }
}
