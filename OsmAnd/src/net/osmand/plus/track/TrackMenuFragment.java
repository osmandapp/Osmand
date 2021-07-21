package net.osmand.plus.track;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import net.osmand.AndroidUtils;
import net.osmand.CallbackWithObject;
import net.osmand.FileUtils;
import net.osmand.FileUtils.RenameCallback;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.GpxSelectionHelper.GpxFileLoaderTask;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndLocationProvider.OsmAndCompassListener;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.UiUtilities.UpdateLocationViewCache;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.MapActivity.ShowQuickSearchMode;
import net.osmand.plus.activities.MapActivityActions;
import net.osmand.plus.base.ContextMenuFragment;
import net.osmand.plus.base.ContextMenuScrollFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.OpenGpxDetailsTask;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.plus.mapcontextmenu.other.TrackChartPoints;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu;
import net.osmand.plus.measurementtool.GpxData;
import net.osmand.plus.measurementtool.MeasurementEditingContext;
import net.osmand.plus.measurementtool.MeasurementToolFragment;
import net.osmand.plus.myplaces.AvailableGPXFragment.GpxInfo;
import net.osmand.plus.myplaces.DeletePointsTask.OnPointsDeleteListener;
import net.osmand.plus.myplaces.MoveGpxFileBottomSheet;
import net.osmand.plus.myplaces.MoveGpxFileBottomSheet.OnTrackFileMoveListener;
import net.osmand.plus.myplaces.SegmentActionsListener;
import net.osmand.plus.myplaces.SplitSegmentDialogFragment;
import net.osmand.plus.myplaces.TrackActivityFragmentAdapter;
import net.osmand.plus.osmedit.OsmEditingPlugin;
import net.osmand.plus.routepreparationmenu.cards.BaseCard;
import net.osmand.plus.routepreparationmenu.cards.BaseCard.CardListener;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.routing.GPXRouteParams.GPXRouteParamsBuilder;
import net.osmand.plus.search.QuickSearchDialogFragment;
import net.osmand.plus.track.SaveGpxAsyncTask.SaveGpxListener;
import net.osmand.plus.track.TrackSelectSegmentBottomSheet.OnSegmentSelectedListener;
import net.osmand.plus.views.AddGpxPointBottomSheetHelper.NewGpxPoint;
import net.osmand.plus.widgets.IconPopupMenu;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import static net.osmand.GPXUtilities.*;
import static net.osmand.plus.activities.MapActivityActions.KEY_LATITUDE;
import static net.osmand.plus.activities.MapActivityActions.KEY_LONGITUDE;
import static net.osmand.plus.activities.TrackActivity.CURRENT_RECORDING;
import static net.osmand.plus.activities.TrackActivity.TRACK_FILE_NAME;
import static net.osmand.plus.myplaces.TrackActivityFragmentAdapter.isGpxFileSelected;
import static net.osmand.plus.track.OptionsCard.ANALYZE_BY_INTERVALS_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.ANALYZE_ON_MAP_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.APPEARANCE_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.CHANGE_FOLDER_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.DELETE_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.DIRECTIONS_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.EDIT_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.JOIN_GAPS_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.RENAME_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.SHARE_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.SHOW_ON_MAP_BUTTON_INDEX;
import static net.osmand.plus.track.OptionsCard.UPLOAD_OSM_BUTTON_INDEX;
import static net.osmand.plus.track.TrackPointsCard.ADD_WAYPOINT_INDEX;
import static net.osmand.plus.track.TrackPointsCard.DELETE_WAYPOINTS_INDEX;
import static net.osmand.plus.track.TrackPointsCard.OPEN_WAYPOINT_INDEX;

