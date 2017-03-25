package com.android.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.util.secutil.Log;
import android.view.KeyEvent;

public class NfcChnEnablePopup extends Activity {
    private static final String TAG = "NfcChnEnablePopup";
    private boolean mEventDecision;
    private AlertDialog mNfcChnEnablePopup;
    private Builder mPopup;

    /* renamed from: com.android.nfc.NfcChnEnablePopup.1 */
    class C00061 implements OnClickListener {
        C00061() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcChnEnablePopup.this.mEventDecision = true;
            NfcService.getInstance().sendChnEnableCancel();
            NfcChnEnablePopup.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcChnEnablePopup.2 */
    class C00072 implements OnClickListener {
        C00072() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcChnEnablePopup.this.mEventDecision = true;
            NfcService.getInstance().sendChnEnableDirect();
            NfcChnEnablePopup.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcChnEnablePopup.3 */
    class C00083 implements OnKeyListener {
        C00083() {
        }

        public boolean onKey(DialogInterface dialog, int mKeyCode, KeyEvent mKeyEvent) {
            if (mKeyEvent.getAction() == 1 && mKeyCode == 4) {
                NfcChnEnablePopup.this.mEventDecision = true;
                NfcService.getInstance().sendChnEnableCancel();
                NfcChnEnablePopup.this.finish();
            }
            return false;
        }
    }

    public NfcChnEnablePopup() {
        this.mNfcChnEnablePopup = null;
        this.mPopup = null;
        this.mEventDecision = false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        this.mPopup = new Builder(this);
        this.mPopup.setTitle(C0027R.string.nfcUserLabel);
        this.mPopup.setMessage(C0027R.string.chn_enable_popup_contents);
        this.mPopup.setNegativeButton(C0027R.string.cancel, new C00061());
        this.mPopup.setPositiveButton(C0027R.string.btn_ok, new C00072());
        this.mNfcChnEnablePopup = this.mPopup.create();
        this.mNfcChnEnablePopup.setCanceledOnTouchOutside(false);
        this.mNfcChnEnablePopup.setOnKeyListener(new C00083());
        this.mNfcChnEnablePopup.show();
    }

    protected void onDestroy() {
        Log.d(TAG, " onDestroy");
        if (this.mNfcChnEnablePopup != null) {
            this.mNfcChnEnablePopup.dismiss();
            this.mNfcChnEnablePopup = null;
        }
        super.onDestroy();
    }

    protected void onPause() {
        Log.d(TAG, " onPause");
        if (!(this.mNfcChnEnablePopup == null || this.mEventDecision)) {
            Log.d(TAG, " onPause- mEventDecision yet close popup");
            this.mEventDecision = true;
            NfcService.getInstance().sendChnEnableCancel();
            finish();
        }
        super.onPause();
    }
}
