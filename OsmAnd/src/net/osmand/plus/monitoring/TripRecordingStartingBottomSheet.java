package net.osmand.plus.monitoring;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.slider.RangeSlider;

import net.osmand.plus.NavigationService;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.SideMenuBottomSheetDialogFragment;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.monitoring.TripRecordingBottomSheet.ItemType;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.fragments.BaseSettingsFragment.SettingsScreenType;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentManager;

import static net.osmand.plus.monitoring.OsmandMonitoringPlugin.MINUTES;
import static net.osmand.plus.monitoring.OsmandMonitoringPlugin.SECONDS;
import static net.osmand.plus.monitoring.TripRecordingBottomSheet.createItem;
import static net.osmand.plus.monitoring.TripRecordingBottomSheet.createItemActive;
import static net.osmand.plus.monitoring.TripRecordingBottomSheet.createShowTrackItem;
import static net.osmand.plus.monitoring.TripRecordingBottomSheet.updateTrackIcon;

public class TripRecordingStartingBottomSheet extends SideMenuBottomSheetDialogFragment {

	public static final String TAG = TripRecordingStartingBottomSheet.class.getSimpleName();
	public static final String UPDATE_LOGGING_INTERVAL = "update_logging_interval";

	private OsmandApplication app;
	private OsmandSettings settings;

	private AppCompatImageView upDownBtn;
	private AppCompatImageView trackAppearanceIcon;
	private TextView intervalValueView;
	private LinearLayout intervalContainer;
	private RangeSlider intervalSlider;

