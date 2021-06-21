package net.osmand.plus.onlinerouting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.osm.io.NetworkUtils;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.Version;
import net.osmand.plus.onlinerouting.engine.EngineType;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine.OnlineRoutingResponse;
import net.osmand.plus.routing.RouteCalculationParams;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import static net.osmand.util.Algorithms.GZIP_FILE_SIGNATURE;
import static net.osmand.util.Algorithms.ZIP_FILE_SIGNATURE;
import static net.osmand.util.Algorithms.isEmpty;

public class OnlineRoutingHelper {

	private static final Log LOG = PlatformUtil.getLog(OnlineRoutingHelper.class);

	private final OsmandApplication app;
	private final OsmandSettings settings;
	private final Map<String, OnlineRoutingEngine> cachedEngines;

	public OnlineRoutingHelper(@NonNull OsmandApplication app) {
		this.app = app;
		this.settings = app.getSettings();
		this.cachedEngines = loadSavedEngines();
	}

	@NonNull
	public List<OnlineRoutingEngine> getEngines() {
		return new ArrayList<>(cachedEngines.values());
	}

	public List<OnlineRoutingEngine> getOnlyCustomEngines() {
		List<OnlineRoutingEngine> engines = new ArrayList<>();
		for (OnlineRoutingEngine engine : getEngines()) {
			if (!engine.isPredefined()) {
				engines.add(engine);
			}
		}
		return engines;
	}

	@NonNull
	public List<OnlineRoutingEngine> getEnginesExceptMentionedKeys(@Nullable String... excludeKeys) {
		List<OnlineRoutingEngine> engines = getEngines();
		if (excludeKeys != null) {
			for (String key : excludeKeys) {
				OnlineRoutingEngine engine = getEngineByKey(key);
				engines.remove(engine);
			}
		}
		return engines;
	}

	@Nullable
	public OnlineRoutingEngine getEngineByKey(@Nullable String stringKey) {
		return cachedEngines.get(stringKey);
	}

	@Nullable
	public OnlineRoutingEngine getEngineByName(@Nullable String name) {
		for (OnlineRoutingEngine engine : getEngines()) {
			if (Algorithms.objectEquals(engine.getName(app), name)) {
				return engine;
			}
		}
		return null;
	}

	@Nullable
	public OnlineRoutingResponse calculateRouteOnline(@Nullable String stringKey,
	                                                  @NonNull List<LatLon> path,
	                                                  boolean leftSideNavigation) throws IOException, JSONException {
		OnlineRoutingEngine engine = getEngineByKey(stringKey);
		return engine != null ? calculateRouteOnline(engine, path, leftSideNavigation) : null;
	}

	@Nullable
	public OnlineRoutingResponse calculateRouteOnline(@NonNull OnlineRoutingEngine engine,
													  @NonNull List<LatLon> path,
													  boolean leftSideNavigation) throws IOException, JSONException {
		String url = engine.getFullUrl(path);
		String content = makeRequest(url);
		return engine.parseResponse(content, app, leftSideNavigation);
	}

