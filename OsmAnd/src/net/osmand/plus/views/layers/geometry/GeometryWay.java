package net.osmand.plus.views.layers.geometry;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.views.layers.geometry.GeometryWayDrawer.DrawPathData;
import net.osmand.util.Algorithms;
import net.osmand.util.MapAlgorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gnu.trove.list.array.TByteArrayList;

public abstract class GeometryWay<T extends GeometryWayContext, D extends GeometryWayDrawer<T>> {
	private double mapDensity;
	private final T context;
	private final D drawer;
	private GeometryWayProvider locationProvider;
	protected Map<Integer, GeometryWayStyle<?>> styleMap = Collections.emptyMap();
	protected TreeMap<Integer, PathGeometryZoom> zooms = new TreeMap<>();

	// cache arrays
	private List<Float> tx = new ArrayList<>();
	private List<Float> ty = new ArrayList<>();
	private List<Double> angles = new ArrayList<>();
	private List<Double> distances = new ArrayList<>();
	private List<GeometryWayStyle<?>> styles = new ArrayList<>();

	public interface GeometryWayProvider {
		double getLatitude(int index);

		double getLongitude(int index);

		int getSize();
	}

	private static class GeometryWayLocationProvider implements GeometryWayProvider {
		private final List<Location> locations;

		public GeometryWayLocationProvider(@NonNull List<Location> locations) {
			this.locations = locations;
		}

		@Override
		public double getLatitude(int index) {
			return locations.get(index).getLatitude();
		}

		@Override
		public double getLongitude(int index) {
			return locations.get(index).getLongitude();
		}

		@Override
		public int getSize() {
			return locations.size();
		}
	}

	public GeometryWay(T context, D drawer) {
		this.context = context;
		this.drawer = drawer;
	}

	protected GeometryWayStyle<?> getStyle(int index, GeometryWayStyle<?> defaultWayStyle) {
		List<Integer> list = new ArrayList<>(styleMap.keySet());
		for (int i = list.size() - 1; i >= 0; i--) {
			int c = list.get(i);
			if (c <= index) {
				return styleMap.get(c);
			}
		}
		return defaultWayStyle;
	}

	public T getContext() {
		return context;
	}

	public D getDrawer() {
		return drawer;
	}

	public double getMapDensity() {
		return mapDensity;
	}

	@Nullable
	public GeometryWayProvider getLocationProvider() {
		return locationProvider;
	}

	protected void updateWay(@NonNull GeometryWayProvider locationProvider, @NonNull RotatedTileBox tb) {
		updateWay(locationProvider, null, tb);
	}

	protected void updateWay(@NonNull GeometryWayProvider locationProvider, @Nullable Map<Integer, GeometryWayStyle<?>> styleMap, @NonNull RotatedTileBox tb) {
		this.locationProvider = locationProvider;
		this.styleMap = styleMap == null ? Collections.<Integer, GeometryWayStyle<?>>emptyMap() : styleMap;
		this.mapDensity = tb.getMapDensity();
		this.zooms = new TreeMap<>();
	}

	public void updateWay(@NonNull List<Location> locations, @NonNull RotatedTileBox tb) {
		updateWay(locations, null, tb);
	}

	public void updateWay(@NonNull List<Location> locations, @Nullable Map<Integer, GeometryWayStyle<?>> styleMap, @NonNull RotatedTileBox tb) {
		this.locationProvider = new GeometryWayLocationProvider(locations);
		this.styleMap = styleMap == null ? Collections.<Integer, GeometryWayStyle<?>>emptyMap() : styleMap;
		this.mapDensity = tb.getMapDensity();
		this.zooms = new TreeMap<>();
	}

	public void clearWay() {
		this.locationProvider = null;
		this.styleMap = Collections.emptyMap();
		this.zooms = new TreeMap<>();
	}

	protected PathGeometryZoom getGeometryZoom(RotatedTileBox tb) {
		int zoom = tb.getZoom();
		PathGeometryZoom zm = zooms.size() > zoom ? zooms.get(zoom) : null;
		if (zm == null) {
			zm = new PathGeometryZoom(locationProvider, tb, tb.getZoom() < context.getSimplificationZoom());
			zooms.put(zoom, zm);
		}
		return zm;
	}

	@NonNull
	public abstract GeometryWayStyle<?> getDefaultWayStyle();

	public Location getNextVisiblePoint() {
		return null;
	}

