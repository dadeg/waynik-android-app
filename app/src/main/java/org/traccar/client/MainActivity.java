/*
 * Copyright 2012 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import android.net.Uri;

import com.google.firebase.iid.FirebaseInstanceId;

@SuppressWarnings("deprecation")
public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static MainActivity instance = null;

    public static final String KEY_EMAIL = "email";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_STATUS = "status";
    public static final String KEY_EMERGENCY = "emergency";

    private static final int PERMISSIONS_REQUEST_LOCATION = 2;

    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.HIDDEN_APP) {
            removeLauncherIcon();
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        addPreferencesFromResource(R.xml.preferences);
        initPreferences();

        findPreference(KEY_EMAIL).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // email_address matcher doesn't seem to work every time.
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                    //return newValue != null && Patterns.EMAIL_ADDRESS.matcher((String) newValue).matches();
                //} else {
                    return newValue != null && !((String) newValue).isEmpty();
                //}
            }
        });
        findPreference(KEY_TOKEN).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return newValue != null && !((String) newValue).isEmpty();
            }
        });

        findPreference(KEY_INTERVAL).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue != null) {
                    try {
                        Integer.parseInt((String) newValue);
                        return true;
                    } catch (NumberFormatException e) {
                    }
                }
                return false;
            }
        });

        if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
            startTrackingService(true, false);
        }
    }

    private void removeLauncherIcon() {
        String className = MainActivity.class.getCanonicalName().replace(".MainActivity", ".Launcher");
        ComponentName componentName = new ComponentName(getPackageName(), className);
        PackageManager packageManager = getPackageManager();
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                    componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.hidden_alert));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    private void addShortcuts(boolean start, int name) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setComponent(new ComponentName(getPackageName(), ".ShortcutActivity"));
        shortcutIntent.putExtra(ShortcutActivity.EXTRA_ACTION, start);

        Intent installShortCutIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        installShortCutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        installShortCutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(name));
        installShortCutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher));

        sendBroadcast(installShortCutIntent);
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true;
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        instance = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setPreferencesEnabled(boolean enabled) {
        findPreference(KEY_EMAIL).setEnabled(enabled);
        findPreference(KEY_TOKEN).setEnabled(enabled);
        findPreference(KEY_INTERVAL).setEnabled(enabled);
        findPreference(KEY_PROVIDER).setEnabled(enabled);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_STATUS)) {
            if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                startTrackingService(true, false);
            } else {
                stopTrackingService();
                if (sharedPreferences.getBoolean(KEY_EMERGENCY, false)) {
                    //sharedPreferences.edit().putBoolean(KEY_EMERGENCY, false).commit();
                    SwitchPreference statusPreference = (SwitchPreference) findPreference(KEY_EMERGENCY);
                    statusPreference.setChecked(false);
                }
            }
        }

        if (key.equals(KEY_EMERGENCY)) {
            if (sharedPreferences.getBoolean(KEY_EMERGENCY, false)) {
                if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                    // stop and then start again in emergency mode.
                    stopTrackingService();
                    startTrackingService(true, false);
                } else {
                    //sharedPreferences.edit().putBoolean(KEY_STATUS, true).commit();
                    SwitchPreference statusPreference = (SwitchPreference) findPreference(KEY_STATUS);
                    statusPreference.setChecked(true);
                }

                // call emergency number automatically
//                Intent intent = new Intent(Intent.ACTION_CALL);
//                intent.setData(Uri.parse("tel:12026431047"));
//                startActivity(intent);
            } else {
                if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                    // stop and then start again in non-emergency mode.
                    stopTrackingService();
                    startTrackingService(true, false);
                }
            }
        }

        WaynikFirebaseTokenRegistration requestProvider = new WaynikFirebaseTokenRegistration();
        requestProvider.sendRegistrationToServer(this, FirebaseInstanceId.getInstance().getToken());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.status) {
            startActivity(new Intent(this, StatusActivity.class));
            return true;
        } else if (item.getItemId() == R.id.shortcuts) {
            addShortcuts(true, R.string.shortcut_start);
            addShortcuts(false, R.string.shortcut_stop);
            return true;
        } else if (item.getItemId() == R.id.about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initPreferences() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }

    public void startTrackingService(boolean checkPermission, boolean permission) {
        if (checkPermission) {
            Set<String> missingPermissions = new HashSet<>();
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (missingPermissions.isEmpty()) {
                permission = true;
            } else {
                requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), PERMISSIONS_REQUEST_LOCATION);
                return;
            }
        }

        if (permission) {
            setPreferencesEnabled(false);
            startService(new Intent(this, TrackingService.class));
        } else {
            sharedPreferences.edit().putBoolean(KEY_STATUS, false).commit();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                TwoStatePreference preference = (TwoStatePreference) findPreference(KEY_STATUS);
                preference.setChecked(false);
            } else {
                CheckBoxPreference preference = (CheckBoxPreference) findPreference(KEY_STATUS);
                preference.setChecked(false);
            }
        }
    }

    public void stopTrackingService() {
        stopService(new Intent(this, TrackingService.class));
        setPreferencesEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            startTrackingService(false, grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    (permissions.length < 2 || grantResults[1] == PackageManager.PERMISSION_GRANTED));
        }
    }

}
