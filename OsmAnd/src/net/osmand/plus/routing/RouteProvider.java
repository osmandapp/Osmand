package net.osmand.plus.routing;


import android.os.Bundle;
import android.util.Base64;

import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.Route;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.LocationsHolder;
import net.osmand.PlatformUtil;
import net.osmand.ResultMatcher;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.LatLon;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.TargetPointsHelper.TargetPoint;
import net.osmand.plus.onlinerouting.OnlineRoutingHelper;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine.OnlineRoutingResponse;
import net.osmand.plus.render.NativeOsmandLibrary;
import net.osmand.plus.routing.GPXRouteParams.GPXRouteParamsBuilder;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.router.GeneralRouter;
import net.osmand.router.GeneralRouter.RoutingParameter;
import net.osmand.router.GeneralRouter.RoutingParameterType;
import net.osmand.router.PrecalculatedRouteDirection;
import net.osmand.router.RouteExporter;
import net.osmand.router.RouteImporter;
import net.osmand.router.RoutePlannerFrontEnd;
import net.osmand.router.RoutePlannerFrontEnd.GpxPoint;
import net.osmand.router.RoutePlannerFrontEnd.GpxRouteApproximation;
import net.osmand.router.RoutePlannerFrontEnd.RouteCalculationMode;
import net.osmand.router.RouteResultPreparation;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RoutingConfiguration;
import net.osmand.router.RoutingConfiguration.Builder;
import net.osmand.router.RoutingContext;
import net.osmand.router.TurnType;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.json.JSONException;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import btools.routingapp.IBRouterService;


public class RouteProvider {

	private static final org.apache.commons.logging.Log log = PlatformUtil.getLog(RouteProvider.class);
	private static final int MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT = 60;
	private static final int ADDITIONAL_DISTANCE_FOR_START_POINT = 300;
	private static final int MIN_STRAIGHT_DIST = 50000;

	public static Location createLocation(WptPt pt){
		Location loc = new Location("OsmandRouteProvider");
		loc.setLatitude(pt.lat);
		loc.setLongitude(pt.lon);
		loc.setSpeed((float) pt.speed);
		if(!Double.isNaN(pt.ele)) {
			loc.setAltitude(pt.ele);
		}
		loc.setTime(pt.time);
		if(!Double.isNaN(pt.hdop)) {
			loc.setAccuracy((float) pt.hdop);
		}
		return loc;
	}

	public static List<Location> locationsFromWpts(List<WptPt> wpts) {
		List<Location> locations = new ArrayList<>(wpts.size());
		for (WptPt pt : wpts) {
			locations.add(createLocation(pt));
		}
		return locations;
	}

