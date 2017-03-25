package com.samsung.android.sdk;

public class SsdkUnsupportedException extends Exception {
    public static final int DEVICE_NOT_SUPPORTED = 1;
    public static final int SDK_VERSION_MISMATCH = 2;
    public static final int VENDOR_NOT_SUPPORTED = 0;
    private String mPackageName;
    private int mType;
    private int mVersionCode;

    public SsdkUnsupportedException(String s, int type) {
        super(s);
        this.mType = 0;
        this.mVersionCode = 0;
        this.mPackageName = null;
        this.mType = type;
    }

    public SsdkUnsupportedException(String s, int type, String packageName, int versionCode) {
        super(s);
        this.mType = 0;
        this.mVersionCode = 0;
        this.mPackageName = null;
        this.mType = type;
        this.mPackageName = packageName;
        this.mVersionCode = versionCode;
    }

    public int getType() {
        return this.mType;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public int getVersionCode() {
        return this.mVersionCode;
    }
}
