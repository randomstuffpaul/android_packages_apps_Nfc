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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AndroidBeamPopUp extends Activity {
    private static final String TAG = "[ABeam]";
    private static final String TAGClass = "AndroidBeamPopUp: ";
    private String mAlert_Message;
    private String mMode_Check;
    private Builder mPopup;
    private AlertDialog mS_Beam_Popup;
    private TextView textView_Message;

    /* renamed from: com.android.nfc.AndroidBeamPopUp.1 */
    class C00001 implements OnClickListener {
        C00001() {
        }

        public void onClick(DialogInterface dialog, int which) {
            AndroidBeamPopUp.this.finish();
        }
    }

    /* renamed from: com.android.nfc.AndroidBeamPopUp.2 */
    class C00012 implements OnKeyListener {
        C00012() {
        }

        public boolean onKey(DialogInterface dialog, int mKeyCode, KeyEvent mKeyEvent) {
            if (mKeyEvent.getAction() == 1 && mKeyCode == 4) {
                AndroidBeamPopUp.this.finish();
            }
            return false;
        }
    }

    public AndroidBeamPopUp() {
        this.mS_Beam_Popup = null;
        this.mPopup = null;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "AndroidBeamPopUp:  onCreate ");
        this.mMode_Check = getIntent().getStringExtra("POPUP_MODE");
        Log.d(TAG, "AndroidBeamPopUp:  mMode_Check  = " + this.mMode_Check);
        if (this.mMode_Check == null) {
            finish();
        }
        this.mPopup = new Builder(this);
        if ("no_file_selected".equals(this.mMode_Check)) {
            this.mAlert_Message = getString(C0027R.string.filepath_null_popup);
            this.mPopup.setTitle(C0027R.string.no_files_selected_popup_label);
        } else if ("from_cloud_file".equals(this.mMode_Check)) {
            this.mAlert_Message = getString(C0027R.string.from_cloud);
            this.mPopup.setTitle(C0027R.string.unable_to_share_file_popup_label);
        } else if ("from_drm_file".equals(this.mMode_Check)) {
            this.mAlert_Message = getString(C0027R.string.frem_drm);
            this.mPopup.setTitle(C0027R.string.unable_to_share_file_popup_label);
        } else if ("does_not_saved".equals(this.mMode_Check)) {
            this.mAlert_Message = getString(C0027R.string.not_save);
            this.mPopup.setTitle(C0027R.string.unable_to_share_file_popup_label);
        } else if ("disk_full".equals(this.mMode_Check)) {
            this.mAlert_Message = getString(C0027R.string.disk_full);
            this.mPopup.setTitle(C0027R.string.device_storage_full_popup_label);
        } else {
            finish();
        }
        LinearLayout linear = (LinearLayout) View.inflate(this, C0027R.layout.pop_up, null);
        this.textView_Message = (TextView) linear.findViewById(C0027R.id.true_popup);
        this.textView_Message.setText(this.mAlert_Message);
        this.mPopup.setView(linear);
        this.mPopup.setNegativeButton(C0027R.string.btn_ok, new C00001());
        this.mS_Beam_Popup = this.mPopup.create();
        this.mS_Beam_Popup.setCanceledOnTouchOutside(false);
        this.mS_Beam_Popup.setOnKeyListener(new C00012());
        try {
            Log.d(TAG, "AndroidBeamPopUp:  PopUp show ");
            this.mS_Beam_Popup.show();
        } catch (Exception e) {
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AndroidBeamPopUp:  onDestroy ");
        if (this.mS_Beam_Popup != null) {
            this.mS_Beam_Popup.dismiss();
        }
    }
}
