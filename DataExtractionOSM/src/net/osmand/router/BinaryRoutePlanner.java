package net.osmand.router;

import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import net.osmand.LogUtil;
import net.osmand.binary.RouteDataObject;
import net.osmand.osm.MapRenderingTypes;
import net.osmand.osm.MapUtils;

import org.apache.commons.logging.Log;

public class BinaryRoutePlanner {
	
	public static boolean PRINT_TO_CONSOLE_ROUTE_INFORMATION_TO_TEST = true;
	private final int REVERSE_WAY_RESTRICTION_ONLY = 1024;
	/*private*/ final int STANDARD_ROAD_IN_QUEUE_OVERHEAD = 220;
	/*private */final int STANDARD_ROAD_VISITED_OVERHEAD = 150;
	
	protected static final Log log = LogUtil.getLog(BinaryRoutePlanner.class);
	
	private static final int ROUTE_POINTS = 11;
	private static final float TURN_DEGREE_MIN = 45;
	
	
	private static double squareRootDist(int x1, int y1, int x2, int y2) {
		// translate into meters 
		double dy = convert31YToMeters(y1, y2);
		double dx = convert31XToMeters(x1, x2);
		return Math.sqrt(dx * dx + dy * dy);
//		return measuredDist(x1, y1, x2, y2);
	}
	
	private static double measuredDist(int x1, int y1, int x2, int y2) {
		return MapUtils.getDistance(MapUtils.get31LatitudeY(y1), MapUtils.get31LongitudeX(x1), 
				MapUtils.get31LatitudeY(y2), MapUtils.get31LongitudeX(x2));
	}
	
	private static double squareDist(int x1, int y1, int x2, int y2) {
		// translate into meters 
		double dy = convert31YToMeters(y1, y2);
		double dx = convert31XToMeters(x1, x2);
		return dx * dx + dy * dy;
	}
	
	private static double convert31YToMeters(int y1, int y2) {
		// translate into meters 
		return (y1 - y2) * 0.01863d;
	}
	
	private static double convert31XToMeters(int x1, int x2) {
		// translate into meters 
		return (x1 - x2) * 0.011d;
	}
   
	
	private static double calculateProjection(int xA, int yA, int xB, int yB, int xC, int yC) {
		// Scalar multiplication between (AB, AC)
		double multiple = convert31XToMeters(xB, xA) * convert31XToMeters(xC, xA) + convert31YToMeters(yB, yA) * convert31YToMeters(yC, yA);
		return multiple;
	}
	
	
	
	public RouteSegment findRouteSegment(double lat, double lon, RoutingContext ctx) throws IOException {
		int px = MapUtils.get31TileNumberX(lon);
		int py = MapUtils.get31TileNumberY(lat);
		ArrayList<RouteDataObject> dataObjects = new ArrayList<RouteDataObject>();
		ctx.loadTileData(px, py, 16, dataObjects);
		if (dataObjects.isEmpty()) {
			ctx.loadTileData(px, py, 14, dataObjects);
		}
		RouteSegment road = null;
		double sdist = 0;
		@SuppressWarnings("unused")
		int foundProjX = 0;
		@SuppressWarnings("unused")
		int foundProjY = 0;

		for (RouteDataObject r : dataObjects) {
			if (r.getPointsLength() > 1) {
				for (int j = 1; j < r.getPointsLength(); j++) {
					double mDist = squareRootDist(r.getPoint31XTile(j), r.getPoint31YTile(j), r.getPoint31XTile(j - 1),
							r.getPoint31YTile(j - 1));
					int prx = r.getPoint31XTile(j);
					int pry = r.getPoint31YTile(j);
					double projection = calculateProjection(r.getPoint31XTile(j - 1), r.getPoint31YTile(j - 1), r.getPoint31XTile(j),
							r.getPoint31YTile(j), px, py);
					if (projection < 0) {
						prx = r.getPoint31XTile(j - 1);
						pry = r.getPoint31YTile(j - 1);
					} else if (projection >= mDist * mDist) {
						prx = r.getPoint31XTile(j);
						pry = r.getPoint31YTile(j);
					} else {
						prx = (int) (r.getPoint31XTile(j - 1) + (r.getPoint31XTile(j) - r.getPoint31XTile(j - 1))
								* (projection / (mDist * mDist)));
						pry = (int) (r.getPoint31YTile(j - 1) + (r.getPoint31YTile(j) - r.getPoint31YTile(j - 1))
								* (projection / (mDist * mDist)));
					}
					double currentsDist = squareDist(prx, pry, px, py);
					if (road == null || currentsDist < sdist) {
						RouteDataObject ro = new RouteDataObject(r);
						road = new RouteSegment(ro, j);
						ro.insert(j, prx, pry);
						sdist = currentsDist;
						foundProjX = prx;
						foundProjY = pry;
					}
				}
			}
		}
		if (road != null) {
			// re-register the best road because one more point was inserted
			ctx.registerRouteDataObject(road.getRoad());
		}
		return road;
	}
	
	public List<RouteSegmentResult> searchRoute(final RoutingContext ctx, RouteSegment start, RouteSegment end, List<RouteSegment> intermediate, boolean leftSideNavigation) throws IOException, InterruptedException {
		if(intermediate != null && intermediate.size() > 0) {
			ArrayList<RouteSegment> ps = new ArrayList<RouteSegment>(intermediate);
			ArrayList<RouteSegmentResult> firstPartRecalculatedRoute = null;
			ArrayList<RouteSegmentResult> restPartRecalculatedRoute = null;
			if (ctx.previouslyCalculatedRoute != null) {
				List<RouteSegmentResult> prev = ctx.previouslyCalculatedRoute;
				long id = intermediate.get(0).getRoad().id;
				int ss = intermediate.get(0).getSegmentStart();
				for (int i = 0; i < prev.size(); i++) {
					RouteSegmentResult rsr = prev.get(i);
					if (id == rsr.getObject().getId() && ss == rsr.getEndPointIndex()) {
						firstPartRecalculatedRoute = new ArrayList<RouteSegmentResult>(prev.subList(0, i + 1));
						restPartRecalculatedRoute = new ArrayList<RouteSegmentResult>(prev.subList(i + 1, prev.size()));
						break;
					}
				}
			}
			ps.add(end);
			ps.add(0, start);
			List<RouteSegmentResult> results = new ArrayList<RouteSegmentResult>();
			for (int i = 0; i < ps.size() - 1; i++) {
				RoutingContext local = new RoutingContext(ctx);
				if(i == 0) {
					local.previouslyCalculatedRoute = firstPartRecalculatedRoute;
				}
				local.visitor = ctx.visitor;
				List<RouteSegmentResult> res = searchRouteInternalPrepare(local, ps.get(i), ps.get(i + 1), leftSideNavigation);

				results.addAll(res);
				ctx.distinctLoadedTiles += local.distinctLoadedTiles;
				ctx.loadedTiles += local.loadedTiles;
				ctx.visitedSegments += local.visitedSegments;
				ctx.loadedPrevUnloadedTiles += local.loadedPrevUnloadedTiles;
				ctx.timeToCalculate += local.timeToCalculate;
				ctx.timeToLoad += local.timeToLoad;
				ctx.timeToLoadHeaders += local.timeToLoadHeaders;
				ctx.relaxedSegments += local.relaxedSegments;
				
				local.unloadAllData(ctx);
				if(restPartRecalculatedRoute != null) {
					results.addAll(restPartRecalculatedRoute);
					break;
				}
			}
			ctx.unloadAllData();
			printResults(ctx, start, end, results);
			return results;
		}
		return searchRoute(ctx, start, end, leftSideNavigation);
	}
	
