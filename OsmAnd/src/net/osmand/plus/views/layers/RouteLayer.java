package net.osmand.plus.views.layers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.LayerDrawable;

import net.osmand.AndroidUtils;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.data.TransportStop;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.mapcontextmenu.other.TrackChartPoints;
import net.osmand.plus.measurementtool.MeasurementToolFragment;
import net.osmand.plus.profiles.LocationIcon;
import net.osmand.plus.routing.ColoringTypeAvailabilityCache;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RouteService;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.TransportRoutingHelper;
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider;
import net.osmand.plus.views.layers.geometry.PublicTransportGeometryWay;
import net.osmand.plus.views.layers.geometry.PublicTransportGeometryWayContext;
import net.osmand.plus.views.layers.geometry.RouteGeometryWay;
import net.osmand.plus.views.layers.geometry.RouteGeometryWayContext;
import net.osmand.router.TransportRouteResult;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

public class RouteLayer extends BaseRouteLayer implements IContextMenuProvider {

	private static final Log log = PlatformUtil.getLog(RouteLayer.class);

	private final RoutingHelper helper;
	private final TransportRoutingHelper transportHelper;
	// keep array lists created
	private final List<Location> actionPoints = new ArrayList<>();

	private Paint paintGridOuterCircle;
	private Paint paintGridCircle;

	private LayerDrawable selectedPoint;
	private TrackChartPoints trackChartPoints;

	private RenderingLineAttributes attrsPT;
	private RenderingLineAttributes attrsW;

	private RouteGeometryWayContext routeWayContext;
	private PublicTransportGeometryWayContext publicTransportWayContext;
	private RouteGeometryWay routeGeometry;
	private PublicTransportGeometryWay publicTransportRouteGeometry;

	private final ColoringTypeAvailabilityCache coloringAvailabilityCache;

	private LayerDrawable projectionIcon;

	public RouteLayer(RoutingHelper helper) {
		this.helper = helper;
		this.transportHelper = helper.getTransportRoutingHelper();
		coloringAvailabilityCache = new ColoringTypeAvailabilityCache(helper.getApplication());
	}

	public RoutingHelper getHelper() {
		return helper;
	}

	public void setTrackChartPoints(TrackChartPoints trackChartPoints) {
		this.trackChartPoints = trackChartPoints;
	}

	@Override
	protected void initAttrs(float density) {
		super.initAttrs(density);

		attrsPT = new RenderingLineAttributes("publicTransportLine");
		attrsPT.defaultWidth = (int) (12 * density);
		attrsPT.defaultWidth3 = (int) (7 * density);
		attrsPT.defaultColor = view.getResources().getColor(R.color.nav_track);
		attrsPT.paint3.setStrokeCap(Cap.BUTT);
		attrsPT.paint3.setColor(Color.WHITE);
		attrsPT.paint2.setStrokeCap(Cap.BUTT);
		attrsPT.paint2.setColor(Color.BLACK);

		attrsW = new RenderingLineAttributes("walkingRouteLine");
		attrsW.defaultWidth = (int) (12 * density);
		attrsW.defaultWidth3 = (int) (7 * density);
		attrsW.defaultColor = view.getResources().getColor(R.color.nav_track_walk_fill);
		attrsW.paint3.setStrokeCap(Cap.BUTT);
		attrsW.paint3.setColor(Color.WHITE);
		attrsW.paint2.setStrokeCap(Cap.BUTT);
		attrsW.paint2.setColor(Color.BLACK);
	}

	@Override
	protected void initGeometries(float density) {
		routeWayContext = new RouteGeometryWayContext(view.getContext(), density);
		routeWayContext.updatePaints(nightMode, attrs);
		routeGeometry = new RouteGeometryWay(routeWayContext);

		publicTransportWayContext = new PublicTransportGeometryWayContext(view.getContext(), density);
		publicTransportWayContext.updatePaints(nightMode, attrs, attrsPT, attrsW);
		publicTransportRouteGeometry = new PublicTransportGeometryWay(publicTransportWayContext);
	}

