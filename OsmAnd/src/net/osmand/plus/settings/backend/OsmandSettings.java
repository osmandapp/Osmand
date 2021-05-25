package net.osmand.plus.settings.backend;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import net.osmand.FileUtils;
import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.ValueHolder;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.map.ITileSource;
import net.osmand.map.TileSourceManager;
import net.osmand.map.TileSourceManager.TileSourceTemplate;
import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.io.NetworkUtils;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.SQLiteTileSource;
import net.osmand.plus.access.AccessibilityMode;
import net.osmand.plus.access.RelativeDirectionStyle;
import net.osmand.plus.api.SettingsAPI;
import net.osmand.plus.api.SettingsAPI.SettingsEditor;
import net.osmand.plus.api.SettingsAPIImpl;
import net.osmand.plus.audionotes.NotesSortByMode;
import net.osmand.plus.helpers.AvoidSpecificRoads.AvoidRoadInfo;
import net.osmand.plus.helpers.RateUsHelper.RateUsState;
import net.osmand.plus.helpers.SearchHistoryHelper;
import net.osmand.plus.helpers.enums.AngularConstants;
import net.osmand.plus.helpers.enums.AutoZoomMap;
import net.osmand.plus.helpers.enums.DayNightMode;
import net.osmand.plus.helpers.enums.DrivingRegion;
import net.osmand.plus.helpers.enums.MetricsConstants;
import net.osmand.plus.helpers.enums.SpeedConstants;
import net.osmand.plus.helpers.enums.TracksSortByMode;
import net.osmand.plus.mapmarkers.CoordinateInputFormats.Format;
import net.osmand.plus.mapmarkers.MapMarkersMode;
import net.osmand.plus.osmedit.OsmEditingPlugin.UploadVisibility;
import net.osmand.plus.profiles.LocationIcon;
import net.osmand.plus.profiles.NavigationIcon;
import net.osmand.plus.profiles.ProfileIconColors;
import net.osmand.plus.rastermaps.LayerTransparencySeekbarMode;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.routing.RouteService;
import net.osmand.plus.srtmplugin.TerrainMode;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.plus.views.layers.RadiusRulerControlLayer.RadiusRulerMode;
import net.osmand.plus.voice.CommandPlayer;
import net.osmand.plus.wikipedia.WikiArticleShowImages;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static net.osmand.aidlapi.OsmAndCustomizationConstants.CONFIGURE_MAP_ITEM_ID_SCHEME;
import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_ITEM_ID_SCHEME;
import static net.osmand.aidlapi.OsmAndCustomizationConstants.MAP_CONTEXT_MENU_ACTIONS;
import static net.osmand.plus.routing.TransportRoutingHelper.PUBLIC_TRANSPORT_KEY;

public class OsmandSettings {

	private static final Log LOG = PlatformUtil.getLog(OsmandSettings.class.getName());

	public static final int VERSION = 1;

	// These settings are stored in SharedPreferences
	private static final String CUSTOM_SHARED_PREFERENCES_PREFIX = "net.osmand.customsettings.";
	private static final String SHARED_PREFERENCES_NAME = "net.osmand.settings";
	private static String CUSTOM_SHARED_PREFERENCES_NAME;

	private static final String RENDERER_PREFERENCE_PREFIX = "nrenderer_";
	public static final String ROUTING_PREFERENCE_PREFIX = "prouting_";

	/// Settings variables
	private final OsmandApplication ctx;
	private SettingsAPI settingsAPI;
	private Object globalPreferences;
	private Object profilePreferences;
	private ApplicationMode currentMode;
	private Map<String, OsmandPreference<?>> registeredPreferences = new LinkedHashMap<>();

	// cache variables
	private long lastTimeInternetConnectionChecked = 0;
	private boolean internetConnectionAvailable = true;

	// TODO variable
	private Map<String, CommonPreference<String>> customRoutingProps = new LinkedHashMap<String, CommonPreference<String>>();
	private Map<String, CommonPreference<String>> customRendersProps = new LinkedHashMap<String, CommonPreference<String>>();
	private Map<String, CommonPreference<Boolean>> customBooleanRoutingProps = new LinkedHashMap<String, CommonPreference<Boolean>>();
	private Map<String, CommonPreference<Boolean>> customBooleanRendersProps = new LinkedHashMap<String, CommonPreference<Boolean>>();

	private ImpassableRoadsStorage impassableRoadsStorage = new ImpassableRoadsStorage(this);
	private IntermediatePointsStorage intermediatePointsStorage = new IntermediatePointsStorage(this);

	private Object objectToShow;
	private boolean editObjectToShow;
	private String searchRequestToShow;

	protected OsmandSettings(OsmandApplication clientContext, SettingsAPI settinsAPI) {
		ctx = clientContext;
		this.settingsAPI = settinsAPI;
		initPrefs();
	}

	protected OsmandSettings(OsmandApplication clientContext, SettingsAPI settinsAPI, String sharedPreferencesName) {
		ctx = clientContext;
		this.settingsAPI = settinsAPI;
		CUSTOM_SHARED_PREFERENCES_NAME = CUSTOM_SHARED_PREFERENCES_PREFIX + sharedPreferencesName;
		initPrefs();
		setCustomized();
	}

	private void initPrefs() {
		globalPreferences = settingsAPI.getPreferenceObject(getSharedPreferencesName(null));
		currentMode = readApplicationMode();
		profilePreferences = getProfilePreferences(currentMode);
		registeredPreferences.put(APPLICATION_MODE.getId(), APPLICATION_MODE);
	}

	public Map<String, OsmandPreference<?>> getRegisteredPreferences() {
		return Collections.unmodifiableMap(registeredPreferences);
	}

	public static boolean isRendererPreference(String key) {
		return key.startsWith(RENDERER_PREFERENCE_PREFIX);
	}

	public static boolean isRoutingPreference(String key) {
		return key.startsWith(ROUTING_PREFERENCE_PREFIX);
	}

	private static final String SETTING_CUSTOMIZED_ID = "settings_customized";

	private void setCustomized() {
		settingsAPI.edit(globalPreferences).putBoolean(SETTING_CUSTOMIZED_ID, true).commit();
	}

	public OsmandApplication getContext() {
		return ctx;
	}

	public void setSettingsAPI(SettingsAPI settingsAPI) {
		this.settingsAPI = settingsAPI;
		initPrefs();
	}

	public SettingsAPI getSettingsAPI() {
		return settingsAPI;
	}

	public OsmAndPreferencesDataStore getDataStore(@Nullable ApplicationMode appMode) {
		return new OsmAndPreferencesDataStore(this, appMode != null ? appMode : APPLICATION_MODE.get());
	}

	public static String getSharedPreferencesName(ApplicationMode mode) {
		String modeKey = mode != null ? mode.getStringKey() : null;
		return getSharedPreferencesNameForKey(modeKey);
	}

	public static String getSharedPreferencesNameForKey(String modeKey) {
		String sharedPreferencesName = !Algorithms.isEmpty(CUSTOM_SHARED_PREFERENCES_NAME) ? CUSTOM_SHARED_PREFERENCES_NAME : SHARED_PREFERENCES_NAME;
		if (modeKey == null) {
			return sharedPreferencesName;
		} else {
			return sharedPreferencesName + "." + modeKey.toLowerCase();
		}
	}

	public static boolean areSettingsCustomizedForPreference(String sharedPreferencesName, OsmandApplication app) {
		String customPrefName = CUSTOM_SHARED_PREFERENCES_PREFIX + sharedPreferencesName;
		SettingsAPIImpl settingsAPI = new net.osmand.plus.api.SettingsAPIImpl(app);
		SharedPreferences globalPreferences = (SharedPreferences) settingsAPI.getPreferenceObject(customPrefName);

		return globalPreferences != null && globalPreferences.getBoolean(SETTING_CUSTOMIZED_ID, false);
	}

	// TODO doesn't look correct package visibility
	public Object getProfilePreferences(ApplicationMode mode) {
		return settingsAPI.getPreferenceObject(getSharedPreferencesName(mode));
	}

	// TODO doesn't look correct package visibility
	Object getProfilePreferences(String modeKey) {
		return settingsAPI.getPreferenceObject(getSharedPreferencesNameForKey(modeKey));
	}

	public OsmandPreference<?> getPreference(String key) {
		return registeredPreferences.get(key);
	}