	private void printMemoryConsumption(String message ){
		RoutingContext.runGCUsedMemory();
		long h1 = RoutingContext.runGCUsedMemory();
		float mb = (1 << 20);
		log.warn(message + h1 / mb+ " mb");
	}
	
	@SuppressWarnings("static-access")
	public List<RouteSegmentResult> searchRoute(final RoutingContext ctx, RouteSegment start, RouteSegment end, boolean leftSideNavigation) throws IOException, InterruptedException {
		if(ctx.SHOW_GC_SIZE){
			printMemoryConsumption("Memory occupied before routing ");
		}
		List<RouteSegmentResult> result = searchRouteInternalPrepare(ctx, start, end, leftSideNavigation);
		if(result != null) {
			printResults(ctx, start, end, result);
		}
		if (RoutingContext.SHOW_GC_SIZE) {
			int sz = ctx.global.size;
			log.warn("Subregion size " + ctx.subregionTiles.size() + " " + " tiles " + ctx.indexedSubregions.size());
			ctx.runGCUsedMemory();
			long h1 = ctx.runGCUsedMemory();
			ctx.unloadAllData();
			ctx.runGCUsedMemory();
			long h2 = ctx.runGCUsedMemory();
			float mb = (1 << 20);
			log.warn("Unload context :  estimated " + sz / mb + " ?= " + (h1 - h2) / mb + " actual");
		}
		return result;
	}
	
	
	private List<RouteSegmentResult> searchRouteInternalPrepare(final RoutingContext ctx, RouteSegment start, RouteSegment end, boolean leftSideNavigation) throws IOException, InterruptedException {
		// Split into 2 methods to let GC work in between
		FinalRouteSegment finalRouteSegment = searchRouteInternal(ctx, start, end, leftSideNavigation);
		// 4. Route is found : collect all segments and prepare result
		return prepareResult(ctx, finalRouteSegment, leftSideNavigation);
	}
	
	/**
	 * Calculate route between start.segmentEnd and end.segmentStart (using A* algorithm)
	 * return list of segments
	 * @return 
	 */
	private FinalRouteSegment searchRouteInternal(final RoutingContext ctx, RouteSegment start, RouteSegment end, boolean leftSideNavigation) throws IOException, InterruptedException {
		// measure time
		ctx.timeToLoad = 0;
		ctx.visitedSegments = 0;
		ctx.memoryOverhead  = 1000;
		ctx.timeToCalculate = System.nanoTime();
		if(ctx.config.initialDirection != null) {
			// mark here as positive for further check
			ctx.firstRoadId = calculateRoutePointId(start.getRoad(), start.getSegmentStart(), true);
			double plusDir = start.getRoad().directionRoute(start.getSegmentStart(), true);
			double diff	 = plusDir - ctx.config.initialDirection;
			if(Math.abs(MapUtils.alignAngleDifference(diff)) <= Math.PI / 3) {
				ctx.firstRoadDirection = 1;
			} else if(Math.abs(MapUtils.alignAngleDifference(diff - Math.PI)) <= Math.PI / 3) {
				ctx.firstRoadDirection = -1;
			}
			
		}

		// Initializing priority queue to visit way segments 
		Comparator<RouteSegment> segmentsComparator = new Comparator<RouteSegment>(){
			@Override
			public int compare(RouteSegment o1, RouteSegment o2) {
				return ctx.roadPriorityComparator(o1.distanceFromStart, o1.distanceToEnd, o2.distanceFromStart, o2.distanceToEnd);
			}
		};
		
		Comparator<RouteSegment> nonHeuristicSegmentsComparator = new Comparator<RouteSegment>(){
			@Override
			public int compare(RouteSegment o1, RouteSegment o2) {
				return roadPriorityComparator(o1.distanceFromStart, o1.distanceToEnd, o2.distanceFromStart, o2.distanceToEnd, 0.5);
			}
		};

		PriorityQueue<RouteSegment> graphDirectSegments = new PriorityQueue<RouteSegment>(50, segmentsComparator);
		PriorityQueue<RouteSegment> graphReverseSegments = new PriorityQueue<RouteSegment>(50, segmentsComparator);
		
		// Set to not visit one segment twice (stores road.id << X + segmentStart)
		TLongObjectHashMap<RouteSegment> visitedDirectSegments = new TLongObjectHashMap<RouteSegment>();
		TLongObjectHashMap<RouteSegment> visitedOppositeSegments = new TLongObjectHashMap<RouteSegment>();
		
		boolean runRecalculation = ctx.previouslyCalculatedRoute != null && ctx.previouslyCalculatedRoute.size() > 0;
		if (runRecalculation) {
			RouteSegment previous = null;
			List<RouteSegmentResult> rlist = new ArrayList<RouteSegmentResult>();
			// always recalculate first 7 km
			int distanceThreshold = 7000;
			float threshold = 0;
			for(RouteSegmentResult rr : ctx.previouslyCalculatedRoute) {
				threshold += rr.getDistance();
				if(threshold > distanceThreshold) {
					rlist.add(rr);
				}
			}
			runRecalculation = rlist.size() > 0;
			if (rlist.size() > 0) {
				for (RouteSegmentResult rr : rlist) {
					RouteSegment segment = new RouteSegment(rr.getObject(), rr.getEndPointIndex());
					if (previous != null) {
						previous.setParentRoute(segment);
						previous.setParentSegmentEnd(rr.getStartPointIndex());
						boolean positive = rr.getStartPointIndex() < rr.getEndPointIndex();
						long t = calculateRoutePointId(rr.getObject(), positive ? rr.getEndPointIndex() - 1 : rr.getEndPointIndex(),
								positive);
						visitedOppositeSegments.put(t, segment);
					}
					previous = segment;
				}
				end = previous;
			}
		}
		
		// for start : f(start) = g(start) + h(start) = 0 + h(start) = h(start)
		int targetEndX = end.road.getPoint31XTile(end.getSegmentStart());
		int targetEndY = end.road.getPoint31YTile(end.getSegmentStart());
		int startX = start.road.getPoint31XTile(start.getSegmentStart());
		int startY = start.road.getPoint31YTile(start.getSegmentStart());
		float estimatedDistance = (float) h(ctx, targetEndX, targetEndY, startX, startY);
		end.distanceToEnd = start.distanceToEnd	= estimatedDistance;
		
		graphDirectSegments.add(start);
		graphReverseSegments.add(end);
		
		// Extract & analyze segment with min(f(x)) from queue while final segment is not found
		boolean inverse = false;
		boolean init = false;
		
		PriorityQueue<RouteSegment>  graphSegments;
		if(inverse) {
			graphSegments = graphReverseSegments;
		} else {
			graphSegments = graphDirectSegments;
		}
		FinalRouteSegment finalSegment = null;
		while (!graphSegments.isEmpty()) {
			RouteSegment segment = graphSegments.poll();
			// use accumulative approach
			ctx.memoryOverhead = (visitedDirectSegments.size() + visitedOppositeSegments.size()) * STANDARD_ROAD_VISITED_OVERHEAD + 
					(graphDirectSegments.size() +
					graphReverseSegments.size()) * STANDARD_ROAD_IN_QUEUE_OVERHEAD;
			
			
//			String pr = " pend="+segment.parentSegmentEnd +" " +( segment.parentRoute == null ? "" : (" parent=" + segment.parentRoute.road));
//			System.out.println("Seg " + segment.road + " ind=" + segment.segmentStart + 
//					" ds=" + ((float)segment.distanceFromStart) + " es="+((float)segment.distanceToEnd) + pr);
			if(segment instanceof FinalRouteSegment) {
				if(RoutingContext.SHOW_GC_SIZE){
					log.warn("Estimated overhead " + (ctx.memoryOverhead / (1<<20))+ " mb");
					printMemoryConsumption("Memory occupied after calculation : ");
				}
				finalSegment = (FinalRouteSegment) segment;
				break;
			}
			if (ctx.memoryOverhead > ctx.config.memoryLimitation * 0.95 && RoutingContext.SHOW_GC_SIZE) {
				printMemoryConsumption("Memory occupied before exception : ");
			}
			if(ctx.memoryOverhead > ctx.config.memoryLimitation * 0.95) {
				throw new IllegalStateException("There is no enough memory " + ctx.config.memoryLimitation/(1<<20) + " Mb");
			}
			ctx.visitedSegments++;
			// for debug purposes
			if (ctx.visitor != null) {
				ctx.visitor.visitSegment(segment, true);
			}
			if (!inverse) {
				processRouteSegment(ctx, false, graphDirectSegments, visitedDirectSegments, targetEndX, targetEndY,
						segment, visitedOppositeSegments);
			} else {
				processRouteSegment(ctx, true, graphReverseSegments, visitedOppositeSegments, startX, startY, segment,
						visitedDirectSegments);
			}
			if(graphReverseSegments.isEmpty()){
				throw new IllegalArgumentException("Route is not found to selected target point.");
			}
			if(graphDirectSegments.isEmpty()){
				throw new IllegalArgumentException("Route is not found from selected start point.");
			}
			if(runRecalculation) {
				// nothing to do
				inverse = false;
			} else if (!init) {
				inverse = !inverse;
				init = true;
			} else if (ctx.planRouteIn2Directions()) {
				inverse = nonHeuristicSegmentsComparator.compare(graphDirectSegments.peek(), graphReverseSegments.peek()) > 0;
				if (graphDirectSegments.size() * 1.3 > graphReverseSegments.size()) {
					inverse = true;
				} else if (graphDirectSegments.size() < 1.3 * graphReverseSegments.size()) {
					inverse = false;
				}
			} else {
				// different strategy : use onedirectional graph
				inverse = ctx.getPlanRoadDirection() < 0;
			}
			if (inverse) {
				graphSegments = graphReverseSegments;
			} else {
				graphSegments = graphDirectSegments;
			}

			if(ctx.runRelaxingStrategy() ) {
				relaxNotNeededSegments(ctx, graphDirectSegments, true);
				relaxNotNeededSegments(ctx, graphReverseSegments, false);
			}
			// check if interrupted
			if(ctx.interruptable != null && ctx.interruptable.isCancelled()) {
				throw new InterruptedException("Route calculation interrupted");
			}
		}
		printDebugMemoryInformation(ctx, graphDirectSegments, graphReverseSegments, visitedDirectSegments, visitedOppositeSegments);
		return finalSegment;
	}
	
	