	protected void initPaints() {
		super.initPaints();

		paintGridCircle = new Paint();
		paintGridCircle.setStyle(Paint.Style.FILL_AND_STROKE);
		paintGridCircle.setAntiAlias(true);
		paintGridCircle.setColor(attrs.defaultColor);
		paintGridCircle.setAlpha(255);

		paintGridOuterCircle = new Paint();
		paintGridOuterCircle.setStyle(Paint.Style.FILL_AND_STROKE);
		paintGridOuterCircle.setAntiAlias(true);
		paintGridOuterCircle.setColor(Color.WHITE);
		paintGridOuterCircle.setAlpha(204);
	}

	@Override
	protected void initIcons() {
		super.initIcons();
		selectedPoint = (LayerDrawable) AppCompatResources.getDrawable(view.getContext(), R.drawable.map_location_default);
	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		if ((helper.isPublicTransportMode() && transportHelper.getRoutes() != null) ||
				(helper.getFinalLocation() != null && helper.getRoute().isCalculated()) ||
				isPlanRouteGraphsAvailable()) {

			updateRouteColoringType();
			updateAttrs(settings, tileBox);
			updateRouteColors(nightMode);
			
			int w = tileBox.getPixWidth();
			int h = tileBox.getPixHeight();
			Location lastProjection = helper.getLastProjection();
			final RotatedTileBox cp ;
			if(lastProjection != null &&
					tileBox.containsLatLon(lastProjection.getLatitude(), lastProjection.getLongitude())){
				cp = tileBox.copy();
				cp.increasePixelDimensions(w /2, h);
			} else {
				cp = tileBox;
			}

			final QuadRect latlonRect = cp.getLatLonBounds();
			final QuadRect correctedQuadRect = getCorrectedQuadRect(latlonRect);
			drawLocations(tileBox, canvas, correctedQuadRect.top, correctedQuadRect.left, correctedQuadRect.bottom, correctedQuadRect.right);

			if (trackChartPoints != null) {
				canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());

				drawXAxisPoints(canvas, tileBox);
				LatLon highlightedPoint = trackChartPoints.getHighlightedPoint();
				if (highlightedPoint != null
						&& highlightedPoint.getLatitude() >= latlonRect.bottom
						&& highlightedPoint.getLatitude() <= latlonRect.top
						&& highlightedPoint.getLongitude() >= latlonRect.left
						&& highlightedPoint.getLongitude() <= latlonRect.right) {
					float x = tileBox.getPixXFromLatLon(highlightedPoint.getLatitude(), highlightedPoint.getLongitude());
					float y = tileBox.getPixYFromLatLon(highlightedPoint.getLatitude(), highlightedPoint.getLongitude());
					selectedPoint.setBounds((int) x - selectedPoint.getIntrinsicWidth() / 2,
							(int) y - selectedPoint.getIntrinsicHeight() / 2,
							(int) x + selectedPoint.getIntrinsicWidth() / 2,
							(int) y + selectedPoint.getIntrinsicHeight() / 2);
					selectedPoint.draw(canvas);
				}
				canvas.rotate(tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
			}
		}

	}

