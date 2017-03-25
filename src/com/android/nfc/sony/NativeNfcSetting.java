package com.android.nfc.sony;

import android.util.Log;
import com.samsung.android.sdk.cover.ScoverState;

public class NativeNfcSetting {
    private static final byte BIT_0 = (byte) 1;
    private static final byte BIT_1 = (byte) 2;
    private static final byte BIT_2 = (byte) 4;
    private static final byte BIT_3 = (byte) 8;
    private static final byte BIT_4 = (byte) 16;
    private static final byte BIT_5 = (byte) 32;
    private static final byte BIT_ALL = (byte) 15;
    private static final byte BIT_OFF = (byte) 0;
    private static final boolean DBG = false;
    private static final int INT_DEFAULT = 0;
    private static final int INT_TYPE_A = 1;
    private static final int INT_TYPE_ALL = 4;
    private static final int INT_TYPE_F212 = 2;
    private static final int INT_TYPE_F424 = 3;
    private static final byte RF_REG_14 = (byte) 40;
    private static final byte RF_REG_15 = (byte) 48;
    private static final byte RF_REG_6 = (byte) 32;
    private static final byte RF_REG_7 = (byte) 36;
    private static final byte RF_REG_8 = (byte) 52;
    private static final String TAG = "NativeNfcSetting";
    private static final int TGT_DEFAULT = 0;
    private static final int TGT_TYPE_ALL = 3;
    private static final int TGT_TYPE_A_WAIT = 1;
    private static final int TGT_TYPE_F_WAIT = 2;
    private byte mAutoPollOffListen;
    private byte mListenChangeSwitch;
    private int mListenTime;
    private int[] mPollGapListenTime;
    private int[] mPollTimeRfOnToPoll;
    private byte mPolloingChangeSwitch;
    private int mReg14Data;
    private int mReg15Data;
    private int mReg6Data;
    private int mReg7Data;
    private int mReg8Data;
    private byte mRegAddress;
    private byte mSelectPollType;
    private byte mSpdTime;
    private byte mTypeAWait;
    private boolean mTypeBWait;
    private byte mTypeFWait;

    private native int nativeRfParameter(byte[] bArr);

    private native int nativeSetListenParameter(byte b, int[] iArr, int i, byte b2, byte b3, boolean z, byte b4);

    private native int nativeSetPollParameter(byte b, byte b2, int[] iArr);

    public NativeNfcSetting() {
        initPollingParam();
        initListenParam();
        initRfParam();
    }

    private void initPollingParam() {
        this.mPolloingChangeSwitch = BIT_OFF;
        this.mSelectPollType = BIT_ALL;
        this.mPollTimeRfOnToPoll = new int[INT_TYPE_ALL];
        this.mPollTimeRfOnToPoll[TGT_DEFAULT] = 6;
        this.mPollTimeRfOnToPoll[TGT_TYPE_A_WAIT] = 6;
        this.mPollTimeRfOnToPoll[TGT_TYPE_F_WAIT] = 21;
        this.mPollTimeRfOnToPoll[TGT_TYPE_ALL] = 21;
    }

    private void initListenParam() {
        this.mListenChangeSwitch = BIT_OFF;
        this.mPollGapListenTime = new int[TGT_TYPE_ALL];
        this.mPollGapListenTime[TGT_DEFAULT] = 10;
        this.mPollGapListenTime[TGT_TYPE_A_WAIT] = 10;
        this.mPollGapListenTime[TGT_TYPE_F_WAIT] = 10;
        this.mListenTime = 15;
        this.mTypeAWait = BIT_2;
        this.mTypeFWait = BIT_OFF;
        this.mTypeBWait = true;
        this.mAutoPollOffListen = (byte) 7;
    }

    private void initRfParam() {
        this.mRegAddress = BIT_OFF;
        this.mReg6Data = 67896383;
        this.mReg7Data = 339678015;
        this.mReg8Data = 1610612736;
        this.mReg14Data = 522715136;
        this.mReg15Data = 671612928;
    }