	private void relaxNotNeededSegments(RoutingContext ctx, PriorityQueue<RouteSegment> graphSegments, boolean inverse) {
		// relax strategy is incorrect if we already found a route but it is very long due to some obstacles
		RouteSegment next = graphSegments.peek();
		double mine = next.distanceToEnd;
//		int before = graphSegments.size();
//		SegmentStat statStart = new SegmentStat("Distance from start (" + inverse + ") ");
//		SegmentStat statEnd = new SegmentStat("Distance to end (" + inverse + ") ");
		Iterator<RouteSegment> iterator = graphSegments.iterator();
		while (iterator.hasNext()) {
			RouteSegment s = iterator.next();
//			statStart.addNumber((float) s.distanceFromStart);
//			statEnd.addNumber((float) s.distanceToEnd);
			if (s.distanceToEnd < mine) {
				mine = s.distanceToEnd;
			}
		}
		double d = mine * ctx.config.RELAX_NODES_IF_START_DIST_COEF;
		iterator = graphSegments.iterator();
		while (iterator.hasNext()) {
			RouteSegment s = iterator.next();
			if (s.distanceToEnd > d) {
				ctx.relaxedSegments++;
				iterator.remove();
			}
		}
//		int after = graphSegments.size();
//		println(statStart.toString());
//		println(statEnd.toString());
//		println("Relaxing : before " + before + " after " + after + " minend " + ((float) mine));
	}

	private float h(final RoutingContext ctx, int targetEndX, int targetEndY,
			int startX, int startY) {
		double distance = squareRootDist(startX, startY, targetEndX, targetEndY);
		return (float) (distance / ctx.getRouter().getMaxDefaultSpeed());
	}
	
	protected static float h(RoutingContext ctx, float distToFinalPoint, RouteSegment next) {
		double result = distToFinalPoint / ctx.getRouter().getMaxDefaultSpeed();
		if(ctx.isUseDynamicRoadPrioritising() && next != null){
			double priority = ctx.getRouter().getFutureRoadPriority(next.road);
			result /= priority;
			int dist = ctx.getDynamicRoadPriorityDistance();
			// only first N km-s count by dynamic priority
			if(distToFinalPoint > dist && dist != 0){
				result = (distToFinalPoint - dist) / ctx.getRouter().getMaxDefaultSpeed() + 
						dist / (ctx.getRouter().getMaxDefaultSpeed() * priority);
			}
		}
		return (float) result; 
	}
	
	
	
	private static void println(String logMsg) {
//		log.info(logMsg);
		System.out.println(logMsg);
	}
	
	private static void printInfo(String logMsg) {
		log.warn(logMsg);
	}
	
	public void printDebugMemoryInformation(RoutingContext ctx, PriorityQueue<RouteSegment> graphDirectSegments, PriorityQueue<RouteSegment> graphReverseSegments, 
			TLongObjectHashMap<RouteSegment> visitedDirectSegments,TLongObjectHashMap<RouteSegment> visitedOppositeSegments) {
		printInfo("Time to calculate : " + (System.nanoTime() - ctx.timeToCalculate) / 1e6 + ", time to load : " + ctx.timeToLoad / 1e6 + ", time to load headers : " + ctx.timeToLoadHeaders / 1e6);
		int maxLoadedTiles = Math.max(ctx.maxLoadedTiles, ctx.getCurrentlyLoadedTiles());
		printInfo("Current loaded tiles : " + ctx.getCurrentlyLoadedTiles() + ", maximum loaded tiles " + maxLoadedTiles);
		printInfo("Loaded tiles " + ctx.loadedTiles + " (distinct "+ctx.distinctLoadedTiles+ "), unloaded tiles " + ctx.unloadedTiles + 
				", loaded more than once same tiles "
				+ ctx.loadedPrevUnloadedTiles );
		printInfo("Visited roads, " + ctx.visitedSegments + ", relaxed roads " + ctx.relaxedSegments);
		if (graphDirectSegments != null && graphReverseSegments != null) {
			printInfo("Priority queues sizes : " + graphDirectSegments.size() + "/" + graphReverseSegments.size());
		}
		if (visitedDirectSegments != null && visitedOppositeSegments != null) {
			printInfo("Visited segments sizes: " + visitedDirectSegments.size() + "/" + visitedOppositeSegments.size());
		}

	}
	

