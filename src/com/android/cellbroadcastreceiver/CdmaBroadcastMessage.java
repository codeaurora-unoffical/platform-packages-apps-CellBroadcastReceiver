/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import com.android.internal.telephony.cdma.SmsMessage;
import com.android.internal.telephony.gsm.SmsCbHeader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.telephony.SmsCbConstants;
import android.telephony.EmergencyMessage.Certainty;
import android.telephony.EmergencyMessage.Severity;
import android.telephony.EmergencyMessage.Urgency;



/**
 * Application wrapper for {@link SmsMessage}. This is Parcelable so that
 * decoded broadcast message objects can be passed between running Services.
 * New broadcasts are received by {@link CellBroadcastReceiver},
 * displayed by {@link CellBroadcastAlertService}, and saved to SQLite by
 * {@link CellBroadcastDatabaseService}.
 */
public class CdmaBroadcastMessage extends BroadcastMessage {

    /** Identifier for getExtra() when adding CdmaBroadcastMessage object to an Intent. */
    public static final String SMS_CDMA_MESSAGE_EXTRA =
            "com.android.cellbroadcastreceiver.CDMA_SMS_MESSAGE";

    // system property defining the emergency cdma channel ranges
    // Note: key name cannot exceeds 32 chars.
    static final String EMERGENCY_BROADCAST_RANGE =
            "ro.cb.cdma.emergencyids";

    private final Severity mSeverity;
    private final Urgency mUrgency;
    private final Certainty mCertainty;
    private final int mLanguageCodeInt;

    //fixme maybe move those constants to another file?
    // SmsEnvelope has EMERGENCY_MESSAGE_ID_START and END and END doesn't include
    // future extension
    //C.R1001-G 9.3.3 - Cmas message IDs
    /** Start of CMAS Message Identifier range. */
    public static final int CMAS_FIRST_IDENTIFIER = 0x1000;

    public static final int CMAS_PRESIDENTIAL = 0x1000;
    public static final int CMAS_EXTREME      = 0x1001;
    public static final int CMAS_SEVERE       = 0x1002;
    public static final int CMAS_AMBER        = 0x1003;
    public static final int CMAS_TEST         = 0x1004;

    /** End of CMAS Message Identifier range */
    public static final int CMAS_LAST_IDENTIFIER = 0x1004;

    private CdmaBroadcastMessage(SmsMessage msg) {
        this(msg.getMessageBody(),
                msg.getServiceCategory(),
                msg.getSeverity(),
                msg.getUrgency(),
                msg.getCertainty(),
                msg.getLanguage(),
                System.currentTimeMillis(),
                false);
    }

    private CdmaBroadcastMessage(String messageBody, int serviceCategory,
            Severity severity, Urgency urgency, Certainty certainty,
            int languageCode, long deliveryTime, boolean isRead) {
        super(serviceCategory, languageCodeIntToString(languageCode),
                messageBody, deliveryTime, isRead);
        mSeverity = severity;
        mUrgency = urgency;
        mCertainty = certainty;
        mLanguageCodeInt = languageCode;
    }

    public int getFormat() {
        return android.telephony.TelephonyManager.PHONE_TYPE_CDMA;
    }

    public static CdmaBroadcastMessage createFromSmsMessage(android.telephony.SmsMessage src) {
        CdmaBroadcastMessage message = null;
        if (src.mWrappedSmsMessage instanceof com.android.internal.telephony.cdma.SmsMessage) {
            message = new CdmaBroadcastMessage((
                    (SmsMessage)src.mWrappedSmsMessage));
        } else {
            // This is a problem!
            // Can't create cdma emergency message out of non-cdma sms
        }
        return message;
    }