	public void drawSegments(RotatedTileBox tb, Canvas canvas, double topLatitude, double leftLongitude,
							 double bottomLatitude, double rightLongitude, Location lastProjection, int startLocationIndex) {
		if (locationProvider == null || locationProvider.getSize() == 0) {
			return;
		}
		PathGeometryZoom geometryZoom = getGeometryZoom(tb);
		TByteArrayList simplification = geometryZoom.getSimplifyPoints();
		List<Double> odistances = geometryZoom.getDistances();

		clearArrays();

		GeometryWayStyle<?> defaultWayStyle = getDefaultWayStyle();
		GeometryWayStyle<?> style = defaultWayStyle;
		boolean previousVisible = false;
		if (lastProjection != null) {
			previousVisible = addInitialPoint(tb, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
					style, previousVisible, lastProjection, startLocationIndex);
		}
		Location nextVisiblePoint = getNextVisiblePoint();
		if (nextVisiblePoint != null) {
			previousVisible = addInitialPoint(tb, topLatitude, leftLongitude, bottomLatitude, rightLongitude,
					style, previousVisible, nextVisiblePoint, startLocationIndex);
		}
		GeometryWayProvider locationProvider = this.locationProvider;
		int previous = -1;
		for (int i = startLocationIndex; i < locationProvider.getSize(); i++) {
			style = getStyle(i, defaultWayStyle);
			if (shouldSkipLocation(simplification, styleMap, i)) {
				continue;
			}
			if (shouldAddLocation(simplification, leftLongitude, rightLongitude, bottomLatitude, topLatitude,
					locationProvider, i)) {
				double dist = previous == -1 ? 0 : odistances.get(i);
				if (!previousVisible) {
					if (previous != -1) {
						addLocation(tb, previous, dist, style, tx, ty, angles, distances, styles);
					} else if (lastProjection != null) {
						addLocation(tb, lastProjection.getLatitude(), lastProjection.getLongitude(),
								dist, getStyle(i - 1, style), tx, ty, angles, distances, styles); // first point
					}
				}
				addLocation(tb, i, dist, style, tx, ty, angles, distances, styles);
				previousVisible = true;
			} else if (previousVisible) {
				addLocation(tb, i, previous == -1 ? 0 : odistances.get(i), style, tx, ty, angles, distances, styles);
				double distToFinish = 0;
				for (int ki = i + 1; ki < odistances.size(); ki++) {
					distToFinish += odistances.get(ki);
				}
				drawRouteSegment(tb, canvas, tx, ty, angles, distances, distToFinish, styles);
				previousVisible = false;
				clearArrays();
			}
			previous = i;
		}
		drawRouteSegment(tb, canvas, tx, ty, angles, distances, 0, styles);
	}

	protected boolean shouldSkipLocation(TByteArrayList simplification, Map<Integer, GeometryWayStyle<?>> styleMap, int locationIdx) {
		return simplification.getQuick(locationIdx) == 0 && !styleMap.containsKey(locationIdx);
	}

	protected boolean shouldAddLocation(TByteArrayList simplification, double leftLon, double rightLon, double bottomLat,
										double topLat, GeometryWayProvider provider, int currLocationIdx) {
		double lat = provider.getLatitude(currLocationIdx);
		double lon = provider.getLongitude(currLocationIdx);
		return leftLon <= lon && lon <= rightLon && bottomLat <= lat && lat <= topLat;
	}

	protected void addLocation(RotatedTileBox tb, int locationIdx, double dist, GeometryWayStyle<?> style,
	                           List<Float> tx, List<Float> ty, List<Double> angles, List<Double> distances,
	                           List<GeometryWayStyle<?>> styles) {
		addLocation(tb, locationProvider.getLatitude(locationIdx), locationProvider.getLongitude(locationIdx),
				dist, style, tx, ty, angles, distances, styles);
	}

	protected void addLocation(RotatedTileBox tb, double latitude, double longitude, double dist,
	                           GeometryWayStyle<?> style, List<Float> tx, List<Float> ty,
	                           List<Double> angles, List<Double> distances, List<GeometryWayStyle<?>> styles) {
		float x = tb.getPixXFromLatLon(latitude, longitude);
		float y = tb.getPixYFromLatLon(latitude, longitude);
		float px = x;
		float py = y;
		int previous = tx.size() - 1;
		if (previous >= 0) {
			px = tx.get(previous);
			py = ty.get(previous);
		}
		double angle = 0;
		if (px != x || py != y) {
			double angleRad = Math.atan2(y - py, x - px);
			angle = (angleRad * 180 / Math.PI) + 90f;
		}
		double distSegment = dist != 0 ? dist : Math.sqrt((y - py) * (y - py) + (x - px) * (x - px));
		tx.add(x);
		ty.add(y);
		angles.add(angle);
		distances.add(distSegment);
		styles.add(style);
	}

	protected boolean addInitialPoint(RotatedTileBox tb, double topLatitude, double leftLongitude, double bottomLatitude,
									  double rightLongitude, GeometryWayStyle<?> style, boolean previousVisible,
									  Location lastPoint, int startLocationIndex) {
		if (leftLongitude <= lastPoint.getLongitude() && lastPoint.getLongitude() <= rightLongitude
				&& bottomLatitude <= lastPoint.getLatitude() && lastPoint.getLatitude() <= topLatitude) {
			addLocation(tb, lastPoint.getLatitude(), lastPoint.getLongitude(), 0, style, tx, ty, angles, distances, styles);
			previousVisible = true;
		}
		return previousVisible;
	}