	private void processRouteSegment(final RoutingContext ctx, boolean reverseWaySearch,
			PriorityQueue<RouteSegment> graphSegments, TLongObjectHashMap<RouteSegment> visitedSegments, int targetEndX, int targetEndY,
            RouteSegment segment, TLongObjectHashMap<RouteSegment> oppositeSegments) throws IOException {
		// Always start from segmentStart (!), not from segmentEnd
		// It makes difference only for the first start segment
		// Middle point will always be skipped from observation considering already visited
		final RouteDataObject road = segment.road;
		final int middle = segment.getSegmentStart();
		float obstaclePlusTime = 0;
		float obstacleMinusTime = 0;
		
		
		int oneway = ctx.getRouter().isOneWay(road);
		boolean minusAllowed;
		boolean plusAllowed; 
		// use positive direction as agreed
		if(ctx.firstRoadId == calculateRoutePointId(road, middle, true) ) {
			minusAllowed = ctx.firstRoadDirection <= 0;
			plusAllowed = ctx.firstRoadDirection >= 0;
		} else if (!reverseWaySearch) {
			minusAllowed = oneway <= 0;
			plusAllowed = oneway >= 0;
		} else {
			minusAllowed = oneway >= 0;
			plusAllowed = oneway <= 0;
		}
		if(middle == road.getPointsLength() - 1 ||
				visitedSegments.containsKey(calculateRoutePointId(road, middle, true)) || 
				segment.getAllowedDirection() == -1) {
			plusAllowed = false;
		}
		if(middle == 0 || visitedSegments.containsKey(calculateRoutePointId(road, middle - 1, false)) || 
				segment.getAllowedDirection() == 1) {
			minusAllowed = false;
		}

		// +/- diff from middle point
		int d = plusAllowed ? 1 : -1;
		if(segment.getParentRoute() != null) {
			if(plusAllowed && middle < segment.getRoad().getPointsLength() - 1) {
				obstaclePlusTime = (float) ctx.getRouter().calculateTurnTime(segment, segment.getRoad().getPointsLength() - 1,  
					segment.getParentRoute(), segment.getParentSegmentEnd());
			}
			if(minusAllowed && middle > 0) {
				obstacleMinusTime = (float) ctx.getRouter().calculateTurnTime(segment, 0, 
						segment.getParentRoute(), segment.getParentSegmentEnd());
			}
		}
		// Go through all point of the way and find ways to continue
		// ! Actually there is small bug when there is restriction to move forward on the way (it doesn't take into account)
		float posSegmentDist = 0;
		float negSegmentDist = 0;
		while (minusAllowed || plusAllowed) {
			// 1. calculate point not equal to middle
			// (algorithm should visit all point on way if it is not oneway)
			int segmentEnd = middle + d;
			boolean positive = d > 0;
			// calculate d for next iteration
			if (!minusAllowed && d > 0) {
				d++;
			} else if (!plusAllowed && d < 0) {
				d--;
			} else {
				if (d <= 0) {
					d = -d + 1;
				} else {
					d = -d;
				}
			}
			if (segmentEnd < 0) {
				minusAllowed = false;
				continue;
			}
			if (segmentEnd >= road.getPointsLength()) {
				plusAllowed = false;
				continue;
			}
			int intervalId = positive ? segmentEnd - 1 : segmentEnd;
			long nds = calculateRoutePointId(road, intervalId, positive);
			visitedSegments.put(nds, segment);
			RouteSegment opposite = null;
			// check if that segment was already visited in different direction
			long opp = calculateRoutePointId(road, intervalId, !positive);
			if (oppositeSegments.containsKey(opp)) {
				opposite = oppositeSegments.get(opp);
				if (opposite.getSegmentStart() != segmentEnd) {
					opposite = null;
				}
			}
			

			// 2. calculate point and try to load neighbor ways if they are not loaded
			int x = road.getPoint31XTile(segmentEnd);
			int y = road.getPoint31YTile(segmentEnd);
			if(positive) {
				posSegmentDist += squareRootDist(x, y, 
						road.getPoint31XTile(segmentEnd - 1), road.getPoint31YTile(segmentEnd - 1));
			} else {
				negSegmentDist += squareRootDist(x, y, 
						road.getPoint31XTile(segmentEnd + 1), road.getPoint31YTile(segmentEnd + 1));
			}
			
			// 2.1 calculate possible obstacle plus time
			if(positive){
				double obstacle = ctx.getRouter().defineRoutingObstacle(road, segmentEnd);
				if (obstacle < 0) {
					plusAllowed = false;
					continue;
				}
				obstaclePlusTime +=  obstacle;
			} else {
				double obstacle = ctx.getRouter().defineRoutingObstacle(road, segmentEnd);
				if (obstacle < 0) {
					minusAllowed = false;
					continue;
				}
				obstacleMinusTime +=  obstacle;
			}
			
			
			// could be expensive calculation
			RouteSegment next = ctx.loadRouteSegment(x, y, ctx.config.memoryLimitation - ctx.memoryOverhead);
			// 3. get intersected ways
			if (next != null || opposite != null) {
				// Using A* routing algorithm
				// g(x) - calculate distance to that point and calculate time
				
				float  priority = (float) ctx.getRouter().defineSpeedPriority(road);
				float speed = (float) (ctx.getRouter().defineSpeed(road) * priority);
				if (speed == 0) {
					speed = (float) (ctx.getRouter().getMinDefaultSpeed() * priority);
				}
				float distOnRoadToPass = positive? posSegmentDist : negSegmentDist;
				float distStartObstacles = segment.distanceFromStart + ( positive ? obstaclePlusTime : obstacleMinusTime) +
						 distOnRoadToPass / speed;
				
				if(opposite != null) {
					FinalRouteSegment frs = new FinalRouteSegment(road, segment.getSegmentStart());
					frs.setParentRoute(segment.getParentRoute());
					frs.setParentSegmentEnd(segment.getParentSegmentEnd());
					frs.reverseWaySearch = reverseWaySearch;
					frs.distanceFromStart = opposite.distanceFromStart + distStartObstacles;
					frs.distanceToEnd = 0;
					frs.opposite = opposite;
					graphSegments.add(frs);
					if(positive) {
						plusAllowed = false;
					} else {
						minusAllowed = false;
					}
					continue;
				}
				if(next == null) {
					continue;
				}
				
				// TO-DO U-Turn
				if((next == segment || next.road.id == road.id) && next.next == null) {
					// simplification if there is no real intersection
					continue;
				}
				// check if there are outgoing connections in that case we need to stop processing
				boolean outgoingConnections = false;
				RouteSegment r = next;
				while(r != null && !outgoingConnections) {
					if(r.road.id != road.id || r.getSegmentStart() != 0 || r.road.getOneway() != 1){
						outgoingConnections = true;
					}
					r = r.next;
				}
				if (outgoingConnections) {
					if (positive) {
						plusAllowed = false;
					} else {
						minusAllowed = false;
					}
				}
				
				
				float distToFinalPoint = (float) squareRootDist(x, y, targetEndX, targetEndY);
				processIntersections(ctx, graphSegments, visitedSegments, 
						distStartObstacles, distToFinalPoint, segment, segmentEnd, next, reverseWaySearch, outgoingConnections);
				
			}
		}
	}
	

	private long calculateRoutePointId(final RouteDataObject road, int intervalId, boolean positive) {
		return (road.getId() << ROUTE_POINTS) + (intervalId << 1) + (positive ? 1 : 0);
	}


