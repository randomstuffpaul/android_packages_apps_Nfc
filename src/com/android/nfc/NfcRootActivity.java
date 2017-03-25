package com.android.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.sec.android.app.CscFeature;

public class NfcRootActivity extends Activity {
    private static final int BROWSER_DIALOG = 1;
    public static final String EXTRA_INVALID_TAG = "invalidTag";
    static final String EXTRA_LAUNCH_INTENT = "launchIntent";
    static final String EXTRA_UNSUPPORTED_TAG = "unSupportedTag";
    static final String EXTRA_UNSUPPORTED_TECH = "mTechnology";
    private static final int INVALID_DIALOG = 3;
    public static final String PREF = "NfcServicePrefs";
    static final String PREF_BROWSER_NEVER_ASK = "browser_never_ask";
    static final String PREF_CONTACT_NEVER_ASK = "contact_never_ask";
    private static final String TAG = "NfcService";
    private static final int UNSUPPORTED_DIALOG = 4;
    private static final int VCARD_DIALOG = 2;
    private AlertDialog browserDialog;
    private AlertDialog contactDialog;
    private AlertDialog invalidTagDialog;
    String[] mBrowerSchemeList;
    String mFName;
    Intent mIntent;
    Intent mLaunchIntent;
    String mMessage;
    private SharedPreferences mPrefs;
    private Editor mPrefsEditor;
    int mTagTechnology;
    String[] mVcardMimeList;

    /* renamed from: com.android.nfc.NfcRootActivity.1 */
    class C00091 implements OnClickListener {
        C00091() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcRootActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcRootActivity.2 */
    class C00102 implements OnClickListener {
        final /* synthetic */ LinearLayout val$linear;

        C00102(LinearLayout linearLayout) {
            this.val$linear = linearLayout;
        }

