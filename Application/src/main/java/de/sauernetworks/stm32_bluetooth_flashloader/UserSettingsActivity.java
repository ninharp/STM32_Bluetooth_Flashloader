package de.sauernetworks.stm32_bluetooth_flashloader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class UserSettingsActivity extends Activity {
    static Activity main;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        main = getParent();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();

    }

    public static class PrefsFragment extends PreferenceFragment {
        Preference matchPref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            SharedPreferences.OnSharedPreferenceChangeListener listener;
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings);

            // TODO number range checks of delay ...

            matchPref = findPreference("prefShowDeviceMatch");
            if (matchPref != null)
                matchPref.setSummary(getString(R.string.pref_showdevicematch_summary, BluetoothUpdaterFragment.getPrefShowDeviceMatch()));
            matchPref = findPreference("prefBootloaderCommand");
            if (matchPref != null)
                matchPref.setSummary(getString(R.string.pref_bootloadercommand_summary, BluetoothUpdaterFragment.getPrefBootloaderCommand()));

            listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals("prefShowDeviceMatch")) {
                        matchPref = findPreference("prefShowDeviceMatch");
                        if (matchPref != null)
                            matchPref.setSummary(getString(R.string.pref_showdevicematch_summary, prefs.getString("prefShowDeviceMatch", "device_name_here")));
                    } else if (key.equals("prefBootloaderCommand")) {
                        matchPref = findPreference("prefBootloaderCommand");
                        if (matchPref != null)
                            matchPref.setSummary(getString(R.string.pref_bootloadercommand_summary, prefs.getString("prefBootloaderCommand", "magic string")));
                    }
                }
            };

            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);

        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            main.setResult(Constants.RESULT_SETTINGS);
        }
    }

}  