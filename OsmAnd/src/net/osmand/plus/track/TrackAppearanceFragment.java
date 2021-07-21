package net.osmand.plus.track;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.PlatformUtil;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.GpxDbHelper;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.UiUtilities.DialogButtonType;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.ContextMenuFragment;
import net.osmand.plus.base.ContextMenuScrollFragment;
import net.osmand.plus.dialogs.GpxAppearanceAdapter;
import net.osmand.plus.dialogs.GpxAppearanceAdapter.AppearanceListItem;
import net.osmand.plus.dialogs.GpxAppearanceAdapter.GpxAppearanceAdapterType;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.monitoring.TripRecordingBottomSheet;
import net.osmand.plus.monitoring.TripRecordingStartingBottomSheet;
import net.osmand.plus.routepreparationmenu.cards.BaseCard;
import net.osmand.plus.routepreparationmenu.cards.BaseCard.CardListener;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.track.CustomColorBottomSheet.ColorPickerListener;
import net.osmand.plus.track.SplitTrackAsyncTask.SplitTrackListener;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import static net.osmand.plus.dialogs.ConfigureMapMenu.CURRENT_TRACK_COLOR_ATTR;
import static net.osmand.plus.dialogs.GpxAppearanceAdapter.TRACK_WIDTH_BOLD;
import static net.osmand.plus.dialogs.GpxAppearanceAdapter.TRACK_WIDTH_MEDIUM;
import static net.osmand.plus.dialogs.GpxAppearanceAdapter.getAppearanceItems;
import static net.osmand.plus.monitoring.TripRecordingBottomSheet.UPDATE_TRACK_ICON;

public class TrackAppearanceFragment extends ContextMenuScrollFragment implements CardListener, ColorPickerListener {

	public static final String TAG = TrackAppearanceFragment.class.getName();
	private static final Log log = PlatformUtil.getLog(TrackAppearanceFragment.class);

	private static final String SHOW_START_FINISH_ICONS_INITIAL_VALUE_KEY = "showStartFinishIconsInitialValueKey";

	private OsmandApplication app;
	private GpxDbHelper gpxDbHelper;

	@Nullable
	private GpxDataItem gpxDataItem;
	private TrackDrawInfo trackDrawInfo;
	private SelectedGpxFile selectedGpxFile;
	private List<GpxDisplayGroup> displayGroups;

	private int menuTitleHeight;
	private long modifiedTime = -1;

	private TrackWidthCard trackWidthCard;
	private SplitIntervalCard splitIntervalCard;
	private TrackColoringCard trackColoringCard;
	private ColorsCard colorsCard;
	private GradientCard gradientCard;
	private boolean showStartFinishIconsInitialValue;

	private ImageView trackIcon;
	private View buttonsShadow;
	private View routeMenuTopShadowAll;
	private View controlButtons;

	@Override
	public int getMainLayoutId() {
		return R.layout.track_appearance;
	}

	@Override
	public int getHeaderViewHeight() {
		return menuTitleHeight;
	}

	@Override
	public boolean isHeaderViewDetached() {
		return false;
	}

	@Override
	public int getToolbarHeight() {
		return 0;
	}

	@Override
	public float getMiddleStateKoef() {
		return 0.5f;
	}

	@Override
	public int getInitialMenuState() {
		return MenuState.HALF_SCREEN;
	}

	public TrackDrawInfo getTrackDrawInfo() {
		return trackDrawInfo;
	}

	public void setSelectedGpxFile(SelectedGpxFile selectedGpxFile) {
		this.selectedGpxFile = selectedGpxFile;
	}

	@Override
	public int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = requireMyApplication();
		gpxDbHelper = app.getGpxDbHelper();