	private void clearArrays() {
		tx.clear();
		ty.clear();
		distances.clear();
		angles.clear();
		styles.clear();
	}

	public static boolean isIn(float x, float y, int lx, int ty, int rx, int by) {
		return x >= lx && x <= rx && y >= ty && y <= by;
	}

	public static int calculatePath(RotatedTileBox tb, List<Float> xs, List<Float> ys, Path path) {
		List<DrawPathData> pathsData = new ArrayList<>();
		int res = calculatePath(tb, xs, ys, null, pathsData);
		if (pathsData.size() > 0) {
			path.addPath(pathsData.get(0).path);
		}
		return res;
	}

	public static int calculatePath(RotatedTileBox tb, List<Float> xs, List<Float> ys,
									List<GeometryWayStyle<?>> styles, List<DrawPathData> pathsData) {
		boolean segmentStarted = false;
		float prevX = xs.get(0);
		float prevY = ys.get(0);
		int height = tb.getPixHeight();
		int width = tb.getPixWidth();
		int cnt = 0;
		boolean hasStyles = styles != null && styles.size() == xs.size();
		GeometryWayStyle<?> style = hasStyles ? styles.get(0) : null;
		Path path = new Path();
		float prevXorig = prevX;
		float prevYorig = prevY;
		float currXorig = Float.NaN;
		float currYorig = Float.NaN;
		boolean prevIn = isIn(prevX, prevY, 0, 0, width, height);
		for (int i = 1; i < xs.size(); i++) {
			float currX = xs.get(i);
			float currY = ys.get(i);
			currXorig = currX;
			currYorig = currY;
			boolean currIn = isIn(currX, currY, 0, 0, width, height);
			boolean draw = false;
			if (prevIn && currIn) {
				draw = true;
			} else {
				long intersection = MapAlgorithms.calculateIntersection((int) currX, (int) currY,
						(int) prevX, (int) prevY, 0, width, height, 0);
				if (intersection != -1) {
					if (prevIn && (i == 1)) {
						cnt++;
						path.moveTo(prevX, prevY);
						segmentStarted = true;
					}
					prevX = (int) (intersection >> 32);
					prevY = (int) (intersection & 0xffffffff);
					draw = true;
				}
				if (i == xs.size() - 1 && !currIn) {
					long inter = MapAlgorithms.calculateIntersection((int) prevX, (int) prevY,
							(int) currX, (int) currY, 0, width, height, 0);
					if (inter != -1) {
						currX = (int) (inter >> 32);
						currY = (int) (inter & 0xffffffff);
					}
				}
			}
			if (draw) {
				if (!segmentStarted) {
					cnt++;
					path.moveTo(prevX, prevY);
					segmentStarted = true;
				}
				path.lineTo(currX, currY);
			} else {
				segmentStarted = false;
			}
			prevIn = currIn;
			prevX = currX;
			prevY = currY;

			if (hasStyles) {
				GeometryWayStyle<?> newStyle = styles.get(i);
				if (!style.equals(newStyle) || newStyle.isUnique()) {
					pathsData.add(new DrawPathData(path, new PointF(prevXorig, prevYorig),
							new PointF(currXorig, currYorig), style));
					prevXorig = currXorig;
					prevYorig = currYorig;
					path = new Path();
					if (segmentStarted) {
						path.moveTo(currX, currY);
					}
					style = newStyle;
				}
			}
		}
		if (!path.isEmpty() && !Float.isNaN(currXorig)) {
			pathsData.add(new DrawPathData(path, new PointF(prevXorig, prevYorig),
					new PointF(currXorig, currYorig), style));
		}
		return cnt;
	}

