package net.osmand.plus.monitoring;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.myplaces.FavoritesActivity;
import net.osmand.plus.profiles.SelectCopyAppModeBottomSheet;
import net.osmand.plus.profiles.SelectCopyAppModeBottomSheet.CopyAppModePrefsListener;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.settings.backend.OsmAndAppCustomization;
import net.osmand.plus.settings.backend.OsmandPreference;
import net.osmand.plus.settings.bottomsheets.ResetProfilePrefsBottomSheet;
import net.osmand.plus.settings.bottomsheets.ResetProfilePrefsBottomSheet.ResetAppModePrefsListener;
import net.osmand.plus.settings.bottomsheets.SingleSelectPreferenceBottomSheet;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.preferences.ListPreferenceEx;
import net.osmand.plus.settings.preferences.SwitchPreferenceEx;
import net.osmand.plus.widgets.style.CustomTypefaceSpan;

import java.util.HashMap;
import java.util.LinkedHashMap;

import static net.osmand.plus.activities.PluginInfoFragment.PLUGIN_INFO;
import static net.osmand.plus.monitoring.OsmandMonitoringPlugin.MINUTES;
import static net.osmand.plus.monitoring.OsmandMonitoringPlugin.SECONDS;
import static net.osmand.plus.myplaces.FavoritesActivity.TAB_ID;
import static net.osmand.plus.settings.backend.OsmandSettings.MONTHLY_DIRECTORY;
import static net.osmand.plus.settings.backend.OsmandSettings.REC_DIRECTORY;

