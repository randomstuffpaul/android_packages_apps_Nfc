package com.android.nfc.dhimpl;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import com.android.nfc.DeviceHost.TagEndpoint;

public class NativeNfcTag implements TagEndpoint {
    static final boolean DBG = false;
    static final int STATUS_CODE_TARGET_LOST = 146;
    private final String TAG;
    private int mConnectedHandle;
    private int mConnectedTechIndex;
    private boolean mIsPresent;
    private byte[][] mTechActBytes;
    private Bundle[] mTechExtras;
    private int[] mTechHandles;
    private int[] mTechLibNfcTypes;
    private int[] mTechList;
    private byte[][] mTechPollBytes;
    private byte[] mUid;
    private PresenceCheckWatchdog mWatchdog;

    class PresenceCheckWatchdog extends Thread {
        private boolean doCheck;
        private boolean isPaused;
        private boolean isPresent;
        private boolean isStopped;
        private int watchdogTimeout;

        public PresenceCheckWatchdog(int presenceCheckDelay) {
            this.isPresent = true;
            this.isStopped = NativeNfcTag.DBG;
            this.isPaused = NativeNfcTag.DBG;
            this.doCheck = true;
            this.watchdogTimeout = presenceCheckDelay;
        }

        public synchronized void pause() {
            this.isPaused = true;
            this.doCheck = NativeNfcTag.DBG;
            notifyAll();
        }

        public synchronized void doResume() {
            this.isPaused = NativeNfcTag.DBG;
            this.doCheck = NativeNfcTag.DBG;
            notifyAll();
        }

