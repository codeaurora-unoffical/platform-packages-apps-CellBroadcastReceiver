/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsCbConstants;
import android.telephony.SmsCbMessage;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * This service manages the display and animation of broadcast messages.
 * Emergency messages display with a flashing animated exclamation mark icon,
 * and an alert tone is played when the alert is first shown to the user
 * (but not when the user views a previously received broadcast).
 */
public class CellBroadcastAlertService extends Service {
    private static final String TAG = "CellBroadcastAlertService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            handleBroadcastIntent(intent, false);
        } else if (Telephony.Sms.Intents.EMERGENCY_CDMA_MESSAGE_RECEIVED_ACTION.equals(action)) {
            // handle emergency CDMA broadcasts
            handleBroadcastIntent(intent, true);
        } else {
            Log.e(TAG, "Unrecognized intent action: " + action);
        }
        stopSelf(); // this service always stops after processing the intent
        return START_NOT_STICKY;
    }

    private void handleBroadcastIntent(Intent intent, boolean isCdma) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "received " + intent.getAction() + " with no extras!");
            return;
        }

        Object[] pdus = (Object[]) extras.get("pdus");

        if (pdus == null || pdus.length < 1) {
            Log.e(TAG, "received "+intent.getAction()+" with no pdus");
            return;
        }

        // create message from first PDU
        BroadcastMessage bm = null;
        if (isCdma) {
            // In Cdma emergency broadcasts are delivered as regular
            // UNSOL_CDMA_NEW_SMS and emergency
            // data is embedded in user payload.
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[0],
                    android.telephony.SmsMessage.FORMAT_3GPP2);
            if (sms == null) {
                Log.e(TAG, "failed to create SmsMessage from PDU: " + pdus[0]);
                return;
            }
            bm = CdmaBroadcastMessage.createFromSmsMessage(sms);
            for (int i = 1; i < pdus.length; i++) {
                SmsMessage nextPage = SmsMessage.createFromPdu((byte[]) pdus[i],
                        android.telephony.SmsMessage.FORMAT_3GPP2);
                if (nextPage != null) {
                    bm.appendToMessageBody(nextPage.getMessageBody());
                } else {
                    Log.w(TAG, "failed to append to CdmaBroadcastMessage from PDU: " + pdus[i]);
                    // continue so we can show the first page of the broadcast
                }
            }
        }
        else {
            SmsCbMessage message = SmsCbMessage.createFromPdu((byte[]) pdus[0]);
            if (message == null) {
                Log.e(TAG, "failed to create SmsCbMessage from PDU: " + pdus[0]);
                return;
            }

            // append message bodies from any additional PDUs (GSM only)
            for (int i = 1; i < pdus.length; i++) {
                SmsCbMessage nextPage = SmsCbMessage.createFromPdu((byte[]) pdus[i]);
                if (nextPage != null) {
                    message.appendToBody(nextPage.getMessageBody());
                } else {
                    Log.w(TAG, "failed to append to SmsCbMessage from PDU: " + pdus[i]);
                    // continue so we can show the first page of the broadcast
                }
            }
            bm = new CellBroadcastMessage(message);
        }

        if (!isMessageEnabledByUser(bm)) {
            Log.d(TAG, "ignoring alert of type " + bm.getMessageIdentifier() +
                    " by user preference");
            return;
        }

        // add notification to the bar
        addToNotificationBar(bm);
        if (bm.isEmergencyAlertMessage() || bm.isOperatorDefinedEmergencyId()) {
            // start audio/vibration/speech service for emergency alerts
            Intent audioIntent = new Intent(this, CellBroadcastAlertAudio.class);
            audioIntent.setAction(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String duration = prefs.getString(CellBroadcastSettings.KEY_ALERT_SOUND_DURATION,
                    CellBroadcastSettings.ALERT_SOUND_DEFAULT_DURATION);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_DURATION_EXTRA,
                    Integer.parseInt(duration));

            if (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH, true)) {
                audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                        bm.getMessageBody());

                String language = bm.getLanguageCode();
                if (bm.isEtwsMessage() && !"ja".equals(language)) {
                    Log.w(TAG, "bad language code for ETWS - using Japanese TTS");
                    language = "ja";
                } else if (bm.isCmasMessage() && !"en".equals(language)) {
                    Log.w(TAG, "bad language code for CMAS - using English TTS");
                    language = "en";
                }
                audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_LANGUAGE,
                        language);
            }
            startService(audioIntent);
        }
        // write to database on a separate service thread
        Intent dbWriteIntent = new Intent(this, CellBroadcastDatabaseService.class);
        dbWriteIntent.setAction(CellBroadcastDatabaseService.ACTION_INSERT_NEW_BROADCAST);
        dbWriteIntent.putExtra(bm.getIntentExtraName(), bm);
        startService(dbWriteIntent);
    }

    /**
     * Filter out broadcasts on the test channels that the user has not enabled,
     * and types of notifications that the user is not interested in receiving.
     * This allows us to enable an entire range of message identifiers in the
     * radio and not have to explicitly disable the message identifiers for
     * test broadcasts. In the unlikely event that the default shared preference
     * values were not initialized in CellBroadcastReceiverApp, the second parameter
     * to the getBoolean() calls match the default values in res/xml/preferences.xml.
     *
     * @param message the message to check
     * @return true if the user has enabled this message type; false otherwise
     */
    private boolean isMessageEnabledByUser(BroadcastMessage message) {
        if (android.telephony.TelephonyManager.PHONE_TYPE_GSM == message.getFormat()) {
            switch (message.getMessageIdentifier()) {
                case SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE:
                    return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
                    return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_IMMINENT_THREAT_ALERTS, true);

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY:
                    return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);

                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE:
                case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE:
                    return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

                default:
                    return true;
            }
        } // CellBroadcastMessage

        // must be CdmaBroadcastMessage
        switch (message.getMessageIdentifier()) {
            case CdmaBroadcastMessage.CMAS_PRESIDENTIAL:
                return true;

            case CdmaBroadcastMessage.CMAS_EXTREME:
            case CdmaBroadcastMessage.CMAS_SEVERE:
                return PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_IMMINENT_THREAT_ALERTS,
                                true);

            case CdmaBroadcastMessage.CMAS_AMBER:
                return PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);

            case CdmaBroadcastMessage.CMAS_TEST:
                return PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, false);

            default:
                return true;
        }
    }

    private void addToNotificationBar(BroadcastMessage message) {
        int channelTitleId = message.getDialogTitleResource();
        CharSequence channelName = getText(channelTitleId);
        String messageBody = message.getMessageBody();

        Notification notification = new Notification(R.drawable.stat_color_warning,
                channelName, System.currentTimeMillis());

        int notificationId = CellBroadcastReceiverApp.getCellBroadcastReceiverApp()
                .getNextNotificationId();

        PendingIntent pi = PendingIntent.getActivity(this, 0, createDisplayMessageIntent(
                this, message, notificationId), 0);

        notification.setLatestEventInfo(this, channelName, messageBody, pi);

        if (message.isEmergencyAlertMessage() ||
                message.isOperatorDefinedEmergencyId()) {
            // Emergency: open notification immediately
            notification.fullScreenIntent = pi;
            // use default notification lights (CellBroadcastAlertAudio plays sound/vibration)
            notification.defaults = Notification.DEFAULT_LIGHTS;
        } else {
            // use default sound/vibration/lights for non-emergency broadcasts
            notification.defaults = Notification.DEFAULT_ALL;
        }

        Log.i(TAG, "addToNotificationBar notificationId: " + notificationId);

        NotificationManager notificationManager =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(notificationId, notification);
    }

    static Intent createDisplayMessageIntent(Context context,
            BroadcastMessage message, int notificationId) {
        // Trigger the list activity to fire up a dialog that shows the received messages
        Intent intent = new Intent(context, CellBroadcastListActivity.class);
        intent.putExtra(message.getIntentExtraName(), message);

        // This line is needed to make this intent compare differently than the other intents
        // created here for other messages. Without this line, the PendingIntent always gets the
        // intent of a previous message and notification.
        intent.setType(Integer.toString(notificationId));

        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;    // clients can't bind to this service
    }
}