	public RouteCalculationResult calculateRouteImpl(RouteCalculationParams params) {
		long time = System.currentTimeMillis();
		if (params.start != null && params.end != null) {
			if(log.isInfoEnabled()){
				log.info("Start finding route from " + params.start + " to " + params.end +" using " + 
						params.mode.getRouteService().getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			try {
				RouteCalculationResult res;
				boolean calcGPXRoute = params.gpxRoute != null && (!params.gpxRoute.points.isEmpty()
						|| (params.gpxRoute.reverse && !params.gpxRoute.routePoints.isEmpty()));
				if (calcGPXRoute && !params.gpxRoute.calculateOsmAndRoute) {
					res = calculateGpxRoute(params);
				} else if (params.mode.getRouteService() == RouteService.OSMAND) {
					res = findVectorMapsRoute(params, calcGPXRoute);
				} else if (params.mode.getRouteService() == RouteService.BROUTER) {
					res = findBROUTERRoute(params);
				} else if (params.mode.getRouteService() == RouteService.ONLINE) {
					res = findOnlineRoute(params);
				} else if (params.mode.getRouteService() == RouteService.STRAIGHT ||
						params.mode.getRouteService() == RouteService.DIRECT_TO) {
					res = findStraightRoute(params);
				} else {
					res = new RouteCalculationResult("Selected route service is not available");
				}
				if(log.isInfoEnabled() ){
					log.info("Finding route contained " + res.getImmutableAllLocations().size() + " points for " + (System.currentTimeMillis() - time) + " ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				return res; 
			} catch (IOException e) {
				log.error("Failed to find route ", e); //$NON-NLS-1$
			} catch (ParserConfigurationException e) {
				log.error("Failed to find route ", e); //$NON-NLS-1$
			} catch (SAXException e) {
				log.error("Failed to find route ", e); //$NON-NLS-1$
			} catch (JSONException e) {
				log.error("Failed to find route ", e); //$NON-NLS-1$
			}
		}
		return new RouteCalculationResult(null);
	}

	public RouteCalculationResult recalculatePartOfflineRoute(RouteCalculationResult res, RouteCalculationParams params) {
		RouteCalculationResult rcr = params.previousToRecalculate;
		List<Location> locs = new ArrayList<Location>(rcr.getRouteLocations());
		try {
			int[] startI = new int[]{0};
			int[] endI = new int[]{locs.size()}; 
			locs = findStartAndEndLocationsFromRoute(locs, params.start, params.end, startI, endI);
			List<RouteDirectionInfo> directions = calcDirections(startI, endI, rcr.getRouteDirections());
			insertInitialSegment(params, locs, directions, true);
			res = new RouteCalculationResult(locs, directions, params, null, true);
		} catch (RuntimeException e) {
			log.error(e.getMessage(), e);
		}
		return res;
	}

	private RouteCalculationResult calculateGpxRoute(RouteCalculationParams routeParams) throws IOException {
		GPXRouteParams gpxParams = routeParams.gpxRoute;
		boolean calcWholeRoute = routeParams.gpxRoute.passWholeRoute && (routeParams.previousToRecalculate == null || !routeParams.onlyStartPointChanged);
		boolean calculateOsmAndRouteParts = gpxParams.calculateOsmAndRouteParts;
		boolean reverseRoutePoints = gpxParams.reverse && gpxParams.routePoints.size() > 1;
		List<RouteSegmentResult> gpxRouteResult = routeParams.gpxRoute.route;
		if (reverseRoutePoints) {
			List<Location> gpxRouteLocations = new ArrayList<>();
			List<RouteSegmentResult> gpxRoute = new ArrayList<>();
			WptPt firstGpxPoint = gpxParams.routePoints.get(0);
			Location start = new Location("", firstGpxPoint.getLatitude(), firstGpxPoint.getLongitude());

			for (int i = 1; i < gpxParams.routePoints.size(); i++) {
				WptPt gpxPoint = gpxParams.routePoints.get(i);
				ApplicationMode appMode = ApplicationMode.valueOfStringKey(gpxPoint.getProfileType(), ApplicationMode.DEFAULT);
				LatLon end = new LatLon(gpxPoint.getLatitude(), gpxPoint.getLongitude());

				RouteCalculationParams params = new RouteCalculationParams();
				params.start = start;
				params.end = end;
				RoutingHelper.applyApplicationSettings(params, routeParams.ctx.getSettings(), appMode);
				params.mode = appMode;
				params.ctx = routeParams.ctx;
				params.calculationProgress = routeParams.calculationProgress;
				RouteCalculationResult result = findOfflineRouteSegment(params, start, end);
				List<Location> locations = result.getRouteLocations();
				List<RouteSegmentResult> route = result.getOriginalRoute();
				if (Algorithms.isEmpty(route)) {
					if (Algorithms.isEmpty(locations)) {
						Location endLoc = new Location("");
						endLoc.setLatitude(end.getLatitude());
						endLoc.setLongitude(end.getLongitude());
						locations = Arrays.asList(start, endLoc);
					}
					route = Collections.singletonList(RoutePlannerFrontEnd.generateStraightLineSegment(
							routeParams.mode.getDefaultSpeed(), new LocationsHolder(locations).getLatLonList()));
				}
				gpxRouteLocations.addAll(locations);
				if (!gpxRouteLocations.isEmpty()) {
					gpxRouteLocations.remove(gpxRouteLocations.size() - 1);
				}
				gpxRoute.addAll(route);

				start = new Location("");
				start.setLatitude(end.getLatitude());
				start.setLongitude(end.getLongitude());
			}
			gpxParams.points = gpxRouteLocations;
			gpxParams.route = gpxRoute;
			gpxRouteResult = gpxRoute;
		}
		if (!Algorithms.isEmpty(gpxRouteResult)) {
			calculateGpxRouteTimeSpeed(routeParams, gpxRouteResult);
			if (calcWholeRoute && !calculateOsmAndRouteParts) {
				return new RouteCalculationResult(gpxRouteResult, routeParams.start, routeParams.end,
						routeParams.intermediates, routeParams.ctx, routeParams.leftSide, null, gpxParams.wpt, routeParams.mode, true);
			}
			RouteCalculationResult result = new RouteCalculationResult(gpxRouteResult, routeParams.start, routeParams.end,
						routeParams.intermediates, routeParams.ctx, routeParams.leftSide, null, gpxParams.wpt, routeParams.mode, false);
			List<Location> gpxRouteLocations = result.getImmutableAllLocations();
			int nearestGpxPointInd = calcWholeRoute ? 0 : findNearestGpxPointIndexFromRoute(gpxRouteLocations, routeParams.start, calculateOsmAndRouteParts);
			Location nearestGpxLocation = null;
			Location gpxLastLocation = !gpxRouteLocations.isEmpty() ? gpxRouteLocations.get(gpxRouteLocations.size() - 1) : null;
			List<RouteSegmentResult> firstSegmentRoute = null;
			List<RouteSegmentResult> lastSegmentRoute = null;
			List<RouteSegmentResult> gpxRoute;

			if (nearestGpxPointInd > 0) {
				nearestGpxLocation = gpxRouteLocations.get(nearestGpxPointInd);

			} else if (!gpxRouteLocations.isEmpty()) {
				nearestGpxLocation = gpxRouteLocations.get(0);
			}
			if (calculateOsmAndRouteParts && !reverseRoutePoints && !Algorithms.isEmpty(gpxParams.segmentEndpoints)) {
				gpxRoute = findRouteWithIntermediateSegments(routeParams, result, gpxRouteLocations, gpxParams.segmentEndpoints, nearestGpxPointInd);
			} else {
				if (nearestGpxPointInd > 0) {
					gpxRoute = result.getOriginalRoute(nearestGpxPointInd, false);
					if (!Algorithms.isEmpty(gpxRoute)) {
						LatLon startPoint = gpxRoute.get(0).getStartPoint();
						nearestGpxLocation = new Location("", startPoint.getLatitude(), startPoint.getLongitude());
					} else {
						nearestGpxLocation = new Location("", routeParams.end.getLatitude(), routeParams.end.getLongitude());
					}
				} else {
					gpxRoute = result.getOriginalRoute();
				}
			}

			if (calculateOsmAndRouteParts
					&& routeParams.start != null && nearestGpxLocation != null
					&& nearestGpxLocation.distanceTo(routeParams.start) > MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
				RouteCalculationResult firstSegmentResult = findOfflineRouteSegment(
						routeParams, routeParams.start, new LatLon(nearestGpxLocation.getLatitude(), nearestGpxLocation.getLongitude()));
				firstSegmentRoute = firstSegmentResult.getOriginalRoute();
			}
			if (calculateOsmAndRouteParts
					&& routeParams.end != null && gpxLastLocation != null
					&& MapUtils.getDistance(gpxLastLocation.getLatitude(), gpxLastLocation.getLongitude(),
					routeParams.end.getLatitude(), routeParams.end.getLongitude()) > MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
				RouteCalculationResult lastSegmentResult = findOfflineRouteSegment(routeParams, gpxLastLocation, routeParams.end);
				lastSegmentRoute = lastSegmentResult.getOriginalRoute();
			}
			List<RouteSegmentResult> newGpxRoute = new ArrayList<>();
			if (firstSegmentRoute != null && !firstSegmentRoute.isEmpty()) {
				newGpxRoute.addAll(firstSegmentRoute);
			}
			newGpxRoute.addAll(gpxRoute);
			if (lastSegmentRoute != null && !lastSegmentRoute.isEmpty()) {
				newGpxRoute.addAll(lastSegmentRoute);
			}
			return new RouteCalculationResult(newGpxRoute, routeParams.start, routeParams.end,
					routeParams.intermediates, routeParams.ctx, routeParams.leftSide, null, gpxParams.wpt, routeParams.mode, true);
		}

		if (routeParams.gpxRoute.useIntermediatePointsRTE) {
			return calculateOsmAndRouteWithIntermediatePoints(routeParams, gpxParams.points);
		}

		List<Location> gpxRoute ;
		int[] startI = new int[]{0};
		int[] endI = new int[]{gpxParams.points.size()};
		if (calcWholeRoute) {
			gpxRoute = gpxParams.points;
		} else {
			gpxRoute = findStartAndEndLocationsFromRoute(gpxParams.points,
					routeParams.start, routeParams.end, startI, endI);
		}
		final List<RouteDirectionInfo> inputDirections = gpxParams.directions;
		List<RouteDirectionInfo> gpxDirections = calcDirections(startI, endI, inputDirections);
		insertIntermediateSegments(routeParams, gpxRoute, gpxDirections, gpxParams.segmentEndpoints, calculateOsmAndRouteParts);
		insertInitialSegment(routeParams, gpxRoute, gpxDirections, calculateOsmAndRouteParts);
		insertFinalSegment(routeParams, gpxRoute, gpxDirections, calculateOsmAndRouteParts);

		for (RouteDirectionInfo info : gpxDirections) {
			// recalculate
			info.distance = 0;
			info.afterLeftTime = 0;			
		}

		return new RouteCalculationResult(gpxRoute, gpxDirections, routeParams,
				gpxParams.wpt, routeParams.gpxRoute.addMissingTurns);
	}

	private void calculateGpxRouteTimeSpeed(RouteCalculationParams params, List<RouteSegmentResult> gpxRouteResult) throws IOException {
		RoutingEnvironment env = calculateRoutingEnvironment(params, false, true);
		if (env != null) {
			RouteResultPreparation.calculateTimeSpeed(env.getCtx(), gpxRouteResult);
		}
	}

	private RouteCalculationResult calculateOsmAndRouteWithIntermediatePoints(RouteCalculationParams routeParams,
			final List<Location> intermediates) throws IOException {
		RouteCalculationParams rp = new RouteCalculationParams();
		rp.calculationProgress = routeParams.calculationProgress;
		rp.ctx = routeParams.ctx;
		rp.mode = routeParams.mode;
		rp.start = routeParams.start;
		rp.end = routeParams.end;
		rp.leftSide = routeParams.leftSide;
		rp.fast = routeParams.fast;
		rp.onlyStartPointChanged = routeParams.onlyStartPointChanged;
		rp.previousToRecalculate =  routeParams.previousToRecalculate;
		rp.intermediates = new ArrayList<LatLon>();
		int closest = 0;
		double maxDist = Double.POSITIVE_INFINITY;
		for (int i = 0; i < intermediates.size(); i++) {
			Location loc = intermediates.get(i);
			double dist = MapUtils.getDistance(loc.getLatitude(), loc.getLongitude(), rp.start.getLatitude(),
					rp.start.getLongitude());
			if (dist <= maxDist) {
				closest = i;
				maxDist = dist;
			}
		}
		for(int i = closest; i< intermediates.size() ; i++ ){
			Location w = intermediates.get(i);
			rp.intermediates.add(new LatLon(w.getLatitude(), w.getLongitude()));
		}

		if (routeParams.mode.getRouteService() == RouteService.BROUTER) {
			try {
				return findBROUTERRoute(rp);
			} catch (ParserConfigurationException e) {
				throw new IOException(e);
			} catch (SAXException e) {
				throw new IOException(e);
			}
		} else if (routeParams.mode.getRouteService() == RouteService.STRAIGHT ||
				routeParams.mode.getRouteService() == RouteService.DIRECT_TO) {
			return findStraightRoute(rp);
		}
		return findVectorMapsRoute(rp, false);
	}

	private List<RouteDirectionInfo> calcDirections(int[] startI, int[] endI,
			final List<RouteDirectionInfo> inputDirections) {
		List<RouteDirectionInfo> directions = new ArrayList<RouteDirectionInfo>();
		if (inputDirections != null) {
			for (RouteDirectionInfo info : inputDirections) {
				if (info.routePointOffset >= startI[0] && info.routePointOffset < endI[0]) {
					RouteDirectionInfo ch = new RouteDirectionInfo(info.getAverageSpeed(), info.getTurnType());
					ch.routePointOffset = info.routePointOffset - startI[0];
					if(info.routeEndPointOffset != 0) {
						ch.routeEndPointOffset = info.routeEndPointOffset - startI[0];
					}
					ch.setDescriptionRoute(info.getDescriptionRoutePart());
					ch.setRouteDataObject(info.getRouteDataObject());
					// Issue #2894
					if (info.getRef() != null && !"null".equals(info.getRef())) {
						ch.setRef(info.getRef());
					}
					if (info.getStreetName() != null && !"null".equals(info.getStreetName())) {
						ch.setStreetName(info.getStreetName());
					}
					if (info.getDestinationName() != null && !"null".equals(info.getDestinationName())) {
						ch.setDestinationName(info.getDestinationName());
					}

					directions.add(ch);
				}
			}
		}
		return directions;
	}

	private void insertFinalSegment(RouteCalculationParams routeParams, List<Location> points,
			List<RouteDirectionInfo> directions, boolean calculateOsmAndRouteParts) {
		if(points.size() > 0) {
			Location routeEnd = points.get(points.size() - 1);
			LatLon e = routeEnd == null ? null : new LatLon(routeEnd.getLatitude(), routeEnd.getLongitude());
			LatLon finalEnd = routeParams.end;
			if (finalEnd != null && MapUtils.getDistance(finalEnd, e) > MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
				RouteCalculationResult newRes = null;
				if (calculateOsmAndRouteParts) {
					newRes = findOfflineRouteSegment(routeParams, routeEnd, finalEnd);
				}
				List<Location> loct;
				List<RouteDirectionInfo> dt;
				if (newRes != null && newRes.isCalculated()) {
					loct = newRes.getImmutableAllLocations();
					dt = newRes.getImmutableAllDirections();
				} else {
					loct = new ArrayList<Location>();
					Location l = new Location("");
					l.setLatitude(finalEnd.getLatitude());
					l.setLongitude(finalEnd.getLongitude());
					loct.add(l);
					dt = new ArrayList<RouteDirectionInfo>();
				}
				for (RouteDirectionInfo i : dt) {
					i.routePointOffset += points.size();
				}
				points.addAll(loct);
				directions.addAll(dt);
			}
		}
	}

	public void insertInitialSegment(RouteCalculationParams routeParams, List<Location> points,
			List<RouteDirectionInfo> directions, boolean calculateOsmAndRouteParts) {
		Location realStart = routeParams.start;
		if (realStart != null && points.size() > 0 && realStart.distanceTo(points.get(0)) > MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
			Location trackStart = points.get(0);
			RouteCalculationResult newRes = null;
			if (calculateOsmAndRouteParts) {
				LatLon end = new LatLon(trackStart.getLatitude(), trackStart.getLongitude());
				newRes = findOfflineRouteSegment(routeParams, realStart, end);
			}
			List<Location> loct;
			List<RouteDirectionInfo> dt;
			if (newRes != null && newRes.isCalculated()) {
				loct = newRes.getImmutableAllLocations();
				dt = newRes.getImmutableAllDirections();
			} else {
				loct = new ArrayList<Location>();
				loct.add(realStart);
				dt = new ArrayList<RouteDirectionInfo>();
			}
			points.addAll(0, loct);
			directions.addAll(0, dt);
			for (int i = dt.size(); i < directions.size(); i++) {
				directions.get(i).routePointOffset += loct.size();
			}
		}
	}

	public void insertIntermediateSegments(RouteCalculationParams routeParams, List<Location> points, List<RouteDirectionInfo> directions,
										   List<Location> segmentEndpoints, boolean calculateOsmAndRouteParts) {
		for (int i = 0; i < segmentEndpoints.size() - 1; i++) {
			Location prevSegmentPoint = segmentEndpoints.get(i);
			Location newSegmentPoint = segmentEndpoints.get(i + 1);

			if (prevSegmentPoint.distanceTo(newSegmentPoint) <= MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
				continue;
			}
			int index = points.indexOf(newSegmentPoint);
			if (calculateOsmAndRouteParts && index != -1 && points.contains(prevSegmentPoint)) {
				LatLon end = new LatLon(newSegmentPoint.getLatitude(), newSegmentPoint.getLongitude());
				RouteCalculationResult newRes = findOfflineRouteSegment(routeParams, prevSegmentPoint, end);

				if (newRes != null && newRes.isCalculated()) {
					List<Location> loct = newRes.getImmutableAllLocations();
					List<RouteDirectionInfo> dt = newRes.getImmutableAllDirections();

					for (RouteDirectionInfo directionInfo : dt) {
						directionInfo.routePointOffset += points.size();
					}
					points.addAll(index, loct);
					directions.addAll(dt);
				}
			}
		}
	}

	public List<RouteSegmentResult> findRouteWithIntermediateSegments(RouteCalculationParams routeParams,
																	  RouteCalculationResult result,
																	  List<Location> gpxRouteLocations,
																	  List<Location> segmentEndpoints,
																	  int nearestGpxPointInd) {
		List<RouteSegmentResult> newGpxRoute = new ArrayList<>();

		int lastIndex = nearestGpxPointInd;
		for (int i = 0; i < segmentEndpoints.size() - 1; i++) {
			Location prevSegmentPoint = segmentEndpoints.get(i);
			Location newSegmentPoint = segmentEndpoints.get(i + 1);

			if (prevSegmentPoint.distanceTo(newSegmentPoint) <= MIN_DISTANCE_FOR_INSERTING_ROUTE_SEGMENT) {
				continue;
			}
			int indexNew = findNearestGpxPointIndexFromRoute(gpxRouteLocations, newSegmentPoint, routeParams.gpxRoute.calculateOsmAndRouteParts);
			int indexPrev = findNearestGpxPointIndexFromRoute(gpxRouteLocations, prevSegmentPoint, routeParams.gpxRoute.calculateOsmAndRouteParts);
			if (indexPrev != -1 && indexPrev > nearestGpxPointInd && indexNew != -1) {
				newGpxRoute.addAll(result.getOriginalRoute(lastIndex, indexPrev, true));
				lastIndex = indexNew;

				LatLon end = new LatLon(newSegmentPoint.getLatitude(), newSegmentPoint.getLongitude());
				RouteCalculationResult newRes = findOfflineRouteSegment(routeParams, prevSegmentPoint, end);
				List<RouteSegmentResult> segmentResults = newRes.getOriginalRoute();
				if (!Algorithms.isEmpty(segmentResults)) {
					newGpxRoute.addAll(segmentResults);
				}
			}
		}
		newGpxRoute.addAll(result.getOriginalRoute(lastIndex));

		return newGpxRoute;
	}
	
	private RouteCalculationResult findOfflineRouteSegment(RouteCalculationParams rParams, Location start, 
			LatLon end) {
		RouteCalculationParams newParams = new RouteCalculationParams();
		newParams.start = start;
		newParams.end = end;
		newParams.ctx = rParams.ctx;
		newParams.calculationProgress = rParams.calculationProgress;
		newParams.mode = rParams.mode;
		newParams.leftSide = rParams.leftSide;
		RouteCalculationResult newRes = null;
		try {
			if (rParams.mode.getRouteService() == RouteService.OSMAND) {
				newRes = findVectorMapsRoute(newParams, false);
			} else if (rParams.mode.getRouteService() == RouteService.BROUTER) {
				newRes= findBROUTERRoute(newParams);
			} else if (rParams.mode.getRouteService() == RouteService.STRAIGHT ||
				rParams.mode.getRouteService() == RouteService.DIRECT_TO) {
				newRes = findStraightRoute(newParams);
			}
		} catch (IOException e) {
		} catch (SAXException e) {
		} catch (ParserConfigurationException e) {
		}
		return newRes;
	}

	private ArrayList<Location> findStartAndEndLocationsFromRoute(List<Location> route, Location startLoc, LatLon endLoc, int[] startI, int[] endI) {
		float minDist = Integer.MAX_VALUE;
		int start = 0;
		int end = route.size();
		if (startLoc != null) {
			for (int i = 0; i < route.size(); i++) {
				float d = route.get(i).distanceTo(startLoc);
				if (d < minDist) {
					start = i;
					minDist = d;
				}
			}
		} else {
			startLoc = route.get(0);
		}
		Location l = new Location("temp"); //$NON-NLS-1$
		l.setLatitude(endLoc.getLatitude());
		l.setLongitude(endLoc.getLongitude());
		minDist = Integer.MAX_VALUE;
		// get in reverse order taking into account ways with cycle
		for (int i = route.size() - 1; i >= start; i--) {
			float d = route.get(i).distanceTo(l);
			if (d < minDist) {
				end = i + 1;
				// slightly modify to allow last point to be added
				minDist = d - 40;
			}
		}
		ArrayList<Location> sublist = new ArrayList<Location>(route.subList(start, end));
		if(startI != null) {
			startI[0] = start;
		}
		if(endI != null) {
			endI[0] = end;
		}
		return sublist;
	}

	private int findNearestGpxPointIndexFromRoute(List<Location> route, Location startLoc, boolean calculateOsmAndRouteParts) {
		float minDist = Integer.MAX_VALUE;
		int nearestPointIndex = 0;
		if (startLoc != null) {
			for (int i = 0; i < route.size(); i++) {
				float d = route.get(i).distanceTo(startLoc);
				if (d < minDist) {
					nearestPointIndex = i;
					minDist = d;
				}
			}
		}
		if (nearestPointIndex > 0 && calculateOsmAndRouteParts) {
			Location nearestLocation = route.get(nearestPointIndex);
			for (int i = nearestPointIndex + 1; i < route.size(); i++) {
				Location nextLocation = route.get(i);
				if (nextLocation.distanceTo(nearestLocation) >= ADDITIONAL_DISTANCE_FOR_START_POINT) {
					return i;
				}
			}
		}
		return nearestPointIndex;
	}

	public RoutingEnvironment getRoutingEnvironment(OsmandApplication ctx, ApplicationMode mode, LatLon start, LatLon end) throws IOException {
		RouteCalculationParams params = new RouteCalculationParams();
		params.ctx = ctx;
		params.mode = mode;
		params.start = new Location("", start.getLatitude(), start.getLongitude());
		params.end = end;
		return calculateRoutingEnvironment(params, false, true);
	}

	public List<GpxPoint> generateGpxPoints(RoutingEnvironment env, GpxRouteApproximation gctx, LocationsHolder locationsHolder) {
		return env.getRouter().generateGpxPoints(gctx, locationsHolder);
	}

	public GpxRouteApproximation calculateGpxPointsApproximation(RoutingEnvironment env, GpxRouteApproximation gctx, List<GpxPoint> points, ResultMatcher<GpxRouteApproximation> resultMatcher) throws IOException, InterruptedException {
		return env.getRouter().searchGpxRoute(gctx, points, resultMatcher);
	}

	protected RoutingEnvironment calculateRoutingEnvironment(RouteCalculationParams params, boolean calcGPXRoute, boolean skipComplex) throws IOException {
		BinaryMapIndexReader[] files = params.ctx.getResourceManager().getRoutingMapFiles();
		RoutePlannerFrontEnd router = new RoutePlannerFrontEnd();
		OsmandSettings settings = params.ctx.getSettings();
		router.setUseFastRecalculation(settings.USE_FAST_RECALCULATION.get());

		RoutingConfiguration.Builder config = params.ctx.getRoutingConfigForMode(params.mode);
		GeneralRouter generalRouter = params.ctx.getRouter(config, params.mode);
		if (generalRouter == null) {
			return null;
		}
		RoutingConfiguration cf = initOsmAndRoutingConfig(config, params, settings, generalRouter);
		if (cf == null) {
			return null;
		}
		PrecalculatedRouteDirection precalculated = null;
		if (calcGPXRoute) {
			ArrayList<Location> sublist = findStartAndEndLocationsFromRoute(params.gpxRoute.points,
					params.start, params.end, null, null);
			LatLon[] latLon = new LatLon[sublist.size()];
			for (int k = 0; k < latLon.length; k++) {
				latLon[k] = new LatLon(sublist.get(k).getLatitude(), sublist.get(k).getLongitude());
			}
			precalculated = PrecalculatedRouteDirection.build(latLon, generalRouter.getMaxSpeed());
			precalculated.setFollowNext(true);
			//cf.planRoadDirection = 1;
		}
		// BUILD context
		NativeOsmandLibrary lib = settings.SAFE_MODE.get() ? null : NativeOsmandLibrary.getLoadedLibrary();
		// check loaded files
		int leftX = MapUtils.get31TileNumberX(params.start.getLongitude());
		int rightX = leftX;
		int bottomY = MapUtils.get31TileNumberY(params.start.getLatitude());
		int topY = bottomY;
		if (params.intermediates != null) {
			for (LatLon l : params.intermediates) {
				leftX = Math.min(MapUtils.get31TileNumberX(l.getLongitude()), leftX);
				rightX = Math.max(MapUtils.get31TileNumberX(l.getLongitude()), rightX);
				bottomY = Math.max(MapUtils.get31TileNumberY(l.getLatitude()), bottomY);
				topY = Math.min(MapUtils.get31TileNumberY(l.getLatitude()), topY);
			}
		}
		LatLon l = params.end;
		leftX = Math.min(MapUtils.get31TileNumberX(l.getLongitude()), leftX);
		rightX = Math.max(MapUtils.get31TileNumberX(l.getLongitude()), rightX);
		bottomY = Math.max(MapUtils.get31TileNumberY(l.getLatitude()), bottomY);
		topY = Math.min(MapUtils.get31TileNumberY(l.getLatitude()), topY);

		params.ctx.getResourceManager().getRenderer().checkInitialized(15, lib, leftX, rightX, bottomY, topY);

		RoutingContext ctx = router.buildRoutingContext(cf, lib, files, RouteCalculationMode.NORMAL);

		RoutingContext complexCtx = null;
		boolean complex = !skipComplex && params.mode.isDerivedRoutingFrom(ApplicationMode.CAR) && !settings.DISABLE_COMPLEX_ROUTING.get()
				&& precalculated == null;
		ctx.leftSideNavigation = params.leftSide;
		ctx.calculationProgress = params.calculationProgress;
		ctx.publicTransport = params.inPublicTransportMode;
		ctx.startTransportStop = params.startTransportStop;
		ctx.targetTransportStop = params.targetTransportStop;
		if (params.previousToRecalculate != null && params.onlyStartPointChanged) {
			int currentRoute = params.previousToRecalculate.getCurrentRoute();
			List<RouteSegmentResult> originalRoute = params.previousToRecalculate.getOriginalRoute();
			if (originalRoute != null && currentRoute < originalRoute.size()) {
				ctx.previouslyCalculatedRoute = originalRoute.subList(currentRoute, originalRoute.size());
			}
		}
		if (complex && router.getRecalculationEnd(ctx) != null) {
			complex = false;
		}
		if (complex) {
			complexCtx = router.buildRoutingContext(cf, lib, files, RouteCalculationMode.COMPLEX);
			complexCtx.calculationProgress = params.calculationProgress;
			complexCtx.leftSideNavigation = params.leftSide;
			complexCtx.previouslyCalculatedRoute = ctx.previouslyCalculatedRoute;
		}
		return new RoutingEnvironment(router, ctx, complexCtx, precalculated);
	}

	protected RouteCalculationResult findVectorMapsRoute(final RouteCalculationParams params, boolean calcGPXRoute) throws IOException {
		RoutingEnvironment env = calculateRoutingEnvironment(params, calcGPXRoute, false);
		if (env == null) {
			return applicationModeNotSupported(params);
		}
		LatLon st = new LatLon(params.start.getLatitude(), params.start.getLongitude());
		LatLon en = new LatLon(params.end.getLatitude(), params.end.getLongitude());
		List<LatLon> inters = new ArrayList<LatLon>();
		if (params.intermediates != null) {
			inters = new ArrayList<LatLon>(params.intermediates);
		}
		return calcOfflineRouteImpl(params, env.getRouter(), env.getCtx(), env.getComplexCtx(), st, en, inters, env.getPrecalculated());
	}

	private RoutingConfiguration initOsmAndRoutingConfig(Builder config, final RouteCalculationParams params, OsmandSettings settings,
			GeneralRouter generalRouter) throws IOException, FileNotFoundException {
		Map<String, String> paramsR = new LinkedHashMap<String, String>();
		for(Map.Entry<String, RoutingParameter> e : generalRouter.getParameters().entrySet()){
			String key = e.getKey();
			RoutingParameter pr = e.getValue();
			String vl;
			if(key.equals(GeneralRouter.USE_SHORTEST_WAY)) {
				Boolean bool = !settings.FAST_ROUTE_MODE.getModeValue(params.mode);
				vl = bool ? "true" : null;
			} else if(pr.getType() == RoutingParameterType.BOOLEAN) {
				CommonPreference<Boolean> pref = settings.getCustomRoutingBooleanProperty(key, pr.getDefaultBoolean());
				Boolean bool = pref.getModeValue(params.mode);
				vl = bool ? "true" : null;
			} else {
				vl = settings.getCustomRoutingProperty(key, "").getModeValue(params.mode);
			}
			if(vl != null && vl.length() > 0) {
				paramsR.put(key, vl);
			}
		}
		Float defaultSpeed = params.mode.getDefaultSpeed();
		if (defaultSpeed > 0) {
			paramsR.put(GeneralRouter.DEFAULT_SPEED, String.valueOf(defaultSpeed));
		}
		Float minSpeed = params.mode.getMinSpeed();
		if (minSpeed > 0) {
			paramsR.put(GeneralRouter.MIN_SPEED, String.valueOf(minSpeed));
		}
		Float maxSpeed = params.mode.getMaxSpeed();
		if (maxSpeed > 0) {
			paramsR.put(GeneralRouter.MAX_SPEED, String.valueOf(maxSpeed));
		}
		float mb = (1 << 20);
		Runtime rt = Runtime.getRuntime();
		// make visible
		int memoryLimit = (int) (0.95 * ((rt.maxMemory() - rt.totalMemory()) + rt.freeMemory()) / mb);
		log.warn("Use " + memoryLimit +  " MB Free " + rt.freeMemory() / mb + " of " + rt.totalMemory() / mb + " max " + rt.maxMemory() / mb);
		RoutingConfiguration cf = config.build( params.mode.getRoutingProfile(), params.start.hasBearing() ?
				params.start.getBearing() / 180d * Math.PI : null, 
				memoryLimit, paramsR);
		if(settings.ENABLE_TIME_CONDITIONAL_ROUTING.getModeValue(params.mode)) {
			cf.routeCalculationTime = System.currentTimeMillis();
		}
		return cf;
	}

	private RouteCalculationResult calcOfflineRouteImpl(final RouteCalculationParams params,
			RoutePlannerFrontEnd router, RoutingContext ctx, RoutingContext complexCtx, LatLon st, LatLon en,
			List<LatLon> inters, PrecalculatedRouteDirection precalculated) throws IOException {
		try {
			List<RouteSegmentResult> result ;
			if(complexCtx != null) {
				try {
					result = router.searchRoute(complexCtx, st, en, inters, precalculated);
					// discard ctx and replace with calculated
					ctx = complexCtx;
				} catch(final RuntimeException e) {
					params.ctx.runInUIThread(new Runnable() {
						@Override
						public void run() {
							params.ctx.showToastMessage(R.string.complex_route_calculation_failed, e.getMessage());							
						}
					});
					result = router.searchRoute(ctx, st, en, inters);
				}
			} else {
				result = router.searchRoute(ctx, st, en, inters);
			}
			
			if(result == null || result.isEmpty()) {
				if(ctx.calculationProgress.segmentNotFound == 0) {
					return new RouteCalculationResult(params.ctx.getString(R.string.starting_point_too_far));
				} else if(ctx.calculationProgress.segmentNotFound == inters.size() + 1) {
					return new RouteCalculationResult(params.ctx.getString(R.string.ending_point_too_far));
				} else if(ctx.calculationProgress.segmentNotFound > 0) {
					return new RouteCalculationResult(params.ctx.getString(R.string.intermediate_point_too_far, "'" + ctx.calculationProgress.segmentNotFound + "'"));
				}
				if(ctx.calculationProgress.directSegmentQueueSize == 0) {
					return new RouteCalculationResult("Route can not be found from start point (" +ctx.calculationProgress.distanceFromBegin/1000f+" km)");
				} else if(ctx.calculationProgress.reverseSegmentQueueSize == 0) {
					return new RouteCalculationResult("Route can not be found from end point (" +ctx.calculationProgress.distanceFromEnd/1000f+" km)");
				}
				if(ctx.calculationProgress.isCancelled) {
					return interrupted();
				}
				// something really strange better to see that message on the scren
				return emptyResult();
			} else {
				RouteCalculationResult res = new RouteCalculationResult(result, params.start, params.end,
						params.intermediates, params.ctx, params.leftSide, ctx, params.gpxRoute  == null? null: params.gpxRoute.wpt,
								params.mode, true);
				return res;
			}
		} catch (RuntimeException e) {
			log.error("Runtime error: " + e.getMessage(), e);
			return new RouteCalculationResult(e.getMessage() );
		} catch (InterruptedException e) {
			log.error("Interrupted: " + e.getMessage(), e);
			return interrupted();
		} catch (OutOfMemoryError e) {
//			ActivityManager activityManager = (ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
//			ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
//			activityManager.getMemoryInfo(memoryInfo);
//			int avl = (int) (memoryInfo.availMem / (1 << 20));
			int max = (int) (Runtime.getRuntime().maxMemory() / (1 << 20)); 
			int avl = (int) (Runtime.getRuntime().freeMemory() / (1 << 20));
			String s = " (" + avl + " MB available of " + max  + ") ";
			return new RouteCalculationResult("Not enough process memory "+ s);
		}
	}

	private RouteCalculationResult applicationModeNotSupported(RouteCalculationParams params) {
		return new RouteCalculationResult("Application mode '"+ params.mode.toHumanString()+ "' is not supported.");
	}

	private RouteCalculationResult interrupted() {
		return new RouteCalculationResult("Route calculation was interrupted");
	}

	private RouteCalculationResult emptyResult() {
		return new RouteCalculationResult("Empty result");
	}

	public static List<RouteSegmentResult> parseOsmAndGPXRoute(List<Location> points, GPXFile gpxFile,
	                                                           List<Location> segmentEndpoints,
	                                                           int selectedSegment) {
		List<TrkSegment> segments = gpxFile.getNonEmptyTrkSegments(false);
		if (selectedSegment != -1 && segments.size() > selectedSegment) {
			TrkSegment segment = segments.get(selectedSegment);
			points.addAll(locationsFromWpts(segment.points));
			RouteImporter routeImporter = new RouteImporter(segment);
			return routeImporter.importRoute();
		} else {
			collectPointsFromSegments(segments, points, segmentEndpoints);
			RouteImporter routeImporter = new RouteImporter(gpxFile);
			return routeImporter.importRoute();
		}
	}

	protected static void collectSegmentPointsFromGpx(GPXFile gpxFile, List<Location> points,
													  List<Location> segmentEndpoints, int selectedSegment) {
		List<TrkSegment> segments = gpxFile.getNonEmptyTrkSegments(false);
		if (selectedSegment != -1 && segments.size() > selectedSegment) {
			TrkSegment segment = segments.get(selectedSegment);
			points.addAll(locationsFromWpts(segment.points));
		} else {
			collectPointsFromSegments(segments, points, segmentEndpoints);
		}
	}

	protected static void collectPointsFromSegments(List<TrkSegment> segments, List<Location> points, List<Location> segmentEndpoints) {
		Location lastPoint = null;
		for (int i = 0; i < segments.size(); i++) {
			TrkSegment segment = segments.get(i);
			points.addAll(locationsFromWpts(segment.points));
			if (i <= segments.size() - 1 && lastPoint != null) {
				segmentEndpoints.add(lastPoint);
				segmentEndpoints.add(points.get((points.size() - segment.points.size())));
			}
			lastPoint = points.get(points.size() - 1);
		}
	}

	protected static List<RouteDirectionInfo> parseOsmAndGPXRoute(List<Location> points, GPXFile gpxFile,
																  List<Location> segmentEndpoints,
																  boolean osmandRouter, boolean leftSide,
																  float defSpeed, int selectedSegment) {
		List<RouteDirectionInfo> directions = null;
		if (!osmandRouter) {
			for (WptPt pt : gpxFile.getPoints()) {
				points.add(createLocation(pt));
			}
		} else {
			collectSegmentPointsFromGpx(gpxFile, points, segmentEndpoints, selectedSegment);
		}
		float[] distanceToEnd = new float[points.size()];
		for (int i = points.size() - 2; i >= 0; i--) {
			distanceToEnd[i] = distanceToEnd[i + 1] + points.get(i).distanceTo(points.get(i + 1));
		}

		Route route = null;
		if (gpxFile.routes.size() > 0) {
			route = gpxFile.routes.get(0);
		}
		RouteDirectionInfo previous = null;
		if (route != null && route.points.size() > 0) {
			directions = new ArrayList<RouteDirectionInfo>();
			Iterator<WptPt> iterator = route.points.iterator();
			float lasttime = 0;
			while(iterator.hasNext()){
				WptPt item = iterator.next();
				try {
					String stime = item.getExtensionsToRead().get("time");
					int time  = 0;
					if (stime != null) {
						time = Integer.parseInt(stime);
					}
					int offset = Integer.parseInt(item.getExtensionsToRead().get("offset")); //$NON-NLS-1$
					if(directions.size() > 0) {
						RouteDirectionInfo last = directions.get(directions.size() - 1);
						// update speed using time and idstance
						if (distanceToEnd.length > last.routePointOffset && distanceToEnd.length > offset) {
							float lastDistanceToEnd = distanceToEnd[last.routePointOffset];
							float currentDistanceToEnd = distanceToEnd[offset];
							if (lasttime != 0) {
								last.setAverageSpeed((lastDistanceToEnd - currentDistanceToEnd) / lasttime);
							} 
							last.distance = (int) Math.round(lastDistanceToEnd - currentDistanceToEnd);
						}
					} 
					// save time as a speed because we don't know distance of the route segment
					lasttime = time;
					float avgSpeed = defSpeed;
					if (!iterator.hasNext() && time > 0) {
						if (distanceToEnd.length > offset) {
							avgSpeed = distanceToEnd[offset] / time;
						} else {
							avgSpeed = defSpeed;
						}
					}
					String stype = item.getExtensionsToRead().get("turn"); //$NON-NLS-1$
					TurnType turnType;
					if (stype != null) {
						turnType = TurnType.fromString(stype.toUpperCase(), leftSide);
					} else {
						turnType = TurnType.straight();
					}
					String sturn = item.getExtensionsToRead().get("turn-angle"); //$NON-NLS-1$
					if (sturn != null) {
						turnType.setTurnAngle((float) Double.parseDouble(sturn));
					}
					String slanes = item.getExtensionsToRead().get("lanes");
					if (slanes != null) {
						try {
							int[] lanes = Algorithms.stringToArray(slanes);
							if (lanes != null && lanes.length > 0) {
								turnType.setLanes(lanes);
							}
						} catch (NumberFormatException e) {
							// ignore
						}
					}
					RouteDirectionInfo dirInfo = new RouteDirectionInfo(avgSpeed, turnType);
					dirInfo.setDescriptionRoute(item.desc); //$NON-NLS-1$
					dirInfo.routePointOffset = offset;

					// Issue #2894
					String sref = item.getExtensionsToRead().get("ref"); //$NON-NLS-1$
					if (sref != null && !"null".equals(sref)) {
						dirInfo.setRef(sref); //$NON-NLS-1$
					}
					String sstreetname = item.getExtensionsToRead().get("street-name"); //$NON-NLS-1$
					if (sstreetname != null && !"null".equals(sstreetname)) {
						dirInfo.setStreetName(sstreetname); //$NON-NLS-1$
					}
					String sdest = item.getExtensionsToRead().get("dest"); //$NON-NLS-1$
					if (sdest != null && !"null".equals(sdest)) {
						dirInfo.setDestinationName(sdest); //$NON-NLS-1$
					}

					if (previous != null && TurnType.C != previous.getTurnType().getValue() &&
							!osmandRouter) {
						// calculate angle
						if (previous.routePointOffset > 0) {
							float paz = points.get(previous.routePointOffset - 1).bearingTo(points.get(previous.routePointOffset));
							float caz;
							if (previous.getTurnType().isRoundAbout() && dirInfo.routePointOffset < points.size() - 1) {
								caz = points.get(dirInfo.routePointOffset).bearingTo(points.get(dirInfo.routePointOffset + 1));
							} else {
								caz = points.get(dirInfo.routePointOffset - 1).bearingTo(points.get(dirInfo.routePointOffset));
							}
							float angle = caz - paz;
							if (angle < 0) {
								angle += 360;
							} else if (angle > 360) {
								angle -= 360;
							}
							// that magic number helps to fix some errors for turn
							angle += 75;

							if (previous.getTurnType().getTurnAngle() < 0.5f) {
								previous.getTurnType().setTurnAngle(angle);
							}
						}
					}

					directions.add(dirInfo);

					previous = dirInfo;
				} catch (NumberFormatException e) {
					log.info("Exception", e); //$NON-NLS-1$
				} catch (IllegalArgumentException e) {
					log.info("Exception", e); //$NON-NLS-1$
				}
			}
		}
		if (previous != null && TurnType.C != previous.getTurnType().getValue()) {
			// calculate angle
			if (previous.routePointOffset > 0 && previous.routePointOffset < points.size() - 1) {
				float paz = points.get(previous.routePointOffset - 1).bearingTo(points.get(previous.routePointOffset));
				float caz = points.get(previous.routePointOffset).bearingTo(points.get(points.size() - 1));
				float angle = caz - paz;
				if (angle < 0) {
					angle += 360;
				}
				if (previous.getTurnType().getTurnAngle() < 0.5f) {
					previous.getTurnType().setTurnAngle(angle);
				}
			}
		}
		return directions;
	}

	public GPXFile createOsmandRouterGPX(RouteCalculationResult route, OsmandApplication ctx, String name) {
		TargetPointsHelper helper = ctx.getTargetPointsHelper();
		List<WptPt> points = new ArrayList<>();
		List<TargetPoint> ps = helper.getIntermediatePointsWithTarget();
		for (int k = 0; k < ps.size(); k++) {
			WptPt pt = new WptPt();
			pt.lat = ps.get(k).getLatitude();
			pt.lon = ps.get(k).getLongitude();
			if (k < ps.size()) {
				pt.name = ps.get(k).getOnlyName() + "";
				if (k == ps.size() - 1) {
					String target = ctx.getString(R.string.destination_point, "");
					if (pt.name.startsWith(target)) {
						pt.name = ctx.getString(R.string.destination_point, pt.name);
					}
				} else {
					String prefix = (k + 1) +". ";
					if(Algorithms.isEmpty(pt.name)) {
						pt.name = ctx.getString(R.string.target_point, pt.name);
					}
					if (pt.name.startsWith(prefix)) {
						pt.name = prefix + pt.name;
					}
				}
				pt.desc = pt.name;
			}
			points.add(pt);
		}

		List<Location> locations = route.getImmutableAllLocations();
		List<RouteSegmentResult> originalRoute = route.getOriginalRoute();
		RouteExporter exporter = new RouteExporter(name, originalRoute, locations, points);
		return exporter.exportRoute();
	}

	private RouteCalculationResult findOnlineRoute(RouteCalculationParams params) throws IOException, JSONException {
		OsmandApplication app = params.ctx;
		OnlineRoutingHelper helper = app.getOnlineRoutingHelper();
		OsmandSettings settings = app.getSettings();
		String engineKey = params.mode.getRoutingProfile();
		OnlineRoutingResponse response =
				helper.calculateRouteOnline(engineKey, getPathFromParams(params), params.leftSide);

		if (response != null) {
			if (response.getGpxFile() != null) {
				GPXRouteParamsBuilder builder = new GPXRouteParamsBuilder(response.getGpxFile(), settings);
				params.gpxRoute = builder.build(app);
				return calculateGpxRoute(params);
			}
			List<Location> route = response.getRoute();
			List<RouteDirectionInfo> directions = response.getDirections();
			if (!Algorithms.isEmpty(route) && !Algorithms.isEmpty(directions)) {
				params.intermediates = null;
				return new RouteCalculationResult(route, directions, params, null, false);
			}
		}

		return new RouteCalculationResult("Route is empty");
	}

	private static List<LatLon> getPathFromParams(RouteCalculationParams params) {
		List<LatLon> points = new ArrayList<>();
		points.add(new LatLon(params.start.getLatitude(), params.start.getLongitude()));
		if (Algorithms.isEmpty(params.intermediates)) {
			points.addAll(params.intermediates);
		}
		points.add(params.end);
		return points;
	}

	protected RouteCalculationResult findBROUTERRoute(RouteCalculationParams params) throws MalformedURLException,
			IOException, ParserConfigurationException, FactoryConfigurationError, SAXException {
		int numpoints = 2 + (params.intermediates != null ? params.intermediates.size() : 0);
		double[] lats = new double[numpoints];
		double[] lons = new double[numpoints];
		int index = 0;
		String mode;
		boolean addMissingTurns = true;
		lats[index] = params.start.getLatitude();
		lons[index] = params.start.getLongitude();
		index++;
		if(params.intermediates != null && params.intermediates.size() > 0) {
			for(LatLon il : params.intermediates) {
				lats[index] = il.getLatitude();
				lons[index] = il.getLongitude();
				index++;
			}
		}
		lats[index] = params.end.getLatitude();
		lons[index] = params.end.getLongitude();

		Set<LatLon> impassableRoads = params.ctx.getAvoidSpecificRoads().getImpassableRoads().keySet();
		double[] nogoLats = new double[impassableRoads.size()];
		double[] nogoLons = new double[impassableRoads.size()];
		double[] nogoRadi = new double[impassableRoads.size()];

		if(impassableRoads.size() != 0) {
			int nogoindex = 0;
			for (LatLon nogos : impassableRoads) {
				nogoLats[nogoindex] = nogos.getLatitude();
				nogoLons[nogoindex] = nogos.getLongitude();
				nogoRadi[nogoindex] = 10;
				nogoindex++;
			}
		}
		
		if (params.mode.isDerivedRoutingFrom(ApplicationMode.PEDESTRIAN)) {
			mode = "foot"; //$NON-NLS-1$
		} else if (params.mode.isDerivedRoutingFrom(ApplicationMode.BICYCLE)) {
			mode = "bicycle"; //$NON-NLS-1$
		} else {
			mode = "motorcar"; //$NON-NLS-1$
		}
		Bundle bpars = new Bundle();
		bpars.putDoubleArray("lats", lats);
		bpars.putDoubleArray("lons", lons);
		bpars.putDoubleArray("nogoLats", nogoLats);
		bpars.putDoubleArray("nogoLons", nogoLons);
		bpars.putDoubleArray("nogoRadi", nogoRadi);
		bpars.putString("fast", params.fast ? "1" : "0");
		bpars.putString("v", mode);
		bpars.putString("trackFormat", "gpx");
		bpars.putString("turnInstructionFormat", "osmand");
		bpars.putString("acceptCompressedResult", "true");

		OsmandApplication ctx = params.ctx;
		List<Location> res = new ArrayList<Location>();
		List<RouteDirectionInfo> dir = new ArrayList<>();
		List<Location> segmentEndpoints = new ArrayList<>();

		IBRouterService brouterService = ctx.getBRouterService();
		if (brouterService == null) {
			return new RouteCalculationResult("BRouter service is not available");
		}
		try {
			String gpxMessage = brouterService.getTrackFromParams(bpars);
			if (gpxMessage == null)
				gpxMessage = "no result from brouter";

			boolean isZ64Encoded = gpxMessage.startsWith("ejY0"); // base-64 version of "z64"

			if (!(isZ64Encoded || gpxMessage.startsWith("<"))) {
				return new RouteCalculationResult(gpxMessage);
			}

			InputStream gpxStream;
			if (isZ64Encoded) {
				ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(gpxMessage, Base64.DEFAULT));
				bais.read(new byte[3]); // skip prefix
				gpxStream = new GZIPInputStream(bais);
			} else {
				gpxStream = new ByteArrayInputStream(gpxMessage.getBytes("UTF-8"));
			}

			GPXFile gpxFile = GPXUtilities.loadGPXFile(gpxStream);

			dir = parseOsmAndGPXRoute(res, gpxFile, segmentEndpoints, true, params.leftSide, params.mode.getDefaultSpeed(), -1);

			if (dir != null) {
				addMissingTurns = false;
			}

		} catch (Exception e) {
			return new RouteCalculationResult("Exception calling BRouter: " + e); //$NON-NLS-1$
		}
		return new RouteCalculationResult(res, dir, params, null, addMissingTurns);
	}

	private RouteCalculationResult findStraightRoute(RouteCalculationParams params) {
		LinkedList<Location> points = new LinkedList<>();
		List<Location> segments = new ArrayList<>();
		points.add(new Location("pnt", params.start.getLatitude(), params.start.getLongitude()));
		if(params.intermediates != null) {
			for (LatLon l : params.intermediates) {
				points.add(new Location("pnt", l.getLatitude(), l.getLongitude()));
			}
		}
		points.add(new Location("", params.end.getLatitude(), params.end.getLongitude()));
		Location lastAdded = null;
		float speed = params.mode.getDefaultSpeed();
		List<RouteDirectionInfo> computeDirections = new ArrayList<RouteDirectionInfo>();
		while(!points.isEmpty()) {
			Location pl = points.peek();
			if (lastAdded == null || lastAdded.distanceTo(pl) < MIN_STRAIGHT_DIST) {
				lastAdded = points.poll();
				if(lastAdded.getProvider().equals("pnt")) {
					RouteDirectionInfo previousInfo = new RouteDirectionInfo(speed, TurnType.straight());
					previousInfo.routePointOffset = segments.size();
					previousInfo.setDescriptionRoute(params.ctx.getString(R.string.route_head));
					computeDirections.add(previousInfo);
				}
				segments.add(lastAdded);
			} else {
				Location mp = MapUtils.calculateMidPoint(lastAdded, pl);
				points.add(0, mp);
			}
		}
		return new RouteCalculationResult(segments, computeDirections, params, null, false);
	}
}