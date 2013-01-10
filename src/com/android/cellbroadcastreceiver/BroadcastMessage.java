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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;


public abstract class BroadcastMessage implements Parcelable {
    private static final String TAG = "BroadcastMessage";

    protected final int mMessageIdentifier;
    protected final String mLanguageCode;
    protected String mMessageBody;
    protected final long mDeliveryTime;
    protected boolean mIsRead = false;

    protected BroadcastMessage(int messageId, String languageCode,
            String messageBody, long deliveryTime, boolean isRead) {
        mMessageIdentifier = messageId;
        mLanguageCode = languageCode;
        mMessageBody = messageBody;
        mDeliveryTime = deliveryTime;
        mIsRead = isRead;
    }

    /** Parcelable: no special flags. */
    public int describeContents() {
        return 0;
    }

    /**
     * Create a BroadcastMessage from a row in the database.
     * @param cursor an open SQLite cursor pointing to the row to read
     * @return the new CellBroadcastMessage
     */
    public static BroadcastMessage createFromCursor(Cursor cursor) {
        int format = cursor.getInt(CellBroadcastDatabase.COLUMN_FORMAT);
        if (android.telephony.TelephonyManager.PHONE_TYPE_GSM == format) {
            return (BroadcastMessage)CellBroadcastMessage.createFromCursor(cursor);
        } else if (android.telephony.TelephonyManager.PHONE_TYPE_CDMA == format) {
            return (BroadcastMessage)CdmaBroadcastMessage.createFromCursor(cursor);
        }
        // should never come here.
        Log.e(TAG, "unknown format " + format + " in createFromCursor");
        return null;
    }

    /**
     * Return a ContentValues object for insertion into the database.
     * @return a new ContentValues object containing this object's data
     */
    public abstract ContentValues getContentValues();

    /**
     * Set or clear the "read message" flag.
     * @param isRead true if the message has been read; false if not
     */
    public void setIsRead(boolean isRead) {
        mIsRead = isRead;
    }

    public int getMessageIdentifier() {
        return mMessageIdentifier;
    }

    public String getLanguageCode() {
        return mLanguageCode;
    }

    public long getDeliveryTime() {
        return mDeliveryTime;
    }

    public String getMessageBody() {
        return mMessageBody;
    }

    /**
     * Append text to the message body.
     * @param body the text to append to this message
     */
    public void appendToMessageBody(String body) {
        mMessageBody = mMessageBody + body;
    }

    public boolean isRead() {
        return mIsRead;
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
    public abstract boolean isPublicAlertMessage();

    /**
     * Returns whether the broadcast is an emergency (PWS) message type,
     * including test messages, but excluding lower priority Amber alert broadcasts.
     *
     * @return true if the message is PWS type, excluding Amber alerts
     */
    public abstract boolean isEmergencyAlertMessage();
    /**
     * Return whether the broadcast is an ETWS emergency message type.
     * @return true if the message is ETWS emergency type; false otherwise
     */
    public abstract boolean isEtwsMessage();

    /**
     * Return whether the broadcast is a CMAS emergency message type.
     * @return true if the message is CMAS emergency type; false otherwise
     */
    public abstract boolean isCmasMessage();


    public abstract int getDialogTitleResource();

    public abstract String getIntentExtraName();

    public abstract int getFormat();

    /**
     * Return the abbreviated date string for the message delivery time.
     * @param context the context object
     * @return a String to use in the broadcast list UI
     */
    String getDateString(Context context) {
        int flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT | DateUtils.FORMAT_SHOW_TIME |
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_CAP_AMPM;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }

    /**
     * Return the date string for the message delivery time, suitable for text-to-speech.
     * @param context the context object
     * @return a String for populating the list item AccessibilityEvent for TTS
     */
    String getSpokenDateString(Context context) {
        int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }

    abstract boolean isOperatorDefinedEmergencyId();

    /**
     * Return whether the messageId is operator defined emergency ID.
     * @param emergencyIdRange contains operator defined emergency IDs.
     * @see #isOperatorDefinedEmergencyId(String, int)
     * @return true this.messageId is operator defined, false otherwise
     */
    boolean isOperatorDefinedEmergencyId(String emergencyIdRange) {
        return isOperatorDefinedEmergencyId(emergencyIdRange, getMessageIdentifier());
    }

    /**
     * Return whether the messageId is operator defined emergency ID.
     * @param emergencyIdRange contains operator defined emergency IDs.
     * @param messageId id to check against operator defined emergency IDs.
     * Sample format of emergencyIdRange:
     * "1,3,9-12,15"
     * "0x1,0x3,0x9-0xc,0xf"
     * @see java.lang.Integer.decode(String)
     * @return true this.messageId is operator defined, false otherwise
     */
    static boolean isOperatorDefinedEmergencyId(String emergencyIdRange, int messageId) {
        // Check for system property defining the emergency channel ranges to enable
        if (TextUtils.isEmpty(emergencyIdRange)) {
            return false;
        }
        try {
            for (String channelRange : emergencyIdRange.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (messageId >= startId && messageId <= endId) {
                        return true;
                    }
                } else {
                    int emergencyMessageId = Integer.decode(channelRange.trim());
                    if (emergencyMessageId == messageId) {
                        return true;
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
        return false;
    }
}
