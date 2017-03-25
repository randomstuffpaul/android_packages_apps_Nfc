package com.android.nfc;

import android.nfc.NdefMessage;
import android.os.Bundle;
import java.io.IOException;

public interface DeviceHost {

    public interface DeviceHostListener {
        void onAidRoutingTableFull();

        void onCardEmulationAidSelected(byte[] bArr, byte[] bArr2);

        void onCardEmulationAidSelected(byte[] bArr, byte[] bArr2, int i);

        void onCardEmulationAidSelected4Google(byte[] bArr);

        void onCardEmulationDeselected();

        void onConnectivityEvent();

        void onConnectivityEvent(int i);

        void onHciEvtTransaction(byte[][] bArr);

        void onHostCardEmulationActivated();

        void onHostCardEmulationData(byte[] bArr);

        void onHostCardEmulationDeactivated();

        void onLlcpFirstPacketReceived(NfcDepEndpoint nfcDepEndpoint);

        void onLlcpLinkActivated(NfcDepEndpoint nfcDepEndpoint);

        void onLlcpLinkDeactivated(NfcDepEndpoint nfcDepEndpoint);

        void onRemoteEndpointDiscovered(TagEndpoint tagEndpoint);

        void onRemoteFieldActivated();

        void onRemoteFieldDeactivated();

        void onSeApduReceived(byte[] bArr);

        void onSeEmvCardRemoval();

        void onSeListenActivated();

        void onSeListenDeactivated();

        void onSeMifareAccess(byte[] bArr);

        void onUartAbnormal();
    }

    public interface LlcpConnectionlessSocket {
        void close() throws IOException;

        int getLinkMiu();

        int getSap();

        LlcpPacket receive() throws IOException;

        void send(int i, byte[] bArr) throws IOException;
    }

    public interface LlcpServerSocket {
        LlcpSocket accept() throws IOException, LlcpException;

        void close() throws IOException;
    }

    public interface LlcpSocket {
        void close() throws IOException;

        void connectToSap(int i) throws IOException;

        void connectToService(String str) throws IOException;

        int getLocalMiu();

        int getLocalRw();

        int getLocalSap();

        int getRemoteMiu();

        int getRemoteRw();

        int receive(byte[] bArr) throws IOException;

        void send(byte[] bArr) throws IOException;
    }

    public interface NfcDepEndpoint {
        public static final short MODE_INVALID = (short) 255;
        public static final short MODE_P2P_INITIATOR = (short) 1;
        public static final short MODE_P2P_TARGET = (short) 0;

        boolean connect();

        boolean disconnect();

        byte[] getGeneralBytes();

        int getHandle();

        int getMode();

        byte[] receive();

        boolean send(byte[] bArr);

        byte[] transceive(byte[] bArr);
    }

    public interface NfceeEndpoint {
    }

    public interface TagEndpoint {
        boolean checkNdef(int[] iArr);

        boolean connect(int i);

        boolean disconnect();

        NdefMessage findAndReadNdef();

        boolean formatNdef(byte[] bArr);

        int getConnectedTechnology();

        int getHandle();

        Bundle[] getTechExtras();

        int[] getTechList();

        byte[] getUid();

        boolean isNdefFormatable();

        boolean isPresent();

        boolean makeReadOnly();

        boolean presenceCheck();

        byte[] readNdef();

        boolean reconnect();

        void removeTechnology(int i);

        void startPresenceChecking(int i);

        byte[] transceive(byte[] bArr, boolean z, int[] iArr);

        boolean writeNdef(byte[] bArr);
    }

    int GetDefaultSE();

    int JCOSDownload();

    int SWPSelfTest(int i);

    boolean SetFilterTag(int i);

    boolean canMakeReadOnly(int i);

    void checkFirmware();

    boolean clearAidTable();

    void clearRouting();

    void commitRouting();

    LlcpConnectionlessSocket createLlcpConnectionlessSocket(int i, String str) throws LlcpException;

    LlcpServerSocket createLlcpServerSocket(int i, String str, int i2, int i3, int i4) throws LlcpException;

    LlcpSocket createLlcpSocket(int i, int i2, int i3, int i4) throws LlcpException;

    boolean deinitialize();

    void disableDiscovery();

    boolean disableReaderMode();

    void disableRoutingToHost();

    void doAbort();

    boolean doActivateLlcp();

    boolean doCheckLlcp();

    void doDeselectSecureElement(int i);

    int[] doGetSecureElementList();

    int doGetSecureElementTechList();

    void doPrbsOff();

    void doPrbsOn(int i, int i2);

    void doSelectSecureElement(int i);

    void doSetEEPROM(byte[] bArr);

    void doSetSEPowerOffState(int i, boolean z);

    void doSetScreenState(int i);

    void doSetSecureElementListenTechMask(int i);

    void doSetVenConfigValue(int i);

    String dump();

    void enableDiscovery();

    boolean enablePN544Quirks();

    boolean enableReaderMode(int i);

    void enableRoutingToHost();

    void enableTech_A(boolean z);

    int getAidTableSize();

    int getChipVer();

    int getDefaultLlcpMiu();

    int getDefaultLlcpRwSize();

    boolean getExtendedLengthApdusSupported();

    int getFWVersion();

    int getMaxTransceiveLength(int i);

    String getName();

    int getTimeout(int i);

    byte[][] getWipeApdus();

    boolean initialize(boolean z);

    boolean onPpseRouted(boolean z, int i);

    boolean reRouteAid(byte[] bArr, int i, boolean z, boolean z2);

    void removeHceOffHostAidRoute(byte[] bArr);

    void resetTimeouts();

    void routToSecureElement(int i);

    boolean routeAid(byte[] bArr, int i, int i2);

    boolean sendRawFrame(byte[] bArr);

    boolean setDefaultAidRoute(int i);

    void setDefaultProtoRoute(int i, int i2, int i3);

    boolean setDefaultRoute(int i, int i2, int i3);

    void setDefaultRouteDestinations(int i, int i2);

    void setDefaultTechRoute(int i, int i2, int i3);

    void setHceOffHostAidRoute(byte[] bArr, boolean z, boolean z2, boolean z3, int i, boolean z4, boolean z5, boolean z6);

    void setP2pInitiatorModes(int i);

    void setP2pTargetModes(int i);

    void setStaticRouteByProto(int i, boolean z, boolean z2, boolean z3, int i2, boolean z4, boolean z5, boolean z6);

    void setStaticRouteByTech(int i, boolean z, boolean z2, boolean z3, int i2, boolean z4, boolean z5, boolean z6);

    boolean setTimeout(int i, int i2);

    void setUiccIdleTime(int i);

    boolean unrouteAid(byte[] bArr);
}
