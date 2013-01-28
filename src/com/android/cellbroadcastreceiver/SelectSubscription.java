/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
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

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;
import android.telephony.MSimTelephonyManager;

import android.util.Log;

import com.android.internal.telephony.MSimConstants;

public class SelectSubscription extends PreferenceActivity {
    private static final String LOG_TAG = "SelectSubscription";

    // String keys for preference lookup
    private static final String PREF_PARENT_KEY = "parent_pref";
    private static final String SUBSCRIPTION_KEY = "subscription";

    private int[] resourceIndex = {R.string.sub1, R.string.sub2, R.string.sub3};

    // Preference instance variables.
    private Preference mSubscriptionPref;

    Preference.OnPreferenceClickListener mPreferenceClickListener =
            new Preference.OnPreferenceClickListener() {
       public boolean onPreferenceClick(Preference preference) {
           startActivity(preference.getIntent());
           return true;
       }
    };

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.d (LOG_TAG, "On Create()");
        addPreferencesFromResource(R.xml.select_subscription);
        PreferenceScreen prefParent = (PreferenceScreen) getPreferenceScreen().
                findPreference(PREF_PARENT_KEY);

        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        Intent selectIntent;

        for (int i = 0; i < numPhones; i++) {
            selectIntent = new Intent();
            mSubscriptionPref = new Preference(getApplicationContext());
            // Set the package and target class.
            selectIntent.setClassName("com.android.cellbroadcastreceiver",
                    "com.android.cellbroadcastreceiver.CellBroadcastChannel50Alerts");
            selectIntent.putExtra(SUBSCRIPTION_KEY, i);
            mSubscriptionPref.setIntent(selectIntent);
            mSubscriptionPref.setTitle(resourceIndex[i]);
            mSubscriptionPref.setOnPreferenceClickListener(mPreferenceClickListener);
            prefParent.addPreference(mSubscriptionPref);
       }

    }
}
