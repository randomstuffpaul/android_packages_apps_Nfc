package com.android.nfc;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class NfcBackupAgent extends BackupAgentHelper {
    static final String SHARED_PREFS_BACKUP_KEY = "shared_prefs";

    public void onCreate() {
        addHelper(SHARED_PREFS_BACKUP_KEY, new SharedPreferencesBackupHelper(this, new String[]{NfcService.PREF}));
    }
}