public class TrackMenuFragment extends ContextMenuScrollFragment implements CardListener,
		SegmentActionsListener, RenameCallback, OnTrackFileMoveListener, OnPointsDeleteListener,
		OsmAndLocationListener, OsmAndCompassListener, OnSegmentSelectedListener {

	public static final String OPEN_TRACK_MENU = "open_track_menu";
	public static final String RETURN_SCREEN_NAME = "return_screen_name";

	public static final String TAG = TrackMenuFragment.class.getName();
	private static final Log log = PlatformUtil.getLog(TrackMenuFragment.class);

	private OsmandApplication app;
	private TrackDisplayHelper displayHelper;
	private SelectedGpxFile selectedGpxFile;

	private TrackMenuType menuType = TrackMenuType.OVERVIEW;
	private SegmentsCard segmentsCard;
	private OptionsCard optionsCard;
	private DescriptionCard descriptionCard;
	private OverviewCard overviewCard;
	private TrackPointsCard pointsCard;
	private PointsGroupsCard groupsCard;

	private TextView headerTitle;
	private ImageView headerIcon;
	private View toolbarContainer;
	private View searchContainer;
	private ImageView searchButton;
	private EditText searchEditText;
	private View backButtonContainer;
	private TextView toolbarTextView;
	private ViewGroup headerContainer;
	private View routeMenuTopShadowAll;
	private BottomNavigationView bottomNav;
	private OnGlobalLayoutListener scrollViewLayoutListener;

	private String gpxTitle;
	private String returnScreenName;
	private String callingFragmentTag;
	private SelectedGpxPoint gpxPoint;
	private TrackChartPoints trackChartPoints;

	private Float heading;
	private Location lastLocation;
	private UpdateLocationViewCache updateLocationViewCache;
	private boolean locationUpdateStarted;
	private LatLon latLon;

	private int menuTitleHeight;
	private int menuHeaderHeight;
	private int toolbarHeightPx;
	private boolean adjustMapPosition = true;
	private GPXTrackAnalysis analyses;

	public enum TrackMenuType {
		OVERVIEW(R.id.action_overview, R.string.shared_string_overview),
		TRACK(R.id.action_track, R.string.shared_string_gpx_tracks),
		POINTS(R.id.action_points, R.string.shared_string_gpx_points),
		OPTIONS(R.id.action_options, R.string.shared_string_options);

		TrackMenuType(int menuItemId, @StringRes int titleId) {
			this.menuItemId = menuItemId;
			this.titleId = titleId;
		}

		public final int menuItemId;
		public final int titleId;
	}


	@Override
	public int getMainLayoutId() {
		return R.layout.track_menu;
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
		return isPortrait() ? toolbarHeightPx : 0;
	}

	public float getMiddleStateKoef() {
		return 0.5f;
	}

	public int getMinY() {
		return getFullScreenTopPosY();
	}

	@Override
	public int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	@Override
	public int getInitialMenuState() {
		return MenuState.HEADER_ONLY;
	}

	public TrackDisplayHelper getDisplayHelper() {
		return displayHelper;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = requireMyApplication();
		displayHelper = new TrackDisplayHelper(app);
		updateLocationViewCache = app.getUIUtilities().getUpdateLocationViewCache();
		toolbarHeightPx = getResources().getDimensionPixelSize(R.dimen.dashboard_map_toolbar);

		if (selectedGpxFile == null && savedInstanceState != null) {
			String path = savedInstanceState.getString(TRACK_FILE_NAME);
			boolean showCurrentTrack = savedInstanceState.getBoolean(CURRENT_RECORDING);
			MapActivity mapActivity = requireMapActivity();
			loadSelectedGpxFile(mapActivity, path, showCurrentTrack, new CallbackWithObject<SelectedGpxFile>() {
				@Override
				public boolean processResult(SelectedGpxFile result) {
					setSelectedGpxFile(result);
					setupDisplayHelper();
					if (getView() != null) {
						initContent(getView());
					}
					return true;
				}
			});
			if (savedInstanceState.containsKey(KEY_LATITUDE) && savedInstanceState.containsKey(KEY_LONGITUDE)) {
				double latitude = savedInstanceState.getDouble(KEY_LATITUDE);
				double longitude = savedInstanceState.getDouble(KEY_LONGITUDE);
				latLon = new LatLon(latitude, longitude);
			}
		} else if (selectedGpxFile != null) {
			setupDisplayHelper();
			if (FileUtils.isTempFile(app, getGpx().path)) {
				app.getSelectedGpxHelper().selectGpxFile(selectedGpxFile.getGpxFile(), true, false);
			}
		}

		FragmentActivity activity = requireMyActivity();
		activity.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			public void handleOnBackPressed() {
				if (getCurrentMenuState() != MenuState.HEADER_ONLY && isPortrait()) {
					openMenuHeaderOnly();
				} else {
					dismiss();
					MapActivity mapActivity = getMapActivity();
					if (mapActivity != null) {
						MapContextMenu contextMenu = mapActivity.getContextMenu();
						PointDescription pointDescription = contextMenu.getPointDescription();
						if (pointDescription != null && pointDescription.isGpxPoint()) {
							contextMenu.init(contextMenu.getLatLon(), pointDescription, contextMenu.getObject());
							contextMenu.show();
						} else if (Algorithms.objectEquals(callingFragmentTag, QuickSearchDialogFragment.TAG)) {
							mapActivity.showQuickSearch(ShowQuickSearchMode.CURRENT, false);
						} else {
							mapActivity.launchPrevActivityIntent();
						}
					}
				}
			}
		});
	}

	private void setupDisplayHelper() {
		if (!selectedGpxFile.isShowCurrentTrack()) {
			File file = new File(selectedGpxFile.getGpxFile().path);
			displayHelper.setFile(file);
			displayHelper.setGpxDataItem(app.getGpxDbHelper().getItem(file));
		}
		displayHelper.setGpx(selectedGpxFile.getGpxFile());
		String title = getGpx().getArticleTitle();
		if (title == null) {
			title = GpxUiHelper.getGpxTitle(Algorithms.getFileWithoutDirs(getGpx().path));
		}
		gpxTitle = !isCurrentRecordingTrack() ? title
				: app.getString(R.string.shared_string_currently_recording_track);
	}

	public LatLon getLatLon() {
		return latLon;
	}

	public GPXFile getGpx() {
		return displayHelper.getGpx();
	}

	public void setSelectedGpxFile(SelectedGpxFile selectedGpxFile) {
		this.selectedGpxFile = selectedGpxFile;
	}

	public void setLatLon(LatLon latLon) {
		this.latLon = latLon;
	}

	public void setReturnScreenName(String returnScreenName) {
		this.returnScreenName = returnScreenName;
	}

	public void setCallingFragmentTag(String callingFragmentTag) {
		this.callingFragmentTag = callingFragmentTag;
	}

	public void setGpxPoint(SelectedGpxPoint point) {
		this.gpxPoint = point;
	}

	public void setAdjustMapPosition(boolean adjustMapPosition) {
		this.adjustMapPosition = adjustMapPosition;
	}

	private void setAnalyses(@Nullable GPXTrackAnalysis analyses) {
		this.analyses = analyses;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		if (view != null) {
			bottomNav = view.findViewById(R.id.bottom_navigation);
			routeMenuTopShadowAll = view.findViewById(R.id.route_menu_top_shadow_all);
			headerContainer = view.findViewById(R.id.header_container);
			headerTitle = view.findViewById(R.id.title);
			headerIcon = view.findViewById(R.id.icon_view);
			toolbarContainer = view.findViewById(R.id.context_menu_toolbar_container);
			toolbarTextView = view.findViewById(R.id.toolbar_title);
			searchButton = view.findViewById(R.id.search_button);
			searchContainer = view.findViewById(R.id.search_container);
			backButtonContainer = view.findViewById(R.id.back_button_container);

			if (isPortrait()) {
				AndroidUiHelper.updateVisibility(getTopShadow(), true);
			} else {
				int widthNoShadow = getLandscapeNoShadowWidth();
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(widthNoShadow, ViewGroup.LayoutParams.WRAP_CONTENT);
				params.gravity = Gravity.BOTTOM | Gravity.START;
				bottomNav.setLayoutParams(params);
			}
			if (selectedGpxFile != null) {
				initContent(view);
			}
		}
		return view;
	}

	private void initContent(@NonNull View view) {
		setupCards();
		setupToolbar();
		updateHeader();
		updateHeadersBottomShadow();
		setupButtons(view);
		updateCardsLayout();
		if (menuType == TrackMenuType.OVERVIEW && isPortrait()) {
			calculateLayoutAndShowHeader();
		} else {
			calculateLayoutAndUpdateMenuState();
		}
	}

	private void updateHeader() {
		updateHeaderCard();
		headerTitle.setText(getHeaderTitle());

		if (menuType == TrackMenuType.POINTS) {
			AndroidUiHelper.updateVisibility(searchButton, true);
		} else {
			AndroidUiHelper.updateVisibility(toolbarTextView, true);
			AndroidUiHelper.updateVisibility(searchButton, false);
			AndroidUiHelper.updateVisibility(searchContainer, false);
		}
		AndroidUiHelper.updateVisibility(headerIcon, menuType != TrackMenuType.OPTIONS);
	}

	@NonNull
	private String getHeaderTitle() {
		if (menuType == TrackMenuType.TRACK) {
			return app.getString(R.string.shared_string_gpx_track) + "\n" + gpxTitle;
		} else if (menuType == TrackMenuType.OPTIONS) {
			return app.getString(menuType.titleId);
		} else {
			return gpxTitle;
		}
	}

	private void updateHeadersBottomShadow() {
		View scrollView = getBottomScrollView();
		if (menuType != TrackMenuType.TRACK) {
			showBottomHeaderShadow();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(scrollViewLayoutListener);
			} else {
				scrollView.getViewTreeObserver().removeGlobalOnLayoutListener(scrollViewLayoutListener);
			}
		} else {
			scrollViewLayoutListener = () -> {
				boolean scrollToTopAvailable = scrollView.canScrollVertically(-1);
				if (scrollToTopAvailable) {
					showBottomHeaderShadow();
				} else {
					hideBottomHeaderShadow();
				}
			};
			scrollView.getViewTreeObserver().addOnGlobalLayoutListener(scrollViewLayoutListener);
		}
	}
	
	private void showBottomHeaderShadow() {
		if (getBottomContainer() != null) {
			getBottomContainer().setForeground(getIcon(R.drawable.bg_contextmenu_shadow));
		}
	}
	
	private void hideBottomHeaderShadow() {
		if (getBottomContainer() != null) {
			getBottomContainer().setForeground(null);
		}
	}

	private void updateHeaderCard() {
		if (menuType == TrackMenuType.OVERVIEW) {
			addOverviewCardToHeader();
			removeCardViewFromHeader(groupsCard);
		} else {
			if (overviewCard != null) {
				overviewCard.getBlockStatisticsBuilder().stopUpdatingStatBlocks();
			}
			if (menuType == TrackMenuType.POINTS && !Algorithms.isEmpty(pointsCard.getGroups())) {
				addPointsGroupsCardToHeader();
			} else {
				removeCardViewFromHeader(groupsCard);
			}
			removeCardViewFromHeader(overviewCard);
		}
	}

	private void addOverviewCardToHeader() {
		if (overviewCard != null && overviewCard.getView() != null) {
			addCardViewToHeader(overviewCard);
		} else {
			MapActivity mapActivity = requireMapActivity();
			overviewCard = new OverviewCard(mapActivity, this, selectedGpxFile, analyses);
			overviewCard.setListener(this);
			headerContainer.addView(overviewCard.build(mapActivity));
		}
		if (isCurrentRecordingTrack()) {
			overviewCard.getBlockStatisticsBuilder().runUpdatingStatBlocksIfNeeded();
		}
	}

	private void addPointsGroupsCardToHeader() {
		if (groupsCard != null) {
			addCardViewToHeader(groupsCard);
		} else {
			MapActivity mapActivity = requireMapActivity();
			groupsCard = new PointsGroupsCard(mapActivity, pointsCard.getGroups());
			groupsCard.setListener(this);
			headerContainer.addView(groupsCard.build(mapActivity));
		}
	}

	private void removeCardViewFromHeader(MapBaseCard card) {
		if (card != null && card.getView() != null) {
			headerContainer.removeView(card.getView());
		}
	}

	private void addCardViewToHeader(MapBaseCard card) {
		if (card != null && card.getView() != null) {
			ViewGroup parent = ((ViewGroup) card.getView().getParent());
			if (parent != null) {
				parent.removeView(card.getView());
			}
			headerContainer.addView(card.getView());
		}
	}

	private void setupToolbar() {
		toolbarTextView.setText(gpxTitle);

		ImageView closeButton = toolbarContainer.findViewById(R.id.close_button);
		closeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (menuType == TrackMenuType.POINTS) {
					AndroidUiHelper.updateVisibility(toolbarTextView, true);
					AndroidUiHelper.updateVisibility(searchButton, true);
					AndroidUiHelper.updateVisibility(searchContainer, false);
				}
				openMenuHeaderOnly();
			}
		});
		closeButton.setImageResource(AndroidUtils.getNavigationIconResId(toolbarContainer.getContext()));

		searchButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUiHelper.updateVisibility(searchContainer, true);
				AndroidUiHelper.updateVisibility(searchButton, false);
				AndroidUiHelper.updateVisibility(toolbarTextView, false);
			}
		});
		searchEditText = toolbarContainer.findViewById(R.id.searchEditText);
		searchEditText.setHint(R.string.search_poi_filter);
		searchEditText.addTextChangedListener(
				new TextWatcher() {

					@Override
					public void beforeTextChanged(CharSequence s, int start, int count, int after) {
					}

					@Override
					public void onTextChanged(CharSequence s, int start, int before, int count) {
					}

					@Override
					public void afterTextChanged(Editable s) {
						if (pointsCard != null) {
							pointsCard.filter(s.toString());
						}
					}
				}
		);
		ImageView clearButton = toolbarContainer.findViewById(R.id.clearButton);
		clearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!Algorithms.isEmpty(searchEditText.getText())) {
					searchEditText.setText("");
					searchEditText.setSelection(0);
				}
				if (pointsCard != null) {
					pointsCard.updateContent();
				}
			}
		});
		backButtonContainer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null) {
					mapActivity.launchPrevActivityIntent();
				}
				dismiss();
			}
		});
		TextView backButtonText = backButtonContainer.findViewById(R.id.back_button_text);
		backButtonText.setText(returnScreenName);
		ImageView backButtonIcon = backButtonContainer.findViewById(R.id.back_button_icon);
		backButtonIcon.setImageResource(AndroidUtils.getNavigationIconResId(backButtonIcon.getContext()));
	}

	private void setupCards() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			ViewGroup cardsContainer = getCardsContainer();
			cardsContainer.removeAllViews();
			if (menuType == TrackMenuType.TRACK) {
				if (segmentsCard != null && segmentsCard.getView() != null) {
					ViewGroup parent = (ViewGroup) segmentsCard.getView().getParent();
					if (parent != null) {
						parent.removeAllViews();
					}
					cardsContainer.addView(segmentsCard.getView());
				} else {
					segmentsCard = new SegmentsCard(mapActivity, displayHelper, gpxPoint, this);
					segmentsCard.setListener(this);
					cardsContainer.addView(segmentsCard.build(mapActivity));
				}
			} else if (menuType == TrackMenuType.OPTIONS) {
				if (optionsCard != null && optionsCard.getView() != null) {
					ViewGroup parent = (ViewGroup) optionsCard.getView().getParent();
					if (parent != null) {
						parent.removeAllViews();
					}
					cardsContainer.addView(optionsCard.getView());
				} else {
					optionsCard = new OptionsCard(mapActivity, displayHelper, selectedGpxFile);
					optionsCard.setListener(this);
					cardsContainer.addView(optionsCard.build(mapActivity));
				}
			} else if (menuType == TrackMenuType.OVERVIEW) {
				if (descriptionCard != null && descriptionCard.getView() != null) {
					ViewGroup parent = ((ViewGroup) descriptionCard.getView().getParent());
					if (parent != null) {
						parent.removeView(descriptionCard.getView());
					}
					cardsContainer.addView(descriptionCard.getView());
				} else {
					descriptionCard = new DescriptionCard(getMapActivity(), displayHelper.getGpx());
					cardsContainer.addView(descriptionCard.build(mapActivity));
				}
			} else if (menuType == TrackMenuType.POINTS) {
				if (pointsCard != null && pointsCard.getView() != null) {
					ViewGroup parent = (ViewGroup) pointsCard.getView().getParent();
					if (parent != null) {
						parent.removeAllViews();
					}
					cardsContainer.addView(pointsCard.getView());
				} else {
					pointsCard = new TrackPointsCard(mapActivity, displayHelper);
					pointsCard.setListener(this);
					cardsContainer.addView(pointsCard.build(mapActivity));
				}
			}
		}
	}

	private void updateCardsLayout() {
		FrameLayout bottomContainer = getBottomContainer();
		if (bottomContainer == null) {
			return;
		}
		if (menuType == TrackMenuType.OPTIONS) {
			AndroidUtils.setBackground(app, bottomContainer, isNightMode(),
					R.color.list_background_color_light, R.color.list_background_color_dark);
		} else {
			AndroidUtils.setBackground(app, bottomContainer, isNightMode(),
					R.color.activity_background_color_light, R.color.activity_background_color_dark);
		}
	}

	@Override
	protected void calculateLayout(View view, boolean initLayout) {
		menuHeaderHeight = headerContainer.getHeight();
		menuTitleHeight = routeMenuTopShadowAll.getHeight() + bottomNav.getHeight();
		super.calculateLayout(view, initLayout);
	}

	@Override
	protected void setViewY(int y, boolean animated, boolean adjustMapPos) {
		super.setViewY(y, animated, adjustMapPos);
		updateStatusBarColor();
		updateToolbar(y, animated);
	}

	@Override
	protected void updateMainViewLayout(int posY) {
		super.updateMainViewLayout(posY);
		updateStatusBarColor();
		updateToolbar(posY, true);
	}

	@Override
	public boolean shouldShowMapControls(int menuState) {
		return menuState == MenuState.HEADER_ONLY || menuState == MenuState.HALF_SCREEN;
	}

	@Override
	public void onContextMenuStateChanged(@NonNull ContextMenuFragment fragment, int currentMenuState, int previousMenuState) {
		super.onContextMenuStateChanged(fragment, currentMenuState, previousMenuState);

		boolean changed = currentMenuState != previousMenuState;
		if (changed) {
			updateControlsVisibility(true);
			boolean backButtonVisible = !Algorithms.isEmpty(returnScreenName) && currentMenuState == MenuState.HALF_SCREEN;
			AndroidUiHelper.updateVisibility(backButtonContainer, backButtonVisible);
		}
		if (currentMenuState != MenuState.FULL_SCREEN && (changed || adjustMapPosition)) {
			adjustMapPosition(getMenuStatePosY(currentMenuState));
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		updateStatusBarColor();
	}

	@Override
	public void onResume() {
		super.onResume();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && trackChartPoints != null) {
			mapActivity.getMapLayers().getGpxLayer().setTrackChartPoints(trackChartPoints);
		}
		updateControlsVisibility(true);
		startLocationUpdate();
		if (overviewCard != null && menuType == TrackMenuType.OVERVIEW && isCurrentRecordingTrack()) {
			overviewCard.getBlockStatisticsBuilder().runUpdatingStatBlocksIfNeeded();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapLayers().getGpxLayer().setTrackChartPoints(null);
		}
		updateControlsVisibility(false);
		stopLocationUpdate();
		if (overviewCard != null) {
			overviewCard.getBlockStatisticsBuilder().stopUpdatingStatBlocks();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (FileUtils.isTempFile(app, getGpx().path)) {
			FileUtils.removeGpxFile(app, new File(getGpx().path));
		}
	}

	@Override
	public void updateLocation(Location location) {
		if (!MapUtils.areLatLonEqual(lastLocation, location)) {
			lastLocation = location;
			updateLocationUi();
		}
	}

	@Override
	public void updateCompassValue(float value) {
		// 99 in next line used to one-time initialize arrows (with reference vs. fixed-north direction)
		// on non-compass devices
		float lastHeading = heading != null ? heading : 99;
		heading = value;
		if (Math.abs(MapUtils.degreesDiff(lastHeading, heading)) > 5) {
			updateLocationUi();
		} else {
			heading = lastHeading;
		}
	}

	private void updateLocationUi() {
		app.runInUIThread(new Runnable() {
			@Override
			public void run() {
				updateDistanceDirection();
			}
		});
	}

	private void updateDistanceDirection() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && overviewCard != null && overviewCard.getView() != null) {
			View view = overviewCard.getView();
			TextView distanceText = view.findViewById(R.id.distance);
			ImageView direction = view.findViewById(R.id.direction);
			app.getUIUtilities().updateLocationView(updateLocationViewCache, direction, distanceText, latLon);
		}
	}

	private void startLocationUpdate() {
		OsmandApplication app = getMyApplication();
		if (app != null && !locationUpdateStarted) {
			locationUpdateStarted = true;
			app.getLocationProvider().addCompassListener(this);
			app.getLocationProvider().addLocationListener(this);
			updateLocationUi();
		}
	}

	private void stopLocationUpdate() {
		OsmandApplication app = getMyApplication();
		if (app != null && locationUpdateStarted) {
			locationUpdateStarted = false;
			app.getLocationProvider().removeLocationListener(this);
			app.getLocationProvider().removeCompassListener(this);
		}
	}

	@Override
	public void renamedTo(File file) {
		updateFile(file);
	}

	@Override
	public void onFileMove(@NonNull File src, @NonNull File dest) {
		File file = FileUtils.renameGpxFile(app, src, dest);
		if (file != null) {
			updateFile(file);
		} else {
			app.showToastMessage(R.string.file_can_not_be_renamed);
		}
	}

	private void updateFile(File file) {
		displayHelper.setFile(file);
		displayHelper.updateDisplayGroups();
		updateHeader();
		updateContent();
	}

	public void updateControlsVisibility(boolean menuVisible) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			boolean topControlsVisible = shouldShowTopControls(menuVisible);
			boolean bottomControlsVisible = shouldShowBottomControls(menuVisible);
			mapActivity.getWidgetsVisibilityHelper().updateControlsVisibility(topControlsVisible, bottomControlsVisible);
			mapActivity.refreshMap();
		}
	}

	public boolean shouldShowTopControls() {
		return shouldShowTopControls(isVisible());
	}

	public boolean shouldShowTopControls(boolean menuVisible) {
		return !menuVisible || !isPortrait() || getCurrentMenuState() == MenuState.HEADER_ONLY;
	}

	public boolean shouldShowBottomControls(boolean menuVisible) {
		return !menuVisible || !isPortrait();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString(TRACK_FILE_NAME, getGpx().path);
		outState.putBoolean(CURRENT_RECORDING, selectedGpxFile.isShowCurrentTrack());
		if (latLon != null) {
			outState.putDouble(KEY_LATITUDE, latLon.getLatitude());
			outState.putDouble(KEY_LONGITUDE, latLon.getLongitude());
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public int getStatusBarColorId() {
		if (getView() != null && getViewY() <= getFullScreenTopPosY() || !isPortrait()) {
			return isNightMode() ? R.color.status_bar_color_dark : R.color.status_bar_color_light;
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
	protected String getThemeInfoProviderTag() {
		return TAG;
	}

	@Override
	public void onCardLayoutNeeded(@NonNull BaseCard card) {

	}

	@Override
	public void onCardPressed(@NonNull BaseCard card) {

	}

	@Override
	public void onCardButtonPressed(@NonNull BaseCard card, int buttonIndex) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity == null) {
			return;
		}
		GPXFile gpxFile = getGpx();
		if (card instanceof OptionsCard || card instanceof OverviewCard) {
			if (buttonIndex == SHOW_ON_MAP_BUTTON_INDEX) {
				if (FileUtils.isTempFile(app, getGpx().path)) {
					File srcFile = displayHelper.getFile();
					File destFIle = new File(app.getAppPath(IndexConstants.GPX_TRAVEL_DIR), srcFile.getName());
					onFileMove(srcFile, destFIle);
					gpxFile = getGpx();
				} else {
					boolean gpxFileSelected = !isGpxFileSelected(app, gpxFile);
					app.getSelectedGpxHelper().selectGpxFile(gpxFile, gpxFileSelected, false);
				}
				updateContent();
				mapActivity.refreshMap();
			} else if (buttonIndex == APPEARANCE_BUTTON_INDEX) {
				TrackAppearanceFragment.showInstance(mapActivity, selectedGpxFile, this);
			} else if (buttonIndex == DIRECTIONS_BUTTON_INDEX) {
				MapActivityActions mapActions = mapActivity.getMapActions();
				if (gpxFile.getNonEmptySegmentsCount() > 1) {
					TrackSelectSegmentBottomSheet.showInstance(mapActivity.getSupportFragmentManager(), gpxFile, this);
				} else {
					startNavigationForGPX(gpxFile, mapActions, mapActivity);
					dismiss();
				}
			}
			if (buttonIndex == JOIN_GAPS_BUTTON_INDEX) {
				displayHelper.setJoinSegments(!displayHelper.isJoinSegments());
				mapActivity.refreshMap();

				if (segmentsCard != null) {
					segmentsCard.updateContent();
				}
			} else if (buttonIndex == ANALYZE_ON_MAP_BUTTON_INDEX) {
				new OpenGpxDetailsTask(selectedGpxFile, null, mapActivity).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				dismiss();
			} else if (buttonIndex == ANALYZE_BY_INTERVALS_BUTTON_INDEX) {
				FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
				TrkSegment segment = gpxFile.getGeneralSegment();
				if (segment == null) {
					List<TrkSegment> segments = gpxFile.getNonEmptyTrkSegments(false);
					if (!Algorithms.isEmpty(segments)) {
						segment = segments.get(0);
					}
				}
				GpxDisplayItemType[] filterTypes = new GpxDisplayItemType[] {GpxDisplayItemType.TRACK_SEGMENT};
				List<GpxDisplayItem> items = TrackDisplayHelper.flatten(displayHelper.getOriginalGroups(filterTypes));
				if (segment != null && !Algorithms.isEmpty(items)) {
					SplitSegmentDialogFragment.showInstance(fragmentManager, displayHelper, items.get(0), segment);
				}
			} else if (buttonIndex == SHARE_BUTTON_INDEX) {
				OsmandApplication app = mapActivity.getMyApplication();
				if (gpxFile.showCurrentTrack) {
					GpxUiHelper.saveAndShareCurrentGpx(app, gpxFile);
				} else if (!Algorithms.isEmpty(gpxFile.path)) {
					GpxUiHelper.saveAndShareGpxWithAppearance(app, gpxFile);
				}
			} else if (buttonIndex == UPLOAD_OSM_BUTTON_INDEX) {
				OsmEditingPlugin osmEditingPlugin = OsmandPlugin.getActivePlugin(OsmEditingPlugin.class);
				if (osmEditingPlugin != null) {
					GpxInfo gpxInfo = new GpxInfo();
					gpxInfo.gpx = gpxFile;
					gpxInfo.file = new File(gpxFile.path);
					osmEditingPlugin.sendGPXFiles(mapActivity, this, gpxInfo);
				}
			} else if (buttonIndex == EDIT_BUTTON_INDEX) {
				app.getSelectedGpxHelper().selectGpxFile(gpxFile, true, false);
				dismiss();
				String fileName = Algorithms.getFileWithoutDirs(gpxFile.path);
				MeasurementToolFragment.showInstance(mapActivity.getSupportFragmentManager(), fileName);
			} else if (buttonIndex == RENAME_BUTTON_INDEX) {
				FileUtils.renameFile(mapActivity, new File(gpxFile.path), this, true);
			} else if (buttonIndex == CHANGE_FOLDER_BUTTON_INDEX) {
				FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
				MoveGpxFileBottomSheet.showInstance(fragmentManager, this, gpxFile.path, true, false);
			} else if (buttonIndex == DELETE_BUTTON_INDEX) {
				String fileName = Algorithms.getFileWithoutDirs(gpxFile.path);

				AlertDialog.Builder builder = new AlertDialog.Builder(UiUtilities.getThemedContext(mapActivity, isNightMode()));
				builder.setTitle(getString(R.string.delete_confirmation_msg, fileName));
				builder.setMessage(R.string.are_you_sure);
				final String gpxFilePath = gpxFile.path;
				builder.setNegativeButton(R.string.shared_string_cancel, null).setPositiveButton(
						R.string.shared_string_ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (FileUtils.removeGpxFile(app, new File(gpxFilePath))) {
									dismiss();
								}
							}
						});
				builder.show();
			}
		} else if (card instanceof TrackPointsCard) {
			if (buttonIndex == ADD_WAYPOINT_INDEX) {
				PointDescription pointDescription = new PointDescription(PointDescription.POINT_TYPE_WPT, app.getString(R.string.add_waypoint));
				QuadRect rect = displayHelper.getRect();
				NewGpxPoint newGpxPoint = new NewGpxPoint(gpxFile, pointDescription, rect);

				mapActivity.getMapView().fitRectToMap(rect.left, rect.right, rect.top, rect.bottom,
						(int) rect.width(), (int) rect.height(), 0);
				mapActivity.getMapLayers().getContextMenuLayer().enterAddGpxPointMode(newGpxPoint);

				hide();
			} else if (buttonIndex == DELETE_WAYPOINTS_INDEX) {
				TrackPointsCard pointsCard = (TrackPointsCard) card;
				if (pointsCard.isSelectionMode()) {
					pointsCard.deleteItemsAction();
				} else {
					pointsCard.setSelectionMode(true);
				}
			} else if (buttonIndex == OPEN_WAYPOINT_INDEX) {
				dismiss();
			}
		} else if (card instanceof PointsGroupsCard) {
			PointsGroupsCard groupsCard = (PointsGroupsCard) card;
			GpxDisplayGroup group = groupsCard.getSelectedGroup();
			if (pointsCard != null) {
				pointsCard.setSelectedGroup(group);
				pointsCard.updateContent();
			}
		}
	}

	public static void startNavigationForGPX(final GPXFile gpxFile, MapActivityActions mapActions, final MapActivity mapActivity) {
		if (mapActivity.getMyApplication().getRoutingHelper().isFollowingMode()) {
			final WeakReference<MapActivity> activityRef = new WeakReference<>(mapActivity);
			mapActions.stopNavigationActionConfirm(null, new Runnable() {
				@Override
				public void run() {
					MapActivity activity = activityRef.get();
					if (activity != null) {
						activity.getMapActions().enterRoutePlanningModeGivenGpx(gpxFile, null,
								null, null, true, true, MenuState.HEADER_ONLY);
					}
				}
			});
		} else {
			mapActions.stopNavigationWithoutConfirm();
			mapActions.enterRoutePlanningModeGivenGpx(gpxFile, null, null,
					null, true, true, MenuState.HEADER_ONLY);
		}
	}

	public void updateToolbar(int y, boolean animated) {
		final MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (toolbarContainer != null && isPortrait()) {
				if (animated) {
					final float toolbarAlpha = getToolbarAlpha(y);
					if (toolbarAlpha > 0) {
						updateVisibility(toolbarContainer, true);
					}
					toolbarContainer.animate().alpha(toolbarAlpha)
							.setDuration(ContextMenuFragment.ANIMATION_DURATION)
							.setInterpolator(new DecelerateInterpolator())
							.setListener(new AnimatorListenerAdapter() {
								@Override
								public void onAnimationEnd(Animator animation) {
									updateVisibility(toolbarContainer, toolbarAlpha);
									mapActivity.updateStatusBarColor();
								}
							})
							.start();
				} else {
					updateToolbarVisibility(toolbarContainer, y);
					mapActivity.updateStatusBarColor();
				}
			}
		}
	}

	@Override
	protected void onHeaderClick() {
		if (getCurrentMenuState() == MenuState.HEADER_ONLY) {
			updateMenuState();
		}
	}

	private void adjustMapPosition(int y) {
		GPXFile gpxFile = getGpx();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && mapActivity.getMapView() != null && gpxFile != null) {
			QuadRect r = gpxFile.getRect();

			RotatedTileBox tb = mapActivity.getMapView().getCurrentRotatedTileBox().copy();
			int tileBoxWidthPx = 0;
			int tileBoxHeightPx = 0;

			if (!isPortrait()) {
				tileBoxWidthPx = tb.getPixWidth() - getWidth();
			} else {
				int fHeight = getViewHeight() - y - AndroidUtils.getStatusBarHeight(mapActivity);
				tileBoxHeightPx = tb.getPixHeight() - fHeight;
			}
			if (r.left != 0 && r.right != 0) {
				mapActivity.getMapView().fitRectToMap(r.left, r.right, r.top, r.bottom, tileBoxWidthPx, tileBoxHeightPx, 0);
			}
			adjustMapPosition = false;
		}
	}

	private void setupButtons(View view) {
		ColorStateList navColorStateList = AndroidUtils.createBottomNavColorStateList(getContext(), isNightMode());
		BottomNavigationView bottomNav = view.findViewById(R.id.bottom_navigation);
		bottomNav.setItemIconTintList(navColorStateList);
		bottomNav.setItemTextColor(navColorStateList);
		bottomNav.setSelectedItemId(R.id.action_overview);
		bottomNav.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
			@Override
			public boolean onNavigationItemSelected(@NonNull MenuItem item) {
				for (TrackMenuType type : TrackMenuType.values()) {
					if (type.menuItemId == item.getItemId()) {
						menuType = type;
						setupCards();
						updateHeader();
						updateHeadersBottomShadow();
						updateCardsLayout();
						calculateLayoutAndUpdateMenuState();
						break;
					}
				}
				return true;
			}
		});
	}

	private void calculateLayoutAndUpdateMenuState() {
		runLayoutListener(new Runnable() {
			@Override
			public void run() {
				if (getCurrentMenuState() != MenuState.FULL_SCREEN) {
					updateMenuState();
				}
			}
		});
	}

	private void calculateLayoutAndShowHeader() {
		runLayoutListener(new Runnable() {
			@Override
			public void run() {
				int posY = getViewHeight() - menuHeaderHeight - menuTitleHeight - getShadowHeight();
				if (posY < getViewY()) {
					updateMainViewLayout(posY);
				}
				animateMainView(posY, false, getCurrentMenuState(), getCurrentMenuState());
				updateMapControlsPos(TrackMenuFragment.this, posY, true);
			}
		});
	}

	private void updateMenuState() {
		if (menuType == TrackMenuType.OPTIONS) {
			openMenuFullScreen();
		} else {
			openMenuHalfScreen();
		}
	}

	@Override
	public void updateContent() {
		if (overviewCard != null) {
			overviewCard.updateContent();
		}
		if (segmentsCard != null) {
			segmentsCard.updateContent();
		}
		if (optionsCard != null) {
			optionsCard.updateContent();
		}
		if (descriptionCard != null) {
			descriptionCard.updateContent();
		}
		if (pointsCard != null) {
			pointsCard.updateContent();
		}
		setupCards();
	}

	@Override
	public void onChartTouch() {
		getBottomScrollView().requestDisallowInterceptTouchEvent(true);
	}

	@Override
	public void scrollBy(int px) {

	}

	@Override
	public void onPointsDeletionStarted() {

	}

	@Override
	public void onPointsDeleted() {
		if (pointsCard != null) {
			pointsCard.onPointsDeleted();
		}
	}

	@Override
	public void onPointSelected(TrkSegment segment, double lat, double lon) {
		if (trackChartPoints == null) {
			trackChartPoints = new TrackChartPoints();
			trackChartPoints.setGpx(getGpx());
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			int segmentColor = segment != null ? segment.getColor(0) : 0;
			trackChartPoints.setSegmentColor(segmentColor);
			trackChartPoints.setHighlightedPoint(new LatLon(lat, lon));
			mapActivity.getMapLayers().getGpxLayer().setTrackChartPoints(trackChartPoints);
			mapActivity.refreshMap();
		}
	}

	@Override
	public void openSplitInterval(GpxDisplayItem gpxItem, TrkSegment trkSegment) {
		FragmentManager fragmentManager = getFragmentManager();
		if (fragmentManager != null && displayHelper != null) {
			SplitSegmentDialogFragment.showInstance(fragmentManager, displayHelper, gpxItem, trkSegment);
		}
	}

	@Override
	public void openAnalyzeOnMap(@NonNull GpxDisplayItem gpxItem) {
		if (gpxPoint != null) {
			gpxItem.locationOnMap = gpxPoint.getSelectedPoint();
		}
		TrackDetailsMenu trackDetailsMenu = getMapActivity().getTrackDetailsMenu();
		trackDetailsMenu.setGpxItem(gpxItem);
		trackDetailsMenu.setSelectedGpxFile(selectedGpxFile);
		trackDetailsMenu.show();
		hide();
	}

	@Override
	public void showOptionsPopupMenu(View view, final TrkSegment segment, final boolean confirmDeletion, final GpxDisplayItem gpxItem) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			IconPopupMenu optionsPopupMenu = new IconPopupMenu(activity, view.findViewById(R.id.overflow_menu));
			Menu menu = optionsPopupMenu.getMenu();
			optionsPopupMenu.getMenuInflater().inflate(R.menu.track_segment_menu, menu);
			menu.findItem(R.id.action_edit).setIcon(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_edit_dark));
			menu.findItem(R.id.action_delete).setIcon(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_remove_dark));
			if (getGpx().showCurrentTrack) {
				menu.findItem(R.id.split_interval).setVisible(false);
			} else {
				menu.findItem(R.id.split_interval).setIcon(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_split_interval));
			}
			optionsPopupMenu.setOnMenuItemClickListener(new IconPopupMenu.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					int i = item.getItemId();
					if (i == R.id.action_edit) {
						editSegment();
						return true;
					} else if (i == R.id.action_delete) {
						FragmentActivity activity = getActivity();
						if (!confirmDeletion) {
							deleteAndSaveSegment(segment);
						} else if (activity != null) {
							AlertDialog.Builder builder = new AlertDialog.Builder(activity);
							builder.setMessage(R.string.recording_delete_confirm);
							builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									deleteAndSaveSegment(segment);
								}
							});
							builder.setNegativeButton(R.string.shared_string_cancel, null);
							builder.show();
						}
						return true;
					} else if (i == R.id.split_interval) {
						openSplitInterval(gpxItem, segment);
					}
					return false;
				}
			});
			optionsPopupMenu.show();
		}
	}

	@Override
	public void onSegmentSelect(GPXFile gpxFile, int selectedSegment) {
		app.getSettings().GPX_ROUTE_SEGMENT.set(selectedSegment);
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			startNavigationForGPX(gpxFile, mapActivity.getMapActions(), mapActivity);
			GPXRouteParamsBuilder paramsBuilder = app.getRoutingHelper().getCurrentGPXRoute();
			if (paramsBuilder != null) {
				paramsBuilder.setSelectedSegment(selectedSegment);
				app.getRoutingHelper().onSettingsChanged(true);
			}
			dismiss();
		}
	}

	private void editSegment() {
		GPXFile gpxFile = getGpx();
		openPlanRoute(new GpxData(gpxFile));
		hide();
	}

	public void openPlanRoute(GpxData gpxData) {
		QuadRect qr = gpxData.getRect();
		getMapActivity().getMapView().fitRectToMap(qr.left, qr.right, qr.top, qr.bottom, (int) qr.width(), (int) qr.height(), 0);
		MeasurementEditingContext editingContext = new MeasurementEditingContext();
		editingContext.setGpxData(gpxData);
		MeasurementToolFragment.showInstance(getFragmentManager(), editingContext);
	}

	private void deleteAndSaveSegment(TrkSegment segment) {
		if (deleteSegment(segment)) {
			GPXFile gpx = displayHelper.getGpx();
			if (gpx != null) {
				boolean showOnMap = TrackActivityFragmentAdapter.isGpxFileSelected(app, gpx);
				SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().selectGpxFile(gpx, showOnMap, false);
				saveGpx(showOnMap ? selectedGpxFile : null, gpx);
			}
		}
	}

	private boolean deleteSegment(TrkSegment segment) {
		if (segment != null) {
			GPXFile gpx = displayHelper.getGpx();
			if (gpx != null) {
				return gpx.removeTrkSegment(segment);
			}
		}
		return false;
	}

	private void saveGpx(final SelectedGpxFile selectedGpxFile, GPXFile gpxFile) {
		new SaveGpxAsyncTask(new File(gpxFile.path), gpxFile, new SaveGpxListener() {
			@Override
			public void gpxSavingStarted() {

			}

			@Override
			public void gpxSavingFinished(Exception errorMessage) {
				if (selectedGpxFile != null) {
					List<GpxDisplayGroup> groups = displayHelper.getDisplayGroups(new GpxDisplayItemType[] {GpxDisplayItemType.TRACK_SEGMENT});
					selectedGpxFile.setDisplayGroups(groups, app);
					selectedGpxFile.processPoints(app);
				}
				updateContent();
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private boolean isCurrentRecordingTrack() {
		return app.getSavingTrackHelper().getCurrentTrack() == selectedGpxFile;
	}

	private void hide() {
		try {
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
				fragmentManager.beginTransaction().hide(this).commit();
			}
		} catch (Exception e) {
			log.error(e);
		}
	}

	public void show() {
		try {
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
				fragmentManager.beginTransaction().show(this).commit();
			}
		} catch (Exception e) {
			log.error(e);
		}
	}

	public static void openTrack(@NonNull Context context, @Nullable File file, Bundle prevIntentParams) {
		openTrack(context, file, prevIntentParams, null);
	}

	public static void openTrack(@NonNull Context context, @Nullable File file, @Nullable Bundle prevIntentParams, @Nullable String returnScreenName) {
		boolean currentRecording = file == null;
		String path = file != null ? file.getAbsolutePath() : null;
		if (context instanceof MapActivity) {
			TrackMenuFragment.showInstance((MapActivity) context, path, currentRecording, null, null);
		} else {
			Bundle bundle = new Bundle();
			bundle.putString(TRACK_FILE_NAME, path);
			bundle.putBoolean(OPEN_TRACK_MENU, true);
			bundle.putBoolean(CURRENT_RECORDING, currentRecording);
			bundle.putString(RETURN_SCREEN_NAME, returnScreenName);
			MapActivity.launchMapActivityMoveToTop(context, prevIntentParams, null, bundle);
		}
	}

	public static void loadSelectedGpxFile(@NonNull MapActivity mapActivity, @Nullable String path,
	                                       boolean showCurrentTrack,
	                                       final CallbackWithObject<SelectedGpxFile> callback) {
		OsmandApplication app = mapActivity.getMyApplication();
		SelectedGpxFile selectedGpxFile;
		if (showCurrentTrack) {
			selectedGpxFile = app.getSavingTrackHelper().getCurrentTrack();
		} else {
			selectedGpxFile = app.getSelectedGpxHelper().getSelectedFileByPath(path);
		}
		if (selectedGpxFile != null) {
			callback.processResult(selectedGpxFile);
		} else if (!Algorithms.isEmpty(path)) {
			String title = app.getString(R.string.loading_smth, "");
			final ProgressDialog progress = ProgressDialog.show(mapActivity, title, app.getString(R.string.loading_data));
			final WeakReference<MapActivity> mapActivityRef = new WeakReference<>(mapActivity);

			GpxFileLoaderTask gpxFileLoaderTask = new GpxFileLoaderTask(new File(path), new CallbackWithObject<GPXFile>() {
				@Override
				public boolean processResult(GPXFile result) {
					MapActivity mapActivity = mapActivityRef.get();
					if (mapActivity != null) {
						OsmandApplication app = mapActivity.getMyApplication();
						SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().selectGpxFile(result, true, false);
						if (selectedGpxFile != null) {
							callback.processResult(selectedGpxFile);
						}
					}
					if (progress != null && AndroidUtils.isActivityNotDestroyed(mapActivity)) {
						progress.dismiss();
					}
					return true;
				}
			});
			gpxFileLoaderTask.execute();
		}
	}

	public static void showInstance(@NonNull MapActivity mapActivity,
									@Nullable String path,
									boolean showCurrentTrack,
									@Nullable final String returnScreenName,
									@Nullable final String callingFragmentTag) {
		final WeakReference<MapActivity> mapActivityRef = new WeakReference<>(mapActivity);
		loadSelectedGpxFile(mapActivity, path, showCurrentTrack, new CallbackWithObject<SelectedGpxFile>() {
			@Override
			public boolean processResult(SelectedGpxFile selectedGpxFile) {
				MapActivity mapActivity = mapActivityRef.get();
				if (mapActivity != null && selectedGpxFile != null) {
					showInstance(mapActivity, selectedGpxFile, null, returnScreenName, callingFragmentTag, true, null);
				}
				return true;
			}
		});
	}

	public static boolean showInstance(@NonNull MapActivity mapActivity,
	                                   @NonNull SelectedGpxFile selectedGpxFile,
	                                   @Nullable SelectedGpxPoint gpxPoint) {
		return showInstance(mapActivity, selectedGpxFile, gpxPoint, null, null, false, null);
	}

	public static boolean showInstance(@NonNull MapActivity mapActivity,
	                                   @NonNull SelectedGpxFile selectedGpxFile,
	                                   @Nullable SelectedGpxPoint gpxPoint,
	                                   @Nullable String returnScreenName,
	                                   @Nullable String callingFragmentTag,
	                                   boolean adjustMapPosition,
	                                   @Nullable GPXTrackAnalysis analyses) {
		try {
			Bundle args = new Bundle();
			args.putInt(ContextMenuFragment.MENU_STATE_KEY, MenuState.HEADER_ONLY);

			TrackMenuFragment fragment = new TrackMenuFragment();
			fragment.setArguments(args);
			fragment.setRetainInstance(true);
			fragment.setAnalyses(analyses);
			fragment.setSelectedGpxFile(selectedGpxFile);
			fragment.setReturnScreenName(returnScreenName);
			fragment.setCallingFragmentTag(callingFragmentTag);
			fragment.setAdjustMapPosition(adjustMapPosition);

			if (gpxPoint != null) {
				WptPt wptPt = gpxPoint.getSelectedPoint();
				fragment.setLatLon(new LatLon(wptPt.lat, wptPt.lon));
				fragment.setGpxPoint(gpxPoint);
			} else {
				QuadRect rect = selectedGpxFile.getGpxFile().getRect();
				LatLon latLonRect = new LatLon(rect.centerY(), rect.centerX());
				fragment.setLatLon(latLonRect);
			}

			mapActivity.getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, TAG)
					.addToBackStack(fragment.getFragmentTag())
					.commitAllowingStateLoss();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}