package com.gsma.services.nfc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface INfcController extends IInterface {

    public static abstract class Stub extends Binder implements INfcController {
        private static final String DESCRIPTOR = "com.gsma.services.nfc.INfcController";
        static final int TRANSACTION_disableCardEmulationMode = 5;
        static final int TRANSACTION_enableCardEmulationMode = 4;
        static final int TRANSACTION_enableNfcController = 2;
        static final int TRANSACTION_isCardEmulationEnabled = 3;
        static final int TRANSACTION_isEnabled = 1;

        private static class Proxy implements INfcController {
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

            public boolean isEnabled() throws RemoteException {
                boolean _result = true;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_isEnabled, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() == 0) {
                        _result = false;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int enableNfcController(ICallbacks cb) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(cb != null ? cb.asBinder() : null);
                    this.mRemote.transact(Stub.TRANSACTION_enableNfcController, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isCardEmulationEnabled() throws RemoteException {
                boolean _result = false;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_isCardEmulationEnabled, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        _result = true;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int enableCardEmulationMode(ICallbacks cb) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(cb != null ? cb.asBinder() : null);
                    this.mRemote.transact(Stub.TRANSACTION_enableCardEmulationMode, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int disableCardEmulationMode(ICallbacks cb) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(cb != null ? cb.asBinder() : null);
                    this.mRemote.transact(Stub.TRANSACTION_disableCardEmulationMode, _data, _reply, 0);
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

        public static INfcController asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof INfcController)) {
                return new Proxy(obj);
            }
            return (INfcController) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            int i = 0;
            boolean _result;
            int _result2;
            switch (code) {
                case TRANSACTION_isEnabled /*1*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isEnabled();
                    reply.writeNoException();
                    if (_result) {
                        i = TRANSACTION_isEnabled;
                    }
                    reply.writeInt(i);
                    return true;
                case TRANSACTION_enableNfcController /*2*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = enableNfcController(com.gsma.services.nfc.ICallbacks.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case TRANSACTION_isCardEmulationEnabled /*3*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isCardEmulationEnabled();
                    reply.writeNoException();
                    if (_result) {
                        i = TRANSACTION_isEnabled;
                    }
                    reply.writeInt(i);
                    return true;
                case TRANSACTION_enableCardEmulationMode /*4*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = enableCardEmulationMode(com.gsma.services.nfc.ICallbacks.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case TRANSACTION_disableCardEmulationMode /*5*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = disableCardEmulationMode(com.gsma.services.nfc.ICallbacks.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    int disableCardEmulationMode(ICallbacks iCallbacks) throws RemoteException;

    int enableCardEmulationMode(ICallbacks iCallbacks) throws RemoteException;

    int enableNfcController(ICallbacks iCallbacks) throws RemoteException;

    boolean isCardEmulationEnabled() throws RemoteException;

    boolean isEnabled() throws RemoteException;
}
