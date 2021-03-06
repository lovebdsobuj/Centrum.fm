package org.indywidualni.centrumfm.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.indywidualni.centrumfm.MyApplication;
import org.indywidualni.centrumfm.R;
import org.indywidualni.centrumfm.rest.RestClient;
import org.indywidualni.centrumfm.rest.model.Server;
import org.indywidualni.centrumfm.util.AlarmHelper;
import org.indywidualni.centrumfm.util.Miscellany;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();
    public static final String DEFAULT_REMINDER_OFFSET = "-300000";
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    private SharedPreferences preferences;
    private Tracker tracker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Google Analytics tracker
        tracker = ((MyApplication) getActivity().getApplication()).getDefaultTracker();

        // get Shared Preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // update server status
        Preference status = findPreference("indywidualni_server_status");
        getServerStatus(status);

        // shared preference changed
        prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                switch (key) {
                    case "reminders_mode":

                        // reset reminders
                        if (prefs.getString("reminders_mode", DEFAULT_REMINDER_OFFSET).equals("0"))
                            AlarmHelper.cancelAllAlarms();
                        else
                            AlarmHelper.setAllAlarms();

                        break;
                }

                Log.v("SharedPreferenceChange", key + " changed in SettingsFragment");
            }
        };

        // show open source libraries dialog
        final Preference libraries = findPreference("open_source_libraries");
        libraries.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                int padding = Miscellany.dpToPx(17);

                ScrollView scroller = new ScrollView(getActivity());
                scroller.setPadding(padding, padding, padding, padding);
                scroller.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
                scroller.setClipToPadding(false);

                TextView msg = new TextView(getActivity());
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(Html.fromHtml(Miscellany.readFromAssets("libraries.html")));

                scroller.addView(msg);

                AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
                ab.setTitle(getString(R.string.open_source_libraries_pref)).setView(scroller).setCancelable(true).create()
                        .show();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // register the listener
        preferences.registerOnSharedPreferenceChangeListener(prefChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        // unregister the listener
        preferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
    }

    private void getServerStatus(final Preference status) {
        Call<Server> call = RestClient.getClientJSON().getServerStatus();
        call.enqueue(new Callback<Server>() {
            @Override
            public void onResponse(Call<Server> call, Response<Server> response) {
                Log.v(TAG, "getServerStatus: response " + response.code());

                if (response.isSuccess()) {
                    if (status != null)
                        status.setSummary(String.format(getString(R.string.indywidualni_server_ok),
                                response.body().getVersion()));
                } else {
                    // error response, no access to resource?
                    if (status != null)
                        status.setSummary(String.format(getString(R.string.indywidualni_server_not_ok),
                               response.code()));
                            
                    tracker.send(new HitBuilders.EventBuilder()
                            .setCategory("Error response")
                            .setAction("Get Server Status")
                            .setLabel("error " + response.code())
                            .build());
                }
            }

            @Override
            public void onFailure(Call<Server> call, Throwable t) {
                Log.e(TAG, "getServerStatus: " + t.getLocalizedMessage());
                if (status != null)
                    status.setSummary(getString(R.string.indywidualni_server_failure));
            }
        });
    }

}