    public synchronized boolean setParameter(int index, int value) {
        boolean result;
        switch (index) {
            case TGT_DEFAULT /*0*/:
            case TGT_TYPE_A_WAIT /*1*/:
            case TGT_TYPE_F_WAIT /*2*/:
            case TGT_TYPE_ALL /*3*/:
                result = setPollSetting(BIT_0, index, value);
                break;
            case INT_TYPE_ALL /*4*/:
            case ScoverState.TYPE_S_CHARGER_COVER /*5*/:
            case ScoverState.COLOR_INDIGO_BLUE /*6*/:
            case ScoverState.COLOR_PLUM_RED /*7*/:
                result = setPollSetting(BIT_1, index, value);
                break;
            case ScoverState.COLOR_MINT_BLUE /*8*/:
            case ScoverState.COLOR_MUSTARD_YELLOW /*9*/:
            case ScoverState.COLOR_PEAKCOCK_GREEN /*10*/:
                result = setListenSetting(BIT_0, index, value);
                break;
            case ScoverState.COLOR_ROSE_GOLD /*11*/:
                result = setListenSetting(BIT_1, index, value);
                break;
            case ScoverState.COLOR_PINK /*12*/:
                result = setListenSetting(BIT_2, index, value);
                break;
            case ScoverState.COLOR_PEARL_WHITE /*13*/:
                result = setListenSetting(BIT_3, index, value);
                break;
            case 14:
                result = setListenSetting(BIT_4, index, value);
                break;
            case 15:
                result = setListenSetting(RF_REG_6, index, value);
                break;
            case 16:
            case 17:
            case 18:
            case 19:
            case 21:
            case 22:
            case 25:
            case 29:
                result = setRfSetting(RF_REG_6, index, value);
                break;
            case 20:
            case 23:
            case 24:
                result = setRfSetting(RF_REG_7, index, value);
                break;
            case 26:
            case 27:
            case 28:
                result = setRfSetting(RF_REG_8, index, value);
                break;
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
                result = setRfSetting(RF_REG_14, index, value);
                break;
            case 36:
            case 37:
            case 38:
                result = setRfSetting(RF_REG_15, index, value);
                break;
        }
        return true;
    }

    private boolean setPollSetting(byte switchBit, int index, int value) {
        this.mPolloingChangeSwitch = (byte) (this.mPolloingChangeSwitch | switchBit);
        switch (index) {
            case TGT_DEFAULT /*0*/:
                if (value == 0) {
                    this.mSelectPollType = (byte) (this.mSelectPollType & -2);
                    break;
                }
                this.mSelectPollType = (byte) (this.mSelectPollType | TGT_TYPE_A_WAIT);
                break;
            case TGT_TYPE_A_WAIT /*1*/:
                if (value == 0) {
                    this.mSelectPollType = (byte) (this.mSelectPollType & -3);
                    break;
                }
                this.mSelectPollType = (byte) (this.mSelectPollType | TGT_TYPE_F_WAIT);
                break;
            case TGT_TYPE_F_WAIT /*2*/:
                if (value == 0) {
                    this.mSelectPollType = (byte) (this.mSelectPollType & -5);
                    break;
                }
                this.mSelectPollType = (byte) (this.mSelectPollType | INT_TYPE_ALL);
                break;
            case TGT_TYPE_ALL /*3*/:
                if (value == 0) {
                    this.mSelectPollType = (byte) (this.mSelectPollType & -9);
                    break;
                }
                this.mSelectPollType = (byte) (this.mSelectPollType | 8);
                break;
            case INT_TYPE_ALL /*4*/:
                this.mPollTimeRfOnToPoll[TGT_DEFAULT] = value;
                break;
            case ScoverState.TYPE_S_CHARGER_COVER /*5*/:
                this.mPollTimeRfOnToPoll[TGT_TYPE_A_WAIT] = value;
                break;
            case ScoverState.COLOR_INDIGO_BLUE /*6*/:
                this.mPollTimeRfOnToPoll[TGT_TYPE_F_WAIT] = value;
                break;
            case ScoverState.COLOR_PLUM_RED /*7*/:
                this.mPollTimeRfOnToPoll[TGT_TYPE_ALL] = value;
                break;
        }
        return true;
    }

    private boolean setListenSetting(byte switchBit, int index, int value) {
        this.mListenChangeSwitch = (byte) (this.mListenChangeSwitch | switchBit);
        switch (index) {
            case ScoverState.COLOR_MINT_BLUE /*8*/:
                this.mPollGapListenTime[TGT_DEFAULT] = value;
                break;
            case ScoverState.COLOR_MUSTARD_YELLOW /*9*/:
                this.mPollGapListenTime[TGT_TYPE_A_WAIT] = value;
                break;
            case ScoverState.COLOR_PEAKCOCK_GREEN /*10*/:
                this.mPollGapListenTime[TGT_TYPE_F_WAIT] = value;
                break;
            case ScoverState.COLOR_ROSE_GOLD /*11*/:
                this.mListenTime = value;
                break;
            case ScoverState.COLOR_PINK /*12*/:
                this.mTypeAWait = (byte) value;
                break;
            case ScoverState.COLOR_PEARL_WHITE /*13*/:
                this.mTypeFWait = (byte) value;
                break;
            case 14:
                if (value == 0) {
                    this.mTypeBWait = DBG;
                    break;
                }
                this.mTypeBWait = true;
                break;
            case 15:
                this.mAutoPollOffListen = (byte) value;
                break;
        }
        return true;
    }