	@NonNull
	public String makeRequest(@NonNull String url) throws IOException {
		HttpURLConnection connection = NetworkUtils.getHttpURLConnection(url);
		connection.setRequestProperty("User-Agent", Version.getFullVersion(app));
		StringBuilder content = new StringBuilder();
		BufferedReader reader;
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			reader = new BufferedReader(new InputStreamReader(getInputStream(connection)));
		} else {
			reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
		}
		String s;
		while ((s = reader.readLine()) != null) {
			content.append(s);
		}
		try {
			reader.close();
		} catch (IOException ignored) {
		}
		return content.toString();
	}

	@Nullable
	public OnlineRoutingEngine startOsrmEngine(@NonNull ApplicationMode mode) {
		boolean isCarBicycleFoot = mode.isDerivedRoutingFrom(ApplicationMode.CAR)
				|| mode.isDerivedRoutingFrom(ApplicationMode.BICYCLE)
				|| mode.isDerivedRoutingFrom(ApplicationMode.PEDESTRIAN);
		Map<String, String> paramsOnlineRouting = new HashMap<>();
		paramsOnlineRouting.put(EngineParameter.VEHICLE_KEY.name(), mode.getStringKey());
		if (isCarBicycleFoot) {
			return EngineType.OSRM_TYPE.newInstance(paramsOnlineRouting);
		} else {
			return null;
		}
	}

	private InputStream getInputStream(@NonNull HttpURLConnection connection) throws IOException {
		ByteArrayInputStream localIS = Algorithms.createByteArrayIS(connection.getInputStream());
		if (Algorithms.checkFileSignature(localIS, ZIP_FILE_SIGNATURE)) {
			ZipInputStream zipIS = new ZipInputStream(localIS);
			zipIS.getNextEntry(); // set position to reading for the first item
			return zipIS;
		} else if (Algorithms.checkFileSignature(localIS, GZIP_FILE_SIGNATURE)) {
			return new GZIPInputStream(localIS);
		}
		return localIS;
	}

	public void saveEngine(@NonNull OnlineRoutingEngine engine) {
		deleteInaccessibleParameters(engine);
		String key = createEngineKeyIfNeeded(engine);
		cachedEngines.put(key, engine);
		saveCacheToSettings();
	}

	public void deleteEngine(@NonNull OnlineRoutingEngine engine) {
		String stringKey = engine.getStringKey();
		deleteEngine(stringKey);
	}

	public void deleteEngine(@Nullable String stringKey) {
		if (stringKey != null) {
			cachedEngines.remove(stringKey);
			saveCacheToSettings();
		}
	}

	private void deleteInaccessibleParameters(@NonNull OnlineRoutingEngine engine) {
		for (EngineParameter key : EngineParameter.values()) {
			if (!engine.isParameterAllowed(key)) {
				engine.remove(key);
			}
		}
	}

	@NonNull
	private String createEngineKeyIfNeeded(@NonNull OnlineRoutingEngine engine) {
		String key = engine.get(EngineParameter.KEY);
		if (isEmpty(key)) {
			key = OnlineRoutingEngine.generateKey();
			engine.put(EngineParameter.KEY, key);
		}
		return key;
	}

	public long getLastModifiedTime() {
		return settings.ONLINE_ROUTING_ENGINES.getLastModifiedTime();
	}

	public void setLastModifiedTime(long lastModifiedTime) {
		settings.ONLINE_ROUTING_ENGINES.setLastModifiedTime(lastModifiedTime);
	}

	@NonNull
	private Map<String, OnlineRoutingEngine> loadSavedEngines() {
		Map<String, OnlineRoutingEngine> cachedEngines = new LinkedHashMap<>();
		for (OnlineRoutingEngine engine : readFromSettings()) {
			cachedEngines.put(engine.getStringKey(), engine);
		}
		return cachedEngines;
	}

	@NonNull
	private List<OnlineRoutingEngine> readFromSettings() {
		List<OnlineRoutingEngine> engines = new ArrayList<>();
		String jsonString = settings.ONLINE_ROUTING_ENGINES.get();
		if (!isEmpty(jsonString)) {
			try {
				JSONObject json = new JSONObject(jsonString);
				OnlineRoutingUtils.readFromJson(json, engines);
			} catch (JSONException | IllegalArgumentException e) {
				LOG.debug("Error when reading engines from JSON ", e);
			}
		}
		return engines;
	}

	private void saveCacheToSettings() {
		if (!isEmpty(cachedEngines)) {
			try {
				JSONObject json = new JSONObject();
				OnlineRoutingUtils.writeToJson(json, getEngines());
				settings.ONLINE_ROUTING_ENGINES.set(json.toString());
			} catch (JSONException e) {
				LOG.debug("Error when writing engines to JSON ", e);
			}
		} else {
			settings.ONLINE_ROUTING_ENGINES.set(null);
		}
	}
}
