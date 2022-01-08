/*
 * Copyright (C) 2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.realmeparts;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;

public class DeviceSettings extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String KEY_OTG_SWITCH = "otg";
    public static final String KEY_GAME_SWITCH = "game";
    public static final String KEY_CHARGING_SWITCH = "smart_charging";
    public static final String KEY_CHARGING_SPEED = "charging_speed";
    public static final String KEY_RESET_STATS = "reset_stats";
    public static final String KEY_DND_SWITCH = "dnd";
    public static final String KEY_CABC = "cabc";
    public static final String CABC_SYSTEM_PROPERTY = "persist.cabc_profile";
    public static final String KEY_FPS_INFO = "fps_info";
    public static final String KEY_SETTINGS_PREFIX = "device_setting_";
    public static final String TP_LIMIT_ENABLE = "/proc/touchpanel/oppo_tp_limit_enable";
    public static final String TP_DIRECTION = "/proc/touchpanel/oppo_tp_direction";
    private static final String ProductName = Utils.ProductName();
    private static final String KEY_CATEGORY_CHARGING = "charging";
    private static final String KEY_CATEGORY_GRAPHICS = "graphics";
    public static SecureSettingListPreference mChargingSpeed;
    public static TwoStatePreference mResetStats;
    public static SeekBarPreference mSeekBarPreference;
    public static DisplayManager mDisplayManager;
    private static NotificationManager mNotificationManager;
    public TwoStatePreference mDNDSwitch;
    public PreferenceCategory mPreferenceCategory;
    private TwoStatePreference mOTGModeSwitch;
    private TwoStatePreference mGameModeSwitch;
    private TwoStatePreference mSmartChargingSwitch;
    private SwitchPreference mFpsInfo;
    private boolean CABC_DeviceMatched;
    private SecureSettingListPreference mCABC;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
        prefs.edit().putString("ProductName", ProductName).apply();

        addPreferencesFromResource(R.xml.main);

        mOTGModeSwitch = (TwoStatePreference) findPreference(KEY_OTG_SWITCH);
        mOTGModeSwitch.setEnabled(OTGModeSwitch.isSupported());
        mOTGModeSwitch.setChecked(OTGModeSwitch.isCurrentlyEnabled(this.getContext()));
        mOTGModeSwitch.setOnPreferenceChangeListener(new OTGModeSwitch());

        mGameModeSwitch = findPreference(KEY_GAME_SWITCH);
        mGameModeSwitch.setEnabled(GameModeSwitch.isSupported());
        mGameModeSwitch.setChecked(GameModeSwitch.isCurrentlyEnabled(this.getContext()));
        mGameModeSwitch.setOnPreferenceChangeListener(new GameModeSwitch(getContext()));

        mDNDSwitch = findPreference(KEY_DND_SWITCH);
        mDNDSwitch.setChecked(prefs.getBoolean(KEY_DND_SWITCH, false));
        mDNDSwitch.setOnPreferenceChangeListener(this);

        mSmartChargingSwitch = findPreference(KEY_CHARGING_SWITCH);
        mSmartChargingSwitch.setChecked(prefs.getBoolean(KEY_CHARGING_SWITCH, false));
        mSmartChargingSwitch.setOnPreferenceChangeListener(new SmartChargingSwitch(getContext()));

        mChargingSpeed = findPreference(KEY_CHARGING_SPEED);
        mChargingSpeed.setEnabled(mSmartChargingSwitch.isChecked());
        mChargingSpeed.setOnPreferenceChangeListener(this);

        mResetStats = findPreference(KEY_RESET_STATS);
        mResetStats.setChecked(prefs.getBoolean(KEY_RESET_STATS, false));
        mResetStats.setEnabled(mSmartChargingSwitch.isChecked());
        mResetStats.setOnPreferenceChangeListener(this);

        mSeekBarPreference = findPreference("seek_bar");
        mSeekBarPreference.setEnabled(mSmartChargingSwitch.isChecked());
        SeekBarPreference.mProgress = prefs.getInt("seek_bar", 95);

        mFpsInfo = findPreference(KEY_FPS_INFO);
        mFpsInfo.setChecked(prefs.getBoolean(KEY_FPS_INFO, false));
        mFpsInfo.setOnPreferenceChangeListener(this);

        mCABC = (SecureSettingListPreference) findPreference(KEY_CABC);
        mCABC.setValue(Utils.getStringProp(CABC_SYSTEM_PROPERTY, "0"));
        mCABC.setSummary(mCABC.getEntry());
        mCABC.setOnPreferenceChangeListener(this);

        isCoolDownAvailable();
        try {
            ParseJson();
        } catch (Exception e) {
            Log.d("DeviceSettings", e.toString());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mFpsInfo) {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
                Utils.startService(this.getContext(), com.realmeparts.FPSInfoService.class);
            } else {
                Utils.stopService(this.getContext(), com.realmeparts.FPSInfoService.class);
            }
        }

        if (preference == mChargingSpeed) {
            mChargingSpeed.setValue((String) newValue);
            mChargingSpeed.setSummary(mChargingSpeed.getEntry());
        }

        if (preference == mCABC) {
            mCABC.setValue((String) newValue);
            mCABC.setSummary(mCABC.getEntry());
            Utils.setStringProp(CABC_SYSTEM_PROPERTY, (String) newValue);
        }
        return true;
    }

    // Remove Charging Speed preference if cool_down node is unavailable
    private void isCoolDownAvailable() {
        mPreferenceCategory = (PreferenceCategory) findPreference(KEY_CATEGORY_CHARGING);

        if (Utils.fileWritable(SmartChargingService.mmi_charging_enable)) {
            if (!Utils.fileWritable(SmartChargingService.cool_down)) {
                mPreferenceCategory.removePreference(findPreference(KEY_CHARGING_SPEED));
            }
        } else {
            getPreferenceScreen().removePreference(mPreferenceCategory);
        }
    }

    private void ParseJson() throws JSONException {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
        mPreferenceCategory = (PreferenceCategory) findPreference(KEY_CATEGORY_GRAPHICS);
        String features_json = Utils.InputStreamToString(getResources().openRawResource(R.raw.realmeparts_features));
        JSONObject jsonOB = new JSONObject(features_json);

        JSONArray CABC = jsonOB.getJSONArray(KEY_CABC);
        for (int i = 0; i < CABC.length(); i++) {
            if (ProductName.toUpperCase().contains(CABC.getString(i))) {
                {
                    CABC_DeviceMatched = true;
                }
            }
        }

        // Remove CABC preference if device is unsupported
        if (!CABC_DeviceMatched) {
            mPreferenceCategory.removePreference(findPreference(KEY_CABC));
            prefs.edit().putBoolean("CABC_DeviceMatched", false).apply();
        } else prefs.edit().putBoolean("CABC_DeviceMatched", true).apply();
    }
}
