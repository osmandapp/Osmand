package net.osmand.plus.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentManager.BackStackEntry;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.SecondSplashScreenFragment;
import net.osmand.StateChangedListener;
import net.osmand.ValueHolder;
import net.osmand.access.MapAccessibilityActions;
import net.osmand.aidl.AidlMapPointWrapper;
import net.osmand.aidl.OsmandAidlApi.AMapPointUpdateListener;
import net.osmand.core.android.AtlasMapRendererView;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadPoint;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.map.MapTileDownloader.DownloadRequest;
import net.osmand.map.MapTileDownloader.IMapDownloaderCallback;
import net.osmand.plus.AppInitializer;
import net.osmand.plus.AppInitializer.AppInitializeListener;
import net.osmand.plus.AppInitializer.InitEvents;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OnDismissDialogFragmentListener;
import net.osmand.plus.OsmAndConstants;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmAndLocationSimulation;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.TargetPointsHelper.TargetPoint;
import net.osmand.plus.Version;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.base.FailSafeFuntions;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.chooseplan.OsmLiveGoneDialog;
import net.osmand.plus.dashboard.DashBaseFragment;
import net.osmand.plus.dashboard.DashboardOnMap;
import net.osmand.plus.dialogs.CrashBottomSheetDialogFragment;
import net.osmand.plus.dialogs.ImportGpxBottomSheetDialogFragment;
import net.osmand.plus.dialogs.SendAnalyticsBottomSheetDialogFragment;
import net.osmand.plus.dialogs.WhatsNewDialogFragment;
import net.osmand.plus.dialogs.XMasDialogFragment;
import net.osmand.plus.download.DownloadActivity;
import net.osmand.plus.download.DownloadIndexesThread.DownloadEvents;
import net.osmand.plus.download.ui.DataStoragePlaceDialogFragment;
import net.osmand.plus.firstusage.FirstUsageWelcomeFragment;
import net.osmand.plus.firstusage.FirstUsageWizardFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.DayNightHelper;
import net.osmand.plus.helpers.DiscountHelper;
import net.osmand.plus.helpers.IntentHelper;
import net.osmand.plus.helpers.LockHelper;
import net.osmand.plus.helpers.LockHelper.LockUIAdapter;
import net.osmand.plus.helpers.RateUsHelper;
import net.osmand.plus.helpers.ScrollHelper;
import net.osmand.plus.helpers.ScrollHelper.OnScrollEventListener;
import net.osmand.plus.importfiles.ImportHelper;
import net.osmand.plus.mapcontextmenu.AdditionalActionsBottomSheetDialogFragment;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapcontextmenu.builders.cards.dialogs.ContextMenuCardDialogFragment;
import net.osmand.plus.mapcontextmenu.other.DestinationReachedMenu;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu;
import net.osmand.plus.mapmarkers.MapMarker;
import net.osmand.plus.mapmarkers.MapMarkersHelper.MapMarkerChangedListener;
import net.osmand.plus.mapmarkers.PlanRouteFragment;
import net.osmand.plus.measurementtool.GpxApproximationFragment;
import net.osmand.plus.measurementtool.GpxData;
import net.osmand.plus.measurementtool.LoginBottomSheetFragment;
import net.osmand.plus.measurementtool.MeasurementEditingContext;
import net.osmand.plus.measurementtool.MeasurementToolFragment;
import net.osmand.plus.measurementtool.SnapTrackWarningFragment;
import net.osmand.plus.monitoring.TripRecordingStartingBottomSheet;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.resources.ResourceManager;
import net.osmand.plus.routepreparationmenu.ChooseRouteFragment;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenuFragment;
import net.osmand.plus.routing.IRouteInformationListener;
import net.osmand.plus.routing.RouteCalculationProgressCallback;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.TransportRoutingHelper.TransportRouteCalculationProgressCallback;
import net.osmand.plus.search.QuickSearchDialogFragment;
import net.osmand.plus.search.QuickSearchDialogFragment.QuickSearchTab;
import net.osmand.plus.search.QuickSearchDialogFragment.QuickSearchType;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.settings.backend.OsmAndAppCustomization.OsmAndAppCustomizationListener;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.datastorage.DataStorageFragment;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.fragments.BaseSettingsFragment.SettingsScreenType;
import net.osmand.plus.settings.fragments.ConfigureProfileFragment;
import net.osmand.plus.settings.fragments.RouteLineAppearanceFragment;
import net.osmand.plus.track.TrackAppearanceFragment;
import net.osmand.plus.track.TrackMenuFragment;
import net.osmand.plus.views.AddGpxPointBottomSheetHelper.NewGpxPoint;
import net.osmand.plus.views.AnimateDraggingMapThread;
import net.osmand.plus.views.OsmAndMapLayersView;
import net.osmand.plus.views.OsmAndMapSurfaceView;
import net.osmand.plus.views.OsmandMapLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.OsmandMapTileView.OnDrawMapListener;
import net.osmand.plus.views.corenative.NativeCoreContext;
import net.osmand.plus.views.layers.MapControlsLayer;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarController;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarControllerType;
import net.osmand.plus.views.mapwidgets.WidgetsVisibilityHelper;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.GeneralRouter;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_SETTINGS_ID;

