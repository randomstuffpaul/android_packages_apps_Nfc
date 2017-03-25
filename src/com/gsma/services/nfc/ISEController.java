package com.gsma.services.nfc;

import android.app.PendingIntent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ISEController extends IInterface {

    public static abstract class Stub extends Binder implements ISEController {
        private static final String DESCRIPTOR = "com.gsma.services.nfc.ISEController";
        static final int TRANSACTION_enableMultiEvt_transactionReception = 4;
        static final int TRANSACTION_getActiveSecureElement = 1;
        static final int TRANSACTION_setActiveSecureElement = 2;
        static final int TRANSACTION_setForegroundDispatch = 3;

        private static class Proxy implements ISEController {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public String getActiveSecureElement() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getActiveSecureElement, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int setActiveSecureElement(String SEName) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(SEName);
                    this.mRemote.transact(Stub.TRANSACTION_setActiveSecureElement, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int setForegroundDispatch(PendingIntent intent, IntentFilter[] filters) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (intent != null) {
                        _data.writeInt(Stub.TRANSACTION_getActiveSecureElement);
                        intent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeTypedArray(filters, 0);
                    this.mRemote.transact(Stub.TRANSACTION_setForegroundDispatch, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int enableMultiEvt_transactionReception(String SEName, boolean enable) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(SEName);
                    if (enable) {
                        i = Stub.TRANSACTION_getActiveSecureElement;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(Stub.TRANSACTION_enableMultiEvt_transactionReception, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ISEController asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof ISEController)) {
                return new Proxy(obj);
            }
            return (ISEController) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            int _result;
            switch (code) {
                case TRANSACTION_getActiveSecureElement /*1*/:
                    data.enforceInterface(DESCRIPTOR);
                    String _result2 = getActiveSecureElement();
                    reply.writeNoException();
                    reply.writeString(_result2);
                    return true;
                case TRANSACTION_setActiveSecureElement /*2*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result = setActiveSecureElement(data.readString());
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case TRANSACTION_setForegroundDispatch /*3*/:
                    PendingIntent _arg0;
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        _arg0 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    _result = setForegroundDispatch(_arg0, (IntentFilter[]) data.createTypedArray(IntentFilter.CREATOR));
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case TRANSACTION_enableMultiEvt_transactionReception /*4*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result = enableMultiEvt_transactionReception(data.readString(), data.readInt() != 0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    int enableMultiEvt_transactionReception(String str, boolean z) throws RemoteException;

    String getActiveSecureElement() throws RemoteException;

    int setActiveSecureElement(String str) throws RemoteException;

    int setForegroundDispatch(PendingIntent pendingIntent, IntentFilter[] intentFilterArr) throws RemoteException;
}