		if (savedInstanceState != null) {
			trackDrawInfo = new TrackDrawInfo(savedInstanceState);
			if (selectedGpxFile == null) {
				restoreSelectedGpxFile(trackDrawInfo.getFilePath(), trackDrawInfo.isCurrentRecording());
			}
			if (!trackDrawInfo.isCurrentRecording()) {
				gpxDataItem = gpxDbHelper.getItem(new File(trackDrawInfo.getFilePath()));
			}
			showStartFinishIconsInitialValue = savedInstanceState.getBoolean(SHOW_START_FINISH_ICONS_INITIAL_VALUE_KEY,
					app.getSettings().SHOW_START_FINISH_ICONS.get());
		} else {
			showStartFinishIconsInitialValue = app.getSettings().SHOW_START_FINISH_ICONS.get();

			if (selectedGpxFile.isShowCurrentTrack()) {
				trackDrawInfo = new TrackDrawInfo(true);
				trackDrawInfo.setColor(app.getSettings().CURRENT_TRACK_COLOR.get());
				trackDrawInfo.setGradientScaleType(app.getSettings().CURRENT_TRACK_COLORIZATION.get());
				trackDrawInfo.setSpeedGradientPalette(Algorithms.stringToArray(app.getSettings().CURRENT_TRACK_SPEED_GRADIENT_PALETTE.get()));
				trackDrawInfo.setAltitudeGradientPalette(Algorithms.stringToArray(app.getSettings().CURRENT_TRACK_ALTITUDE_GRADIENT_PALETTE.get()));
				trackDrawInfo.setSlopeGradientPalette(Algorithms.stringToArray(app.getSettings().CURRENT_TRACK_SLOPE_GRADIENT_PALETTE.get()));
				trackDrawInfo.setWidth(app.getSettings().CURRENT_TRACK_WIDTH.get());
				trackDrawInfo.setShowArrows(app.getSettings().CURRENT_TRACK_SHOW_ARROWS.get());
				trackDrawInfo.setShowStartFinish(app.getSettings().CURRENT_TRACK_SHOW_START_FINISH.get());
			} else {
				gpxDataItem = gpxDbHelper.getItem(new File(selectedGpxFile.getGpxFile().path));
				trackDrawInfo = new TrackDrawInfo(app, gpxDataItem, false);
			}
			updateTrackColor();
		}
		requireMyActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			public void handleOnBackPressed() {
				dismiss();
			}
		});
	}

	private void restoreSelectedGpxFile(String gpxFilePath, boolean isCurrentRecording) {
		TrackMenuFragment.loadSelectedGpxFile(requireMapActivity(), gpxFilePath, isCurrentRecording, (gpxFile) -> {
			setSelectedGpxFile(gpxFile);
			if (getView() != null) {
				initContent(getView());
			}
			return true;
		});
	}

	private void updateTrackColor() {
		int color = gpxDataItem != null ? gpxDataItem.getColor() : 0;
		GPXFile gpxFile = selectedGpxFile.getGpxFile();
		if (color == 0 && gpxFile != null) {
			if (gpxFile.showCurrentTrack) {
				color = app.getSettings().CURRENT_TRACK_COLOR.get();
			} else {
				color = gpxFile.getColor(0);
			}
		}
		if (color == 0) {
			RenderingRulesStorage renderer = app.getRendererRegistry().getCurrentSelectedRenderer();
			CommonPreference<String> prefColor = app.getSettings().getCustomRenderProperty(CURRENT_TRACK_COLOR_ATTR);
			color = GpxAppearanceAdapter.parseTrackColor(renderer, prefColor.get());
		}
		trackDrawInfo.setColor(color);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		if (view != null) {
			trackIcon = view.findViewById(R.id.track_icon);
			buttonsShadow = view.findViewById(R.id.buttons_shadow);
			controlButtons = view.findViewById(R.id.control_buttons);
			routeMenuTopShadowAll = view.findViewById(R.id.route_menu_top_shadow_all);

			if (isPortrait()) {
				updateCardsLayout();
			} else {
				int widthNoShadow = getLandscapeNoShadowWidth();
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthNoShadow, ViewGroup.LayoutParams.WRAP_CONTENT);
				params.gravity = Gravity.BOTTOM | Gravity.START;
				controlButtons.setLayoutParams(params);
			}

			if (selectedGpxFile != null) {
				initContent(view);
			}
		}
		return view;
	}

	private void initContent(@NonNull View view) {
		setupCards();
		setupButtons(view);
		setupScrollShadow();
		updateAppearanceIcon();
		enterTrackAppearanceMode();
		runLayoutListener();
	}

	@Override
	protected void calculateLayout(View view, boolean initLayout) {
		menuTitleHeight = routeMenuTopShadowAll.getHeight()
				+ controlButtons.getHeight() - buttonsShadow.getHeight();
		super.calculateLayout(view, initLayout);
	}

	@Override
	protected void setViewY(int y, boolean animated, boolean adjustMapPos) {
		super.setViewY(y, animated, adjustMapPos);
		updateStatusBarColor();
	}

	@Override
	protected void updateMainViewLayout(int posY) {
		super.updateMainViewLayout(posY);
		updateStatusBarColor();
	}

	@Override
	public boolean shouldShowMapControls(int menuState) {
		return menuState == MenuState.HEADER_ONLY || menuState == MenuState.HALF_SCREEN;
	}

	@Override
	public void onResume() {
		super.onResume();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapLayers().getGpxLayer().setTrackDrawInfo(trackDrawInfo);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapLayers().getGpxLayer().setTrackDrawInfo(null);
		}
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		adjustMapPosition(getHeight());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		exitTrackAppearanceMode();
	}

	private void enterTrackAppearanceMode() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			boolean portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);
			AndroidUiHelper.setVisibility(mapActivity, portrait ? View.INVISIBLE : View.GONE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info);
		}
	}

	private void exitTrackAppearanceMode() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			AndroidUiHelper.setVisibility(mapActivity, View.VISIBLE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info,
					R.id.map_search_button);
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		trackDrawInfo.saveToBundle(outState);
		outState.putBoolean(SHOW_START_FINISH_ICONS_INITIAL_VALUE_KEY, showStartFinishIconsInitialValue);
	}

	@Override
	public int getStatusBarColorId() {
		View view = getView();
		if (view != null) {
			boolean nightMode = isNightMode();
			if (getViewY() <= getFullScreenTopPosY() || !isPortrait()) {
				if (Build.VERSION.SDK_INT >= 23 && !nightMode) {
					view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
				}
				return nightMode ? R.color.divider_color_dark : R.color.divider_color_light;
			} else {
				if (Build.VERSION.SDK_INT >= 23 && !nightMode) {
					view.setSystemUiVisibility(view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
				}
			}
		}
		return -1;
	}

	private void updateStatusBarColor() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.updateStatusBarColor();
		}
	}

	@Override
	public void onCardLayoutNeeded(@NonNull BaseCard card) {

	}

	@Override
	public void onCardPressed(@NonNull BaseCard card) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (card instanceof SplitIntervalCard) {
				SplitIntervalBottomSheet.showInstance(mapActivity.getSupportFragmentManager(), trackDrawInfo, this);
			} else if (card instanceof TrackColoringCard) {
				GradientScaleType currentScaleType = ((TrackColoringCard) card).getSelectedScaleType();
				trackDrawInfo.setGradientScaleType(currentScaleType);
				mapActivity.refreshMap();
				if (gradientCard != null) {
					gradientCard.setSelectedScaleType(currentScaleType);
				}
				if (colorsCard != null) {
					AndroidUiHelper.updateVisibility(colorsCard.getView(), currentScaleType == null);
				}
			} else if (card instanceof ColorsCard) {
				int color = ((ColorsCard) card).getSelectedColor();
				trackDrawInfo.setColor(color);
				updateColorItems();
			} else if (card instanceof TrackWidthCard) {
				updateAppearanceIcon();
			} else if (card instanceof DirectionArrowsCard) {
				updateAppearanceIcon();
			}
		}
	}

	@Override
	public void onCardButtonPressed(@NonNull BaseCard card, int buttonIndex) {

	}

	@Override
	public void onColorSelected(Integer prevColor, int newColor) {
		if (prevColor != null) {
			List<Integer> customColors = ColorsCard.getCustomColors(app.getSettings().CUSTOM_TRACK_COLORS);
			int index = customColors.indexOf(prevColor);
			if (index != ColorsCard.INVALID_VALUE) {
				saveCustomColorsToTracks(prevColor, newColor);
			}
		}
		trackDrawInfo.setColor(newColor);
		colorsCard.onColorSelected(prevColor, newColor);
		updateColorItems();
	}

	@Override
	protected int applyPosY(int currentY, boolean needCloseMenu, boolean needMapAdjust, int previousMenuState, int newMenuState, int dZoom, boolean animated) {
		int y = super.applyPosY(currentY, needCloseMenu, needMapAdjust, previousMenuState, newMenuState, dZoom, animated);
		if (needMapAdjust) {
			adjustMapPosition(y);
		}
		return y;
	}

	@Override
	public void onContextMenuDismiss(@NonNull ContextMenuFragment fragment) {
		Fragment target = getTargetFragment();
		if (target instanceof TripRecordingStartingBottomSheet) {
			((TripRecordingStartingBottomSheet) target).show();
		} else if (target instanceof TripRecordingBottomSheet) {
			((TripRecordingBottomSheet) target).show(UPDATE_TRACK_ICON);
		}
	}

	private void updateAppearanceIcon() {
		Drawable icon = getTrackIcon(app, trackDrawInfo.getWidth(), trackDrawInfo.isShowArrows(), trackDrawInfo.getColor());
		trackIcon.setImageDrawable(icon);
	}

	@Override
	protected void onHeaderClick() {
		adjustMapPosition(getViewY());
	}

	private void adjustMapPosition(int y) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && mapActivity.getMapView() != null) {
			GPXFile gpxFile = selectedGpxFile.getGpxFile();
			QuadRect r = gpxFile.getRect();

			RotatedTileBox tb = mapActivity.getMapView().getCurrentRotatedTileBox().copy();
			int tileBoxWidthPx = 0;
			int tileBoxHeightPx = 0;
			int marginLeftPx = 0;

			if (!isPortrait()) {
				tileBoxWidthPx = tb.getPixWidth() - getWidth();
				marginLeftPx = getWidth();
			} else {
				int fHeight = getViewHeight() - y - AndroidUtils.getStatusBarHeight(mapActivity);
				tileBoxHeightPx = tb.getPixHeight() - fHeight;
			}
			if (r.left != 0 && r.right != 0) {
				mapActivity.getMapView().fitRectToMap(r.left, r.right, r.top, r.bottom, tileBoxWidthPx, tileBoxHeightPx, 0, marginLeftPx);
			}
		}
	}

	public static Drawable getTrackIcon(OsmandApplication app, String widthAttr, boolean showArrows, @ColorInt int color) {
		int widthIconId = getWidthIconId(widthAttr);
		Drawable widthIcon = app.getUIUtilities().getPaintedIcon(widthIconId, color);

		int strokeIconId = getStrokeIconId(widthAttr);
		int strokeColor = UiUtilities.getColorWithAlpha(Color.BLACK, 0.7f);
		Drawable strokeIcon = app.getUIUtilities().getPaintedIcon(strokeIconId, strokeColor);

		Drawable transparencyIcon = getTransparencyIcon(app, widthAttr, color);
		if (showArrows) {
			int arrowsIconId = getArrowsIconId(widthAttr);
			int contrastColor = UiUtilities.getContrastColor(app, color, false);
			Drawable arrows = app.getUIUtilities().getPaintedIcon(arrowsIconId, contrastColor);
			return UiUtilities.getLayeredIcon(transparencyIcon, widthIcon, strokeIcon, arrows);
		}
		return UiUtilities.getLayeredIcon(transparencyIcon, widthIcon, strokeIcon);
	}

	private static Drawable getTransparencyIcon(OsmandApplication app, String widthAttr, @ColorInt int color) {
		int transparencyIconId = getTransparencyIconId(widthAttr);
		int colorWithoutAlpha = UiUtilities.removeAlpha(color);
		int transparencyColor = UiUtilities.getColorWithAlpha(colorWithoutAlpha, 0.8f);
		return app.getUIUtilities().getPaintedIcon(transparencyIconId, transparencyColor);
	}

	private void updateCardsLayout() {
		View mainView = getMainView();
		if (mainView != null) {
			LinearLayout cardsContainer = getCardsContainer();
			View topShadow = getTopShadow();
			FrameLayout bottomContainer = getBottomContainer();
			if (bottomContainer == null) {
				return;
			}
			if (getCurrentMenuState() == MenuState.HEADER_ONLY) {
				topShadow.setVisibility(View.INVISIBLE);
				bottomContainer.setBackgroundDrawable(null);
				AndroidUtils.setBackground(mainView.getContext(), cardsContainer, isNightMode(), R.drawable.travel_card_bg_light, R.drawable.travel_card_bg_dark);
			} else {
				topShadow.setVisibility(View.VISIBLE);
				AndroidUtils.setBackground(mainView.getContext(), bottomContainer, isNightMode(), R.color.list_background_color_light, R.color.list_background_color_dark);
				AndroidUtils.setBackground(mainView.getContext(), cardsContainer, isNightMode(), R.color.list_background_color_light, R.color.list_background_color_dark);
			}
		}
	}

	private void setupButtons(View view) {
		View buttonsContainer = view.findViewById(R.id.buttons_container);
		buttonsContainer.setBackgroundColor(AndroidUtils.getColorFromAttr(view.getContext(), R.attr.bg_color));
		View saveButton = view.findViewById(R.id.right_bottom_button);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveTrackInfo();
				dismiss();
			}
		});

		View cancelButton = view.findViewById(R.id.dismiss_button);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				discardSplitChanges();
				discardShowStartFinishChanges();
				FragmentActivity activity = getActivity();
				if (activity != null) {
					activity.onBackPressed();
				}
			}
		});

		UiUtilities.setupDialogButton(isNightMode(), cancelButton, DialogButtonType.SECONDARY, R.string.shared_string_cancel);
		UiUtilities.setupDialogButton(isNightMode(), saveButton, DialogButtonType.PRIMARY, R.string.shared_string_apply);

		AndroidUiHelper.updateVisibility(saveButton, true);
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.buttons_divider), true);
	}

	private void showShadowButton() {
		buttonsShadow.setVisibility(View.VISIBLE);
		buttonsShadow.animate()
				.alpha(0.8f)
				.setDuration(200)
				.setListener(null);
	}

	private void hideShadowButton() {
		buttonsShadow.animate()
				.alpha(0f)
				.setDuration(200);

	}

	private void setupScrollShadow() {
		final View scrollView = getBottomScrollView();
		scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {

			@Override
			public void onScrollChanged() {
				boolean scrollToBottomAvailable = scrollView.canScrollVertically(1);
				if (scrollToBottomAvailable) {
					showShadowButton();
				} else {
					hideShadowButton();
				}
			}
		});
	}

	private void updateButtonsShadow() {
		boolean scrollToBottomAvailable = getBottomScrollView().canScrollVertically(1);
		AndroidUiHelper.updateVisibility(buttonsShadow, scrollToBottomAvailable);
	}

	private void saveCustomColorsToTracks(int prevColor, int newColor) {
		List<GpxDataItem> gpxDataItems = gpxDbHelper.getItems();
		for (GpxDataItem dataItem : gpxDataItems) {
			if (prevColor == dataItem.getColor()) {
				gpxDbHelper.updateColor(dataItem, newColor);
			}
		}
		List<SelectedGpxFile> files = app.getSelectedGpxHelper().getSelectedGPXFiles();
		for (SelectedGpxFile selectedGpxFile : files) {
			if (prevColor == selectedGpxFile.getGpxFile().getColor(0)) {
				selectedGpxFile.getGpxFile().setColor(newColor);
			}
		}
	}

	private void updateColorItems() {
		updateAppearanceIcon();
		if (trackWidthCard != null) {
			trackWidthCard.updateItems();
		}
		if (trackColoringCard != null) {
			trackColoringCard.updateColor();
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.refreshMap();
		}
	}

	private void saveTrackInfo() {
		GPXFile gpxFile = selectedGpxFile.getGpxFile();
		if (gpxFile.showCurrentTrack) {
			app.getSettings().CURRENT_TRACK_COLOR.set(trackDrawInfo.getColor());
			app.getSettings().CURRENT_TRACK_COLORIZATION.set(trackDrawInfo.getGradientScaleType());
			app.getSettings().CURRENT_TRACK_SPEED_GRADIENT_PALETTE.set(Algorithms.arrayToString(trackDrawInfo.getSpeedGradientPalette()));
			app.getSettings().CURRENT_TRACK_ALTITUDE_GRADIENT_PALETTE.set(Algorithms.arrayToString(trackDrawInfo.getAltitudeGradientPalette()));
			app.getSettings().CURRENT_TRACK_SLOPE_GRADIENT_PALETTE.set(Algorithms.arrayToString(trackDrawInfo.getSlopeGradientPalette()));
			app.getSettings().CURRENT_TRACK_WIDTH.set(trackDrawInfo.getWidth());
			app.getSettings().CURRENT_TRACK_SHOW_ARROWS.set(trackDrawInfo.isShowArrows());
			app.getSettings().CURRENT_TRACK_SHOW_START_FINISH.set(trackDrawInfo.isShowStartFinish());
		} else if (gpxDataItem != null) {
			GpxSplitType splitType = GpxSplitType.getSplitTypeByTypeId(trackDrawInfo.getSplitType());
			gpxDbHelper.updateColor(gpxDataItem, trackDrawInfo.getColor());
			gpxDbHelper.updateGradientScalePalette(gpxDataItem, GradientScaleType.SPEED, trackDrawInfo.getSpeedGradientPalette());
			gpxDbHelper.updateGradientScalePalette(gpxDataItem, GradientScaleType.ALTITUDE, trackDrawInfo.getAltitudeGradientPalette());
			gpxDbHelper.updateGradientScalePalette(gpxDataItem, GradientScaleType.SLOPE, trackDrawInfo.getSlopeGradientPalette());
			gpxDbHelper.updateWidth(gpxDataItem, trackDrawInfo.getWidth());
			gpxDbHelper.updateShowArrows(gpxDataItem, trackDrawInfo.isShowArrows());
//			gpxDbHelper.updateShowStartFinish(gpxDataItem, trackDrawInfo.isShowStartFinish());
			gpxDbHelper.updateSplit(gpxDataItem, splitType, trackDrawInfo.getSplitInterval());
			gpxDbHelper.updateGradientScaleType(gpxDataItem, trackDrawInfo.getGradientScaleType());
		}
	}

	private void discardSplitChanges() {
		if (gpxDataItem != null && (gpxDataItem.getSplitType() != trackDrawInfo.getSplitType()
				|| gpxDataItem.getSplitInterval() != trackDrawInfo.getSplitInterval())) {
			int timeSplit = (int) gpxDataItem.getSplitInterval();
			double distanceSplit = gpxDataItem.getSplitInterval();

			GpxSplitType splitType = GpxSplitType.getSplitTypeByTypeId(gpxDataItem.getSplitType());
			applySplit(splitType, timeSplit, distanceSplit);
		}
	}

	private void discardShowStartFinishChanges() {
		app.getSettings().SHOW_START_FINISH_ICONS.set(showStartFinishIconsInitialValue);
	}

	void applySplit(GpxSplitType splitType, int timeSplit, double distanceSplit) {
		if (splitIntervalCard != null) {
			splitIntervalCard.updateContent();
		}
		SplitTrackListener splitTrackListener = new SplitTrackListener() {

			@Override
			public void trackSplittingStarted() {

			}

			@Override
			public void trackSplittingFinished() {
				if (selectedGpxFile != null) {
					List<GpxDisplayGroup> groups = getGpxDisplayGroups();
					selectedGpxFile.setDisplayGroups(groups, app);
				}
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null && AndroidUtils.isActivityNotDestroyed(mapActivity)) {
					mapActivity.refreshMap();
				}
			}
		};
		List<GpxDisplayGroup> groups = getGpxDisplayGroups();
		new SplitTrackAsyncTask(app, splitType, groups, splitTrackListener, trackDrawInfo.isJoinSegments(),
				timeSplit, distanceSplit).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void setupCards() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			ViewGroup cardsContainer = getCardsContainer();
			cardsContainer.removeAllViews();

			if (!selectedGpxFile.isShowCurrentTrack() && !Algorithms.isEmpty(getDisplaySegmentGroups())) {
				splitIntervalCard = new SplitIntervalCard(mapActivity, trackDrawInfo);
				splitIntervalCard.setListener(this);
				cardsContainer.addView(splitIntervalCard.build(mapActivity));
			}

			DirectionArrowsCard directionArrowsCard = new DirectionArrowsCard(mapActivity, trackDrawInfo);
			directionArrowsCard.setListener(this);
			cardsContainer.addView(directionArrowsCard.build(mapActivity));

			ShowStartFinishCard showStartFinishCard = new ShowStartFinishCard(mapActivity, trackDrawInfo);
			showStartFinishCard.setListener(this);
			cardsContainer.addView(showStartFinishCard.build(mapActivity));

			trackColoringCard = new TrackColoringCard(mapActivity, selectedGpxFile.getTrackAnalysis(app), trackDrawInfo);
			trackColoringCard.setListener(this);
			cardsContainer.addView(trackColoringCard.build(mapActivity));

			setupColorsCard(cardsContainer);

			gradientCard = new GradientCard(mapActivity, selectedGpxFile.getTrackAnalysis(app), trackDrawInfo.getGradientScaleType());
			cardsContainer.addView(gradientCard.build(mapActivity));

			trackWidthCard = new TrackWidthCard(mapActivity, trackDrawInfo, new OnNeedScrollListener() {

				@Override
				public void onVerticalScrollNeeded(int y) {
					View view = trackWidthCard.getView();
					if (view != null) {
						int resultYPosition = view.getTop() + y;
						int dialogHeight = getInnerScrollableHeight();
						ScrollView scrollView = (ScrollView) getBottomScrollView();
						if (resultYPosition > (scrollView.getScrollY() + dialogHeight)) {
							scrollView.smoothScrollTo(0, resultYPosition - dialogHeight);
						}
					}
				}
			});
			trackWidthCard.setListener(this);
			cardsContainer.addView(trackWidthCard.build(mapActivity));
		}
	}

	private void setupColorsCard(ViewGroup cardsContainer) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			List<Integer> colors = getTrackColors();
			colorsCard = new ColorsCard(mapActivity, trackDrawInfo.getColor(), this, colors, app.getSettings().CUSTOM_TRACK_COLORS, null);
			colorsCard.setListener(this);
			AndroidUiHelper.updateVisibility(colorsCard.build(mapActivity), trackDrawInfo.getGradientScaleType() == null);
			cardsContainer.addView(colorsCard.getView());
		}
	}

	private List<Integer> getTrackColors() {
		List<Integer> colors = new ArrayList<>();
		for (AppearanceListItem appearanceListItem : getAppearanceItems(app, GpxAppearanceAdapterType.TRACK_COLOR)) {
			if (!colors.contains(appearanceListItem.getColor())) {
				colors.add(appearanceListItem.getColor());
			}
		}
		return colors;
	}

	public List<GpxDisplayGroup> getGpxDisplayGroups() {
		GPXFile gpxFile = selectedGpxFile.getGpxFile();
		if (gpxFile == null) {
			return new ArrayList<>();
		}
		if (gpxFile.modifiedTime != modifiedTime) {
			modifiedTime = gpxFile.modifiedTime;
			displayGroups = app.getSelectedGpxHelper().collectDisplayGroups(gpxFile);
			if (selectedGpxFile.getDisplayGroups(app) != null) {
				displayGroups = selectedGpxFile.getDisplayGroups(app);
			}
		}
		return displayGroups;
	}

	@NonNull
	public List<GpxDisplayGroup> getDisplaySegmentGroups() {
		List<GpxDisplayGroup> groups = new ArrayList<>();
		for (GpxDisplayGroup group : getGpxDisplayGroups()) {
			if (GpxDisplayItemType.TRACK_SEGMENT == group.getType()) {
				groups.add(group);
			}
		}
		return groups;
	}

	public void dismissImmediate() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			try {
				mapActivity.getSupportFragmentManager().popBackStackImmediate(TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
			} catch (Exception e) {
				log.error(e);
			}
		}
	}

	public int getInnerScrollableHeight() {
		int totalScreenHeight = getViewHeight() - getMenuStatePosY(getCurrentMenuState());
		int frameTotalHeight = routeMenuTopShadowAll.getHeight()
				+ controlButtons.getHeight() + buttonsShadow.getHeight();
		return totalScreenHeight - frameTotalHeight;
	}

	public static boolean showInstance(@NonNull MapActivity mapActivity, @NonNull SelectedGpxFile selectedGpxFile, Fragment target) {
		try {
			TrackAppearanceFragment fragment = new TrackAppearanceFragment();
			fragment.setRetainInstance(true);
			fragment.setSelectedGpxFile(selectedGpxFile);
			fragment.setTargetFragment(target, 0);

			mapActivity.getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, fragment.getFragmentTag())
					.addToBackStack(fragment.getFragmentTag())
					.commitAllowingStateLoss();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static int getTransparencyIconId(String widthAttr) {
		if (TRACK_WIDTH_BOLD.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_bold_transparency;
		} else if (TRACK_WIDTH_MEDIUM.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_medium_transparency;
		} else {
			return R.drawable.ic_action_track_line_thin_transparency;
		}
	}

	public static int getWidthIconId(String widthAttr) {
		if (TRACK_WIDTH_BOLD.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_bold_color;
		} else if (TRACK_WIDTH_MEDIUM.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_medium_color;
		} else {
			return R.drawable.ic_action_track_line_thin_color;
		}
	}

	public static int getStrokeIconId(String widthAttr) {
		if (TRACK_WIDTH_BOLD.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_bold_stroke;
		} else if (TRACK_WIDTH_MEDIUM.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_medium_stroke;
		} else {
			return R.drawable.ic_action_track_line_thin_stroke;
		}
	}

	public static int getArrowsIconId(String widthAttr) {
		if (TRACK_WIDTH_BOLD.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_bold_direction;
		} else if (TRACK_WIDTH_MEDIUM.equals(widthAttr)) {
			return R.drawable.ic_action_track_line_medium_direction;
		} else {
			return R.drawable.ic_action_track_line_thin_direction;
		}
	}

	@Override
	protected String getThemeInfoProviderTag() {
		return TAG;
	}

	public interface OnNeedScrollListener {
		void onVerticalScrollNeeded(int y);
	}
}