public class MapActivity extends OsmandActionBarActivity implements DownloadEvents,
		OnRequestPermissionsResultCallback, IRouteInformationListener, AMapPointUpdateListener,
		MapMarkerChangedListener, OnDismissDialogFragmentListener, OnDrawMapListener,
		OsmAndAppCustomizationListener, LockUIAdapter, OnPreferenceStartFragmentCallback,
		OnScrollEventListener {

	public static final String INTENT_KEY_PARENT_MAP_ACTIVITY = "intent_parent_map_activity_key";
	public static final String INTENT_PARAMS = "intent_prarams";

	private static final int SHOW_POSITION_MSG_ID = OsmAndConstants.UI_HANDLER_MAP_VIEW + 1;
	private static final int ZOOM_LABEL_DISPLAY = 16;
	private static final int MIN_ZOOM_LABEL_DISPLAY = 12;
	private static final int SECOND_SPLASH_TIME_OUT = 8000;

	private static final int SMALL_SCROLLING_UNIT = 1;
	private static final int BIG_SCROLLING_UNIT = 200;

	private static final Log LOG = PlatformUtil.getLog(MapActivity.class);

	private MapViewTrackingUtilities mapViewTrackingUtilities;
	private static MapContextMenu mapContextMenu = new MapContextMenu();
	private static MapRouteInfoMenu mapRouteInfoMenu = new MapRouteInfoMenu();
	private static TrackDetailsMenu trackDetailsMenu = new TrackDetailsMenu();
	private static Intent prevActivityIntent = null;

	private List<ActivityResultListener> activityResultListeners = new ArrayList<>();

	private BroadcastReceiver screenOffReceiver;

	/**
	 * Called when the activity is first created.
	 */
	private OsmandMapTileView mapView;
	private AtlasMapRendererView atlasMapRendererView;

	private MapActivityActions mapActions;
	private MapActivityLayers mapLayers;
	private WidgetsVisibilityHelper mapWidgetsVisibilityHelper;

	// App variables
	private OsmandApplication app;
	private OsmandSettings settings;

	private LockHelper lockHelper;
	private ImportHelper importHelper;
	private IntentHelper intentHelper;
	private ScrollHelper mapScrollHelper;

	private boolean landscapeLayout;

	private List<DialogProvider> dialogProviders = new ArrayList<>(2);
	private StateChangedListener<ApplicationMode> applicationModeListener;
	private boolean intentLocation = false;

	private DashboardOnMap dashboardOnMap = new DashboardOnMap(this);
	private AppInitializeListener initListener;
	private IMapDownloaderCallback downloaderCallback;
	private DrawerLayout drawerLayout;
	private boolean drawerDisabled;

	private static boolean permissionDone;
	private boolean permissionAsked;
	private boolean permissionGranted;

	private boolean mIsDestroyed = false;
	private boolean pendingPause = false;
	private Timer splashScreenTimer;
	private boolean activityRestartNeeded = false;
	private boolean stopped = true;

	private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

	private StateChangedListener<Integer> mapScreenOrientationSettingListener = new StateChangedListener<Integer>() {
		@Override
		public void stateChanged(Integer change) {
			applyScreenOrientation();
		}
	};

	private StateChangedListener<Boolean> useSystemScreenTimeoutListener = new StateChangedListener<Boolean>() {
		@Override
		public void stateChanged(Boolean change) {
			changeKeyguardFlags();
		}
	};
	private MapActivityKeyListener mapActivityKeyListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setRequestedOrientation(AndroidUiHelper.getScreenOrientation(this));
		long tm = System.currentTimeMillis();
		app = getMyApplication();
		settings = app.getSettings();
		lockHelper = app.getLockHelper();
		mapScrollHelper = new ScrollHelper(app);
		app.applyTheme(this);
		supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

		boolean portraitMode = AndroidUiHelper.isOrientationPortrait(this);
		boolean largeDevice = AndroidUiHelper.isXLargeDevice(this);
		landscapeLayout = !portraitMode && !largeDevice;
		mapViewTrackingUtilities = app.getMapViewTrackingUtilities();
		mapContextMenu.setMapActivity(this);
		mapRouteInfoMenu.setMapActivity(this);
		trackDetailsMenu.setMapActivity(this);

		super.onCreate(savedInstanceState);

		// Full screen is not used here
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.main);

		if (Build.VERSION.SDK_INT >= 21) {
			enterToFullScreen();
			// Navigation Drawer:
			AndroidUtils.addStatusBarPadding21v(this, findViewById(R.id.menuItems));
		}

		int statusBarHeight = AndroidUtils.getStatusBarHeight(this);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int w = dm.widthPixels;
		int h = dm.heightPixels - statusBarHeight;

		mapView = new OsmandMapTileView(this, w, h);
		if (app.getAppInitializer().checkAppVersionChanged() && WhatsNewDialogFragment.SHOW) {
			SecondSplashScreenFragment.SHOW = false;
			WhatsNewDialogFragment.SHOW = false;
			new WhatsNewDialogFragment().show(getSupportFragmentManager(), null);
		}
		mapActions = new MapActivityActions(this);
		mapLayers = new MapActivityLayers(this);
		mapWidgetsVisibilityHelper = new WidgetsVisibilityHelper(this);
		dashboardOnMap.createDashboardView();
		checkAppInitialization();

		intentHelper = new IntentHelper(this, getMyApplication());
		intentHelper.parseLaunchIntents();

		mapView.setTrackBallDelegate(new OsmandMapTileView.OnTrackBallListener() {
			@Override
			public boolean onTrackBallEvent(MotionEvent e) {
				showAndHideMapPosition();
				return MapActivity.this.onTrackballEvent(e);
			}
		});
		mapView.setAccessibilityActions(new MapAccessibilityActions(this));
		mapViewTrackingUtilities.setMapView(mapView);

		// to not let it gc
		downloaderCallback = new IMapDownloaderCallback() {
			@Override
			public void tileDownloaded(DownloadRequest request) {
				if (request != null && !request.error && request.fileToSave != null) {
					ResourceManager mgr = app.getResourceManager();
					mgr.tileDownloaded(request);
				}
				if (request == null || !request.error) {
					mapView.tileDownloaded(request);
				}
			}
		};
		app.getResourceManager().getMapTileDownloader().addDownloaderCallback(downloaderCallback);
		mapLayers.createLayers(mapView);
		createProgressBarForRouting();
		updateStatusBarColor();

		if ((app.getRoutingHelper().isRouteCalculated() || app.getRoutingHelper().isRouteBeingCalculated())
				&& !app.getRoutingHelper().isRoutePlanningMode()
				&& !settings.FOLLOW_THE_ROUTE.get()
				&& app.getTargetPointsHelper().getAllPoints().size() > 0) {
			app.getRoutingHelper().clearCurrentRoute(null, new ArrayList<LatLon>());
			app.getTargetPointsHelper().removeAllWayPoints(false, false);
		}

		if (!settings.isLastKnownMapLocation()) {
			// show first time when application ran
			final WeakReference<MapActivity> activityRef = new WeakReference<>(this);
			net.osmand.Location location = app.getLocationProvider().getFirstTimeRunDefaultLocation(new OsmAndLocationListener() {
				@Override
				public void updateLocation(Location location) {
					MapActivity a = activityRef.get();
					if (AndroidUtils.isActivityNotDestroyed(a) && app.getLocationProvider().getLastKnownLocation() == null) {
						setMapInitialLatLon(a.mapView, location);
					}
				}
			});
			mapViewTrackingUtilities.setMapLinkedToLocation(true);
			if (location != null) {
				setMapInitialLatLon(mapView, location);
			}
		}
		addDialogProvider(mapActions);
		OsmandPlugin.onMapActivityCreate(this);
		importHelper = new ImportHelper(this, getMyApplication(), getMapView());
		if (System.currentTimeMillis() - tm > 50) {
			System.err.println("OnCreate for MapActivity took " + (System.currentTimeMillis() - tm) + " ms");
		}
		mapView.refreshMap(true);

		mapActions.updateDrawerMenu();
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		screenOffReceiver = new ScreenOffReceiver();
		registerReceiver(screenOffReceiver, filter);

		app.getAidlApi().onCreateMapActivity(this);

		lockHelper.setLockUIAdapter(this);
		mapActivityKeyListener = new MapActivityKeyListener(this);
		mIsDestroyed = false;
	}

	private void setMapInitialLatLon(@NonNull OsmandMapTileView mapView, @Nullable Location location) {
		if (location != null) {
			mapView.setLatLon(location.getLatitude(), location.getLongitude());
			mapView.setIntZoom(14);
		}
	}

	public void exitFromFullScreen(View view) {
		AndroidUtils.exitFromFullScreen(this, view);
	}

	public void enterToFullScreen() {
		AndroidUtils.enterToFullScreen(this, getLayout());
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (removeFragment(PlanRouteFragment.TAG)) {
			app.getMapMarkersHelper().getPlanRouteContext().setFragmentVisible(true);
		}
		removeFragment(ImportGpxBottomSheetDialogFragment.TAG);
		removeFragment(AdditionalActionsBottomSheetDialogFragment.TAG);
		super.onSaveInstanceState(outState);
	}

	private boolean removeFragment(String tag) {
		FragmentManager fm = getSupportFragmentManager();
		Fragment fragment = fm.findFragmentByTag(tag);
		if (fragment != null) {
			fm.beginTransaction()
					.remove(fragment)
					.commitNowAllowingStateLoss();
			return true;
		}
		return false;
	}

	private void checkAppInitialization() {
		if (app.isApplicationInitializing()) {
			findViewById(R.id.init_progress).setVisibility(View.VISIBLE);
			initListener = new AppInitializeListener() {
				boolean openGlSetup = false;

				@Override
				public void onStart(AppInitializer init) {

				}

				@Override
				public void onProgress(AppInitializer init, InitEvents event) {
					String tn = init.getCurrentInitTaskName();
					if (tn != null) {
						((TextView) findViewById(R.id.ProgressMessage)).setText(tn);
					}
					boolean openGlInitialized = event == InitEvents.NATIVE_OPEN_GLINITIALIZED && NativeCoreContext.isInit();
					if ((openGlInitialized || event == InitEvents.NATIVE_INITIALIZED) && !openGlSetup) {
						setupOpenGLView(false);
						openGlSetup = true;
					}
					if (event == InitEvents.MAPS_INITIALIZED) {
						// TODO investigate if this false cause any issues!
						mapView.refreshMap(false);
						if (dashboardOnMap != null) {
							dashboardOnMap.updateLocation(true, true, false);
						}
						app.getTargetPointsHelper().lookupAddessAll();
						app.getMapMarkersHelper().lookupAddressAll();
					}
					if (event == InitEvents.FAVORITES_INITIALIZED) {
						refreshMap();
					}
					if (event == InitEvents.ROUTING_CONFIG_INITIALIZED) {
						checkRestoreRoutingMode();
					}
				}

				@Override
				public void onFinish(AppInitializer init) {
					if (!openGlSetup) {
						setupOpenGLView(false);
					}
					mapView.refreshMap(false);
					if (dashboardOnMap != null) {
						dashboardOnMap.updateLocation(true, true, false);
					}
					findViewById(R.id.init_progress).setVisibility(View.GONE);
					findViewById(R.id.drawer_layout).invalidate();
				}
			};
			getMyApplication().checkApplicationIsBeingInitialized(this, initListener);
		} else {
			setupOpenGLView(true);
			checkRestoreRoutingMode();
		}
	}

	private void checkRestoreRoutingMode() {
		// This situation could be when navigation suddenly crashed and after restarting
		// it tries to continue the last route
		if (settings.FOLLOW_THE_ROUTE.get()
				&& !app.getRoutingHelper().isRouteCalculated()
				&& !app.getRoutingHelper().isRouteBeingCalculated()) {
			FailSafeFuntions.restoreRoutingMode(MapActivity.this);
		}
	}

	private void setupOpenGLView(boolean init) {
		if (settings.USE_OPENGL_RENDER.get() && NativeCoreContext.isInit()) {
			ViewStub stub = (ViewStub) findViewById(R.id.atlasMapRendererViewStub);
			atlasMapRendererView = (AtlasMapRendererView) stub.inflate();
			OsmAndMapLayersView ml = (OsmAndMapLayersView) findViewById(R.id.MapLayersView);
			ml.setVisibility(View.VISIBLE);
			atlasMapRendererView.setAzimuth(0);
			atlasMapRendererView.setElevationAngle(app.getSettings().getLastKnownMapElevation());
			NativeCoreContext.getMapRendererContext().setMapRendererView(atlasMapRendererView);
			ml.setMapView(mapView);
			mapViewTrackingUtilities.setMapView(mapView);
			mapView.setMapRender(atlasMapRendererView);
			OsmAndMapSurfaceView surf = (OsmAndMapSurfaceView) findViewById(R.id.MapView);
			surf.setVisibility(View.GONE);
		} else {
			OsmAndMapSurfaceView surf = (OsmAndMapSurfaceView) findViewById(R.id.MapView);
			surf.setVisibility(View.VISIBLE);
			surf.setMapView(mapView);
		}
	}

	public void setMapElevation(float angle) {
		if (atlasMapRendererView != null) {
			atlasMapRendererView.setElevationAngle(angle);
		}
	}

	private void createProgressBarForRouting() {
		final ProgressBar pb = (ProgressBar) findViewById(R.id.map_horizontal_progress);

		final RouteCalculationProgressCallback progressCallback = new RouteCalculationProgressCallback() {

			@Override
			public void start() {
				setupRouteCalculationProgressBar(pb);
				mapRouteInfoMenu.routeCalculationStarted();
				RoutingHelper routingHelper = getRoutingHelper();
				if (routingHelper.isPublicTransportMode() || !routingHelper.isOsmandRouting()) {
					dashboardOnMap.updateRouteCalculationProgress(0);
				}
			}

			@Override
			public void updateProgress(int progress) {
				mapRouteInfoMenu.updateRouteCalculationProgress(progress);
				dashboardOnMap.updateRouteCalculationProgress(progress);
				if (findViewById(R.id.MapHudButtonsOverlay).getVisibility() == View.VISIBLE) {
					if (mapRouteInfoMenu.isVisible() || dashboardOnMap.isVisible()) {
						pb.setVisibility(View.GONE);
						return;
					}
					if (pb.getVisibility() == View.GONE) {
						pb.setVisibility(View.VISIBLE);
					}
					pb.setProgress(progress);
					pb.invalidate();
					pb.requestLayout();
				}
			}

			@Override
			public void requestPrivateAccessRouting() {
				if (!settings.FORCE_PRIVATE_ACCESS_ROUTING_ASKED.getModeValue(getRoutingHelper().getAppMode())) {
					final CommonPreference<Boolean> allowPrivate
							= settings.getCustomRoutingBooleanProperty(GeneralRouter.ALLOW_PRIVATE, false);
					final List<ApplicationMode> modes = ApplicationMode.values(app);
					for (ApplicationMode mode : modes) {
						if (!allowPrivate.getModeValue(mode)) {
							settings.FORCE_PRIVATE_ACCESS_ROUTING_ASKED.setModeValue(mode, true);
						}
					}
					if (!allowPrivate.getModeValue(getRoutingHelper().getAppMode())) {
						AlertDialog.Builder dlg = new AlertDialog.Builder(MapActivity.this);
						dlg.setMessage(R.string.private_access_routing_req);
						dlg.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								for (ApplicationMode mode : modes) {
									if (!allowPrivate.getModeValue(mode)) {
										allowPrivate.setModeValue(mode, true);
									}
								}
								getRoutingHelper().onSettingsChanged(null, true);
							}
						});
						dlg.setNegativeButton(R.string.shared_string_no, null);
						dlg.show();
					}
				}
			}

			@Override
			public void finish() {
				mapRouteInfoMenu.routeCalculationFinished();
				dashboardOnMap.routeCalculationFinished();
				mapLayers.getMapInfoLayer().routeCalculationFinished();
				pb.setVisibility(View.GONE);
			}
		};

		app.getRoutingHelper().setProgressBar(progressCallback);

		app.getTransportRoutingHelper().setProgressBar(new TransportRouteCalculationProgressCallback() {
			@Override
			public void start() {
				progressCallback.start();
			}

			@Override
			public void updateProgress(int progress) {
				progressCallback.updateProgress(progress);
			}

			@Override
			public void finish() {
				progressCallback.finish();
			}
		});
	}

	public void setupRouteCalculationProgressBar(@NonNull ProgressBar pb) {
		DayNightHelper dayNightHelper = getMyApplication().getDaynightHelper();

		boolean nightMode = dayNightHelper.isNightModeForMapControls();
		boolean useRouteLineColor = nightMode == dayNightHelper.isNightMode();

		int bgColorId = nightMode ? R.color.map_progress_bar_bg_dark : R.color.map_progress_bar_bg_light;
		int bgColor = ContextCompat.getColor(this, bgColorId);

		int progressColor = useRouteLineColor
				? mapLayers.getRouteLayer().getRouteLineColor(nightMode)
				: ContextCompat.getColor(this, R.color.wikivoyage_active_light);

		RoutingHelper routingHelper = getRoutingHelper();
		pb.setProgressDrawable(AndroidUtils.createProgressDrawable(bgColor, progressColor));
		pb.setIndeterminate(routingHelper.isPublicTransportMode() || !routingHelper.isOsmandRouting());
		pb.getIndeterminateDrawable().setColorFilter(progressColor, android.graphics.PorterDuff.Mode.SRC_IN);
	}

	public ImportHelper getImportHelper() {
		return importHelper;
	}

	@SuppressWarnings("rawtypes")
	public Object getLastNonConfigurationInstanceByKey(String key) {
		Object k = super.getLastNonConfigurationInstance();
		if (k instanceof Map) {

			return ((Map) k).get(key);
		}
		return null;
	}

	@Override
	public Object onRetainCustomNonConfigurationInstance() {
		LinkedHashMap<String, Object> l = new LinkedHashMap<>();
		for (OsmandMapLayer ml : mapView.getLayers()) {
			ml.onRetainNonConfigurationInstance(l);
		}
		return l;
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		if (!intentHelper.parseLaunchIntents()) {
			intentHelper.parseContentIntent();
		}
	}

	@Override
	public void startActivity(Intent intent) {
		clearPrevActivityIntent();
		super.startActivity(intent);
	}

	@Override
	public void onBackPressed() {
		if (dashboardOnMap.onBackPressed()) {
			return;
		}
		if (drawerLayout.isDrawerOpen(Gravity.START)) {
			closeDrawer();
			return;
		}
		if (getMapLayers().getContextMenuLayer().isInAddGpxPointMode()) {
			quitAddGpxPointMode();
		}
		if (getSupportFragmentManager().getBackStackEntryCount() == 0 && launchPrevActivityIntent()) {
			return;
		}
		super.onBackPressed();
	}

	public boolean launchPrevActivityIntent() {
		if (prevActivityIntent != null) {
			prevActivityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			LatLon loc = getMapLocation();
			prevActivityIntent.putExtra(SearchActivity.SEARCH_LAT, loc.getLatitude());
			prevActivityIntent.putExtra(SearchActivity.SEARCH_LON, loc.getLongitude());
			if (mapViewTrackingUtilities.isMapLinkedToLocation()) {
				prevActivityIntent.putExtra(SearchActivity.SEARCH_NEARBY, true);
			}
			startActivity(prevActivityIntent);
			prevActivityIntent = null;
			return true;
		}
		return false;
	}

	private void quitAddGpxPointMode() {
		getMapLayers().getContextMenuLayer().getAddGpxPointBottomSheetHelper().hide();
		getMapLayers().getContextMenuLayer().quitAddGpxPoint();
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		intentHelper.parseLaunchIntents();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (activityRestartNeeded) {
			activityRestartNeeded = false;
			recreate();
			return;
		}

		long tm = System.currentTimeMillis();

		if (app.getMapMarkersHelper().getPlanRouteContext().isFragmentVisible()) {
			PlanRouteFragment.showInstance(this);
		}

		if (app.isApplicationInitializing() || DashboardOnMap.staticVisible) {
			if (!dashboardOnMap.isVisible()) {
				if (settings.SHOW_DASHBOARD_ON_START.get()) {
					dashboardOnMap.setDashboardVisibility(true, DashboardOnMap.staticVisibleType);
				} else {
					if (CrashBottomSheetDialogFragment.shouldShow(settings, this)) {
						SecondSplashScreenFragment.SHOW = false;
						CrashBottomSheetDialogFragment.showInstance(getSupportFragmentManager());
					} else if (RateUsHelper.shouldShowRateDialog(app)) {
						SecondSplashScreenFragment.SHOW = false;
						RateUsHelper.showRateDialog(this);
					}
				}
			} else {
				dashboardOnMap.updateDashboard();
			}
		}
		dashboardOnMap.updateLocation(true, true, false);

		getMyApplication().getNotificationHelper().refreshNotifications();
		// fixing bug with action bar appearing on android 2.3.3
		if (getSupportActionBar() != null) {
			getSupportActionBar().hide();
		}

		// for voice navigation
		ApplicationMode routingAppMode = getRoutingHelper().getAppMode();
		if (routingAppMode != null && settings.AUDIO_MANAGER_STREAM.getModeValue(routingAppMode) != null) {
			setVolumeControlStream(settings.AUDIO_MANAGER_STREAM.getModeValue(routingAppMode));
		} else {
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}

		applicationModeListener = new StateChangedListener<ApplicationMode>() {
			@Override
			public void stateChanged(ApplicationMode change) {
				updateApplicationModeSettings();
			}
		};
		settings.APPLICATION_MODE.addListener(applicationModeListener);
		updateApplicationModeSettings();


		// if destination point was changed try to recalculate route
		TargetPointsHelper targets = app.getTargetPointsHelper();
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (routingHelper.isFollowingMode()
				&& (!Algorithms.objectEquals(targets.getPointToNavigate().point, routingHelper.getFinalLocation()) || !Algorithms
				.objectEquals(targets.getIntermediatePointsLatLonNavigation(), routingHelper.getIntermediatePoints()))) {
			targets.updateRouteAndRefresh(true);
		}
		app.getLocationProvider().resumeAllUpdates();

		if (settings != null && settings.isLastKnownMapLocation() && !intentLocation) {
			LatLon l = settings.getLastKnownMapLocation();
			mapView.setLatLon(l.getLatitude(), l.getLongitude());
			mapView.setIntZoom(settings.getLastKnownMapZoom());
		} else {
			intentLocation = false;
		}

		settings.MAP_ACTIVITY_ENABLED.set(true);
		checkExternalStorage();
		showAndHideMapPosition();

		readLocationToShow();

		OsmandPlugin.checkInstalledMarketPlugins(app, this);
		OsmandPlugin.onMapActivityResume(this);

		intentHelper.parseContentIntent();
		mapView.refreshMap(true);
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnResume();
		}

		app.getDownloadThread().setUiActivity(this);

		boolean routeWasFinished = routingHelper.isRouteWasFinished();
		if (routeWasFinished && !DestinationReachedMenu.wasShown()) {
			DestinationReachedMenu.show(this);
		}

		routingHelper.addListener(this);
		app.getMapMarkersHelper().addListener(this);

		QuickSearchDialogFragment searchDialogFragment = getQuickSearchDialogFragment();
		if (searchDialogFragment != null) {
			if (searchDialogFragment.isSearchHidden()) {
				searchDialogFragment.hide();
				searchDialogFragment.restoreToolbar();
			}
		}

		if (System.currentTimeMillis() - tm > 50) {
			System.err.println("OnCreate for MapActivity took " + (System.currentTimeMillis() - tm) + " ms");
		}

		boolean showOsmAndWelcomeScreen = true;
		final Intent intent = getIntent();
		if (intent != null && intent.hasExtra(FirstUsageWelcomeFragment.SHOW_OSMAND_WELCOME_SCREEN)) {
			showOsmAndWelcomeScreen = intent.getBooleanExtra(FirstUsageWelcomeFragment.SHOW_OSMAND_WELCOME_SCREEN, true);
		}
		boolean showWelcomeScreen = ((app.getAppInitializer().isFirstTime() && Version.isDeveloperVersion(app)) || !app.getResourceManager().isAnyMapInstalled())
				&& FirstUsageWelcomeFragment.SHOW && settings.SHOW_OSMAND_WELCOME_SCREEN.get() && showOsmAndWelcomeScreen;

		if (!showWelcomeScreen && !permissionDone && !app.getAppInitializer().isFirstTime()) {
			if (!permissionAsked) {
				if (app.isExternalStorageDirectoryReadOnly()
						&& getSupportFragmentManager().findFragmentByTag(DataStoragePlaceDialogFragment.TAG) == null) {
					if (DownloadActivity.hasPermissionToWriteExternalStorage(this)) {
						DataStoragePlaceDialogFragment.showInstance(getSupportFragmentManager(), true);
					} else {
						ActivityCompat.requestPermissions(this,
								new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
								DownloadActivity.PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
					}
				}
			} else {
				if (permissionGranted) {
					restartApp();
				} else if (getSupportFragmentManager().findFragmentByTag(DataStoragePlaceDialogFragment.TAG) == null) {
					DataStoragePlaceDialogFragment.showInstance(getSupportFragmentManager(), true);
				}
				permissionAsked = false;
				permissionGranted = false;
				permissionDone = true;
			}
		}
		enableDrawer();

		if (showWelcomeScreen) {
			SecondSplashScreenFragment.SHOW = false;
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, new FirstUsageWelcomeFragment(),
							FirstUsageWelcomeFragment.TAG).commitAllowingStateLoss();
		} else if (SendAnalyticsBottomSheetDialogFragment.shouldShowDialog(app)) {
			SendAnalyticsBottomSheetDialogFragment.showInstance(app, getSupportFragmentManager(), null);
		}
		FirstUsageWelcomeFragment.SHOW = false;
		if (isFirstScreenShowing() && (!settings.SHOW_OSMAND_WELCOME_SCREEN.get() || !showOsmAndWelcomeScreen)) {
			FirstUsageWelcomeFragment welcomeFragment = getFirstUsageWelcomeFragment();
			if (welcomeFragment != null) {
				welcomeFragment.closeWelcomeFragment();
			}
			FirstUsageWizardFragment wizardFragment = getFirstUsageWizardFragment();
			if (wizardFragment != null) {
				wizardFragment.closeWizard();
			}
		}
		if (SecondSplashScreenFragment.SHOW) {
			SecondSplashScreenFragment.SHOW = false;
			SecondSplashScreenFragment.VISIBLE = true;
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.fragmentContainer, new SecondSplashScreenFragment(), SecondSplashScreenFragment.TAG)
					.commitAllowingStateLoss();
			mapView.setOnDrawMapListener(this);
			splashScreenTimer = new Timer();
			splashScreenTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					dismissSecondSplashScreen();
				}
			}, SECOND_SPLASH_TIME_OUT);
		} else {
			if (SecondSplashScreenFragment.VISIBLE) {
				dismissSecondSplashScreen();
			}
			applyScreenOrientation();
		}

		settings.MAP_SCREEN_ORIENTATION.addListener(mapScreenOrientationSettingListener);
		settings.USE_SYSTEM_SCREEN_TIMEOUT.addListener(useSystemScreenTimeoutListener);
	}

	@Override
	public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
		if (isTopResumedActivity) {
			OsmandPlugin.onMapActivityResumeOnTop(this);
		}
	}

	public void applyScreenOrientation() {
		if (settings.MAP_SCREEN_ORIENTATION.get() != getRequestedOrientation()) {
			setRequestedOrientation(settings.MAP_SCREEN_ORIENTATION.get());
		}
	}

	public void setKeepScreenOn(boolean keepScreenOn) {
		View mainView = findViewById(R.id.MapViewWithLayers);
		if (mainView != null) {
			mainView.setKeepScreenOn(keepScreenOn);
		}
	}

	public void updateStatusBarColor() {
		if (Build.VERSION.SDK_INT >= 21) {
			int colorId = -1;
			BaseOsmAndFragment fragmentAboveDashboard = getVisibleBaseOsmAndFragment(R.id.fragmentContainer);
			BaseSettingsFragment settingsFragmentAboveDashboard = getVisibleBaseSettingsFragment(R.id.fragmentContainer);
			BaseOsmAndFragment fragmentBelowDashboard = getVisibleBaseOsmAndFragment(R.id.routeMenuContainer,
					R.id.topFragmentContainer, R.id.bottomFragmentContainer);
			if (fragmentAboveDashboard != null) {
				colorId = fragmentAboveDashboard.getStatusBarColorId();
			} else if (settingsFragmentAboveDashboard != null) {
				colorId = settingsFragmentAboveDashboard.getStatusBarColorId();
			} else if (dashboardOnMap.isVisible()) {
				colorId = dashboardOnMap.getStatusBarColor();
			} else if (fragmentBelowDashboard != null) {
				colorId = fragmentBelowDashboard.getStatusBarColorId();
			} else if (mapLayers.getMapQuickActionLayer() != null
					&& mapLayers.getMapQuickActionLayer().isWidgetVisible()) {
				colorId = R.color.status_bar_transparent_gradient;
			}
			if (colorId != -1) {
				getWindow().setStatusBarColor(ContextCompat.getColor(this, colorId));
				return;
			}
			int color = TopToolbarController.NO_COLOR;
			boolean mapControlsVisible = findViewById(R.id.MapHudButtonsOverlay).getVisibility() == View.VISIBLE;
			boolean topToolbarVisible = getMapLayers().getMapInfoLayer().isTopToolbarViewVisible();
			boolean night = app.getDaynightHelper().isNightModeForMapControls();
			TopToolbarController toolbarController = getMapLayers().getMapInfoLayer().getTopToolbarController();
			if (toolbarController != null && mapControlsVisible && topToolbarVisible) {
				color = toolbarController.getStatusBarColor(this, night);
			}
			if (color == TopToolbarController.NO_COLOR) {
				boolean mapTopBar = findViewById(R.id.map_top_bar).getVisibility() == View.VISIBLE;
				boolean markerTopBar = findViewById(R.id.map_markers_top_bar).getVisibility() == View.VISIBLE;
				boolean coordinatesTopBar = findViewById(R.id.coordinates_top_bar).getVisibility() == View.VISIBLE;
				if (coordinatesTopBar && mapControlsVisible) {
					colorId = night ? R.color.status_bar_main_dark : R.color.status_bar_main_dark;
				} else if (mapTopBar && mapControlsVisible) {
					colorId = night ? R.color.status_bar_route_dark : R.color.status_bar_route_light;
				} else if (markerTopBar && mapControlsVisible) {
					colorId = R.color.status_bar_color_dark;
				} else {
					colorId = night ? R.color.status_bar_transparent_dark : R.color.status_bar_transparent_light;
				}
				color = ContextCompat.getColor(this, colorId);
			}
			getWindow().setStatusBarColor(color);
		}
	}

	private BaseOsmAndFragment getVisibleBaseOsmAndFragment(int... ids) {
		for (int id : ids) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);
			if (fragment != null && !fragment.isRemoving() && fragment instanceof BaseOsmAndFragment
					&& ((BaseOsmAndFragment) fragment).getStatusBarColorId() != -1) {
				return (BaseOsmAndFragment) fragment;
			}
		}
		return null;
	}

	private BaseSettingsFragment getVisibleBaseSettingsFragment(int... ids) {
		for (int id : ids) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(id);
			if (fragment != null && !fragment.isRemoving() && fragment instanceof BaseSettingsFragment
					&& ((BaseSettingsFragment) fragment).getStatusBarColorId() != -1) {
				return (BaseSettingsFragment) fragment;
			}
		}
		return null;
	}

	public boolean isInAppPurchaseAllowed() {
		return true;
	}

	public void showXMasDialog() {
		SecondSplashScreenFragment.SHOW = false;
		dismissSecondSplashScreen();
		new XMasDialogFragment().show(getSupportFragmentManager(), XMasDialogFragment.TAG);
	}


	private void dismissSecondSplashScreen() {
		if (SecondSplashScreenFragment.VISIBLE) {
			SecondSplashScreenFragment.VISIBLE = false;
			SecondSplashScreenFragment.SHOW = false;
			Fragment fragment = getSupportFragmentManager().findFragmentByTag(SecondSplashScreenFragment.TAG);
			if (fragment != null) {
				getSupportFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
			}
			applyScreenOrientation();
		}
	}

	@Override
	public void onDrawOverMap() {
		mapView.setOnDrawMapListener(null);
		cancelSplashScreenTimer();
		dismissSecondSplashScreen();
	}

	private void cancelSplashScreenTimer() {
		if (splashScreenTimer != null) {
			splashScreenTimer.cancel();
			splashScreenTimer = null;
		}
	}

	public void dismissCardDialog() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			fragmentManager.popBackStack(ContextMenuCardDialogFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		}
	}

	@Override
	public void onDismissDialogFragment(DialogFragment dialogFragment) {
		if (dialogFragment instanceof DataStoragePlaceDialogFragment) {
			FirstUsageWizardFragment wizardFragment = getFirstUsageWizardFragment();
			if (wizardFragment != null) {
				wizardFragment.updateStorageView();
			}
		}
	}

	public boolean isActivityDestroyed() {
		return mIsDestroyed;
	}

	public boolean isMapVisible() {
		if (isFragmentVisible()) {
			return false;
		}
		return AndroidUtils.isActivityNotDestroyed(this) && settings.MAP_ACTIVITY_ENABLED.get()
				&& !dashboardOnMap.isVisible();
	}

	public boolean isFragmentVisible() {
		for (Fragment fragment : getSupportFragmentManager().getFragments()) {
			if (!(fragment instanceof DashBaseFragment) && fragment.isVisible()
					|| dashboardOnMap.isVisible()) {
				return true;
			}
		}
		return false;
	}

	private void restartApp() {
		AlertDialog.Builder bld = new AlertDialog.Builder(this);
		bld.setMessage(R.string.storage_permission_restart_is_required);
		bld.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				doRestart(MapActivity.this);
				//android.os.Process.killProcess(android.os.Process.myPid());
			}
		});
		bld.show();
	}

	public static void doRestart(Context c) {
		boolean res = false;
		try {
			//check if the context is given
			if (c != null) {
				//fetch the packagemanager so we can get the default launch activity
				// (you can replace this intent with any other activity if you want
				PackageManager pm = c.getPackageManager();
				//check if we got the PackageManager
				if (pm != null) {
					//create the intent with the default start activity for your application
					Intent mStartActivity = pm.getLaunchIntentForPackage(
							c.getPackageName()
					);
					if (mStartActivity != null) {
						mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						//create a pending intent so the application is restarted after System.exit(0) was called.
						// We use an AlarmManager to call this intent in 100ms
						int mPendingIntentId = 84523443;
						PendingIntent mPendingIntent = PendingIntent
								.getActivity(c, mPendingIntentId, mStartActivity,
										PendingIntent.FLAG_CANCEL_CURRENT);
						AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
						if (Build.VERSION.SDK_INT >= 19) {
							mgr.setExact(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
						} else {
							mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
						}
						//kill the application
						res = true;
						android.os.Process.killProcess(android.os.Process.myPid());
						//System.exit(0);
					} else {
						LOG.error("Was not able to restart application, mStartActivity null");
					}
				} else {
					LOG.error("Was not able to restart application, PM null");
				}
			} else {
				LOG.error("Was not able to restart application, Context null");
			}
		} catch (Exception ex) {
			LOG.error("Was not able to restart application");
		}
		if (!res) {
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	public void readLocationToShow() {
		showMapControls();

		LatLon cur = new LatLon(mapView.getLatitude(), mapView.getLongitude());
		LatLon latLonToShow = settings.getAndClearMapLocationToShow();
		PointDescription mapLabelToShow = settings.getAndClearMapLabelToShow(latLonToShow);
		Object toShow = settings.getAndClearObjectToShow();
		boolean editToShow = settings.getAndClearEditObjectToShow();
		int status = settings.isRouteToPointNavigateAndClear();
		String searchRequestToShow = settings.getAndClearSearchRequestToShow();
		if (status != 0 || searchRequestToShow != null || latLonToShow != null) {
			dismissSettingsScreens();
		}
		if (status != 0) {
			// always enable and follow and let calculate it (i.e.GPS is not accessible in a garage)
			Location loc = new Location("map");
			loc.setLatitude(mapView.getLatitude());
			loc.setLongitude(mapView.getLongitude());
			getMapActions().enterRoutePlanningModeGivenGpx(null, null, null, true, true);
			if (dashboardOnMap.isVisible()) {
				dashboardOnMap.hideDashboard();
			}
		}
		if (trackDetailsMenu.isVisible()) {
			trackDetailsMenu.show();
		}
		if (searchRequestToShow != null) {
			showQuickSearch(searchRequestToShow);
		}
		if (latLonToShow != null) {
			if (dashboardOnMap.isVisible()) {
				dashboardOnMap.hideDashboard();
			}
			// remember if map should come back to isMapLinkedToLocation=true
			mapViewTrackingUtilities.setMapLinkedToLocation(false);
			if (mapLabelToShow != null && !mapLabelToShow.contextMenuDisabled()) {
				mapContextMenu.setMapCenter(latLonToShow);
				mapContextMenu.setMapPosition(mapView.getMapPosition());
				mapContextMenu.setCenterMarker(true);

				RotatedTileBox tb = mapView.getCurrentRotatedTileBox().copy();
				LatLon prevCenter = tb.getCenterLatLon();

				double border = 0.8;
				int tbw = (int) (tb.getPixWidth() * border);
				int tbh = (int) (tb.getPixHeight() * border);
				tb.setPixelDimensions(tbw, tbh);

				tb.setLatLonCenter(latLonToShow.getLatitude(), latLonToShow.getLongitude());
				tb.setZoom(ZOOM_LABEL_DISPLAY);
				while (!tb.containsLatLon(prevCenter.getLatitude(), prevCenter.getLongitude()) && tb.getZoom() > MIN_ZOOM_LABEL_DISPLAY) {
					tb.setZoom(tb.getZoom() - 1);
				}
				//mapContextMenu.setMapZoom(settings.getMapZoomToShow());
				mapContextMenu.setMapZoom(tb.getZoom());
				if (toShow instanceof GpxDisplayItem) {
					trackDetailsMenu.setGpxItem((GpxDisplayItem) toShow);
					trackDetailsMenu.show();
				} else if (mapRouteInfoMenu.isVisible()) {
					mapContextMenu.showMinimized(latLonToShow, mapLabelToShow, toShow);
					mapRouteInfoMenu.updateMenu();
					MapRouteInfoMenu.showLocationOnMap(this, latLonToShow.getLatitude(), latLonToShow.getLongitude());
				} else if (toShow instanceof GPXFile) {
					hideContextAndRouteInfoMenues();
					GPXFile gpxFile = (GPXFile) toShow;
					SelectedGpxFile selectedGpxFile;
					if (gpxFile.showCurrentTrack) {
						selectedGpxFile = app.getSavingTrackHelper().getCurrentTrack();
					} else {
						selectedGpxFile = app.getSelectedGpxHelper().getSelectedFileByPath(gpxFile.path);
					}

					TrackAppearanceFragment.showInstance(this, selectedGpxFile, null);
				} else if (toShow instanceof QuadRect) {
					QuadRect qr = (QuadRect) toShow;
					mapView.fitRectToMap(qr.left, qr.right, qr.top, qr.bottom, (int) qr.width(), (int) qr.height(), 0);
				} else if (toShow instanceof NewGpxPoint) {
					NewGpxPoint newGpxPoint = (NewGpxPoint) toShow;
					QuadRect qr = newGpxPoint.getRect();
					mapView.fitRectToMap(qr.left, qr.right, qr.top, qr.bottom, (int) qr.width(), (int) qr.height(), 0);
					getMapLayers().getContextMenuLayer().enterAddGpxPointMode(newGpxPoint);
				} else if (toShow instanceof GpxData) {
					hideContextAndRouteInfoMenues();

					GpxData gpxData = (GpxData) toShow;
					QuadRect qr = gpxData.getRect();
					mapView.fitRectToMap(qr.left, qr.right, qr.top, qr.bottom, (int) qr.width(), (int) qr.height(), 0);
					MeasurementEditingContext editingContext = new MeasurementEditingContext();
					editingContext.setGpxData(gpxData);
					MeasurementToolFragment.showInstance(getSupportFragmentManager(), editingContext);
				} else {
					mapContextMenu.show(latLonToShow, mapLabelToShow, toShow);
				}
				if (editToShow) {
					mapContextMenu.openEditor();
				}
			} else if (!latLonToShow.equals(cur)) {
				mapView.getAnimatedDraggingThread().startMoving(latLonToShow.getLatitude(),
						latLonToShow.getLongitude(), settings.getMapZoomToShow(), true);
			}
		}
	}

	public OsmandApplication getMyApplication() {
		return ((OsmandApplication) getApplication());
	}

	public void addDialogProvider(DialogProvider dp) {
		dialogProviders.add(dp);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		for (DialogProvider dp : dialogProviders) {
			dialog = dp.onCreateDialog(id);
			if (dialog != null) {
				return dialog;
			}
		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		for (DialogProvider dp : dialogProviders) {
			dp.onPrepareDialog(id, dialog);
		}
	}

	public void changeZoom(int stp, long time) {
		mapViewTrackingUtilities.setZoomTime(time);
		changeZoom(stp);
	}

	public void changeZoom(int stp) {
		// delta = Math.round(delta * OsmandMapTileView.ZOOM_DELTA) * OsmandMapTileView.ZOOM_DELTA_1;
		boolean changeLocation = false;
		// if (settings.AUTO_ZOOM_MAP.get() == AutoZoomMap.NONE) {
		// changeLocation = false;
		// }

		// double curZoom = mapView.getZoom() + mapView.getZoomFractionalPart() + stp * 0.3;
		// int newZoom = (int) Math.round(curZoom);
		// double zoomFrac = curZoom - newZoom;

		final int newZoom = mapView.getZoom() + stp;
		final double zoomFrac = mapView.getZoomFractionalPart();
		if (newZoom > mapView.getMaxZoom()) {
			Toast.makeText(this, R.string.edit_tilesource_maxzoom, Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
			return;
		}
		if (newZoom < mapView.getMinZoom()) {
			Toast.makeText(this, R.string.edit_tilesource_minzoom, Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
			return;
		}
		mapView.getAnimatedDraggingThread().startZooming(newZoom, zoomFrac, changeLocation);
		if (app.accessibilityEnabled())
			Toast.makeText(this, getString(R.string.zoomIs) + " " + newZoom, Toast.LENGTH_SHORT).show(); //$NON-NLS-1$
		showAndHideMapPosition();
	}

	public void setMapLocation(double lat, double lon) {
		mapView.setLatLon(lat, lon);
		mapViewTrackingUtilities.locationChanged(lat, lon, this);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_MOVE && settings.USE_TRACKBALL_FOR_MOVEMENTS.get()) {
			float x = event.getX();
			float y = event.getY();
			final RotatedTileBox tb = mapView.getCurrentRotatedTileBox();
			final QuadPoint cp = tb.getCenterPixelPoint();
			final LatLon l = tb.getLatLonFromPixel(cp.x + x * 15, cp.y + y * 15);
			setMapLocation(l.getLatitude(), l.getLongitude());
			return true;
		}
		return super.onTrackballEvent(event);
	}


	@Override
	protected void onStart() {
		super.onStart();
		stopped = false;
		lockHelper.onStart(this);
		mapScrollHelper.setListener(this);
		getMyApplication().getNotificationHelper().showNotifications();
	}

	@Override
	protected void onStop() {
		getMyApplication().getNotificationHelper().removeNotifications(true);
		if (pendingPause) {
			onPauseActivity();
		}
		stopped = true;
		lockHelper.onStop(this);
		mapScrollHelper.setListener(null);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mapContextMenu.setMapActivity(null);
		mapRouteInfoMenu.setMapActivity(null);
		trackDetailsMenu.setMapActivity(null);
		unregisterReceiver(screenOffReceiver);
		app.getAidlApi().onDestroyMapActivity(this);
		FailSafeFuntions.quitRouteRestoreDialog();
		OsmandPlugin.onMapActivityDestroy(this);
		getMyApplication().unsubscribeInitListener(initListener);
		mapViewTrackingUtilities.setMapView(null);
		app.getResourceManager().getMapTileDownloader().removeDownloaderCallback(mapView);
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnDestroy();
		}
		lockHelper.setLockUIAdapter(null);

		mIsDestroyed = true;
	}

	public LatLon getMapLocation() {
		return mapViewTrackingUtilities.getMapLocation();
	}

	public float getMapRotate() {
		if (mapView == null) {
			return 0;
		}
		return mapView.getRotate();
	}

	// Duplicate methods to OsmAndApplication
	public TargetPoint getPointToNavigate() {
		return app.getTargetPointsHelper().getPointToNavigate();
	}

	public RoutingHelper getRoutingHelper() {
		return app.getRoutingHelper();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode()) {
			pendingPause = true;
		} else {
			onPauseActivity();
		}

	}

	private void onPauseActivity() {
		settings.MAP_SCREEN_ORIENTATION.removeListener(mapScreenOrientationSettingListener);
		settings.USE_SYSTEM_SCREEN_TIMEOUT.removeListener(useSystemScreenTimeoutListener);
		if (!app.getRoutingHelper().isRouteWasFinished()) {
			DestinationReachedMenu.resetShownState();
		}
		if (trackDetailsMenu.isVisible()) {
			trackDetailsMenu.dismiss(false);
		}
		pendingPause = false;
		mapView.setOnDrawMapListener(null);
		cancelSplashScreenTimer();
		app.getMapMarkersHelper().removeListener(this);
		app.getRoutingHelper().removeListener(this);
		app.getDownloadThread().resetUiActivity(this);
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnPause();
		}
		app.getLocationProvider().pauseAllUpdates();
		app.getDaynightHelper().stopSensorIfNeeded();
		settings.APPLICATION_MODE.removeListener(applicationModeListener);

		settings.setLastKnownMapLocation((float) mapView.getLatitude(), (float) mapView.getLongitude());
		AnimateDraggingMapThread animatedThread = mapView.getAnimatedDraggingThread();
		if (animatedThread.isAnimating() && animatedThread.getTargetIntZoom() != 0 && !mapViewTrackingUtilities.isMapLinkedToLocation()) {
			settings.setMapLocationToShow(animatedThread.getTargetLatitude(), animatedThread.getTargetLongitude(),
					animatedThread.getTargetIntZoom());
		}

		settings.setLastKnownMapZoom(mapView.getZoom());
		settings.setLastKnownMapElevation(mapView.getElevationAngle());
		settings.MAP_ACTIVITY_ENABLED.set(false);
		app.getResourceManager().interruptRendering();
		OsmandPlugin.onMapActivityPause(this);
	}

	public void updateApplicationModeSettings() {
		changeKeyguardFlags();
		updateMapSettings();
		app.getPoiFilters().loadSelectedPoiFilters();
		mapViewTrackingUtilities.updateSettings();
		mapViewTrackingUtilities.resetDrivingRegionUpdate();
		//app.getRoutingHelper().setAppMode(settings.getApplicationMode());
		if (mapLayers.getMapInfoLayer() != null) {
			mapLayers.getMapInfoLayer().recreateControls();
		}
		if (mapLayers.getMapQuickActionLayer() != null) {
			mapLayers.getMapQuickActionLayer().refreshLayer();
		}
		MapControlsLayer mapControlsLayer = mapLayers.getMapControlsLayer();
		if (mapControlsLayer != null && (!mapControlsLayer.isMapControlsVisible() && !settings.MAP_EMPTY_STATE_ALLOWED.get())) {
			showMapControls();
		}

		mapLayers.updateLayers(mapView);
		mapActions.updateDrawerMenu();
		updateNavigationBarColor();
		mapView.setComplexZoom(mapView.getZoom(), mapView.getSettingsMapDensity());
		app.getDaynightHelper().startSensorIfNeeded(new StateChangedListener<Boolean>() {

			@Override
			public void stateChanged(Boolean change) {
				getMapView().refreshMap(true);
			}
		});
		getMapView().refreshMap(true);
		applyScreenOrientation();
		app.getAppCustomization().updateMapMargins(this);
	}

	public void updateNavigationBarColor() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			if (getMyApplication().getDaynightHelper().isNightModeForMapControls() || getMyApplication().getDaynightHelper().isNightMode()) {
				getWindow().setNavigationBarColor(ContextCompat.getColor(app, R.color.navigation_bar_bg_dark));
			} else {
				getWindow().setNavigationBarColor(ContextCompat.getColor(app, R.color.navigation_bar_bg_light));
			}
		}
	}

	public void updateMapSettings() {
		if (app.isApplicationInitializing()) {
			return;
		}
		// update vector renderer
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				RendererRegistry registry = app.getRendererRegistry();
				RenderingRulesStorage newRenderer = registry.getRenderer(settings.RENDERER.get());
				if (newRenderer == null) {
					newRenderer = registry.defaultRender();
				}
				if (mapView.getMapRenderer() != null) {
					NativeCoreContext.getMapRendererContext().updateMapSettings();
				}
				if (registry.getCurrentSelectedRenderer() != newRenderer) {
					registry.setCurrentSelectedRender(newRenderer);
					app.getResourceManager().getRenderer().clearCache();
					mapView.resetDefaultColor();
					mapView.refreshMap(true);
				} else {
					mapView.resetDefaultColor();
				}

				return null;
			}

			protected void onPostExecute(Void result) {
				DashboardOnMap dashboard = getDashboard();
				if (dashboard != null) {
					dashboard.onMapSettingsUpdated();
				}
			}
		}.executeOnExecutor(singleThreadExecutor, (Void) null);

	}

	public ScrollHelper getMapScrollHelper() {
		return mapScrollHelper;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mapActivityKeyListener != null) {
			if (mapActivityKeyListener.onKeyDown(keyCode, event)) {
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (mapActivityKeyListener != null) {
			if (mapActivityKeyListener.onKeyUp(keyCode, event)) {
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private void scrollMap(int dx, int dy) {
		final RotatedTileBox tb = mapView.getCurrentRotatedTileBox();
		final QuadPoint cp = tb.getCenterPixelPoint();
		final LatLon l = tb.getLatLonFromPixel(cp.x + dx, cp.y + dy);
		setMapLocation(l.getLatitude(), l.getLongitude());
	}

	public void checkExternalStorage() {
		if (Build.VERSION.SDK_INT >= 19) {
			return;
		}
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// ok
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			Toast.makeText(this, R.string.sd_mounted_ro, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, R.string.sd_unmounted, Toast.LENGTH_LONG).show();
		}
	}

	public void showAndHideMapPosition() {
		mapView.setShowMapPosition(true);
		app.runMessageInUIThreadAndCancelPrevious(SHOW_POSITION_MSG_ID, new Runnable() {
			@Override
			public void run() {
				if (mapView.isShowMapPosition()) {
					mapView.setShowMapPosition(false);
					mapView.refreshMap();
				}
			}
		}, 2500);
	}

	public void showMapControls() {
		if (!getDashboard().isVisible() && mapLayers.getMapControlsLayer() != null) {
			mapLayers.getMapControlsLayer().showMapControlsIfHidden();
		}
	}

	public boolean shouldHideTopControls() {
		boolean hideTopControls = !mapContextMenu.shouldShowTopControls();

		TrackMenuFragment fragment = getTrackMenuFragment();
		if (fragment != null) {
			hideTopControls = hideTopControls || !fragment.shouldShowTopControls();
		}

		return hideTopControls;
	}

	public OsmandMapTileView getMapView() {
		return mapView;
	}

	public MapViewTrackingUtilities getMapViewTrackingUtilities() {
		return mapViewTrackingUtilities;
	}

	public MapActivityActions getMapActions() {
		return mapActions;
	}

	public MapActivityLayers getMapLayers() {
		return mapLayers;
	}

	public WidgetsVisibilityHelper getWidgetsVisibilityHelper() {
		return mapWidgetsVisibilityHelper;
	}

	public static void launchMapActivityMoveToTop(Context activity, Bundle prevIntentParams, Uri intentData, Bundle intentParams) {
		if (activity instanceof MapActivity) {
			if (((MapActivity) activity).getDashboard().isVisible()) {
				((MapActivity) activity).getDashboard().hideDashboard();
			}
			((MapActivity) activity).readLocationToShow();
		} else {
			int additionalFlags = 0;
			if (activity instanceof Activity) {
				Intent intent = ((Activity) activity).getIntent();
				if (intent != null) {
					prevActivityIntent = new Intent(intent);
					if (prevIntentParams != null) {
						prevActivityIntent.putExtra(INTENT_PARAMS, prevIntentParams);
						prevActivityIntent.putExtras(prevIntentParams);
					}
					prevActivityIntent.putExtra(INTENT_KEY_PARENT_MAP_ACTIVITY, true);
				} else {
					prevActivityIntent = null;
				}
			} else {
				prevActivityIntent = null;
				additionalFlags = Intent.FLAG_ACTIVITY_NEW_TASK;
			}

			Intent newIntent = new Intent(activity, ((OsmandApplication) activity.getApplicationContext())
					.getAppCustomization().getMapActivity());
			newIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | additionalFlags);
			if (intentData != null) {
				newIntent.setAction(Intent.ACTION_VIEW);
				newIntent.setData(intentData);
			}
			if (intentParams != null) {
				newIntent.putExtra(INTENT_PARAMS, intentParams);
				newIntent.putExtras(intentParams);
			}
			activity.startActivity(newIntent);
		}
	}

	public static void launchMapActivityMoveToTop(Context activity) {
		launchMapActivityMoveToTop(activity, null);
	}

	public static void launchMapActivityMoveToTop(Context activity, Bundle prevIntentParams) {
		launchMapActivityMoveToTop(activity, prevIntentParams, null, null);
	}

	public static void clearPrevActivityIntent() {
		prevActivityIntent = null;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		for (ActivityResultListener listener : activityResultListeners) {
			if (listener.processResult(requestCode, resultCode, data)) {
				removeActivityResultListener(listener);
				return;
			}
		}
		OsmandPlugin.onMapActivityResult(requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void refreshMap() {
		getMapView().refreshMap();
	}

	public void refreshMapComplete() {
		getMyApplication().getResourceManager().getRenderer().clearCache();
		updateMapSettings();
		getMapView().refreshMap(true);
	}

	public View getLayout() {
		return getWindow().getDecorView().findViewById(android.R.id.content);
	}

	public void setMargins(int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
		View layout = getLayout();
		if (layout != null) {
			ViewGroup.LayoutParams params = layout.getLayoutParams();
			if (params instanceof ViewGroup.MarginLayoutParams) {
				((ViewGroup.MarginLayoutParams) params).setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
			}
		}
	}

	public DashboardOnMap getDashboard() {
		return dashboardOnMap;
	}

	@NonNull
	public MapContextMenu getContextMenu() {
		return mapContextMenu;
	}

	@NonNull
	public MapRouteInfoMenu getMapRouteInfoMenu() {
		return mapRouteInfoMenu;
	}

	@NonNull
	public TrackDetailsMenu getTrackDetailsMenu() {
		return trackDetailsMenu;
	}

	public void hideContextAndRouteInfoMenues() {
		mapContextMenu.hideMenues();
		mapRouteInfoMenu.hide();
	}

	public void openDrawer() {
		mapActions.updateDrawerMenu();
		boolean animate = !settings.DO_NOT_USE_ANIMATIONS.get();
		drawerLayout.openDrawer(Gravity.START, animate);
	}

	public void disableDrawer() {
		drawerDisabled = true;
		if (settings.DO_NOT_USE_ANIMATIONS.get()) {
			closeDrawer();
		}
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
	}

	public void enableDrawer() {
		drawerDisabled = false;
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	}

	public boolean isDrawerDisabled() {
		return drawerDisabled;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (settings.DO_NOT_USE_ANIMATIONS.get()) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				if (drawerLayout.isDrawerOpen(Gravity.START)) {
					int drawerWidth = AndroidUtils.dpToPx(this, 280);
					int screenWidth = AndroidUtils.getScreenWidth(MapActivity.this);
					boolean isLayoutRtl = AndroidUtils.isLayoutRtl(app);
					if ((!isLayoutRtl && event.getRawX() > drawerWidth)
							|| (isLayoutRtl && event.getRawX() <= screenWidth - drawerWidth)) {
						closeDrawer();
					}
				}
			}
		}
		return super.dispatchTouchEvent(event);
	}

	public void closeDrawer() {
		boolean animate = !settings.DO_NOT_USE_ANIMATIONS.get();
		drawerLayout.closeDrawer(Gravity.START, animate);
	}

	public void toggleDrawer() {
		if (drawerLayout.isDrawerOpen(Gravity.START)) {
			closeDrawer();
		} else {
			openDrawer();
		}
	}

	public FirstUsageWelcomeFragment getFirstUsageWelcomeFragment() {
		FirstUsageWelcomeFragment welcomeFragment = (FirstUsageWelcomeFragment) getSupportFragmentManager().findFragmentByTag(FirstUsageWelcomeFragment.TAG);
		if (welcomeFragment != null && !welcomeFragment.isDetached()) {
			return welcomeFragment;
		} else {
			return null;
		}
	}

	public FirstUsageWizardFragment getFirstUsageWizardFragment() {
		FirstUsageWizardFragment wizardFragment = (FirstUsageWizardFragment) getSupportFragmentManager().findFragmentByTag(FirstUsageWizardFragment.TAG);
		if (wizardFragment != null && !wizardFragment.isDetached()) {
			return wizardFragment;
		} else {
			return null;
		}
	}

	public boolean isFirstScreenShowing() {
		FirstUsageWelcomeFragment welcomeFragment = (FirstUsageWelcomeFragment) getSupportFragmentManager().findFragmentByTag(FirstUsageWelcomeFragment.TAG);
		FirstUsageWizardFragment wizardFragment = (FirstUsageWizardFragment) getSupportFragmentManager().findFragmentByTag(FirstUsageWizardFragment.TAG);
		return welcomeFragment != null && !welcomeFragment.isDetached()
				|| wizardFragment != null && !wizardFragment.isDetached();
	}

	// DownloadEvents
	@Override
	public void newDownloadIndexes() {
		for (Fragment fragment : getSupportFragmentManager().getFragments()) {
			if (fragment instanceof DownloadEvents) {
				((DownloadEvents) fragment).newDownloadIndexes();
			}
		}
		if (dashboardOnMap.isVisible()) {
			dashboardOnMap.onNewDownloadIndexes();
		}
		refreshMap();
	}

	@Override
	public void downloadInProgress() {
		for (Fragment fragment : getSupportFragmentManager().getFragments()) {
			if (fragment instanceof DownloadEvents) {
				((DownloadEvents) fragment).downloadInProgress();
			}
		}
		if (dashboardOnMap.isVisible()) {
			dashboardOnMap.onDownloadInProgress();
		}
	}

	@Override
	public void downloadHasFinished() {
		for (Fragment fragment : getSupportFragmentManager().getFragments()) {
			if (fragment instanceof DownloadEvents) {
				((DownloadEvents) fragment).downloadHasFinished();
			}
		}
		if (dashboardOnMap.isVisible()) {
			dashboardOnMap.onDownloadHasFinished();
		}
		refreshMap();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull final int[] grantResults) {
		if (grantResults.length > 0) {
			OsmandPlugin.onRequestPermissionsResult(requestCode, permissions, grantResults);

			MapControlsLayer mcl = mapView.getLayerByClass(MapControlsLayer.class);
			if (mcl != null) {
				mcl.onRequestPermissionsResult(requestCode, permissions, grantResults);
			}

			if (requestCode == DataStorageFragment.PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
					&& permissions.length > 0
					&& Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[0])) {
				if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					Toast.makeText(this,
							R.string.missing_write_external_storage_permission,
							Toast.LENGTH_LONG).show();
				}
			} else if (requestCode == DownloadActivity.PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
					&& permissions.length > 0
					&& Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[0])) {
				permissionAsked = true;
				permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
			} else if (requestCode == FirstUsageWizardFragment.FIRST_USAGE_REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION
					&& permissions.length > 0
					&& Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[0])) {

				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						FirstUsageWizardFragment wizardFragment = getFirstUsageWizardFragment();
						if (wizardFragment != null) {
							wizardFragment.processStoragePermission(grantResults[0] == PackageManager.PERMISSION_GRANTED);
						}
					}
				}, 1);
			} else if (requestCode == FirstUsageWizardFragment.FIRST_USAGE_LOCATION_PERMISSION) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						FirstUsageWizardFragment wizardFragment = getFirstUsageWizardFragment();
						if (wizardFragment != null) {
							wizardFragment.processLocationPermission(grantResults[0] == PackageManager.PERMISSION_GRANTED);
						}
					}
				}, 1);
			} else if (requestCode == MapActivityActions.REQUEST_LOCATION_FOR_DIRECTIONS_NAVIGATION_PERMISSION
					&& permissions.length > 0
					&& Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0])) {
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					LatLon latLon = getContextMenu().getLatLon();
					if (latLon != null) {
						mapActions.enterDirectionsFromPoint(latLon.getLatitude(), latLon.getLongitude());
					}
				} else {
					app.showToastMessage(R.string.ask_for_location_permission);
				}
			}
		}

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	public void onMapMarkerChanged(MapMarker mapMarker) {
		refreshMap();
	}

	@Override
	public void onMapMarkersChanged() {
		refreshMap();
	}

	@Override
	public void onAMapPointUpdated(final AidlMapPointWrapper point, String layerId) {
		if (canUpdateAMapPointMenu(point, layerId)) {
			app.runInUIThread(new Runnable() {
				@Override
				public void run() {
					LatLon latLon = point.getLocation();
					PointDescription pointDescription = new PointDescription(PointDescription.POINT_TYPE_MARKER, point.getFullName());
					mapContextMenu.update(latLon, pointDescription, point);
					mapContextMenu.centerMarkerLocation();
				}
			});
		}
	}

	private boolean canUpdateAMapPointMenu(AidlMapPointWrapper point, String layerId) {
		Object object = mapContextMenu.getObject();
		if (!mapContextMenu.isVisible() || !(object instanceof AidlMapPointWrapper)) {
			return false;
		}
		AidlMapPointWrapper oldPoint = (AidlMapPointWrapper) object;
		return oldPoint.getLayerId().equals(layerId) && oldPoint.getId().equals(point.getId());
	}

	public void changeKeyguardFlags() {
		boolean enabled = settings.TURN_SCREEN_ON_TIME_INT.get() >= 0;
		boolean keepScreenOn = !settings.USE_SYSTEM_SCREEN_TIMEOUT.get();
		changeKeyguardFlags(enabled, keepScreenOn);
	}

	private void changeKeyguardFlags(boolean enable, boolean forceKeepScreenOn) {
		if (enable) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
					WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}
		setKeepScreenOn(forceKeepScreenOn);
	}

	@Override
	public void lock() {
		changeKeyguardFlags(false, false);
	}

	@Override
	public void unlock() {
		changeKeyguardFlags(true, true);
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
		try {
			String fragmentName = pref.getFragment();
			Fragment fragment = Fragment.instantiate(this, fragmentName);
			if (caller instanceof BaseSettingsFragment) {
				fragment.setArguments(((BaseSettingsFragment) caller).buildArguments());
			}
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, fragment, fragment.getClass().getName())
					.addToBackStack(DRAWER_SETTINGS_ID + ".new")
					.commit();

			return true;
		} catch (Exception e) {
			LOG.error(e);
		}

		return false;
	}

	public void dismissSettingsScreens() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			fragmentManager.popBackStack(DRAWER_SETTINGS_ID + ".new", FragmentManager.POP_BACK_STACK_INCLUSIVE);
		}
	}

	@Override
	public void onScrollEvent(boolean continuousScrolling, boolean up, boolean down, boolean left, boolean right) {
		int scrollingUnit = continuousScrolling ? SMALL_SCROLLING_UNIT : BIG_SCROLLING_UNIT;
		int dx = (left ? -scrollingUnit : 0) + (right ? scrollingUnit : 0);
		int dy = (up ? -scrollingUnit : 0) + (down ? scrollingUnit : 0);
		scrollMap(dx, dy);
	}

	private class ScreenOffReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			OsmandPlugin.onMapActivityScreenOff(MapActivity.this);
		}

	}

	public boolean isLandscapeLayout() {
		return landscapeLayout;
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		if (mapRouteInfoMenu.isSelectFromMapTouch()) {
			return;
		}
		refreshMap();
		RoutingHelper rh = app.getRoutingHelper();
		if (newRoute && rh.isRoutePlanningMode() && mapView != null) {
			Location lt = rh.getLastProjection();
			if (lt == null) {
				lt = app.getTargetPointsHelper().getPointToStartLocation();
			}
			if (lt != null) {
				double left = lt.getLongitude(), right = lt.getLongitude();
				double top = lt.getLatitude(), bottom = lt.getLatitude();
				List<Location> list = rh.getCurrentCalculatedRoute();
				for (Location l : list) {
					left = Math.min(left, l.getLongitude());
					right = Math.max(right, l.getLongitude());
					top = Math.max(top, l.getLatitude());
					bottom = Math.min(bottom, l.getLatitude());
				}
				List<TargetPoint> targetPoints = app.getTargetPointsHelper().getIntermediatePointsWithTarget();
				for (TargetPoint l : targetPoints) {
					left = Math.min(left, l.getLongitude());
					right = Math.max(right, l.getLongitude());
					top = Math.max(top, l.getLatitude());
					bottom = Math.min(bottom, l.getLatitude());
				}

				RotatedTileBox tb = mapView.getCurrentRotatedTileBox().copy();
				int tileBoxWidthPx = 0;
				int tileBoxHeightPx = 0;

				WeakReference<MapRouteInfoMenuFragment> fragmentRef = mapRouteInfoMenu.findMenuFragment();
				if (fragmentRef != null) {
					MapRouteInfoMenuFragment f = fragmentRef.get();
					if (!f.isPortrait()) {
						tileBoxWidthPx = tb.getPixWidth() - f.getWidth();
					} else {
						tileBoxHeightPx = tb.getPixHeight() - f.getHeight();
					}
				}
				mapView.fitRectToMap(left, right, top, bottom, tileBoxWidthPx, tileBoxHeightPx, 0);
			}
		}
		if (app.getSettings().simulateNavigation) {
			OsmAndLocationSimulation sim = app.getLocationProvider().getLocationSimulation();
			if (newRoute && rh.isFollowingMode() && !sim.isRouteAnimating()) {
				sim.startStopRouteAnimation(this);
			}
		}
	}

	@Override
	public void routeWasCancelled() {
		changeKeyguardFlags();
	}

	@Override
	public void routeWasFinished() {
		if (!mIsDestroyed) {
			DestinationReachedMenu.show(this);
			changeKeyguardFlags();
		}
	}

	public void showQuickSearch(double latitude, double longitude) {
		hideVisibleMenu();
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (fragment != null) {
			fragment.dismiss();
			refreshMap();
		}
		QuickSearchDialogFragment.showInstance(this, "", null,
				QuickSearchType.REGULAR, QuickSearchTab.CATEGORIES, new LatLon(latitude, longitude));
	}

	public void showQuickSearch(String searchQuery) {
		hideVisibleMenu();
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (fragment != null) {
			fragment.dismiss();
			refreshMap();
		}
		QuickSearchDialogFragment.showInstance(this, searchQuery, null,
				QuickSearchType.REGULAR, QuickSearchTab.CATEGORIES, null);
	}

	public void showQuickSearch(Object object) {
		showQuickSearch(object, null);
	}

	public void showQuickSearch(Object object, @Nullable LatLon latLon) {
		hideVisibleMenu();
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (fragment != null) {
			fragment.dismiss();
			refreshMap();
		}
		QuickSearchDialogFragment.showInstance(this, "", object,
				QuickSearchType.REGULAR, QuickSearchTab.CATEGORIES, latLon);
	}

	public void showQuickSearch(ShowQuickSearchMode mode, boolean showCategories) {
		showQuickSearch(mode, showCategories, "", null);
	}

	public void showQuickSearch(ShowQuickSearchMode mode, QuickSearchTab showSearchTab) {
		showQuickSearch(mode, showSearchTab, "", null);
	}

	public void showQuickSearch(@NonNull ShowQuickSearchMode mode, boolean showCategories,
								@NonNull String searchQuery, @Nullable LatLon searchLocation) {
		if (mode == ShowQuickSearchMode.CURRENT) {
			mapContextMenu.close();
		} else {
			hideVisibleMenu();
		}
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (mode.isPointSelection()) {
			if (fragment != null) {
				fragment.dismiss();
			}
			QuickSearchType searchType = null;
			switch (mode) {
				case START_POINT_SELECTION:
					searchType = QuickSearchType.START_POINT;
					break;
				case DESTINATION_SELECTION:
					searchType = QuickSearchType.DESTINATION;
					break;
				case DESTINATION_SELECTION_AND_START:
					searchType = QuickSearchType.DESTINATION_AND_START;
					break;
				case INTERMEDIATE_SELECTION:
					searchType = QuickSearchType.INTERMEDIATE;
					break;
				case HOME_POINT_SELECTION:
					searchType = QuickSearchType.HOME_POINT;
					break;
				case WORK_POINT_SELECTION:
					searchType = QuickSearchType.WORK_POINT;
					break;
			}
			if (searchType != null) {
				QuickSearchDialogFragment.showInstance(this, searchQuery, null,
						searchType, showCategories ? QuickSearchTab.CATEGORIES : QuickSearchTab.ADDRESS, searchLocation);
			}
		} else if (fragment != null) {
			if (mode == ShowQuickSearchMode.NEW
					|| (mode == ShowQuickSearchMode.NEW_IF_EXPIRED && fragment.isExpired())) {
				fragment.dismiss();
				QuickSearchDialogFragment.showInstance(this, searchQuery, null,
						QuickSearchType.REGULAR, showCategories ? QuickSearchTab.CATEGORIES : QuickSearchTab.HISTORY, searchLocation);
			} else {
				fragment.show();
			}
			refreshMap();
		} else {
			QuickSearchDialogFragment.showInstance(this, searchQuery, null,
					QuickSearchType.REGULAR, showCategories ? QuickSearchTab.CATEGORIES : QuickSearchTab.HISTORY, searchLocation);
		}
	}

	public void showQuickSearch(@NonNull ShowQuickSearchMode mode, QuickSearchTab showSearchTab,
								@NonNull String searchQuery, @Nullable LatLon searchLocation) {
		if (mode == ShowQuickSearchMode.CURRENT) {
			mapContextMenu.close();
		} else {
			hideVisibleMenu();
		}
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (mode.isPointSelection()) {
			if (fragment != null) {
				fragment.dismiss();
			}
			QuickSearchType searchType = null;
			switch (mode) {
				case START_POINT_SELECTION:
					searchType = QuickSearchType.START_POINT;
					break;
				case DESTINATION_SELECTION:
				case DESTINATION_SELECTION_AND_START:
					searchType = QuickSearchType.DESTINATION;
					break;
				case INTERMEDIATE_SELECTION:
					searchType = QuickSearchType.INTERMEDIATE;
					break;
				case HOME_POINT_SELECTION:
					searchType = QuickSearchType.HOME_POINT;
					break;
				case WORK_POINT_SELECTION:
					searchType = QuickSearchType.WORK_POINT;
					break;
			}
			QuickSearchDialogFragment.showInstance(this, searchQuery, null,
					searchType, showSearchTab, searchLocation);
		} else if (fragment != null) {
			if (mode == ShowQuickSearchMode.NEW
					|| (mode == ShowQuickSearchMode.NEW_IF_EXPIRED && fragment.isExpired())) {
				fragment.dismiss();
				QuickSearchDialogFragment.showInstance(this, searchQuery, null,
						QuickSearchType.REGULAR, showSearchTab, searchLocation);
			} else {
				fragment.show();
			}
			refreshMap();
		} else {
			QuickSearchDialogFragment.showInstance(this, searchQuery, null,
					QuickSearchType.REGULAR, showSearchTab, searchLocation);
		}
	}

	public void showSettings() {
		dismissSettingsScreens();
		BaseSettingsFragment.showInstance(this, SettingsScreenType.MAIN_SETTINGS);
	}

	private void hideVisibleMenu() {
		if (mapContextMenu.isVisible()) {
			mapContextMenu.hide();
		} else if (mapContextMenu.getMultiSelectionMenu().isVisible()) {
			mapContextMenu.getMultiSelectionMenu().hide();
		} else if (getTrackMenuFragment() != null) {
			dismissTrackMenu();
		}
	}

	public void closeQuickSearch() {
		QuickSearchDialogFragment fragment = getQuickSearchDialogFragment();
		if (fragment != null) {
			fragment.closeSearch();
			refreshMap();
		}
	}

	public QuickSearchDialogFragment getQuickSearchDialogFragment() {
		return getFragment(QuickSearchDialogFragment.TAG);
	}

	public PlanRouteFragment getPlanRouteFragment() {
		return getFragment(PlanRouteFragment.TAG);
	}

	public MeasurementToolFragment getMeasurementToolFragment() {
		return getFragment(MeasurementToolFragment.TAG);
	}

	public TripRecordingStartingBottomSheet getTripRecordingBottomSheet() {
		return getFragment(TripRecordingStartingBottomSheet.TAG);
	}

	public ChooseRouteFragment getChooseRouteFragment() {
		return getFragment(ChooseRouteFragment.TAG);
	}

	public GpxApproximationFragment getGpxApproximationFragment() {
		return getFragment(GpxApproximationFragment.TAG);
	}

	public LoginBottomSheetFragment getLoginBottomSheetFragment() {
		return getFragment(LoginBottomSheetFragment.TAG);
	}

	public SnapTrackWarningFragment getSnapTrackWarningBottomSheet() {
		return getFragment(SnapTrackWarningFragment.TAG);
	}

	public TrackMenuFragment getTrackMenuFragment() {
		return getFragment(TrackMenuFragment.TAG);
	}

	public RouteLineAppearanceFragment getRouteLineAppearanceFragment() {
		return getFragment(RouteLineAppearanceFragment.TAG);
	}

	public void dismissTrackMenu() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			fragmentManager.popBackStack(TrackMenuFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		}
	}

	public void backToConfigureProfileFragment() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		int backStackEntryCount = fragmentManager.getBackStackEntryCount();
		if (backStackEntryCount > 0 && !fragmentManager.isStateSaved()) {
			BackStackEntry entry = fragmentManager.getBackStackEntryAt(backStackEntryCount - 1);
			if (ConfigureProfileFragment.TAG.equals(entry.getName())) {
				fragmentManager.popBackStack();
			}
		}
	}

	<T> T getFragment(String fragmentTag) {
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(fragmentTag);
		return fragment != null && !fragment.isDetached() && !fragment.isRemoving() ? (T) fragment : null;
	}

	public boolean isTopToolbarActive() {
		MapInfoLayer mapInfoLayer = getMapLayers().getMapInfoLayer();
		return mapInfoLayer.hasTopToolbar();
	}

	public TopToolbarController getTopToolbarController(@NonNull TopToolbarControllerType type) {
		MapInfoLayer mapInfoLayer = getMapLayers().getMapInfoLayer();
		return mapInfoLayer.getTopToolbarController(type);
	}

	public void showTopToolbar(@NonNull TopToolbarController controller) {
		MapInfoLayer mapInfoLayer = getMapLayers().getMapInfoLayer();
		mapInfoLayer.addTopToolbarController(controller);
		updateStatusBarColor();
	}

	public void hideTopToolbar(@NonNull TopToolbarController controller) {
		MapInfoLayer mapInfoLayer = getMapLayers().getMapInfoLayer();
		mapInfoLayer.removeTopToolbarController(controller);
		updateStatusBarColor();
	}

	public void hideTopToolbar(@NonNull TopToolbarControllerType type) {
		TopToolbarController controller = getTopToolbarController(type);
		if (controller != null) {
			hideTopToolbar(controller);
		}
	}

	public void registerActivityResultListener(ActivityResultListener listener) {
		activityResultListeners.add(listener);
	}

	public void removeActivityResultListener(ActivityResultListener listener) {
		activityResultListeners.remove(listener);
	}

	@Override
	public void onOsmAndSettingsCustomized() {
		if (stopped) {
			activityRestartNeeded = true;
		} else {
			recreate();
		}
	}

	@Override
	public void onInAppPurchaseGetItems() {
		DiscountHelper.checkAndDisplay(this);
		if (!isFirstScreenShowing() && OsmLiveGoneDialog.shouldShowDialog(app)) {
			OsmLiveGoneDialog.showInstance(app, getSupportFragmentManager());
		}
	}

	public enum ShowQuickSearchMode {
		NEW,
		NEW_IF_EXPIRED,
		CURRENT,
		START_POINT_SELECTION,
		DESTINATION_SELECTION,
		DESTINATION_SELECTION_AND_START,
		INTERMEDIATE_SELECTION,
		HOME_POINT_SELECTION,
		WORK_POINT_SELECTION;

		public boolean isPointSelection() {
			return this != NEW && this != NEW_IF_EXPIRED && this != CURRENT;
		}
	}
}