	private boolean proccessRestrictions(RoutingContext ctx, RouteDataObject road, RouteSegment inputNext, boolean reverseWay) {
		ctx.segmentsToVisitPrescripted.clear();
		ctx.segmentsToVisitNotForbidden.clear();
		boolean exclusiveRestriction = false;
		RouteSegment next = inputNext;
		if (!reverseWay && road.getRestrictionLength() == 0) {
			return false;
		}
		if(!ctx.getRouter().restrictionsAware()) {
			return false;
		}
		while (next != null) {
			int type = -1;
			if (!reverseWay) {
				for (int i = 0; i < road.getRestrictionLength(); i++) {
					if (road.getRestrictionId(i) == next.road.id) {
						type = road.getRestrictionType(i);
						break;
					}
				}
			} else {
				for (int i = 0; i < next.road.getRestrictionLength(); i++) {
					int rt = next.road.getRestrictionType(i);
					long restrictedTo = next.road.getRestrictionId(i);
					if (restrictedTo == road.id) {
						type = rt;
						break;
					}

					// Check if there is restriction only to the other than current road
					if (rt == MapRenderingTypes.RESTRICTION_ONLY_RIGHT_TURN || rt == MapRenderingTypes.RESTRICTION_ONLY_LEFT_TURN
							|| rt == MapRenderingTypes.RESTRICTION_ONLY_STRAIGHT_ON) {
						// check if that restriction applies to considered junk
						RouteSegment foundNext = inputNext;
						while (foundNext != null) {
							if (foundNext.getRoad().id == restrictedTo) {
								break;
							}
							foundNext = foundNext.next;
						}
						if (foundNext != null) {
							type = REVERSE_WAY_RESTRICTION_ONLY; // special constant
						}
					}
				}
			}
			if (type == REVERSE_WAY_RESTRICTION_ONLY) {
				// next = next.next; continue;
			} else if (type == -1 && exclusiveRestriction) {
				// next = next.next; continue;
			} else if (type == MapRenderingTypes.RESTRICTION_NO_LEFT_TURN || type == MapRenderingTypes.RESTRICTION_NO_RIGHT_TURN
					|| type == MapRenderingTypes.RESTRICTION_NO_STRAIGHT_ON || type == MapRenderingTypes.RESTRICTION_NO_U_TURN) {
				// next = next.next; continue;
			} else if (type == -1) {
				// case no restriction
				ctx.segmentsToVisitNotForbidden.add(next);
			} else {
				// case exclusive restriction (only_right, only_straight, ...)
				// 1. in case we are going backward we should not consider only_restriction
				// as exclusive because we have many "in" roads and one "out"
				// 2. in case we are going forward we have one "in" and many "out"
				if (!reverseWay) {
					exclusiveRestriction = true;
					ctx.segmentsToVisitNotForbidden.clear();
					ctx.segmentsToVisitPrescripted.add(next);
				} else {
					ctx.segmentsToVisitNotForbidden.add(next);
				}
			}
			next = next.next;
		}
		ctx.segmentsToVisitPrescripted.addAll(ctx.segmentsToVisitNotForbidden);
		return true;
	}
	
	


	private void processIntersections(RoutingContext ctx, PriorityQueue<RouteSegment> graphSegments,
			TLongObjectHashMap<RouteSegment> visitedSegments,  float  distFromStart, float distToFinalPoint, 
			RouteSegment segment, int segmentEnd, RouteSegment inputNext, boolean reverseWaySearch, 
			boolean addSameRoadFutureDirection) {
		byte searchDirection = reverseWaySearch ? (byte)-1 : (byte)1;
		boolean thereAreRestrictions = proccessRestrictions(ctx, segment.road, inputNext, reverseWaySearch);
		Iterator<RouteSegment> nextIterator = null;
		if (thereAreRestrictions) {
			nextIterator = ctx.segmentsToVisitPrescripted.iterator();
		}
		// Calculate possible ways to put into priority queue
		RouteSegment next = inputNext;
		boolean hasNext = nextIterator == null || nextIterator.hasNext();
		while (hasNext) {
			if (nextIterator != null) {
				next = nextIterator.next();
			}
			boolean nextPlusNotAllowed = (next.getSegmentStart() == next.road.getPointsLength() - 1) ||
					visitedSegments.containsKey(calculateRoutePointId(next.road, next.getSegmentStart(), true));
			boolean nextMinusNotAllowed = (next.getSegmentStart() == 0) ||
					visitedSegments.containsKey(calculateRoutePointId(next.road, next.getSegmentStart() - 1, false));
			boolean sameRoadFutureDirection = next.road.id == segment.road.id && next.getSegmentStart() == segmentEnd;
			// road.id could be equal on roundabout, but we should accept them
			boolean alreadyVisited = nextPlusNotAllowed && nextMinusNotAllowed;
			boolean skipRoad = sameRoadFutureDirection && !addSameRoadFutureDirection;
			if (!alreadyVisited && !skipRoad) {
				float distanceToEnd = h(ctx, distToFinalPoint, next);
				// assigned to wrong direction
				if(next.getDirectionAssigned() == -searchDirection){
					next = new RouteSegment(next.getRoad(), next.getSegmentStart());
				}
				 
				if (next.getParentRoute() == null
						|| ctx.roadPriorityComparator(next.distanceFromStart, next.distanceToEnd, distFromStart, distanceToEnd) > 0) {
					if (next.getParentRoute() != null) {
						if (!graphSegments.remove(next)) {
							throw new IllegalStateException("Should be handled by direction flag");
						} 
					} 
					next.assignDirection(searchDirection);
					next.distanceFromStart = distFromStart;
					next.distanceToEnd = distanceToEnd;
					if(sameRoadFutureDirection) {
						next.setAllowedDirection((byte) (segment.getSegmentStart() < next.getSegmentStart() ? 1 : - 1));
					}
					// put additional information to recover whole route after
					next.setParentRoute(segment);
					next.setParentSegmentEnd(segmentEnd);
					graphSegments.add(next);
				}
				if (ctx.visitor != null) {
					ctx.visitor.visitSegment(next, false);
				}
			} else if(!sameRoadFutureDirection){
				// the segment was already visited! We need to follow better route if it exists
				// that is very strange situation and almost exception (it can happen when we underestimate distnceToEnd)
				if (next.getDirectionAssigned() == searchDirection && 
						distFromStart < next.distanceFromStart && next.road.id != segment.road.id) {
					if(ctx.config.heuristicCoefficient <= 1) {
						throw new IllegalStateException("distance from start " + distFromStart + " < " +next.distanceFromStart); 
					}
					// That code is incorrect (when segment is processed itself,
					// then it tries to make wrong u-turn) -
					// this situation should be very carefully checked in future (seems to be fixed)
					next.distanceFromStart = distFromStart;
					next.setParentRoute(segment);
					next.setParentSegmentEnd(segmentEnd);
					if (ctx.visitor != null) {
						ctx.visitor.visitSegment(next, false);
					}
				}
			}

			// iterate to next road
			if (nextIterator == null) {
				next = next.next;
				hasNext = next != null;
			} else {
				hasNext = nextIterator.hasNext();
			}
		}
	}
	
	
	/**
	 * Helper method to prepare final result 
	 */
	private List<RouteSegmentResult> prepareResult(RoutingContext ctx, FinalRouteSegment finalSegment,boolean leftside) {
		List<RouteSegmentResult> result = new ArrayList<RouteSegmentResult>();
		
		if (finalSegment != null) {
			println("Routing calculated time distance " + finalSegment.distanceFromStart);
			// Get results from opposite direction roads
			RouteSegment segment = finalSegment.reverseWaySearch ? finalSegment : finalSegment.opposite.getParentRoute();
			int parentSegmentStart = finalSegment.reverseWaySearch ? finalSegment.opposite.getSegmentStart() : finalSegment.opposite.getParentSegmentEnd();
			
			if(segment != null) {
//				println(segment.road +" 1->"+ finalSegment.reverseWaySearch+"?"+finalSegment.finalSegmentEnd+":"+finalSegment.opposite.parentSegmentEnd + "="+ finalSegment.opposite.segmentStart);
			}
			while (segment != null) {
				RouteSegmentResult res = new RouteSegmentResult(segment.road, parentSegmentStart, segment.getSegmentStart());
				parentSegmentStart = segment.getParentSegmentEnd();
				segment = segment.getParentRoute();
				if (res.getStartPointIndex() != res.getEndPointIndex()) {
					result.add(res);
				}
			}
			// reverse it just to attach good direction roads
			Collections.reverse(result);

			segment = finalSegment.reverseWaySearch ? finalSegment.opposite.getParentRoute() : finalSegment;
			int parentSegmentEnd = finalSegment.reverseWaySearch ? finalSegment.opposite.getParentSegmentEnd() : finalSegment.opposite.getSegmentStart();
			
			if(segment != null) {
//				println(segment.road +" 2->"+ finalSegment.reverseWaySearch+"?"+finalSegment.finalSegmentEnd+":"+finalSegment.opposite.parentSegmentEnd + "="+ finalSegment.opposite.segmentStart);
			}
			while (segment != null) {
				RouteSegmentResult res = new RouteSegmentResult(segment.road, segment.getSegmentStart(), parentSegmentEnd);
				parentSegmentEnd = segment.getParentSegmentEnd();
				segment = segment.getParentRoute();
				// happens in smart recalculation
				if (res.getStartPointIndex() != res.getEndPointIndex()) {
					result.add(res);
				}
			}
			Collections.reverse(result);

		}

		

		

		// calculate time
		for (int i = 0; i < result.size(); i++) {
			if(ctx.checkIfMemoryLimitCritical(ctx.config.memoryLimitation)) {
				ctx.unloadUnusedTiles(ctx.config.memoryLimitation);
			}
			RouteSegmentResult rr = result.get(i);
			RouteDataObject road = rr.getObject();
			double distOnRoadToPass = 0;
			double speed = ctx.getRouter().defineSpeed(road);
			if (speed == 0) {
				speed = ctx.getRouter().getMinDefaultSpeed();
			}
			boolean plus = rr.getStartPointIndex() < rr.getEndPointIndex();
			int next;
			double distance = 0;
			for (int j = rr.getStartPointIndex(); j != rr.getEndPointIndex(); j = next) {
				next = plus ? j + 1 : j - 1;
				if(j == rr.getStartPointIndex()) {
					attachRoadSegments(ctx, result, i, j, plus);
				}
				if(next != rr.getEndPointIndex()) {
					attachRoadSegments(ctx, result, i, next, plus);
				}
				
				double d = measuredDist(road.getPoint31XTile(j), road.getPoint31YTile(j), road.getPoint31XTile(next),
						road.getPoint31YTile(next));
				distance += d;
				double obstacle = ctx.getRouter().defineObstacle(road, j);
				if(obstacle < 0) {
					obstacle = 0;
				}
				distOnRoadToPass += d / speed + obstacle;
				
				List<RouteSegmentResult> attachedRoutes = rr.getAttachedRoutes(next);
				if (next != rr.getEndPointIndex() && !rr.getObject().roundabout() && attachedRoutes != null) {
					float before = rr.getBearing(next, !plus);
					float after = rr.getBearing(next, plus);
					boolean straight = Math.abs(MapUtils.degreesDiff(before + 180, after)) < TURN_DEGREE_MIN;
					boolean split = false;
					// split if needed
					for (RouteSegmentResult rs : attachedRoutes) {
						double diff = MapUtils.degreesDiff(before + 180, rs.getBearingBegin());
						if (Math.abs(diff) <= TURN_DEGREE_MIN) {
							split = true;
						} else if (!straight && Math.abs(diff) < 100) {
							split = true;
						}
					}
					if (split) {
						int endPointIndex = rr.getEndPointIndex();
						RouteSegmentResult splitted = new RouteSegmentResult(rr.getObject(), next, endPointIndex);
						rr.setSegmentTime((float) distOnRoadToPass);
						rr.setSegmentSpeed((float) speed);
						rr.setDistance((float) distance);
						rr.setEndPointIndex(next);

						result.add(i + 1, splitted);
						// switch current segment to the splitted
						rr = splitted;
						distOnRoadToPass = 0;
						distance = 0;
					}
				}
			}
			// last point turn time can be added
			// if(i + 1 < result.size()) { distOnRoadToPass += ctx.getRouter().calculateTurnTime(); }
			rr.setSegmentTime((float) distOnRoadToPass);
			rr.setSegmentSpeed((float) speed);
			rr.setDistance((float) distance);

			
		}
		addTurnInfo(leftside, result);
		return result;
	}


