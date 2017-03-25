package com.android.nfc.snep;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class SnepMessage {
    private static final int HEADER_LENGTH = 6;
    public static final byte REQUEST_CONTINUE = (byte) 0;
    public static final byte REQUEST_GET = (byte) 1;
    public static final byte REQUEST_PUT = (byte) 2;
    public static final byte REQUEST_REJECT = (byte) 7;
    public static final byte RESPONSE_BAD_REQUEST = (byte) -62;
    public static final byte RESPONSE_CONTINUE = Byte.MIN_VALUE;
    public static final byte RESPONSE_EXCESS_DATA = (byte) -63;
    public static final byte RESPONSE_NOT_FOUND = (byte) -64;
    public static final byte RESPONSE_NOT_IMPLEMENTED = (byte) -32;
    public static final byte RESPONSE_REJECT = (byte) -1;
    public static final byte RESPONSE_SUCCESS = (byte) -127;
    public static final byte RESPONSE_UNSUPPORTED_VERSION = (byte) -31;
    public static final byte VERSION = (byte) 16;
    public static final byte VERSION_MAJOR = (byte) 1;
    public static final byte VERSION_MINOR = (byte) 0;
    private final int mAcceptableLength;
    private final byte mField;
    private final int mLength;
    private final NdefMessage mNdefMessage;
    private final byte mVersion;

    public static SnepMessage getGetRequest(int acceptableLength, NdefMessage ndef) {
        return new SnepMessage(VERSION, VERSION_MAJOR, ndef.toByteArray().length + 4, acceptableLength, ndef);
    }

    public static SnepMessage getPutRequest(NdefMessage ndef) {
        return new SnepMessage(VERSION, REQUEST_PUT, ndef.toByteArray().length, 0, ndef);
    }

    public static SnepMessage getMessage(byte field) {
        return new SnepMessage(VERSION, field, 0, 0, null);
    }

    public static SnepMessage getSuccessResponse(NdefMessage ndef) {
        if (ndef == null) {
            return new SnepMessage(VERSION, RESPONSE_SUCCESS, 0, 0, null);
        }
        return new SnepMessage(VERSION, RESPONSE_SUCCESS, ndef.toByteArray().length, 0, ndef);
    }

    public static SnepMessage fromByteArray(byte[] data) throws FormatException {
        return new SnepMessage(data);
    }

    private SnepMessage(byte[] data) throws FormatException {
        int ndefOffset;
        int ndefLength;
        ByteBuffer input = ByteBuffer.wrap(data);
        this.mVersion = input.get();
        this.mField = input.get();
        this.mLength = input.getInt();
        if (this.mField == 1) {
            this.mAcceptableLength = input.getInt();
            ndefOffset = 10;
            ndefLength = this.mLength - 4;
        } else {
            this.mAcceptableLength = -1;
            ndefOffset = HEADER_LENGTH;
            ndefLength = this.mLength;
        }
        if (ndefLength > 0) {
            byte[] bytes = new byte[ndefLength];
            System.arraycopy(data, ndefOffset, bytes, 0, ndefLength);
            this.mNdefMessage = new NdefMessage(bytes);
            return;
        }
        this.mNdefMessage = null;
    }

    SnepMessage(byte version, byte field, int length, int acceptableLength, NdefMessage ndefMessage) {
        this.mVersion = version;
        this.mField = field;
        this.mLength = length;
        this.mAcceptableLength = acceptableLength;
        this.mNdefMessage = ndefMessage;
    }

    public byte[] toByteArray() {
        byte[] bytes;
        if (this.mNdefMessage != null) {
            bytes = this.mNdefMessage.toByteArray();
        } else {
            bytes = new byte[0];
        }
        try {
            ByteArrayOutputStream buffer;
            if (this.mField == VERSION_MAJOR) {
                buffer = new ByteArrayOutputStream((bytes.length + HEADER_LENGTH) + 4);
            } else {
                buffer = new ByteArrayOutputStream(bytes.length + HEADER_LENGTH);
            }
            DataOutputStream output = new DataOutputStream(buffer);
            output.writeByte(this.mVersion);
            output.writeByte(this.mField);
            if (this.mField == VERSION_MAJOR) {
                output.writeInt(bytes.length + 4);
                output.writeInt(this.mAcceptableLength);
            } else {
                output.writeInt(bytes.length);
            }
            output.write(bytes);
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public NdefMessage getNdefMessage() {
        return this.mNdefMessage;
    }

    public byte getField() {
        return this.mField;
    }

    public byte getVersion() {
        return this.mVersion;
    }

    public int getLength() {
        return this.mLength;
    }

    public int getAcceptableLength() {
        if (this.mField == 1) {
            return this.mAcceptableLength;
        }
        throw new UnsupportedOperationException("Acceptable Length only available on get request messages.");
    }
}