	public void drawRouteSegment(RotatedTileBox tb, Canvas canvas, List<Float> tx, List<Float> ty,
								 List<Double> angles, List<Double> distances, double distToFinish,
								 List<GeometryWayStyle<?>> styles) {
		if (tx.size() < 2) {
			return;
		}
		boolean hasPathLine = false;
		try {
			for (GeometryWayStyle<?> style : styles) {
				if (style.hasPathLine()) {
					hasPathLine = true;
					break;
				}
			}
			if (hasPathLine) {
				List<DrawPathData> pathsData = new ArrayList<>();
				canvas.rotate(-tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
				calculatePath(tb, tx, ty, styles, pathsData);

				if (!Algorithms.isEmpty(pathsData)) {
					drawPathLine(canvas, tb, pathsData);
				}

				context.clearCustomColor();
				context.clearCustomShader();
			}
			drawer.drawArrowsOverPath(canvas, tb, tx, ty, angles, distances, distToFinish, styles);
		} finally {
			if (hasPathLine) {
				canvas.rotate(tb.getRotate(), tb.getCenterPixelX(), tb.getCenterPixelY());
			}
		}
	}

	private void drawPathLine(Canvas canvas, RotatedTileBox tb, List<DrawPathData> pathsData) {
		drawer.drawFullBorder(canvas, tb.getZoom(), pathsData);
		drawer.drawSegmentBorder(canvas, tb.getZoom(), pathsData.get(0));
		for (int i = 1; i <= pathsData.size(); i++) {
			if (i != pathsData.size()) {
				DrawPathData prev = pathsData.get(i);
				if (prev.style.hasPathLine()) {
					drawer.drawSegmentBorder(canvas, tb.getZoom(), prev);
				}
			}

			DrawPathData pd = pathsData.get(i - 1);
			GeometryWayStyle<?> style = pd.style;
			if (style.hasPathLine()) {
				drawer.drawPath(canvas, pd);
			}
		}
	}

	protected static class PathGeometryZoom {

		private static final float EPSILON_IN_DPI = 2;

		private final TByteArrayList simplifyPoints;
		private List<Double> distances;
		private List<Double> angles;

		public PathGeometryZoom(GeometryWayProvider locationProvider, RotatedTileBox tb, boolean simplify) {
			//  this.locations = locations;
			tb = new RotatedTileBox(tb);
			tb.setZoomAndAnimation(tb.getZoom(), 0, tb.getZoomFloatPart());
			int size = locationProvider.getSize();
			simplifyPoints = new TByteArrayList(size);
			distances = new ArrayList<>(size);
			angles = new ArrayList<>(size);
			if (simplify) {
				simplifyPoints.fill(0, size, (byte) 0);
				simplify(tb, locationProvider, simplifyPoints);
			} else {
				simplifyPoints.fill(0, size, (byte) 1);
			}
			int previousIndex = -1;
			for (int i = 0; i < size; i++) {
				double d = 0;
				double angle = 0;
				if (simplifyPoints.get(i) > 0) {
					if (previousIndex > -1) {
						float x = tb.getPixXFromLatLon(locationProvider.getLatitude(i), locationProvider.getLongitude(i));
						float y = tb.getPixYFromLatLon(locationProvider.getLatitude(i), locationProvider.getLongitude(i));
						float px = tb.getPixXFromLatLon(locationProvider.getLatitude(previousIndex), locationProvider.getLongitude(previousIndex));
						float py = tb.getPixYFromLatLon(locationProvider.getLatitude(previousIndex), locationProvider.getLongitude(previousIndex));
						d = Math.sqrt((y - py) * (y - py) + (x - px) * (x - px));
						if (px != x || py != y) {
							double angleRad = Math.atan2(y - py, x - px);
							angle = (angleRad * 180 / Math.PI) + 90f;
						}
					}
					previousIndex = i;
				}
				distances.add(d);
				angles.add(angle);
			}
		}

		protected void simplify(RotatedTileBox tb, GeometryWayProvider locationProvider, TByteArrayList simplifyPoints) {
			int size = locationProvider.getSize();
			if (size > 0) {
				simplifyPoints.set(0, (byte) 1);
			}
			double distInPix = (tb.getDistance(0, 0, tb.getPixWidth(), 0) / tb.getPixWidth());
			double cullDistance = (distInPix * (EPSILON_IN_DPI * Math.max(1, tb.getDensity())));
			cullRamerDouglasPeucker(simplifyPoints, locationProvider, 0, size - 1, cullDistance);
		}

		public List<Double> getDistances() {
			return distances;
		}

		public List<Double> getAngles() {
			return angles;
		}

		private void cullRamerDouglasPeucker(TByteArrayList survivor, GeometryWayProvider locationProvider,
											 int start, int end, double epsillon) {
			double dmax = Double.NEGATIVE_INFINITY;
			int index = -1;
			for (int i = start + 1; i < end; i++) {
				double d = MapUtils.getOrthogonalDistance(locationProvider.getLatitude(i), locationProvider.getLongitude(i),
						locationProvider.getLatitude(start), locationProvider.getLongitude(start),
						locationProvider.getLatitude(end), locationProvider.getLongitude(end));
				if (d > dmax) {
					dmax = d;
					index = i;
				}
			}
			if (dmax > epsillon) {
				cullRamerDouglasPeucker(survivor, locationProvider, start, index, epsillon);
				cullRamerDouglasPeucker(survivor, locationProvider, index, end, epsillon);
			} else {
				survivor.set(end, (byte) 1);
			}
		}

		public TByteArrayList getSimplifyPoints() {
			return simplifyPoints;
		}
	}
}