package com.gsma.services.nfc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ICallbacks extends IInterface {

    public static abstract class Stub extends Binder implements ICallbacks {
        private static final String DESCRIPTOR = "com.gsma.services.nfc.ICallbacks";
        static final int TRANSACTION_onCardEmulationMode = 2;
        static final int TRANSACTION_onNfcController = 1;

        private static class Proxy implements ICallbacks {
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

            public void onNfcController(boolean success) throws RemoteException {
                int i = Stub.TRANSACTION_onNfcController;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!success) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(Stub.TRANSACTION_onNfcController, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void onCardEmulationMode(int status) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(status);
                    this.mRemote.transact(Stub.TRANSACTION_onCardEmulationMode, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICallbacks asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof ICallbacks)) {
                return new Proxy(obj);
            }
            return (ICallbacks) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case TRANSACTION_onNfcController /*1*/:
                    data.enforceInterface(DESCRIPTOR);
                    onNfcController(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case TRANSACTION_onCardEmulationMode /*2*/:
                    data.enforceInterface(DESCRIPTOR);
                    onCardEmulationMode(data.readInt());
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void onCardEmulationMode(int i) throws RemoteException;

    void onNfcController(boolean z) throws RemoteException;
}
