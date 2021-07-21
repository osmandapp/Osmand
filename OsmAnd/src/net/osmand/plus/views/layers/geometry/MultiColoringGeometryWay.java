package net.osmand.plus.views.layers.geometry;

import android.graphics.Bitmap;
import android.graphics.Color;

import net.osmand.GPXUtilities.GPXFile;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.routing.ColoringType;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteColorize;
import net.osmand.router.RouteColorize.ColorizationType;
import net.osmand.router.RouteColorize.RouteColorizationPoint;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RouteStatisticsHelper.RouteSegmentAttribute;
import net.osmand.router.RouteStatisticsHelper.RouteStatisticComputer;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import gnu.trove.list.array.TByteArrayList;

public abstract class MultiColoringGeometryWay
		<C extends MultiColoringGeometryWayContext, D extends MultiColoringGeometryWayDrawer<C>> extends
		GeometryWay<C, D> {

	protected int customColor;
	protected float customWidth;
	protected Integer customDirectionArrowColor;
	@NonNull
	protected ColoringType coloringType;
	protected String routeInfoAttribute;

	protected boolean coloringChanged;

	public MultiColoringGeometryWay(C context, D drawer) {
		super(context, drawer);
		coloringType = context.getDefaultColoringType();
	}

	public void setStyleParams(int color,
	                           float width,
	                           @Nullable @ColorInt Integer directionArrowColor,
	                           @NonNull ColoringType routeColoringType,
	                           @Nullable String routeInfoAttribute) {
		this.coloringChanged = this.coloringType != routeColoringType
				|| routeColoringType == ColoringType.ATTRIBUTE
				&& !Algorithms.objectEquals(this.routeInfoAttribute, routeInfoAttribute);

		boolean widthChanged = !Algorithms.objectEquals(customWidth, width);
		if (widthChanged) {
			updateStylesWidth(width);
		}
		updatePaints(width, routeColoringType);
		getDrawer().setColoringType(routeColoringType);

		this.customColor = color;
		this.customWidth = width;
		this.customDirectionArrowColor = directionArrowColor;
		this.coloringType = routeColoringType;
		this.routeInfoAttribute = routeInfoAttribute;
	}

	private void updateStylesWidth(@Nullable Float newWidth) {
		for (GeometryWayStyle<?> style : styleMap.values()) {
			style.width = newWidth;
		}
	}

	protected void updatePaints(@Nullable Float width, @NonNull ColoringType routeColoringType) {
		if (width != null) {
			getContext().updateBorderWidth(width);
		}
	}

	protected void updateGradientRoute(RotatedTileBox tb, List<Location> locations) {
		GPXFile gpxFile = GpxUiHelper.makeGpxFromLocations(locations, getContext().getApp());
		ColorizationType colorizationType = coloringType.toGradientScaleType().toColorizationType();
		RouteColorize routeColorize = new RouteColorize(tb.getZoom(), gpxFile, null, colorizationType, 0);
		List<RouteColorizationPoint> points = routeColorize.getResult(false);

		updateWay(new GradientGeometryWayProvider(routeColorize, points), createGradientStyles(points), tb);
	}

	private Map<Integer, GeometryWayStyle<?>> createGradientStyles(List<RouteColorizationPoint> points) {
		Map<Integer, GeometryWayStyle<?>> styleMap = new TreeMap<>();
		for (int i = 0; i < points.size() - 1; i++) {
			GeometryGradientWayStyle style = getGradientWayStyle();
			style.currColor = points.get(i).color;
			style.nextColor = points.get(i + 1).color;
			styleMap.put(i, style);
		}
		return styleMap;
	}

	protected void updateSolidMultiColorRoute(RotatedTileBox tileBox, List<Location> locations,
	                                          List<RouteSegmentResult> routeSegments) {
		List<Integer> colors = getRouteInfoAttributesColors(locations, routeSegments);
		if (Algorithms.isEmpty(colors)) {
			updateWay(Collections.emptyList(), Collections.emptyMap(), tileBox);
			return;
		}

		Map<Integer, GeometryWayStyle<?>> styleMap = new TreeMap<>();

		for (int i = 0; i < colors.size(); ) {
			int color = colors.get(i);
			GeometrySolidWayStyle<?> style = getSolidWayStyle(color);
			styleMap.put(i, style);

			i++;
			while (i < colors.size() && colors.get(i) == color) {
				i++;
			}
		}

		updateWay(locations, styleMap, tileBox);
	}

	private List<Integer> getRouteInfoAttributesColors(List<Location> locations, List<RouteSegmentResult> routeSegments) {
		if (Algorithms.isEmpty(routeSegments)) {
			return Collections.emptyList();
		}

		int firstSegmentLocationIdx = getIdxOfFirstSegmentLocation(locations, routeSegments);

		RouteStatisticComputer statisticComputer = createRouteStatisticsComputer();
		List<Integer> colors = new ArrayList<>(locations.size());
		for (int i = 0; i < routeSegments.size(); i++) {
			RouteSegmentResult segment = routeSegments.get(i);
			RouteSegmentAttribute attribute =
					statisticComputer.classifySegment(routeInfoAttribute, -1, segment.getObject());
			int color = attribute.getColor();

			if (i == 0) {
				for (int j = 0; j < firstSegmentLocationIdx; j++) {
					colors.add(color);
				}
			}

			int pointsSize = Math.abs(segment.getStartPointIndex() - segment.getEndPointIndex());
			for (int j = 0; j < pointsSize; j++) {
				colors.add(color);
			}

			if (i == routeSegments.size() - 1) {
				int start = colors.size();
				for (int j = start; j < locations.size(); j++) {
					colors.add(color);
				}
			}
		}

		return colors;
	}

	protected int getIdxOfFirstSegmentLocation(List<Location> locations, List<RouteSegmentResult> routeSegments) {
		int locationsIdx = 0;
		LatLon segmentStartPoint = routeSegments.get(0).getStartPoint();
		while (locationsIdx < locations.size()) {
			Location location = locations.get(locationsIdx);
			if (location.getLatitude() == segmentStartPoint.getLatitude()
					&& location.getLongitude() == segmentStartPoint.getLongitude()) {
				break;
			}
			locationsIdx++;
		}
		return locationsIdx == locations.size() ? 0 : locationsIdx;
	}

	private RouteStatisticComputer createRouteStatisticsComputer() {
		OsmandApplication app = getContext().getApp();
		boolean night = app.getDaynightHelper().isNightModeForMapControls();
		RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
		RenderingRulesStorage defaultRenderer = app.getRendererRegistry().defaultRender();
		MapRenderRepositories maps = app.getResourceManager().getRenderer();
		RenderingRuleSearchRequest currentSearchRequest =
				maps.getSearchRequestWithAppliedCustomRules(currentRenderer, night);
		RenderingRuleSearchRequest defaultSearchRequest =
				maps.getSearchRequestWithAppliedCustomRules(defaultRenderer, night);

		return new RouteStatisticComputer(currentRenderer, defaultRenderer,
				currentSearchRequest, defaultSearchRequest);
	}

	@Override
	protected void addLocation(RotatedTileBox tb, int locationIdx, double dist, GeometryWayStyle<?> style,
	                           List<Float> tx, List<Float> ty, List<Double> angles,
	                           List<Double> distances, List<GeometryWayStyle<?>> styles) {
		super.addLocation(tb, locationIdx, dist, style, tx, ty, angles, distances, styles);
		if (style instanceof GeometryGradientWayStyle && styles.size() > 1) {
			GeometryGradientWayStyle prevStyle = (GeometryGradientWayStyle) styles.get(styles.size() - 2);
			GeometryGradientWayStyle currStyle = (GeometryGradientWayStyle) style;
			prevStyle.nextColor = currStyle.currColor;
		}
	}

	@Override
	protected boolean addInitialPoint(RotatedTileBox tb, double topLatitude, double leftLongitude,
	                                  double bottomLatitude, double rightLongitude, GeometryWayStyle<?> style,
	                                  boolean previousVisible, Location lastPoint, int startLocationIndex) {
		previousVisible = super.addInitialPoint(tb, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
				style, previousVisible, lastPoint, startLocationIndex);
		if (style instanceof GeometryGradientWayStyle) {
			GeometryGradientWayStyle gradientWayStyle = (GeometryGradientWayStyle) style;
			GradientGeometryWayProvider locationProvider = getGradientLocationProvider();
			if (startLocationIndex == 0) {
				int startColor = locationProvider.getColor(0);
				gradientWayStyle.currColor = startColor;
				gradientWayStyle.nextColor = startColor;
			} else {
				double currLat = lastPoint.getLatitude();
				double currLon = lastPoint.getLongitude();
				double prevLat = locationProvider.getLatitude(startLocationIndex - 1);
				double prevLon = locationProvider.getLongitude(startLocationIndex - 1);
				double nextLat = locationProvider.getLatitude(startLocationIndex);
				double nextLon = locationProvider.getLongitude(startLocationIndex);
				double percent = MapUtils.getProjectionCoeff(currLat, currLon, prevLat, prevLon, nextLat, nextLon);
				int prevColor = locationProvider.getColor(startLocationIndex - 1);
				int nextColor = locationProvider.getColor(startLocationIndex);
				gradientWayStyle.currColor = RouteColorize.getIntermediateColor(prevColor, nextColor, percent);
				gradientWayStyle.nextColor = nextColor;
			}
		} else if (coloringType.isRouteInfoAttribute() && style instanceof GeometrySolidWayStyle) {
			GeometrySolidWayStyle<?> prevStyle = (GeometrySolidWayStyle<?>) style;
			GeometrySolidWayStyle<?> transparentWayStyle = getSolidWayStyle(Color.TRANSPARENT);
			int prevStyleIdx = startLocationIndex > 0 ? startLocationIndex - 1 : 0;
			prevStyle.color = getStyle(prevStyleIdx, transparentWayStyle).color;
		}
		return previousVisible;
	}

	@Override
	protected boolean shouldSkipLocation(TByteArrayList simplification, Map<Integer,
	                                     GeometryWayStyle<?>> styleMap, int locationIdx) {
		return coloringType.isGradient()
				? simplification.getQuick(locationIdx) == 0
				: super.shouldSkipLocation(simplification, styleMap, locationIdx);
	}

	@NonNull
	@Override
	public GeometryWayStyle<?> getDefaultWayStyle() {
		if (coloringType.isGradient()) {
			return new GeometryGradientWayStyle(getContext(), customColor, customWidth);
		}
		return new GeometrySolidWayStyle<>(getContext(), customColor, customWidth, customDirectionArrowColor);
	}

	@NonNull
	public GeometrySolidWayStyle<C> getSolidWayStyle(int lineColor) {
		return new GeometrySolidWayStyle<>(getContext(), lineColor, customWidth, customDirectionArrowColor);
	}

	@NonNull
	public GeometryGradientWayStyle getGradientWayStyle() {
		return new GeometryGradientWayStyle(getContext(), customColor, customWidth);
	}

	private GradientGeometryWayProvider getGradientLocationProvider() {
		return (GradientGeometryWayProvider) getLocationProvider();
	}

	private static class GradientGeometryWayProvider implements GeometryWayProvider {

		private final RouteColorize routeColorize;
		private final List<RouteColorizationPoint> locations;

		public GradientGeometryWayProvider(RouteColorize routeColorize, List<RouteColorizationPoint> locations) {
			this.routeColorize = routeColorize;
			this.locations = locations;
		}

		public List<RouteColorizationPoint> simplify(int zoom) {
			routeColorize.setZoom(zoom);
			return routeColorize.simplify();
		}

		public int getColor(int index) {
			return locations.get(index).color;
		}

		@Override
		public double getLatitude(int index) {
			return locations.get(index).lat;
		}

		@Override
		public double getLongitude(int index) {
			return locations.get(index).lon;
		}

		@Override
		public int getSize() {
			return locations.size();
		}
	}

	protected static class GradientPathGeometryZoom extends PathGeometryZoom {

		public GradientPathGeometryZoom(GeometryWayProvider locationProvider, RotatedTileBox tb, boolean simplify) {
			super(locationProvider, tb, simplify);
		}

		@Override
		protected void simplify(RotatedTileBox tb, GeometryWayProvider locationProvider, TByteArrayList simplifyPoints) {
			if (locationProvider instanceof GradientGeometryWayProvider) {
				GradientGeometryWayProvider provider = (GradientGeometryWayProvider) locationProvider;
				List<RouteColorizationPoint> simplified = provider.simplify(tb.getZoom());
				for (RouteColorizationPoint location : simplified) {
					simplifyPoints.set(location.id, (byte) 1);
				}
			}
		}
	}

	public static class GeometryGradientWayStyle extends GeometryWayStyle<MultiColoringGeometryWayContext> {

		public int currColor;
		public int nextColor;

		public GeometryGradientWayStyle(MultiColoringGeometryWayContext context, Integer color, Float width) {
			super(context, color, width);
		}

		@Override
		public Bitmap getPointBitmap() {
			return getContext().getArrowBitmap();
		}

		@Override
		public boolean isUnique() {
			return true;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!super.equals(other)) {
				return false;
			}
			if (!(other instanceof GeometryGradientWayStyle)) {
				return false;
			}
			GeometryGradientWayStyle o = (GeometryGradientWayStyle) other;
			return currColor == o.currColor && nextColor == o.nextColor;
		}
	}

	public static class GeometrySolidWayStyle<T extends MultiColoringGeometryWayContext> extends GeometryWayStyle<T> {

		protected final Integer directionArrowsColor;

		GeometrySolidWayStyle(T context, Integer color, Float width, Integer directionArrowsColor) {
			super(context, color, width);
			this.directionArrowsColor = directionArrowsColor;
		}

		@Override
		public Bitmap getPointBitmap() {
			return getContext().getArrowBitmap();
		}

		@Override
		public Integer getPointColor() {
			return directionArrowsColor;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!super.equals(other)) {
				return false;
			}
			if (!(other instanceof GeometrySolidWayStyle)) {
				return false;
			}
			GeometrySolidWayStyle<?> o = (GeometrySolidWayStyle<?>) other;
			return Algorithms.objectEquals(directionArrowsColor, o.directionArrowsColor);
		}
	}
}