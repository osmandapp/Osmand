package net.osmand.plus.monitoring;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.slider.Slider;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.StateChangedListener;
import net.osmand.ValueHolder;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.NavigationService;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmAndTaskManager.OsmAndTaskRunnable;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.SavingTrackHelper;
import net.osmand.plus.activities.SavingTrackHelper.SaveGpxResult;
import net.osmand.plus.dashboard.tools.DashFragmentData;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.fragments.BaseSettingsFragment.SettingsScreenType;
import net.osmand.plus.track.TrackMenuFragment;
import net.osmand.plus.views.OsmandMapLayer.DrawSettings;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.osmand.plus.UiUtilities.CompoundButtonType.PROFILE_DEPENDENT;

public class OsmandMonitoringPlugin extends OsmandPlugin {

	private static final Log LOG = PlatformUtil.getLog(OsmandMonitoringPlugin.class);
	public static final String ID = "osmand.monitoring";
	public final static String OSMAND_SAVE_SERVICE_ACTION = "OSMAND_SAVE_SERVICE_ACTION";
	public static final int REQUEST_LOCATION_PERMISSION_FOR_GPX_RECORDING = 208;

	private MapActivity mapActivity;
	private OsmandSettings settings;
	private TextInfoWidget monitoringControl;
	private LiveMonitoringHelper liveMonitoringHelper;
	private boolean isSaving;
	private boolean showDialogWhenActivityResumed;
	private BroadcastReceiver powerChangeReceiver;
	private AtomicBoolean powerChangeReceiverRegistered;
	private StateChangedListener<Boolean> powerChangeOptionListener;
	private IntentFilter powerChangeActions;