	private boolean infoExpanded;

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (!fragmentManager.isStateSaved()) {
			TripRecordingStartingBottomSheet fragment = new TripRecordingStartingBottomSheet();
			fragment.show(fragmentManager, TAG);
		}
	}

	public static void showTripRecordingDialog(@NonNull FragmentManager fragmentManager, OsmandApplication app) {
		if (!fragmentManager.isStateSaved()) {
			OsmandSettings settings = app.getSettings();
			boolean showStartDialog = settings.SHOW_TRIP_REC_START_DIALOG.get();
			if (showStartDialog) {
				showInstance(fragmentManager);
			} else {
				startRecording(app);
			}
		}
	}

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		app = requiredMyApplication();
		settings = app.getSettings();
		Context context = requireContext();

		LayoutInflater inflater = UiUtilities.getInflater(context, nightMode);
		View itemView = inflater.inflate(R.layout.trip_recording_starting_fragment, null, false);
		items.add(new BaseBottomSheetItem.Builder()
				.setCustomView(itemView)
				.create());

		LinearLayout expandHideIntervalContainer = itemView.findViewById(R.id.interval_view_container);
		upDownBtn = itemView.findViewById(R.id.up_down_button);
		expandHideIntervalContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				updateIntervalContainer();
			}
		});

		intervalValueView = itemView.findViewById(R.id.interval_value);
		intervalContainer = itemView.findViewById(R.id.always_ask_and_range_slider_container);
		intervalSlider = itemView.findViewById(R.id.interval_slider);
		updateIntervalValue();

		LinearLayout showTrackContainer = itemView.findViewById(R.id.show_track_on_map);
		trackAppearanceIcon = showTrackContainer.findViewById(R.id.additional_button_icon);
		createShowTrackItem(showTrackContainer, trackAppearanceIcon, R.string.shared_string_show_on_map,
				TripRecordingStartingBottomSheet.this, nightMode, new Runnable() {
					@Override
					public void run() {
						hide();
					}
				});

		updateUpDownBtn();
	}

	@Override
	protected void setupBottomButtons(ViewGroup view) {
		LayoutInflater themedInflater = UiUtilities.getInflater(view.getContext(), nightMode);
		int contentPadding = getDimen(R.dimen.content_padding);
		int topPadding = getDimen(R.dimen.context_menu_first_line_top_margin);
		View buttonsContainer = themedInflater.inflate(R.layout.preference_button_with_icon_triple, null);
		buttonsContainer.setPadding(contentPadding, topPadding, contentPadding, contentPadding);
		view.addView(buttonsContainer);

		setupCancelButton(buttonsContainer);
		setupStartButton(buttonsContainer);
		setupSettingsButton(buttonsContainer);
	}

	private void setupCancelButton(View buttonsContainer) {
		CardView cancelButton = buttonsContainer.findViewById(R.id.button_left);
		createItem(app, nightMode, cancelButton, ItemType.CANCEL, true, null);
		cancelButton.setOnClickListener(v -> dismiss());
	}

	private void setupStartButton(View buttonsContainer) {
		CardView startButton = buttonsContainer.findViewById(R.id.button_center);
		createItemActive(app, nightMode, startButton, ItemType.START_RECORDING);
		startButton.setOnClickListener(v -> startRecording());
	}

	private void setupSettingsButton(View buttonsContainer) {
		CardView settingsButton = buttonsContainer.findViewById(R.id.button_right);
		createItem(app, nightMode, settingsButton, ItemType.SETTINGS, true, null);
		settingsButton.setOnClickListener(v -> {
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				hide();
				BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.MONITORING_SETTINGS,
						null, new Bundle(), TripRecordingStartingBottomSheet.this);
			}
		});
	}

	private void updateIntervalLegend() {
		String text = getString(R.string.save_track_interval_globally);
		String textValue;
		int interval = settings.SAVE_GLOBAL_TRACK_INTERVAL.get();
		if (interval == 0) {
			textValue = getString(R.string.int_continuosly);
		} else {
			int seconds = interval / 1000;
			if (seconds <= SECONDS[SECONDS.length - 1]) {
				textValue = seconds + " " + getString(R.string.int_seconds);
			} else {
				textValue = (seconds / 60) + " " + getString(R.string.int_min);
			}
		}
		String textAll = getString(R.string.ltr_or_rtl_combine_via_colon, text, textValue);
		Typeface typeface = FontCache.getRobotoMedium(app);
		SpannableString spannableString = UiUtilities.createCustomFontSpannable(typeface, textAll, textValue);
		intervalValueView.setText(spannableString);
	}

	private void updateIntervalValue() {
		if (intervalSlider != null && intervalContainer != null) {
			updateIntervalLegend();
			final int secondsLength = SECONDS.length;
			final int minutesLength = MINUTES.length;
			intervalSlider.setValueTo(secondsLength + minutesLength - 1);
			int currentModeColor = app.getSettings().getApplicationMode().getProfileColor(nightMode);
			UiUtilities.setupSlider(intervalSlider, nightMode, currentModeColor, true);
			intervalContainer.setVisibility(View.GONE);
			intervalSlider.addOnChangeListener(new RangeSlider.OnChangeListener() {

				@Override
				public void onValueChange(@NonNull RangeSlider slider, float value, boolean fromUser) {
					int progress = (int) value;
					if (progress == 0) {
						settings.SAVE_GLOBAL_TRACK_INTERVAL.set(0);
					} else if (progress < secondsLength) {
						settings.SAVE_GLOBAL_TRACK_INTERVAL.set(SECONDS[progress] * 1000);
					} else {
						settings.SAVE_GLOBAL_TRACK_INTERVAL.set(MINUTES[progress - secondsLength] * 60 * 1000);
					}
					updateIntervalLegend();
				}
			});

			for (int i = 0; i < secondsLength + minutesLength; i++) {
				if (i < secondsLength) {
					if (settings.SAVE_GLOBAL_TRACK_INTERVAL.get() <= SECONDS[i] * 1000) {
						intervalSlider.setValues((float) i);
						break;
					}
				} else {
					if (settings.SAVE_GLOBAL_TRACK_INTERVAL.get() <= MINUTES[i - secondsLength] * 1000 * 60) {
						intervalSlider.setValues((float) i);
						break;
					}
				}
			}
		}
	}

	private void updateIntervalContainer() {
		infoExpanded = !infoExpanded;
		AndroidUiHelper.updateVisibility(intervalContainer, infoExpanded);
		updateUpDownBtn();
	}

	private void updateUpDownBtn() {
		int iconId = infoExpanded ? R.drawable.ic_action_arrow_down : R.drawable.ic_action_arrow_up;
		upDownBtn.setImageDrawable(getContentIcon(iconId));
	}

	private static void startRecording(OsmandApplication app) {
		app.getSavingTrackHelper().startNewSegment();
		app.getSettings().SAVE_GLOBAL_TRACK_TO_GPX.set(true);
		app.startNavigationService(NavigationService.USED_BY_GPX);
	}

	private void startRecording() {
		startRecording(app);
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			TripRecordingBottomSheet.showInstance(mapActivity.getSupportFragmentManager());
		}
		dismiss();
	}

	public void show() {
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.show();
			updateTrackIcon(app, trackAppearanceIcon);
			updateIntervalValue();
			AndroidUiHelper.updateVisibility(intervalContainer, infoExpanded);
		}
	}

	public void hide() {
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.hide();
		}
	}

	@Override
	protected boolean hideButtonsContainer() {
		return true;
	}
}