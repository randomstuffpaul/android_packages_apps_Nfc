package com.android.nfc;

/* compiled from: P2pLinkManager */
interface P2pEventListener {

    /* compiled from: P2pLinkManager */
    public interface Callback {
        void onP2pSendConfirmed();
    }

    void onP2pHandoverNotSupported();

    void onP2pInRange();

    void onP2pOutOfRange();

    void onP2pReceiveComplete(boolean z);

    void onP2pResumeSend();

    void onP2pSendComplete();

    void onP2pSendConfirmationRequested();

    void onP2pSendDebounce();
}
