package net.osmand.plus.mapmarkers;

import android.util.Pair;

import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.map.WorldRegion;
import net.osmand.plus.routing.RouteCalculationProgressCallback;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.routing.RouteCalculationParams;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.router.RouteCalculationProgress;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MarkersPlanRouteContext {

	private static final int MAX_DIST_FOR_SNAP_TO_ROAD = 500 * 1000; // 500 km

	private OsmandApplication app;

	private final Map<Pair<WptPt, WptPt>, List<WptPt>> snappedToRoadPoints = new ConcurrentHashMap<>();
	private final Queue<Pair<WptPt, WptPt>> snapToRoadPairsToCalculate = new ConcurrentLinkedQueue<>();
	private final TrkSegment snapTrkSegment = new TrkSegment();

	private ApplicationMode snappedMode;
	private RouteCalculationProgress calculationProgress;
	private int calculatedPairs;

	private PlanRouteProgressListener listener;
	private boolean progressBarVisible;
	private boolean fragmentVisible;
	private boolean markersListOpened;
	private boolean adjustMapOnStart = true;
	private boolean navigationFromMarkers;

	Map<Pair<WptPt, WptPt>, List<WptPt>> getSnappedToRoadPoints() {
		return snappedToRoadPoints;
	}

	TrkSegment getSnapTrkSegment() {
		return snapTrkSegment;
	}

	public ApplicationMode getSnappedMode() {
		return snappedMode;
	}

	void setSnappedMode(ApplicationMode snappedMode) {
		this.snappedMode = snappedMode;
	}

	public PlanRouteProgressListener getListener() {
		return listener;
	}

	public void setListener(PlanRouteProgressListener listener) {
		this.listener = listener;
	}

	boolean isProgressBarVisible() {
		return progressBarVisible;
	}

	void setProgressBarVisible(boolean progressBarVisible) {
		this.progressBarVisible = progressBarVisible;
	}

	public boolean isFragmentVisible() {
		return fragmentVisible;
	}

	public void setFragmentVisible(boolean fragmentVisible) {
		this.fragmentVisible = fragmentVisible;
	}

	public boolean isMarkersListOpened() {
		return markersListOpened;
	}

	public void setMarkersListOpened(boolean markersListOpened) {
		this.markersListOpened = markersListOpened;
	}

	public boolean isAdjustMapOnStart() {
		return adjustMapOnStart;
	}

	public void setAdjustMapOnStart(boolean adjustMapOnStart) {
		this.adjustMapOnStart = adjustMapOnStart;
	}

	public boolean isNavigationFromMarkers() {
		return navigationFromMarkers;
	}

	public void setNavigationFromMarkers(boolean navigationFromMarkers) {
		this.navigationFromMarkers = navigationFromMarkers;
	}

	public MarkersPlanRouteContext(OsmandApplication app) {
		this.app = app;
	}

	void cancelSnapToRoad() {
		listener.hideProgressBar(true);
		snapToRoadPairsToCalculate.clear();
		if (calculationProgress != null) {
			calculationProgress.isCancelled = true;
		}
	}

	private void scheduleRouteCalculateIfNotEmpty(List<WptPt> points) {
		if (points.isEmpty()) {
			return;
		}
		findPairsToCalculate(points);
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (!snapToRoadPairsToCalculate.isEmpty() && !routingHelper.isRouteBeingCalculated()) {
			routingHelper.startRouteCalculationThread(getParams());
			app.runInUIThread(new Runnable() {
				@Override
				public void run() {
					listener.showProgressBar();
				}
			});
		}
	}

	private void findPairsToCalculate(List<WptPt> points) {
		snapToRoadPairsToCalculate.clear();
		for (int i = 0; i < points.size() - 1; i++) {
			Pair<WptPt, WptPt> pair = new Pair<>(points.get(i), points.get(i + 1));
			if (snappedToRoadPoints.get(pair) == null) {
				double dist = MapUtils.getDistance(pair.first.lat, pair.first.lon, pair.second.lat, pair.second.lon);
				if (dist < MAX_DIST_FOR_SNAP_TO_ROAD) {
					snapToRoadPairsToCalculate.add(pair);
				}
			}
		}
	}

	void recreateSnapTrkSegment(boolean adjustMap) {
		snapTrkSegment.points.clear();
		List<WptPt> points = getPointsToCalculate();
		if (snappedMode == ApplicationMode.DEFAULT) {
			snapTrkSegment.points.addAll(points);
		} else if (points.size() > 1) {
			for (int i = 0; i < points.size() - 1; i++) {
				Pair<WptPt, WptPt> pair = new Pair<>(points.get(i), points.get(i + 1));
				List<WptPt> pts = snappedToRoadPoints.get(pair);
				if (pts != null) {
					snapTrkSegment.points.addAll(pts);
				} else {
					scheduleRouteCalculateIfNotEmpty(points);
					snapTrkSegment.points.addAll(Arrays.asList(pair.first, pair.second));
				}
			}
		}
		listener.showMarkersRouteOnMap(adjustMap);
		app.runInUIThread(new Runnable() {
			@Override
			public void run() {
				listener.updateText();
			}
		});
	}

	private List<WptPt> getPointsToCalculate() {
		MapMarkersHelper markersHelper = app.getMapMarkersHelper();
		List<WptPt> points = new LinkedList<>();
		Location myLoc = app.getLocationProvider().getLastStaleKnownLocation();
		if (markersHelper.isStartFromMyLocation() && myLoc != null) {
			addWptPt(points, myLoc.getLatitude(), myLoc.getLongitude());
		}
		for (LatLon l : markersHelper.getSelectedMarkersLatLon()) {
			addWptPt(points, l.getLatitude(), l.getLongitude());
		}
		if (app.getSettings().ROUTE_MAP_MARKERS_ROUND_TRIP.get() && !points.isEmpty()) {
			WptPt l = points.get(0);
			addWptPt(points, l.getLatitude(), l.getLongitude());
		}
		return points;
	}

	private void addWptPt(List<WptPt> points, double lat, double lon) {
		WptPt pt = new WptPt();
		pt.lat = lat;
		pt.lon = lon;
		points.add(pt);
	}

	private RouteCalculationParams getParams() {
		final Pair<WptPt, WptPt> currentPair = snapToRoadPairsToCalculate.poll();

		Location start = new Location("");
		start.setLatitude(currentPair.first.getLatitude());
		start.setLongitude(currentPair.first.getLongitude());

		LatLon end = new LatLon(currentPair.second.getLatitude(), currentPair.second.getLongitude());

		final RouteCalculationParams params = new RouteCalculationParams();
		params.start = start;
		params.end = end;
		RoutingHelper.applyApplicationSettings(params, app.getSettings(), snappedMode);
		params.mode = snappedMode;
		params.ctx = app;
		params.calculationProgress = calculationProgress = new RouteCalculationProgress();
		params.calculationProgressCallback = new RouteCalculationProgressCallback() {

			@Override
			public void start() {

			}

			@Override
			public void updateProgress(int progress) {
				int pairs = calculatedPairs + snapToRoadPairsToCalculate.size();
				if (pairs != 0) {
					int pairProgress = 100 / pairs;
					progress = calculatedPairs * pairProgress + progress / pairs;
				}
				listener.updateProgress(progress);
			}

			@Override
			public void requestPrivateAccessRouting() {
			}

			@Override
			public void updateMissingMaps(@Nullable List<WorldRegion> missingMaps, boolean onlineSearch) {
			}

			@Override
			public void finish() {
				calculatedPairs = 0;
			}
		};
		params.alternateResultListener = new RouteCalculationParams.RouteCalculationResultListener() {
			@Override
			public void onRouteCalculated(RouteCalculationResult route) {
				List<Location> locations = route.getRouteLocations();
				ArrayList<WptPt> pts = new ArrayList<>(locations.size());
				for (Location loc : locations) {
					WptPt pt = new WptPt();
					pt.lat = loc.getLatitude();
					pt.lon = loc.getLongitude();
					pts.add(pt);
				}
				calculatedPairs++;
				snappedToRoadPoints.put(currentPair, pts);
				recreateSnapTrkSegment(false);
				app.runInUIThread(new Runnable() {
					@Override
					public void run() {
						listener.refresh();
					}
				});
				if (!snapToRoadPairsToCalculate.isEmpty()) {
					app.getRoutingHelper().startRouteCalculationThread(getParams());
				} else {
					app.runInUIThread(new Runnable() {
						@Override
						public void run() {
							listener.hideProgressBar(false);
						}
					});
				}
			}
		};

		return params;
	}

	interface PlanRouteProgressListener {

		void showProgressBar();

		void updateProgress(int progress);

		void hideProgressBar(boolean canceled);

		void refresh();

		void updateText();

		void showMarkersRouteOnMap(boolean adjustMap);
	}
}