        public void onClick(DialogInterface dialog, int which) {
            try {
                if (((CheckBox) this.val$linear.findViewById(C0027R.id.browser_checkbox)).isChecked()) {
                    NfcRootActivity.this.mPrefsEditor.putBoolean(NfcRootActivity.PREF_BROWSER_NEVER_ASK, true);
                    NfcRootActivity.this.mPrefsEditor.apply();
                }
                NfcRootActivity.this.startActivityAsUser(NfcRootActivity.this.mLaunchIntent, UserHandle.CURRENT);
            } catch (ActivityNotFoundException e) {
            }
            NfcRootActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcRootActivity.3 */
    class C00113 implements OnClickListener {
        C00113() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcRootActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcRootActivity.4 */
    class C00124 implements OnClickListener {
        final /* synthetic */ LinearLayout val$linear;

        C00124(LinearLayout linearLayout) {
            this.val$linear = linearLayout;
        }

        public void onClick(DialogInterface dialog, int which) {
            try {
                if (((CheckBox) this.val$linear.findViewById(C0027R.id.contact_checkbox)).isChecked()) {
                    NfcRootActivity.this.mPrefsEditor.putBoolean(NfcRootActivity.PREF_CONTACT_NEVER_ASK, true);
                    NfcRootActivity.this.mPrefsEditor.apply();
                }
                NfcRootActivity.this.startActivityAsUser(NfcRootActivity.this.mLaunchIntent, UserHandle.CURRENT);
            } catch (ActivityNotFoundException e) {
            }
            NfcRootActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcRootActivity.5 */
    class C00135 implements OnClickListener {
        C00135() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcRootActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.NfcRootActivity.6 */
    class C00146 implements OnClickListener {
        C00146() {
        }

        public void onClick(DialogInterface dialog, int which) {
            NfcRootActivity.this.finish();
        }
    }

    public NfcRootActivity() {
        String[] strArr = new String[VCARD_DIALOG];
        strArr[0] = "http";
        strArr[BROWSER_DIALOG] = "https";
        this.mBrowerSchemeList = strArr;
        strArr = new String[INVALID_DIALOG];
        strArr[0] = "text/vcard";
        strArr[BROWSER_DIALOG] = "text/x-vcard";
        strArr[VCARD_DIALOG] = "text/x-vCard";
        this.mVcardMimeList = strArr;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mIntent = getIntent();
        if (this.mIntent != null && this.mIntent.getBooleanExtra(EXTRA_INVALID_TAG, false)) {
            Log.d(TAG, "Display invalid tag popup");
            Log.d(TAG, "technology: " + this.mTagTechnology);
            this.mTagTechnology = this.mIntent.getIntExtra(EXTRA_UNSUPPORTED_TECH, -1);
            if (this.mTagTechnology != -1) {
                showDialog(UNSUPPORTED_DIALOG);
            } else {
                showDialog(INVALID_DIALOG);
            }
        } else if (this.mIntent == null || !this.mIntent.hasExtra(EXTRA_LAUNCH_INTENT)) {
            finish();
        } else {
            this.mLaunchIntent = (Intent) this.mIntent.getParcelableExtra(EXTRA_LAUNCH_INTENT);
            if (this.mLaunchIntent != null) {
                String popup = CscFeature.getInstance().getString("CscFeature_NFC_EnableSecurityPromptPopup", "none");
                Log.d(TAG, "tag value : " + popup);
                Uri intentData = this.mLaunchIntent.getData();
                String intentType = this.mLaunchIntent.getType();
                if (isValidPopFeature(popup)) {
                    this.mPrefs = getSharedPreferences(PREF, 0);
                    this.mPrefsEditor = this.mPrefs.edit();
                    if (intentData == null || !checkSchemList(intentData.getScheme()) || this.mPrefs.getBoolean(PREF_BROWSER_NEVER_ASK, false)) {
                        if (intentType != null && checkVCardList(intentType) && !this.mPrefs.getBoolean(PREF_CONTACT_NEVER_ASK, false) && (popup.toLowerCase().contains("all") || popup.toLowerCase().contains("contact"))) {
                            this.mFName = this.mLaunchIntent.getStringExtra("mFNname");
                            showDialog(VCARD_DIALOG);
                            return;
                        }
                    } else if (popup.toLowerCase().contains("all") || popup.toLowerCase().contains("browser")) {
                        Resources resources = getResources();
                        Object[] objArr = new Object[BROWSER_DIALOG];
                        objArr[0] = this.mLaunchIntent.getData().toString();
                        this.mMessage = resources.getString(C0027R.string.browser_warning_message, objArr).replaceAll("%", "%%");
                        showDialog(BROWSER_DIALOG);
                        return;
                    }
                }
                try {
                    startActivityAsUser(this.mLaunchIntent, UserHandle.CURRENT);
                } catch (ActivityNotFoundException e) {
                }
                finish();
                return;
            }
            finish();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    protected Dialog onCreateDialog(int id) {
        LinearLayout linear;
        switch (id) {
            case BROWSER_DIALOG /*1*/:
                linear = (LinearLayout) View.inflate(this, C0027R.layout.browser_dialog, null);
                ((TextView) linear.findViewById(C0027R.id.browser_dialog_message)).setText(String.format(this.mMessage, new Object[0]));
                return new Builder(this).setTitle(C0027R.string.browser_title).setView(linear).setPositiveButton(17039370, new C00102(linear)).setNegativeButton(17039360, new C00091()).create();
            case VCARD_DIALOG /*2*/:
                linear = (LinearLayout) View.inflate(this, C0027R.layout.contact_dialog, null);
                Builder title = new Builder(this).setTitle(C0027R.string.contact_title);
                Resources resources = getResources();
                Object[] objArr = new Object[BROWSER_DIALOG];
                objArr[0] = this.mFName;
                return title.setMessage(String.format(resources.getString(C0027R.string.contact_warning_message, objArr), new Object[0])).setView(linear).setPositiveButton(17039370, new C00124(linear)).setNegativeButton(17039360, new C00113()).create();
            case INVALID_DIALOG /*3*/:
                return new Builder(this).setTitle(C0027R.string.error).setMessage(C0027R.string.invalid_popup).setPositiveButton(17039370, new C00135()).create();
            case UNSUPPORTED_DIALOG /*4*/:
                return new Builder(this).setTitle(C0027R.string.error).setMessage(C0027R.string.unsupport_mifare_msg).setPositiveButton(17039370, new C00146()).create();
            default:
                return null;
        }
    }

    boolean isValidPopFeature(String popup) {
        if (popup.toLowerCase().contains("all") || popup.toLowerCase().contains("browser") || popup.toLowerCase().contains("contact")) {
            return true;
        }
        return false;
    }

    boolean checkSchemList(String scheme) {
        if (scheme == null) {
            return false;
        }
        for (int i = 0; i < this.mBrowerSchemeList.length; i += BROWSER_DIALOG) {
            if (scheme.equals(this.mBrowerSchemeList[i])) {
                return true;
            }
        }
        return false;
    }

    boolean checkVCardList(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        for (int i = 0; i < this.mVcardMimeList.length; i += BROWSER_DIALOG) {
            if (mimeType.equals(this.mVcardMimeList[i])) {
                return true;
            }
        }
        return false;
    }
}
