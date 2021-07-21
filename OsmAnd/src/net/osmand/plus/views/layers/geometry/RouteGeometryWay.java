package net.osmand.plus.views.layers.geometry;

import android.graphics.Paint;

import net.osmand.Location;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RoutingHelper;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RouteGeometryWay extends
		MultiColoringGeometryWay<RouteGeometryWayContext, MultiColoringGeometryWayDrawer<RouteGeometryWayContext>> {

	private final RoutingHelper helper;
	private RouteCalculationResult route;

	public RouteGeometryWay(RouteGeometryWayContext context) {
		super(context, new MultiColoringGeometryWayDrawer<>(context));
		this.helper = context.getApp().getRoutingHelper();
	}

	@Override
	protected void updatePaints(@Nullable Float width, @NonNull ColoringType routeColoringType) {
		super.updatePaints(width, routeColoringType);
		Paint.Cap cap = routeColoringType.isGradient() || routeColoringType.isRouteInfoAttribute() ?
				Paint.Cap.ROUND : getContext().getAttrs().paint.getStrokeCap();
		getContext().getAttrs().customColorPaint.setStrokeCap(cap);
	}

	public void updateRoute(@NonNull RotatedTileBox tb, @NonNull RouteCalculationResult route) {
		if (coloringChanged || tb.getMapDensity() != getMapDensity() || this.route != route) {
			this.route = route;
			coloringChanged = false;
			List<Location> locations = route.getImmutableAllLocations();

			if (coloringType.isGradient()) {
				updateGradientRoute(tb, locations);
			} else if (coloringType.isRouteInfoAttribute()) {
				updateSolidMultiColorRoute(tb, locations, route.getOriginalRoute());
			} else {
				updateWay(locations, tb);
			}
		}
	}

	@Override
	protected PathGeometryZoom getGeometryZoom(RotatedTileBox tb) {
		if (coloringType.isGradient()) {
			int zoom = tb.getZoom();
			PathGeometryZoom zm = zooms.get(zoom);
			if (zm == null) {
				zm = new GradientPathGeometryZoom(getLocationProvider(), tb, true);
				zooms.put(zoom, zm);
			}
			return zm;
		}
		return super.getGeometryZoom(tb);
	}

	@Override
	public Location getNextVisiblePoint() {
		return helper.getRoute().getCurrentStraightAnglePoint();
	}

	public void clearRoute() {
		if (route != null) {
			route = null;
			clearWay();
		}
	}
}