    @Override
    public String toString() {
        return ("Cdma Broadcast Message: " + mMessageBody);
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mMessageBody);
        dest.writeInt(mMessageIdentifier);
        dest.writeInt(mSeverity.ordinal());
        dest.writeInt(mUrgency.ordinal());
        dest.writeInt(mCertainty.ordinal());
        dest.writeInt(mLanguageCodeInt);
        dest.writeLong(mDeliveryTime);
        dest.writeInt(mIsRead ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CdmaBroadcastMessage>
            CREATOR = new Parcelable.Creator<CdmaBroadcastMessage>() {
        public CdmaBroadcastMessage createFromParcel(Parcel in) {
            return new CdmaBroadcastMessage(
                    in.readString(),
                    in.readInt(),
                    Severity.values()[in.readInt()],
                    Urgency.values()[in.readInt()],
                    Certainty.values()[in.readInt()],
                    in.readInt(),
                    in.readLong(),
                    (in.readInt() != 0));
        }

        public CdmaBroadcastMessage[] newArray(int size) {
            return new CdmaBroadcastMessage[size];
        }
    };

    /**
     * Create a CellBroadcastMessage from a row in the database.
     * @param cursor an open SQLite cursor pointing to the row to read
     * @return the new CellBroadcastMessage
     */
    public static CdmaBroadcastMessage createFromCursor(Cursor cursor) {
        int messageId = cursor.getInt(CellBroadcastDatabase.COLUMN_MESSAGE_IDENTIFIER);
        String language = cursor.getString(CellBroadcastDatabase.COLUMN_LANGUAGE_CODE);
        String body = cursor.getString(CellBroadcastDatabase.COLUMN_MESSAGE_BODY);
        long deliveryTime = cursor.getLong(CellBroadcastDatabase.COLUMN_DELIVERY_TIME);
        boolean isRead = (cursor.getInt(CellBroadcastDatabase.COLUMN_MESSAGE_READ) != 0);

        Severity severity = Severity.values()[cursor.getInt(CellBroadcastDatabase.COLUMN_SEVERITY)];
        Urgency urgency = Urgency.values()[cursor.getInt(CellBroadcastDatabase.COLUMN_URGENCY)];
        Certainty certainty = Certainty.values()[cursor.getInt(CellBroadcastDatabase.COLUMN_CERTAINTY)];

        CdmaBroadcastMessage message = new CdmaBroadcastMessage(
                body, messageId, severity, urgency, certainty,
                languageCodeStringToInt(language), deliveryTime, isRead);
        return message;
    }

    /**
     * Return a ContentValues object for insertion into the database.
     * @return a new ContentValues object containing this object's data
     */
    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues(9);
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_IDENTIFIER, getMessageIdentifier());
        cv.put(CellBroadcastDatabase.Columns.LANGUAGE_CODE, getLanguageCode());
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_BODY, getMessageBody());
        cv.put(CellBroadcastDatabase.Columns.DELIVERY_TIME, getDeliveryTime());
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_READ, isRead());
        cv.put(CellBroadcastDatabase.Columns.MESSAGE_FORMAT, getFormat());
        cv.put(CellBroadcastDatabase.Columns.SEVERITY, getSeverity().ordinal());
        cv.put(CellBroadcastDatabase.Columns.URGENCY, getUrgency().ordinal());
        cv.put(CellBroadcastDatabase.Columns.CERTAINTY, getCertainty().ordinal());
        return cv;
    }

    public Severity getSeverity() {
        return mSeverity;
    }

    public Urgency getUrgency() {
        return mUrgency;
    }

    public Certainty getCertainty() {
        return mCertainty;
    }

    /**
     * Return whether the broadcast is an emergency (PWS) message type.
     * This includes lower priority test messages and Amber alerts.
     *
     * All public alerts show the flashing warning icon in the dialog,
     * but only emergency alerts play the alert sound and speak the message.
     *
     * @return true if the message is PWS type; false otherwise
     */
    public boolean isPublicAlertMessage() {
        return (isEtwsMessage() || isCmasMessage());
    }

    /**
     * Returns whether the broadcast is an emergency (PWS) message type,
     * including test messages, but excluding lower priority Amber alert broadcasts.
     *
     * @return true if the message is PWS type, excluding Amber alerts
     */
    public boolean isEmergencyAlertMessage() {
        return (isPublicAlertMessage()) &&
                getMessageIdentifier() != CMAS_AMBER;
    }

    /**
     * Return whether the broadcast is an ETWS emergency message type.
     * @return true if the message is ETWS emergency type; false otherwise
     */
    public boolean isEtwsMessage() {
        return false; //not supported in CDMA
    }

    /**
     * Return whether the broadcast is a CMAS emergency message type.
     * @return true if the message is CMAS emergency type; false otherwise
     */
    public boolean isCmasMessage() {
        return getMessageIdentifier() >= CMAS_FIRST_IDENTIFIER &&
                getMessageIdentifier() <= CMAS_LAST_IDENTIFIER;
    }

    public int getDialogTitleResource() {
        switch (getMessageIdentifier()) {
            case CdmaBroadcastMessage.CMAS_PRESIDENTIAL:
                return R.string.cmas_presidential_level_alert;

            case CdmaBroadcastMessage.CMAS_EXTREME:
                return R.string.cmas_extreme_alert;

            case CdmaBroadcastMessage.CMAS_SEVERE:
                return R.string.cmas_severe_alert;

            case CdmaBroadcastMessage.CMAS_AMBER:
                return R.string.cmas_amber_alert;

            case CdmaBroadcastMessage.CMAS_TEST:
                return R.string.cmas_required_monthly_test;

            default:
                if (isPublicAlertMessage() || isOperatorDefinedEmergencyId()) {
                    return R.string.pws_other_message_identifiers;
                } else {
                    return R.string.cb_other_message_identifiers;
                }
        }
    }

    public String getIntentExtraName() {
        return SMS_CDMA_MESSAGE_EXTRA;
    }

    private static String languageCodeIntToString(int languageCode) {
        return (languageCode == 1) ? "en" : "";
    }

    private static int languageCodeStringToInt(String languageCode) {
        if ("en".equals(languageCode)) {
            return 1;
        }
        return 0; //unspecified
    }

    boolean isOperatorDefinedEmergencyId() {
        return isOperatorDefinedEmergencyId(
                SystemProperties.get(EMERGENCY_BROADCAST_RANGE));
    }
}