	private void printResults(RoutingContext ctx, RouteSegment start, RouteSegment end, List<RouteSegmentResult> result) {
		float completeTime = 0;
		float completeDistance = 0;
		for(RouteSegmentResult r : result) {
			completeTime += r.getSegmentTime();
			completeDistance += r.getDistance();
		}

		println("ROUTE : ");
		double startLat = MapUtils.get31LatitudeY(start.road.getPoint31YTile(start.getSegmentStart()));
		double startLon = MapUtils.get31LongitudeX(start.road.getPoint31XTile(start.getSegmentStart()));
		double endLat = MapUtils.get31LatitudeY(end.road.getPoint31YTile(end.getSegmentStart()));
		double endLon = MapUtils.get31LongitudeX(end.road.getPoint31XTile(end.getSegmentStart()));
		StringBuilder add = new StringBuilder();
		add.append("loadedTiles = \"").append(ctx.loadedTiles).append("\" ");
		add.append("visitedSegments = \"").append(ctx.visitedSegments).append("\" ");
		add.append("complete_distance = \"").append(completeDistance).append("\" ");
		println(MessageFormat.format("<test regions=\"\" description=\"\" best_percent=\"\" vehicle=\"{5}\" \n"
				+ "    start_lat=\"{0}\" start_lon=\"{1}\" target_lat=\"{2}\" target_lon=\"{3}\" complete_time=\"{4}\" {6} >", startLat
				+ "", startLon + "", endLat + "", endLon + "", completeTime + "", ctx.config.routerName, add.toString()));
		if (PRINT_TO_CONSOLE_ROUTE_INFORMATION_TO_TEST) {
			for (RouteSegmentResult res : result) {
				String name = res.getObject().getName();
				String ref = res.getObject().getRef();
				if (name == null) {
					name = "";
				}
				if (ref != null) {
					name += " (" + ref + ") ";
				}
				StringBuilder additional = new StringBuilder();
				additional.append("time = \"").append(res.getSegmentTime()).append("\" ");
				additional.append("name = \"").append(name).append("\" ");
				additional.append("distance = \"").append(res.getDistance()).append("\" ");
				if (res.getTurnType() != null) {
					additional.append("turn = \"").append(res.getTurnType()).append("\" ");
					additional.append("turn_angle = \"").append(res.getTurnType().getTurnAngle()).append("\" ");
					if (res.getTurnType().getLanes() != null) {
						additional.append("lanes = \"").append(Arrays.toString(res.getTurnType().getLanes())).append("\" ");
					}
				}
				additional.append("start_bearing = \"").append(res.getBearingBegin()).append("\" ");
				additional.append("end_bearing = \"").append(res.getBearingEnd()).append("\" ");
				additional.append("description = \"").append(res.getDescription()).append("\" ");
				println(MessageFormat.format("\t<segment id=\"{0}\" start=\"{1}\" end=\"{2}\" {3}/>", (res.getObject().getId()) + "",
						res.getStartPointIndex() + "", res.getEndPointIndex() + "", additional.toString()));
			}
		}
		println("</test>");
	}


