package net.osmand.plus.routing;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.map.WorldRegion;
import net.osmand.plus.onlinerouting.OnlineRoutingHelper;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine.OnlineRoutingResponse;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.osmand.util.MapUtils.calculateMidPoint;

public class MissingMapsHelper {
	private final static Log LOG = PlatformUtil.getLog(MissingMapsHelper.class);

	private static final int MIN_STRAIGHT_DIST = 20000;
	private final RouteCalculationParams params;

	public MissingMapsHelper(@NonNull RouteCalculationParams params) {
		this.params = params;
	}

	public boolean isAnyPointOnWater(@NonNull List<Location> points) throws IOException {
		for (int i = 0; i < points.size(); i++) {
			final Location point = points.get(i);
			List<WorldRegion> downloadRegions = params.ctx.getRegions().getWorldRegionsAt(
					new LatLon(point.getLatitude(), point.getLongitude()));
			if (downloadRegions.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	public List<Location> getDistributedPathPoints(@NonNull List<Location> points) {
		List<Location> result = new ArrayList<>(points.size());
		for (Location point : points) {
			result.add(new Location(point));
		}
		for (int i = 0; i < result.size() - 1; i++) {
			int nextIndex = i + 1;
			while (result.get(i).distanceTo(result.get(nextIndex)) > MIN_STRAIGHT_DIST) {
				Location location = calculateMidPoint(result.get(i), result.get(nextIndex));
				result.add(nextIndex, location);
			}
		}
		return removeDensePoints(result);
	}

	public List<Location> getStartFinishIntermediatePoints() {
		List<Location> points = new ArrayList<>();
		points.add(new Location("", params.start.getLatitude(), params.start.getLongitude()));
		if (params.intermediates != null) {
			for (LatLon l : params.intermediates) {
				points.add(new Location("", l.getLatitude(), l.getLongitude()));
			}
		}
		points.add(new Location("", params.end.getLatitude(), params.end.getLongitude()));
		return points;
	}

	public List<Location> findOnlineRoutePoints() throws IOException, JSONException {
		List<LatLon> points = new ArrayList<>();
		List<Location> routeLocation = new ArrayList<>();
		OnlineRoutingHelper onlineRoutingHelper = params.ctx.getOnlineRoutingHelper();
		List<Location> location = getStartFinishIntermediatePoints();
		for (Location e : location) {
			points.add(new LatLon(e.getLatitude(), e.getLongitude()));
		}
		OnlineRoutingEngine engine = onlineRoutingHelper.startOsrmEngine(params.mode);
		if (engine != null) {
			OnlineRoutingResponse response = onlineRoutingHelper.calculateRouteOnline(
					engine, points, params.leftSide);
			if (response != null) {
				routeLocation.addAll(response.getRoute());
			}
		}
		return removeDensePoints(routeLocation);
	}

	@NonNull
	public List<WorldRegion> getMissingMaps(@NonNull List<Location> points) throws IOException {
		Set<WorldRegion> result = new LinkedHashSet<>();
		for (int i = 0; i < points.size(); i++) {
			final Location l = points.get(i);
			LatLon latLonPoint = new LatLon(l.getLatitude(), l.getLongitude());
			List<WorldRegion> worldRegions = params.ctx.getRegions().getWorldRegionsAt(latLonPoint);
			boolean addMaps = true;
			List<WorldRegion> maps = new ArrayList<>();
			for (WorldRegion region : worldRegions) {
				String mapName = region.getRegionDownloadName();
				String countryMapName = region.getSuperregion().getRegionDownloadName();
				List<WorldRegion> subregions = region.getSubregions();
				boolean isDownloaded = params.ctx.getResourceManager().checkIfObjectDownloaded(mapName);
				boolean isCountry = Algorithms.isEmpty(countryMapName) && !subregions.isEmpty();
				if (!isDownloaded) {
					if (!isCountry) {
						maps.add(region);
					}
				} else {
					addMaps = false;
				}
			}
			if (addMaps) {
				result.addAll(maps);
			}
		}
		return new ArrayList<>(result);
	}

	@NonNull
	private List<Location> removeDensePoints(@NonNull List<Location> routeLocation) {
		List<Location> mapsBasedOnPoints = new ArrayList<>();
		for (int i = 0, j = i + 1; j < routeLocation.size() - 1; j++) {
			if (routeLocation.get(i).distanceTo(routeLocation.get(j)) >= MIN_STRAIGHT_DIST) {
				mapsBasedOnPoints.add(routeLocation.get(j));
				i = j;
			}
		}
		return mapsBasedOnPoints;
	}

	public static class MissingMapsOnlineSearchTask extends AsyncTask<Void, Void, List<WorldRegion>> {

		private final RouteCalculationParams params;
		private final OnlineSearchMissingMapsListener listener;

		public interface OnlineSearchMissingMapsListener {
			void onMissingMapsOnlineSearchComplete(@Nullable List<WorldRegion> missingMaps);
		}

		public MissingMapsOnlineSearchTask(@NonNull RouteCalculationParams params,
										   @Nullable OnlineSearchMissingMapsListener listener) {
			this.params = params;
			this.listener = listener;
		}

		@Override
		protected List<WorldRegion> doInBackground(Void... voids) {
			try {
				MissingMapsHelper missingMapsHelper = new MissingMapsHelper(params);
				List<Location> onlinePoints = missingMapsHelper.findOnlineRoutePoints();
				return missingMapsHelper.getMissingMaps(onlinePoints);
			} catch (IOException | JSONException e) {
				LOG.error(e.getMessage(), e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(@Nullable List<WorldRegion> worldRegions) {
			if (listener != null) {
				listener.onMissingMapsOnlineSearchComplete(worldRegions);
			}
		}
	}
}