public class MonitoringSettingsFragment extends BaseSettingsFragment
		implements CopyAppModePrefsListener, ResetAppModePrefsListener {

	private static final String COPY_PLUGIN_SETTINGS = "copy_plugin_settings";
	private static final String RESET_TO_DEFAULT = "reset_to_default";
	private static final String OPEN_TRACKS = "open_tracks";
	private static final String SAVE_GLOBAL_TRACK_INTERVAL = "save_global_track_interval";

	boolean showSwitchProfile = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args != null) {
			showSwitchProfile = args.getBoolean(PLUGIN_INFO, false);
		}
	}

	@Override
	protected void createToolbar(LayoutInflater inflater, View view) {
		super.createToolbar(inflater, view);

		View switchProfile = view.findViewById(R.id.profile_button);
		if (switchProfile != null) {
			AndroidUiHelper.updateVisibility(switchProfile, showSwitchProfile);
		}
	}

	@Override
	public Bundle buildArguments() {
		Bundle args = super.buildArguments();
		args.putBoolean(PLUGIN_INFO, showSwitchProfile);
		return args;
	}

	@Override
	protected void setupPreferences() {
		setupShowStartDialog();

		setupSaveTrackToGpxPref();
		setupSaveTrackIntervalPref();

		setupSaveGlobalTrackIntervalPref();
		setupSaveTrackMinDistancePref();
		setupSaveTrackPrecisionPref();
		setupSaveTrackMinSpeedPref();
		setupAutoSplitRecordingPref();
		setupPowerchangeSplitRecordingPref();
		setupDisableRecordingOnceAppKilledPref();
		setupSaveHeadingToGpxPref();

		setupTrackStorageDirectoryPref();
		setupShowTripRecNotificationPref();
		setupLiveMonitoringPref();

		setupOpenNotesDescrPref();
		setupOpenNotesPref();

		setupCopyProfileSettingsPref();
		setupResetToDefaultPref();
	}

	private void setupShowStartDialog() {
		SwitchPreferenceEx showStartDialog = (SwitchPreferenceEx) findPreference(settings.SHOW_TRIP_REC_START_DIALOG.getId());
		showStartDialog.setDescription(getString(R.string.trip_recording_show_start_dialog_setting));
		showStartDialog.setIcon(getPersistentPrefIcon(R.drawable.ic_action_dialog));
	}

	private void setupSaveTrackToGpxPref() {
		SwitchPreferenceEx saveTrackToGpx = (SwitchPreferenceEx) findPreference(settings.SAVE_TRACK_TO_GPX.getId());
		saveTrackToGpx.setDescription(getString(R.string.save_track_to_gpx_descrp));
		saveTrackToGpx.setIcon(getPersistentPrefIcon(R.drawable.ic_action_gdirections_dark));
	}

	private void setupSaveTrackIntervalPref() {
		HashMap<Object, String> entry = getTimeValues();
		ListPreferenceEx saveTrackInterval = (ListPreferenceEx) findPreference(settings.SAVE_TRACK_INTERVAL.getId());
		saveTrackInterval.setEntries(entry.values().toArray(new String[0]));
		saveTrackInterval.setEntryValues(entry.keySet().toArray());
		saveTrackInterval.setIcon(getActiveIcon(R.drawable.ic_action_time_span));
		saveTrackInterval.setDescription(R.string.save_track_interval_descr);
	}

	private void setupSaveGlobalTrackIntervalPref() {
		HashMap<Object, String> entry = getTimeValues();
		ListPreferenceEx saveTrackInterval = (ListPreferenceEx) findPreference(settings.SAVE_GLOBAL_TRACK_INTERVAL.getId());
		saveTrackInterval.setEntries(entry.values().toArray(new String[0]));
		saveTrackInterval.setEntryValues(entry.keySet().toArray());
		ApplicationMode selectedAppMode = getSelectedAppMode();
		saveTrackInterval.setValue(settings.SAVE_GLOBAL_TRACK_INTERVAL.getModeValue(selectedAppMode));
		saveTrackInterval.setIcon(getActiveIcon(R.drawable.ic_action_time_span));
		saveTrackInterval.setDescription(R.string.save_global_track_interval_descr);
	}

	private HashMap<Object, String> getTimeValues() {
		HashMap<Object, String> entry = new LinkedHashMap<>();
		for (int second : SECONDS) {
			entry.put(second * 1000, second + " " + getString(R.string.int_seconds));
		}
		for (int minute : MINUTES) {
			entry.put((minute * 60) * 1000, minute + " " + getString(R.string.int_min));
		}
		return entry;
	}

	private void setupSaveTrackMinDistancePref() {
		Float[] entryValues = new Float[] {0.f, 2.0f, 5.0f, 10.0f, 20.0f, 30.0f, 50.0f};
		String[] entries = new String[entryValues.length];
		entries[0] = getString(R.string.shared_string_not_selected);
		for (int i = 1; i < entryValues.length; i++) {
			entries[i] = entryValues[i].intValue() + " " + getString(R.string.m);
		}

		ListPreferenceEx saveTrackMinDistance = (ListPreferenceEx) findPreference(settings.SAVE_TRACK_MIN_DISTANCE.getId());
		saveTrackMinDistance.setEntries(entries);
		saveTrackMinDistance.setEntryValues(entryValues);

		SpannableStringBuilder stringBuilder = new SpannableStringBuilder(getString(R.string.monitoring_min_distance_descr));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_distance_descr_side_effect));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_distance_descr_recommendation));

		saveTrackMinDistance.setDescription(stringBuilder.toString());
	}

	private void setupSaveTrackPrecisionPref() {
		Float[] entryValues = new Float[] {0.f, 1.0f, 2.0f, 5.0f, 10.0f, 15.0f, 20.0f, 50.0f, 100.0f};
		String[] entries = new String[entryValues.length];
		entries[0] = getString(R.string.shared_string_not_selected);
		for (int i = 1; i < entryValues.length; i++) {
			entries[i] = entryValues[i].intValue() + " " + getString(R.string.m) + "  (" + Math.round(entryValues[i] / 0.3048f) + " " + getString(R.string.foot) + ")";
		}

		ListPreferenceEx saveTrackPrecision = (ListPreferenceEx) findPreference(settings.SAVE_TRACK_PRECISION.getId());
		saveTrackPrecision.setEntries(entries);
		saveTrackPrecision.setEntryValues(entryValues);

		SpannableStringBuilder stringBuilder = new SpannableStringBuilder(getString(R.string.monitoring_min_accuracy_descr));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_accuracy_descr_side_effect));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_accuracy_descr_recommendation));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_accuracy_descr_remark));

		saveTrackPrecision.setDescription(stringBuilder.toString());
	}

	private void setupSaveTrackMinSpeedPref() {
		Float[] entryValues = new Float[] {0.f, 0.000001f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f};
		String[] entries = new String[entryValues.length];
		entries[0] = getString(R.string.shared_string_not_selected);
		entries[1] = "> 0"; // This option for the GPS chipset motion detection
		for (int i = 2; i < entryValues.length; i++) {
			entries[i] = entryValues[i].intValue() + " " + getString(R.string.km_h);
			entryValues[i] = entryValues[i] / 3.6f;
		}

		ListPreferenceEx saveTrackMinSpeed = (ListPreferenceEx) findPreference(settings.SAVE_TRACK_MIN_SPEED.getId());
		saveTrackMinSpeed.setEntries(entries);
		saveTrackMinSpeed.setEntryValues(entryValues);

		SpannableStringBuilder stringBuilder = new SpannableStringBuilder(getString(R.string.monitoring_min_speed_descr));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_speed_descr_side_effect));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_speed_descr_recommendation));
		stringBuilder.append("\n");
		stringBuilder.append(getString(R.string.monitoring_min_speed_descr_remark));

		saveTrackMinSpeed.setDescription(stringBuilder.toString());
	}

	private void setupAutoSplitRecordingPref() {
		SwitchPreferenceEx autoSplitRecording = (SwitchPreferenceEx) findPreference(settings.AUTO_SPLIT_RECORDING.getId());
		autoSplitRecording.setDescription(getString(R.string.auto_split_recording_descr));
	}

	private void setupPowerchangeSplitRecordingPref() {
		SwitchPreferenceEx powerchangeSplitRecording = (SwitchPreferenceEx) findPreference(settings.POWERCHANGE_SPLIT_RECORDING.getId());
		powerchangeSplitRecording.setDescription(getString(R.string.powerchange_split_recording_descr));
	}

	private void setupDisableRecordingOnceAppKilledPref() {
		SwitchPreferenceEx disableRecordingOnceAppKilled = (SwitchPreferenceEx) findPreference(settings.DISABLE_RECORDING_ONCE_APP_KILLED.getId());
		disableRecordingOnceAppKilled.setDescription(getString(R.string.disable_recording_once_app_killed_descrp));
	}

	private void setupSaveHeadingToGpxPref() {
		SwitchPreferenceEx saveHeadingToGpx = (SwitchPreferenceEx) findPreference(settings.SAVE_HEADING_TO_GPX.getId());
		saveHeadingToGpx.setDescription(getString(R.string.save_heading_descr));
	}

	private void setupShowTripRecNotificationPref() {
		SwitchPreferenceEx showTripRecNotification = (SwitchPreferenceEx) findPreference(settings.SHOW_TRIP_REC_NOTIFICATION.getId());
		showTripRecNotification.setDescription(getString(R.string.trip_rec_notification_settings_desc));
		showTripRecNotification.setIcon(getPersistentPrefIcon(R.drawable.ic_action_notification));
	}

	private void setupTrackStorageDirectoryPref() {
		Integer[] entryValues = new Integer[] {REC_DIRECTORY, MONTHLY_DIRECTORY};
		String[] entries = new String[entryValues.length];
		entries[0] = getString(R.string.store_tracks_in_rec_directory);
		entries[1] = getString(R.string.store_tracks_in_monthly_directories);
//		entries[2] = getString(R.string.store_tracks_in_daily_directories);

		ListPreferenceEx trackStorageDirectory = (ListPreferenceEx) findPreference(settings.TRACK_STORAGE_DIRECTORY.getId());
		trackStorageDirectory.setEntries(entries);
		trackStorageDirectory.setEntryValues(entryValues);
		trackStorageDirectory.setDescription(R.string.track_storage_directory_descrp);
		trackStorageDirectory.setIcon(getActiveIcon(R.drawable.ic_action_folder));
	}

	private void setupLiveMonitoringPref() {
		Drawable disabled = getContentIcon(R.drawable.ic_action_offline);
		Drawable enabled = getActiveIcon(R.drawable.ic_world_globe_dark);
		Drawable icon = getPersistentPrefIcon(enabled, disabled);

		SwitchPreferenceEx liveMonitoring = (SwitchPreferenceEx) findPreference(settings.LIVE_MONITORING.getId());
		liveMonitoring.setDescription(getString(R.string.live_monitoring_m_descr));
		liveMonitoring.setIcon(icon);
	}

	private void setupOpenNotesDescrPref() {
		String menu = getString(R.string.shared_string_menu);
		String myPlaces = getString(R.string.shared_string_my_places);
		String tracks = getString(R.string.shared_string_tracks);
		String tracksPath = getString(R.string.ltr_or_rtl_triple_combine_via_dash, menu, myPlaces, tracks);
		String tracksPathDescr = getString(R.string.tracks_view_descr, tracksPath);

		int startIndex = tracksPathDescr.indexOf(tracksPath);
		SpannableString titleSpan = new SpannableString(tracksPathDescr);
		Typeface typeface = FontCache.getRobotoMedium(getContext());
		titleSpan.setSpan(new CustomTypefaceSpan(typeface), startIndex, startIndex + tracksPath.length(), 0);

		Preference openTracksDescription = findPreference("open_tracks_description");
		openTracksDescription.setTitle(titleSpan);
	}

	private void setupOpenNotesPref() {
		Preference openNotes = findPreference(OPEN_TRACKS);
		openNotes.setIcon(getActiveIcon(R.drawable.ic_action_folder));
	}

	private void setupCopyProfileSettingsPref() {
		Preference copyProfilePrefs = findPreference(COPY_PLUGIN_SETTINGS);
		copyProfilePrefs.setIcon(getActiveIcon(R.drawable.ic_action_copy));
	}

	private void setupResetToDefaultPref() {
		Preference resetToDefault = findPreference(RESET_TO_DEFAULT);
		resetToDefault.setIcon(getActiveIcon(R.drawable.ic_action_reset_to_default_dark));
	}

	@Override
	public void onDestroy() {
		FragmentActivity activity = getActivity();
		if (activity != null && !activity.isChangingConfigurations()) {
			Fragment target = getTargetFragment();
			if (target instanceof TripRecordingStartingBottomSheet) {
				((TripRecordingStartingBottomSheet) target).show();
			}
		}
		super.onDestroy();
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String prefId = preference.getKey();
		if (OPEN_TRACKS.equals(prefId)) {
			Bundle bundle = new Bundle();
			bundle.putInt(TAB_ID, FavoritesActivity.GPX_TAB);

			OsmAndAppCustomization appCustomization = app.getAppCustomization();
			Intent favorites = new Intent(preference.getContext(), appCustomization.getFavoritesActivity());
			favorites.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			favorites.putExtra(MapActivity.INTENT_PARAMS, bundle);
			startActivity(favorites);
			return true;
		} else if (COPY_PLUGIN_SETTINGS.equals(prefId)) {
			FragmentManager fragmentManager = getFragmentManager();
			if (fragmentManager != null) {
				SelectCopyAppModeBottomSheet.showInstance(fragmentManager, this, false, getSelectedAppMode());
			}
		} else if (RESET_TO_DEFAULT.equals(prefId)) {
			FragmentManager fragmentManager = getFragmentManager();
			if (fragmentManager != null) {
				ResetProfilePrefsBottomSheet.showInstance(fragmentManager, prefId, this, false, getSelectedAppMode());
			}
		}
		return super.onPreferenceClick(preference);
	}

	@Override
	public void onApplyPreferenceChange(String prefId, boolean applyToAllProfiles, Object newValue) {
		if (SAVE_GLOBAL_TRACK_INTERVAL.equals(prefId)) {
			OsmandPreference pref = settings.getPreference(prefId);
			if (newValue instanceof Boolean) {
				applyPreference(settings.SAVE_GLOBAL_TRACK_REMEMBER.getId(), applyToAllProfiles, false);
			} else if (pref instanceof CommonPreference
					&& !((CommonPreference) pref).hasDefaultValueForMode(getSelectedAppMode())) {
				applyPreference(SAVE_GLOBAL_TRACK_INTERVAL, applyToAllProfiles, newValue);
				applyPreference(settings.SAVE_GLOBAL_TRACK_REMEMBER.getId(), applyToAllProfiles, true);
			}
		} else {
			super.onApplyPreferenceChange(prefId, applyToAllProfiles, newValue);
		}
	}

	@Override
	public void onDisplayPreferenceDialog(Preference preference) {
		String prefId = preference.getKey();
		if (settings.SAVE_TRACK_MIN_DISTANCE.getId().equals(prefId)
				|| settings.SAVE_TRACK_PRECISION.getId().equals(prefId)
				|| settings.SAVE_TRACK_MIN_SPEED.getId().equals(prefId)) {
			FragmentManager fm = getFragmentManager();
			if (fm != null) {
				ApplicationMode appMode = getSelectedAppMode();
				SingleSelectPreferenceBottomSheet.showInstance(fm, prefId, this, false, appMode, true, true);
			}
		} else {
			super.onDisplayPreferenceDialog(preference);
		}
	}

	@Override
	public void copyAppModePrefs(ApplicationMode appMode) {
		OsmandMonitoringPlugin plugin = OsmandPlugin.getPlugin(OsmandMonitoringPlugin.class);
		if (plugin != null) {
			app.getSettings().copyProfilePreferences(appMode, getSelectedAppMode(), plugin.getPreferences());
			updateAllSettings();
		}
	}

	@Override
	public void resetAppModePrefs(ApplicationMode appMode) {
		OsmandMonitoringPlugin plugin = OsmandPlugin.getPlugin(OsmandMonitoringPlugin.class);
		if (plugin != null) {
			app.getSettings().resetProfilePreferences(appMode, plugin.getPreferences());
			app.showToastMessage(R.string.plugin_prefs_reset_successful);
			updateAllSettings();
		}
	}
}