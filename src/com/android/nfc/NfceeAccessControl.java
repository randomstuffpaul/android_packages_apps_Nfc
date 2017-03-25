package com.android.nfc;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class NfceeAccessControl {
    static final boolean DBG;
    public static final String NFCEE_ACCESS_PATH = "/etc/nfcee_access.xml";
    static final String TAG = "NfceeAccess";
    final Context mContext;
    final boolean mDebugPrintSignature;
    final HashMap<Signature, String[]> mNfceeAccess;
    final HashMap<Integer, Boolean> mUidCache;

    static {
        DBG = NfcService.DBG;
    }

    NfceeAccessControl(Context context) {
        this.mContext = context;
        this.mNfceeAccess = new HashMap();
        this.mUidCache = new HashMap();
        this.mDebugPrintSignature = parseNfceeAccess();
    }

    public boolean check(int uid, String pkg) {
        boolean booleanValue;
        synchronized (this) {
            Boolean cached = (Boolean) this.mUidCache.get(Integer.valueOf(uid));
            if (cached != null) {
                booleanValue = cached.booleanValue();
            } else {
                booleanValue = DBG;
                String[] arr$ = this.mContext.getPackageManager().getPackagesForUid(uid);
                int len$ = arr$.length;
                int i$ = 0;
                while (i$ < len$) {
                    if (arr$[i$].equals(pkg)) {
                        if (checkPackageNfceeAccess(pkg)) {
                            booleanValue = true;
                        }
                        this.mUidCache.put(Integer.valueOf(uid), Boolean.valueOf(booleanValue));
                    } else {
                        i$++;
                    }
                }
                this.mUidCache.put(Integer.valueOf(uid), Boolean.valueOf(booleanValue));
            }
        }
        return booleanValue;
    }

    public boolean check(ApplicationInfo info) {
        boolean booleanValue;
        synchronized (this) {
            Boolean access = (Boolean) this.mUidCache.get(Integer.valueOf(info.uid));
            if (access == null) {
                access = Boolean.valueOf(checkPackageNfceeAccess(info.packageName));
                this.mUidCache.put(Integer.valueOf(info.uid), access);
            }
            booleanValue = access.booleanValue();
        }
        return booleanValue;
    }

    public void invalidateCache() {
        synchronized (this) {
            this.mUidCache.clear();
        }
    }

    boolean checkPackageNfceeAccess(String pkg) {
        try {
            PackageInfo info = this.mContext.getPackageManager().getPackageInfo(pkg, 64);
            if (info.signatures == null) {
                return DBG;
            }
            Signature s;
            int i$;
            Signature[] signatureArr = info.signatures;
            int len$ = signatureArr.length;
            int i$2 = 0;
            while (i$2 < len$) {
                s = signatureArr[i$2];
                if (s != null) {
                    String[] packages = (String[]) this.mNfceeAccess.get(s);
                    if (packages == null) {
                        continue;
                    } else if (packages.length == 0) {
                        if (DBG) {
                            Log.d(TAG, "Granted NFCEE access to " + pkg + " (wildcard)");
                        }
                        return true;
                    } else {
                        for (String p : packages) {
                            if (pkg.equals(p)) {
                                if (DBG) {
                                    Log.d(TAG, "Granted access to " + pkg + " (explicit)");
                                }
                                return true;
                            }
                        }
                        continue;
                    }
                }
                i$2++;
            }
            if (this.mDebugPrintSignature) {
                Log.w(TAG, "denied NFCEE access for " + pkg + " with signature:");
                for (Signature s2 : info.signatures) {
                    if (s2 != null) {
                        Log.w(TAG, s2.toCharsString());
                    }
                }
            } else {
                i$ = i$2;
            }
            return DBG;
        } catch (NameNotFoundException e) {
        }
    }

    boolean parseNfceeAccess() {
        XmlPullParserException e;
        Throwable th;
        File file = new File(Environment.getRootDirectory(), NFCEE_ACCESS_PATH);
        FileReader fileReader = null;
        boolean debug = DBG;
        try {
            InputStreamReader reader = new FileReader(file);
            try {
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(reader);
                ArrayList<String> packages = new ArrayList();
                Signature signature = null;
                parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", DBG);
                while (true) {
                    int event = parser.next();
                    String tag = parser.getName();
                    int i;
                    if (event == 2 && "signer".equals(tag)) {
                        signature = null;
                        packages.clear();
                        for (i = 0; i < parser.getAttributeCount(); i++) {
                            if ("android:signature".equals(parser.getAttributeName(i))) {
                                signature = new Signature(parser.getAttributeValue(i));
                                break;
                            }
                        }
                        if (signature == null) {
                            Log.w(TAG, "signer tag is missing android:signature attribute, igorning");
                        } else if (this.mNfceeAccess.containsKey(signature)) {
                            Log.w(TAG, "duplicate signature, ignoring");
                            signature = null;
                        }
                    } else if (event != 3 || !"signer".equals(tag)) {
                        if (event == 2) {
                            if ("package".equals(tag)) {
                                if (signature == null) {
                                    Log.w(TAG, "ignoring unnested packge tag");
                                } else {
                                    String name = null;
                                    for (i = 0; i < parser.getAttributeCount(); i++) {
                                        if ("android:name".equals(parser.getAttributeName(i))) {
                                            name = parser.getAttributeValue(i);
                                            break;
                                        }
                                    }
                                    if (name == null) {
                                        Log.w(TAG, "package missing android:name, ignoring signer group");
                                        signature = null;
                                    } else if (packages.contains(name)) {
                                        Log.w(TAG, "duplicate package name in signer group, ignoring");
                                    } else {
                                        packages.add(name);
                                    }
                                }
                            }
                        }
                        if (event == 2 && "debug".equals(tag)) {
                            debug = true;
                        } else if (event == 1) {
                            break;
                        }
                    } else if (signature == null) {
                        Log.w(TAG, "mis-matched signer tag");
                    } else {
                        this.mNfceeAccess.put(signature, packages.toArray(new String[0]));
                        packages.clear();
                    }
                }
                if (reader != null) {
                    InputStreamReader inputStreamReader;
                    try {
                        reader.close();
                        inputStreamReader = reader;
                    } catch (IOException e2) {
                        inputStreamReader = reader;
                    }
                }
            } catch (XmlPullParserException e3) {
                e = e3;
                fileReader = reader;
            } catch (FileNotFoundException e4) {
                fileReader = reader;
            } catch (IOException e5) {
                e = e5;
                fileReader = reader;
            } catch (Throwable th2) {
                th = th2;
                fileReader = reader;
            }
        } catch (XmlPullParserException e6) {
            e = e6;
            try {
                Log.w(TAG, "failed to load NFCEE access list", e);
                this.mNfceeAccess.clear();
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e7) {
                    }
                }
                Log.i(TAG, "read " + this.mNfceeAccess.size() + " signature(s) for NFCEE access");
                return debug;
            } catch (Throwable th3) {
                th = th3;
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e8) {
                    }
                }
                throw th;
            }
        } catch (FileNotFoundException e9) {
            Log.w(TAG, "could not find /etc/nfcee_access.xml, no NFCEE access allowed");
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e10) {
                }
            }
            Log.i(TAG, "read " + this.mNfceeAccess.size() + " signature(s) for NFCEE access");
            return debug;
        } catch (IOException e11) {
            e = e11;
            Log.e(TAG, "Failed to load NFCEE access list", e);
            this.mNfceeAccess.clear();
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e12) {
                }
            }
            Log.i(TAG, "read " + this.mNfceeAccess.size() + " signature(s) for NFCEE access");
            return debug;
        }
        Log.i(TAG, "read " + this.mNfceeAccess.size() + " signature(s) for NFCEE access");
        return debug;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mNfceeAccess=");
        for (Signature s : this.mNfceeAccess.keySet()) {
            pw.printf("\t%s [", new Object[]{s.toCharsString()});
            int len$ = ((String[]) this.mNfceeAccess.get(s)).length;
            for (int i$ = 0; i$ < len$; i$++) {
                pw.printf("%s, ", new Object[]{arr$[i$]});
            }
            pw.println("]");
        }
        synchronized (this) {
            pw.println("mNfceeUidCache=");
            for (Integer uid : this.mUidCache.keySet()) {
                Boolean b = (Boolean) this.mUidCache.get(uid);
                pw.printf("\t%d %s\n", new Object[]{(Integer) r2.next(), b});
            }
        }
    }
}
