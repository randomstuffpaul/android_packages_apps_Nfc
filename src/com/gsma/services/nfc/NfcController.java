package com.gsma.services.nfc;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import com.android.nfc.NfcService;
import com.gsma.services.nfc.ICallbacks.Stub;
import java.util.HashMap;

public class NfcController {
    public static final String ACTION_TRANSACTION_EVENT = "com.gsma.services.nfc.action.TRANSACTION_EVENT";
    public static final int CARD_EMULATION_DISABLED = 0;
    public static final int CARD_EMULATION_ENABLED = 1;
    public static final int CARD_EMULATION_ERROR = 100;
    public static final int ERROR_INVALID_PARAM = -4;
    public static final int ERROR_NOT_ALLOWED = -3;
    public static final int ERROR_NOT_ENABLED = -2;
    public static final int ERROR_NOT_SUPPORTED = -1;
    public static final String NFC_CONTROLLER_PERMISSION = "android.permission.NFC";
    public static final String NFC_TRANSACTION_PERMISSION = "com.gsma.services.nfc.permission.TRANSACTION_EVENT";
    public static final int SUCCESS = 0;
    static String TAG = null;
    public static final String TRANSACTION_EXTRA_AID = "com.gsma.services.nfc.extra.AID";
    public static final String TRANSACTION_EXTRA_DATA = "com.gsma.services.nfc.extra.DATA";
    static INfcController controllerService;
    static HashMap<Context, NfcController> sNfcControllers;
    final Context mContext;

    public static abstract class Callbacks extends Stub {
    }

    static {
        TAG = "NfcController";
        controllerService = null;
        sNfcControllers = new HashMap();
    }

    private NfcController(Context context) {
        this.mContext = context;
    }

    public static synchronized NfcController getDefaultController(Context context) {
        NfcController controller;
        synchronized (NfcController.class) {
            if (context == null) {
                throw new IllegalArgumentException("context cannot be null");
            }
            context = context.getApplicationContext();
            if (context == null) {
                throw new IllegalArgumentException("context not associated with any application (using a mock context?)");
            } else if (context.getPackageManager().hasSystemFeature("android.hardware.nfc.hce")) {
                Log.e(TAG, "This model supports HCE so we don't use GSMA API");
                throw new UnsupportedOperationException();
            } else {
                controllerService = getServiceInterface();
                if (controllerService == null) {
                    Log.e(TAG, "Could not retrieve NfcController service.");
                    throw new UnsupportedOperationException();
                }
                controller = (NfcController) sNfcControllers.get(context);
                if (controller == null) {
                    controller = new NfcController(context);
                    sNfcControllers.put(context, controller);
                }
            }
        }
        return controller;
    }

    public boolean isEnabled() {
        try {
            return controllerService.isEnabled();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
            return false;
        }
    }

    public void enableNfcController(Callbacks cb) {
        try {
            int res = controllerService.enableNfcController(cb);
            if (res != 0 && res == ERROR_NOT_ALLOWED) {
                throw new SecurityException("API not allowed.");
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public boolean isCardEmulationEnabled() {
        try {
            return controllerService.isCardEmulationEnabled();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
            return false;
        }
    }

    public void enableCardEmulationMode(Callbacks cb) {
        try {
            int res = controllerService.enableCardEmulationMode(cb);
            if (res != 0) {
                if (res == ERROR_NOT_ALLOWED) {
                    throw new SecurityException("API not allowed.");
                } else if (res == ERROR_NOT_ENABLED) {
                    throw new IllegalStateException("NFC controller is not enabled.");
                }
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public void disableCardEmulationMode(Callbacks cb) {
        try {
            int res = controllerService.disableCardEmulationMode(cb);
            if (res != 0 && res == ERROR_NOT_ALLOWED) {
                throw new SecurityException("API not allowed.");
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    private static INfcController getServiceInterface() {
        IBinder b = ServiceManager.getService(NfcService.NFCCONTROLLER_SERVICE_NAME);
        if (b == null) {
            return null;
        }
        return INfcController.Stub.asInterface(b);
    }

    private void attemptDeadServiceRecovery(Exception e) {
        Log.e(TAG, "NFC service dead - attempting to recover.", e);
        INfcController service = getServiceInterface();
        if (service == null) {
            Log.e(TAG, "Could not retrieve NFC service during service recovery.");
        } else {
            controllerService = service;
        }
    }
}
