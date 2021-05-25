package net.osmand.plus.views.mapwidgets.widgets;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;

import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.GPXTrackAnalysis;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetAxisType;
import net.osmand.plus.helpers.GpxUiHelper.OrderedLineDataSet;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu;
import net.osmand.plus.measurementtool.graph.BaseCommonGraphAdapter;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.layers.GPXLayer;
import net.osmand.plus.views.layers.MapQuickActionLayer;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.List;

import static net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu.MAX_DISTANCE_LOCATION_PROJECTION;

public class ElevationProfileWidget {

	private static final int MAX_DISTANCE_TO_SHOW_IM_METERS = 10_000;

	private final MapActivity map;
	private final OsmandApplication app;
	private final OsmandSettings settings;

	private View view;
	private View uphillView;
	private View downhillView;
	private View gradeView;
	private LineChart chart;
	private BaseCommonGraphAdapter graphAdapter;

	private GPXTrackAnalysis analysis;
	private GpxDisplayItem gpxItem;
	private TrkSegment segment;
	private GPXFile gpx;
	private float toMetersMultiplier;
	private Location myLocation;

	private boolean showSlopes = false;
	private boolean shouldSetupGraph = false;

	public ElevationProfileWidget(MapActivity map) {
		this.map = map;
		app = map.getMyApplication();
		settings = app.getSettings();
		initView();
	}

	private void initView() {
		this.view = map.findViewById(R.id.elevation_profile_widget_layout);
		setupStatisticBlocks();
	}

	private void setupStatisticBlocks() {
		uphillView = setupStatisticBlock(R.id.uphill_widget,
				R.string.shared_string_uphill,
				R.drawable.ic_action_ascent_arrow_16);

		downhillView = setupStatisticBlock(R.id.downhill_widget,
				R.string.shared_string_downhill,
				R.drawable.ic_action_descent_arrow_16);

		gradeView = setupStatisticBlock(R.id.grade_widget,
				R.string.shared_string_grade,
				R.drawable.ic_action_percent_16);
	}

	private View setupStatisticBlock(int viewId, int textId, int iconId) {
		View blockView = view.findViewById(viewId);

		TextView title = blockView.findViewById(R.id.title);
		title.setText(textId);

		ImageView icon = blockView.findViewById(R.id.image);
		icon.setImageResource(iconId);

		return blockView;
	}

	public void updateInfo() {
		boolean visible = map.getWidgetsVisibilityHelper().shouldShowElevationProfileWidget();
		AndroidUiHelper.updateVisibility(view, visible);
		if (visible) {
			updateInfoImpl();
		}
	}

	private void updateInfoImpl() {
		updateSettings();
		if (shouldSetupGraph) {
			setupGraph();
		}
		updateGraph();
		updateWidgets();
	}

	private void updateSettings() {
		boolean previousShowSlopes = showSlopes;
		showSlopes = settings.SHOW_SLOPES_ON_ELEVATION_WIDGET.get();
		boolean slopesChanged = previousShowSlopes != showSlopes;

		if (!shouldSetupGraph) {
			shouldSetupGraph = slopesChanged;
		}
	}

	private void setupGraph() {
		gpx = GpxUiHelper.makeGpxFromRoute(app.getRoutingHelper().getRoute(), app);
		analysis = gpx.getAnalysis(0);
		gpxItem = GpxUiHelper.makeGpxDisplayItem(app, gpx, true);

		chart = (LineChart) view.findViewById(R.id.line_chart);
		Drawable markerIcon = app.getUIUtilities().getIcon(R.drawable.ic_action_location_color);
		GpxUiHelper.setupGPXChart(chart, 4, 24f, 16f, !isNightMode(), true, markerIcon);
		chart.setHighlightPerTapEnabled(false);
		chart.setHighlightPerDragEnabled(false);
		graphAdapter = new BaseCommonGraphAdapter(chart, true);

		if (analysis.hasElevationData) {
			List<ILineDataSet> dataSets = new ArrayList<>();
			OrderedLineDataSet elevationDataSet = GpxUiHelper.createGPXElevationDataSet(app, chart, analysis,
					GPXDataSetAxisType.DISTANCE, false, true, false);
			dataSets.add(elevationDataSet);

			if (showSlopes) {
				OrderedLineDataSet slopeDataSet = GpxUiHelper.createGPXSlopeDataSet(app, chart, analysis,
						GPXDataSetAxisType.DISTANCE, elevationDataSet.getValues(), true, true, false);
				if (slopeDataSet != null) {
					dataSets.add(slopeDataSet);
				}
			}

			graphAdapter.updateContent(new LineData(dataSets), gpxItem);
			toMetersMultiplier = ((OrderedLineDataSet) dataSets.get(0)).getDivX();

			setupZoom(chart);
			shiftQuickActionButton();
			chart.setVisibility(View.VISIBLE);
			shouldSetupGraph = false;
		} else {
			chart.setVisibility(View.GONE);
		}
	}