        public synchronized void end() {
            this.isStopped = true;
            this.doCheck = NativeNfcTag.DBG;
            notifyAll();
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public synchronized void run() {
            /*
            r2 = this;
            monitor-enter(r2);
        L_0x0001:
            r0 = r2.isPresent;	 Catch:{ all -> 0x0039 }
            if (r0 == 0) goto L_0x0025;
        L_0x0005:
            r0 = r2.isStopped;	 Catch:{ all -> 0x0039 }
            if (r0 != 0) goto L_0x0025;
        L_0x0009:
            r0 = r2.isPaused;	 Catch:{ InterruptedException -> 0x0023 }
            if (r0 != 0) goto L_0x0010;
        L_0x000d:
            r0 = 1;
            r2.doCheck = r0;	 Catch:{ InterruptedException -> 0x0023 }
        L_0x0010:
            r0 = r2.watchdogTimeout;	 Catch:{ InterruptedException -> 0x0023 }
            r0 = (long) r0;	 Catch:{ InterruptedException -> 0x0023 }
            r2.wait(r0);	 Catch:{ InterruptedException -> 0x0023 }
            r0 = r2.doCheck;	 Catch:{ InterruptedException -> 0x0023 }
            if (r0 == 0) goto L_0x0001;
        L_0x001a:
            r0 = com.android.nfc.dhimpl.NativeNfcTag.this;	 Catch:{ InterruptedException -> 0x0023 }
            r0 = r0.doPresenceCheck();	 Catch:{ InterruptedException -> 0x0023 }
            r2.isPresent = r0;	 Catch:{ InterruptedException -> 0x0023 }
            goto L_0x0001;
        L_0x0023:
            r0 = move-exception;
            goto L_0x0001;
        L_0x0025:
            r0 = com.android.nfc.dhimpl.NativeNfcTag.this;	 Catch:{ all -> 0x0039 }
            r1 = 0;
            r0.mIsPresent = r1;	 Catch:{ all -> 0x0039 }
            r0 = "NativeNfcTag";
            r1 = "Tag lost, restarting polling loop";
            android.util.Log.d(r0, r1);	 Catch:{ all -> 0x0039 }
            r0 = com.android.nfc.dhimpl.NativeNfcTag.this;	 Catch:{ all -> 0x0039 }
            r0.doDisconnect();	 Catch:{ all -> 0x0039 }
            monitor-exit(r2);
            return;
        L_0x0039:
            r0 = move-exception;
            monitor-exit(r2);
            throw r0;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.dhimpl.NativeNfcTag.PresenceCheckWatchdog.run():void");
        }
    }

    private native int doCheckNdef(int[] iArr);

    private native int doConnect(int i);

    private native byte[] doRead();

    private native byte[] doTransceive(byte[] bArr, boolean z, int[] iArr);

    private native boolean doWrite(byte[] bArr);

    native boolean doDisconnect();

    native int doGetNdefType(int i, int i2);

    native int doHandleReconnect(int i);

    native boolean doIsIsoDepNdefFormatable(byte[] bArr, byte[] bArr2);

    native boolean doMakeReadonly(byte[] bArr);

    native boolean doNdefFormat(byte[] bArr);

    native boolean doPresenceCheck();

    native int doReconnect();

    public NativeNfcTag() {
        this.TAG = "NativeNfcTag";
    }

    public synchronized int connectWithStatus(int technology) {
        int i;
        if (technology == 2) {
            i = -1;
        } else {
            if (this.mWatchdog != null) {
                this.mWatchdog.pause();
            }
            i = -1;
            int i2 = 0;
            while (i2 < this.mTechList.length) {
                if (this.mTechList[i2] == technology) {
                    if (this.mConnectedHandle != this.mTechHandles[i2]) {
                        if (this.mConnectedHandle == -1) {
                            i = doConnect(this.mTechHandles[i2]);
                        } else {
                            i = reconnectWithStatus(this.mTechHandles[i2]);
                        }
                        if (i == 0) {
                            this.mConnectedHandle = this.mTechHandles[i2];
                            this.mConnectedTechIndex = i2;
                        }
                    } else {
                        if (technology == 6 || technology == 7) {
                            i = 0;
                        } else if (technology == 3 || !hasTechOnHandle(3, this.mTechHandles[i2])) {
                            i = 0;
                        } else {
                            i = -1;
                        }
                        if (i == 0) {
                            this.mConnectedTechIndex = i2;
                        }
                    }
                    if (this.mWatchdog != null) {
                        this.mWatchdog.doResume();
                    }
                } else {
                    i2++;
                }
            }
            if (this.mWatchdog != null) {
                this.mWatchdog.doResume();
            }
        }
        return i;
    }

    public synchronized boolean connect(int technology) {
        return connectWithStatus(technology) == 0 ? true : DBG;
    }

    public synchronized void startPresenceChecking(int presenceCheckDelay) {
        this.mIsPresent = true;
        if (this.mConnectedTechIndex == -1 && this.mTechList.length > 0) {
            connect(this.mTechList[0]);
        }
        if (this.mWatchdog == null) {
            this.mWatchdog = new PresenceCheckWatchdog(presenceCheckDelay);
            this.mWatchdog.start();
        }
    }

    public synchronized boolean isPresent() {
        return this.mIsPresent;
    }

    public synchronized boolean disconnect() {
        boolean result;
        this.mIsPresent = DBG;
        if (this.mWatchdog != null) {
            this.mWatchdog.end();
            try {
                this.mWatchdog.join();
            } catch (InterruptedException e) {
            }
            this.mWatchdog = null;
            result = true;
        } else {
            result = doDisconnect();
        }
        this.mConnectedTechIndex = -1;
        this.mConnectedHandle = -1;
        return result;
    }

    public synchronized int reconnectWithStatus() {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doReconnect();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    public synchronized boolean reconnect() {
        return reconnectWithStatus() == 0 ? true : DBG;
    }

    public synchronized int reconnectWithStatus(int handle) {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doHandleReconnect(handle);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    public synchronized byte[] transceive(byte[] data, boolean raw, int[] returnCode) {
        byte[] result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doTransceive(data, raw, returnCode);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    private synchronized int checkNdefWithStatus(int[] ndefinfo) {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doCheckNdef(ndefinfo);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    public synchronized boolean checkNdef(int[] ndefinfo) {
        return checkNdefWithStatus(ndefinfo) == 0 ? true : DBG;
    }

    public synchronized byte[] readNdef() {
        byte[] result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doRead();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    public synchronized boolean writeNdef(byte[] buf) {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doWrite(buf);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    public synchronized boolean presenceCheck() {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doPresenceCheck();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    public synchronized boolean formatNdef(byte[] key) {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doNdefFormat(key);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    public synchronized boolean makeReadOnly() {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        if (hasTech(8)) {
            result = doMakeReadonly(MifareClassic.KEY_DEFAULT);
        } else {
            result = doMakeReadonly(new byte[0]);
        }
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    public synchronized boolean isNdefFormatable() {
        boolean z = true;
        synchronized (this) {
            if (!(hasTech(8) || hasTech(9))) {
                if (hasTech(5)) {
                    if (this.mUid[5] < (byte) 1 || this.mUid[5] > 3 || this.mUid[6] != 4) {
                        z = DBG;
                    }
                } else if (hasTech(3)) {
                    int nfcaTechIndex = getTechIndex(1);
                    z = nfcaTechIndex != -1 ? doIsIsoDepNdefFormatable(this.mTechPollBytes[nfcaTechIndex], this.mTechActBytes[nfcaTechIndex]) : DBG;
                } else {
                    z = DBG;
                }
            }
        }
        return z;
    }

    public int getHandle() {
        if (this.mTechHandles.length > 0) {
            return this.mTechHandles[0];
        }
        return 0;
    }

    public byte[] getUid() {
        return this.mUid;
    }

    public int[] getTechList() {
        return this.mTechList;
    }

    private int getConnectedHandle() {
        return this.mConnectedHandle;
    }

    private int getConnectedLibNfcType() {
        if (this.mConnectedTechIndex == -1 || this.mConnectedTechIndex >= this.mTechLibNfcTypes.length) {
            return 0;
        }
        return this.mTechLibNfcTypes[this.mConnectedTechIndex];
    }

    public int getConnectedTechnology() {
        if (this.mConnectedTechIndex == -1 || this.mConnectedTechIndex >= this.mTechList.length) {
            return 0;
        }
        return this.mTechList[this.mConnectedTechIndex];
    }

    private int getNdefType(int libnfctype, int javatype) {
        return doGetNdefType(libnfctype, javatype);
    }

    private void addTechnology(int tech, int handle, int libnfctype) {
        int[] mNewTechList = new int[(this.mTechList.length + 1)];
        System.arraycopy(this.mTechList, 0, mNewTechList, 0, this.mTechList.length);
        mNewTechList[this.mTechList.length] = tech;
        this.mTechList = mNewTechList;
        int[] mNewHandleList = new int[(this.mTechHandles.length + 1)];
        System.arraycopy(this.mTechHandles, 0, mNewHandleList, 0, this.mTechHandles.length);
        mNewHandleList[this.mTechHandles.length] = handle;
        this.mTechHandles = mNewHandleList;
        int[] mNewTypeList = new int[(this.mTechLibNfcTypes.length + 1)];
        System.arraycopy(this.mTechLibNfcTypes, 0, mNewTypeList, 0, this.mTechLibNfcTypes.length);
        mNewTypeList[this.mTechLibNfcTypes.length] = libnfctype;
        this.mTechLibNfcTypes = mNewTypeList;
    }

    public void removeTechnology(int tech) {
        synchronized (this) {
            int techIndex = getTechIndex(tech);
            if (techIndex != -1) {
                int[] mNewTechList = new int[(this.mTechList.length - 1)];
                System.arraycopy(this.mTechList, 0, mNewTechList, 0, techIndex);
                System.arraycopy(this.mTechList, techIndex + 1, mNewTechList, techIndex, (this.mTechList.length - techIndex) - 1);
                this.mTechList = mNewTechList;
                int[] mNewHandleList = new int[(this.mTechHandles.length - 1)];
                System.arraycopy(this.mTechHandles, 0, mNewHandleList, 0, techIndex);
                System.arraycopy(this.mTechHandles, techIndex + 1, mNewTechList, techIndex, (this.mTechHandles.length - techIndex) - 1);
                this.mTechHandles = mNewHandleList;
                int[] mNewTypeList = new int[(this.mTechLibNfcTypes.length - 1)];
                System.arraycopy(this.mTechLibNfcTypes, 0, mNewTypeList, 0, techIndex);
                System.arraycopy(this.mTechLibNfcTypes, techIndex + 1, mNewTypeList, techIndex, (this.mTechLibNfcTypes.length - techIndex) - 1);
                this.mTechLibNfcTypes = mNewTypeList;
            }
        }
    }

    public void addNdefFormatableTechnology(int handle, int libnfcType) {
        synchronized (this) {
            addTechnology(7, handle, libnfcType);
        }
    }

    public void addNdefTechnology(NdefMessage msg, int handle, int libnfcType, int javaType, int maxLength, int cardState) {
        synchronized (this) {
            addTechnology(6, handle, libnfcType);
            Bundle extras = new Bundle();
            extras.putParcelable("ndefmsg", msg);
            extras.putInt("ndefmaxlength", maxLength);
            extras.putInt("ndefcardstate", cardState);
            extras.putInt("ndeftype", getNdefType(libnfcType, javaType));
            if (this.mTechExtras == null) {
                Bundle[] builtTechExtras = getTechExtras();
                builtTechExtras[builtTechExtras.length - 1] = extras;
            } else {
                Bundle[] oldTechExtras = getTechExtras();
                Bundle[] newTechExtras = new Bundle[(oldTechExtras.length + 1)];
                System.arraycopy(oldTechExtras, 0, newTechExtras, 0, oldTechExtras.length);
                newTechExtras[oldTechExtras.length] = extras;
                this.mTechExtras = newTechExtras;
            }
        }
    }

    private int getTechIndex(int tech) {
        for (int i = 0; i < this.mTechList.length; i++) {
            if (this.mTechList[i] == tech) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasTech(int tech) {
        for (int i : this.mTechList) {
            if (i == tech) {
                return true;
            }
        }
        return DBG;
    }

    private boolean hasTechOnHandle(int tech, int handle) {
        int i = 0;
        while (i < this.mTechList.length) {
            if (this.mTechList[i] == tech && this.mTechHandles[i] == handle) {
                return true;
            }
            i++;
        }
        return DBG;
    }

    private boolean isUltralightC() {
        byte[] respData = transceive(new byte[]{(byte) 48, (byte) 2}, DBG, new int[2]);
        if (respData == null || respData.length != 16) {
            return DBG;
        }
        if (respData[2] == null && respData[3] == null && respData[4] == null && respData[5] == null && respData[6] == null && respData[7] == null) {
            if (respData[8] == (byte) 2 && respData[9] == null) {
                return true;
            }
            return DBG;
        } else if (respData[4] != -31 || (respData[5] & 255) >= 32) {
            return DBG;
        } else {
            if ((respData[6] & 255) > 6) {
                return true;
            }
            return DBG;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public android.os.Bundle[] getTechExtras() {
        /*
        r14 = this;
        r13 = 2;
        r12 = 8;
        monitor-enter(r14);
        r8 = r14.mTechExtras;	 Catch:{ all -> 0x004a }
        if (r8 == 0) goto L_0x000c;
    L_0x0008:
        r8 = r14.mTechExtras;	 Catch:{ all -> 0x004a }
        monitor-exit(r14);	 Catch:{ all -> 0x004a }
    L_0x000b:
        return r8;
    L_0x000c:
        r8 = r14.mTechList;	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        r8 = new android.os.Bundle[r8];	 Catch:{ all -> 0x004a }
        r14.mTechExtras = r8;	 Catch:{ all -> 0x004a }
        r3 = 0;
    L_0x0014:
        r8 = r14.mTechList;	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        if (r3 >= r8) goto L_0x0108;
    L_0x0019:
        r2 = new android.os.Bundle;	 Catch:{ all -> 0x004a }
        r2.<init>();	 Catch:{ all -> 0x004a }
        r8 = r14.mTechList;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        switch(r8) {
            case 1: goto L_0x0028;
            case 2: goto L_0x004d;
            case 3: goto L_0x00b2;
            case 4: goto L_0x007a;
            case 5: goto L_0x00ce;
            case 6: goto L_0x0025;
            case 7: goto L_0x0025;
            case 8: goto L_0x0025;
            case 9: goto L_0x00f5;
            case 10: goto L_0x0100;
            default: goto L_0x0025;
        };	 Catch:{ all -> 0x004a }
    L_0x0025:
        r3 = r3 + 1;
        goto L_0x0014;
    L_0x0028:
        r8 = r14.mTechActBytes;	 Catch:{ all -> 0x004a }
        r0 = r8[r3];	 Catch:{ all -> 0x004a }
        if (r0 == 0) goto L_0x003c;
    L_0x002e:
        r8 = r0.length;	 Catch:{ all -> 0x004a }
        if (r8 <= 0) goto L_0x003c;
    L_0x0031:
        r8 = "sak";
        r9 = 0;
        r9 = r0[r9];	 Catch:{ all -> 0x004a }
        r9 = r9 & 255;
        r9 = (short) r9;	 Catch:{ all -> 0x004a }
        r2.putShort(r8, r9);	 Catch:{ all -> 0x004a }
    L_0x003c:
        r8 = "atqa";
        r9 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r9 = r9[r3];	 Catch:{ all -> 0x004a }
        r2.putByteArray(r8, r9);	 Catch:{ all -> 0x004a }
    L_0x0045:
        r8 = r14.mTechExtras;	 Catch:{ all -> 0x004a }
        r8[r3] = r2;	 Catch:{ all -> 0x004a }
        goto L_0x0025;
    L_0x004a:
        r8 = move-exception;
        monitor-exit(r14);	 Catch:{ all -> 0x004a }
        throw r8;
    L_0x004d:
        r8 = 4;
        r1 = new byte[r8];	 Catch:{ all -> 0x004a }
        r8 = 3;
        r6 = new byte[r8];	 Catch:{ all -> 0x004a }
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        r9 = 7;
        if (r8 < r9) goto L_0x0045;
    L_0x005b:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r9 = 0;
        r10 = 0;
        r11 = 4;
        java.lang.System.arraycopy(r8, r9, r1, r10, r11);	 Catch:{ all -> 0x004a }
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r9 = 4;
        r10 = 0;
        r11 = 3;
        java.lang.System.arraycopy(r8, r9, r6, r10, r11);	 Catch:{ all -> 0x004a }
        r8 = "appdata";
        r2.putByteArray(r8, r1);	 Catch:{ all -> 0x004a }
        r8 = "protinfo";
        r2.putByteArray(r8, r6);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x007a:
        r8 = 8;
        r5 = new byte[r8];	 Catch:{ all -> 0x004a }
        r8 = 2;
        r7 = new byte[r8];	 Catch:{ all -> 0x004a }
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        if (r8 < r12) goto L_0x0098;
    L_0x0088:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r9 = 0;
        r10 = 0;
        r11 = 8;
        java.lang.System.arraycopy(r8, r9, r5, r10, r11);	 Catch:{ all -> 0x004a }
        r8 = "pmm";
        r2.putByteArray(r8, r5);	 Catch:{ all -> 0x004a }
    L_0x0098:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        r9 = 10;
        if (r8 != r9) goto L_0x0045;
    L_0x00a1:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r9 = 8;
        r10 = 0;
        r11 = 2;
        java.lang.System.arraycopy(r8, r9, r7, r10, r11);	 Catch:{ all -> 0x004a }
        r8 = "systemcode";
        r2.putByteArray(r8, r7);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x00b2:
        r8 = 1;
        r8 = r14.hasTech(r8);	 Catch:{ all -> 0x004a }
        if (r8 == 0) goto L_0x00c3;
    L_0x00b9:
        r8 = "histbytes";
        r9 = r14.mTechActBytes;	 Catch:{ all -> 0x004a }
        r9 = r9[r3];	 Catch:{ all -> 0x004a }
        r2.putByteArray(r8, r9);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x00c3:
        r8 = "hiresp";
        r9 = r14.mTechActBytes;	 Catch:{ all -> 0x004a }
        r9 = r9[r3];	 Catch:{ all -> 0x004a }
        r2.putByteArray(r8, r9);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x00ce:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        if (r8 == 0) goto L_0x0045;
    L_0x00d4:
        r8 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r8 = r8[r3];	 Catch:{ all -> 0x004a }
        r8 = r8.length;	 Catch:{ all -> 0x004a }
        if (r8 < r13) goto L_0x0045;
    L_0x00db:
        r8 = "respflags";
        r9 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r9 = r9[r3];	 Catch:{ all -> 0x004a }
        r10 = 0;
        r9 = r9[r10];	 Catch:{ all -> 0x004a }
        r2.putByte(r8, r9);	 Catch:{ all -> 0x004a }
        r8 = "dsfid";
        r9 = r14.mTechPollBytes;	 Catch:{ all -> 0x004a }
        r9 = r9[r3];	 Catch:{ all -> 0x004a }
        r10 = 1;
        r9 = r9[r10];	 Catch:{ all -> 0x004a }
        r2.putByte(r8, r9);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x00f5:
        r4 = r14.isUltralightC();	 Catch:{ all -> 0x004a }
        r8 = "isulc";
        r2.putBoolean(r8, r4);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x0100:
        r8 = "barcodetype";
        r9 = 1;
        r2.putInt(r8, r9);	 Catch:{ all -> 0x004a }
        goto L_0x0045;
    L_0x0108:
        r8 = r14.mTechExtras;	 Catch:{ all -> 0x004a }
        monitor-exit(r14);	 Catch:{ all -> 0x004a }
        goto L_0x000b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.dhimpl.NativeNfcTag.getTechExtras():android.os.Bundle[]");
    }

    public NdefMessage findAndReadNdef() {
        NdefMessage ndefMsg;
        int[] technologies = getTechList();
        int[] handles = this.mTechHandles;
        boolean foundFormattable = DBG;
        int formattableHandle = 0;
        int formattableLibNfcType = 0;
        for (int techIndex = 0; techIndex < technologies.length; techIndex++) {
            int i = 0;
            while (i < techIndex) {
                i = handles[i] == handles[techIndex] ? i + 1 : i + 1;
            }
            int status = connectWithStatus(technologies[techIndex]);
            if (status != 0) {
                Log.d("NativeNfcTag", "Connect Failed - status = " + status);
                if (status == STATUS_CODE_TARGET_LOST) {
                    ndefMsg = null;
                    break;
                }
            } else {
                if (!foundFormattable) {
                    if (isNdefFormatable()) {
                        foundFormattable = true;
                        formattableHandle = getConnectedHandle();
                        formattableLibNfcType = getConnectedLibNfcType();
                    }
                    reconnect();
                }
                int[] ndefinfo = new int[2];
                status = checkNdefWithStatus(ndefinfo);
                if (status != 0) {
                    Log.d("NativeNfcTag", "Check NDEF Failed - status = " + status);
                    if (status == STATUS_CODE_TARGET_LOST) {
                        ndefMsg = null;
                        break;
                    }
                } else {
                    boolean generateEmptyNdef = DBG;
                    int supportedNdefLength = ndefinfo[0];
                    int cardState = ndefinfo[1];
                    byte[] buff = readNdef();
                    if (buff != null) {
                        try {
                            ndefMsg = new NdefMessage(buff);
                            try {
                                addNdefTechnology(ndefMsg, getConnectedHandle(), getConnectedLibNfcType(), getConnectedTechnology(), supportedNdefLength, cardState);
                                reconnect();
                            } catch (FormatException e) {
                                generateEmptyNdef = true;
                                if (generateEmptyNdef) {
                                    ndefMsg = null;
                                    addNdefTechnology(null, getConnectedHandle(), getConnectedLibNfcType(), getConnectedTechnology(), supportedNdefLength, cardState);
                                    foundFormattable = DBG;
                                    reconnect();
                                }
                                addNdefFormatableTechnology(formattableHandle, formattableLibNfcType);
                                return ndefMsg;
                            }
                        } catch (FormatException e2) {
                            ndefMsg = null;
                            generateEmptyNdef = true;
                            if (generateEmptyNdef) {
                                ndefMsg = null;
                                addNdefTechnology(null, getConnectedHandle(), getConnectedLibNfcType(), getConnectedTechnology(), supportedNdefLength, cardState);
                                foundFormattable = DBG;
                                reconnect();
                            }
                            addNdefFormatableTechnology(formattableHandle, formattableLibNfcType);
                            return ndefMsg;
                        }
                    }
                    generateEmptyNdef = true;
                    ndefMsg = null;
                    if (generateEmptyNdef) {
                        ndefMsg = null;
                        addNdefTechnology(null, getConnectedHandle(), getConnectedLibNfcType(), getConnectedTechnology(), supportedNdefLength, cardState);
                        foundFormattable = DBG;
                        reconnect();
                    }
                }
            }
        }
        ndefMsg = null;
        if (ndefMsg == null && foundFormattable) {
            addNdefFormatableTechnology(formattableHandle, formattableLibNfcType);
        }
        return ndefMsg;
    }
}
