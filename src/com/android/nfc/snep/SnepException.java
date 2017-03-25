package com.android.nfc.snep;

public class SnepException extends Exception {
    public SnepException(String message) {
        super(message);
    }

    public SnepException(Exception cause) {
        super(cause);
    }

    public SnepException(String message, Exception cause) {
        super(message, cause);
    }
}