	// TODO doesn't look correct
	public void setPreferenceForAllModes(String key, Object value) {
		for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
			setPreference(key, value, mode);
		}
	}

	// TODO doesn't look correct
	public boolean setPreference(String key, Object value) {
		return setPreference(key, value, APPLICATION_MODE.get());
	}

	// TODO doesn't look correct
	@SuppressWarnings("unchecked")
	public boolean setPreference(String key, Object value, ApplicationMode mode) {
		OsmandPreference<?> preference = registeredPreferences.get(key);
		if (preference != null) {
			if (preference == APPLICATION_MODE) {
				if (value instanceof String) {
					String appModeKey = (String) value;
					ApplicationMode appMode = ApplicationMode.valueOfStringKey(appModeKey, null);
					if (appMode != null) {
						setApplicationMode(appMode);
						return true;
					}
				}
			} else if (preference == DEFAULT_APPLICATION_MODE) {
				if (value instanceof String) {
					String appModeKey = (String) value;
					ApplicationMode appMode = ApplicationMode.valueOfStringKey(appModeKey, null);
					if (appMode != null) {
						DEFAULT_APPLICATION_MODE.set(appMode);
						return true;
					}
				}
			} else if (preference == METRIC_SYSTEM) {
				MetricsConstants metricSystem = null;
				if (value instanceof String) {
					String metricSystemName = (String) value;
					try {
						metricSystem = MetricsConstants.valueOf(metricSystemName);
					} catch (IllegalArgumentException e) {
						return false;
					}
				} else if (value instanceof Integer) {
					int index = (Integer) value;
					if (index >= 0 && index < MetricsConstants.values().length) {
						metricSystem = MetricsConstants.values()[index];
					}
				}
				if (metricSystem != null) {
					METRIC_SYSTEM.setModeValue(mode, metricSystem);
					return true;
				}
			} else if (preference == SPEED_SYSTEM) {
				SpeedConstants speedSystem = null;
				if (value instanceof String) {
					String speedSystemName = (String) value;
					try {
						speedSystem = SpeedConstants.valueOf(speedSystemName);
					} catch (IllegalArgumentException e) {
						return false;
					}
				} else if (value instanceof Integer) {
					int index = (Integer) value;
					if (index >= 0 && index < SpeedConstants.values().length) {
						speedSystem = SpeedConstants.values()[index];
					}
				}
				if (speedSystem != null) {
					SPEED_SYSTEM.setModeValue(mode, speedSystem);
					return true;
				}
			} else if (preference instanceof BooleanPreference) {
				if (value instanceof Boolean) {
					((BooleanPreference) preference).setModeValue(mode, (Boolean) value);
					return true;
				}
			} else if (preference instanceof StringPreference) {
				if (value instanceof String) {
					((StringPreference) preference).setModeValue(mode, (String) value);
					return true;
				}
			} else if (preference instanceof FloatPreference) {
				if (value instanceof Float) {
					((FloatPreference) preference).setModeValue(mode, (Float) value);
					return true;
				}
			} else if (preference instanceof IntPreference) {
				if (value instanceof Integer) {
					((IntPreference) preference).setModeValue(mode, (Integer) value);
					return true;
				}
			} else if (preference instanceof LongPreference) {
				if (value instanceof Long) {
					((LongPreference) preference).setModeValue(mode, (Long) value);
					return true;
				}
			} else if (preference instanceof EnumStringPreference) {
				EnumStringPreference enumPref = (EnumStringPreference) preference;
				if (value instanceof String) {
					Enum<?> enumValue = enumPref.parseString((String) value);
					if (enumValue != null) {
						return enumPref.setModeValue(mode, enumValue);
					}
					return false;
				} else if (value instanceof Enum) {
					return enumPref.setModeValue(mode, value);
				} else if (value instanceof Integer) {
					int newVal = (Integer) value;
					if (enumPref.getValues().length > newVal) {
						Enum<?> enumValue = enumPref.getValues()[newVal];
						return enumPref.setModeValue(mode, enumValue);
					}
					return false;
				}
			} else if (preference instanceof ContextMenuItemsPreference) {
				if (value instanceof ContextMenuItemsSettings) {
					((ContextMenuItemsPreference) preference).setModeValue(mode, (ContextMenuItemsSettings) value);
				}
			}
		}
		return false;
	}

	public void copyPreferencesFromProfile(ApplicationMode modeFrom, ApplicationMode modeTo) {
		copyProfilePreferences(modeFrom, modeTo, new ArrayList<OsmandPreference>(registeredPreferences.values()));
	}

	public void copyProfilePreferences(ApplicationMode modeFrom, ApplicationMode modeTo, List<OsmandPreference> profilePreferences) {
		for (OsmandPreference pref : profilePreferences) {
			if (prefCanBeCopiedOrReset(pref) && !USER_PROFILE_NAME.getId().equals(pref.getId())) {
				CommonPreference profilePref = (CommonPreference) pref;
				if (PARENT_APP_MODE.getId().equals(pref.getId())) {
					if (modeTo.isCustomProfile()) {
						modeTo.setParentAppMode(modeFrom.isCustomProfile() ? modeFrom.getParent() : modeFrom);
					}
				} else {
					Object copiedValue = profilePref.getModeValue(modeFrom);
					profilePref.setModeValue(modeTo, copiedValue);
				}
			}
		}
	}

	public void resetPreferencesForProfile(ApplicationMode mode) {
		resetProfilePreferences(mode, new ArrayList<OsmandPreference>(registeredPreferences.values()));
	}

	public void resetProfilePreferences(ApplicationMode mode, List<OsmandPreference> profilePreferences) {
		for (OsmandPreference pref : profilePreferences) {
			if (prefCanBeCopiedOrReset(pref)) {
				pref.resetModeToDefault(mode);
			}
		}
	}

	private boolean prefCanBeCopiedOrReset(OsmandPreference pref) {
		return pref instanceof CommonPreference && !((CommonPreference) pref).isGlobal()
				&& !APP_MODE_ORDER.getId().equals(pref.getId());
	}

	public ApplicationMode LAST_ROUTING_APPLICATION_MODE = null;

	public boolean setApplicationMode(ApplicationMode appMode) {
		return setApplicationMode(appMode, true);
	}

	public boolean setApplicationMode(ApplicationMode appMode, boolean markAsLastUsed) {
		boolean valueSaved = APPLICATION_MODE.set(appMode);
		if (markAsLastUsed && valueSaved) {
			LAST_USED_APPLICATION_MODE.set(appMode.getStringKey());
		}
		return valueSaved;
	}

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<ApplicationMode> APPLICATION_MODE = new PreferenceWithListener<ApplicationMode>() {

		@Override
		public String getId() {
			return "application_mode";
		}

		@Override
		public ApplicationMode get() {
			return currentMode;
		}

		@Override
		public void overrideDefaultValue(ApplicationMode newDefaultValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void resetToDefault() {
			set(ApplicationMode.DEFAULT);
		}

		@Override
		public void resetModeToDefault(ApplicationMode m) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isSet() {
			return true;
		}

		@Override
		public boolean isSetForMode(ApplicationMode m) {
			return true;
		}

		@Override
		public boolean set(ApplicationMode val) {
			ApplicationMode oldMode = currentMode;
			boolean valueSaved = settingsAPI.edit(globalPreferences).putString(getId(), val.getStringKey()).commit();
			if (valueSaved) {
				currentMode = val;
				profilePreferences = getProfilePreferences(currentMode);

				fireEvent(oldMode);
			}
			return valueSaved;
		}

		@Override
		public ApplicationMode getModeValue(ApplicationMode m) {
			return m;
		}

		@Override
		public boolean setModeValue(ApplicationMode m, ApplicationMode obj) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean writeToJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				return writeAppModeToJson(json, this);
			} else {
				return true;
			}
		}

		@Override
		public void readFromJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				readAppModeFromJson(json, this);
			}
		}

		@Override
		public String asString() {
			return appModeToString(get());
		}

		@Override
		public String asStringModeValue(ApplicationMode m) {
			return appModeToString(m);
		}

		@Override
		public ApplicationMode parseString(String s) {
			return appModeFromString(s);
		}
	};

	private String appModeToString(ApplicationMode appMode) {
		return appMode.getStringKey();
	}

	private ApplicationMode appModeFromString(String s) {
		return ApplicationMode.valueOfStringKey(s, ApplicationMode.DEFAULT);
	}

	private boolean writeAppModeToJson(JSONObject json, OsmandPreference<ApplicationMode> appModePref) throws JSONException {
		json.put(appModePref.getId(), appModePref.asString());
		return true;
	}

	private void readAppModeFromJson(JSONObject json, OsmandPreference<ApplicationMode> appModePref) throws JSONException {
		String s = json.getString(appModePref.getId());
		if (s != null) {
			appModePref.set(appModePref.parseString(s));
		}
	}

	public ApplicationMode getApplicationMode() {
		return APPLICATION_MODE.get();
	}

	public boolean hasAvailableApplicationMode() {
		int currentModeCount = ApplicationMode.values(ctx).size();
		if (currentModeCount == 0 || currentModeCount == 1 && getApplicationMode() == ApplicationMode.DEFAULT) {
			return false;
		}
		return true;
	}

	public ApplicationMode readApplicationMode() {
		String s = settingsAPI.getString(globalPreferences, APPLICATION_MODE.getId(), ApplicationMode.DEFAULT.getStringKey());
		return ApplicationMode.valueOfStringKey(s, ApplicationMode.DEFAULT);
	}


	// Check internet connection available every 15 seconds
	public boolean isInternetConnectionAvailable() {
		return isInternetConnectionAvailable(false);
	}

	public boolean isInternetConnectionAvailable(boolean update) {
		long delta = System.currentTimeMillis() - lastTimeInternetConnectionChecked;
		if (delta < 0 || delta > 15000 || update) {
			internetConnectionAvailable = isInternetConnected();
		}
		return internetConnectionAvailable;
	}

	public boolean isWifiConnected() {
		try {
			ConnectivityManager mgr = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo ni = mgr.getActiveNetworkInfo();
			return ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isInternetConnected() {
		try {
			ConnectivityManager mgr = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo active = mgr.getActiveNetworkInfo();
			if (active == null) {
				return false;
			} else {
				NetworkInfo.State state = active.getState();
				return state != NetworkInfo.State.DISCONNECTED && state != NetworkInfo.State.DISCONNECTING;
			}
		} catch (Exception e) {
			return false;
		}
	}

	<T> void registerInternalPreference(String id, CommonPreference<T> tCommonPreference) {
		registeredPreferences.put(id, tCommonPreference);
	}

	boolean isSet(boolean global, String id) {
		return settingsAPI.contains(getPreferences(global), id);
	}

	boolean isSet(ApplicationMode m, String id) {
		return settingsAPI.contains(getProfilePreferences(m), id);
	}

	Object getPreferences(boolean global) {
		return global ? globalPreferences : profilePreferences;
	}


	@SuppressWarnings("unchecked")
	public CommonPreference<Boolean> registerBooleanPreference(String id, boolean defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<Boolean>) registeredPreferences.get(id);
		}
		BooleanPreference p = new BooleanPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public CommonPreference<Boolean> registerBooleanAccessibilityPreference(String id, boolean defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<Boolean>) registeredPreferences.get(id);
		}
		BooleanPreference p = new BooleanAccessibilityPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public CommonPreference<String> registerStringPreference(String id, String defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<String>) registeredPreferences.get(id);
		}
		StringPreference p = new StringPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public CommonPreference<Integer> registerIntPreference(String id, int defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<Integer>) registeredPreferences.get(id);
		}
		IntPreference p = new IntPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public CommonPreference<Long> registerLongPreference(String id, long defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<Long>) registeredPreferences.get(id);
		}
		LongPreference p = new LongPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public CommonPreference<Float> registerFloatPreference(String id, float defValue) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<Float>) registeredPreferences.get(id);
		}
		FloatPreference p = new FloatPreference(this, id, defValue);
		registeredPreferences.put(id, p);
		return p;
	}

	@SuppressWarnings("unchecked")
	public <T extends Enum> CommonPreference<T> registerEnumIntPreference(String id, Enum defaultValue, Enum[] values, Class<T> clz) {
		if (registeredPreferences.containsKey(id)) {
			return (CommonPreference<T>) registeredPreferences.get(id);
		}
		EnumStringPreference p = new EnumStringPreference(this, id, defaultValue, values);
		registeredPreferences.put(id, p);
		return p;
	}

	///////////////////// PREFERENCES ////////////////

	public static final String NUMBER_OF_FREE_DOWNLOADS_ID = "free_downloads_v3";

	// this value string is synchronized with settings_pref.xml preference name
	private final OsmandPreference<String> PLUGINS = new StringPreference(this, "enabled_plugins", "").makeGlobal().makeShared();

	public Set<String> getEnabledPlugins() {
		String plugs = PLUGINS.get();
		StringTokenizer toks = new StringTokenizer(plugs, ",");
		Set<String> res = new LinkedHashSet<String>();
		while (toks.hasMoreTokens()) {
			String tok = toks.nextToken();
			if (!tok.startsWith("-")) {
				res.add(tok);
			}
		}
		return res;
	}

	public Set<String> getPlugins() {
		String plugs = PLUGINS.get();
		StringTokenizer toks = new StringTokenizer(plugs, ",");
		Set<String> res = new LinkedHashSet<String>();
		while (toks.hasMoreTokens()) {
			res.add(toks.nextToken());
		}
		return res;
	}

	public boolean enablePlugin(String pluginId, boolean enable) {
		Set<String> set = getPlugins();
		if (enable) {
			set.remove("-" + pluginId);
			set.add(pluginId);
		} else {
			set.remove(pluginId);
			set.add("-" + pluginId);
		}
		StringBuilder serialization = new StringBuilder();
		Iterator<String> it = set.iterator();
		while (it.hasNext()) {
			serialization.append(it.next());
			if (it.hasNext()) {
				serialization.append(",");
			}
		}
		if (!serialization.toString().equals(PLUGINS.get())) {
			return PLUGINS.set(serialization.toString());
		}
		return false;
	}

	public final CommonPreference<RadiusRulerMode> RADIUS_RULER_MODE = new EnumStringPreference<>(this, "ruler_mode", RadiusRulerMode.FIRST, RadiusRulerMode.values()).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> SHOW_COMPASS_CONTROL_RULER = new BooleanPreference(this, "show_compass_ruler", true).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> SHOW_DISTANCE_RULER = new BooleanPreference(this, "show_distance_ruler", false).makeProfile();
	public final OsmandPreference<Boolean> SHOW_ELEVATION_PROFILE_WIDGET = new BooleanPreference(this, "show_elevation_profile_widget", false).makeProfile();
	public final OsmandPreference<Boolean> SHOW_SLOPES_ON_ELEVATION_WIDGET = new BooleanPreference(this, "show_slopes_on_elevation_widget", false).makeProfile();

	public final CommonPreference<Boolean> SHOW_LINES_TO_FIRST_MARKERS = new BooleanPreference(this, "show_lines_to_first_markers", false).makeProfile();
	public final CommonPreference<Boolean> SHOW_ARROWS_TO_FIRST_MARKERS = new BooleanPreference(this, "show_arrows_to_first_markers", false).makeProfile();

	public final CommonPreference<Boolean> WIKI_ARTICLE_SHOW_IMAGES_ASKED = new BooleanPreference(this, "wikivoyage_show_images_asked", false).makeGlobal();
	public final CommonPreference<WikiArticleShowImages> WIKI_ARTICLE_SHOW_IMAGES = new EnumStringPreference<>(this, "wikivoyage_show_imgs", WikiArticleShowImages.OFF, WikiArticleShowImages.values()).makeGlobal().makeShared();
	public final CommonPreference<Boolean> GLOBAL_WIKIPEDIA_POI_ENABLED = new BooleanPreference(this, "global_wikipedia_poi_enabled", false).makeProfile();
	public final ListStringPreference WIKIPEDIA_POI_ENABLED_LANGUAGES = (ListStringPreference) new ListStringPreference(this, "wikipedia_poi_enabled_languages", null, ",").makeProfile().cache();

	public final CommonPreference<Boolean> SELECT_MARKER_ON_SINGLE_TAP = new BooleanPreference(this, "select_marker_on_single_tap", false).makeProfile();
	public final CommonPreference<Boolean> KEEP_PASSED_MARKERS_ON_MAP = new BooleanPreference(this, "keep_passed_markers_on_map", true).makeProfile();

	public final CommonPreference<Boolean> COORDS_INPUT_USE_RIGHT_SIDE = new BooleanPreference(this, "coords_input_use_right_side", true).makeGlobal().makeShared();
	public final OsmandPreference<Format> COORDS_INPUT_FORMAT = new EnumStringPreference<>(this, "coords_input_format", Format.DD_MM_MMM, Format.values()).makeGlobal().makeShared();
	public final CommonPreference<Boolean> COORDS_INPUT_USE_OSMAND_KEYBOARD = new BooleanPreference(this, "coords_input_use_osmand_keyboard", Build.VERSION.SDK_INT >= 16).makeGlobal().makeShared();
	public final CommonPreference<Boolean> COORDS_INPUT_TWO_DIGITS_LONGTITUDE = new BooleanPreference(this, "coords_input_two_digits_longitude", false).makeGlobal().makeShared();

	public final CommonPreference<Boolean> USE_MAPILLARY_FILTER = new BooleanPreference(this, "use_mapillary_filters", false).makeGlobal().makeShared();
	public final CommonPreference<String> MAPILLARY_FILTER_USER_KEY = new StringPreference(this, "mapillary_filter_user_key", "").makeGlobal().makeShared();
	public final CommonPreference<String> MAPILLARY_FILTER_USERNAME = new StringPreference(this, "mapillary_filter_username", "").makeGlobal().makeShared();
	public final CommonPreference<Long> MAPILLARY_FILTER_FROM_DATE = new LongPreference(this, "mapillary_filter_from_date", 0).makeGlobal().makeShared();
	public final CommonPreference<Long> MAPILLARY_FILTER_TO_DATE = new LongPreference(this, "mapillary_filter_to_date", 0).makeGlobal().makeShared();
	public final CommonPreference<Boolean> MAPILLARY_FILTER_PANO = new BooleanPreference(this, "mapillary_filter_pano", false).makeGlobal().makeShared();

	public final CommonPreference<Boolean> USE_FAST_RECALCULATION = new BooleanPreference(this, "use_fast_recalculation", true).makeProfile().cache();
	public final CommonPreference<Boolean> FORCE_PRIVATE_ACCESS_ROUTING_ASKED = new BooleanPreference(this, "force_private_access_routing", false).makeProfile().cache();

	public final CommonPreference<Boolean> SHOW_CARD_TO_CHOOSE_DRAWER = new BooleanPreference(this, "show_card_to_choose_drawer", false).makeGlobal().makeShared();
	public final CommonPreference<Boolean> SHOW_DASHBOARD_ON_START = new BooleanPreference(this, "should_show_dashboard_on_start", false).makeGlobal().makeShared();
	public final CommonPreference<Boolean> SHOW_DASHBOARD_ON_MAP_SCREEN = new BooleanPreference(this, "show_dashboard_on_map_screen", false).makeGlobal().makeShared();
	public final CommonPreference<Boolean> SHOW_OSMAND_WELCOME_SCREEN = new BooleanPreference(this, "show_osmand_welcome_screen", true).makeGlobal();

	public final CommonPreference<String> API_NAV_DRAWER_ITEMS_JSON = new StringPreference(this, "api_nav_drawer_items_json", "{}").makeGlobal().makeShared();
	public final CommonPreference<String> API_CONNECTED_APPS_JSON = new StringPreference(this, "api_connected_apps_json", "[]").makeGlobal().makeShared();
	public final CommonPreference<String> NAV_DRAWER_LOGO = new StringPreference(this, "drawer_logo", "").makeProfile();
	public final CommonPreference<String> NAV_DRAWER_URL = new StringPreference(this, "drawer_url", "").makeProfile();

	public final CommonPreference<Integer> NUMBER_OF_STARTS_FIRST_XMAS_SHOWN = new IntPreference(this, "number_of_starts_first_xmas_shown", 0).makeGlobal();

	public final OsmandPreference<String> AVAILABLE_APP_MODES = new StringPreference(this, "available_application_modes", "car,bicycle,pedestrian,public_transport,").makeGlobal().makeShared().cache();

	public final OsmandPreference<String> LAST_FAV_CATEGORY_ENTERED = new StringPreference(this, "last_fav_category", "").makeGlobal();

	public final OsmandPreference<Boolean> USE_LAST_APPLICATION_MODE_BY_DEFAULT = new BooleanPreference(this, "use_last_application_mode_by_default", false).makeGlobal().makeShared();

	public final OsmandPreference<String> LAST_USED_APPLICATION_MODE = new StringPreference(this, "last_used_application_mode", ApplicationMode.DEFAULT.getStringKey()).makeGlobal().makeShared();

	public final OsmandPreference<ApplicationMode> DEFAULT_APPLICATION_MODE = new CommonPreference<ApplicationMode>(this, "default_application_mode_string", ApplicationMode.DEFAULT) {

		@Override
		protected ApplicationMode getValue(Object prefs, ApplicationMode defaultValue) {
			String key;
			if (USE_LAST_APPLICATION_MODE_BY_DEFAULT.get()) {
				key = LAST_USED_APPLICATION_MODE.get();
			} else {
				key = settingsAPI.getString(prefs, getId(), defaultValue.getStringKey());
			}
			return ApplicationMode.valueOfStringKey(key, defaultValue);
		}

		@Override
		protected boolean setValue(Object prefs, ApplicationMode val) {
			boolean valueSaved = settingsAPI.edit(prefs).putString(getId(), val.getStringKey()).commit();
			if (valueSaved) {
				setApplicationMode(val);
			}

			return valueSaved;
		}

		@Override
		public boolean writeToJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				return writeAppModeToJson(json, this);
			} else {
				return true;
			}
		}

		@Override
		public void readFromJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				readAppModeFromJson(json, this);
			}
		}

		@Override
		protected String toString(ApplicationMode o) {
			return appModeToString(o);
		}


		@Override
		public ApplicationMode parseString(String s) {
			return appModeFromString(s);
		}
	}.makeGlobal().makeShared();

	public final OsmandPreference<ApplicationMode> LAST_ROUTE_APPLICATION_MODE = new CommonPreference<ApplicationMode>(this, "last_route_application_mode_backup_string", ApplicationMode.DEFAULT) {

		@Override
		protected ApplicationMode getValue(Object prefs, ApplicationMode defaultValue) {
			String key = settingsAPI.getString(prefs, getId(), defaultValue.getStringKey());
			return ApplicationMode.valueOfStringKey(key, defaultValue);
		}

		@Override
		protected boolean setValue(Object prefs, ApplicationMode val) {
			return settingsAPI.edit(prefs).putString(getId(), val.getStringKey()).commit();
		}

		@Override
		public boolean writeToJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				return writeAppModeToJson(json, this);
			} else {
				return true;
			}
		}

		@Override
		public void readFromJson(JSONObject json, ApplicationMode appMode) throws JSONException {
			if (appMode == null) {
				readAppModeFromJson(json, this);
			}
		}

		@Override
		protected String toString(ApplicationMode o) {
			return appModeToString(o);
		}


		@Override
		public ApplicationMode parseString(String s) {
			return appModeFromString(s);
		}
	}.makeGlobal();

	public final OsmandPreference<Boolean> FIRST_MAP_IS_DOWNLOADED = new BooleanPreference(this, "first_map_is_downloaded", false);

	public final CommonPreference<Boolean> DRIVING_REGION_AUTOMATIC = new BooleanPreference(this, "driving_region_automatic", true).makeProfile().cache();
	public final OsmandPreference<DrivingRegion> DRIVING_REGION = new EnumStringPreference<DrivingRegion>(this,
			"default_driving_region", DrivingRegion.EUROPE_ASIA, DrivingRegion.values()) {
		public boolean setValue(Object prefs, DrivingRegion val) {
			boolean overrideMetricSystem = !DRIVING_REGION_AUTOMATIC.getValue(prefs, DRIVING_REGION_AUTOMATIC.getDefaultValue());
			if (overrideMetricSystem && val != null) {
				METRIC_SYSTEM.setValue(prefs, val.defMetrics);
			}
			return super.setValue(prefs, val);
		}

		protected DrivingRegion getDefaultValue() {
			return DrivingRegion.getDrivingRegionByLocale();
		}

	}.makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	// cache of metrics constants as they are used very often
	public final EnumStringPreference<MetricsConstants> METRIC_SYSTEM = (EnumStringPreference<MetricsConstants>) new EnumStringPreference<MetricsConstants>(this,
			"default_metric_system", MetricsConstants.KILOMETERS_AND_METERS, MetricsConstants.values()) {
		protected MetricsConstants getDefaultValue() {
			return DRIVING_REGION.get().defMetrics;
		}

	}.makeProfile();

	//public final OsmandPreference<Integer> COORDINATES_FORMAT = new IntPreference("coordinates_format", PointDescription.FORMAT_DEGREES).makeGlobal();

	public final OsmandPreference<AngularConstants> ANGULAR_UNITS = new EnumStringPreference<AngularConstants>(this,
			"angular_measurement", AngularConstants.DEGREES, AngularConstants.values()).makeProfile();

	public static final String LAST_START_LAT = "last_searched_lat"; //$NON-NLS-1$
	public static final String LAST_START_LON = "last_searched_lon"; //$NON-NLS-1$

	public LatLon getLastStartPoint() {
		if (settingsAPI.contains(globalPreferences, LAST_START_LAT) && settingsAPI.contains(globalPreferences, LAST_START_LON)) {
			return new LatLon(settingsAPI.getFloat(globalPreferences, LAST_START_LAT, 0),
					settingsAPI.getFloat(globalPreferences, LAST_START_LON, 0));
		}
		return null;
	}

	public boolean setLastStartPoint(LatLon l) {
		if (l == null) {
			return settingsAPI.edit(globalPreferences).remove(LAST_START_LAT).remove(LAST_START_LON).commit();
		} else {
			return setLastStartPoint(l.getLatitude(), l.getLongitude());
		}
	}

	public boolean setLastStartPoint(double lat, double lon) {
		return settingsAPI.edit(globalPreferences).putFloat(LAST_START_LAT, (float) lat).
				putFloat(LAST_START_LON, (float) lon).commit();
	}

	public final OsmandPreference<SpeedConstants> SPEED_SYSTEM = new EnumStringPreference<SpeedConstants>(this,
			"default_speed_system", SpeedConstants.KILOMETERS_PER_HOUR, SpeedConstants.values()) {

		@Override
		public SpeedConstants getProfileDefaultValue(ApplicationMode mode) {
			MetricsConstants mc = METRIC_SYSTEM.getModeValue(mode);
			if (mode.isDerivedRoutingFrom(ApplicationMode.PEDESTRIAN)) {
				if (mc == MetricsConstants.KILOMETERS_AND_METERS) {
					return SpeedConstants.MINUTES_PER_KILOMETER;
				} else {
					return SpeedConstants.MILES_PER_HOUR;
				}
			}
			if (mode.isDerivedRoutingFrom(ApplicationMode.BOAT)) {
				return SpeedConstants.NAUTICALMILES_PER_HOUR;
			}
			if (mc == MetricsConstants.NAUTICAL_MILES) {
				return SpeedConstants.NAUTICALMILES_PER_HOUR;
			} else if (mc == MetricsConstants.KILOMETERS_AND_METERS) {
				return SpeedConstants.KILOMETERS_PER_HOUR;
			} else {
				return SpeedConstants.MILES_PER_HOUR;
			}
		}
	}.makeProfile();


	// this value string is synchronized with settings_pref.xml preference name
	// cache of metrics constants as they are used very often
	public final OsmandPreference<RelativeDirectionStyle> DIRECTION_STYLE = new EnumStringPreference<RelativeDirectionStyle>(this,
			"direction_style", RelativeDirectionStyle.SIDEWISE, RelativeDirectionStyle.values()).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	// cache of metrics constants as they are used very often
	public final OsmandPreference<AccessibilityMode> ACCESSIBILITY_MODE = new EnumStringPreference<AccessibilityMode>(this,
			"accessibility_mode", AccessibilityMode.DEFAULT, AccessibilityMode.values()).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Float> SPEECH_RATE =
			new FloatPreference(this, "speech_rate", 1f).makeProfile();

	public final OsmandPreference<Float> ARRIVAL_DISTANCE_FACTOR =
			new FloatPreference(this, "arrival_distance_factor", 1f).makeProfile();

	public final OsmandPreference<Float> SPEED_LIMIT_EXCEED_KMH =
			new FloatPreference(this, "speed_limit_exceed", 5f).makeProfile();

	public final CommonPreference<Float> DEFAULT_SPEED = new FloatPreference(this, "default_speed", 10f).makeProfile().cache();

	{
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.DEFAULT, 1.5f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.CAR, 12.5f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.BICYCLE, 2.77f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 1.11f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.BOAT, 1.38f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.AIRCRAFT, 200f);
		DEFAULT_SPEED.setModeDefaultValue(ApplicationMode.SKI, 1.38f);
	}

	public final OsmandPreference<Float> MIN_SPEED = new FloatPreference(this,
			"min_speed", 0f).makeProfile().cache();

	public final OsmandPreference<Float> MAX_SPEED = new FloatPreference(this,
			"max_speed", 0f).makeProfile().cache();

	public final CommonPreference<String> ICON_RES_NAME = new StringPreference(this, "app_mode_icon_res_name", "ic_world_globe_dark").makeProfile().cache();

	{
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.DEFAULT, "ic_world_globe_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.CAR, "ic_action_car_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.BICYCLE, "ic_action_bicycle_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.PEDESTRIAN, "ic_action_pedestrian_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.PUBLIC_TRANSPORT, "ic_action_bus_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.BOAT, "ic_action_sail_boat_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.AIRCRAFT, "ic_action_aircraft");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.SKI, "ic_action_skiing");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.TRUCK, "ic_action_truck_dark");
		ICON_RES_NAME.setModeDefaultValue(ApplicationMode.MOTORCYCLE, "ic_action_motorcycle_dark");
	}

	public final CommonPreference<ProfileIconColors> ICON_COLOR = new EnumStringPreference<>(this,
			"app_mode_icon_color", ProfileIconColors.DEFAULT, ProfileIconColors.values()).makeProfile().cache();

	public final ListStringPreference CUSTOM_ICON_COLORS = (ListStringPreference) new ListStringPreference(this, "custom_icon_colors", null, ",").makeProfile().cache();

	public final CommonPreference<String> CUSTOM_ICON_COLOR = new StringPreference(this, "custom_icon_color", null).makeProfile().cache();

	public final CommonPreference<String> USER_PROFILE_NAME = new StringPreference(this, "user_profile_name", "").makeProfile().cache();

	public final CommonPreference<String> PARENT_APP_MODE = new StringPreference(this, "parent_app_mode", null).makeProfile().cache();

	public final CommonPreference<String> ROUTING_PROFILE = new StringPreference(this, "routing_profile", "").makeProfile().cache();

	{
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.CAR, "car");
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.BICYCLE, "bicycle");
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, "pedestrian");
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.PUBLIC_TRANSPORT, PUBLIC_TRANSPORT_KEY);
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.BOAT, "boat");
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.AIRCRAFT, "STRAIGHT_LINE_MODE");
		ROUTING_PROFILE.setModeDefaultValue(ApplicationMode.SKI, "ski");
	}

	public final CommonPreference<RouteService> ROUTE_SERVICE = new EnumStringPreference<RouteService>(this, "route_service", RouteService.OSMAND, RouteService.values()) {
		@Override
		public RouteService getModeValue(ApplicationMode mode) {
			if (mode == ApplicationMode.DEFAULT) {
				return RouteService.STRAIGHT;
			} else {
				return super.getModeValue(mode);
			}
		}
	}.makeProfile().cache();

	{
		ROUTE_SERVICE.setModeDefaultValue(ApplicationMode.DEFAULT, RouteService.STRAIGHT);
		ROUTE_SERVICE.setModeDefaultValue(ApplicationMode.AIRCRAFT, RouteService.STRAIGHT);
	}

	public final CommonPreference<String> ONLINE_ROUTING_ENGINES = new StringPreference(this, "online_routing_engines", null).makeGlobal();

	public final CommonPreference<NavigationIcon> NAVIGATION_ICON = new EnumStringPreference<>(this, "navigation_icon", NavigationIcon.DEFAULT, NavigationIcon.values()).makeProfile().cache();

	{
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.DEFAULT, NavigationIcon.DEFAULT);
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.CAR, NavigationIcon.DEFAULT);
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.BICYCLE, NavigationIcon.DEFAULT);
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.BOAT, NavigationIcon.NAUTICAL);
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.AIRCRAFT, NavigationIcon.DEFAULT);
		NAVIGATION_ICON.setModeDefaultValue(ApplicationMode.SKI, NavigationIcon.DEFAULT);
	}

	public final CommonPreference<LocationIcon> LOCATION_ICON = new EnumStringPreference<>(this, "location_icon", LocationIcon.DEFAULT, LocationIcon.values()).makeProfile().cache();

	{
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.DEFAULT, LocationIcon.DEFAULT);
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.CAR, LocationIcon.CAR);
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.BICYCLE, LocationIcon.BICYCLE);
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.BOAT, LocationIcon.DEFAULT);
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.AIRCRAFT, LocationIcon.CAR);
		LOCATION_ICON.setModeDefaultValue(ApplicationMode.SKI, LocationIcon.BICYCLE);
	}

	public final CommonPreference<Integer> APP_MODE_ORDER = new IntPreference(this, "app_mode_order", 0).makeProfile().cache();

	public final OsmandPreference<Float> SWITCH_MAP_DIRECTION_TO_COMPASS_KMH =
			new FloatPreference(this, "speed_for_map_to_direction_of_movement", 0f).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> USE_TRACKBALL_FOR_MOVEMENTS =
			new BooleanPreference(this, "use_trackball_for_movements", true).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> ACCESSIBILITY_SMART_AUTOANNOUNCE =
			new BooleanAccessibilityPreference(this, "accessibility_smart_autoannounce", true).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	// cache of metrics constants as they are used very often
	public final OsmandPreference<Integer> ACCESSIBILITY_AUTOANNOUNCE_PERIOD = new IntPreference(this, "accessibility_autoannounce_period", 10000).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> DISABLE_OFFROUTE_RECALC =
			new BooleanPreference(this, "disable_offroute_recalc", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> DISABLE_WRONG_DIRECTION_RECALC =
			new BooleanPreference(this, "disable_wrong_direction_recalc", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> DIRECTION_AUDIO_FEEDBACK =
			new BooleanAccessibilityPreference(this, "direction_audio_feedback", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> DIRECTION_HAPTIC_FEEDBACK =
			new BooleanAccessibilityPreference(this, "direction_haptic_feedback", false).makeProfile();

	// magnetic field doesn'torkmost of the time on some phones
	public final OsmandPreference<Boolean> USE_MAGNETIC_FIELD_SENSOR_COMPASS = new BooleanPreference(this, "use_magnetic_field_sensor_compass", false).makeProfile().cache();
	public final OsmandPreference<Boolean> USE_KALMAN_FILTER_FOR_COMPASS = new BooleanPreference(this, "use_kalman_filter_compass", true).makeProfile().cache();
	public final OsmandPreference<Boolean> USE_VOLUME_BUTTONS_AS_ZOOM = new BooleanPreference(this, "use_volume_buttons_as_zoom", false).makeProfile().cache();

	public final OsmandPreference<Boolean> DO_NOT_SHOW_STARTUP_MESSAGES = new BooleanPreference(this, "do_not_show_startup_messages", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> SHOW_DOWNLOAD_MAP_DIALOG = new BooleanPreference(this, "show_download_map_dialog", true).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> DO_NOT_USE_ANIMATIONS = new BooleanPreference(this, "do_not_use_animations", false).makeProfile().cache();

	public final OsmandPreference<Boolean> SEND_ANONYMOUS_MAP_DOWNLOADS_DATA = new BooleanPreference(this, "send_anonymous_map_downloads_data", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> SEND_ANONYMOUS_APP_USAGE_DATA = new BooleanPreference(this, "send_anonymous_app_usage_data", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> SEND_ANONYMOUS_DATA_REQUEST_PROCESSED = new BooleanPreference(this, "send_anonymous_data_request_processed", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Integer> SEND_ANONYMOUS_DATA_REQUESTS_COUNT = new IntPreference(this, "send_anonymous_data_requests_count", 0).makeGlobal().cache();
	public final OsmandPreference<Integer> SEND_ANONYMOUS_DATA_LAST_REQUEST_NS = new IntPreference(this, "send_anonymous_data_last_request_ns", -1).makeGlobal().cache();

	public final OsmandPreference<Boolean> MAP_EMPTY_STATE_ALLOWED = new BooleanPreference(this, "map_empty_state_allowed", false).makeProfile().cache();


	public final CommonPreference<Float> TEXT_SCALE = new FloatPreference(this, "text_scale", 1f).makeProfile().cache();

	{
		TEXT_SCALE.setModeDefaultValue(ApplicationMode.CAR, 1.25f);
	}

	public final CommonPreference<Float> MAP_DENSITY = new FloatPreference(this, "map_density_n", 1f).makeProfile().cache();

	{
		MAP_DENSITY.setModeDefaultValue(ApplicationMode.CAR, 1.5f);
	}


	public final OsmandPreference<Boolean> SHOW_POI_LABEL = new BooleanPreference(this, "show_poi_label", false).makeProfile();

	public final OsmandPreference<Boolean> SHOW_MAPILLARY = new BooleanPreference(this, "show_mapillary", false).makeProfile();
	public final OsmandPreference<Boolean> MAPILLARY_FIRST_DIALOG_SHOWN = new BooleanPreference(this, "mapillary_first_dialog_shown", false).makeGlobal();
	public final OsmandPreference<Boolean> ONLINE_PHOTOS_ROW_COLLAPSED = new BooleanPreference(this, "mapillary_menu_collapsed", true).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> WEBGL_SUPPORTED = new BooleanPreference(this, "webgl_supported", true).makeGlobal();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<String> PREFERRED_LOCALE = new StringPreference(this, "preferred_locale", "").makeGlobal().makeShared();

	public final OsmandPreference<String> MAP_PREFERRED_LOCALE = new StringPreference(this, "map_preferred_locale", "").makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> MAP_TRANSLITERATE_NAMES = new BooleanPreference(this, "map_transliterate_names", false) {

		protected Boolean getDefaultValue() {
			return usingEnglishNames();
		}

	}.makeGlobal().makeShared().cache();

	public boolean usingEnglishNames() {
		return MAP_PREFERRED_LOCALE.get().equals("en");
	}

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<String> OSM_USER_NAME = new StringPreference(this, "user_name", "").makeGlobal().makeShared();
	public final OsmandPreference<String> OSM_USER_DISPLAY_NAME = new StringPreference(this, "user_display_name", "").makeGlobal().makeShared();
	public final CommonPreference<UploadVisibility> OSM_UPLOAD_VISIBILITY = new EnumStringPreference<>(this, "upload_visibility", UploadVisibility.PUBLIC, UploadVisibility.values()).makeGlobal().makeShared();

	public static final String BILLING_USER_DONATION_WORLD_PARAMETER = "";
	public static final String BILLING_USER_DONATION_NONE_PARAMETER = "none";

	public final OsmandPreference<Boolean> INAPPS_READ = new BooleanPreference(this, "inapps_read", false).makeGlobal();

	public final OsmandPreference<String> BILLING_USER_ID = new StringPreference(this, "billing_user_id", "").makeGlobal();
	public final OsmandPreference<String> BILLING_USER_TOKEN = new StringPreference(this, "billing_user_token", "").makeGlobal();
	public final OsmandPreference<String> BILLING_USER_NAME = new StringPreference(this, "billing_user_name", "").makeGlobal();
	public final OsmandPreference<String> BILLING_USER_EMAIL = new StringPreference(this, "billing_user_email", "").makeGlobal();
	public final OsmandPreference<String> BILLING_USER_COUNTRY = new StringPreference(this, "billing_user_country", "").makeGlobal();
	public final OsmandPreference<String> BILLING_USER_COUNTRY_DOWNLOAD_NAME = new StringPreference(this, "billing_user_country_download_name", BILLING_USER_DONATION_NONE_PARAMETER).makeGlobal();
	public final OsmandPreference<Boolean> BILLING_HIDE_USER_NAME = new BooleanPreference(this, "billing_hide_user_name", false).makeGlobal();
	public final OsmandPreference<Boolean> BILLING_PURCHASE_TOKEN_SENT = new BooleanPreference(this, "billing_purchase_token_sent", false).makeGlobal();
	public final OsmandPreference<String> BILLING_PURCHASE_TOKENS_SENT = new StringPreference(this, "billing_purchase_tokens_sent", "").makeGlobal();
	public final OsmandPreference<Boolean> LIVE_UPDATES_PURCHASED = new BooleanPreference(this, "billing_live_updates_purchased", false).makeGlobal();
	public final OsmandPreference<Long> LIVE_UPDATES_EXPIRED_FIRST_DLG_SHOWN_TIME = new LongPreference(this, "live_updates_expired_first_dlg_shown_time", 0).makeGlobal();
	public final OsmandPreference<Long> LIVE_UPDATES_EXPIRED_SECOND_DLG_SHOWN_TIME = new LongPreference(this, "live_updates_expired_second_dlg_shown_time", 0).makeGlobal();
	public final OsmandPreference<Boolean> FULL_VERSION_PURCHASED = new BooleanPreference(this, "billing_full_version_purchased", false).makeGlobal();
	public final OsmandPreference<Boolean> DEPTH_CONTOURS_PURCHASED = new BooleanPreference(this, "billing_sea_depth_purchased", false).makeGlobal();
	public final OsmandPreference<Boolean> CONTOUR_LINES_PURCHASED = new BooleanPreference(this, "billing_srtm_purchased", false).makeGlobal();
	public final OsmandPreference<Boolean> EMAIL_SUBSCRIBED = new BooleanPreference(this, "email_subscribed", false).makeGlobal();
	public final OsmandPreference<Boolean> OSMAND_PRO_PURCHASED = new BooleanPreference(this, "billing_osmand_pro_purchased", false).makeGlobal();
	public final OsmandPreference<Boolean> OSMAND_MAPS_PURCHASED = new BooleanPreference(this, "billing_osmand_maps_purchased", false).makeGlobal();

	public final OsmandPreference<Integer> DISCOUNT_ID = new IntPreference(this, "discount_id", 0).makeGlobal();
	public final OsmandPreference<Integer> DISCOUNT_SHOW_NUMBER_OF_STARTS = new IntPreference(this, "number_of_starts_on_discount_show", 0).makeGlobal();
	public final OsmandPreference<Integer> DISCOUNT_TOTAL_SHOW = new IntPreference(this, "discount_total_show", 0).makeGlobal();
	public final OsmandPreference<Long> DISCOUNT_SHOW_DATETIME_MS = new LongPreference(this, "show_discount_datetime_ms", 0).makeGlobal();

	public final OsmandPreference<String> BACKUP_USER_EMAIL = new StringPreference(this, "backup_user_email", "").makeGlobal();
	public final OsmandPreference<String> BACKUP_USER_ID = new StringPreference(this, "backup_user_id", "").makeGlobal();
	public final OsmandPreference<String> BACKUP_DEVICE_ID = new StringPreference(this, "backup_device_id", "").makeGlobal();
	public final OsmandPreference<String> BACKUP_NATIVE_DEVICE_ID = new StringPreference(this, "backup_native_device_id", "").makeGlobal();
	public final OsmandPreference<String> BACKUP_ACCESS_TOKEN = new StringPreference(this, "backup_access_token", "").makeGlobal();
	public final OsmandPreference<String> BACKUP_ACCESS_TOKEN_UPDATE_TIME = new StringPreference(this, "backup_access_token_update_time", "").makeGlobal();

	public final OsmandPreference<Long> FAVORITES_LAST_UPLOADED_TIME = new LongPreference(this, "favorites_last_uploaded_time", 0L).makeGlobal();
	public final OsmandPreference<Long> BACKUP_LAST_UPLOADED_TIME = new LongPreference(this, "backup_last_uploaded_time", 0L).makeGlobal();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<String> USER_OSM_BUG_NAME =
			new StringPreference(this, "user_osm_bug_name", "NoName/OsmAnd").makeGlobal().makeShared();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<String> OSM_USER_PASSWORD =
			new StringPreference(this, "user_password", "").makeGlobal().makeShared();

	public final OsmandPreference<String> OSM_USER_ACCESS_TOKEN =
			new StringPreference(this, "user_access_token", "").makeGlobal();

	public final OsmandPreference<String> OSM_USER_ACCESS_TOKEN_SECRET =
			new StringPreference(this, "user_access_token_secret", "").makeGlobal();

	public final OsmandPreference<String> OPR_ACCESS_TOKEN =
			new StringPreference(this, "opr_user_access_token_secret", "").makeGlobal();

	public final OsmandPreference<String> OPR_USERNAME =
			new StringPreference(this, "opr_username_secret", "").makeGlobal();

	public final OsmandPreference<String> OPR_BLOCKCHAIN_NAME =
			new StringPreference(this, "opr_blockchain_name", "").makeGlobal();

	public final OsmandPreference<Boolean> OPR_USE_DEV_URL = new BooleanPreference(this, "opr_use_dev_url", false).makeGlobal().makeShared();

	// this value boolean is synchronized with settings_pref.xml preference offline POI/Bugs edition
	public final OsmandPreference<Boolean> OFFLINE_EDITION = new BooleanPreference(this, "offline_osm_editing", true).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> OSM_USE_DEV_URL = new BooleanPreference(this, "use_dev_url", false).makeGlobal().makeShared();

	public String getOsmUrl() {
		String osmUrl;
		if (OSM_USE_DEV_URL.get()) {
			osmUrl = "https://master.apis.dev.openstreetmap.org/";
		} else {
			osmUrl = "https://api.openstreetmap.org/";
		}
		return osmUrl;
	}

	public String getOprUrl() {
		return ctx.getString(OPR_USE_DEV_URL.get() ? R.string.dev_opr_base_url : R.string.opr_base_url);
	}

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<DayNightMode> DAYNIGHT_MODE =
			new EnumStringPreference<DayNightMode>(this, "daynight_mode", DayNightMode.DAY, DayNightMode.values());

	{
		DAYNIGHT_MODE.makeProfile().cache();
		DAYNIGHT_MODE.setModeDefaultValue(ApplicationMode.CAR, DayNightMode.AUTO);
		DAYNIGHT_MODE.setModeDefaultValue(ApplicationMode.BICYCLE, DayNightMode.AUTO);
		DAYNIGHT_MODE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, DayNightMode.DAY);
	}

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Boolean> AUTO_ZOOM_MAP = new BooleanPreference(this, "auto_zoom_map_on_off", false).makeProfile().cache();

	{
		AUTO_ZOOM_MAP.setModeDefaultValue(ApplicationMode.CAR, true);
		AUTO_ZOOM_MAP.setModeDefaultValue(ApplicationMode.BICYCLE, false);
		AUTO_ZOOM_MAP.setModeDefaultValue(ApplicationMode.PEDESTRIAN, false);
	}

	public final CommonPreference<AutoZoomMap> AUTO_ZOOM_MAP_SCALE =
			new EnumStringPreference<AutoZoomMap>(this, "auto_zoom_map_scale", AutoZoomMap.FAR,
					AutoZoomMap.values()).makeProfile().cache();

	{
		AUTO_ZOOM_MAP_SCALE.setModeDefaultValue(ApplicationMode.CAR, AutoZoomMap.FAR);
		AUTO_ZOOM_MAP_SCALE.setModeDefaultValue(ApplicationMode.BICYCLE, AutoZoomMap.CLOSE);
		AUTO_ZOOM_MAP_SCALE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, AutoZoomMap.CLOSE);
	}

	public final CommonPreference<Integer> DELAY_TO_START_NAVIGATION = new IntPreference(this, "delay_to_start_navigation", -1) {

		protected Integer getDefaultValue() {
			if (DEFAULT_APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.CAR)) {
				return 10;
			}
			return -1;
		}
	}.makeGlobal().makeShared().cache();

	public final CommonPreference<Boolean> SNAP_TO_ROAD = new BooleanPreference(this, "snap_to_road", false).makeProfile().cache();

	{
		SNAP_TO_ROAD.setModeDefaultValue(ApplicationMode.CAR, true);
		SNAP_TO_ROAD.setModeDefaultValue(ApplicationMode.BICYCLE, true);
	}

	public final CommonPreference<Boolean> INTERRUPT_MUSIC = new BooleanPreference(this, "interrupt_music", false).makeProfile();

	public final CommonPreference<Boolean> ENABLE_PROXY = new BooleanPreference(this, "enable_proxy", false) {
		@Override
		protected boolean setValue(Object prefs, Boolean val) {
			boolean valueSaved = super.setValue(prefs, val);
			if (valueSaved) {
				NetworkUtils.setProxy(val ? PROXY_HOST.get() : null, val ? PROXY_PORT.get() : 0);
			}
			return valueSaved;
		}
	}.makeGlobal().makeShared();

	public final CommonPreference<String> PROXY_HOST = new StringPreference(this, "proxy_host", "127.0.0.1").makeGlobal().makeShared();
	public final CommonPreference<Integer> PROXY_PORT = new IntPreference(this, "proxy_port", 8118).makeGlobal().makeShared();
	public final CommonPreference<String> USER_ANDROID_ID = new StringPreference(this, "user_android_id", "").makeGlobal();

	// this value string is synchronized with settings_pref.xml preference name
	public static final String SAVE_CURRENT_TRACK = "save_current_track"; //$NON-NLS-1$

	public final CommonPreference<Boolean> SAVE_GLOBAL_TRACK_TO_GPX = new BooleanPreference(this, "save_global_track_to_gpx", false).makeGlobal().cache();
	public final CommonPreference<Integer> SAVE_GLOBAL_TRACK_INTERVAL = new IntPreference(this, "save_global_track_interval", 5000).makeProfile().cache();
	public final CommonPreference<Boolean> SAVE_GLOBAL_TRACK_REMEMBER = new BooleanPreference(this, "save_global_track_remember", false).makeProfile().cache();
	public final CommonPreference<Boolean> SHOW_SAVED_TRACK_REMEMBER = new BooleanPreference(this, "show_saved_track_remember", true).makeGlobal().makeShared();
	public final CommonPreference<Boolean> SHOW_TRIP_REC_START_DIALOG = new BooleanPreference(this, "show_trip_recording_start_dialog", true).makeGlobal().makeShared();
	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Boolean> SAVE_TRACK_TO_GPX = new BooleanPreference(this, "save_track_to_gpx", false).makeProfile().cache();

	{
		SAVE_TRACK_TO_GPX.setModeDefaultValue(ApplicationMode.CAR, false);
		SAVE_TRACK_TO_GPX.setModeDefaultValue(ApplicationMode.BICYCLE, false);
		SAVE_TRACK_TO_GPX.setModeDefaultValue(ApplicationMode.PEDESTRIAN, false);
	}

	public static final Integer REC_DIRECTORY = 0;
	public static final Integer MONTHLY_DIRECTORY = 1;
//	public static final Integer DAILY_DIRECTORY = 2;

	public final CommonPreference<Boolean> DISABLE_RECORDING_ONCE_APP_KILLED = new BooleanPreference(this, "disable_recording_once_app_killed", false).makeProfile();

	public final CommonPreference<Boolean> SAVE_HEADING_TO_GPX = new BooleanPreference(this, "save_heading_to_gpx", false).makeProfile();

	public final CommonPreference<Integer> TRACK_STORAGE_DIRECTORY = new IntPreference(this, "track_storage_directory", 0).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> FAST_ROUTE_MODE = new BooleanPreference(this, "fast_route_mode", true).makeProfile();
	// dev version
	public final CommonPreference<Boolean> DISABLE_COMPLEX_ROUTING = new BooleanPreference(this, "disable_complex_routing", false).makeProfile();
	public final CommonPreference<Boolean> ENABLE_TIME_CONDITIONAL_ROUTING = new BooleanPreference(this, "enable_time_conditional_routing", true).makeProfile();

	public boolean simulateNavigation = false;

	public final CommonPreference<Boolean> SHOW_ROUTING_ALARMS = new BooleanPreference(this, "show_routing_alarms", true).makeProfile().cache();

	public final CommonPreference<Boolean> SHOW_TRAFFIC_WARNINGS = new BooleanPreference(this, "show_traffic_warnings", false).makeProfile().cache();

	{
		SHOW_TRAFFIC_WARNINGS.setModeDefaultValue(ApplicationMode.CAR, true);
	}

	public final CommonPreference<Boolean> SHOW_PEDESTRIAN = new BooleanPreference(this, "show_pedestrian", false).makeProfile().cache();

	{
		SHOW_PEDESTRIAN.setModeDefaultValue(ApplicationMode.CAR, true);
	}

	public final CommonPreference<Boolean> SHOW_TUNNELS = new BooleanPreference(this, "show_tunnels", false).makeProfile().cache();

	{
		SHOW_TUNNELS.setModeDefaultValue(ApplicationMode.CAR, true);
	}

	public final OsmandPreference<Boolean> SHOW_CAMERAS = new BooleanPreference(this, "show_cameras", false).makeProfile().cache();
	public final CommonPreference<Boolean> SHOW_LANES = new BooleanPreference(this, "show_lanes", false).makeProfile().cache();

	{
		SHOW_LANES.setModeDefaultValue(ApplicationMode.CAR, true);
		SHOW_LANES.setModeDefaultValue(ApplicationMode.BICYCLE, true);
	}

	public final OsmandPreference<Boolean> SHOW_WPT = new BooleanPreference(this, "show_gpx_wpt", true).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> SHOW_NEARBY_FAVORITES = new BooleanPreference(this, "show_nearby_favorites", false).makeProfile().cache();
	public final OsmandPreference<Boolean> SHOW_NEARBY_POI = new BooleanPreference(this, "show_nearby_poi", false).makeProfile().cache();

	public final OsmandPreference<Boolean> SPEAK_STREET_NAMES = new BooleanPreference(this, "speak_street_names", true).makeProfile().cache();
	public final CommonPreference<Boolean> SPEAK_TRAFFIC_WARNINGS = new BooleanPreference(this, "speak_traffic_warnings", true).makeProfile().cache();

	{
		SPEAK_TRAFFIC_WARNINGS.setModeDefaultValue(ApplicationMode.CAR, true);
	}

	public final CommonPreference<Boolean> SPEAK_PEDESTRIAN = new BooleanPreference(this, "speak_pedestrian", false).makeProfile().cache();

	{
		SPEAK_PEDESTRIAN.setModeDefaultValue(ApplicationMode.CAR, true);
	}

	public final OsmandPreference<Boolean> SPEAK_SPEED_LIMIT = new BooleanPreference(this, "speak_speed_limit", false).makeProfile().cache();
	public final OsmandPreference<Boolean> SPEAK_SPEED_CAMERA = new BooleanPreference(this, "speak_cameras", false).makeProfile().cache();
	public final OsmandPreference<Boolean> SPEAK_TUNNELS = new BooleanPreference(this, "speak_tunnels", false).makeProfile().cache();
	public final OsmandPreference<Boolean> SPEAK_EXIT_NUMBER_NAMES = new BooleanPreference(this, "exit_number_names", true).makeProfile().cache();

	public final OsmandPreference<Boolean> SPEED_CAMERAS_UNINSTALLED = new BooleanPreference(this, "speed_cameras_uninstalled", false).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> SPEED_CAMERAS_ALERT_SHOWED = new BooleanPreference(this, "speed_cameras_alert_showed", false).makeGlobal().makeShared();

	public Set<String> getForbiddenTypes() {
		Set<String> typeNames = new HashSet<>();
		if (SPEED_CAMERAS_UNINSTALLED.get()) {
			typeNames.add(MapPoiTypes.SPEED_CAMERA);
		}
		return typeNames;
	}

	public final OsmandPreference<Boolean> ANNOUNCE_WPT = new BooleanPreference(this, "announce_wpt", true) {
		@Override
		protected boolean setValue(Object prefs, Boolean val) {
			boolean valueSaved = super.setValue(prefs, val);
			if (valueSaved) {
				SHOW_WPT.set(val);
			}

			return valueSaved;
		}
	}.makeProfile().cache();

	public final OsmandPreference<Boolean> ANNOUNCE_NEARBY_FAVORITES = new BooleanPreference(this, "announce_nearby_favorites", false) {
		@Override
		protected boolean setValue(Object prefs, Boolean val) {
			boolean valueSaved = super.setValue(prefs, val);
			if (valueSaved) {
				SHOW_NEARBY_FAVORITES.set(val);
			}

			return valueSaved;
		}
	}.makeProfile().cache();

	public final OsmandPreference<Boolean> ANNOUNCE_NEARBY_POI = new BooleanPreference(this, "announce_nearby_poi", false) {
		@Override
		protected boolean setValue(Object prefs, Boolean val) {
			boolean valueSaved = super.setValue(prefs, val);
			if (valueSaved) {
				SHOW_NEARBY_POI.set(val);
			}

			return valueSaved;
		}
	}.makeProfile().cache();

	public final OsmandPreference<Boolean> GPX_ROUTE_CALC_OSMAND_PARTS = new BooleanPreference(this, "gpx_routing_calculate_osmand_route", true).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> GPX_CALCULATE_RTEPT = new BooleanPreference(this, "gpx_routing_calculate_rtept", true).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> GPX_ROUTE_CALC = new BooleanPreference(this, "calc_gpx_route", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Integer> GPX_ROUTE_SEGMENT = new IntPreference(this, "gpx_route_segment", -1).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> SHOW_START_FINISH_ICONS = new BooleanPreference(this, "show_start_finish_icons", true).makeGlobal().makeShared().cache();

	public final OsmandPreference<Boolean> AVOID_TOLL_ROADS = new BooleanPreference(this, "avoid_toll_roads", false).makeProfile().cache();
	public final OsmandPreference<Boolean> AVOID_MOTORWAY = new BooleanPreference(this, "avoid_motorway", false).makeProfile().cache();
	public final OsmandPreference<Boolean> AVOID_UNPAVED_ROADS = new BooleanPreference(this, "avoid_unpaved_roads", false).makeProfile().cache();
	public final OsmandPreference<Boolean> AVOID_FERRIES = new BooleanPreference(this, "avoid_ferries", false).makeProfile().cache();

	public final OsmandPreference<Boolean> PREFER_MOTORWAYS = new BooleanPreference(this, "prefer_motorways", false).makeProfile().cache();

	public final OsmandPreference<Long> LAST_UPDATES_CARD_REFRESH = new LongPreference(this, "last_updates_card_refresh", 0).makeGlobal();

	public final CommonPreference<Integer> CURRENT_TRACK_COLOR = new IntPreference(this, "current_track_color", 0).makeGlobal().makeShared().cache();
	public final CommonPreference<GradientScaleType> CURRENT_TRACK_COLORIZATION = new EnumStringPreference<>(this, "current_track_colorization", null, GradientScaleType.values()).makeGlobal().makeShared().cache();
	public final CommonPreference<String> CURRENT_TRACK_SPEED_GRADIENT_PALETTE = new StringPreference(this, "current_track_speed_gradient_palette", null).makeGlobal().makeShared().cache();
	public final CommonPreference<String> CURRENT_TRACK_ALTITUDE_GRADIENT_PALETTE = new StringPreference(this, "current_track_altitude_gradient_palette", null).makeGlobal().makeShared().cache();
	public final CommonPreference<String> CURRENT_TRACK_SLOPE_GRADIENT_PALETTE = new StringPreference(this, "current_track_slope_gradient_palette", null).makeGlobal().makeShared().cache();
	public final CommonPreference<String> CURRENT_TRACK_WIDTH = new StringPreference(this, "current_track_width", "").makeGlobal().makeShared().cache();
	public final CommonPreference<Boolean> CURRENT_TRACK_SHOW_ARROWS = new BooleanPreference(this, "current_track_show_arrows", false).makeGlobal().makeShared().cache();
	public final CommonPreference<Boolean> CURRENT_TRACK_SHOW_START_FINISH = new BooleanPreference(this, "current_track_show_start_finish", true).makeGlobal().makeShared().cache();
	public final ListStringPreference CUSTOM_TRACK_COLORS = (ListStringPreference) new ListStringPreference(this, "custom_track_colors", null, ",").makeShared().makeGlobal();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Integer> SAVE_TRACK_INTERVAL = new IntPreference(this, "save_track_interval", 5000).makeProfile();

	{
		SAVE_TRACK_INTERVAL.setModeDefaultValue(ApplicationMode.CAR, 3000);
		SAVE_TRACK_INTERVAL.setModeDefaultValue(ApplicationMode.BICYCLE, 5000);
		SAVE_TRACK_INTERVAL.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 10000);
	}

	// Please note that SAVE_TRACK_MIN_DISTANCE, SAVE_TRACK_PRECISION, SAVE_TRACK_MIN_SPEED should all be "0" for the default profile, as we have no interface to change them
	public final CommonPreference<Float> SAVE_TRACK_MIN_DISTANCE = new FloatPreference(this, "save_track_min_distance", 0).makeProfile();
	//{
	//	SAVE_TRACK_MIN_DISTANCE.setModeDefaultValue(ApplicationMode.CAR, 5.f);
	//	SAVE_TRACK_MIN_DISTANCE.setModeDefaultValue(ApplicationMode.BICYCLE, 5.f);
	//	SAVE_TRACK_MIN_DISTANCE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 5.f);
	//}
	public final CommonPreference<Float> SAVE_TRACK_PRECISION = new FloatPreference(this, "save_track_precision", 50.0f).makeProfile();
	//{
//		SAVE_TRACK_PRECISION.setModeDefaultValue(ApplicationMode.CAR, 50.f);
//		SAVE_TRACK_PRECISION.setModeDefaultValue(ApplicationMode.BICYCLE, 50.f);
//		SAVE_TRACK_PRECISION.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 50.f);
	//}
	public final CommonPreference<Float> SAVE_TRACK_MIN_SPEED = new FloatPreference(this, "save_track_min_speed", 0.f).makeProfile();
	//{
	//	SAVE_TRACK_MIN_SPEED.setModeDefaultValue(ApplicationMode.CAR, 2.f);
	//	SAVE_TRACK_MIN_SPEED.setModeDefaultValue(ApplicationMode.BICYCLE, 1.f);
//		SAVE_TRACK_MIN_SPEED.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 0.f);
	//}
	public final CommonPreference<Boolean> AUTO_SPLIT_RECORDING = new BooleanPreference(this, "auto_split_recording", true).makeProfile();

	public final CommonPreference<Boolean> SHOW_TRIP_REC_NOTIFICATION = new BooleanPreference(this, "show_trip_recording_notification", true).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Boolean> LIVE_MONITORING = new BooleanPreference(this, "live_monitoring", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Integer> LIVE_MONITORING_INTERVAL = new IntPreference(this, "live_monitoring_interval", 5000).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Integer> LIVE_MONITORING_MAX_INTERVAL_TO_SEND = new IntPreference(this, "live_monitoring_maximum_interval_to_send", 900000).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<String> LIVE_MONITORING_URL = new StringPreference(this, "live_monitoring_url",
			"https://example.com?lat={0}&lon={1}&timestamp={2}&hdop={3}&altitude={4}&speed={5}").makeProfile();

	public final CommonPreference<String> GPS_STATUS_APP = new StringPreference(this, "gps_status_app", "").makeGlobal().makeShared();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> SHOW_OSM_BUGS = new BooleanPreference(this, "show_osm_bugs", false).makeProfile().cache();

	public final OsmandPreference<Boolean> SHOW_OSM_EDITS = new BooleanPreference(this, "show_osm_edits", true).makeProfile().cache();

	public final CommonPreference<Boolean> SHOW_CLOSED_OSM_BUGS = new BooleanPreference(this, "show_closed_osm_bugs", false).makeProfile().cache();
	public final CommonPreference<Integer> SHOW_OSM_BUGS_MIN_ZOOM = new IntPreference(this, "show_osm_bugs_min_zoom", 8).makeProfile().cache();

	public final CommonPreference<String> MAP_INFO_CONTROLS = new StringPreference(this, "map_info_controls", "").makeProfile();

	{
		for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
			MAP_INFO_CONTROLS.setModeDefaultValue(mode, "");
		}
	}


	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> DEBUG_RENDERING_INFO = new BooleanPreference(this, "debug_rendering", false).makeGlobal().makeShared();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> SHOW_FAVORITES = new BooleanPreference(this, "show_favorites", true).makeProfile().cache();

	public final CommonPreference<Boolean> SHOW_ZOOM_BUTTONS_NAVIGATION = new BooleanPreference(this, "show_zoom_buttons_navigation", false).makeProfile().cache();

	{
		SHOW_ZOOM_BUTTONS_NAVIGATION.setModeDefaultValue(ApplicationMode.PEDESTRIAN, true);
	}

	// Json
	public final OsmandPreference<String> SELECTED_GPX = new StringPreference(this, "selected_gpx", "").makeGlobal().makeShared();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Integer> MAP_SCREEN_ORIENTATION =
			new IntPreference(this, "map_screen_orientation", -1/*ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED*/).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
//	public final CommonPreference<Boolean> SHOW_VIEW_ANGLE = new BooleanPreference("show_view_angle", false).makeProfile().cache();
//	{
//		SHOW_VIEW_ANGLE.setModeDefaultValue(ApplicationMode.CAR, false);
//		SHOW_VIEW_ANGLE.setModeDefaultValue(ApplicationMode.BICYCLE, true);
//		SHOW_VIEW_ANGLE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, true);
//	}

	// this value string is synchronized with settings_pref.xml preference name
	// seconds to auto_follow
	public final CommonPreference<Integer> AUTO_FOLLOW_ROUTE = new IntPreference(this, "auto_follow_route", 0).makeProfile();

	{
		AUTO_FOLLOW_ROUTE.setModeDefaultValue(ApplicationMode.CAR, 15);
		AUTO_FOLLOW_ROUTE.setModeDefaultValue(ApplicationMode.BICYCLE, 15);
		AUTO_FOLLOW_ROUTE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 0);
	}

	// this value string is synchronized with settings_pref.xml preference name
	// seconds to auto_follow
	public final CommonPreference<Integer> KEEP_INFORMING = new IntPreference(this, "keep_informing", 0).makeProfile();

	{
		KEEP_INFORMING.setModeDefaultValue(ApplicationMode.CAR, 0);
		KEEP_INFORMING.setModeDefaultValue(ApplicationMode.BICYCLE, 0);
		KEEP_INFORMING.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 0);
	}

	public final CommonPreference<Boolean> USE_SYSTEM_SCREEN_TIMEOUT = new BooleanPreference(this, "use_system_screen_timeout", false).makeProfile();

	public final CommonPreference<Integer> TURN_SCREEN_ON_TIME_INT = new IntPreference(this, "turn_screen_on_time_int", 0).makeProfile();

	{
		TURN_SCREEN_ON_TIME_INT.setModeDefaultValue(ApplicationMode.CAR, 0);
		TURN_SCREEN_ON_TIME_INT.setModeDefaultValue(ApplicationMode.BICYCLE, 0);
		TURN_SCREEN_ON_TIME_INT.setModeDefaultValue(ApplicationMode.PEDESTRIAN, 0);
	}

	public final CommonPreference<Boolean> TURN_SCREEN_ON_SENSOR = new BooleanPreference(this, "turn_screen_on_sensor", false).makeProfile();

	{
		TURN_SCREEN_ON_SENSOR.setModeDefaultValue(ApplicationMode.CAR, false);
		TURN_SCREEN_ON_SENSOR.setModeDefaultValue(ApplicationMode.BICYCLE, false);
		TURN_SCREEN_ON_SENSOR.setModeDefaultValue(ApplicationMode.PEDESTRIAN, false);
	}

	public final CommonPreference<Boolean> TURN_SCREEN_ON_NAVIGATION_INSTRUCTIONS = new BooleanPreference(this, "turn_screen_on_navigation_instructions", false).makeProfile();

	public final CommonPreference<Boolean> TURN_SCREEN_ON_POWER_BUTTON = new BooleanPreference(this, "turn_screen_on_power_button", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	// try without AUTO_FOLLOW_ROUTE_NAV (see forum discussion 'Simplify our navigation preference menu')
	//public final CommonPreference<Boolean> AUTO_FOLLOW_ROUTE_NAV = new BooleanPreference("auto_follow_route_navigation", true, false);

	// this value string is synchronized with settings_pref.xml preference name
	public static final int ROTATE_MAP_NONE = 0;
	public static final int ROTATE_MAP_BEARING = 1;
	public static final int ROTATE_MAP_COMPASS = 2;
	public final CommonPreference<Integer> ROTATE_MAP =
			new IntPreference(this, "rotate_map", ROTATE_MAP_NONE).makeProfile().cache();

	{
		ROTATE_MAP.setModeDefaultValue(ApplicationMode.CAR, ROTATE_MAP_BEARING);
		ROTATE_MAP.setModeDefaultValue(ApplicationMode.BICYCLE, ROTATE_MAP_BEARING);
		ROTATE_MAP.setModeDefaultValue(ApplicationMode.PEDESTRIAN, ROTATE_MAP_COMPASS);
	}

	// this value string is synchronized with settings_pref.xml preference name
	public static final int CENTER_CONSTANT = 0;
	public static final int BOTTOM_CONSTANT = 1;
	public static final int MIDDLE_BOTTOM_CONSTANT = 2;
	public static final int MIDDLE_TOP_CONSTANT = 3;
	public static final int LANDSCAPE_MIDDLE_RIGHT_CONSTANT = 4;
	public final CommonPreference<Boolean> CENTER_POSITION_ON_MAP = new BooleanPreference(this, "center_position_on_map", false).makeProfile();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Integer> MAX_LEVEL_TO_DOWNLOAD_TILE = new IntPreference(this, "max_level_download_tile", 20).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Integer> LEVEL_TO_SWITCH_VECTOR_RASTER = new IntPreference(this, "level_to_switch_vector_raster", 1).makeGlobal().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Integer> AUDIO_MANAGER_STREAM = new IntPreference(this, "audio_stream", 3/*AudioManager.STREAM_MUSIC*/) {
		@Override
		protected boolean setValue(Object prefs, Integer stream) {
			boolean valueSaved = super.setValue(prefs, stream);

			if (valueSaved) {
				CommandPlayer player = ctx.getPlayer();
				if (player != null) {
					player.updateAudioStream(get());
				}
				// Sync corresponding AUDIO_USAGE value
				ApplicationMode mode = APPLICATION_MODE.get();
				if (stream == 3 /*AudioManager.STREAM_MUSIC*/) {
					AUDIO_USAGE.setModeValue(mode, 12 /*AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE*/);
				} else if (stream == 5 /*AudioManager.STREAM_NOTIFICATION*/) {
					AUDIO_USAGE.setModeValue(mode, 5 /*AudioAttributes.USAGE_NOTIFICATION*/);
				} else if (stream == 0 /*AudioManager.STREAM_VOICE_CALL*/) {
					AUDIO_USAGE.setModeValue(mode, 2 /*AudioAttributes.USAGE_VOICE_COMMUNICATION*/);
				}
			}

			return valueSaved;
		}
	}.makeProfile();

	// Corresponding USAGE value for AudioAttributes
	public final OsmandPreference<Integer> AUDIO_USAGE = new IntPreference(this, "audio_usage",
			12/*AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE*/).makeProfile();

	// For now this can be changed only in TestVoiceActivity
	public final OsmandPreference<Integer>[] VOICE_PROMPT_DELAY = new IntPreference[10];

	{
		// 1500 ms delay works for most configurations to establish a BT SCO link
		VOICE_PROMPT_DELAY[0] = new IntPreference(this, "voice_prompt_delay_0", 1500).makeGlobal().makeShared().cache(); /*AudioManager.STREAM_VOICE_CALL*/
		// On most devices sound output works pomptly so usually no voice prompt delay needed
		VOICE_PROMPT_DELAY[3] = new IntPreference(this, "voice_prompt_delay_3", 0).makeGlobal().makeShared().cache();    /*AudioManager.STREAM_MUSIC*/
		VOICE_PROMPT_DELAY[5] = new IntPreference(this, "voice_prompt_delay_5", 0).makeGlobal().makeShared().cache();    /*AudioManager.STREAM_NOTIFICATION*/
	}

	public final OsmandPreference<Boolean> DISPLAY_TTS_UTTERANCE = new BooleanPreference(this, "display_tts_utterance", false).makeGlobal().makeShared();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Boolean> MAP_ONLINE_DATA = new BooleanPreference(this, "map_online_data", false).makeProfile();

	public final CommonPreference<TerrainMode> TERRAIN_MODE = new EnumStringPreference<>(this, "terrain_mode", TerrainMode.HILLSHADE, TerrainMode.values()).makeProfile();

	public final CommonPreference<Integer> HILLSHADE_MIN_ZOOM = new IntPreference(this, "hillshade_min_zoom", 3).makeProfile();

	public final CommonPreference<Integer> HILLSHADE_MAX_ZOOM = new IntPreference(this, "hillshade_max_zoom", 17).makeProfile();

	public final CommonPreference<Integer> HILLSHADE_TRANSPARENCY = new IntPreference(this, "hillshade_transparency", 100).makeProfile();

	public final CommonPreference<Integer> SLOPE_MIN_ZOOM = new IntPreference(this, "slope_min_zoom", 3).makeProfile();

	public final CommonPreference<Integer> SLOPE_MAX_ZOOM = new IntPreference(this, "slope_max_zoom", 17).makeProfile();

	public final CommonPreference<Integer> SLOPE_TRANSPARENCY = new IntPreference(this, "slope_transparency", 80).makeProfile();

	public final CommonPreference<Boolean> TERRAIN = new BooleanPreference(this, "terrain_layer", true).makeProfile();

	public final CommonPreference<String> CONTOUR_LINES_ZOOM = new StringPreference(this, "contour_lines_zoom", null).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<String> MAP_OVERLAY = new StringPreference(this, "map_overlay", null).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<String> MAP_UNDERLAY = new StringPreference(this, "map_underlay", null).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Integer> MAP_OVERLAY_TRANSPARENCY = new IntPreference(this, "overlay_transparency", 100).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<Integer> MAP_TRANSPARENCY = new IntPreference(this, "map_transparency", 255).makeProfile().cache();

	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<String> MAP_TILE_SOURCES = new StringPreference(this, "map_tile_sources",
			TileSourceManager.getMapnikSource().getName()).makeProfile();

	public final CommonPreference<LayerTransparencySeekbarMode> LAYER_TRANSPARENCY_SEEKBAR_MODE =
			new EnumStringPreference<>(this, "layer_transparency_seekbar_mode", LayerTransparencySeekbarMode.UNDEFINED, LayerTransparencySeekbarMode.values());

	public final CommonPreference<String> MAP_OVERLAY_PREVIOUS = new StringPreference(this, "map_overlay_previous", null).makeGlobal().cache();

	public final CommonPreference<String> MAP_UNDERLAY_PREVIOUS = new StringPreference(this, "map_underlay_previous", null).makeGlobal().cache();

	public CommonPreference<String> PREVIOUS_INSTALLED_VERSION = new StringPreference(this, "previous_installed_version", "").makeGlobal();

	public final OsmandPreference<Boolean> SHOULD_SHOW_FREE_VERSION_BANNER = new BooleanPreference(this, "should_show_free_version_banner", false).makeGlobal().makeShared().cache();

	public final OsmandPreference<Boolean> MARKERS_DISTANCE_INDICATION_ENABLED = new BooleanPreference(this, "markers_distance_indication_enabled", true).makeProfile();

	public final OsmandPreference<Integer> DISPLAYED_MARKERS_WIDGETS_COUNT = new IntPreference(this, "displayed_markers_widgets_count", 1).makeProfile();

	public final CommonPreference<MapMarkersMode> MAP_MARKERS_MODE =
			new EnumStringPreference<>(this, "map_markers_mode", MapMarkersMode.TOOLBAR, MapMarkersMode.values());

	{
		MAP_MARKERS_MODE.makeProfile().cache();
		MAP_MARKERS_MODE.setModeDefaultValue(ApplicationMode.DEFAULT, MapMarkersMode.TOOLBAR);
		MAP_MARKERS_MODE.setModeDefaultValue(ApplicationMode.CAR, MapMarkersMode.TOOLBAR);
		MAP_MARKERS_MODE.setModeDefaultValue(ApplicationMode.BICYCLE, MapMarkersMode.TOOLBAR);
		MAP_MARKERS_MODE.setModeDefaultValue(ApplicationMode.PEDESTRIAN, MapMarkersMode.TOOLBAR);
	}

	public final OsmandPreference<Boolean> SHOW_MAP_MARKERS = new BooleanPreference(this, "show_map_markers", true).makeProfile();

	public final OsmandPreference<Boolean> SHOW_COORDINATES_WIDGET = new BooleanPreference(this, "show_coordinates_widget", false).makeProfile().cache();

	public final CommonPreference<NotesSortByMode> NOTES_SORT_BY_MODE = new EnumStringPreference<>(this, "notes_sort_by_mode", NotesSortByMode.BY_DATE, NotesSortByMode.values());
	public final CommonPreference<TracksSortByMode> TRACKS_SORT_BY_MODE = new EnumStringPreference<>(this, "tracks_sort_by_mode", TracksSortByMode.BY_DATE, TracksSortByMode.values());

	public final OsmandPreference<Boolean> ANIMATE_MY_LOCATION = new BooleanPreference(this, "animate_my_location", true).makeProfile().cache();

	public final OsmandPreference<Integer> EXTERNAL_INPUT_DEVICE = new IntPreference(this, "external_input_device", 0).makeProfile();

	public final OsmandPreference<Boolean> ROUTE_MAP_MARKERS_START_MY_LOC = new BooleanPreference(this, "route_map_markers_start_my_loc", false).makeGlobal().makeShared().cache();
	public final OsmandPreference<Boolean> ROUTE_MAP_MARKERS_ROUND_TRIP = new BooleanPreference(this, "route_map_markers_round_trip", false).makeGlobal().makeShared().cache();

	public ITileSource getMapTileSource(boolean warnWhenSelected) {
		String tileName = MAP_TILE_SOURCES.get();
		if (tileName != null) {
			ITileSource ts = getTileSourceByName(tileName, warnWhenSelected);
			if (ts != null) {
				return ts;
			}
		}
		return TileSourceManager.getMapnikSource();
	}

	private TileSourceTemplate checkAmongAvailableTileSources(File dir, List<TileSourceTemplate> list) {
		if (list != null) {
			for (TileSourceTemplate l : list) {
				if (dir.getName().equals(l.getName())) {
					try {
						dir.mkdirs();
						TileSourceManager.createMetaInfoFile(dir, l, true);
					} catch (IOException e) {
					}
					return l;
				}

			}
		}
		return null;
	}


	public ITileSource getTileSourceByName(String tileName, boolean warnWhenSelected) {
		if (tileName == null || tileName.length() == 0) {
			return null;
		}
		List<TileSourceTemplate> knownTemplates = TileSourceManager.getKnownSourceTemplates();
		File tPath = ctx.getAppPath(IndexConstants.TILES_INDEX_DIR);
		File dir = new File(tPath, tileName);
		if (!dir.exists()) {
			return checkAmongAvailableTileSources(dir, knownTemplates);
		} else if (tileName.endsWith(IndexConstants.SQLITE_EXT)) {
			return new SQLiteTileSource(ctx, dir, knownTemplates);
		} else if (dir.isDirectory() && !dir.getName().startsWith(".")) {
			TileSourceTemplate t = TileSourceManager.createTileSourceTemplate(dir);
			if (warnWhenSelected && !t.isRuleAcceptable()) {
				ctx.showToastMessage(R.string.warning_tile_layer_not_downloadable, dir.getName());
			}
			if (!TileSourceManager.isTileSourceMetaInfoExist(dir)) {
				TileSourceTemplate ret = checkAmongAvailableTileSources(dir, knownTemplates);
				if (ret != null) {
					t = ret;
				}
			}
			return t;
		}
		return null;
	}

	public boolean installTileSource(TileSourceTemplate toInstall) {
		File tPath = ctx.getAppPath(IndexConstants.TILES_INDEX_DIR);
		File dir = new File(tPath, toInstall.getName());
		dir.mkdirs();
		if (dir.exists() && dir.isDirectory()) {
			try {
				TileSourceManager.createMetaInfoFile(dir, toInstall, true);
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}

	public Map<String, String> getTileSourceEntries() {
		return getTileSourceEntries(true);

	}

	public Map<String, String> getTileSourceEntries(boolean sqlite) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		File dir = ctx.getAppPath(IndexConstants.TILES_INDEX_DIR);
		if (dir != null && dir.canRead()) {
			File[] files = dir.listFiles();
			Arrays.sort(files, new Comparator<File>() {
				@Override
				public int compare(File object1, File object2) {
					if (object1.lastModified() > object2.lastModified()) {
						return -1;
					} else if (object1.lastModified() == object2.lastModified()) {
						return 0;
					}
					return 1;
				}

			});
			if (files != null) {
				for (File f : files) {
					if (f.getName().endsWith(IndexConstants.SQLITE_EXT)) {
						if (sqlite) {
							String n = f.getName();
							map.put(f.getName(), n.substring(0, n.lastIndexOf('.')));
						}
					} else if (f.isDirectory() && !f.getName().equals(IndexConstants.TEMP_SOURCE_TO_LOAD)
							&& !f.getName().startsWith(".")) {
						map.put(f.getName(), f.getName());
					}
				}
			}
		}
		for (TileSourceTemplate l : TileSourceManager.getKnownSourceTemplates()) {
			if (!l.isHidden()) {
				map.put(l.getName(), l.getName());
			} else {
				map.remove(l.getName());
			}
		}
		return map;

	}

	public static final String EXTERNAL_STORAGE_DIR = "external_storage_dir"; //$NON-NLS-1$

	public static final String EXTERNAL_STORAGE_DIR_V19 = "external_storage_dir_V19"; //$NON-NLS-1$
	public static final String EXTERNAL_STORAGE_DIR_TYPE_V19 = "external_storage_dir_type_V19"; //$NON-NLS-1$
	public static final int EXTERNAL_STORAGE_TYPE_DEFAULT = 0; // Environment.getExternalStorageDirectory()
	public static final int EXTERNAL_STORAGE_TYPE_EXTERNAL_FILE = 1; // ctx.getExternalFilesDirs(null)
	public static final int EXTERNAL_STORAGE_TYPE_INTERNAL_FILE = 2; // ctx.getFilesDir()
	public static final int EXTERNAL_STORAGE_TYPE_OBB = 3; // ctx.getObbDirs
	public static final int EXTERNAL_STORAGE_TYPE_SPECIFIED = 4;
	public final OsmandPreference<Long> OSMAND_USAGE_SPACE = new LongPreference(this, "osmand_usage_space", 0).makeGlobal();


	public void freezeExternalStorageDirectory() {
		if (Build.VERSION.SDK_INT >= 19) {
			int type = settingsAPI.getInt(globalPreferences, EXTERNAL_STORAGE_DIR_TYPE_V19, -1);
			if (type == -1) {
				ValueHolder<Integer> vh = new ValueHolder<>();
				File f = getExternalStorageDirectoryV19(vh);
				setExternalStorageDirectoryV19(vh.value, f.getAbsolutePath());
			}
		}
	}

	public void initExternalStorageDirectory() {
		if (Build.VERSION.SDK_INT < 19) {
			setExternalStorageDirectoryPre19(getInternalAppPath().getAbsolutePath());
		} else {
			File externalStorage = getExternal1AppPath();
			if (externalStorage != null && FileUtils.isWritable(externalStorage)) {
				setExternalStorageDirectoryV19(EXTERNAL_STORAGE_TYPE_EXTERNAL_FILE,
						getExternal1AppPath().getAbsolutePath());
			} else {
				setExternalStorageDirectoryV19(EXTERNAL_STORAGE_TYPE_INTERNAL_FILE,
						getInternalAppPath().getAbsolutePath());
			}
		}
	}

	public File getExternalStorageDirectory() {
		return getExternalStorageDirectory(null);
	}

	public File getExternalStorageDirectory(ValueHolder<Integer> type) {
		if (Build.VERSION.SDK_INT < 19) {
			return getExternalStorageDirectoryPre19();
		} else {
			return getExternalStorageDirectoryV19(type);
		}
	}

	public File getInternalAppPath() {
		if (Build.VERSION.SDK_INT >= 21) {
			File fl = getNoBackupPath();
			if (fl != null) {
				return fl;
			}
		}
		return ctx.getFilesDir();
	}

	@TargetApi(19)
	public File getExternal1AppPath() {
		File[] externals = ctx.getExternalFilesDirs(null);
		if (externals != null && externals.length > 0) {
			return externals[0];
		} else {
			return null;
		}
	}

	@TargetApi(21)
	private File getNoBackupPath() {
		return ctx.getNoBackupFilesDir();
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public File getExternalStorageDirectoryV19(ValueHolder<Integer> tp) {
		int type = settingsAPI.getInt(globalPreferences, EXTERNAL_STORAGE_DIR_TYPE_V19, -1);
		File location = getDefaultLocationV19();
		if (type == -1) {
			if (FileUtils.isWritable(location)) {
				if (tp != null) {
					tp.value = settingsAPI.contains(globalPreferences, EXTERNAL_STORAGE_DIR_V19) ?
							EXTERNAL_STORAGE_TYPE_SPECIFIED :
							EXTERNAL_STORAGE_TYPE_DEFAULT;
				}
				return location;
			}
			File[] external = ctx.getExternalFilesDirs(null);
			if (external != null && external.length > 0 && external[0] != null) {
				location = external[0];
				if (tp != null) {
					tp.value = EXTERNAL_STORAGE_TYPE_EXTERNAL_FILE;
				}
			} else {
				File[] obbDirs = ctx.getObbDirs();
				if (obbDirs != null && obbDirs.length > 0 && obbDirs[0] != null) {
					location = obbDirs[0];
					if (tp != null) {
						tp.value = EXTERNAL_STORAGE_TYPE_OBB;
					}
				} else {
					location = getInternalAppPath();
					if (tp != null) {
						tp.value = EXTERNAL_STORAGE_TYPE_INTERNAL_FILE;
					}
				}
			}
		}
		return location;
	}

	public File getDefaultLocationV19() {
		String location = settingsAPI.getString(globalPreferences, EXTERNAL_STORAGE_DIR_V19,
				getExternalStorageDirectoryPre19().getAbsolutePath());
		return new File(location);
	}

	public boolean isExternalStorageDirectoryTypeSpecifiedV19() {
		return settingsAPI.contains(globalPreferences, EXTERNAL_STORAGE_DIR_TYPE_V19);
	}

	public int getExternalStorageDirectoryTypeV19() {
		return settingsAPI.getInt(globalPreferences, EXTERNAL_STORAGE_DIR_TYPE_V19, -1);
	}

	public boolean isExternalStorageDirectorySpecifiedV19() {
		return settingsAPI.contains(globalPreferences, EXTERNAL_STORAGE_DIR_V19);
	}

	public String getExternalStorageDirectoryV19() {
		return settingsAPI.getString(globalPreferences, EXTERNAL_STORAGE_DIR_V19, null);
	}

	public File getExternalStorageDirectoryPre19() {
		String defaultLocation = Environment.getExternalStorageDirectory().getAbsolutePath();
		File rootFolder = new File(settingsAPI.getString(globalPreferences, EXTERNAL_STORAGE_DIR,
				defaultLocation));
		return new File(rootFolder, IndexConstants.APP_DIR);
	}

	public File getDefaultInternalStorage() {
		return new File(Environment.getExternalStorageDirectory(), IndexConstants.APP_DIR);
	}

	public boolean setExternalStorageDirectoryV19(int type, String externalStorageDir) {
		return settingsAPI.edit(globalPreferences).
				putInt(EXTERNAL_STORAGE_DIR_TYPE_V19, type).
				putString(EXTERNAL_STORAGE_DIR_V19, externalStorageDir).commit();
	}

	@SuppressLint("NewApi")
	@Nullable
	public File getSecondaryStorage() {
		if (Build.VERSION.SDK_INT < 19) {
			return getExternalStorageDirectoryPre19();
		} else {
			File[] externals = ctx.getExternalFilesDirs(null);
			for (File file : externals) {
				if (file != null && !file.getAbsolutePath().contains("emulated")) {
					return file;
				}
			}
		}
		return null;
	}

	public void setExternalStorageDirectory(int type, String directory) {
		if (Build.VERSION.SDK_INT < 19) {
			setExternalStorageDirectoryPre19(directory);
		} else {
			setExternalStorageDirectoryV19(type, directory);
		}

	}

	public boolean isExternalStorageDirectorySpecifiedPre19() {
		return settingsAPI.contains(globalPreferences, EXTERNAL_STORAGE_DIR);
	}

	public boolean setExternalStorageDirectoryPre19(String externalStorageDir) {
		return settingsAPI.edit(globalPreferences).putString(EXTERNAL_STORAGE_DIR, externalStorageDir).commit();
	}

	public Object getGlobalPreferences() {
		return globalPreferences;
	}


	// This value is a key for saving last known location shown on the map
	public static final String LAST_KNOWN_MAP_LAT = "last_known_map_lat"; //$NON-NLS-1$
	public static final String LAST_KNOWN_MAP_LON = "last_known_map_lon"; //$NON-NLS-1$
	public static final String LAST_KNOWN_MAP_ZOOM = "last_known_map_zoom"; //$NON-NLS-1$
	public static final String LAST_KNOWN_MAP_ELEVATION = "last_known_map_elevation"; //$NON-NLS-1$

	public static final String MAP_LABEL_TO_SHOW = "map_label_to_show"; //$NON-NLS-1$
	public static final String MAP_LAT_TO_SHOW = "map_lat_to_show"; //$NON-NLS-1$
	public static final String MAP_LON_TO_SHOW = "map_lon_to_show"; //$NON-NLS-1$
	public static final String MAP_ZOOM_TO_SHOW = "map_zoom_to_show"; //$NON-NLS-1$

	public LatLon getLastKnownMapLocation() {
		float lat = settingsAPI.getFloat(globalPreferences, LAST_KNOWN_MAP_LAT, 0);
		float lon = settingsAPI.getFloat(globalPreferences, LAST_KNOWN_MAP_LON, 0);
		return new LatLon(lat, lon);
	}

	public boolean isLastKnownMapLocation() {
		return settingsAPI.contains(globalPreferences, LAST_KNOWN_MAP_LAT);
	}


	public LatLon getAndClearMapLocationToShow() {
		if (!settingsAPI.contains(globalPreferences, MAP_LAT_TO_SHOW)) {
			return null;
		}
		float lat = settingsAPI.getFloat(globalPreferences, MAP_LAT_TO_SHOW, 0);
		float lon = settingsAPI.getFloat(globalPreferences, MAP_LON_TO_SHOW, 0);
		settingsAPI.edit(globalPreferences).remove(MAP_LAT_TO_SHOW).commit();
		return new LatLon(lat, lon);
	}

	public PointDescription getAndClearMapLabelToShow(LatLon l) {
		String label = settingsAPI.getString(globalPreferences, MAP_LABEL_TO_SHOW, null);
		settingsAPI.edit(globalPreferences).remove(MAP_LABEL_TO_SHOW).commit();
		if (label != null) {
			return PointDescription.deserializeFromString(label, l);
		} else {
			return null;
		}
	}

	public void setSearchRequestToShow(String request) {
		this.searchRequestToShow = request;
	}

	public String getAndClearSearchRequestToShow() {
		String searchRequestToShow = this.searchRequestToShow;
		this.searchRequestToShow = null;
		return searchRequestToShow;
	}

	public Object getAndClearObjectToShow() {
		Object objectToShow = this.objectToShow;
		this.objectToShow = null;
		return objectToShow;
	}

	public boolean getAndClearEditObjectToShow() {
		boolean res = this.editObjectToShow;
		this.editObjectToShow = false;
		return res;
	}

	public int getMapZoomToShow() {
		return settingsAPI.getInt(globalPreferences, MAP_ZOOM_TO_SHOW, 5);
	}

	public void setMapLocationToShow(double latitude, double longitude, int zoom, PointDescription pointDescription,
									 boolean addToHistory, Object toShow) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences);
		edit.putFloat(MAP_LAT_TO_SHOW, (float) latitude);
		edit.putFloat(MAP_LON_TO_SHOW, (float) longitude);
		if (pointDescription != null) {
			edit.putString(MAP_LABEL_TO_SHOW, PointDescription.serializeToString(pointDescription));
		} else {
			edit.remove(MAP_LABEL_TO_SHOW);
		}
		edit.putInt(MAP_ZOOM_TO_SHOW, zoom);
		edit.commit();
		objectToShow = toShow;
		if (addToHistory) {
			SearchHistoryHelper.getInstance(ctx).addNewItemToHistory(latitude, longitude, pointDescription);
		}
	}

	public void setEditObjectToShow() {
		this.editObjectToShow = true;
	}

	public void setMapLocationToShow(double latitude, double longitude, int zoom) {
		setMapLocationToShow(latitude, longitude, zoom, null, false, null);
	}

	public void setMapLocationToShow(double latitude, double longitude, int zoom, PointDescription historyDescription) {
		setMapLocationToShow(latitude, longitude, zoom, historyDescription, true, null);
	}

	// Do not use that method if you want to show point on map. Use setMapLocationToShow
	public void setLastKnownMapLocation(double latitude, double longitude) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences);
		edit.putFloat(LAST_KNOWN_MAP_LAT, (float) latitude);
		edit.putFloat(LAST_KNOWN_MAP_LON, (float) longitude);
		edit.commit();
	}

	public int getLastKnownMapZoom() {
		return settingsAPI.getInt(globalPreferences, LAST_KNOWN_MAP_ZOOM, 5);
	}

	public void setLastKnownMapZoom(int zoom) {
		settingsAPI.edit(globalPreferences).putInt(LAST_KNOWN_MAP_ZOOM, zoom).commit();
	}

	public float getLastKnownMapElevation() {
		return settingsAPI.getFloat(globalPreferences, LAST_KNOWN_MAP_ELEVATION, 90);
	}

	public void setLastKnownMapElevation(float elevation) {
		settingsAPI.edit(globalPreferences).putFloat(LAST_KNOWN_MAP_ELEVATION, elevation).commit();
	}

	public final static String POINT_NAVIGATE_LAT = "point_navigate_lat"; //$NON-NLS-1$
	public final static String POINT_NAVIGATE_LON = "point_navigate_lon"; //$NON-NLS-1$
	public final static String POINT_NAVIGATE_ROUTE = "point_navigate_route_integer"; //$NON-NLS-1$
	public final static int NAVIGATE = 1;
	public final static String POINT_NAVIGATE_DESCRIPTION = "point_navigate_description"; //$NON-NLS-1$
	public final static String START_POINT_LAT = "start_point_lat"; //$NON-NLS-1$
	public final static String START_POINT_LON = "start_point_lon"; //$NON-NLS-1$
	public final static String START_POINT_DESCRIPTION = "start_point_description"; //$NON-NLS-1$

	public final static String INTERMEDIATE_POINTS = "intermediate_points"; //$NON-NLS-1$
	public final static String INTERMEDIATE_POINTS_DESCRIPTION = "intermediate_points_description"; //$NON-NLS-1$

	public final static String POINT_NAVIGATE_LAT_BACKUP = "point_navigate_lat_backup"; //$NON-NLS-1$
	public final static String POINT_NAVIGATE_LON_BACKUP = "point_navigate_lon_backup"; //$NON-NLS-1$
	public final static String POINT_NAVIGATE_DESCRIPTION_BACKUP = "point_navigate_description_backup"; //$NON-NLS-1$
	public final static String START_POINT_LAT_BACKUP = "start_point_lat_backup"; //$NON-NLS-1$
	public final static String START_POINT_LON_BACKUP = "start_point_lon_backup"; //$NON-NLS-1$
	public final static String START_POINT_DESCRIPTION_BACKUP = "start_point_description_backup"; //$NON-NLS-1$
	public final static String INTERMEDIATE_POINTS_BACKUP = "intermediate_points_backup"; //$NON-NLS-1$
	public final static String INTERMEDIATE_POINTS_DESCRIPTION_BACKUP = "intermediate_points_description_backup"; //$NON-NLS-1$
	public final static String MY_LOC_POINT_LAT = "my_loc_point_lat";
	public final static String MY_LOC_POINT_LON = "my_loc_point_lon";
	public final static String MY_LOC_POINT_DESCRIPTION = "my_loc_point_description";

	public static final String IMPASSABLE_ROAD_POINTS = "impassable_road_points";
	public static final String IMPASSABLE_ROADS_DESCRIPTIONS = "impassable_roads_descriptions";
	public static final String IMPASSABLE_ROADS_IDS = "impassable_roads_ids";
	public static final String IMPASSABLE_ROADS_DIRECTIONS = "impassable_roads_directions";
	public static final String IMPASSABLE_ROADS_APP_MODE_KEYS = "impassable_roads_app_mode_keys";

	public void backupPointToStart() {
		settingsAPI.edit(globalPreferences)
				.putFloat(START_POINT_LAT_BACKUP, settingsAPI.getFloat(globalPreferences, START_POINT_LAT, 0))
				.putFloat(START_POINT_LON_BACKUP, settingsAPI.getFloat(globalPreferences, START_POINT_LON, 0))
				.putString(START_POINT_DESCRIPTION_BACKUP, settingsAPI.getString(globalPreferences, START_POINT_DESCRIPTION, ""))
				.commit();
	}

	private void backupPointToNavigate() {
		settingsAPI.edit(globalPreferences)
				.putFloat(POINT_NAVIGATE_LAT_BACKUP, settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LAT, 0))
				.putFloat(POINT_NAVIGATE_LON_BACKUP, settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LON, 0))
				.putString(POINT_NAVIGATE_DESCRIPTION_BACKUP, settingsAPI.getString(globalPreferences, POINT_NAVIGATE_DESCRIPTION, ""))
				.commit();
	}

	private void backupIntermediatePoints() {
		settingsAPI.edit(globalPreferences)
				.putString(INTERMEDIATE_POINTS_BACKUP, settingsAPI.getString(globalPreferences, INTERMEDIATE_POINTS, ""))
				.putString(INTERMEDIATE_POINTS_DESCRIPTION_BACKUP, settingsAPI.getString(globalPreferences, INTERMEDIATE_POINTS_DESCRIPTION, ""))
				.commit();
	}

	public void backupTargetPoints() {
		backupPointToStart();
		backupPointToNavigate();
		backupIntermediatePoints();
	}

	public void restoreTargetPoints() {
		settingsAPI.edit(globalPreferences)
				.putFloat(START_POINT_LAT, settingsAPI.getFloat(globalPreferences, START_POINT_LAT_BACKUP, 0))
				.putFloat(START_POINT_LON, settingsAPI.getFloat(globalPreferences, START_POINT_LON_BACKUP, 0))
				.putString(START_POINT_DESCRIPTION, settingsAPI.getString(globalPreferences, START_POINT_DESCRIPTION_BACKUP, ""))
				.putFloat(POINT_NAVIGATE_LAT, settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LAT_BACKUP, 0))
				.putFloat(POINT_NAVIGATE_LON, settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LON_BACKUP, 0))
				.putString(POINT_NAVIGATE_DESCRIPTION, settingsAPI.getString(globalPreferences, POINT_NAVIGATE_DESCRIPTION_BACKUP, ""))
				.putString(INTERMEDIATE_POINTS, settingsAPI.getString(globalPreferences, INTERMEDIATE_POINTS_BACKUP, ""))
				.putString(INTERMEDIATE_POINTS_DESCRIPTION, settingsAPI.getString(globalPreferences, INTERMEDIATE_POINTS_DESCRIPTION_BACKUP, ""))
				.commit();
	}

	public boolean restorePointToStart() {
		if (settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LAT_BACKUP, 0) == 0) {
			settingsAPI.edit(globalPreferences)
					.putFloat(START_POINT_LAT, settingsAPI.getFloat(globalPreferences, START_POINT_LAT_BACKUP, 0))
					.putFloat(START_POINT_LON, settingsAPI.getFloat(globalPreferences, START_POINT_LON_BACKUP, 0))
					.commit();
			return true;
		} else {
			return false;
		}
	}

	public LatLon getPointToNavigate() {
		float lat = settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LAT, 0);
		float lon = settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LON, 0);
		if (lat == 0 && lon == 0) {
			return null;
		}
		return new LatLon(lat, lon);
	}

	public LatLon getPointToStart() {
		float lat = settingsAPI.getFloat(globalPreferences, START_POINT_LAT, 0);
		float lon = settingsAPI.getFloat(globalPreferences, START_POINT_LON, 0);
		if (lat == 0 && lon == 0) {
			return null;
		}
		return new LatLon(lat, lon);
	}

	public PointDescription getStartPointDescription() {
		return PointDescription.deserializeFromString(
				settingsAPI.getString(globalPreferences, START_POINT_DESCRIPTION, ""), getPointToStart());
	}

	public PointDescription getPointNavigateDescription() {
		return PointDescription.deserializeFromString(
				settingsAPI.getString(globalPreferences, POINT_NAVIGATE_DESCRIPTION, ""), getPointToNavigate());
	}

	public LatLon getPointToNavigateBackup() {
		float lat = settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LAT_BACKUP, 0);
		float lon = settingsAPI.getFloat(globalPreferences, POINT_NAVIGATE_LON_BACKUP, 0);
		if (lat == 0 && lon == 0) {
			return null;
		}
		return new LatLon(lat, lon);
	}

	public LatLon getPointToStartBackup() {
		float lat = settingsAPI.getFloat(globalPreferences, START_POINT_LAT_BACKUP, 0);
		float lon = settingsAPI.getFloat(globalPreferences, START_POINT_LON_BACKUP, 0);
		if (lat == 0 && lon == 0) {
			return null;
		}
		return new LatLon(lat, lon);
	}

	public PointDescription getStartPointDescriptionBackup() {
		return PointDescription.deserializeFromString(
				settingsAPI.getString(globalPreferences, START_POINT_DESCRIPTION_BACKUP, ""), getPointToStart());
	}

	public PointDescription getPointNavigateDescriptionBackup() {
		return PointDescription.deserializeFromString(
				settingsAPI.getString(globalPreferences, POINT_NAVIGATE_DESCRIPTION_BACKUP, ""), getPointToNavigate());
	}

	public LatLon getMyLocationToStart() {
		float lat = settingsAPI.getFloat(globalPreferences, MY_LOC_POINT_LAT, 0);
		float lon = settingsAPI.getFloat(globalPreferences, MY_LOC_POINT_LON, 0);
		if (lat == 0 && lon == 0) {
			return null;
		}
		return new LatLon(lat, lon);
	}

	public PointDescription getMyLocationToStartDescription() {
		return PointDescription.deserializeFromString(
				settingsAPI.getString(globalPreferences, MY_LOC_POINT_DESCRIPTION, ""), getMyLocationToStart());
	}

	public void setMyLocationToStart(double latitude, double longitude, PointDescription p) {
		settingsAPI.edit(globalPreferences).putFloat(MY_LOC_POINT_LAT, (float) latitude).putFloat(MY_LOC_POINT_LON, (float) longitude).commit();
		settingsAPI.edit(globalPreferences).putString(MY_LOC_POINT_DESCRIPTION, PointDescription.serializeToString(p)).commit();
	}

	public void clearMyLocationToStart() {
		settingsAPI.edit(globalPreferences).remove(MY_LOC_POINT_LAT).remove(MY_LOC_POINT_LON).
				remove(MY_LOC_POINT_DESCRIPTION).commit();
	}

	public int isRouteToPointNavigateAndClear() {
		int vl = settingsAPI.getInt(globalPreferences, POINT_NAVIGATE_ROUTE, 0);
		if (vl != 0) {
			settingsAPI.edit(globalPreferences).remove(POINT_NAVIGATE_ROUTE).commit();
		}
		return vl;
	}


	public boolean clearIntermediatePoints() {
		return settingsAPI.edit(globalPreferences).remove(INTERMEDIATE_POINTS).remove(INTERMEDIATE_POINTS_DESCRIPTION).commit();
	}

	public final CommonPreference<Boolean> USE_INTERMEDIATE_POINTS_NAVIGATION =
			new BooleanPreference(this, "use_intermediate_points_navigation", false).makeGlobal().cache();


	public List<String> getIntermediatePointDescriptions(int sz) {
		return intermediatePointsStorage.getPointDescriptions(sz);
	}

	public List<LatLon> getIntermediatePoints() {
		return intermediatePointsStorage.getPoints();
	}

	public boolean insertIntermediatePoint(double latitude, double longitude, PointDescription historyDescription, int index) {
		return intermediatePointsStorage.insertPoint(latitude, longitude, historyDescription, index);
	}

	public boolean updateIntermediatePoint(double latitude, double longitude, PointDescription historyDescription) {
		return intermediatePointsStorage.updatePoint(latitude, longitude, historyDescription);
	}

	public boolean deleteIntermediatePoint(int index) {
		return intermediatePointsStorage.deletePoint(index);
	}

	public boolean saveIntermediatePoints(List<LatLon> ps, List<String> ds) {
		return intermediatePointsStorage.savePoints(ps, ds);
	}

	public boolean clearPointToNavigate() {
		return settingsAPI.edit(globalPreferences).remove(POINT_NAVIGATE_LAT).remove(POINT_NAVIGATE_LON).
				remove(POINT_NAVIGATE_DESCRIPTION).commit();
	}

	public boolean clearPointToStart() {
		return settingsAPI.edit(globalPreferences).remove(START_POINT_LAT).remove(START_POINT_LON).
				remove(START_POINT_DESCRIPTION).commit();
	}

	public boolean setPointToNavigate(double latitude, double longitude, PointDescription p) {
		boolean add = settingsAPI.edit(globalPreferences).putFloat(POINT_NAVIGATE_LAT, (float) latitude).putFloat(POINT_NAVIGATE_LON, (float) longitude).commit();
		settingsAPI.edit(globalPreferences).putString(POINT_NAVIGATE_DESCRIPTION, PointDescription.serializeToString(p)).commit();
		if (add) {
			if (p != null && !p.isSearchingAddress(ctx)) {
				SearchHistoryHelper.getInstance(ctx).addNewItemToHistory(latitude, longitude, p);
			}
		}
		backupTargetPoints();
		return add;
	}

	public boolean setPointToStart(double latitude, double longitude, PointDescription p) {
		boolean add = settingsAPI.edit(globalPreferences).putFloat(START_POINT_LAT, (float) latitude).putFloat(START_POINT_LON, (float) longitude).commit();
		settingsAPI.edit(globalPreferences).putString(START_POINT_DESCRIPTION, PointDescription.serializeToString(p)).commit();
		backupTargetPoints();
		return add;
	}

	public boolean navigateDialog() {
		return settingsAPI.edit(globalPreferences).putInt(POINT_NAVIGATE_ROUTE, NAVIGATE).commit();
	}

	public List<AvoidRoadInfo> getImpassableRoadPoints() {
		return impassableRoadsStorage.getImpassableRoadsInfo();
	}

	public boolean addImpassableRoad(AvoidRoadInfo avoidRoadInfo) {
		return impassableRoadsStorage.addImpassableRoadInfo(avoidRoadInfo);
	}

	public boolean updateImpassableRoadInfo(AvoidRoadInfo avoidRoadInfo) {
		return impassableRoadsStorage.updateImpassableRoadInfo(avoidRoadInfo);
	}

	public boolean removeImpassableRoad(int index) {
		return impassableRoadsStorage.deletePoint(index);
	}

	public boolean removeImpassableRoad(LatLon latLon) {
		return impassableRoadsStorage.deletePoint(latLon);
	}

	public boolean moveImpassableRoad(LatLon latLonEx, LatLon latLonNew) {
		return impassableRoadsStorage.movePoint(latLonEx, latLonNew);
	}

	/**
	 * quick actions prefs
	 */

	public static final String QUICK_FAB_MARGIN_X_PORTRAIT_MARGIN = "quick_fab_margin_x_portrait_margin";
	public static final String QUICK_FAB_MARGIN_Y_PORTRAIT_MARGIN = "quick_fab_margin_y_portrait_margin";
	public static final String QUICK_FAB_MARGIN_X_LANDSCAPE_MARGIN = "quick_fab_margin_x_landscape_margin";
	public static final String QUICK_FAB_MARGIN_Y_LANDSCAPE_MARGIN = "quick_fab_margin_y_landscape_margin";

	public final CommonPreference<Boolean> QUICK_ACTION = new BooleanPreference(this, "quick_action_state", false).makeProfile();

	public final CommonPreference<String> QUICK_ACTION_LIST = new StringPreference(this, "quick_action_list", "").makeGlobal().makeShared();

	public final CommonPreference<Boolean> IS_QUICK_ACTION_TUTORIAL_SHOWN = new BooleanPreference(this, "quick_action_tutorial", false).makeGlobal().makeShared();

	private final CommonPreference<Integer> QUICK_ACTION_FAB_MARGIN_X_PORTRAIT = new IntPreference(this, QUICK_FAB_MARGIN_X_PORTRAIT_MARGIN, 0).makeProfile();
	private final CommonPreference<Integer> QUICK_ACTION_FAB_MARGIN_Y_PORTRAIT = new IntPreference(this, QUICK_FAB_MARGIN_Y_PORTRAIT_MARGIN, 0).makeProfile();
	private final CommonPreference<Integer> QUICK_ACTION_FAB_MARGIN_X_LANDSCAPE_MARGIN = new IntPreference(this, QUICK_FAB_MARGIN_X_LANDSCAPE_MARGIN, 0).makeProfile();
	private final CommonPreference<Integer> QUICK_ACTION_FAB_MARGIN_Y_LANDSCAPE_MARGIN = new IntPreference(this, QUICK_FAB_MARGIN_Y_LANDSCAPE_MARGIN, 0).makeProfile();

	public boolean setPortraitFabMargin(int x, int y) {
		return QUICK_ACTION_FAB_MARGIN_X_PORTRAIT.set(x) && QUICK_ACTION_FAB_MARGIN_Y_PORTRAIT.set(y);
	}

	public boolean setLandscapeFabMargin(int x, int y) {
		return QUICK_ACTION_FAB_MARGIN_X_LANDSCAPE_MARGIN.set(x) && QUICK_ACTION_FAB_MARGIN_Y_LANDSCAPE_MARGIN.set(y);
	}

	public Pair<Integer, Integer> getPortraitFabMargin() {
		if (QUICK_ACTION_FAB_MARGIN_X_PORTRAIT.isSet() && QUICK_ACTION_FAB_MARGIN_Y_PORTRAIT.isSet()) {
			return new Pair<>(QUICK_ACTION_FAB_MARGIN_X_PORTRAIT.get(), QUICK_ACTION_FAB_MARGIN_Y_PORTRAIT.get());
		}
		return null;
	}

	public Pair<Integer, Integer> getLandscapeFabMargin() {
		if (QUICK_ACTION_FAB_MARGIN_X_LANDSCAPE_MARGIN.isSet() && QUICK_ACTION_FAB_MARGIN_Y_LANDSCAPE_MARGIN.isSet()) {
			return new Pair<>(QUICK_ACTION_FAB_MARGIN_X_LANDSCAPE_MARGIN.get(), QUICK_ACTION_FAB_MARGIN_Y_LANDSCAPE_MARGIN.get());
		}
		return null;
	}

	/**
	 * the location of a parked car
	 */

	public static final String LAST_SEARCHED_REGION = "last_searched_region"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_CITY = "last_searched_city"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_CITY_NAME = "last_searched_city_name"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_POSTCODE = "last_searched_postcode"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_STREET = "last_searched_street"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_BUILDING = "last_searched_building"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_INTERSECTED_STREET = "last_searched_intersected_street"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_LAT = "last_searched_lat"; //$NON-NLS-1$
	public static final String LAST_SEARCHED_LON = "last_searched_lon"; //$NON-NLS-1$

	public LatLon getLastSearchedPoint() {
		if (settingsAPI.contains(globalPreferences, LAST_SEARCHED_LAT) && settingsAPI.contains(globalPreferences, LAST_SEARCHED_LON)) {
			return new LatLon(settingsAPI.getFloat(globalPreferences, LAST_SEARCHED_LAT, 0),
					settingsAPI.getFloat(globalPreferences, LAST_SEARCHED_LON, 0));
		}
		return null;
	}

	public boolean setLastSearchedPoint(LatLon l) {
		if (l == null) {
			return settingsAPI.edit(globalPreferences).remove(LAST_SEARCHED_LAT).remove(LAST_SEARCHED_LON).commit();
		} else {
			return setLastSearchedPoint(l.getLatitude(), l.getLongitude());
		}
	}

	public boolean setLastSearchedPoint(double lat, double lon) {
		return settingsAPI.edit(globalPreferences).putFloat(LAST_SEARCHED_LAT, (float) lat).
				putFloat(LAST_SEARCHED_LON, (float) lon).commit();
	}

	public String getLastSearchedRegion() {
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_REGION, ""); //$NON-NLS-1$
	}

	public boolean setLastSearchedRegion(String region, LatLon l) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences).putString(LAST_SEARCHED_REGION, region).putLong(LAST_SEARCHED_CITY, -1).
				putString(LAST_SEARCHED_CITY_NAME, "").putString(LAST_SEARCHED_POSTCODE, "").
				putString(LAST_SEARCHED_STREET, "").putString(LAST_SEARCHED_BUILDING, ""); //$NON-NLS-1$ //$NON-NLS-2$
		if (settingsAPI.contains(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET)) {
			edit.putString(LAST_SEARCHED_INTERSECTED_STREET, ""); //$NON-NLS-1$
		}
		boolean res = edit.commit();
		setLastSearchedPoint(l);
		return res;
	}

	public String getLastSearchedPostcode() {
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_POSTCODE, null);
	}

	public boolean setLastSearchedPostcode(String postcode, LatLon point) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences).putLong(LAST_SEARCHED_CITY, -1).putString(LAST_SEARCHED_STREET, "") //$NON-NLS-1$
				.putString(LAST_SEARCHED_BUILDING, "").putString(LAST_SEARCHED_POSTCODE, postcode); //$NON-NLS-1$
		if (settingsAPI.contains(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET)) {
			edit.putString(LAST_SEARCHED_INTERSECTED_STREET, ""); //$NON-NLS-1$
		}
		boolean res = edit.commit();
		setLastSearchedPoint(point);
		return res;
	}

	public Long getLastSearchedCity() {
		return settingsAPI.getLong(globalPreferences, LAST_SEARCHED_CITY, -1);
	}

	public String getLastSearchedCityName() {
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_CITY_NAME, "");
	}

	public boolean setLastSearchedCity(Long cityId, String name, LatLon point) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences).putLong(LAST_SEARCHED_CITY, cityId).putString(LAST_SEARCHED_CITY_NAME, name).
				putString(LAST_SEARCHED_STREET, "").putString(LAST_SEARCHED_BUILDING, "").putString(LAST_SEARCHED_POSTCODE, ""); //$NON-NLS-1$
		//edit.remove(LAST_SEARCHED_POSTCODE);
		if (settingsAPI.contains(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET)) {
			edit.putString(LAST_SEARCHED_INTERSECTED_STREET, ""); //$NON-NLS-1$
		}
		boolean res = edit.commit();
		setLastSearchedPoint(point);
		return res;
	}

	public String getLastSearchedStreet() {
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_STREET, ""); //$NON-NLS-1$
	}

	public boolean setLastSearchedStreet(String street, LatLon point) {
		SettingsEditor edit = settingsAPI.edit(globalPreferences).putString(LAST_SEARCHED_STREET, street).putString(LAST_SEARCHED_BUILDING, ""); //$NON-NLS-1$
		if (settingsAPI.contains(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET)) {
			edit.putString(LAST_SEARCHED_INTERSECTED_STREET, ""); //$NON-NLS-1$
		}
		boolean res = edit.commit();
		setLastSearchedPoint(point);
		return res;
	}

	public String getLastSearchedBuilding() {
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_BUILDING, ""); //$NON-NLS-1$
	}

	public boolean setLastSearchedBuilding(String building, LatLon point) {
		boolean res = settingsAPI.edit(globalPreferences).putString(LAST_SEARCHED_BUILDING, building).remove(LAST_SEARCHED_INTERSECTED_STREET).commit();
		setLastSearchedPoint(point);
		return res;
	}

	public String getLastSearchedIntersectedStreet() {
		if (!settingsAPI.contains(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET)) {
			return null;
		}
		return settingsAPI.getString(globalPreferences, LAST_SEARCHED_INTERSECTED_STREET, ""); //$NON-NLS-1$
	}

	public boolean setLastSearchedIntersectedStreet(String street, LatLon l) {
		setLastSearchedPoint(l);
		return settingsAPI.edit(globalPreferences).putString(LAST_SEARCHED_INTERSECTED_STREET, street).commit();
	}

	public final OsmandPreference<String> LAST_SELECTED_GPX_TRACK_FOR_NEW_POINT = new StringPreference(this, "last_selected_gpx_track_for_new_point", null).makeGlobal().cache();

	// Avoid using this property, probably you need to use PoiFiltersHelper.getSelectedPoiFilters()
	public final OsmandPreference<String> SELECTED_POI_FILTER_FOR_MAP = new StringPreference(this, "selected_poi_filter_for_map", null).makeProfile().cache();

	public Set<String> getSelectedPoiFilters() {
		Set<String> result = new LinkedHashSet<>();
		String filtersId = SELECTED_POI_FILTER_FOR_MAP.get();
		if (filtersId != null && !filtersId.trim().isEmpty()) {
			Collections.addAll(result, filtersId.split(","));
		}
		return result;
	}

	public void setSelectedPoiFilters(final Set<String> poiFilters) {
		SELECTED_POI_FILTER_FOR_MAP.set(android.text.TextUtils.join(",", poiFilters));
	}

	public final ListStringPreference POI_FILTERS_ORDER = (ListStringPreference)
			new ListStringPreference(this, "poi_filters_order", null, ",,").makeProfile().cache();

	public final ListStringPreference INACTIVE_POI_FILTERS = (ListStringPreference)
			new ListStringPreference(this, "inactive_poi_filters", null, ",,").makeProfile().cache();

	public final ContextMenuItemsPreference DRAWER_ITEMS =
			(ContextMenuItemsPreference) new ContextMenuItemsPreference(this, "drawer_items", DRAWER_ITEM_ID_SCHEME, ContextMenuItemsSettings.getDrawerDefaultInstance())
					.makeProfile().cache();

	public final ContextMenuItemsPreference CONFIGURE_MAP_ITEMS =
			(ContextMenuItemsPreference) new ContextMenuItemsPreference(this, "configure_map_items", CONFIGURE_MAP_ITEM_ID_SCHEME, new ContextMenuItemsSettings())
					.makeProfile().cache();

	public final ContextMenuItemsPreference CONTEXT_MENU_ACTIONS_ITEMS =
			(ContextMenuItemsPreference) new ContextMenuItemsPreference(this, "context_menu_items", MAP_CONTEXT_MENU_ACTIONS, new MainContextMenuItemsSettings())
					.makeProfile().cache();

	public final List<ContextMenuItemsPreference> CONTEXT_MENU_ITEMS_PREFERENCES = Arrays.asList(DRAWER_ITEMS, CONFIGURE_MAP_ITEMS, CONTEXT_MENU_ACTIONS_ITEMS);

	@Nullable
	public ContextMenuItemsPreference getContextMenuItemsPreference(@NonNull String id) {
		for (ContextMenuItemsPreference preference : CONTEXT_MENU_ITEMS_PREFERENCES) {
			if (id.startsWith(preference.getIdScheme())) {
				return preference;
			}
		}
		return null;
	}

	public static final String VOICE_PROVIDER_NOT_USE = "VOICE_PROVIDER_NOT_USE";

	public static final String[] TTS_AVAILABLE_VOICES = new String[]{
			"de", "en", "es", "fr", "it", "ja", "nl", "pl", "pt", "ru", "zh"
	};
	// this value string is synchronized with settings_pref.xml preference name
	// this value could localized
	public final OsmandPreference<String> VOICE_PROVIDER = new StringPreference(this, "voice_provider", null) {
		protected String getDefaultValue() {

			Configuration config = ctx.getResources().getConfiguration();
			for (String lang : TTS_AVAILABLE_VOICES) {
				if (lang.equals(config.locale.getLanguage())) {
					return lang + IndexConstants.VOICE_PROVIDER_SUFFIX;
				}
			}
			return VOICE_PROVIDER_NOT_USE;
		}
	}.makeProfile();


	// this value string is synchronized with settings_pref.xml preference name
	public final CommonPreference<String> RENDERER = new StringPreference(this, "renderer", RendererRegistry.DEFAULT_RENDER) {

		@Override
		protected boolean setValue(Object prefs, String val) {
			if (val == null) {
				val = RendererRegistry.DEFAULT_RENDER;
			}
			RenderingRulesStorage loaded = ctx.getRendererRegistry().getRenderer(val);
			if (loaded != null) {
				return super.setValue(prefs, val);
			}
			return false;
		}

	}.makeProfile();

	{
		RENDERER.setModeDefaultValue(ApplicationMode.BOAT, RendererRegistry.NAUTICAL_RENDER);
		RENDERER.setModeDefaultValue(ApplicationMode.SKI, RendererRegistry.WINTER_SKI_RENDER);
	}

	public CommonPreference<String> getCustomRenderProperty(String attrName) {
		if (!customRendersProps.containsKey(attrName)) {
			customRendersProps.put(attrName, new StringPreference(this, RENDERER_PREFERENCE_PREFIX + attrName, "").makeProfile());
		}
		return customRendersProps.get(attrName);
	}

	{
		getCustomRenderProperty("appMode");
		getCustomRenderProperty("defAppMode");
	}

	public CommonPreference<Boolean> getCustomRenderBooleanProperty(String attrName) {
		if (!customBooleanRendersProps.containsKey(attrName)) {
			customBooleanRendersProps.put(attrName, new BooleanPreference(this, RENDERER_PREFERENCE_PREFIX + attrName, false).makeProfile());
		}
		return customBooleanRendersProps.get(attrName);
	}

	public CommonPreference<String> getCustomRoutingProperty(String attrName, String defValue) {
		if (!customRoutingProps.containsKey(attrName)) {
			customRoutingProps.put(attrName, new StringPreference(this, ROUTING_PREFERENCE_PREFIX + attrName, defValue).makeProfile());
		}
		return customRoutingProps.get(attrName);
	}


	public CommonPreference<Boolean> getCustomRoutingBooleanProperty(String attrName, boolean defaulfValue) {
		if (!customBooleanRoutingProps.containsKey(attrName)) {
			customBooleanRoutingProps.put(attrName, new BooleanStringPreference(this, ROUTING_PREFERENCE_PREFIX + attrName, defaulfValue).makeProfile());
		}
		return customBooleanRoutingProps.get(attrName);
	}

	public final CommonPreference<Float> ROUTE_RECALCULATION_DISTANCE = new FloatPreference(this, "routing_recalc_distance", 0.f).makeProfile();
	public final CommonPreference<Float> ROUTE_STRAIGHT_ANGLE = new FloatPreference(this, "routing_straight_angle", 30.f).makeProfile();

	public final ListStringPreference CUSTOM_ROUTE_LINE_COLORS = (ListStringPreference) new ListStringPreference(this, "custom_route_line_colors", null, ",").makeShared().makeGlobal();
	public final CommonPreference<Integer> ROUTE_LINE_COLOR_DAY = new IntPreference(this, "route_line_color", 0).cache().makeProfile();
	public final CommonPreference<Integer> ROUTE_LINE_COLOR_NIGHT = new IntPreference(this, "route_line_color_night", 0).cache().makeProfile();
	public final CommonPreference<GradientScaleType> ROUTE_LINE_GRADIENT = new EnumStringPreference<>(this, "route_line_gradient", null, new GradientScaleType[] {GradientScaleType.ALTITUDE, GradientScaleType.SLOPE}).cache().makeProfile();
	public final CommonPreference<String> ROUTE_LINE_WIDTH = new StringPreference(this, "route_line_width", null).makeProfile();

	public final OsmandPreference<Boolean> USE_OSM_LIVE_FOR_ROUTING = new BooleanPreference(this, "enable_osmc_routing", true).makeProfile();

	public final OsmandPreference<Boolean> USE_OSM_LIVE_FOR_PUBLIC_TRANSPORT = new BooleanPreference(this, "enable_osmc_public_transport", false).makeProfile();

	public final OsmandPreference<Boolean> VOICE_MUTE = new BooleanPreference(this, "voice_mute", false).makeProfile().cache();

	// for background service
	public final OsmandPreference<Boolean> MAP_ACTIVITY_ENABLED = new BooleanPreference(this, "map_activity_enabled", false).makeGlobal();

	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<Boolean> SAFE_MODE = new BooleanPreference(this, "safe_mode", false).makeGlobal().makeShared();
	public final OsmandPreference<Boolean> PT_SAFE_MODE = new BooleanPreference(this, "pt_safe_mode", false).makeProfile();
	public final OsmandPreference<Boolean> NATIVE_RENDERING_FAILED = new BooleanPreference(this, "native_rendering_failed_init", false).makeGlobal();

	public final OsmandPreference<Boolean> USE_OPENGL_RENDER = new BooleanPreference(this, "use_opengl_render",
			false /*Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH*/
	).makeGlobal().makeShared().cache();

	public final OsmandPreference<Boolean> OPENGL_RENDER_FAILED = new BooleanPreference(this, "opengl_render_failed", false).makeGlobal().cache();


	// this value string is synchronized with settings_pref.xml preference name
	public final OsmandPreference<String> CONTRIBUTION_INSTALL_APP_DATE = new StringPreference(this, "CONTRIBUTION_INSTALL_APP_DATE", null).makeGlobal();

	public final OsmandPreference<Integer> COORDINATES_FORMAT = new IntPreference(this, "coordinates_format", PointDescription.FORMAT_DEGREES).makeProfile();

	public final OsmandPreference<Boolean> FOLLOW_THE_ROUTE = new BooleanPreference(this, "follow_to_route", false).makeGlobal();
	public final OsmandPreference<String> FOLLOW_THE_GPX_ROUTE = new StringPreference(this, "follow_gpx", null).makeGlobal();

	public final OsmandPreference<String> SELECTED_TRAVEL_BOOK = new StringPreference(this, "selected_travel_book", "").makeGlobal().makeShared();

	public final ListStringPreference DISPLAYED_TRANSPORT_SETTINGS = (ListStringPreference)
			new ListStringPreference(this, "displayed_transport_settings", null, ",").makeProfile();

	public final OsmandPreference<Boolean> SHOW_ARRIVAL_TIME_OTHERWISE_EXPECTED_TIME =
			new BooleanPreference(this, "show_arrival_time", true).makeProfile();

	public final OsmandPreference<Boolean> SHOW_INTERMEDIATE_ARRIVAL_TIME_OTHERWISE_EXPECTED_TIME =
			new BooleanPreference(this, "show_intermediate_arrival_time", true).makeProfile();

	public final OsmandPreference<Boolean> SHOW_RELATIVE_BEARING_OTHERWISE_REGULAR_BEARING =
			new BooleanPreference(this, "show_relative_bearing", true).makeProfile();

	public final OsmandPreference<Long> AGPS_DATA_LAST_TIME_DOWNLOADED =
			new LongPreference(this, "agps_data_downloaded", 0).makeGlobal();

	// Live Updates
	public final OsmandPreference<Boolean> IS_LIVE_UPDATES_ON =
			new BooleanPreference(this, "is_live_updates_on", false).makeGlobal().makeShared();
	public final OsmandPreference<Integer> LIVE_UPDATES_RETRIES =
			new IntPreference(this, "live_updates_retryes", 2).makeGlobal();

	// UI boxes
	public final CommonPreference<Boolean> TRANSPARENT_MAP_THEME =
			new BooleanPreference(this, "transparent_map_theme", true).makeProfile();

	{
		TRANSPARENT_MAP_THEME.setModeDefaultValue(ApplicationMode.CAR, false);
		TRANSPARENT_MAP_THEME.setModeDefaultValue(ApplicationMode.BICYCLE, false);
		TRANSPARENT_MAP_THEME.setModeDefaultValue(ApplicationMode.PEDESTRIAN, true);
	}

	public final CommonPreference<Boolean> SHOW_STREET_NAME =
			new BooleanPreference(this, "show_street_name", false).makeProfile();

	{
		SHOW_STREET_NAME.setModeDefaultValue(ApplicationMode.DEFAULT, false);
		SHOW_STREET_NAME.setModeDefaultValue(ApplicationMode.CAR, true);
		SHOW_STREET_NAME.setModeDefaultValue(ApplicationMode.BICYCLE, false);
		SHOW_STREET_NAME.setModeDefaultValue(ApplicationMode.PEDESTRIAN, false);
	}

	public static final int OSMAND_DARK_THEME = 0;
	public static final int OSMAND_LIGHT_THEME = 1;
	public static final int SYSTEM_DEFAULT_THEME = 2;

	public static final int NO_EXTERNAL_DEVICE = 0;
	public static final int GENERIC_EXTERNAL_DEVICE = 1;
	public static final int WUNDERLINQ_EXTERNAL_DEVICE = 2;
	public static final int PARROT_EXTERNAL_DEVICE = 3;

	public final CommonPreference<Integer> SEARCH_TAB =
			new IntPreference(this, "SEARCH_TAB", 0).makeGlobal().cache();

	public final CommonPreference<Integer> FAVORITES_TAB =
			new IntPreference(this, "FAVORITES_TAB", 0).makeGlobal().cache();

	public final CommonPreference<Integer> OSMAND_THEME =
			new IntPreference(this, "osmand_theme", OSMAND_LIGHT_THEME) {
				@Override
				public void readFromJson(JSONObject json, ApplicationMode appMode) throws JSONException {
					Integer theme = parseString(json.getString(getId()));
					if (theme == SYSTEM_DEFAULT_THEME && !isSupportSystemDefaultTheme()) {
						theme = OSMAND_LIGHT_THEME;
					}
					setModeValue(appMode, theme);
				}
			}.makeProfile().cache();

	public final OsmandPreference<Boolean> OPEN_ONLY_HEADER_STATE_ROUTE_CALCULATED =
			new BooleanPreference(this, "open_only_header_route_calculated", false).makeProfile();

	public boolean isLightActionBar() {
		return isLightContent();
	}

	public boolean isLightContent() {
		return isLightContentForMode(APPLICATION_MODE.get());
	}

	public boolean isLightContentForMode(ApplicationMode mode) {
		if (isSupportSystemDefaultTheme() && OSMAND_THEME.getModeValue(mode) == SYSTEM_DEFAULT_THEME) {
			return isLightSystemDefaultTheme();
		}
		return OSMAND_THEME.getModeValue(mode) != OSMAND_DARK_THEME;
	}

	public boolean isLightSystemDefaultTheme() {
		Configuration config = ctx.getResources().getConfiguration();
		int systemNightModeState = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
		return systemNightModeState != Configuration.UI_MODE_NIGHT_YES;
	}

	public boolean isSystemDefaultThemeUsed() {
		return isSystemDefaultThemeUsedForMode(APPLICATION_MODE.get());
	}

	public boolean isSystemDefaultThemeUsedForMode(ApplicationMode mode) {
		return isSupportSystemDefaultTheme() && OSMAND_THEME.getModeValue(mode) == SYSTEM_DEFAULT_THEME;
	}

	public boolean isSupportSystemDefaultTheme() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
	}

	public final CommonPreference<Boolean> FLUORESCENT_OVERLAYS =
			new BooleanPreference(this, "fluorescent_overlays", false).makeGlobal().makeShared().cache();


