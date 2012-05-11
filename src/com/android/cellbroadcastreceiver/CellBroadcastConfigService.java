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

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.SmsCbConstants;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.gsm.SmsCbHeader;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.DBG;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    static final String ACTION_ENABLE_CHANNELS_GSM = "ACTION_ENABLE_CHANNELS_GSM";
    static final String ACTION_ENABLE_CHANNELS_CDMA = "ACTION_ENABLE_CHANNELS_CDMA";

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    private void setChannelRange(SmsManager manager, String ranges, boolean enable, boolean isCdma) {
        try {
            for (String channelRange : ranges.split(",")) {
                int dashIndex = channelRange.indexOf('-');
                if (dashIndex != -1) {
                    int startId = Integer.decode(channelRange.substring(0, dashIndex).trim());
                    int endId = Integer.decode(channelRange.substring(dashIndex + 1).trim());
                    if (enable) {
                        if (DBG) Log.d(TAG, "enabling emergency IDs " + startId + '-' + endId);
                        if (isCdma) {
                            manager.enableCdmaBroadcastRange(startId, endId);
                        } else {
                            manager.enableCellBroadcastRange(startId, endId);
                        }
                    } else {
                        if (DBG) Log.d(TAG, "disabling emergency IDs " + startId + '-' + endId);
                        if (isCdma) {
                            manager.disableCdmaBroadcastRange(startId, endId);
                        } else {
                            manager.disableCellBroadcastRange(startId, endId);
                        }
                    }
                } else {
                    int messageId = Integer.decode(channelRange.trim());
                    if (enable) {
                        if (DBG) Log.d(TAG, "enabling emergency message ID " + messageId);
                        if (isCdma) {
                            manager.enableCdmaBroadcast(messageId);
                        } else {
                            manager.enableCellBroadcast(messageId);
                        }
                    } else {
                        if (DBG) Log.d(TAG, "disabling emergency message ID " + messageId);
                        if (isCdma) {
                            manager.disableCdmaBroadcast(messageId);
                        } else {
                            manager.disableCellBroadcast(messageId);
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Number Format Exception parsing emergency channel range", e);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_ENABLE_CHANNELS_GSM.equals(intent.getAction())) {
            configGsmChannels();
        } else if (ACTION_ENABLE_CHANNELS_CDMA.equals(intent.getAction())) {
            configCdmaChannels();
        }
    }

    private void configGsmChannels() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources res = getResources();

            // Check for system property defining the emergency channel ranges to enable
            String emergencyIdRange = SystemProperties.get(
                    CellBroadcastMessage.EMERGENCY_BROADCAST_RANGE);

            boolean enableEmergencyAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);

            boolean enableChannel50Alerts = res.getBoolean(R.bool.show_brazil_settings) &&
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_CHANNEL_50_ALERTS, true);

            SmsManager manager = SmsManager.getDefault();
            if (enableEmergencyAlerts) {
                if (DBG) Log.d(TAG, "enabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, true, false);
                } else {
                    // No emergency channel system property, enable all emergency channels
                    manager.enableCellBroadcastRange(
                            SmsCbConstants.MESSAGE_ID_PWS_FIRST_IDENTIFIER,
                            SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER);
                }
                if (DBG) Log.d(TAG, "enabled emergency cell broadcast channels");
            } else {
                // we may have enabled these channels previously, so try to disable them
                if (DBG) Log.d(TAG, "disabling emergency cell broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, false, false);
                } else {
                    // No emergency channel system property, disable all emergency channels
                    manager.disableCellBroadcastRange(
                            SmsCbConstants.MESSAGE_ID_PWS_FIRST_IDENTIFIER,
                            SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER);
                }
                if (DBG) Log.d(TAG, "disabled emergency cell broadcast channels");
            }

            if (enableChannel50Alerts) {
                if (DBG) Log.d(TAG, "enabling cell broadcast channel 50");
                manager.enableCellBroadcast(50);
                if (DBG) Log.d(TAG, "enabled cell broadcast channel 50");
            } else {
                if (DBG) Log.d(TAG, "disabling cell broadcast channel 50");
                manager.disableCellBroadcast(50);
                if (DBG) Log.d(TAG, "disabled cell broadcast channel 50");
            }
        } catch (Exception ex) {
            Log.e(TAG, "exception enabling cell broadcast channels", ex);
        }
    }

    private void configCdmaChannels() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources res = getResources();

            // Check for system property defining the emergency channel ranges to enable
            String emergencyIdRange = SystemProperties.get(
                    CdmaBroadcastMessage.EMERGENCY_BROADCAST_RANGE);

            boolean enableEmergencyAlerts = prefs.getBoolean(
                    CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);

            SmsManager manager = SmsManager.getDefault();
            if (enableEmergencyAlerts) {
                if (DBG) Log.d(TAG, "enabling emergency cdma broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, true, true);
                } else {
                    // No emergency channel system property, enable all emergency channels
                    manager.enableCdmaBroadcastRange(
                            CdmaBroadcastMessage.MESSAGE_ID_PWS_FIRST_IDENTIFIER,
                            CdmaBroadcastMessage.MESSAGE_ID_PWS_LAST_IDENTIFIER);
                }
                if (DBG) Log.d(TAG, "enabled emergency cdma broadcast channels");
            } else {
                // we may have enabled these channels previously, so try to
                // disable them
                if (DBG) Log.d(TAG, "disabling emergency cdma broadcast channels");
                if (!TextUtils.isEmpty(emergencyIdRange)) {
                    setChannelRange(manager, emergencyIdRange, false, true);
                } else {
                    // No emergency channel system property, disable all emergency channels
                    manager.disableCdmaBroadcastRange(
                            CdmaBroadcastMessage.MESSAGE_ID_PWS_FIRST_IDENTIFIER,
                            CdmaBroadcastMessage.MESSAGE_ID_PWS_LAST_IDENTIFIER);
                }
                if (DBG) Log.d(TAG, "disabled emergency cdma broadcast channels");
            }
        } catch (Exception ex) {
            Log.e(TAG, "exception enabling cdma broadcast channels", ex);
        }
    }
}
