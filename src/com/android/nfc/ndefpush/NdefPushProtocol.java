package com.android.nfc.ndefpush;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.handover.HandoverService.Device;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NdefPushProtocol {
    public static final byte ACTION_BACKGROUND = (byte) 2;
    public static final byte ACTION_IMMEDIATE = (byte) 1;
    private static final String TAG = "NdefMessageSet";
    private static final byte VERSION = (byte) 1;
    private byte[] mActions;
    private NdefMessage[] mMessages;
    private int mNumMessages;

    public NdefPushProtocol(NdefMessage msg, byte action) {
        this.mNumMessages = 1;
        this.mActions = new byte[1];
        this.mActions[0] = action;
        this.mMessages = new NdefMessage[1];
        this.mMessages[0] = msg;
    }

    public NdefPushProtocol(byte[] actions, NdefMessage[] messages) {
        if (actions.length != messages.length || actions.length == 0) {
            throw new IllegalArgumentException("actions and messages must be the same size and non-empty");
        }
        int numMessages = actions.length;
        this.mActions = new byte[numMessages];
        System.arraycopy(actions, 0, this.mActions, 0, numMessages);
        this.mMessages = new NdefMessage[numMessages];
        System.arraycopy(messages, 0, this.mMessages, 0, numMessages);
        this.mNumMessages = numMessages;
    }

    public NdefPushProtocol(byte[] data) throws FormatException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
        try {
            byte version = input.readByte();
            if (version != VERSION) {
                Log.w(TAG, "Got version " + version + ",  expected " + 1);
                throw new FormatException("Got version " + version + ",  expected " + 1);
            }
            try {
                this.mNumMessages = input.readInt();
                if (this.mNumMessages == 0) {
                    Log.w(TAG, "No NdefMessage inside NdefMessageSet packet");
                    throw new FormatException("Error while parsing NdefMessageSet");
                }
                this.mActions = new byte[this.mNumMessages];
                this.mMessages = new NdefMessage[this.mNumMessages];
                int i = 0;
                while (i < this.mNumMessages) {
                    try {
                        this.mActions[i] = input.readByte();
                        try {
                            int length = input.readInt();
                            byte[] bytes = new byte[length];
                            try {
                                int lengthRead = input.read(bytes);
                                if (length != lengthRead) {
                                    Log.w(TAG, "Read " + lengthRead + " bytes but expected " + length);
                                    throw new FormatException("Error while parsing NdefMessageSet");
                                }
                                try {
                                    this.mMessages[i] = new NdefMessage(bytes);
                                    i++;
                                } catch (FormatException e) {
                                    throw e;
                                }
                            } catch (IOException e2) {
                                Log.w(TAG, "Unable to read bytes for message " + i);
                                throw new FormatException("Error while parsing NdefMessageSet");
                            }
                        } catch (IOException e3) {
                            Log.w(TAG, "Unable to read length for message " + i);
                            throw new FormatException("Error while parsing NdefMessageSet");
                        }
                    } catch (IOException e4) {
                        Log.w(TAG, "Unable to read action for message " + i);
                        throw new FormatException("Error while parsing NdefMessageSet");
                    }
                }
            } catch (IOException e5) {
                Log.w(TAG, "Unable to read numMessages");
                throw new FormatException("Error while parsing NdefMessageSet");
            }
        } catch (IOException e6) {
            Log.w(TAG, "Unable to read version");
            throw new FormatException("Unable to read version");
        }
    }

    public NdefMessage getImmediate() {
        for (int i = 0; i < this.mNumMessages; i++) {
            if (this.mActions[i] == 1) {
                return this.mMessages[i];
            }
        }
        return null;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Device.AUDIO_VIDEO_UNCATEGORIZED);
        DataOutputStream output = new DataOutputStream(buffer);
        try {
            output.writeByte(1);
            output.writeInt(this.mNumMessages);
            for (int i = 0; i < this.mNumMessages; i++) {
                output.writeByte(this.mActions[i]);
                byte[] bytes = this.mMessages[i].toByteArray();
                output.writeInt(bytes.length);
                output.write(bytes);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