	private void addTurnInfo(boolean leftside, List<RouteSegmentResult> result) {
		int prevSegment = -1;
		float dist = 0;
		int next = 1;
		for (int i = 0; i <= result.size(); i = next) {
			TurnType t = null;
			next = i + 1;
			if (i < result.size()) {
				t = getTurnInfo(result, i, leftside);
				// justify turn
				if(t != null && i < result.size() - 1) {
					boolean tl = TurnType.TL.equals(t.getValue());
					boolean tr = TurnType.TR.equals(t.getValue());
					if(tl || tr) {
						TurnType tnext = getTurnInfo(result, i + 1, leftside);
						if(tnext != null && result.get(i).getDistance() < 35) {
							if(tl && TurnType.TL.equals(tnext.getValue()) ) {
								next = i + 2;
								t = TurnType.valueOf(TurnType.TU, false);
							} else if(tr && TurnType.TR.equals(tnext.getValue()) ) {
								next = i + 2;
								t = TurnType.valueOf(TurnType.TU, true);
							}
						}
					}
				}
				result.get(i).setTurnType(t);
			}
			if (t != null || i == result.size()) {
				if (prevSegment >= 0) {
					String turn = result.get(prevSegment).getTurnType().toString();
					if (result.get(prevSegment).getTurnType().getLanes() != null) {
						turn += Arrays.toString(result.get(prevSegment).getTurnType().getLanes());
					}
					result.get(prevSegment).setDescription(turn + String.format(" and go %.2f meters", dist));
					if(result.get(prevSegment).getTurnType().isSkipToSpeak()) {
						result.get(prevSegment).setDescription(result.get(prevSegment).getDescription() +" (*)");
					}
				}
				prevSegment = i;
				dist = 0;
			}
			if (i < result.size()) {
				dist += result.get(i).getDistance();
			}
		}
	}
	
	private static final int MAX_SPEAK_PRIORITY = 5;
	private int highwaySpeakPriority(String highway) {
		if(highway == null || highway.endsWith("track") || highway.endsWith("services") || highway.endsWith("service")
				|| highway.endsWith("path")) {
			return MAX_SPEAK_PRIORITY;
		}
		if (highway.endsWith("_link")  || highway.endsWith("unclassified") || highway.endsWith("road") 
				|| highway.endsWith("living_street") || highway.endsWith("residential") )  {
			return 1;
		}
		return 0;
	}


	private TurnType getTurnInfo(List<RouteSegmentResult> result, int i, boolean leftSide) {
		if (i == 0) {
			return TurnType.valueOf(TurnType.C, false);
		}
		RouteSegmentResult prev = result.get(i - 1) ;
		if(prev.getObject().roundabout()) {
			// already analyzed!
			return null;
		}
		RouteSegmentResult rr = result.get(i);
		if (rr.getObject().roundabout()) {
			return processRoundaboutTurn(result, i, leftSide, prev, rr);
		}
		TurnType t = null;
		if (prev != null) {
			boolean noAttachedRoads = rr.getAttachedRoutes(rr.getStartPointIndex()).size() == 0;
			// add description about turn
			double mpi = MapUtils.degreesDiff(prev.getBearingEnd(), rr.getBearingBegin());
			if(noAttachedRoads){
				// TODO VICTOR : look at the comment inside direction route
//				double begin = rr.getObject().directionRoute(rr.getStartPointIndex(), rr.getStartPointIndex() < 
//						rr.getEndPointIndex(), 25);
//				mpi = MapUtils.degreesDiff(prev.getBearingEnd(), begin);
			}
			if (mpi >= TURN_DEGREE_MIN) {
				if (mpi < 60) {
					t = TurnType.valueOf(TurnType.TSLL, leftSide);
				} else if (mpi < 120) {
					t = TurnType.valueOf(TurnType.TL, leftSide);
				} else if (mpi < 135) {
					t = TurnType.valueOf(TurnType.TSHL, leftSide);
				} else {
					t = TurnType.valueOf(TurnType.TU, leftSide);
				}
			} else if (mpi < -TURN_DEGREE_MIN) {
				if (mpi > -60) {
					t = TurnType.valueOf(TurnType.TSLR, leftSide);
				} else if (mpi > -120) {
					t = TurnType.valueOf(TurnType.TR, leftSide);
				} else if (mpi > -135) {
					t = TurnType.valueOf(TurnType.TSHR, leftSide);
				} else {
					t = TurnType.valueOf(TurnType.TU, leftSide);
				}
			} else {
				t = attachKeepLeftInfoAndLanes(leftSide, prev, rr, t);
			}
			if (t != null) {
				t.setTurnAngle((float) -mpi);
			}
		}
		return t;
	}


	private TurnType processRoundaboutTurn(List<RouteSegmentResult> result, int i, boolean leftSide, RouteSegmentResult prev,
			RouteSegmentResult rr) {
		int exit = 1;
		RouteSegmentResult last = rr;
		for (int j = i; j < result.size(); j++) {
			RouteSegmentResult rnext = result.get(j);
			last = rnext;
			if (rnext.getObject().roundabout()) {
				boolean plus = rnext.getStartPointIndex() < rnext.getEndPointIndex();
				int k = rnext.getStartPointIndex();
				if (j == i) {
					k = plus ? k + 1 : k - 1;
				}
				while (k != rnext.getEndPointIndex()) {
					if (rnext.getAttachedRoutes(k).size() > 0) {
						exit++;
					}
					k = plus ? k + 1 : k - 1;
				}
			} else {
				break;
			}
		}
		// combine all roundabouts
		TurnType t = TurnType.valueOf("EXIT"+exit, leftSide);
		t.setTurnAngle((float) MapUtils.degreesDiff(last.getBearingBegin(), prev.getBearingEnd())) ;
		return t;
	}


	private TurnType attachKeepLeftInfoAndLanes(boolean leftSide, RouteSegmentResult prev, RouteSegmentResult rr, TurnType t) {
		// keep left/right
		int[] lanes =  null;
		boolean kl = false;
		boolean kr = false;
		List<RouteSegmentResult> attachedRoutes = rr.getAttachedRoutes(rr.getStartPointIndex());
		int ls = prev.getObject().getLanes();
		if(ls >= 0 && prev.getObject().getOneway() == 0) {
			ls = (ls + 1) / 2;
		}
		int left = 0;
		int right = 0;
		boolean speak = false;
		int speakPriority = Math.max(highwaySpeakPriority(prev.getObject().getHighway()), highwaySpeakPriority(rr.getObject().getHighway()));
		if (attachedRoutes != null) {
			for (RouteSegmentResult rs : attachedRoutes) {
				double ex = MapUtils.degreesDiff(rs.getBearingBegin(), rr.getBearingBegin());
				double mpi = Math.abs(MapUtils.degreesDiff(prev.getBearingEnd(), rs.getBearingBegin()));
				int rsSpeakPriority = highwaySpeakPriority(rs.getObject().getHighway());
				if (rsSpeakPriority != MAX_SPEAK_PRIORITY || speakPriority == MAX_SPEAK_PRIORITY) {
					if ((ex < TURN_DEGREE_MIN || mpi < TURN_DEGREE_MIN) && ex >= 0) {
						kl = true;
						int lns = rs.getObject().getLanes();
						if(rs.getObject().getOneway() == 0) {
							lns = (lns + 1) / 2;
						}
						if (lns > 0) {
							right += lns;
						}
						speak = speak || rsSpeakPriority <= speakPriority;
					} else if ((ex > -TURN_DEGREE_MIN || mpi < TURN_DEGREE_MIN) && ex <= 0) {
						kr = true;
						int lns = rs.getObject().getLanes();
						if(rs.getObject().getOneway() == 0) {
							lns = (lns + 1) / 2;
						}
						if (lns > 0) {
							left += lns;
						}
						speak = speak || rsSpeakPriority <= speakPriority;
					}
				}
			}
		}
		if(kr && left == 0) {
			left = 1;
		} else if(kl && right == 0) {
			right = 1;
		}
		int current = rr.getObject().getLanes();
		if(rr.getObject().getOneway() == 0) {
			current = (current + 1) / 2;
		}
		if (current <= 0) {
			current = 1;
		}
		if(ls >= 0 /*&& current + left + right >= ls*/){
			lanes = new int[current + left + right];
			ls = current + left + right;
			for(int it=0; it< ls; it++) {
				if(it < left || it >= left + current) {
					lanes[it] = 0;
				} else {
					lanes[it] = 1;
				}
			}
			// sometimes links are 
			if ((current <= left + right) && (left > 1 || right > 1)) {
				speak = true;
			}
		}
		
		if (kl) {
			t = TurnType.valueOf(TurnType.KL, leftSide);
			t.setSkipToSpeak(!speak);
		} else if(kr){
			t = TurnType.valueOf(TurnType.KR, leftSide);
			t.setSkipToSpeak(!speak);
		}
		if (t != null && lanes != null) {
			t.setLanes(lanes);
		}
		return t;
	}
	
