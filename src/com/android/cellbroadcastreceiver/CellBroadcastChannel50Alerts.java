/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
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

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.android.internal.telephony.MSimConstants;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastChannel50Alerts extends PreferenceActivity {



    // Preference key for whether to enable channel 50 notifications
    // Enabled by default for phones sold in Brazil, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS_SUB1 = "enable_channel_50_alerts_sub1";
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS_SUB2 = "enable_channel_50_alerts_sub2";
    public static final String KEY_ENABLE_CHANNEL_50_ALERTS_SUB3 = "enable_channel_50_alerts_sub3";
    public static final String KEY_ENABLE_CHANNEL_50_SUB = "category_brazil_settings_title";
    public static int mSubscription;
    private static final String PREF_PARENT_KEY = "parent_preference";
    public static String channel50alerts[] = {KEY_ENABLE_CHANNEL_50_ALERTS_SUB1,
            KEY_ENABLE_CHANNEL_50_ALERTS_SUB2, KEY_ENABLE_CHANNEL_50_ALERTS_SUB3};

    private static final String TAG = "CellBroadcastChannel50Alerts";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new CellBroadcastSettingsFragment()).commit();
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        mSubscription = extras.getInt(MSimConstants.SUBSCRIPTION_KEY);
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferenceschannel50);
            PreferenceScreen prefParent = (PreferenceScreen) getPreferenceScreen().
                    findPreference(PREF_PARENT_KEY);

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            CellBroadcastReceiver.startConfigService(pref.getContext(),
                                    mSubscription);
                            return true;
                        }
                    };

            CheckBoxPreference subscriptionPref = new CheckBoxPreference(getActivity());
            subscriptionPref.setKey(channel50alerts[mSubscription]);
            subscriptionPref.setTitle(R.string.enable_channel_50_alerts_title);
            subscriptionPref.setSummary(R.string.enable_channel_50_alerts_summary);
            prefParent.addPreference(subscriptionPref);
            subscriptionPref.setOnPreferenceChangeListener(startConfigServiceListener);
       }
   }
}
