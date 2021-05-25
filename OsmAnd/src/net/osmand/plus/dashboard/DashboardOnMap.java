package net.osmand.plus.dashboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.github.ksoichiro.android.observablescrollview.ObservableListView;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollView;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollViewCallbacks;
import com.github.ksoichiro.android.observablescrollview.ScrollState;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.ValueHolder;
import net.osmand.data.LatLon;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuAdapter.OnRowItemClick;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dashboard.tools.DashFragmentData;
import net.osmand.plus.dashboard.tools.DashboardSettingsDialogFragment;
import net.osmand.plus.dashboard.tools.TransactionBuilder;
import net.osmand.plus.dialogs.ConfigureMapMenu;
import net.osmand.plus.dialogs.RasterMapMenu;
import net.osmand.plus.download.DownloadActivity;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.WaypointDialogHelper;
import net.osmand.plus.helpers.WaypointHelper.LocationPointWrapper;
import net.osmand.plus.mapcontextmenu.other.RoutePreferencesMenu;
import net.osmand.plus.mapillary.MapillaryFiltersFragment;
import net.osmand.plus.mapillary.MapillaryPlugin.MapillaryFirstDialogFragment;
import net.osmand.plus.osmedit.OsmNotesMenu;
import net.osmand.plus.rastermaps.OsmandRasterMapsPlugin;
import net.osmand.plus.routepreparationmenu.RoutingOptionsHelper.LocalRoutingParameter;
import net.osmand.plus.routing.IRouteInformationListener;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.srtmplugin.ContourLinesMenu;
import net.osmand.plus.srtmplugin.SRTMPlugin;
import net.osmand.plus.srtmplugin.TerrainFragment;
import net.osmand.plus.views.layers.DownloadedRegionsLayer;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry;
import net.osmand.plus.wikipedia.WikipediaPoiMenu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class DashboardOnMap implements ObservableScrollViewCallbacks, IRouteInformationListener {
	private static final org.apache.commons.logging.Log LOG =
			PlatformUtil.getLog(DashboardOnMap.class);
	private static final String TAG = "DashboardOnMap";
	public static boolean staticVisible = false;
	public static DashboardType staticVisibleType = DashboardType.DASHBOARD;
	public static final String SHOULD_SHOW = "should_show";


	private final DashFragmentData[] fragmentsData = new DashFragmentData[]{
			new DashFragmentData(DashDashboardOrDrawerFragment.TAG, DashDashboardOrDrawerFragment.class,
					DashDashboardOrDrawerFragment.SHOULD_SHOW_FUNCTION, 5, null),
			new DashFragmentData(DashErrorFragment.TAG, DashErrorFragment.class,
					DashErrorFragment.SHOULD_SHOW_FUNCTION, 30, null),
			new DashFragmentData(DashSearchFragment.TAG, DashSearchFragment.class,
					DashSearchFragment.SHOULD_SHOW_FUNCTION, 35, null),
			new DashFragmentData(DashNavigationFragment.TAG, DashNavigationFragment.class,
					DashNavigationFragment.SHOULD_SHOW_FUNCTION, 40, null),
			new DashFragmentData(DashWaypointsFragment.TAG, DashWaypointsFragment.class,
					DashWaypointsFragment.SHOULD_SHOW_FUNCTION, 60, null),
			DashRecentsFragment.FRAGMENT_DATA,
			DashFavoritesFragment.FRAGMENT_DATA,
			new DashFragmentData(DashPluginsFragment.TAG, DashPluginsFragment.class,
					DashPluginsFragment.SHOULD_SHOW_FUNCTION, 140, null)
	};

	private MapActivity mapActivity;
	private ImageView actionButton;
	private View compassButton;
	private FrameLayout dashboardView;
	private float cachedRotate = 0;

	private ArrayAdapter<?> listAdapter;
	private OnItemClickListener listAdapterOnClickListener;

	private boolean visible = false;
	private DashboardType visibleType;
	private DashboardType previousVisibleType;
	private ApplicationMode previousAppMode;
	private boolean landscape;
	private List<WeakReference<DashBaseFragment>> fragList = new LinkedList<>();
	private LatLon mapViewLocation;
	private float heading;
	private boolean mapLinkedToLocation;
	private float mapRotation;
	private boolean inLocationUpdate = false;
	private ObservableListView listView;
	private View listBackgroundView;
	private Toolbar toolbar;
	private View paddingView;
	private int mFlexibleSpaceImageHeight;
	private int mFlexibleBlurSpaceHeight;
	private boolean portrait;
	private long lastUpOrCancelMotionEventTime;
	private TextView listEmptyTextView;
	private int[] animationCoordinates;
	private ProgressBar planRouteProgressBar;

	int baseColor;

	private WaypointDialogHelper waypointDialogHelper;
	private final int[] running = new int[]{-1};
	private List<LocationPointWrapper> deletedPoints = new ArrayList<>();
	private Drawable gradientToolbar;
	boolean nightMode;

	public DashFragmentData[] getFragmentsData() {
		return fragmentsData;
	}

	public enum DashboardType {
		CONFIGURE_SCREEN,
		CONFIGURE_MAP,
		LIST_MENU,
		ROUTE_PREFERENCES,
		DASHBOARD,
		OVERLAY_MAP,
		UNDERLAY_MAP,
		MAPILLARY,
		CONTOUR_LINES,
		OSM_NOTES,
		WIKIPEDIA,
		TERRAIN
	}

	private Map<DashboardActionButtonType, DashboardActionButton> actionButtons = new HashMap<>();

	public enum DashboardActionButtonType {
		MY_LOCATION,
		NAVIGATE,
		ROUTE
	}

	private class DashboardActionButton {
		private Drawable icon;
		private String text;
		private View.OnClickListener onClickListener;
	}

	public DashboardOnMap(MapActivity ma) {
		this.mapActivity = ma;
	}

	public WaypointDialogHelper getWaypointDialogHelper() {
		return waypointDialogHelper;
	}

	public void updateRouteCalculationProgress(int progress) {
		if (planRouteProgressBar != null) {
			if (planRouteProgressBar.getVisibility() != View.VISIBLE) {
				planRouteProgressBar.setVisibility(View.VISIBLE);
			}
			planRouteProgressBar.setProgress(progress);
		}
	}

	public void routeCalculationFinished() {
		if (planRouteProgressBar != null) {
			planRouteProgressBar.setVisibility(View.GONE);
		}
	}

	public void createDashboardView() {
		baseColor = ContextCompat.getColor(mapActivity, R.color.osmand_orange) & 0x00ffffff;
		waypointDialogHelper = new WaypointDialogHelper(mapActivity);
		landscape = !AndroidUiHelper.isOrientationPortrait(mapActivity);
		dashboardView = (FrameLayout) mapActivity.findViewById(R.id.dashboard);
		AndroidUtils.addStatusBarPadding21v(mapActivity, dashboardView);
		final View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				hideDashboard();
				mapActivity.dismissSettingsScreens();
			}
		};
		toolbar = ((Toolbar) dashboardView.findViewById(R.id.toolbar));
		ObservableScrollView scrollView = ((ObservableScrollView) dashboardView.findViewById(R.id.main_scroll));
		listView = (ObservableListView) dashboardView.findViewById(R.id.dash_list_view);
		//listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		listView.setDrawSelectorOnTop(true);
		listView.setScrollViewCallbacks(this);
		listEmptyTextView = (TextView) dashboardView.findViewById(R.id.emptyTextView);
		gradientToolbar = AppCompatResources.getDrawable(mapActivity, R.drawable.gradient_toolbar).mutate();
		if (AndroidUiHelper.isOrientationPortrait(mapActivity)) {
			this.portrait = true;
			scrollView.setScrollViewCallbacks(this);
			listView.setScrollViewCallbacks(this);
			mFlexibleSpaceImageHeight = mapActivity.getResources().getDimensionPixelSize(
					R.dimen.dashboard_map_top_padding);
			mFlexibleBlurSpaceHeight = mapActivity.getResources().getDimensionPixelSize(
					R.dimen.dashboard_map_toolbar);
			// Set padding view for ListView. This is the flexible space.
			paddingView = new FrameLayout(mapActivity);
			AbsListView.LayoutParams lp = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
					mFlexibleSpaceImageHeight);
			paddingView.setLayoutParams(lp);
			// This is required to disable header's list selector effect
			paddingView.setClickable(true);
			paddingView.setOnClickListener(listener);

			FrameLayout shadowContainer = new FrameLayout(mapActivity);
			FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			fl.gravity = Gravity.BOTTOM;
			shadowContainer.setLayoutParams(fl);
			ImageView shadow = new ImageView(mapActivity);
			shadow.setImageDrawable(AppCompatResources.getDrawable(mapActivity, R.drawable.bg_shadow_onmap));
			shadow.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
			shadow.setScaleType(ScaleType.FIT_XY);
			shadowContainer.addView(shadow);
			((FrameLayout) paddingView).addView(shadowContainer);
			listView.addHeaderView(paddingView);
			listBackgroundView = mapActivity.findViewById(R.id.dash_list_background);
		}
		dashboardView.findViewById(R.id.animateContent).setOnClickListener(listener);
		dashboardView.findViewById(R.id.map_part_dashboard).setOnClickListener(listener);

		View pbContainer = LayoutInflater.from(mapActivity).inflate(R.layout.plan_route_progress_bar, null);
		planRouteProgressBar = (ProgressBar) pbContainer.findViewById(R.id.progress_bar);
		listView.addHeaderView(pbContainer);

		initActionButtons();
		dashboardView.addView(actionButton);
	}

	private void updateListBackgroundHeight() {
		if (listBackgroundView != null) {
			final View contentView = mapActivity.getWindow().getDecorView().findViewById(android.R.id.content);
			ViewTreeObserver vto = contentView.getViewTreeObserver();
			vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

				@Override
				public void onGlobalLayout() {

					ViewTreeObserver obs = contentView.getViewTreeObserver();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
						obs.removeOnGlobalLayoutListener(this);
					} else {
						//noinspection deprecation
						obs.removeGlobalOnLayoutListener(this);
					}
					listBackgroundView.getLayoutParams().height = contentView.getHeight();
				}
			});
		}
	}

	private void updateToolbarActions() {
		TextView tv = (TextView) dashboardView.findViewById(R.id.toolbar_text);
		tv.setText("");
		if (visibleType == DashboardType.CONFIGURE_MAP) {
			tv.setText(R.string.configure_map);
		} else if (visibleType == DashboardType.CONFIGURE_SCREEN) {
			tv.setText(R.string.layer_map_appearance);
		} else if (visibleType == DashboardType.ROUTE_PREFERENCES) {
			tv.setText(R.string.shared_string_settings);
		} else if (visibleType == DashboardType.UNDERLAY_MAP) {
			tv.setText(R.string.map_underlay);
		} else if (visibleType == DashboardType.OVERLAY_MAP) {
			tv.setText(R.string.map_overlay);
		} else if (visibleType == DashboardType.MAPILLARY) {
			tv.setText(R.string.street_level_imagery);
		} else if (visibleType == DashboardType.CONTOUR_LINES) {
			tv.setText(R.string.srtm_plugin_name);
		} else if (visibleType == DashboardType.OSM_NOTES) {
			tv.setText(R.string.osm_notes);
		} else if (visibleType == DashboardType.TERRAIN) {
			tv.setText(R.string.shared_string_terrain);
		} else if (visibleType == DashboardType.WIKIPEDIA) {
			tv.setText(R.string.shared_string_wikipedia);
		}
		ImageView edit = (ImageView) dashboardView.findViewById(R.id.toolbar_edit);
		edit.setVisibility(View.GONE);
		ImageView sort = (ImageView) dashboardView.findViewById(R.id.toolbar_sort);
		sort.setVisibility(View.GONE);
		ImageView ok = (ImageView) dashboardView.findViewById(R.id.toolbar_ok);
		ok.setVisibility(View.GONE);
		ImageView flat = (ImageView) dashboardView.findViewById(R.id.toolbar_flat);
		flat.setVisibility(View.GONE);
		ImageView settingsButton = (ImageView) dashboardView.findViewById(R.id.toolbar_settings);
		settingsButton.setVisibility(View.GONE);
		UiUtilities iconsCache = mapActivity.getMyApplication().getUIUtilities();
		ImageView lst = (ImageView) dashboardView.findViewById(R.id.toolbar_list);
		lst.setVisibility(View.GONE);
		ImageButton back = (ImageButton) dashboardView.findViewById(R.id.toolbar_back);
		Drawable icBack = getMyApplication().getUIUtilities().getIcon(AndroidUtils.getNavigationIconResId(mapActivity));
		back.setImageDrawable(icBack);
		back.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				backPressed();
			}
		});

		if (visibleType == DashboardType.DASHBOARD || visibleType == DashboardType.LIST_MENU) {
			settingsButton.setVisibility(View.VISIBLE);
			settingsButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					new DashboardSettingsDialogFragment().show(
							mapActivity.getSupportFragmentManager(), "dashboard_settings");
				}
			});
			lst.setVisibility(View.VISIBLE);
			lst.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					hideDashboard(false);
					mapActivity.openDrawer();
				}
			});
		}

		toolbar.getMenu().clear();
		if (visibleType == DashboardType.CONFIGURE_SCREEN) {
			toolbar.inflateMenu(R.menu.refresh_menu);
			toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem menuItem) {
					if (menuItem.getItemId() == R.id.action_refresh) {
						MapWidgetRegistry registry = mapActivity.getMapLayers().getMapWidgetRegistry();
						registry.resetToDefault();
						MapInfoLayer mil = mapActivity.getMapLayers().getMapInfoLayer();
						if (mil != null) {
							mil.recreateControls();
						}
						updateListAdapter(registry.getViewConfigureMenuAdapter());
					}
					return false;
				}
			});
		}
	}

	private FrameLayout.LayoutParams getActionButtonLayoutParams(int btnSizePx) {
		int topPadPx = mapActivity.getResources().getDimensionPixelSize(R.dimen.dashboard_map_top_padding);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(btnSizePx, btnSizePx);
		int marginEnd = btnSizePx / 4;
		AndroidUtils.setMargins(params, 0, landscape ? 0 : topPadPx - 2 * btnSizePx, marginEnd, landscape ? marginEnd : 0);
		params.gravity = landscape ? Gravity.BOTTOM | Gravity.END : Gravity.TOP | Gravity.END;
		return params;
	}

	private void initActionButtons() {
		actionButton = new ImageView(mapActivity);
		int btnSizePx = mapActivity.getResources().getDimensionPixelSize(R.dimen.map_button_size);
		actionButton.setLayoutParams(getActionButtonLayoutParams(btnSizePx));
		actionButton.setBackgroundResource(R.drawable.btn_circle_blue);
		int iconSizePx = mapActivity.getResources().getDimensionPixelSize(R.dimen.map_widget_icon);
		int iconPadding = (btnSizePx - iconSizePx) / 2;
		actionButton.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
		hideActionButton();


		DashboardActionButton myLocationButton = new DashboardActionButton();
		myLocationButton.icon = AppCompatResources.getDrawable(mapActivity, R.drawable.ic_my_location);
		myLocationButton.text = mapActivity.getString(R.string.map_widget_back_to_loc);
		myLocationButton.onClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getMyApplication().accessibilityEnabled()) {
					mapActivity.getMapActions().whereAmIDialog();
				} else {
					mapActivity.getMapViewTrackingUtilities().backToLocationImpl();
				}
				hideDashboard();
			}
		};

		DashboardActionButton navigateButton = new DashboardActionButton();
		navigateButton.icon = AppCompatResources.getDrawable(mapActivity, R.drawable.ic_action_start_navigation);
		navigateButton.text = mapActivity.getString(R.string.follow);
		navigateButton.onClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mapActivity.getMapLayers().getMapControlsLayer().doNavigate();
				hideDashboard();
			}
		};

		DashboardActionButton routeButton = new DashboardActionButton();
		routeButton.icon = AppCompatResources.getDrawable(mapActivity, R.drawable.ic_action_gdirections_dark);
		routeButton.text = mapActivity.getString(R.string.layer_route);
		routeButton.onClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				hideDashboard();
				mapActivity.getMapLayers().getMapControlsLayer().doRoute(false);
			}
		};

		actionButtons.put(DashboardActionButtonType.MY_LOCATION, myLocationButton);
		actionButtons.put(DashboardActionButtonType.NAVIGATE, navigateButton);
		actionButtons.put(DashboardActionButtonType.ROUTE, routeButton);
	}

	private void setActionButton(DashboardType type) {
		DashboardActionButton button = null;

		if (type == DashboardType.DASHBOARD
				|| type == DashboardType.LIST_MENU
				|| type == DashboardType.CONFIGURE_SCREEN) {
			button = actionButtons.get(DashboardActionButtonType.MY_LOCATION);
		} else if (type == DashboardType.ROUTE_PREFERENCES) {
			button = actionButtons.get(DashboardActionButtonType.NAVIGATE);
		}

		if (button != null) {
			actionButton.setImageDrawable(button.icon);
			actionButton.setContentDescription(button.text);
			actionButton.setOnClickListener(button.onClickListener);
		}
	}

	private boolean isInRouteOrPlannigMode() {
		boolean routePlanningMode = false;
		RoutingHelper rh = mapActivity.getRoutingHelper();
		if (rh.isRoutePlanningMode()) {
			routePlanningMode = true;
		} else if ((rh.isRouteCalculated() || rh.isRouteBeingCalculated()) && !rh.isFollowingMode()) {
			routePlanningMode = true;
		}
		boolean routeFollowingMode = !routePlanningMode && rh.isFollowingMode();
		return routePlanningMode || routeFollowingMode;
	}

	private void hideActionButton() {
		actionButton.setVisibility(View.GONE);
		if (compassButton != null) {
			compassButton.setVisibility(View.GONE);
		}
	}

	public LatLon getMapViewLocation() {
		return mapViewLocation;
	}

	public float getHeading() {
		return heading;
	}

	public float getMapRotation() {
		return mapRotation;
	}

	public boolean isMapLinkedToLocation() {
		return mapLinkedToLocation;
	}

	protected OsmandApplication getMyApplication() {
		return mapActivity.getMyApplication();
	}

	public ArrayAdapter<?> getListAdapter() {
		return listAdapter;
	}

	public OnItemClickListener getListAdapterOnClickListener() {
		return listAdapterOnClickListener;
	}

	public void hideDashboard() {
		setDashboardVisibility(false, visibleType);
	}

	public void hideDashboard(boolean animation) {
		setDashboardVisibility(false, visibleType, animation);
	}

	public void setDashboardVisibility(boolean visible, DashboardType type) {
		setDashboardVisibility(visible, type, null);
	}

	public void setDashboardVisibility(boolean visible, DashboardType type, int[] animationCoordinates) {
		boolean animate = !getMyApplication().getSettings().DO_NOT_USE_ANIMATIONS.get();
		setDashboardVisibility(visible, type, this.visible ? visibleType : null, animate, animationCoordinates);
	}

	public void setDashboardVisibility(boolean visible, DashboardType type, boolean animation) {
		setDashboardVisibility(visible, type, animation, null);
	}

	public void setDashboardVisibility(boolean visible, DashboardType type, boolean animation, int[] animationCoordinates) {
		setDashboardVisibility(visible, type, this.visible ? visibleType : null, animation, animationCoordinates);
	}

	public void refreshDashboardFragments() {
		addOrUpdateDashboardFragments();
	}

	@ColorRes
	public int getStatusBarColor() {
		return R.color.status_bar_transparent_gradient;
	}

	public void setDashboardVisibility(boolean visible, DashboardType type, DashboardType prevItem, boolean animation, int[] animationCoordinates) {
		if (visible == this.visible && type == visibleType) {
			return;
		}
		mapActivity.getRoutingHelper().removeListener(this);
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		this.previousVisibleType = prevItem;
		this.visible = visible;
		ApplicationMode currentAppMode = getMyApplication().getSettings().APPLICATION_MODE.get();
		boolean appModeChanged = currentAppMode != previousAppMode;

		boolean refresh = this.visibleType == type && !appModeChanged;
		previousAppMode = currentAppMode;
		visibleType = type;
		DashboardOnMap.staticVisible = visible;
		DashboardOnMap.staticVisibleType = type;
		mapActivity.enableDrawer();
		removeFragment(MapillaryFiltersFragment.TAG);
		removeFragment(TerrainFragment.TAG);

		if (visible) {
			mapActivity.dismissCardDialog();
			mapActivity.dismissTrackMenu();
			mapActivity.getContextMenu().hideMenues();
			mapViewLocation = mapActivity.getMapLocation();
			mapRotation = mapActivity.getMapRotate();
			mapLinkedToLocation = mapActivity.getMapViewTrackingUtilities().isMapLinkedToLocation();
			mapActivity.getMapViewTrackingUtilities().setDashboard(this);
			mapActivity.disableDrawer();
			dashboardView.setVisibility(View.VISIBLE);
			if (isActionButtonVisible()) {
				setActionButton(visibleType);
				actionButton.setVisibility(View.VISIBLE);
			} else {
				hideActionButton();
				if (visibleType == DashboardType.CONFIGURE_MAP) {
					int btnSizePx = mapActivity.getResources().getDimensionPixelSize(R.dimen.map_small_button_size);
					compassButton = mapActivity.getMapLayers().getMapControlsLayer()
							.moveCompassButton(dashboardView, getActionButtonLayoutParams(btnSizePx), nightMode);
				}
			}
			updateDownloadBtn();
			View listViewLayout = dashboardView.findViewById(R.id.dash_list_view_layout);
			ScrollView scrollView = (ScrollView) dashboardView.findViewById(R.id.main_scroll);
			if (visibleType == DashboardType.DASHBOARD
					|| visibleType == DashboardType.MAPILLARY
					|| visibleType == DashboardType.TERRAIN) {
				if (visibleType == DashboardType.DASHBOARD) {
					addOrUpdateDashboardFragments();
				} else if (visibleType == DashboardType.MAPILLARY) {
					mapActivity.getSupportFragmentManager().beginTransaction()
							.replace(R.id.content, new MapillaryFiltersFragment(), MapillaryFiltersFragment.TAG)
							.commit();
				} else {
					mapActivity.getSupportFragmentManager().beginTransaction()
							.replace(R.id.content, new TerrainFragment(), TerrainFragment.TAG)
							.commit();
				}
				scrollView.setVisibility(View.VISIBLE);
				scrollView.scrollTo(0, 0);
				listViewLayout.setVisibility(View.GONE);
				onScrollChanged(scrollView.getScrollY(), false, false);
			} else {
				scrollView.setVisibility(View.GONE);
				listViewLayout.setVisibility(View.VISIBLE);
				if (refresh) {
					refreshContent(false);
				} else {
					listView.scrollTo(0, 0);
					listView.clearParams();
					onScrollChanged(listView.getScrollY(), false, false);
					updateListAdapter();
				}
				updateListBackgroundHeight();
				applyDayNightMode();
			}
			mapActivity.findViewById(R.id.toolbar_back).setVisibility(isBackButtonVisible() ? View.VISIBLE : View.GONE);
			mapActivity.getMapLayers().getMapControlsLayer().hideMapControls();

			updateToolbarActions();
			//fabButton.showFloatingActionButton();
			open(animation, animationCoordinates);
			updateLocation(true, true, false);
//			addOrUpdateDashboardFragments();
			mapActivity.getRoutingHelper().addListener(this);
		} else {
			mapActivity.getMapViewTrackingUtilities().setDashboard(null);
			hide(animation);
			mapActivity.getMapLayers().getMapControlsLayer().showMapControlsIfHidden();
			hideActionButton();
			for (WeakReference<DashBaseFragment> df : fragList) {
				if (df.get() != null) {
					df.get().onCloseDash();
				}
			}

			OsmandSettings settings = getMyApplication().getSettings();
			if (settings.SHOW_MAPILLARY.get() && !settings.MAPILLARY_FIRST_DIALOG_SHOWN.get()) {
				MapillaryFirstDialogFragment fragment = new MapillaryFirstDialogFragment();
				fragment.show(mapActivity.getSupportFragmentManager(), MapillaryFirstDialogFragment.TAG);
				settings.MAPILLARY_FIRST_DIALOG_SHOWN.set(true);
			}
		}
		mapActivity.updateStatusBarColor();
	}

	public void updateDashboard() {
		if (visibleType == DashboardType.ROUTE_PREFERENCES) {
			refreshContent(false);
		}
	}

	private void applyDayNightMode() {
		final int backgroundColor;
		backgroundColor = ContextCompat.getColor(mapActivity,
				nightMode ? R.color.activity_background_color_dark
						: R.color.activity_background_color_light);
		Drawable dividerDrawable = new ColorDrawable(ContextCompat.getColor(mapActivity,
				nightMode ? R.color.divider_color_dark : R.color.divider_color_light));

		if (listBackgroundView != null) {
			listBackgroundView.setBackgroundColor(backgroundColor);
		} else {
//			listView.setBackgroundColor(backgroundColor);
			listEmptyTextView.setBackgroundColor(backgroundColor);
		}
		if (visibleType != DashboardType.CONFIGURE_SCREEN
				&& visibleType != DashboardType.CONFIGURE_MAP
				&& visibleType != DashboardType.CONTOUR_LINES
				&& visibleType != DashboardType.TERRAIN
				&& visibleType != DashboardType.OSM_NOTES
				&& visibleType != DashboardType.WIKIPEDIA) {
			listView.setDivider(dividerDrawable);
			listView.setDividerHeight(AndroidUtils.dpToPx(mapActivity, 1f));
		} else {
			listView.setDivider(null);
		}
		AndroidUtils.setTextSecondaryColor(mapActivity, listEmptyTextView, nightMode);

		if (planRouteProgressBar != null) {
			mapActivity.setupRouteCalculationProgressBar(planRouteProgressBar);
		}
	}

	private void updateListAdapter() {
		listEmptyTextView.setVisibility(View.GONE);
		listView.setEmptyView(null);
		ContextMenuAdapter cm = null;
		if (visibleType == DashboardType.CONFIGURE_SCREEN) {
			cm = mapActivity.getMapLayers().getMapWidgetRegistry().getViewConfigureMenuAdapter();
		} else if (visibleType == DashboardType.CONFIGURE_MAP) {
			cm = new ConfigureMapMenu().createListAdapter(mapActivity);
		} else if (visibleType == DashboardType.LIST_MENU) {
			cm = mapActivity.getMapActions().createMainOptionsMenu();
		} else if (visibleType == DashboardType.ROUTE_PREFERENCES) {
			RoutePreferencesMenu routePreferencesMenu = new RoutePreferencesMenu(mapActivity);
			ArrayAdapter<LocalRoutingParameter> listAdapter = routePreferencesMenu.getRoutePreferencesDrawerAdapter(nightMode);
			OnItemClickListener listener = routePreferencesMenu.getItemClickListener(listAdapter);
			updateListAdapter(listAdapter, listener);
		} else if (visibleType == DashboardType.UNDERLAY_MAP) {
			cm = RasterMapMenu.createListAdapter(mapActivity, OsmandRasterMapsPlugin.RasterMapType.UNDERLAY);
		} else if (visibleType == DashboardType.OVERLAY_MAP) {
			cm = RasterMapMenu.createListAdapter(mapActivity, OsmandRasterMapsPlugin.RasterMapType.OVERLAY);
		} else if (visibleType == DashboardType.CONTOUR_LINES) {
			cm = ContourLinesMenu.createListAdapter(mapActivity);
		} else if (visibleType == DashboardType.OSM_NOTES) {
			cm = OsmNotesMenu.createListAdapter(mapActivity);
		} else if (visibleType == DashboardType.WIKIPEDIA) {
			cm = WikipediaPoiMenu.createListAdapter(mapActivity);
		}
		if (cm != null) {
			updateListAdapter(cm);
		}
	}

	public void updateListAdapter(ContextMenuAdapter cm) {
		boolean nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		if (this.nightMode != nightMode) {
			this.nightMode = nightMode;
			applyDayNightMode();
		}
		final ArrayAdapter<ContextMenuItem> listAdapter = cm.createListAdapter(mapActivity, !nightMode);
		OnItemClickListener listener = getOptionsMenuOnClickListener(cm, listAdapter);
		updateListAdapter(listAdapter, listener);
	}

	public void onNewDownloadIndexes() {
		if (visibleType == DashboardType.CONTOUR_LINES
				|| visibleType == DashboardType.TERRAIN
				|| visibleType == DashboardType.WIKIPEDIA) {
			refreshContent(true);
		}
	}

	@SuppressWarnings("unchecked")
	public void onDownloadInProgress() {
		if (visibleType == DashboardType.CONTOUR_LINES || visibleType == DashboardType.TERRAIN
				|| visibleType == DashboardType.WIKIPEDIA) {
			DownloadIndexesThread downloadThread = getMyApplication().getDownloadThread();
			IndexItem downloadIndexItem = downloadThread.getCurrentDownloadingItem();
			if (downloadIndexItem != null) {
				int downloadProgress = downloadThread.getCurrentDownloadingItemProgress();
				ArrayAdapter<ContextMenuItem> adapter = (ArrayAdapter<ContextMenuItem>) listAdapter;
				for (int i = 0; i < adapter.getCount(); i++) {
					ContextMenuItem item = adapter.getItem(i);
					if (item != null && item.getProgressListener() != null) {
						item.getProgressListener().onProgressChanged(
								downloadIndexItem, downloadProgress, adapter, (int) adapter.getItemId(i), i);
					}
				}
			}
		}
	}

	public void onDownloadHasFinished() {
		if (visibleType == DashboardType.CONTOUR_LINES || visibleType == DashboardType.TERRAIN) {
			refreshContent(true);
			if (visibleType == DashboardType.TERRAIN) {
				SRTMPlugin plugin = OsmandPlugin.getEnabledPlugin(SRTMPlugin.class);
				if (plugin != null && plugin.isTerrainLayerEnabled()) {
					plugin.registerLayers(mapActivity);
				}
			}
			mapActivity.refreshMapComplete();
		} else if (visibleType == DashboardType.WIKIPEDIA) {
			refreshContent(true);
		}
	}

	public void refreshContent(boolean force) {
		if (visibleType == DashboardType.MAPILLARY) {
			Fragment mapillaryFragment = mapActivity.getSupportFragmentManager().findFragmentByTag(MapillaryFiltersFragment.TAG);
			mapActivity.getSupportFragmentManager().beginTransaction()
					.detach(mapillaryFragment)
					.attach(mapillaryFragment)
					.commit();
		} else if (visibleType == DashboardType.CONFIGURE_SCREEN || force) {
			updateListAdapter();
		} else if (visibleType == DashboardType.CONFIGURE_MAP || visibleType == DashboardType.ROUTE_PREFERENCES) {
			int index = listView.getFirstVisiblePosition();
			View v = listView.getChildAt(0);
			int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());
			updateListAdapter();
			((ListView) listView).setSelectionFromTop(index, top);
		} else if (visibleType == DashboardType.TERRAIN) {
			Fragment terrainFragment = mapActivity.getSupportFragmentManager().findFragmentByTag(TerrainFragment.TAG);
			if (terrainFragment != null) {
				mapActivity.getSupportFragmentManager().beginTransaction()
						.detach(terrainFragment)
						.attach(terrainFragment)
						.commit();
			}
		} else {
			listAdapter.notifyDataSetChanged();
		}
	}

	private OnItemClickListener getOptionsMenuOnClickListener(final ContextMenuAdapter cm,
															  final ArrayAdapter<ContextMenuItem> listAdapter) {
		return new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
				ContextMenuItem item = cm.getItem(which);
				ContextMenuAdapter.ItemClickListener click = item.getItemClickListener();
				if (click instanceof OnRowItemClick) {
					boolean cl = ((OnRowItemClick) click).onRowItemClick(listAdapter, view, item.getTitleId(), which);
					if (cl) {
						hideDashboard();
					}
				} else if (click != null) {
					CompoundButton btn = (CompoundButton) view.findViewById(R.id.toggle_item);
					if (btn != null && btn.getVisibility() == View.VISIBLE) {
						btn.setChecked(!btn.isChecked());
					} else {
						if (click.onContextMenuClick(listAdapter, item.getTitleId(), which, false, null)) {
							hideDashboard();
						}
					}
				} else {
					if (!item.isCategory()) {
						hideDashboard();
					}
				}
			}
		};
	}

	private void updateDownloadBtn() {
		Button btn = (Button) dashboardView.findViewById(R.id.map_download_button);
		String filter = null;
		String txt = "";
		OsmandMapTileView mv = mapActivity.getMapView();
		if (mv != null && !mapActivity.getMyApplication().isApplicationInitializing()) {
			if (mv.getZoom() < 11 && !mapActivity.getMyApplication().getResourceManager().containsBasemap()) {
				filter = "basemap";
				txt = mapActivity.getString(R.string.shared_string_download) + " "
						+ mapActivity.getString(R.string.base_world_map);
			} else {
				DownloadedRegionsLayer dl = mv.getLayerByClass(DownloadedRegionsLayer.class);
				if (dl != null) {
					StringBuilder btnName = new StringBuilder();
					filter = dl.getFilter(btnName);
					txt = btnName.toString();
				}
			}
		}

		btn.setText(txt);
		btn.setVisibility(filter == null ? View.GONE : View.VISIBLE);
		final String f = filter;
		btn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				hideDashboard(false);
				final Intent intent = new Intent(mapActivity, mapActivity.getMyApplication().getAppCustomization()
						.getDownloadIndexActivity());
				if (f != null && !f.equals("basemap")) {
					intent.putExtra(DownloadActivity.FILTER_KEY, f);
				}
				intent.putExtra(DownloadActivity.TAB_TO_OPEN, DownloadActivity.DOWNLOAD_TAB);
				mapActivity.startActivity(intent);
			}
		});
		scheduleDownloadButtonCheck();
	}

	private void scheduleDownloadButtonCheck() {
		mapActivity.getMyApplication().runInUIThread(new Runnable() {

			@Override
			public void run() {
				if (isVisible()) {
					updateDownloadBtn();
				}
			}
		}, 4000);
	}

	void navigationAction() {
		RoutingHelper routingHelper = mapActivity.getRoutingHelper();
		if (!routingHelper.isFollowingMode() && !routingHelper.isRoutePlanningMode()) {
			mapActivity.getMapActions().enterRoutePlanningMode(null, null);
		} else {
			mapActivity.getRoutingHelper().setRoutePlanningMode(true);
			mapActivity.getMapViewTrackingUtilities().switchToRoutePlanningMode();
			mapActivity.refreshMap();
		}
		boolean animate = !getMyApplication().getSettings().DO_NOT_USE_ANIMATIONS.get();
		hideDashboard(animate);
	}

	// To bounce animate view
	private void open(boolean animation, int[] animationCoordinates) {
		if (animation) {
			this.animationCoordinates = animationCoordinates;
			animateDashboard(true);
		} else {
			dashboardView.findViewById(R.id.animateContent).setVisibility(View.VISIBLE);
			dashboardView.findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
		}
	}

	private void animateDashboard(final boolean show) {
		final View content = dashboardView.findViewById(R.id.animateContent);
		final View toolbar = dashboardView.findViewById(R.id.toolbar);
		AnimatorSet set = new AnimatorSet();
		List<Animator> animators = new ArrayList<>();
		if (animationCoordinates != null) {
			int screenHeight = mapActivity.getResources().getDisplayMetrics().heightPixels;
			int screenWidth = mapActivity.getResources().getDisplayMetrics().widthPixels;
			float initialValueX = show ? animationCoordinates[0] - screenWidth / 2 : 0;
			float finalValueX = show ? 0 : animationCoordinates[0] - screenWidth / 2;
			float initialValueY = show ? animationCoordinates[1] - screenHeight / 2 : 0;
			float finalValueY = show ? 0 : animationCoordinates[1] - screenHeight / 2;
			animators.add(ObjectAnimator.ofFloat(content, View.TRANSLATION_X, initialValueX, finalValueX));
			animators.add(ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, initialValueY, finalValueY));
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && ViewCompat.isAttachedToWindow(content)) {
			int centerX = content.getMeasuredWidth() / 2;
			int centerY = content.getMeasuredHeight() / 2;
			float initialRadius = show ? 0 : (float) Math.sqrt(Math.pow(content.getWidth() / 2, 2) + Math.pow(content.getHeight() / 2, 2));
			float finalRadius = show ? (float) Math.sqrt(Math.pow(content.getWidth() / 2, 2) + Math.pow(content.getHeight() / 2, 2)) : 0;
			Animator circleAnimator = ViewAnimationUtils.createCircularReveal(content, centerX, centerY, initialRadius, finalRadius);
			animators.add(circleAnimator);
		}
		final float initialValueScale = show ? 0f : 1f;
		float finalValueScale = show ? 1f : 0f;
		animators.add(ObjectAnimator.ofFloat(content, View.SCALE_X, initialValueScale, finalValueScale));
		animators.add(ObjectAnimator.ofFloat(content, View.SCALE_Y, initialValueScale, finalValueScale));
		float initialToolbarTransY = show ? -toolbar.getHeight() : 0;
		float finalToolbarTransY = show ? 0 : -toolbar.getHeight();
		animators.add(ObjectAnimator.ofFloat(toolbar, View.TRANSLATION_Y, initialToolbarTransY, finalToolbarTransY));
		for (Animator animator : animators) {
			animator.setDuration(300);
		}
		set.playTogether(animators);
		set.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationStart(Animator animation) {
				super.onAnimationStart(animation);
				listView.setVerticalScrollBarEnabled(false);
				if (show) {
					content.setVisibility(View.VISIBLE);
					toolbar.setVisibility(View.VISIBLE);
				}
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				super.onAnimationEnd(animation);
				listView.setVerticalScrollBarEnabled(true);
				if (!show) {
					dashboardView.setVisibility(View.GONE);
					content.setVisibility(View.GONE);
					toolbar.setVisibility(View.GONE);
					content.setTranslationX(0);
					content.setTranslationY(0);
					toolbar.setTranslationY(0);
					content.setScaleX(initialValueScale);
					content.setScaleY(initialValueScale);
				}
			}
		});
		set.start();
	}

	private void hide(boolean animation) {
		if (compassButton != null) {
			mapActivity.getMapLayers().getMapControlsLayer().restoreCompassButton(nightMode);
			compassButton = null;
		}
		if (!animation) {
			dashboardView.setVisibility(View.GONE);
			dashboardView.findViewById(R.id.animateContent).setVisibility(View.GONE);
			dashboardView.findViewById(R.id.toolbar).setVisibility(View.GONE);
		} else {
			animateDashboard(false);
		}
		animationCoordinates = null;
	}

	private void addOrUpdateDashboardFragments() {
		OsmandSettings settings = getMyApplication().getSettings();
		TransactionBuilder builder =
				new TransactionBuilder(mapActivity.getSupportFragmentManager(), settings, mapActivity);
		builder.addFragmentsData(fragmentsData)
				.addFragmentsData(OsmandPlugin.getPluginsCardsList())
				.getFragmentTransaction().commitAllowingStateLoss();
	}

	private void removeFragment(String tag) {
		FragmentManager manager = mapActivity.getSupportFragmentManager();
		Fragment fragment = manager.findFragmentByTag(tag);
		if (fragment != null) {
			OsmandSettings settings = getMyApplication().getSettings();
			TransactionBuilder builder = new TransactionBuilder(manager, settings, mapActivity);
			builder.getFragmentTransaction()
					.remove(fragment)
					.commit();
		}
	}

	public boolean isVisible() {
		return visible;
	}

	void onDetach(DashBaseFragment dashBaseFragment) {
		Iterator<WeakReference<DashBaseFragment>> it = fragList.iterator();
		while (it.hasNext()) {
			WeakReference<DashBaseFragment> wr = it.next();
			if (wr.get() == dashBaseFragment) {
				it.remove();
			}
		}
	}

	public void onMapSettingsUpdated() {
		if (DashboardType.CONFIGURE_MAP.equals(visibleType)) {
			updateMenuItems();
		}
	}

	public void updateMenuItems() {
		if (listAdapter != null) {
			for (int i = 0; i < listAdapter.getCount(); i++) {
				Object o = listAdapter.getItem(i);
				if (o instanceof ContextMenuItem) {
					((ContextMenuItem) o).update();
				}
			}
			listAdapter.notifyDataSetChanged();
		}
	}

	public void updateLocation(final boolean centerChanged, final boolean locationChanged,
							   final boolean compassChanged) {
		if (inLocationUpdate) {
			return;
		}
		inLocationUpdate = true;
		mapActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				inLocationUpdate = false;
				for (WeakReference<DashBaseFragment> df : fragList) {
					if (df.get() instanceof DashLocationFragment) {
						((DashLocationFragment) df.get()).updateLocation(centerChanged, locationChanged, compassChanged);
					}
				}
			}
		});

	}

	public void updateMyLocation(net.osmand.Location location) {
		updateLocation(false, true, false);
	}

	public void updateCompassValue(double heading) {
		this.heading = (float) heading;
		updateLocation(false, false, true);
	}

	public void onAttach(DashBaseFragment dashBaseFragment) {
		fragList.add(new WeakReference<>(dashBaseFragment));
	}

	public void requestLayout() {
		dashboardView.requestLayout();
	}

	public boolean onBackPressed() {
		if (isVisible()) {
			backPressed();
			return true;
		}
		return false;
	}

	private void backPressed() {
		if (previousVisibleType != visibleType && previousVisibleType != null) {
			if (visibleType == DashboardType.MAPILLARY) {
				hideKeyboard();
			}
			visibleType = null;
			setDashboardVisibility(true, previousVisibleType);
		} else {
			hideDashboard();
			mapActivity.backToConfigureProfileFragment();
		}
	}

	private void hideKeyboard() {
		View currentFocus = mapActivity.getCurrentFocus();
		if (currentFocus != null) {
			InputMethodManager imm = (InputMethodManager) mapActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
			}
		}
	}

	@Override
	public void onScrollChanged(int scrollY, boolean firstScroll, boolean dragging) {
		// Translate list background
		if (portrait) {
			if (listBackgroundView != null) {
				setTranslationY(listBackgroundView, Math.max(0, -scrollY + mFlexibleSpaceImageHeight));
			}
		}
		if (portrait && toolbar.getVisibility() == View.VISIBLE) {
			setTranslationY(toolbar, Math.min(0, -scrollY + mFlexibleSpaceImageHeight - mFlexibleBlurSpaceHeight));
		}
		updateColorOfToolbar(scrollY);
		updateTopButton(scrollY);
		updateMapShadow(scrollY);
	}

	private boolean isActionButtonVisible() {
		return visibleType == DashboardType.DASHBOARD
				|| visibleType == DashboardType.LIST_MENU
				|| visibleType == DashboardType.ROUTE_PREFERENCES
				|| visibleType == DashboardType.CONFIGURE_SCREEN;
	}

	private boolean isBackButtonVisible() {
		return !(visibleType == DashboardType.DASHBOARD || visibleType == DashboardType.LIST_MENU);
	}

	private void updateMapShadow(int scrollY) {
		View shadowOnMap = dashboardView.findViewById(R.id.shadow_on_map);
		if (shadowOnMap != null) {
			int minTop = dashboardView.findViewById(R.id.map_part_dashboard).getHeight() - toolbar.getHeight();
			if (scrollY >= minTop) {
				shadowOnMap.setVisibility(View.GONE);
			} else {
				shadowOnMap.setVisibility(View.VISIBLE);
			}
		}
	}

	private void updateTopButton(int scrollY) {
		if (actionButton != null && portrait && isActionButtonVisible()) {
			double scale = mapActivity.getResources().getDisplayMetrics().density;
			int originalPosition = mFlexibleSpaceImageHeight - (int) (80 * scale);
			int minTop = mFlexibleBlurSpaceHeight + (int) (5 * scale);
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) actionButton.getLayoutParams();
			if (minTop > originalPosition - scrollY) {
				hideActionButton();
			} else {
				actionButton.setVisibility(View.VISIBLE);
				lp.topMargin = originalPosition - scrollY;
				((FrameLayout) actionButton.getParent()).updateViewLayout(actionButton, lp);
			}
		} else if (compassButton != null) {
			double scale = mapActivity.getResources().getDisplayMetrics().density;
			int originalPosition = mFlexibleSpaceImageHeight - (int) (64 * scale);
			int minTop = mFlexibleBlurSpaceHeight + (int) (5 * scale);
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) compassButton.getLayoutParams();
			if (minTop > originalPosition - scrollY) {
				hideActionButton();
			} else {
				compassButton.setVisibility(View.VISIBLE);
				lp.topMargin = originalPosition - scrollY;
				((FrameLayout) compassButton.getParent()).updateViewLayout(compassButton, lp);
			}
		}
	}

	private void updateColorOfToolbar(int scrollY) {
		if (portrait) {
			float sh = mFlexibleSpaceImageHeight - mFlexibleBlurSpaceHeight;
			float t = sh == 0 ? 1 : (1 - Math.max(0, -scrollY + sh) / sh);
			t = Math.max(0, t);

			int alpha = (int) (t * 255);
			// in order to have proper fast scroll down
			int malpha = t == 1 ? 0 : alpha;
			setAlpha(paddingView, malpha, baseColor);
			setAlpha(dashboardView.findViewById(R.id.map_part_dashboard), malpha, baseColor);
			gradientToolbar.setAlpha((int) ((1 - t) * 255));
			setAlpha(dashboardView, (int) (t * 128), 0);
			View toolbar = dashboardView.findViewById(R.id.toolbar);
			updateMapShadowColor(malpha);
			if (t < 1) {
				//noinspection deprecation
				toolbar.setBackgroundDrawable(gradientToolbar);
			} else {
				toolbar.setBackgroundColor(0xff000000 | baseColor);
			}
		}
	}

	private void updateMapShadowColor(int alpha) {
		View shadowOnMap = dashboardView.findViewById(R.id.shadow_on_map);
		if (shadowOnMap != null) {
            setAlpha(shadowOnMap, alpha, baseColor);
        }
	}

	private void updateListAdapter(ArrayAdapter<?> listAdapter, OnItemClickListener listener) {
		this.listAdapter = listAdapter;
		listAdapterOnClickListener = listener;
		if (listView != null) {
			listView.setAdapter(listAdapter);
			if (listAdapterOnClickListener != null) {
				listView.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						listAdapterOnClickListener.onItemClick(parent, view, position - listView.getHeaderViewsCount(), id);
					}
				});
			} else {
				listView.setOnItemClickListener(null);
			}
		}
	}

	private void setTranslationY(View v, int y) {
		v.setTranslationY(y);
	}

	@SuppressLint("NewApi")
	private void setAlpha(View v, int alpha, int clr) {
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//			v.setAlpha(alpha/255.f);
//		} else {
		int colr = (alpha << 24) | clr;
		v.setBackgroundColor(colr);
//		}
	}

	@Override
	public void onDownMotionEvent() {
	}


	@Override
	public void onUpOrCancelMotionEvent(ScrollState scrollState) {
		lastUpOrCancelMotionEventTime = System.currentTimeMillis();
	}


	<T extends DashBaseFragment> T getFragmentByClass(Class<T> class1) {
		for (WeakReference<DashBaseFragment> f : fragList) {
			DashBaseFragment b = f.get();
			if (b != null && !b.isDetached() && class1.isInstance(b)) {
				//noinspection unchecked
				return (T) b;
			}
		}
		return null;
	}

	void blacklistFragmentByTag(String tag) {
		hideFragmentByTag(tag);
		getMyApplication().getSettings().registerBooleanPreference(SHOULD_SHOW + tag, true)
				.makeGlobal().set(false);
	}

	void hideFragmentByTag(String tag) {
		FragmentManager manager = mapActivity.getSupportFragmentManager();
		FragmentTransaction transaction = manager.beginTransaction();
		Fragment frag = manager.findFragmentByTag(tag);
		transaction.hide(frag).commit();
	}

	void unblacklistFragmentClass(String tag) {
		unhideFragmentByTag(tag);
		getMyApplication().getSettings().registerBooleanPreference(SHOULD_SHOW + tag, true)
				.makeGlobal().set(true);
	}

	void unhideFragmentByTag(String tag) {
		FragmentManager manager = mapActivity.getSupportFragmentManager();
		FragmentTransaction transaction = manager.beginTransaction();
		Fragment frag = manager.findFragmentByTag(tag);
		transaction.show(frag).commit();
	}

	public void clearDeletedPoints() {
		deletedPoints.clear();
	}

	View getParentView() {
		return dashboardView;
	}

	public static <T> void handleNumberOfRows(List<T> list, OsmandSettings settings,
											  String rowNumberTag) {
		int numberOfRows = settings.registerIntPreference(rowNumberTag, 3)
				.makeGlobal().get();
		if (list.size() > numberOfRows) {
			while (list.size() != numberOfRows) {
				list.remove(numberOfRows);
			}
		}
	}

	public static class DefaultShouldShow extends DashFragmentData.ShouldShowFunction {

		public boolean shouldShow(OsmandSettings settings, MapActivity activity, String tag) {
			return settings.registerBooleanPreference(SHOULD_SHOW + tag, true).makeGlobal().get();
		}
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
	}

	@Override
	public void routeWasCancelled() {
	}

	@Override
	public void routeWasFinished() {
	}
}