	public OsmandMonitoringPlugin(OsmandApplication app) {
		super(app);
		liveMonitoringHelper = new LiveMonitoringHelper(app);
		final List<ApplicationMode> am = ApplicationMode.allPossibleValues();
		ApplicationMode.regWidgetVisibility("monitoring", am.toArray(new ApplicationMode[0]));
		settings = app.getSettings();
		pluginPreferences.add(settings.SAVE_TRACK_TO_GPX);
		pluginPreferences.add(settings.SAVE_TRACK_INTERVAL);
		pluginPreferences.add(settings.SAVE_TRACK_MIN_DISTANCE);
		pluginPreferences.add(settings.SAVE_TRACK_PRECISION);
		pluginPreferences.add(settings.AUTO_SPLIT_RECORDING);
		pluginPreferences.add(settings.POWERCHANGE_SPLIT_RECORDING);
		pluginPreferences.add(settings.DISABLE_RECORDING_ONCE_APP_KILLED);
		pluginPreferences.add(settings.SAVE_HEADING_TO_GPX);
		pluginPreferences.add(settings.SHOW_TRIP_REC_NOTIFICATION);
		pluginPreferences.add(settings.SHOW_TRIP_REC_START_DIALOG);
		pluginPreferences.add(settings.TRACK_STORAGE_DIRECTORY);
		pluginPreferences.add(settings.LIVE_MONITORING);
		pluginPreferences.add(settings.LIVE_MONITORING_URL);
		pluginPreferences.add(settings.LIVE_MONITORING_INTERVAL);
		pluginPreferences.add(settings.LIVE_MONITORING_MAX_INTERVAL_TO_SEND);

		powerChangeReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (settings.SAVE_GLOBAL_TRACK_TO_GPX.get() && settings.POWERCHANGE_SPLIT_RECORDING.get()) {
					app.getSavingTrackHelper().startNewSegment();
				}
			}
		};
		powerChangeReceiverRegistered = new AtomicBoolean(false);

		powerChangeActions = new IntentFilter();
		powerChangeActions.addAction(Intent.ACTION_POWER_CONNECTED);
		powerChangeActions.addAction(Intent.ACTION_POWER_DISCONNECTED);

		powerChangeOptionListener = new StateChangedListener<Boolean>() {
			@Override
			public void stateChanged(Boolean change) {
				if (settings.SAVE_GLOBAL_TRACK_TO_GPX.get() && settings.POWERCHANGE_SPLIT_RECORDING.get()) {
					app.registerReceiver(powerChangeReceiver, powerChangeActions);
					powerChangeReceiverRegistered.set(true);
				} else if (powerChangeReceiverRegistered.get()) {
					app.unregisterReceiver(powerChangeReceiver);
					powerChangeReceiverRegistered.set(false);
				}
			}
		};

		settings.SAVE_GLOBAL_TRACK_TO_GPX.addListener(powerChangeOptionListener);
		settings.POWERCHANGE_SPLIT_RECORDING.addListener(powerChangeOptionListener);
		powerChangeOptionListener.stateChanged(settings.SAVE_GLOBAL_TRACK_TO_GPX.get());
	}

	@Override
	public void disable(OsmandApplication app) {
		super.disable(app);
		app.getNotificationHelper().refreshNotifications();
		settings.SAVE_GLOBAL_TRACK_TO_GPX.removeListener(powerChangeOptionListener);
		settings.POWERCHANGE_SPLIT_RECORDING.removeListener(powerChangeOptionListener);
		if (powerChangeReceiverRegistered.get()) {
			app.unregisterReceiver(powerChangeReceiver);
			powerChangeReceiverRegistered.set(false);
		}
	}

	@Override
	public void updateLocation(Location location) {
		liveMonitoringHelper.updateLocation(location);
	}

	@Override
	public int getLogoResourceId() {
		return R.drawable.ic_action_gps_info;
	}

	@Override
	public Drawable getAssetResourceImage() {
		return app.getUIUtilities().getIcon(R.drawable.trip_recording);
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public CharSequence getDescription() {
		return app.getString(R.string.record_plugin_description);
	}

	@Override
	public String getName() {
		return app.getString(R.string.record_plugin_name);
	}


	@Override
	public String getHelpFileName() {
		return "feature_articles/trip-recording-plugin.html";
	}

	@Override
	public void registerLayers(MapActivity activity) {
		registerWidget(activity);
	}

	private void registerWidget(MapActivity activity) {
		MapInfoLayer layer = activity.getMapLayers().getMapInfoLayer();
		monitoringControl = createMonitoringControl(activity);

		layer.registerSideWidget(monitoringControl,
				R.drawable.ic_action_play_dark, R.string.map_widget_monitoring, "monitoring", false, 30);
		layer.recreateControls();
	}

	@Override
	public void updateLayers(OsmandMapTileView mapView, MapActivity activity) {
		if (isActive()) {
			if (monitoringControl == null) {
				registerWidget(activity);
			}
		} else {
			if (monitoringControl != null) {
				MapInfoLayer layer = activity.getMapLayers().getMapInfoLayer();
				layer.removeSideWidget(monitoringControl);
				layer.recreateControls();
				monitoringControl = null;
			}
		}
	}

	public static final int[] SECONDS = new int[]{0, 1, 2, 3, 5, 10, 15, 20, 30, 60, 90};
	public static final int[] MINUTES = new int[]{2, 3, 5};
	public static final int[] MAX_INTERVAL_TO_SEND_MINUTES = new int[]{1, 2, 5, 10, 15, 20, 30, 60, 90, 2 * 60, 3 * 60, 4 * 60, 6 * 60, 12 * 60, 24 * 60};

	@Override
	public SettingsScreenType getSettingsScreenType() {
		return SettingsScreenType.MONITORING_SETTINGS;
	}

	@Override
	public String getPrefsDescription() {
		return app.getString(R.string.monitoring_prefs_descr);
	}

	/**
	 * creates (if it wasn't created previously) the control to be added on a MapInfoLayer that shows a monitoring state (recorded/stopped)
	 */
	private TextInfoWidget createMonitoringControl(final MapActivity map) {
		monitoringControl = new TextInfoWidget(map) {
			long lastUpdateTime;

			@Override
			public boolean updateInfo(DrawSettings drawSettings) {
				if (isSaving) {
					setText(map.getString(R.string.shared_string_save), "");
					setIcons(R.drawable.widget_monitoring_rec_big_day, R.drawable.widget_monitoring_rec_big_night);
					return true;
				}
				String txt = map.getString(R.string.monitoring_control_start);
				String subtxt = null;
				int dn;
				int d;
				long last = lastUpdateTime;
				final boolean globalRecord = settings.SAVE_GLOBAL_TRACK_TO_GPX.get();
				final boolean isRecording = app.getSavingTrackHelper().getIsRecording();
				float dist = app.getSavingTrackHelper().getDistance();

				//make sure widget always shows recorded track distance if unsaved track exists
				if (dist > 0) {
					last = app.getSavingTrackHelper().getLastTimeUpdated();
					String ds = OsmAndFormatter.getFormattedDistance(dist, map.getMyApplication());
					int ls = ds.lastIndexOf(' ');
					if (ls == -1) {
						txt = ds;
					} else {
						txt = ds.substring(0, ls);
						subtxt = ds.substring(ls + 1);
					}
				}

				final boolean liveMonitoringEnabled = liveMonitoringHelper.isLiveMonitoringEnabled();
				if (globalRecord) {
					//indicates global recording (+background recording)
					if (liveMonitoringEnabled) {
						dn = R.drawable.widget_live_monitoring_rec_big_night;
						d = R.drawable.widget_live_monitoring_rec_big_day;
					} else {
						dn = R.drawable.widget_monitoring_rec_big_night;
						d = R.drawable.widget_monitoring_rec_big_day;
					}
				} else if (isRecording) {
					//indicates (profile-based, configured in settings) recording (looks like is only active during nav in follow mode)
					if (liveMonitoringEnabled) {
						dn = R.drawable.widget_live_monitoring_rec_small_night;
						d = R.drawable.widget_live_monitoring_rec_small_day;
					} else {
						dn = R.drawable.widget_monitoring_rec_small_night;
						d = R.drawable.widget_monitoring_rec_small_day;
					}
				} else {
					dn = R.drawable.widget_monitoring_rec_inactive_night;
					d = R.drawable.widget_monitoring_rec_inactive_day;
				}

				setText(txt, subtxt);
				setIcons(d, dn);
				if ((last != lastUpdateTime) && (globalRecord || isRecording)) {
					lastUpdateTime = last;
					//blink implementation with 2 indicator states (global logging + profile/navigation logging)
					if (liveMonitoringEnabled) {
						dn = R.drawable.widget_live_monitoring_rec_small_night;
						d = R.drawable.widget_live_monitoring_rec_small_day;
					} else {
						dn = R.drawable.widget_monitoring_rec_small_night;
						d = R.drawable.widget_monitoring_rec_small_day;
					}
					setIcons(d, dn);

					map.getMyApplication().runInUIThread(new Runnable() {
						@Override
						public void run() {
							int dn;
							int d;
							if (globalRecord) {
								if (liveMonitoringEnabled) {
									dn = R.drawable.widget_live_monitoring_rec_big_night;
									d = R.drawable.widget_live_monitoring_rec_big_day;
								} else {
									dn = R.drawable.widget_monitoring_rec_big_night;
									d = R.drawable.widget_monitoring_rec_big_day;
								}
							} else {
								if (liveMonitoringEnabled) {
									dn = R.drawable.widget_live_monitoring_rec_small_night;
									d = R.drawable.widget_live_monitoring_rec_small_day;
								} else {
									dn = R.drawable.widget_monitoring_rec_small_night;
									d = R.drawable.widget_monitoring_rec_small_day;
								}
							}
							setIcons(d, dn);
						}
					}, 500);
				}
				return true;
			}
		};
		monitoringControl.updateInfo(null);

		// monitoringControl.addView(child);
		monitoringControl.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				controlDialog(map, true);
			}


		});
		return monitoringControl;
	}

	@Override
	public void mapActivityResume(MapActivity activity) {
		this.mapActivity = activity;
		if (showDialogWhenActivityResumed) {
			showDialogWhenActivityResumed = false;
			controlDialog(mapActivity, true);
		}
	}

	@Override
	public void mapActivityPause(MapActivity activity) {
		this.mapActivity = null;
	}

	@Override
	public void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_LOCATION_PERMISSION_FOR_GPX_RECORDING) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				showDialogWhenActivityResumed = true;
			} else {
				app.showToastMessage(R.string.no_location_permission);
			}
		}
	}

	public SelectedGpxFile getCurrentTrack() {
		return app.getSavingTrackHelper().getCurrentTrack();
	}

	public boolean wasTrackMonitored() {
		return settings.SAVE_GLOBAL_TRACK_TO_GPX.get();
	}

	public boolean hasDataToSave() {
		return app.getSavingTrackHelper().hasDataToSave();
	}

	public void controlDialog(final Activity activity, final boolean showTrackSelection) {
		FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
		if (hasDataToSave() || wasTrackMonitored()) {
			TripRecordingBottomSheet.showInstance(fragmentManager);
		} else {
			TripRecordingStartingBottomSheet.showTripRecordingDialog(fragmentManager, app);
		}

		/*final boolean wasTrackMonitored = settings.SAVE_GLOBAL_TRACK_TO_GPX.get();
		final boolean nightMode;
		if (activity instanceof MapActivity) {
			nightMode = app.getDaynightHelper().isNightModeForMapControls();
		} else {
			nightMode = !app.getSettings().isLightContent();
		}
		AlertDialog.Builder bld = new AlertDialog.Builder(UiUtilities.getThemedContext(activity, nightMode));
		final TIntArrayList items = new TIntArrayList();
		if (wasTrackMonitored) {
			items.add(R.string.gpx_monitoring_stop);
			items.add(R.string.gpx_start_new_segment);
			if(settings.LIVE_MONITORING.get()) {
				items.add(R.string.live_monitoring_stop);
			} else if(!settings.LIVE_MONITORING_URL.getProfileDefaultValue(settings.APPLICATION_MODE.get()).
					equals(settings.LIVE_MONITORING_URL.get())){
				items.add(R.string.live_monitoring_start);
			}
		} else {
			items.add(R.string.gpx_monitoring_start);
		}
		if (app.getSavingTrackHelper().hasDataToSave()) {
			items.add(R.string.save_current_track);
			items.add(R.string.clear_recorded_data);
		}
		String[] strings = new String[items.size()];
		for (int i = 0; i < strings.length; i++) {
			strings[i] = app.getString(items.get(i));
		}
		final int[] holder = new int[] {0};
		final Runnable run = new Runnable() {
			public void run() {
				int which = holder[0];
				int item = items.get(which);
				if(item == R.string.save_current_track){
					saveCurrentTrack(null, activity);
				} else if(item == R.string.gpx_monitoring_start) {
					if (!OsmAndLocationProvider.isLocationPermissionAvailable(activity)) {
						if (mapActivity != null) {
							ActivityCompat.requestPermissions(mapActivity,
									new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
									REQUEST_LOCATION_PERMISSION_FOR_GPX_RECORDING);
						} else {
							app.showToastMessage(R.string.no_location_permission);
						}
					} else if (app.getLocationProvider().checkGPSEnabled(activity)) {
						startGPXMonitoring(activity, showTrackSelection);
					}
				} else if (item == R.string.clear_recorded_data) {
					if (AndroidUtils.isActivityNotDestroyed(activity)) {
						AlertDialog.Builder builder = new AlertDialog.Builder(UiUtilities.getThemedContext(activity, nightMode));
						builder.setTitle(R.string.clear_recorded_data);
						builder.setMessage(R.string.are_you_sure);
						builder.setNegativeButton(R.string.shared_string_cancel, null).setPositiveButton(
								R.string.shared_string_ok, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										app.getSavingTrackHelper().clearRecordedData(true);
										app.getNotificationHelper().refreshNotifications();
									}
								});
						builder.show();
					}
				} else if(item == R.string.gpx_monitoring_stop) {
					stopRecording();
				} else if(item == R.string.gpx_start_new_segment) {
					app.getSavingTrackHelper().startNewSegment();
				} else if(item == R.string.live_monitoring_stop) {
					settings.LIVE_MONITORING.set(false);
				} else if(item == R.string.live_monitoring_start) {
					final ValueHolder<Integer> vs = new ValueHolder<Integer>();
					vs.value = settings.LIVE_MONITORING_INTERVAL.get();
					showIntervalChooseDialog(activity, app.getString(R.string.live_monitoring_interval) + " : %s",
							app.getString(R.string.save_track_to_gpx_globally), SECONDS, MINUTES,
							null, vs, showTrackSelection, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									settings.LIVE_MONITORING_INTERVAL.set(vs.value);
									settings.LIVE_MONITORING.set(true);
								}
							});
				}
				if (monitoringControl != null) {
					monitoringControl.updateInfo(null);
				}
			}
		};
		if(strings.length == 1) {
			run.run();
		} else {
			bld.setItems(strings, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					holder[0] = which;
					run.run();
				}
			});
//			bld.show();
		}*/
	}

	public void saveCurrentTrack() {
		saveCurrentTrack(null, null, true, false);
	}

	public void saveCurrentTrack(@Nullable final Runnable onComplete) {
		saveCurrentTrack(onComplete, null, true, false);
	}

	public void saveCurrentTrack(@Nullable final Runnable onComplete, @Nullable Activity activity) {
		saveCurrentTrack(onComplete, activity, true, false);
	}

	public void saveCurrentTrack(@Nullable final Runnable onComplete, @Nullable Activity activity,
								 final boolean stopRecording, final boolean openTrack) {
		if (stopRecording) {
			stopRecording();
		}
		final WeakReference<Activity> activityRef = activity != null ? new WeakReference<>(activity) : null;

		app.getTaskManager().runInBackground(new OsmAndTaskRunnable<Void, Void, SaveGpxResult>() {

			@Override
			protected void onPreExecute() {
				isSaving = true;
				updateControl();
			}

			@Override
			protected SaveGpxResult doInBackground(Void... params) {
				try {
					SavingTrackHelper helper = app.getSavingTrackHelper();
					SaveGpxResult result = helper.saveDataToGpx(app.getAppCustomization().getTracksDir());
					helper.close();
					return result;
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
				return null;
			}

			@Override
			protected void onPostExecute(SaveGpxResult result) {
				isSaving = false;
				app.getNotificationHelper().refreshNotifications();
				updateControl();

				GPXFile gpxFile = null;
				File file = null;
				File dir = app.getAppCustomization().getTracksDir();
				File[] children = dir.listFiles();
				Map<String, GPXFile> gpxFilesByName = result.getGpxFilesByName();
				if (children != null && !Algorithms.isEmpty(gpxFilesByName)) {
					String filename = gpxFilesByName.keySet().iterator().next();
					SavingTrackHelper helper = app.getSavingTrackHelper();
					for (File child : children) {
						if (child.getName().startsWith(filename)
								&& child.lastModified() == helper.getLastTimeFileSaved()) {
							file = child;
							gpxFile = gpxFilesByName.get(filename);
							break;
						}
					}
				}
				if (file != null && file.exists() && (gpxFile != null && (gpxFile.hasTrkPt() || gpxFile.hasWptPt()))) {
					if (!openTrack) {
						if (activityRef != null) {
							final Activity a = activityRef.get();
							if (a instanceof FragmentActivity && !a.isFinishing()) {
								List<String> singleName = Collections.singletonList(Algorithms.getFileNameWithoutExtension(file));
								SaveGPXBottomSheet.showInstance(((FragmentActivity) a)
										.getSupportFragmentManager(), singleName);
							}
						}
					} else {
						TrackMenuFragment.openTrack(mapActivity, file, null);
					}
				}

				if (onComplete != null) {
					onComplete.run();
				}
			}
		}, (Void) null);
	}

	public void updateControl() {
		if (monitoringControl != null) {
			monitoringControl.updateInfo(null);
		}
	}

	public void stopRecording() {
		settings.SAVE_GLOBAL_TRACK_TO_GPX.set(false);
		if (app.getNavigationService() != null) {
			app.getNavigationService().stopIfNeeded(app, NavigationService.USED_BY_GPX);
		}
	}

	public void startGPXMonitoring(final Activity map) {
		startGPXMonitoring(map, true);
	}

	public void startGPXMonitoring(final Activity map, final boolean showTrackSelection) {
		final ValueHolder<Integer> vs = new ValueHolder<Integer>();
		final ValueHolder<Boolean> choice = new ValueHolder<Boolean>();
		vs.value = settings.SAVE_GLOBAL_TRACK_INTERVAL.get();
		choice.value = settings.SAVE_GLOBAL_TRACK_REMEMBER.get();
		final Runnable runnable = new Runnable() {
			public void run() {
				app.getSavingTrackHelper().startNewSegment();
				settings.SAVE_GLOBAL_TRACK_INTERVAL.set(vs.value);
				settings.SAVE_GLOBAL_TRACK_TO_GPX.set(true);
				settings.SAVE_GLOBAL_TRACK_REMEMBER.set(choice.value);
				app.startNavigationService(NavigationService.USED_BY_GPX);
			}
		};
		if (choice.value || map == null) {
			runnable.run();
		} else if (map instanceof FragmentActivity) {
			FragmentActivity activity = (FragmentActivity) map;
			TripRecordingStartingBottomSheet.showTripRecordingDialog(activity.getSupportFragmentManager(), app);
		}
	}

	public static void showIntervalChooseDialog(final Activity activity, final String patternMsg,
												String title, final int[] seconds, final int[] minutes,
												final ValueHolder<Boolean> choice, final ValueHolder<Integer> v,
												final boolean showTrackSelection, OnClickListener onclick) {
		if (!AndroidUtils.isActivityNotDestroyed(activity)) {
			return;
		}
		final OsmandApplication app = (OsmandApplication) activity.getApplicationContext();
		boolean nightMode;
		if (activity instanceof MapActivity) {
			nightMode = app.getDaynightHelper().isNightModeForMapControls();
		} else {
			nightMode = !app.getSettings().isLightContent();
		}
		Context themedContext = UiUtilities.getThemedContext(activity, nightMode);
		AlertDialog.Builder dlg = new AlertDialog.Builder(themedContext);
		dlg.setTitle(title);
		LinearLayout ll = createIntervalChooseLayout(app, themedContext, patternMsg, seconds, minutes, choice, v, showTrackSelection, nightMode);
		dlg.setView(ll);
		dlg.setPositiveButton(R.string.shared_string_ok, onclick);
		dlg.setNegativeButton(R.string.shared_string_cancel, null);
		dlg.show();
	}

	public static LinearLayout createIntervalChooseLayout(final OsmandApplication app,
														  final Context uiCtx,
														  final String patternMsg, final int[] seconds,
														  final int[] minutes, final ValueHolder<Boolean> choice,
														  final ValueHolder<Integer> v,
														  final boolean showTrackSelection, boolean nightMode) {
		ApplicationMode appMode = app.getSettings().getApplicationMode();
		int textColorPrimary = ContextCompat.getColor(app, nightMode ? R.color.text_color_primary_dark : R.color.text_color_primary_light);
		int textColorSecondary = ContextCompat.getColor(app, nightMode ? R.color.text_color_secondary_dark : R.color.text_color_secondary_light);
		int selectedModeColor = appMode.getProfileColor(nightMode);
		LinearLayout ll = new LinearLayout(uiCtx);
		final int dp24 = AndroidUtils.dpToPx(uiCtx, 24f);
		final int dp8 = AndroidUtils.dpToPx(uiCtx, 8f);
		final TextView tv = new TextView(uiCtx);
		tv.setPadding(dp24, dp8 * 2, dp24, dp8);
		tv.setText(String.format(patternMsg, uiCtx.getString(R.string.int_continuosly)));
		tv.setTextColor(textColorSecondary);

		final int secondsLength = seconds.length;
		final int minutesLength = minutes.length;
		ViewGroup sliderContainer = UiUtilities.createSliderView(uiCtx, nightMode);
		sliderContainer.setPadding(dp24, dp8, dp24, dp8);
		Slider sp = sliderContainer.findViewById(R.id.slider);
		UiUtilities.setupSlider(sp, nightMode, selectedModeColor, true);
		sp.setValueTo(secondsLength + minutesLength - 1);
		sp.setStepSize(1);
		sp.addOnChangeListener(new Slider.OnChangeListener() {

			@Override
			public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
				String s;
				int progress = (int) value;
				if (progress == 0) {
					s = uiCtx.getString(R.string.int_continuosly);
					v.value = 0;
				} else {
					if (progress < secondsLength) {
						s = seconds[progress] + " " + uiCtx.getString(R.string.int_seconds);
						v.value = seconds[progress] * 1000;
					} else {
						s = minutes[progress - secondsLength] + " " + uiCtx.getString(R.string.int_min);
						v.value = minutes[progress - secondsLength] * 60 * 1000;
					}
				}
				tv.setText(String.format(patternMsg, s));
			}
		});

		for (int i = 0; i < secondsLength + minutesLength - 1; i++) {
			if (i < secondsLength) {
				if (v.value <= seconds[i] * 1000) {
					sp.setValue(i);
					break;
				}
			} else {
				if (v.value <= minutes[i - secondsLength] * 1000 * 60) {
					sp.setValue(i);
					break;
				}
			}
		}

		ll.setOrientation(LinearLayout.VERTICAL);
		ll.addView(tv);
		ll.addView(sliderContainer);
		if (choice != null) {
			final AppCompatCheckBox cb = new AppCompatCheckBox(uiCtx);
			cb.setText(R.string.confirm_every_run);
			cb.setTextColor(textColorPrimary);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT);
			AndroidUtils.setMargins(lp, dp24, dp8, dp24, 0);
			cb.setLayoutParams(lp);
			AndroidUtils.setPadding(cb, dp8, 0, 0, 0);
			cb.setChecked(!choice.value);
			cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					choice.value = !isChecked;
				}
			});
			UiUtilities.setupCompoundButton(cb, nightMode, PROFILE_DEPENDENT);
			ll.addView(cb);
		}

		if (showTrackSelection) {
			View divider = new View(uiCtx);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtils.dpToPx(uiCtx, 1f));
			AndroidUtils.setMargins(lp, 0, dp8 * 2, 0, 0);
			divider.setLayoutParams(lp);
			divider.setBackgroundColor(uiCtx.getResources().getColor(nightMode ? R.color.divider_color_dark : R.color.divider_color_light));
			ll.addView(divider);

			final AppCompatCheckBox cb = new AppCompatCheckBox(uiCtx);
			cb.setText(R.string.shared_string_show_on_map);
			cb.setTextColor(textColorPrimary);
			lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT);
			AndroidUtils.setMargins(lp, dp24, dp8 * 2, dp24, 0);
			cb.setLayoutParams(lp);
			AndroidUtils.setPadding(cb, dp8, 0, 0, 0);
			cb.setChecked(app.getSelectedGpxHelper().getSelectedCurrentRecordingTrack() != null);
			cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					app.getSelectedGpxHelper().selectGpxFile(app.getSavingTrackHelper().getCurrentGpx(), isChecked, false);
				}
			});
			UiUtilities.setupCompoundButton(cb, nightMode, PROFILE_DEPENDENT);
			ll.addView(cb);
		}

		return ll;
	}

	@Override
	public DashFragmentData getCardFragment() {
		return DashTrackFragment.FRAGMENT_DATA;
	}
}
