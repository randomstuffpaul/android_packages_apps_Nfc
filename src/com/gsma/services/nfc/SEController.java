package com.gsma.services.nfc;

import android.app.Activity;
import android.app.ActivityThread;
import android.app.OnActivityPausedListener;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import com.android.nfc.NfcService;
import com.gsma.services.nfc.ISEController.Stub;
import java.util.HashMap;

public class SEController {
    public static final int ERROR_INVALID_PARAM = -4;
    public static final int ERROR_INVALID_STORAGE_SETTING = -5;
    public static final int ERROR_NOT_ALLOWED = -3;
    public static final int ERROR_NOT_ENABLED = -2;
    public static final int ERROR_NOT_SUPPORTED = -1;
    public static final int SUCCESS = 0;
    static String TAG;
    static ISEController controllerService;
    static HashMap<Context, SEController> sSEControllers;
    final Context mContext;
    private OnActivityPausedListener mForegroundDispatchListener;

    /* renamed from: com.gsma.services.nfc.SEController.1 */
    class C00481 implements OnActivityPausedListener {
        C00481() {
        }

        public void onPaused(Activity activity) {
            SEController.this.disableForegroundDispatchInternal(activity, true);
        }
    }

    static {
        TAG = "SEController";
        controllerService = null;
        sSEControllers = new HashMap();
    }

    private SEController(Context context) {
        this.mForegroundDispatchListener = new C00481();
        this.mContext = context;
    }

    public static synchronized SEController getDefaultController(Context context) {
        SEController controller;
        synchronized (SEController.class) {
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
                    Log.e(TAG, "Could not retrieve SEController service.");
                    throw new UnsupportedOperationException();
                }
                controller = (SEController) sSEControllers.get(context);
                if (controller == null) {
                    controller = new SEController(context);
                    sSEControllers.put(context, controller);
                }
            }
        }
        return controller;
    }

    public String getActiveSecureElement() {
        try {
            return controllerService.getActiveSecureElement();
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
            return "";
        }
    }

    public void setActiveSecureElement(String SEName) {
        try {
            int res = controllerService.setActiveSecureElement(SEName);
            if (res != 0) {
                if (res == ERROR_NOT_ALLOWED) {
                    throw new SecurityException("API not allowed.");
                } else if (res == ERROR_NOT_SUPPORTED) {
                    throw new IllegalStateException("API not supported.");
                } else if (res == ERROR_NOT_ENABLED) {
                    throw new IllegalStateException("NFC controller is not enabled.");
                } else if (res == ERROR_INVALID_PARAM) {
                    throw new IllegalStateException("Invalid secure element name.");
                } else if (res == ERROR_INVALID_STORAGE_SETTING) {
                    throw new IllegalStateException("Invalid secure storage setting.");
                }
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public void enableEvt_TransactionFgDispatch(Activity activity, IntentFilter[] filters) {
        if (activity == null) {
            throw new NullPointerException();
        } else if (activity.isResumed()) {
            try {
                ActivityThread.currentActivityThread().registerOnActivityPausedListener(activity, this.mForegroundDispatchListener);
                int res = controllerService.setForegroundDispatch(PendingIntent.getActivity(activity, 0, new Intent(activity, activity.getClass()).addFlags(536870912), 0), filters);
                if (res != 0) {
                    ActivityThread.currentActivityThread().unregisterOnActivityPausedListener(activity, this.mForegroundDispatchListener);
                }
                if (res == ERROR_NOT_ALLOWED) {
                    throw new SecurityException("API not allowed.");
                } else if (res == ERROR_INVALID_PARAM) {
                    throw new IllegalStateException("Null intent filter in filters array.");
                }
            } catch (RemoteException e) {
                attemptDeadServiceRecovery(e);
            }
        } else {
            throw new IllegalStateException("Foreground Secure Event dispatch can only be enabled when your activity is resumed");
        }
    }

    public void enableMultiEvt_transactionReception(String SEName) {
        try {
            if (controllerService.enableMultiEvt_transactionReception(SEName, true) == ERROR_NOT_ALLOWED) {
                throw new SecurityException("API not allowed.");
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    public void disableEvt_TransactionFgDispatch(Activity activity) {
        ActivityThread.currentActivityThread().unregisterOnActivityPausedListener(activity, this.mForegroundDispatchListener);
        disableForegroundDispatchInternal(activity, false);
    }

    private void disableForegroundDispatchInternal(Activity activity, boolean force) {
        try {
            if (controllerService.setForegroundDispatch(null, null) == ERROR_NOT_ALLOWED) {
                throw new SecurityException("API not allowed.");
            } else if (!force) {
                if (!activity.isResumed()) {
                    throw new IllegalStateException("You must disable foreground dispatching while your activity is still resumed");
                }
            }
        } catch (RemoteException e) {
            attemptDeadServiceRecovery(e);
        }
    }

    private static ISEController getServiceInterface() {
        IBinder b = ServiceManager.getService(NfcService.SECONTROLLER_SERVICE_NAME);
        if (b == null) {
            return null;
        }
        return Stub.asInterface(b);
    }

    private void attemptDeadServiceRecovery(Exception e) {
        Log.e(TAG, "NFC service dead - attempting to recover.", e);
        ISEController service = getServiceInterface();
        if (service == null) {
            Log.e(TAG, "Could not retrieve NFC service during service recovery.");
        } else {
            controllerService = service;
        }
    }
}
