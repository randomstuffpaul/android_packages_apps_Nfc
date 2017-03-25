package com.android.nfc.secSend;

import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Debug;
import android.util.Log;
import com.android.nfc.snep.SnepClient;
import com.android.nfc.snep.SnepMessage;
import com.android.nfc.snep.SnepServer;
import com.android.nfc.snep.SnepServer.Callback;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SecNdefService {
    static final boolean DBG;
    public static final String FILE = "SecNdefService.bin";
    private static final String SEC_DISCOVERED = "android.nfc.action.SEC_DISCOVERED";
    private static final String SEC_NFCEVENT_INRANGE = "android.nfc.action.P2P_INRANGE";
    private static final String SEC_NFCEVENT_OUTOFRANGE = "android.nfc.action.P2P_OUTOFRANGE";
    private static final String SEC_SEND_COMPLETE = "android.nfc.action.SEC_SENDCOMPLETE";
    static final String SEC_SNEP_BASE = "urn:nfc:xsn:samsung.com:";
    private static final int SEC_SNEP_SAP_CAPACITY = 2;
    private static final int SEC_SNEP_SAP_END = 23;
    private static final int SEC_SNEP_SAP_START = 21;
    private static final String SEC_UNSUPPORTED = "android.nfc.action.SEC_UNSUPPORTED";
    private static final String TAG = "SecNdefService";
    static final Object mSecSnepTransmissionLock;
    private Context mContext;
    private boolean mIsReceiveEnabled;
    private boolean mIsSendEnabled;
    private final BlockingQueue<SecNdefMsg> mSecNdefSendQueue;
    Runnable mSecSendRunnable;
    private HashMap<Integer, SecSnepInfo> mSecSnepInfoArray;
    private Thread mSendThread;
    final Callback secSnepCallback;

    /* renamed from: com.android.nfc.secSend.SecNdefService.1 */
    class C00461 implements Runnable {
        SecSnepInfo mSecSnepInfo;
        SecNdefMsg msg;

        C00461() {
        }

        public void run() {
            if (SecNdefService.DBG) {
                Log.i(SecNdefService.TAG, "Runnable Starts!");
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    this.msg = (SecNdefMsg) SecNdefService.this.mSecNdefSendQueue.take();
                    if (SecNdefService.DBG) {
                        Log.i(SecNdefService.TAG, "Runnable taken!");
                    }
                    synchronized (SecNdefService.mSecSnepTransmissionLock) {
                        SecSnepInfo secSnepInfo = (SecSnepInfo) SecNdefService.this.mSecSnepInfoArray.get(Integer.valueOf(this.msg.getSAP()));
                        this.mSecSnepInfo = secSnepInfo;
                        if (secSnepInfo != null) {
                            SnepClient snepClient = new SnepClient(SecNdefService.SEC_SNEP_BASE + this.mSecSnepInfo.getServiceName());
                            try {
                                snepClient.connect();
                                try {
                                    snepClient.put(this.msg.getMsg());
                                    snepClient.close();
                                    SecNdefService.this.sendSecSendComplete(this.mSecSnepInfo.getPkgName());
                                    if (SecNdefService.DBG) {
                                        Log.i(SecNdefService.TAG, "Send complete!");
                                    }
                                } catch (IOException e) {
                                    if (SecNdefService.DBG) {
                                        Log.i(SecNdefService.TAG, "Failed to send " + e.getMessage());
                                    }
                                    SecNdefService.this.mSecNdefSendQueue.add(this.msg);
                                    snepClient.close();
                                }
                            } catch (IOException e2) {
                                if (SecNdefService.DBG) {
                                    Log.i(SecNdefService.TAG, "Failed to connect " + e2.getMessage());
                                }
                                SecNdefService.this.sendSecUnsupported(this.mSecSnepInfo.getPkgName());
                                snepClient.close();
                            }
                        }
                    }
                } catch (InterruptedException e3) {
                    if (SecNdefService.DBG) {
                        Log.i(SecNdefService.TAG, "Runnable Interrupted!");
                        return;
                    }
                    return;
                }
            }
        }
    }

    /* renamed from: com.android.nfc.secSend.SecNdefService.2 */
    class C00472 implements Callback {
        C00472() {
        }

        public SnepMessage doPut(NdefMessage msg) {
            SecNdefService.this.onRecvMessage(msg);
            return SnepMessage.getMessage(SnepMessage.RESPONSE_SUCCESS);
        }

        public SnepMessage doGet(int acceptableLength, NdefMessage msg) {
            return SnepMessage.getMessage(SnepMessage.RESPONSE_NOT_FOUND);
        }
    }

    static final class SecNdefMsg {
        private int SAP;
        private NdefMessage msg;

        public SecNdefMsg(int SAP, NdefMessage msg) {
            this.SAP = 0;
            this.msg = null;
            this.SAP = SAP;
            this.msg = msg;
        }

        public int getSAP() {
            return this.SAP;
        }

        public NdefMessage getMsg() {
            return this.msg;
        }
    }

    public static final class SecSnepInfo implements Serializable {
        private transient Callback mCallback;
        private byte[] mId;
        private String mPkgName;
        private int mServerSap;
        private String mServiceName;
        private transient SnepServer mSnepServer;
        private byte[] mType;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeUTF(this.mServiceName);
            out.writeInt(this.mServerSap);
            out.writeUTF(this.mPkgName);
            out.writeUTF(new String(this.mType));
            out.writeUTF(new String(this.mId));
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            this.mServiceName = in.readUTF();
            this.mServerSap = in.readInt();
            this.mPkgName = in.readUTF();
            this.mType = in.readUTF().getBytes();
            this.mId = in.readUTF().getBytes();
        }

        public SecSnepInfo(String serviceName, int serverSap, String packageName, byte[] type, byte[] id, Callback callback) {
            this.mServiceName = null;
            this.mServerSap = 0;
            this.mPkgName = null;
            this.mType = null;
            this.mId = null;
            this.mCallback = null;
            this.mSnepServer = null;
            this.mServiceName = serviceName;
            this.mServerSap = serverSap;
            this.mPkgName = packageName;
            this.mType = type;
            this.mId = id;
            this.mCallback = callback;
        }

        public String getServiceName() {
            return this.mServiceName;
        }

        public int getServerSap() {
            return this.mServerSap;
        }

        public byte[] getType() {
            return this.mType;
        }

        public byte[] getId() {
            return this.mId;
        }

        public String getPkgName() {
            return this.mPkgName;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof SecSnepInfo) {
                SecSnepInfo comp = (SecSnepInfo) obj;
                if (comp.getServiceName() == null) {
                    return SecNdefService.DBG;
                }
                if (comp.getServerSap() == 0) {
                    return SecNdefService.DBG;
                }
                if (comp.getServiceName().equals(getServiceName()) && comp.getServerSap() == comp.getServerSap() && Arrays.equals(comp.getType(), getType()) && Arrays.equals(comp.getId(), getId())) {
                    return true;
                }
            }
            return SecNdefService.DBG;
        }

        public boolean createSnepServer() {
            if (this.mCallback == null || this.mServiceName == null) {
                return SecNdefService.DBG;
            }
            try {
                this.mSnepServer = new SnepServer(SecNdefService.SEC_SNEP_BASE + this.mServiceName, this.mServerSap, this.mCallback);
            } catch (Exception e) {
                if (SecNdefService.DBG) {
                    Log.i(SecNdefService.TAG, "SnepServer Creation Error : msg[" + e.getMessage() + "]");
                }
            }
            return true;
        }

        private boolean startServer() {
            if (this.mSnepServer == null) {
                return SecNdefService.DBG;
            }
            if (SecNdefService.DBG) {
                Log.i(SecNdefService.TAG, "startServer() : SAP(" + this.mServerSap + ").");
            }
            this.mSnepServer.start();
            return true;
        }

        private boolean stopServer() {
            if (this.mSnepServer == null) {
                return SecNdefService.DBG;
            }
            if (SecNdefService.DBG) {
                Log.i(SecNdefService.TAG, "stopServer() : SAP(" + this.mServerSap + ").");
            }
            this.mSnepServer.stop();
            return true;
        }
    }

    static {
        boolean z = true;
        if (Debug.isProductShip() == 1) {
            z = DBG;
        }
        DBG = z;
        mSecSnepTransmissionLock = new Object();
    }

    public void enableDisable(boolean sendEnable, boolean receiveEnable) {
        if (DBG) {
            Log.i(TAG, "enableDisable(sendEnable=" + Boolean.valueOf(sendEnable).toString() + ",receiveEnable=" + Boolean.valueOf(receiveEnable) + ")");
        }
        synchronized (this) {
            if (!this.mIsReceiveEnabled && receiveEnable) {
                startSecNdefService();
            } else if (this.mIsReceiveEnabled && !receiveEnable) {
                stopSecNdefService();
            }
            if (this.mIsSendEnabled || !sendEnable) {
                if (this.mIsSendEnabled && !sendEnable && DBG) {
                    Log.i(TAG, "Stop SendThread");
                }
            } else if (DBG) {
                Log.i(TAG, "Create SendThread");
            }
            this.mIsSendEnabled = sendEnable;
            this.mIsReceiveEnabled = receiveEnable;
        }
    }

    public SecNdefService(Context context, boolean sendEnable, boolean receiveEnable) {
        this.mSecSnepInfoArray = new HashMap(SEC_SNEP_SAP_CAPACITY);
        this.mSecNdefSendQueue = new ArrayBlockingQueue(32);
        this.mSendThread = null;
        this.mContext = null;
        this.mIsReceiveEnabled = DBG;
        this.mIsSendEnabled = DBG;
        this.mSecSendRunnable = new C00461();
        this.secSnepCallback = new C00472();
        if (DBG) {
            Log.i(TAG, "SecNdefService Created!");
        }
        this.mContext = context;
        enableDisable(sendEnable, receiveEnable);
        this.mIsSendEnabled = sendEnable;
        this.mIsReceiveEnabled = receiveEnable;
        loadSnepInfo();
    }

    public void saveSnepInfo() {
        if (DBG) {
            Log.i(TAG, "saveSnepInfo()");
        }
        try {
            ObjectOutputStream oStream = new ObjectOutputStream(this.mContext.openFileOutput(FILE, 0));
            ArrayList<SecSnepInfo> arrSnep = new ArrayList();
            for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
                arrSnep.add(secSnep);
            }
            oStream.writeObject(arrSnep);
            oStream.flush();
            oStream.close();
        } catch (IOException e) {
            if (DBG) {
                Log.i(TAG, "Failed to save SNEP information. [" + e.toString() + "]," + "[" + e.getMessage() + "]");
            }
        }
    }

    public void loadSnepInfo() {
        Iterator i$;
        SecSnepInfo secSnep;
        Throwable th;
        ArrayList<SecSnepInfo> arrSnep = null;
        ObjectInputStream iStream = null;
        try {
            ObjectInputStream iStream2 = new ObjectInputStream(this.mContext.openFileInput(FILE));
            try {
                arrSnep = (ArrayList) iStream2.readObject();
                iStream2.close();
                if (iStream2 != null) {
                    try {
                        iStream2.close();
                    } catch (Exception e) {
                        iStream = iStream2;
                    }
                }
                iStream = iStream2;
            } catch (Exception e2) {
                iStream = iStream2;
                if (iStream != null) {
                    try {
                        iStream.close();
                    } catch (Exception e3) {
                    }
                }
                if (arrSnep == null) {
                    i$ = arrSnep.iterator();
                    while (i$.hasNext()) {
                        secSnep = (SecSnepInfo) i$.next();
                        if (!DBG) {
                            Log.i(TAG, "Loaded SNEP Information : ServiceName[" + secSnep.getServiceName() + "]," + "ServiceSap[" + secSnep.getServerSap() + "]," + "PkgName[" + secSnep.getPkgName() + "]," + "Type[" + new String(secSnep.getType()) + "]," + "Id[" + new String(secSnep.getId()) + "]");
                        }
                        addSecSnepInfo(new SecSnepInfo(secSnep.getServiceName(), secSnep.getServerSap(), secSnep.getPkgName(), secSnep.getType(), secSnep.getId(), this.secSnepCallback));
                    }
                }
            } catch (Throwable th2) {
                th = th2;
                iStream = iStream2;
                if (iStream != null) {
                    try {
                        iStream.close();
                    } catch (Exception e4) {
                    }
                }
                throw th;
            }
        } catch (Exception e5) {
            if (iStream != null) {
                iStream.close();
            }
            if (arrSnep == null) {
                i$ = arrSnep.iterator();
                while (i$.hasNext()) {
                    secSnep = (SecSnepInfo) i$.next();
                    if (!DBG) {
                        Log.i(TAG, "Loaded SNEP Information : ServiceName[" + secSnep.getServiceName() + "]," + "ServiceSap[" + secSnep.getServerSap() + "]," + "PkgName[" + secSnep.getPkgName() + "]," + "Type[" + new String(secSnep.getType()) + "]," + "Id[" + new String(secSnep.getId()) + "]");
                    }
                    addSecSnepInfo(new SecSnepInfo(secSnep.getServiceName(), secSnep.getServerSap(), secSnep.getPkgName(), secSnep.getType(), secSnep.getId(), this.secSnepCallback));
                }
            }
        } catch (Throwable th3) {
            th = th3;
            if (iStream != null) {
                iStream.close();
            }
            throw th;
        }
        if (arrSnep == null) {
            i$ = arrSnep.iterator();
            while (i$.hasNext()) {
                secSnep = (SecSnepInfo) i$.next();
                if (!DBG) {
                    Log.i(TAG, "Loaded SNEP Information : ServiceName[" + secSnep.getServiceName() + "]," + "ServiceSap[" + secSnep.getServerSap() + "]," + "PkgName[" + secSnep.getPkgName() + "]," + "Type[" + new String(secSnep.getType()) + "]," + "Id[" + new String(secSnep.getId()) + "]");
                }
                addSecSnepInfo(new SecSnepInfo(secSnep.getServiceName(), secSnep.getServerSap(), secSnep.getPkgName(), secSnep.getType(), secSnep.getId(), this.secSnepCallback));
            }
        }
    }

    public void onP2pOutOfRange() {
        if (DBG) {
            Log.i(TAG, "onP2pOutOfRange()");
        }
        for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
            sendSecNFCEvent(secSnep.getPkgName(), SEC_NFCEVENT_OUTOFRANGE);
        }
        if (this.mSendThread != null) {
            if (DBG) {
                Log.i(TAG, "mSendThread interrupted!");
            }
            this.mSendThread.interrupt();
        }
        this.mSecNdefSendQueue.clear();
    }

    public void onP2pInRange() {
        if (DBG) {
            Log.i(TAG, "onP2pInRange()");
        }
        for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
            sendSecNFCEvent(secSnep.getPkgName(), SEC_NFCEVENT_INRANGE);
        }
        if (DBG) {
            Log.i(TAG, "mSendThread start!");
        }
        this.mSendThread = new Thread(this.mSecSendRunnable);
        this.mSendThread.start();
    }

    private void sendSecSendComplete(String pkgName) {
        Intent intent = new Intent(SEC_SEND_COMPLETE);
        intent.setPackage(pkgName);
        this.mContext.sendBroadcast(intent);
    }

    private void sendSecUnsupported(String pkgName) {
        Intent intent = new Intent(SEC_UNSUPPORTED);
        intent.setPackage(pkgName);
        this.mContext.sendBroadcast(intent);
    }

    private void sendSecNFCEvent(String pkgName, String actionName) {
        Intent intent = new Intent(actionName);
        intent.setPackage(pkgName);
        this.mContext.sendBroadcast(intent);
    }

    public void onRecvMessage(NdefMessage msg) {
        if (DBG) {
            Log.i(TAG, "onRecvMessage()");
        }
        NdefRecord[] records = msg.getRecords();
        if (records != null && records.length >= 1) {
            NdefRecord refRecord = records[0];
            if (refRecord.getTnf() == (short) 1 || refRecord.getTnf() == (short) 4) {
                if (records.length > 1 && (Arrays.equals(refRecord.getType(), "Hr".getBytes()) || Arrays.equals(refRecord.getType(), "Hs".getBytes()))) {
                    refRecord = records[1];
                }
                if (DBG) {
                    Log.i(TAG, "Received NDEF Message: Type[" + new String(refRecord.getType()) + "], " + "ID [" + new String(refRecord.getId()) + "].");
                }
                for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
                    if (Arrays.equals(refRecord.getType(), secSnep.getType()) && Arrays.equals(refRecord.getId(), secSnep.getId())) {
                        Intent WPFIntent = new Intent(SEC_DISCOVERED);
                        WPFIntent.setPackage(secSnep.getPkgName());
                        WPFIntent.putExtra("NdefMessage", msg);
                        this.mContext.sendBroadcast(WPFIntent);
                        if (DBG) {
                            Log.i(TAG, "Event:android.nfc.action.SEC_DISCOVERED sent to " + secSnep.getPkgName());
                        }
                    }
                }
            } else if (DBG) {
                Log.i(TAG, "Received NDEF Message is neither TNF WELL KNOW nor TNF EXTERNAL TYPE.");
            }
        }
    }

    public int createSecNdefService(String serviceName, int serverSap, String pkgName, byte[] type, byte[] id) throws Exception {
        if (DBG) {
            Log.i(TAG, "createSecNdefService() : serviceName[" + serviceName + "]," + "serverSAP[" + serverSap + "]," + "PackageName[" + pkgName + "]");
        }
        if (serviceName == null) {
            throw new IllegalArgumentException("Service Name is null.");
        } else if (pkgName == null) {
            throw new IllegalArgumentException("Package Name is null.");
        } else if (SEC_SNEP_SAP_START > serverSap || SEC_SNEP_SAP_END < serverSap) {
            throw new IndexOutOfBoundsException("The range of Server SAP should be from 21 to 23.");
        } else {
            SecSnepInfo si = new SecSnepInfo(serviceName, serverSap, pkgName, type, id, this.secSnepCallback);
            if (addSecSnepInfo(si)) {
                return si.getServerSap();
            }
            return 0;
        }
    }

    private boolean addSecSnepInfo(SecSnepInfo si) {
        if (this.mSecSnepInfoArray.containsKey(Integer.valueOf(si.getServerSap()))) {
            SecSnepInfo existing = (SecSnepInfo) this.mSecSnepInfoArray.get(Integer.valueOf(si.getServerSap()));
            if (existing != null) {
                if (existing.equals(si)) {
                    if (DBG) {
                        Log.i(TAG, "The requested SNEPInfo is already existing.");
                    }
                    return true;
                }
                existing.stopServer();
                synchronized (this) {
                    this.mSecSnepInfoArray.remove(Integer.valueOf(si.getServerSap()));
                }
            }
        }
        synchronized (this) {
            this.mSecSnepInfoArray.put(Integer.valueOf(si.getServerSap()), si);
            if (this.mIsReceiveEnabled) {
                si.createSnepServer();
                si.startServer();
            }
        }
        if (DBG) {
            Log.i(TAG, "createSecNdefService() : returned value is " + si.getServerSap());
        }
        saveSnepInfo();
        return true;
    }

    public int secSendNdefMsg(int SAP, NdefMessage msg) {
        if (DBG) {
            Log.i(TAG, "secSendNdefMsg() : SAP[" + SAP + "]");
        }
        synchronized (this) {
            if (this.mIsSendEnabled) {
                this.mSecNdefSendQueue.add(new SecNdefMsg(SAP, msg));
            }
        }
        return 0;
    }

    public boolean closeSecNdefService(int SAP) {
        if (DBG) {
            Log.i(TAG, "closeSecNdefService() : SAP[" + SAP + "]");
        }
        if (this.mSecSnepInfoArray.remove(Integer.valueOf(SAP)) != null) {
            return true;
        }
        return DBG;
    }

    public void startSecNdefService() {
        if (DBG) {
            Log.i(TAG, "startSecNdefService()");
        }
        for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
            secSnep.createSnepServer();
            secSnep.startServer();
        }
        Intent intent = new Intent("android.nfc.action.SEC_NDEF_START");
        intent.setPackage("com.sec.android.band");
        intent.addFlags(32);
        this.mContext.sendBroadcast(intent);
    }

    public void stopSecNdefService() {
        if (DBG) {
            Log.i(TAG, "stopSecNdefService()size=" + this.mSecSnepInfoArray.size());
        }
        for (SecSnepInfo secSnep : this.mSecSnepInfoArray.values()) {
            secSnep.stopServer();
        }
    }
}