    private boolean setRfSetting(byte address, int index, int value) {
        this.mRegAddress = address;
        if (address == 32) {
            if (index == 25) {
                this.mReg6Data &= -939524097;
                this.mReg6Data |= value << 27;
            } else if (index == 21) {
                this.mReg6Data &= -117440513;
                this.mReg6Data |= value << 24;
            } else if (index == 16) {
                this.mReg6Data &= -1572865;
                this.mReg6Data |= value << 19;
            } else if (index == 17) {
                this.mReg6Data &= -458753;
                this.mReg6Data |= value << 16;
            } else if (index == 18) {
                this.mReg6Data &= -6145;
                this.mReg6Data |= value << 11;
            } else if (index == 19) {
                this.mReg6Data &= -1793;
                this.mReg6Data |= value << 8;
            } else if (index == 22) {
                this.mReg6Data &= -64;
                this.mReg6Data |= value;
            } else if (index == 29) {
                this.mReg6Data &= -8388609;
                this.mReg6Data |= value << 23;
            }
        } else if (address == 36) {
            if (index == 23) {
                this.mReg7Data &= -1056964609;
                this.mReg7Data |= value << 24;
            } else if (index == 24) {
                this.mReg7Data &= -4128769;
                this.mReg7Data |= value << 16;
            } else if (index == 20) {
                this.mReg7Data &= -49153;
                this.mReg7Data |= value << 14;
            }
        } else if (address == 52) {
            if (index == 26) {
                this.mReg8Data &= -1073741825;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg8Data |= 1073741824;
                }
            } else if (index == 27) {
                this.mReg8Data &= -536870913;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg8Data |= 536870912;
                }
            } else if (index == 28) {
                this.mReg8Data &= -134217729;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg8Data |= value << 27;
                }
            }
        } else if (address == 40) {
            if (index == 30) {
                this.mReg14Data &= Integer.MAX_VALUE;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg14Data |= value << 31;
                }
            } else if (index == 31) {
                this.mReg14Data &= -1073741825;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg14Data |= value << 30;
                }
            } else if (index == 32) {
                this.mReg14Data &= -1056964609;
                this.mReg14Data |= value << 24;
            } else if (index == 33) {
                this.mReg14Data &= -8388609;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg14Data |= value << 23;
                }
            } else if (index == 34) {
                this.mReg14Data &= -4194305;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg14Data |= value << 22;
                }
            } else if (index == 35) {
                this.mReg14Data &= -4128769;
                this.mReg14Data |= value << 16;
            }
        } else if (address == 48) {
            if (index == 36) {
                this.mReg15Data &= Integer.MAX_VALUE;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg15Data |= value << 31;
                }
            } else if (index == 37) {
                this.mReg15Data &= -1073741825;
                if (value == TGT_TYPE_A_WAIT) {
                    this.mReg15Data |= value << 30;
                }
            } else if (index == 38) {
                this.mReg15Data &= -1056964609;
                this.mReg15Data |= value << 24;
            }
        }
        return true;
    }

    public boolean changeParameter(int target) {
        if (target == TGT_TYPE_A_WAIT) {
            return changePollParam();
        }
        if (target == TGT_TYPE_F_WAIT) {
            return changeListenParam();
        }
        if (target == TGT_TYPE_ALL) {
            return changeRfParam();
        }
        return DBG;
    }

    private boolean changePollParam() {
        int ret = nativeSetPollParameter(this.mPolloingChangeSwitch, this.mSelectPollType, this.mPollTimeRfOnToPoll);
        if (ret == 0) {
            return true;
        }
        Log.e(TAG, "Error!!! nativeSetPollParameter() return = " + ret);
        return DBG;
    }

    private boolean changeListenParam() {
        int ret = nativeSetListenParameter(this.mListenChangeSwitch, this.mPollGapListenTime, this.mListenTime, this.mTypeAWait, this.mTypeFWait, this.mTypeBWait, this.mAutoPollOffListen);
        if (ret == 0) {
            return true;
        }
        Log.e(TAG, "Error!!! nativeSetListenParameter() return = " + ret);
        return DBG;
    }

    private boolean changeRfParam() {
        byte[] reg6Data = createRegData(RF_REG_6, this.mReg6Data);
        byte[] reg7Data = createRegData(RF_REG_7, this.mReg7Data);
        byte[] reg8Data = createRegData(RF_REG_8, this.mReg8Data);
        byte[] reg14Data = createRegData(RF_REG_14, this.mReg14Data);
        byte[] reg15Data = createRegData(RF_REG_15, this.mReg15Data);
        if (nativeRfParameter(concat(reg6Data, reg7Data, reg8Data, reg14Data, reg15Data)) != 0) {
            return DBG;
        }
        return true;
    }

    private byte[] createRegData(byte address, int value) {
        return new byte[]{BIT_OFF, address, (byte) ((value >>> 24) & 255), (byte) ((value >>> 16) & 255), (byte) ((value >>> 8) & 255), (byte) ((value >>> TGT_DEFAULT) & 255)};
    }

    private byte[] concat(byte[]... arrays) {
        int i$;
        int length = TGT_DEFAULT;
        byte[][] arr$ = arrays;
        int len$ = arr$.length;
        for (i$ = TGT_DEFAULT; i$ < len$; i$ += TGT_TYPE_A_WAIT) {
            length += arr$[i$].length;
        }
        byte[] result = new byte[length];
        int pos = TGT_DEFAULT;
        arr$ = arrays;
        len$ = arr$.length;
        for (i$ = TGT_DEFAULT; i$ < len$; i$ += TGT_TYPE_A_WAIT) {
            byte[] array = arr$[i$];
            System.arraycopy(array, TGT_DEFAULT, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    public void setP2pModes(int initiatorModes, int targetModes) {
        initPollingParam();
        initListenParam();
        switch (initiatorModes) {
            case TGT_DEFAULT /*0*/:
                setParameter(TGT_DEFAULT, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_A_WAIT, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_ALL, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_F_WAIT, TGT_TYPE_A_WAIT);
                break;
            case TGT_TYPE_A_WAIT /*1*/:
                setParameter(TGT_DEFAULT, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_A_WAIT, TGT_DEFAULT);
                setParameter(TGT_TYPE_ALL, TGT_DEFAULT);
                setParameter(TGT_TYPE_F_WAIT, TGT_DEFAULT);
                break;
            case TGT_TYPE_F_WAIT /*2*/:
                setParameter(TGT_DEFAULT, TGT_DEFAULT);
                setParameter(TGT_TYPE_A_WAIT, TGT_DEFAULT);
                setParameter(TGT_TYPE_ALL, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_F_WAIT, TGT_DEFAULT);
                break;
            case TGT_TYPE_ALL /*3*/:
                setParameter(TGT_DEFAULT, TGT_DEFAULT);
                setParameter(TGT_TYPE_A_WAIT, TGT_DEFAULT);
                setParameter(TGT_TYPE_ALL, TGT_DEFAULT);
                setParameter(TGT_TYPE_F_WAIT, TGT_TYPE_A_WAIT);
                break;
            case INT_TYPE_ALL /*4*/:
                setParameter(TGT_DEFAULT, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_A_WAIT, TGT_DEFAULT);
                setParameter(TGT_TYPE_ALL, TGT_TYPE_A_WAIT);
                setParameter(TGT_TYPE_F_WAIT, TGT_TYPE_A_WAIT);
                break;
            default:
                return;
        }
        switch (targetModes) {
            case TGT_DEFAULT /*0*/:
                setParameter(12, TGT_TYPE_F_WAIT);
                setParameter(13, TGT_DEFAULT);
                setParameter(14, TGT_TYPE_A_WAIT);
                break;
            case TGT_TYPE_A_WAIT /*1*/:
                setParameter(12, TGT_TYPE_A_WAIT);
                setParameter(13, TGT_TYPE_F_WAIT);
                setParameter(14, TGT_DEFAULT);
                break;
            case TGT_TYPE_F_WAIT /*2*/:
                setParameter(12, INT_TYPE_ALL);
                setParameter(13, TGT_TYPE_A_WAIT);
                setParameter(14, TGT_DEFAULT);
                break;
            case TGT_TYPE_ALL /*3*/:
                setParameter(12, TGT_TYPE_A_WAIT);
                setParameter(13, TGT_TYPE_A_WAIT);
                setParameter(14, TGT_DEFAULT);
                break;
            default:
                return;
        }
        changePollParam();
        changeListenParam();
    }
}