//	public final OsmandPreference<Integer> NUMBER_OF_FREE_DOWNLOADS_V2 = new IntPreference("free_downloads_v2", 0).makeGlobal();

	public final OsmandPreference<Integer> NUMBER_OF_FREE_DOWNLOADS = new IntPreference(this, NUMBER_OF_FREE_DOWNLOADS_ID, 0).makeGlobal();

	// For RateUsDialog
	public final OsmandPreference<Long> LAST_DISPLAY_TIME =
			new LongPreference(this, "last_display_time", 0).makeGlobal().cache();

	public final OsmandPreference<Long> LAST_CHECKED_UPDATES =
			new LongPreference(this, "last_checked_updates", 0).makeGlobal();

	public final OsmandPreference<Integer> NUMBER_OF_APP_STARTS_ON_DISLIKE_MOMENT =
			new IntPreference(this, "number_of_app_starts_on_dislike_moment", 0).makeGlobal().cache();

	public final OsmandPreference<RateUsState> RATE_US_STATE =
			new EnumStringPreference<>(this, "rate_us_state", RateUsState.INITIAL_STATE, RateUsState.values()).makeGlobal();

	public final CommonPreference<String> CUSTOM_APP_MODES_KEYS =
			new StringPreference(this, "custom_app_modes_keys", "").makeGlobal().makeShared().cache();

	public Set<String> getCustomAppModesKeys() {
		String appModesKeys = CUSTOM_APP_MODES_KEYS.get();
		StringTokenizer toks = new StringTokenizer(appModesKeys, ",");
		Set<String> res = new LinkedHashSet<String>();
		while (toks.hasMoreTokens()) {
			res.add(toks.nextToken());
		}
		return res;
	}

	public void setQuickActions(HashMap<String, Boolean> quickActions, ApplicationMode mode) {
		if (!QUICK_ACTION.isSetForMode(mode)) {
			Boolean actionState = quickActions.get(mode.getStringKey());
			if (actionState == null) {
				actionState = QUICK_ACTION.getDefaultValue();
			}
			setPreference(QUICK_ACTION.getId(), actionState, mode);
		}
	}
}
