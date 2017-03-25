package com.android.nfc;

import android.util.EventLog;

public class EventLogTags {
    public static final int NFC_FIRST_SHARE = 90000;
    public static final int NFC_NDEF_RECEIVED = 90003;
    public static final int NFC_SHARE = 90001;
    public static final int NFC_SHARE_FAIL = 90002;

    private EventLogTags() {
    }

    public static void writeNfcFirstShare() {
        EventLog.writeEvent(NFC_FIRST_SHARE, new Object[0]);
    }

    public static void writeNfcShare(int size, int tnf, String type, int aarPresent, int duration) {
        EventLog.writeEvent(NFC_SHARE, new Object[]{Integer.valueOf(size), Integer.valueOf(tnf), type, Integer.valueOf(aarPresent), Integer.valueOf(duration)});
    }

    public static void writeNfcShareFail(int size, int tnf, String type, int aarPresent) {
        EventLog.writeEvent(NFC_SHARE_FAIL, new Object[]{Integer.valueOf(size), Integer.valueOf(tnf), type, Integer.valueOf(aarPresent)});
    }

    public static void writeNfcNdefReceived(int size, int tnf, String type, int aarPresent) {
        EventLog.writeEvent(NFC_NDEF_RECEIVED, new Object[]{Integer.valueOf(size), Integer.valueOf(tnf), type, Integer.valueOf(aarPresent)});
    }
}