	private long getPoint(RouteDataObject road, int pointInd) {
		return (((long) road.getPoint31XTile(pointInd)) << 31) + (long) road.getPoint31YTile(pointInd);
	}


	private void attachRoadSegments(RoutingContext ctx, List<RouteSegmentResult> result, int routeInd, int pointInd, boolean plus) {
		RouteSegmentResult rr = result.get(routeInd);
		RouteDataObject road = rr.getObject();
		long nextL = pointInd < road.getPointsLength() - 1 ? getPoint(road, pointInd + 1) : 0;
		long prevL = pointInd > 0 ? getPoint(road, pointInd - 1) : 0;
		
		// attach additional roads to represent more information about the route
		RouteSegmentResult previousResult = null;
		
		// by default make same as this road id
		long previousRoadId = road.getId();
		if (pointInd == rr.getStartPointIndex() && routeInd > 0) {
			previousResult = result.get(routeInd - 1);
			previousRoadId = previousResult.getObject().getId();
			if (previousRoadId != road.getId()) {
				if (previousResult.getStartPointIndex() < previousResult.getEndPointIndex()
						&& previousResult.getEndPointIndex() < previousResult.getObject().getPointsLength() - 1) {
					rr.attachRoute(pointInd, new RouteSegmentResult(previousResult.getObject(), previousResult.getEndPointIndex(),
							previousResult.getObject().getPointsLength() - 1));
				} else if (previousResult.getStartPointIndex() > previousResult.getEndPointIndex() 
						&& previousResult.getEndPointIndex() > 0) {
					rr.attachRoute(pointInd, new RouteSegmentResult(previousResult.getObject(), previousResult.getEndPointIndex(), 0));
				}
			}
		}
		RouteSegment routeSegment = ctx.loadRouteSegment(road.getPoint31XTile(pointInd), road.getPoint31YTile(pointInd), ctx.config.memoryLimitation);
		// try to attach all segments except with current id
		while (routeSegment != null) {
			if (routeSegment.road.getId() != road.getId() && routeSegment.road.getId() != previousRoadId) {
				RouteDataObject addRoad = routeSegment.road;
				
				// TODO restrictions can be considered as well
				int oneWay = ctx.getRouter().isOneWay(addRoad);
				if (oneWay >= 0 && routeSegment.getSegmentStart() < addRoad.getPointsLength() - 1) {
					long pointL = getPoint(addRoad, routeSegment.getSegmentStart() + 1);
					if(pointL != nextL && pointL != prevL) {
						// if way contains same segment (nodes) as different way (do not attach it)
						rr.attachRoute(pointInd, new RouteSegmentResult(addRoad, routeSegment.getSegmentStart(), addRoad.getPointsLength() - 1));
					}
				}
				if (oneWay <= 0 && routeSegment.getSegmentStart() > 0) {
					long pointL = getPoint(addRoad, routeSegment.getSegmentStart() - 1);
					// if way contains same segment (nodes) as different way (do not attach it)
					if(pointL != nextL && pointL != prevL) {
						rr.attachRoute(pointInd, new RouteSegmentResult(addRoad, routeSegment.getSegmentStart(), 0));
					}
				}
			}
			routeSegment = routeSegment.next;
		}
	}
	
	
	/*public */static int roadPriorityComparator(double o1DistanceFromStart, double o1DistanceToEnd, 
			double o2DistanceFromStart, double o2DistanceToEnd, double heuristicCoefficient ) {
		// f(x) = g(x) + h(x)  --- g(x) - distanceFromStart, h(x) - distanceToEnd (not exact)
		return Double.compare(o1DistanceFromStart + heuristicCoefficient * o1DistanceToEnd, 
				o2DistanceFromStart + heuristicCoefficient *  o2DistanceToEnd);
	}

	
	public interface RouteSegmentVisitor {
		
		public void visitSegment(RouteSegment segment, boolean poll);
	}
	
	public static class RouteSegment {
		final short segStart;
		final RouteDataObject road;
		// needed to store intersection of routes
		RouteSegment next = null;
		
		// search context (needed for searching route)
		// Initially it should be null (!) because it checks was it segment visited before
		RouteSegment parentRoute = null;
		short parentSegmentEnd = 0;
		// 1 - positive , -1 - negative, 0 not assigned
		byte directionAssgn = 0;
		// 1 - only positive allowed, -1 - only negative allowed
		byte allowedDirection = 0;
		
		// distance measured in time (seconds)
		float distanceFromStart = 0;
		float distanceToEnd = 0;
		
		public RouteSegment(RouteDataObject road, int segmentStart) {
			this.road = road;
			this.segStart = (short) segmentStart;
		}
		
		public byte getDirectionAssigned(){
			return directionAssgn;
		}
		
		public RouteSegment getParentRoute() {
			return parentRoute;
		}
		
		public void setParentRoute(RouteSegment parentRoute) {
			this.parentRoute = parentRoute;
		}
		
		public void assignDirection(byte b) {
			directionAssgn = b;
		}
		
		public byte getAllowedDirection() {
			return allowedDirection;
		}
		
		public void setAllowedDirection(byte allowedDirection) {
			this.allowedDirection = allowedDirection;
		}
		
		
		public void setParentSegmentEnd(int parentSegmentEnd) {
			this.parentSegmentEnd = (short) parentSegmentEnd;
		}
		
		public int getParentSegmentEnd() {
			return parentSegmentEnd;
		}
		
		public RouteSegment getNext() {
			return next;
		}
		
		public int getSegmentStart() {
			return segStart;
		}
		
		public RouteDataObject getRoad() {
			return road;
		}
		
		public String getTestName(){
			return String.format("s%.2f e%.2f", ((float)distanceFromStart), ((float)distanceToEnd));
		}
		
	}
	
	private class FinalRouteSegment extends RouteSegment {
		
		boolean reverseWaySearch;
		RouteSegment opposite;

		public FinalRouteSegment(RouteDataObject road, int segmentStart) {
			super(road, segmentStart);
		}
		
	}
	
}