	private boolean isPlanRouteGraphsAvailable() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			MeasurementToolFragment fragment = mapActivity.getMeasurementToolFragment();
			if (fragment != null) {
				return fragment.hasVisibleGraph();
			}
		}
		return false;
	}

	@Override
	protected void updateAttrs(DrawSettings settings, RotatedTileBox tileBox) {
		boolean updatePaints = attrs.updatePaints(view.getApplication(), settings, tileBox);
		attrs.isPaint3 = false;
		attrs.isPaint2 = false;
		attrsPT.updatePaints(view.getApplication(), settings, tileBox);
		attrsPT.isPaint3 = false;
		attrsPT.isPaint2 = false;
		attrsW.updatePaints(view.getApplication(), settings, tileBox);
		attrsPT.isPaint3 = false;
		attrsPT.isPaint2 = false;

		nightMode = settings != null && settings.isNightMode();

		updateIsPaint_1(updatePaints);

		if (updatePaints) {
			routeWayContext.updatePaints(nightMode, attrs);
			publicTransportWayContext.updatePaints(nightMode, attrs, attrsPT, attrsW);
		}
	}

	@Override
	protected void updateTurnArrowColor() {
		if (routeColoringType.isGradient() && isColoringAvailable(routeColoringType, null)) {
			customTurnArrowColor = Color.WHITE;
		} else {
			customTurnArrowColor = attrs.paint3.getColor();
		}
		paintIconAction.setColorFilter(new PorterDuffColorFilter(customTurnArrowColor, PorterDuff.Mode.MULTIPLY));
	}

	@Override
	protected void updateIsPaint_1(boolean updatePaints) {
		if (updatePaints) {
			attrsIsPaint_1 = attrs.isPaint_1;
		}
		if (attrsIsPaint_1 != null) {
			attrs.isPaint_1 = attrsIsPaint_1 && (routeColoringType.isDefault()
					|| !isColoringAvailable(routeColoringType, routeInfoAttribute));
		}
	}

	private void drawXAxisPoints(Canvas canvas, RotatedTileBox tileBox) {
		QuadRect latLonBounds = tileBox.getLatLonBounds();
		List<LatLon> xAxisPoints = trackChartPoints.getXAxisPoints();
		if (xAxisPoints != null) {
			float r = 3 * tileBox.getDensity();
			float density = (float) Math.ceil(tileBox.getDensity());
			float outerRadius = r + 2 * density;
			float innerRadius = r + density;
			QuadRect prevPointRect = null;
			for (int i = 0; i < xAxisPoints.size(); i++) {
				LatLon axisPoint = xAxisPoints.get(i);
				if (axisPoint.getLatitude() >= latLonBounds.bottom
						&& axisPoint.getLatitude() <= latLonBounds.top
						&& axisPoint.getLongitude() >= latLonBounds.left
						&& axisPoint.getLongitude() <= latLonBounds.right) {
					float x = tileBox.getPixXFromLatLon(axisPoint.getLatitude(), axisPoint.getLongitude());
					float y = tileBox.getPixYFromLatLon(axisPoint.getLatitude(), axisPoint.getLongitude());
					QuadRect pointRect = new QuadRect(x - outerRadius, y - outerRadius, x + outerRadius, y + outerRadius);
					if (prevPointRect == null || !QuadRect.intersects(prevPointRect, pointRect)) {
						canvas.drawCircle(x, y, outerRadius, paintGridOuterCircle);
						canvas.drawCircle(x, y, innerRadius, paintGridCircle);
						prevPointRect = pointRect;
					}
				}
			}
		}
	}

	private void drawAction(RotatedTileBox tb, Canvas canvas, List<Location> actionPoints) {
		if (actionPoints.size() > 0) {
			canvas.rotate(-tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
			try {
				Path pth = new Path();
				Matrix matrix = new Matrix();
				boolean first = true;
				int x = 0, px = 0, py = 0, y = 0;
				for (int i = 0; i < actionPoints.size(); i++) {
					Location o = actionPoints.get(i);
					if (o == null) {
						first = true;
						int defaultTurnArrowColor = attrs.paint3.getColor();
						if (customTurnArrowColor != 0) {
							attrs.paint3.setColor(customTurnArrowColor);
						}
						canvas.drawPath(pth, attrs.paint3);
						drawTurnArrow(canvas, matrix, x, y, px, py);
						attrs.paint3.setColor(defaultTurnArrowColor);
					} else {
						px = x;
						py = y;
						x = (int) tb.getPixXFromLatLon(o.getLatitude(), o.getLongitude());
						y = (int) tb.getPixYFromLatLon(o.getLatitude(), o.getLongitude());
						if (first) {
							pth.reset();
							pth.moveTo(x, y);
							first = false;
						} else {
							pth.lineTo(x, y);
						}
					}
				}

			} finally {
				canvas.rotate(tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
			}
		}
	}

	private void drawProjectionPoint(Canvas canvas, double[] projectionXY) {
		if (projectionIcon == null) {
			helper.getSettings().getApplicationMode().getLocationIcon();
			projectionIcon = (LayerDrawable) AppCompatResources.getDrawable(view.getContext(), LocationIcon.DEFAULT.getIconId());
		}
		if (projectionIcon != null) {
			int locationX = (int) projectionXY[0];
			int locationY = (int) projectionXY[1];
			drawIcon(canvas, projectionIcon, locationX, locationY);
		}
	}

	public void drawLocations(RotatedTileBox tb, Canvas canvas, double topLatitude, double leftLongitude, double bottomLatitude, double rightLongitude) {
		if (helper.isPublicTransportMode()) {
			int currentRoute = transportHelper.getCurrentRoute();
			List<TransportRouteResult> routes = transportHelper.getRoutes();
			TransportRouteResult route = routes != null && routes.size() > currentRoute ? routes.get(currentRoute) : null;
			routeGeometry.clearRoute();
			publicTransportRouteGeometry.updateRoute(tb, route);
			if (route != null) {
				LatLon start = transportHelper.getStartLocation();
				Location startLocation = new Location("transport");
				startLocation.setLatitude(start.getLatitude());
				startLocation.setLongitude(start.getLongitude());
				publicTransportRouteGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						startLocation, 0);
			}
		} else {
			RouteCalculationResult route = helper.getRoute();
			boolean directTo = route.getRouteService() == RouteService.DIRECT_TO;
			boolean straight = route.getRouteService() == RouteService.STRAIGHT;
			publicTransportRouteGeometry.clearRoute();

			ColoringType actualColoringType = isColoringAvailable(routeColoringType, routeInfoAttribute) ?
							routeColoringType : ColoringType.DEFAULT;
			routeGeometry.setStyleParams(getRouteLineColor(), getRouteLineWidth(tb),
					directionArrowsColor, actualColoringType, routeInfoAttribute);
			routeGeometry.updateRoute(tb, route);

			if (directTo) {
				routeGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						null, 0);
			} else if (straight) {
				routeGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						helper.getLastFixedLocation(), route.getCurrentStraightAngleRoute());
			} else {
				routeGeometry.drawSegments(tb, canvas, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
						helper.getLastProjection(), route.getCurrentStraightAngleRoute());
			}
			List<RouteDirectionInfo> rd = helper.getRouteDirections();
			Iterator<RouteDirectionInfo> it = rd.iterator();
			if (!directTo && tb.getZoom() >= 14) {
				List<Location> actionPoints = calculateActionPoints(topLatitude, leftLongitude, bottomLatitude, rightLongitude, helper.getLastProjection(),
						helper.getRoute().getRouteLocations(), helper.getRoute().getCurrentRoute(), it, tb.getZoom());
				drawAction(tb, canvas, actionPoints);
			}
			if (directTo) {
				//add projection point on original route
				double[] projectionOnRoute = calculateProjectionOnRoutePoint(
						helper.getRoute().getImmutableAllLocations(), helper, tb);
				if (projectionOnRoute != null) {
					drawProjectionPoint(canvas, projectionOnRoute);
				}
			}
		}
	}
	
	private double[] calculateProjectionOnRoutePoint(List<Location> routeNodes, RoutingHelper helper, RotatedTileBox box) {
		double[] projectionXY = null;
		Location ll = helper.getLastFixedLocation();
		RouteCalculationResult route = helper.getRoute();
		List<Location> locs = route.getImmutableAllLocations();
		int cr = route.getCurrentRoute();
		int locIndex = locs.size() - 1;
		if (route.getIntermediatePointsToPass() > 0) {
			locIndex = route.getIndexOfIntermediate(route.getIntermediatePointsToPass() - 1);
		}
		if (ll != null && cr > 0 && cr < locs.size() && locIndex >= 0 && locIndex < locs.size()) {
			Location loc1 = locs.get(cr - 1);
			Location loc2 = locs.get(cr);
			double distLeft = route.getDistanceFromPoint(cr) - route.getDistanceFromPoint(locIndex);
			double baDist = route.getDistanceFromPoint(cr - 1) - route.getDistanceFromPoint(cr);
			Location target = locs.get(locIndex);
			double dTarget = ll.distanceTo(target);
			final int aX = box.getPixXFromLonNoRot(loc1.getLongitude());
			final int aY = box.getPixYFromLatNoRot(loc1.getLatitude());
			final int bX = box.getPixXFromLonNoRot(loc2.getLongitude());
			final int bY = box.getPixYFromLatNoRot(loc2.getLatitude());
			if (baDist != 0) {
				double CF = (dTarget - distLeft) / baDist;
				double rX = bX - CF * (bX - aX);
				double rY = bY - CF * (bY - aY);
				projectionXY = new double[] {rX, rY};
			}
		}
		if (projectionXY != null) {

			double distanceLoc2Proj = MapUtils.getSqrtDistance((int)projectionXY[0], (int) projectionXY[1],
					box.getPixXFromLonNoRot(ll.getLongitude()), box.getPixYFromLatNoRot(ll.getLatitude()));
			boolean visible = box.containsPoint((float) projectionXY[0], (float) projectionXY[1], 20.0f)
					&& distanceLoc2Proj > AndroidUtils.dpToPx(view.getContext(), 52) / 2.0;
			if (visible) {
				return projectionXY;
			}
		}
		return null;
	}

	private List<Location> calculateActionPoints(double topLatitude, double leftLongitude, double bottomLatitude,
			double rightLongitude, Location lastProjection, List<Location> routeNodes, int cd,
			Iterator<RouteDirectionInfo> it, int zoom) {
		RouteDirectionInfo nf = null;
		
		double DISTANCE_ACTION = 35;
		if(zoom >= 17) {
			DISTANCE_ACTION = 15;
		} else if (zoom == 15) {
			DISTANCE_ACTION = 70;
		} else if (zoom < 15) {
			DISTANCE_ACTION = 110;
		}
		double actionDist = 0;
		Location previousAction = null; 
		List<Location> actionPoints = this.actionPoints;
		actionPoints.clear();
		int prevFinishPoint = -1;
		for (int routePoint = 0; routePoint < routeNodes.size(); routePoint++) {
			Location loc = routeNodes.get(routePoint);
			if(nf != null) {
				int pnt = nf.routeEndPointOffset == 0 ? nf.routePointOffset : nf.routeEndPointOffset;
				if(pnt < routePoint + cd ) {
					nf = null;
				}
			}
			while (nf == null && it.hasNext()) {
				nf = it.next();
				int pnt = nf.routeEndPointOffset == 0 ? nf.routePointOffset : nf.routeEndPointOffset;
				if (pnt < routePoint + cd) {
					nf = null;
				}
			}
			boolean action = nf != null && (nf.routePointOffset == routePoint + cd ||
					(nf.routePointOffset <= routePoint + cd && routePoint + cd  <= nf.routeEndPointOffset));
			if(!action && previousAction == null) {
				// no need to check
				continue;
			}
			boolean visible = leftLongitude <= loc.getLongitude() && loc.getLongitude() <= rightLongitude && bottomLatitude <= loc.getLatitude()
					&& loc.getLatitude() <= topLatitude;
			if(action && !visible && previousAction == null) {
				continue;
			}
			if (!action) {
				// previousAction != null
				float dist = loc.distanceTo(previousAction);
				actionDist += dist;
				if (actionDist >= DISTANCE_ACTION) {
					actionPoints.add(calculateProjection(1 - (actionDist - DISTANCE_ACTION) / dist, previousAction, loc));
					actionPoints.add(null);
					prevFinishPoint = routePoint;
					previousAction = null;
					actionDist = 0;
				} else {
					actionPoints.add(loc);
					previousAction = loc;
				}
			} else {
				// action point
				if (previousAction == null) {
					addPreviousToActionPoints(actionPoints, lastProjection, routeNodes, DISTANCE_ACTION,
							prevFinishPoint, routePoint, loc);
				}
				actionPoints.add(loc);
				previousAction = loc;
				prevFinishPoint = -1;
				actionDist = 0;
			}
		}
		if(previousAction != null) {
			actionPoints.add(null);
		}
		return actionPoints;
	}


	private void addPreviousToActionPoints(List<Location> actionPoints, Location lastProjection, List<Location> routeNodes, double DISTANCE_ACTION,
			int prevFinishPoint, int routePoint, Location loc) {
		// put some points in front
		int ind = actionPoints.size();
		Location lprevious = loc;
		double dist = 0;
		for (int k = routePoint - 1; k >= -1; k--) {
			Location l = k == -1 ? lastProjection : routeNodes.get(k);
			float locDist = lprevious.distanceTo(l);
			dist += locDist;
			if (dist >= DISTANCE_ACTION) {
				if (locDist > 1) {
					actionPoints.add(ind,
							calculateProjection(1 - (dist - DISTANCE_ACTION) / locDist, lprevious, l));
				}
				break;
			} else {
				actionPoints.add(ind, l);
				lprevious = l;
			}
			if (prevFinishPoint == k) {
				if (ind >= 2) {
					actionPoints.remove(ind - 2);
					actionPoints.remove(ind - 2);
				}
				break;
			}
		}
	}

	private Location calculateProjection(double part, Location lp, Location l) {
		Location p = new Location(l);
		p.setLatitude(lp.getLatitude() + part * (l.getLatitude() - lp.getLatitude()));
		p.setLongitude(lp.getLongitude() + part * (l.getLongitude() - lp.getLongitude()));
		return p;
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public boolean onLongPressEvent(PointF point, RotatedTileBox tileBox) {
		return false;
	}

	@Override
	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		return false;
	}

	private int getRadiusPoi(RotatedTileBox tb) {
		final double zoom = tb.getZoom();
		int r;
		if (zoom <= 15) {
			r = 8;
		} else if (zoom <= 16) {
			r = 10;
		} else if (zoom <= 17) {
			r = 14;
		} else {
			r = 18;
		}
		return (int) (r * tb.getDensity());
	}

	@Nullable
	private List<TransportStop> getRouteTransportStops() {
		return helper.isPublicTransportMode() ? publicTransportRouteGeometry.getDrawer().getRouteTransportStops() : null;
	}

	private void getFromPoint(RotatedTileBox tb, PointF point, List<? super TransportStop> res, @NonNull List<TransportStop> routeTransportStops) {
		int ex = (int) point.x;
		int ey = (int) point.y;
		final int rp = getRadiusPoi(tb);
		int radius = rp * 3 / 2;
		try {
			for (int i = 0; i < routeTransportStops.size(); i++) {
				TransportStop n = routeTransportStops.get(i);
				if (n.getLocation() == null) {
					continue;
				}
				int x = (int) tb.getPixXFromLatLon(n.getLocation().getLatitude(), n.getLocation().getLongitude());
				int y = (int) tb.getPixYFromLatLon(n.getLocation().getLatitude(), n.getLocation().getLongitude());
				if (Math.abs(x - ex) <= radius && Math.abs(y - ey) <= radius) {
					radius = rp;
					res.add(n);
				}
			}
		} catch (IndexOutOfBoundsException e) {
			// ignore
		}
	}

	private boolean isColoringAvailable(@NonNull ColoringType routeColoringType,
	                                    @Nullable String routeInfoAttribute) {
		return coloringAvailabilityCache
				.isColoringAvailable(helper.getRoute(), routeColoringType, routeInfoAttribute);
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> res, boolean unknownLocation) {
		List<TransportStop> routeTransportStops = getRouteTransportStops();
		if (!Algorithms.isEmpty(routeTransportStops)) {
			getFromPoint(tileBox, point, res, routeTransportStops);
		}
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		if (o instanceof TransportStop){
			return ((TransportStop)o).getLocation();
		}
		return null;
	}

	@Override
	public PointDescription getObjectName(Object o) {
		if (o instanceof TransportStop){
			return new PointDescription(PointDescription.POINT_TYPE_TRANSPORT_STOP, view.getContext().getString(R.string.transport_Stop),
					((TransportStop)o).getName());
		}
		return null;
	}

	@Override
	public boolean disableSingleTap() {
		return isPreviewRouteLineVisible();
	}

	@Override
	public boolean disableLongPressOnMap(PointF point, RotatedTileBox tileBox) {
		return isPreviewRouteLineVisible();
	}

	@Override
	public boolean isObjectClickable(Object o) {
		return false;
	}

	@Override
	public boolean runExclusiveAction(@Nullable Object o, boolean unknownLocation) {
		return false;
	}

	@Override
	public boolean showMenuAction(@Nullable Object o) {
		return false;
	}
}