	private void updateGraph() {
		Location location = app.getLocationProvider().getLastKnownLocation();
		if (myLocation != null && MapUtils.areLatLonEqual(myLocation, location)) return;
		myLocation = location;

		TrkSegment segment = getTrackSegment(chart);
		LineData lineData = chart.getLineData();
		List<ILineDataSet> ds = lineData != null ? lineData.getDataSets() : null;
		if (Algorithms.isEmpty(ds) || gpxItem == null || segment == null) return;

		RotatedTileBox tb = map.getMapView().getCurrentRotatedTileBox();
		int mx = (int) tb.getPixXFromLatLon(location.getLatitude(), location.getLongitude());
		int my = (int) tb.getPixYFromLatLon(location.getLatitude(), location.getLongitude());
		int r = (int) (MAX_DISTANCE_LOCATION_PROJECTION * tb.getPixDensity());
		Pair<WptPt, WptPt> points = GPXLayer.findPointsNearSegment(tb, segment.points, r, mx, my);
		if (points != null) {
			LatLon latLon = tb.getLatLonFromPixel(mx, my);
			gpxItem.locationOnMap = GPXLayer.createProjectionPoint(points.first, points.second, latLon);

			double totalDistance = 0;
			int index = segment.points.indexOf(points.first);
			if (index != -1) {
				WptPt previousPoint = null;
				for (int i = 0; i < index; i++) {
					WptPt currentPoint = segment.points.get(i);
					if (previousPoint != null) {
						totalDistance += MapUtils.getDistance(previousPoint.lat, previousPoint.lon, currentPoint.lat, currentPoint.lon);
					}
					previousPoint = currentPoint;
				}
			}

			float minVisibleX = chart.getLowestVisibleX();
			float maxVisibleX = chart.getHighestVisibleX();
			float twentyPercent = ((maxVisibleX - minVisibleX) / 5);
			float startMoveChartPosition = minVisibleX + twentyPercent;
			float pos = (float) (totalDistance / ((OrderedLineDataSet) ds.get(0)).getDivX());

			if (pos >= minVisibleX && pos <= maxVisibleX) {
				if (pos >= startMoveChartPosition) {
					float nextVisibleX = pos - twentyPercent;
					moveViewToX(chart, nextVisibleX);
				}
				gpxItem.chartHighlightPos = pos;
				chart.highlightValue(gpxItem.chartHighlightPos, 0);

				chart.notifyDataSetChanged();
				chart.invalidate();
			}
		}
	}

	private void updateWidgets() {
		double minVisibleX = chart.getLowestVisibleX();
		double maxVisibleX = chart.getHighestVisibleX();
		float highlightPosition = gpxItem != null ? gpxItem.chartHighlightPos : -1f;

		if (highlightPosition != -1) {
			minVisibleX = highlightPosition;
		}

		double fromDistance = minVisibleX * toMetersMultiplier;
		double toDistance = maxVisibleX * toMetersMultiplier;

		GPXTrackAnalysis analysis = gpx.getAnalysis(0, fromDistance, toDistance);
		String uphill = OsmAndFormatter.getFormattedAlt(analysis.diffElevationUp, app);
		String downhill = OsmAndFormatter.getFormattedAlt(analysis.diffElevationDown, app);
		String grade = calculateGrade() + " %";

		updateTextWidget(uphillView, uphill);
		updateTextWidget(downhillView, downhill);
		updateTextWidget(gradeView, grade);
	}

	private void updateTextWidget(View container, String text) {
		String[] split = text.split(" ");
		if (split.length == 2) {
			((TextView) container.findViewById(R.id.widget_text)).setText(split[0]);
			((TextView) container.findViewById(R.id.widget_text_small)).setText(split[1]);
		}
	}

	private int calculateGrade() {
		return 0;
	}

	private void setupZoom(LineChart chart) {
		chart.fitScreen();
		float maxValue = chart.getXChartMax() * toMetersMultiplier;
		if (maxValue > MAX_DISTANCE_TO_SHOW_IM_METERS) {
			float scaleX = maxValue / MAX_DISTANCE_TO_SHOW_IM_METERS;
			chart.zoom(scaleX, 1.0f, 0, 0);
			chart.scrollTo(0, 0);
		}
	}

	private void shiftQuickActionButton() {
		MapQuickActionLayer quickActionLayer = map.getMapLayers().getMapQuickActionLayer();
		if (quickActionLayer != null) {
			quickActionLayer.refreshLayer();
		}
	}

	public void onRouteCalculated() {
		shouldSetupGraph = true;
	}

	@Nullable
	private TrkSegment getTrackSegment(@NonNull LineChart chart) {
		if (segment == null) {
			segment = TrackDetailsMenu.getTrackSegment(chart, gpxItem);
		}
		return segment;
	}

	private boolean isNightMode() {
		return app.getDaynightHelper().isNightModeForMapControls();
	}

	private static void moveViewToX(LineChart chart, float nextVisibleX) {
		ViewPortHandler handler = chart.getViewPortHandler();
		Transformer transformer = chart.getTransformer(AxisDependency.LEFT);

		float[] pts = new float[2];
		pts[0] = nextVisibleX;
		pts[1] = 0f;
		transformer.pointValuesToPixel(pts);

		Matrix save = new Matrix();
		save.reset();
		save.set(handler.getMatrixTouch());

		float x = pts[0] - handler.offsetLeft();
		float y = pts[1] - handler.offsetTop();
		save.postTranslate(-x, -y);
		handler.refresh(save, chart, false);
	}

}
