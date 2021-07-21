package net.osmand.plus.views.layers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.util.Pair;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.QuadTree;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.GpxDbHelper;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.PointImageDrawable;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.plus.mapcontextmenu.other.TrackChartPoints;
import net.osmand.plus.mapmarkers.MapMarker;
import net.osmand.plus.mapmarkers.MapMarkersGroup;
import net.osmand.plus.mapmarkers.MapMarkersHelper;
import net.osmand.plus.render.OsmandRenderer;
import net.osmand.plus.render.OsmandRenderer.RenderingContext;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.track.CachedTrack;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.plus.track.SaveGpxAsyncTask;
import net.osmand.plus.track.TrackDrawInfo;
import net.osmand.plus.track.TrackMenuFragment;
import net.osmand.plus.views.OsmandMapLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.Renderable;
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider;
import net.osmand.plus.views.layers.ContextMenuLayer.IMoveObjectProvider;
import net.osmand.plus.views.layers.MapTextLayer.MapTextProvider;
import net.osmand.plus.views.layers.geometry.GpxGeometryWay;
import net.osmand.plus.views.layers.geometry.GpxGeometryWayContext;
import net.osmand.plus.wikivoyage.data.TravelGpx;
import net.osmand.plus.wikivoyage.data.TravelHelper;
import net.osmand.render.RenderingRuleProperty;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import static net.osmand.GPXUtilities.calculateTrackBounds;
import static net.osmand.plus.dialogs.ConfigureMapMenu.CURRENT_TRACK_COLOR_ATTR;
import static net.osmand.plus.dialogs.ConfigureMapMenu.CURRENT_TRACK_WIDTH_ATTR;

public class GPXLayer extends OsmandMapLayer implements IContextMenuProvider, IMoveObjectProvider, MapTextProvider<WptPt> {

	private static final Log log = PlatformUtil.getLog(GPXLayer.class);

	private static final double TOUCH_RADIUS_MULTIPLIER = 1.5;
	private static final int DEFAULT_WIDTH_MULTIPLIER = 7;
	private static final int START_ZOOM = 7;

	private OsmandMapTileView view;

	private Paint paint;
	private Paint borderPaint;
	private Paint shadowPaint;
	private Paint paintIcon;

	private int cachedHash;
	@ColorInt
	private int cachedColor;
	private float defaultTrackWidth;
	private Map<String, Float> cachedTrackWidth = new HashMap<>();

	private Drawable startPointIcon;
	private Drawable finishPointIcon;
	private Drawable startAndFinishIcon;
	private LayerDrawable selectedPoint;
	private TrackDrawInfo trackDrawInfo;
	private TrackChartPoints trackChartPoints;

	private GpxDbHelper gpxDbHelper;
	private MapMarkersHelper mapMarkersHelper;
	private GpxSelectionHelper selectedGpxHelper;

	private final Map<String, CachedTrack> segmentsCache = new HashMap<>();

	private List<WptPt> cache = new ArrayList<>();
	private Map<WptPt, SelectedGpxFile> pointFileMap = new HashMap<>();
	private MapTextLayer textLayer;

	private Paint paintOuterRect;
	private Paint paintInnerRect;

	private Paint paintGridOuterCircle;
	private Paint paintGridCircle;

	private Paint paintTextIcon;

	private GpxGeometryWayContext wayContext;
	private GpxGeometryWay wayGeometry;

	private OsmandRenderer osmandRenderer;

	private ContextMenuLayer contextMenuLayer;
	@ColorInt
	private int visitedColor;
	@ColorInt
	private int defPointColor;
	@ColorInt
	private int grayColor;

	private CommonPreference<String> defaultTrackColorPref;
	private CommonPreference<String> defaultTrackWidthPref;

	private CommonPreference<Integer> currentTrackColorPref;
	private CommonPreference<ColoringType> currentTrackColoringTypePref;
	private CommonPreference<String> currentTrackRouteInfoAttributePref;
	private CommonPreference<String> currentTrackWidthPref;
	private CommonPreference<Boolean> currentTrackShowArrowsPref;
	private CommonPreference<Boolean> currentTrackShowStartFinishPref;

	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		gpxDbHelper = view.getApplication().getGpxDbHelper();
		selectedGpxHelper = view.getApplication().getSelectedGpxHelper();
		mapMarkersHelper = view.getApplication().getMapMarkersHelper();
		osmandRenderer = view.getApplication().getResourceManager().getRenderer().getRenderer();

		currentTrackColorPref = view.getSettings().CURRENT_TRACK_COLOR;
		currentTrackColoringTypePref = view.getSettings().CURRENT_TRACK_COLORING_TYPE;
		currentTrackRouteInfoAttributePref = view.getSettings().CURRENT_TRACK_ROUTE_INFO_ATTRIBUTE;
		currentTrackWidthPref = view.getSettings().CURRENT_TRACK_WIDTH;
		currentTrackShowArrowsPref = view.getSettings().CURRENT_TRACK_SHOW_ARROWS;
		currentTrackShowStartFinishPref = view.getSettings().CURRENT_TRACK_SHOW_START_FINISH;
		defaultTrackColorPref = view.getSettings().getCustomRenderProperty(CURRENT_TRACK_COLOR_ATTR).cache();
		defaultTrackWidthPref = view.getSettings().getCustomRenderProperty(CURRENT_TRACK_WIDTH_ATTR).cache();

		initUI();
	}

	public void setTrackChartPoints(TrackChartPoints trackChartPoints) {
		this.trackChartPoints = trackChartPoints;
	}

	public boolean isInTrackAppearanceMode() {
		return trackDrawInfo != null;
	}

	public void setTrackDrawInfo(TrackDrawInfo trackDrawInfo) {
		this.trackDrawInfo = trackDrawInfo;
	}

	private void initUI() {
		paint = new Paint();
		paint.setStyle(Style.STROKE);
		paint.setAntiAlias(true);

		borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		borderPaint.setStyle(Style.STROKE);
		borderPaint.setStrokeJoin(Paint.Join.ROUND);
		borderPaint.setStrokeCap(Paint.Cap.ROUND);
		borderPaint.setColor(0x80000000);

		shadowPaint = new Paint();
		shadowPaint.setStyle(Style.STROKE);
		shadowPaint.setAntiAlias(true);

		paintTextIcon = new Paint();
		paintTextIcon.setTextSize(10 * view.getDensity());
		paintTextIcon.setTextAlign(Align.CENTER);
		paintTextIcon.setFakeBoldText(true);
		paintTextIcon.setAntiAlias(true);

		textLayer = view.getLayerByClass(MapTextLayer.class);

		paintInnerRect = new Paint();
		paintInnerRect.setStyle(Style.FILL);
		paintInnerRect.setAntiAlias(true);
		paintOuterRect = new Paint();
		paintOuterRect.setStyle(Style.STROKE);
		paintOuterRect.setAntiAlias(true);
		paintOuterRect.setStrokeWidth(3);
		paintOuterRect.setAlpha(255);
		paintGridCircle = new Paint();
		paintGridCircle.setStyle(Style.FILL_AND_STROKE);
		paintGridCircle.setAntiAlias(true);
		paintGridOuterCircle = new Paint();
		paintGridOuterCircle.setStyle(Style.FILL_AND_STROKE);
		paintGridOuterCircle.setAntiAlias(true);
		paintGridOuterCircle.setColor(Color.WHITE);
		paintGridOuterCircle.setAlpha(204);

		paintIcon = new Paint();
		selectedPoint = (LayerDrawable) AppCompatResources.getDrawable(view.getContext(), R.drawable.map_location_default);

		UiUtilities iconsCache = view.getApplication().getUIUtilities();
		startPointIcon = iconsCache.getIcon(R.drawable.map_track_point_start);
		finishPointIcon = iconsCache.getIcon(R.drawable.map_track_point_finish);
		startAndFinishIcon = iconsCache.getIcon(R.drawable.map_track_point_start_finish);

		contextMenuLayer = view.getLayerByClass(ContextMenuLayer.class);

		visitedColor = ContextCompat.getColor(view.getApplication(), R.color.color_ok);
		defPointColor = ContextCompat.getColor(view.getApplication(), R.color.gpx_color_point);
		grayColor = ContextCompat.getColor(view.getApplication(), R.color.color_favorite_gray);

		wayContext = new GpxGeometryWayContext(view.getContext(), view.getDensity());
		wayGeometry = new GpxGeometryWay(wayContext);
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		if (contextMenuLayer.getMoveableObject() instanceof WptPt) {
			WptPt objectInMotion = (WptPt) contextMenuLayer.getMoveableObject();
			SelectedGpxFile gpxFile = pointFileMap.get(objectInMotion);
			if (gpxFile != null) {
				PointF pf = contextMenuLayer.getMovableCenterPoint(tileBox);
				MapMarker mapMarker = mapMarkersHelper.getMapMarker(objectInMotion);
				float textScale = view.getSettings().TEXT_SCALE.get();
				drawBigPoint(canvas, objectInMotion, getFileColor(gpxFile), pf.x, pf.y, mapMarker, textScale);
			}
		}
	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		List<SelectedGpxFile> selectedGPXFiles = new ArrayList<>(selectedGpxHelper.getSelectedGPXFiles());

		Iterator<SelectedGpxFile> iterator = selectedGPXFiles.iterator();
		while (iterator.hasNext()) {
			SelectedGpxFile selectedGpxFile = iterator.next();
			if (selectedGpxFile.isFollowTrack(view.getApplication()) && !showTrackToFollow()) {
				iterator.remove();
			}
		}
		cache.clear();
		removeCachedUnselectedTracks(selectedGPXFiles);
		if (!selectedGPXFiles.isEmpty()) {
			drawSelectedFilesSegments(canvas, tileBox, selectedGPXFiles, settings);
			canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
			if (trackChartPoints != null) {
				drawXAxisPoints(canvas, tileBox);
			}
			drawDirectionArrows(canvas, tileBox, selectedGPXFiles);
			drawSelectedFilesSplits(canvas, tileBox, selectedGPXFiles, settings);
			drawSelectedFilesPoints(canvas, tileBox, selectedGPXFiles);
			drawSelectedFilesStartEndPoints(canvas, tileBox, selectedGPXFiles);
		}
		if (textLayer != null && isTextVisible()) {
			textLayer.putData(this, cache);
		}
	}

	private void updatePaints(int color, String width, boolean routePoints, boolean currentTrack, DrawSettings drawSettings, RotatedTileBox tileBox) {
		RenderingRulesStorage rrs = view.getApplication().getRendererRegistry().getCurrentSelectedRenderer();
		boolean nightMode = drawSettings != null && drawSettings.isNightMode();
		int hash = calculateHash(rrs, cachedTrackWidth, routePoints, nightMode, tileBox.getMapDensity(), tileBox.getZoom(),
				defaultTrackColorPref.get(), defaultTrackWidthPref.get());
		if (hash != cachedHash) {
			cachedHash = hash;
			cachedColor = ContextCompat.getColor(view.getApplication(), R.color.gpx_track);
			defaultTrackWidth = DEFAULT_WIDTH_MULTIPLIER * view.getDensity();
			if (rrs != null) {
				RenderingRuleSearchRequest req = new RenderingRuleSearchRequest(rrs);
				req.setBooleanFilter(rrs.PROPS.R_NIGHT_MODE, nightMode);
				if (defaultTrackColorPref != null && defaultTrackColorPref.isSet()) {
					RenderingRuleProperty ctColor = rrs.PROPS.get(CURRENT_TRACK_COLOR_ATTR);
					if (ctColor != null) {
						req.setStringFilter(ctColor, defaultTrackColorPref.get());
					}
				}
				if (defaultTrackWidthPref != null && defaultTrackWidthPref.isSet()) {
					RenderingRuleProperty ctWidth = rrs.PROPS.get(CURRENT_TRACK_WIDTH_ATTR);
					if (ctWidth != null) {
						req.setStringFilter(ctWidth, defaultTrackWidthPref.get());
					}
				}
				String additional = "";
				if (routePoints) {
					additional = "routePoints=true";
				}
				if (currentTrack) {
					additional = (additional.length() == 0 ? "" : ";") + "currentTrack=true";
				}
				req.setIntFilter(rrs.PROPS.R_MINZOOM, tileBox.getZoom());
				req.setIntFilter(rrs.PROPS.R_MAXZOOM, tileBox.getZoom());
				if (additional.length() > 0) {
					req.setStringFilter(rrs.PROPS.R_ADDITIONAL, additional);
				}
				if (req.searchRenderingAttribute("gpx")) {
					RenderingContext rc = new OsmandRenderer.RenderingContext(view.getContext());
					rc.setDensityValue((float) tileBox.getMapDensity());
					cachedColor = req.getIntPropertyValue(rrs.PROPS.R_COLOR);
					defaultTrackWidth = rc.getComplexValue(req, req.ALL.R_STROKE_WIDTH);
					osmandRenderer.updatePaint(req, paint, 0, false, rc);

					if (req.isSpecified(rrs.PROPS.R_SHADOW_RADIUS)) {
						int shadowColor = req.getIntPropertyValue(rrs.PROPS.R_SHADOW_COLOR);
						float shadowRadius = rc.getComplexValue(req, rrs.PROPS.R_SHADOW_RADIUS);
						shadowPaint.setColorFilter(new PorterDuffColorFilter(shadowColor, Mode.SRC_IN));
						shadowPaint.setStrokeWidth(paint.getStrokeWidth() + 2 * shadowRadius);
					}
					for (String key : cachedTrackWidth.keySet()) {
						acquireTrackWidth(key, rrs, req, rc);
					}
				} else {
					log.error("Rendering attribute gpx is not found !");
					for (String key : cachedTrackWidth.keySet()) {
						cachedTrackWidth.put(key, defaultTrackWidth);
					}
				}
			}
		}
		paint.setColor(color == 0 ? cachedColor : color);
		paint.setStrokeWidth(getTrackWidth(width, defaultTrackWidth));
		borderPaint.setStrokeWidth(paint.getStrokeWidth() + AndroidUtils.dpToPx(view.getContext(), 2));
	}

	private void acquireTrackWidth(String widthKey, RenderingRulesStorage rrs, RenderingRuleSearchRequest req, RenderingContext rc) {
		if (!Algorithms.isEmpty(widthKey) && Algorithms.isInt(widthKey)) {
			try {
				int widthDp = Integer.parseInt(widthKey);
				float widthF = AndroidUtils.dpToPx(view.getApplication(), widthDp);
				cachedTrackWidth.put(widthKey, widthF);
			} catch (NumberFormatException e) {
				log.error(e.getMessage(), e);
				cachedTrackWidth.put(widthKey, defaultTrackWidth);
			}
		} else {
			RenderingRuleProperty ctWidth = rrs.PROPS.get(CURRENT_TRACK_WIDTH_ATTR);
			if (ctWidth != null) {
				req.setStringFilter(ctWidth, widthKey);
			}
			if (req.searchRenderingAttribute("gpx")) {
				float widthF = rc.getComplexValue(req, req.ALL.R_STROKE_WIDTH);
				cachedTrackWidth.put(widthKey, widthF);
			}
		}
	}

	private int calculateHash(Object... o) {
		return Arrays.hashCode(o);
	}

	private void drawSelectedFilesSplits(Canvas canvas, RotatedTileBox tileBox, List<SelectedGpxFile> selectedGPXFiles,
										 DrawSettings settings) {
		if (tileBox.getZoom() >= START_ZOOM) {
			// request to load
			OsmandApplication app = view.getApplication();
			for (SelectedGpxFile selectedGpxFile : selectedGPXFiles) {
				List<GpxDisplayGroup> groups = selectedGpxFile.getDisplayGroups(app);
				if (!Algorithms.isEmpty(groups)) {
					int color = getTrackColor(selectedGpxFile.getGpxFile(), cachedColor);
					paintInnerRect.setColor(color);
					paintInnerRect.setAlpha(179);

					int contrastColor = UiUtilities.getContrastColor(app, color, false);
					paintTextIcon.setColor(contrastColor);
					paintOuterRect.setColor(contrastColor);

					List<GpxDisplayItem> items = groups.get(0).getModifiableList();

					drawSplitItems(canvas, tileBox, items, settings);
				}
			}
		}
	}

	private void drawSplitItems(Canvas canvas, RotatedTileBox tileBox, List<GpxDisplayItem> items, DrawSettings settings) {
		final QuadRect latLonBounds = tileBox.getLatLonBounds();
		int r = (int) (12 * tileBox.getDensity());
		paintTextIcon.setTextSize(r);
		int dr = r * 3 / 2;
		float px = -1;
		float py = -1;
		for (int k = 0; k < items.size(); k++) {
			GpxDisplayItem i = items.get(k);
			WptPt point = i.locationEnd;
			if (point != null && point.lat >= latLonBounds.bottom && point.lat <= latLonBounds.top
					&& point.lon >= latLonBounds.left && point.lon <= latLonBounds.right) {
				float x = tileBox.getPixXFromLatLon(point.lat, point.lon);
				float y = tileBox.getPixYFromLatLon(point.lat, point.lon);
				if (px != -1 || py != -1) {
					if (Math.abs(x - px) <= dr && Math.abs(y - py) <= dr) {
						continue;
					}
				}
				px = x;
				py = y;
				String name = i.splitName;
				if (name != null) {
					int ind = name.indexOf(' ');
					if (ind > 0) {
						name = name.substring(0, ind);
					}
					Rect bounds = new Rect();
					paintTextIcon.getTextBounds(name, 0, name.length(), bounds);

					float nameHalfWidth = bounds.width() / 2f;
					float nameHalfHeight = bounds.height() / 2f;
					float density = (float) Math.ceil(tileBox.getDensity());
					RectF rect = new RectF(x - nameHalfWidth - 2 * density,
							y + nameHalfHeight + 3 * density,
							x + nameHalfWidth + 3 * density,
							y - nameHalfHeight - 2 * density);

					canvas.drawRoundRect(rect, 0, 0, paintInnerRect);
					canvas.drawRoundRect(rect, 0, 0, paintOuterRect);
					canvas.drawText(name, x, y + nameHalfHeight, paintTextIcon);
				}
			}
		}
	}

	private void drawDirectionArrows(Canvas canvas, RotatedTileBox tileBox, List<SelectedGpxFile> selectedGPXFiles) {
		if (!tileBox.isZoomAnimated()) {
			QuadRect correctedQuadRect = getCorrectedQuadRect(tileBox.getLatLonBounds());
			for (SelectedGpxFile selectedGpxFile : selectedGPXFiles) {
				boolean showArrows = isShowArrowsForTrack(selectedGpxFile.getGpxFile());
				String coloringTypeName = getAvailableOrDefaultColoringType(selectedGpxFile);
				ColoringType coloringType = ColoringType.getNonNullTrackColoringTypeByName(coloringTypeName);

				if (!showArrows || coloringType.isRouteInfoAttribute()
						|| !QuadRect.trivialOverlap(correctedQuadRect, calculateTrackBounds(selectedGpxFile.getPointsToDisplay()))) {
					continue;
				}
				String width = getTrackWidthName(selectedGpxFile.getGpxFile(), defaultTrackWidthPref.get());
				float trackWidth = getTrackWidth(width, defaultTrackWidth);
				int trackColor = getTrackColor(selectedGpxFile.getGpxFile(), cachedColor);
				int arrowColor = UiUtilities.getContrastColor(view.getApplication(), trackColor, false);
				List<TrkSegment> segments = coloringType.isGradient()
						? getCachedSegments(selectedGpxFile, coloringType.toGradientScaleType())
						: selectedGpxFile.getPointsToDisplay();
				for (TrkSegment segment : segments) {
					if (segment.renderer instanceof Renderable.RenderableSegment) {
						((Renderable.RenderableSegment) segment.renderer)
								.drawGeometry(canvas, tileBox, correctedQuadRect, arrowColor, trackColor, trackWidth);
					}
				}
			}
		}
	}

	private void drawSelectedFilesStartEndPoints(Canvas canvas, RotatedTileBox tileBox, List<SelectedGpxFile> selectedGPXFiles) {
		if (tileBox.getZoom() >= START_ZOOM) {
			for (SelectedGpxFile selectedGpxFile : selectedGPXFiles) {
				boolean showStartFinish = isShowStartFinishForTrack(selectedGpxFile.getGpxFile());
				if (showStartFinish) {
					List<TrkSegment> segments = selectedGpxFile.getPointsToDisplay();
					for (TrkSegment segment : segments) {
						if (segment.points.size() >= 2) {
							WptPt start = segment.points.get(0);
							WptPt end = segment.points.get(segment.points.size() - 1);
							drawStartEndPoints(canvas, tileBox, start, selectedGpxFile.isShowCurrentTrack() ? null : end);
						}
					}
				}
			}
		}
	}

	private void drawStartEndPoints(@NonNull Canvas canvas, @NonNull RotatedTileBox tileBox, @Nullable WptPt start, @Nullable WptPt end) {
		int startX = start != null ? (int) tileBox.getPixXFromLatLon(start.lat, start.lon) : 0;
		int startY = start != null ? (int) tileBox.getPixYFromLatLon(start.lat, start.lon) : 0;
		int endX = end != null ? (int) tileBox.getPixXFromLatLon(end.lat, end.lon) : 0;
		int endY = end != null ? (int) tileBox.getPixYFromLatLon(end.lat, end.lon) : 0;

		int iconSize = AndroidUtils.dpToPx(view.getContext(), 14);
		QuadRect startRectWithoutShadow = calculateRect(startX, startY, iconSize, iconSize);
		QuadRect endRectWithoutShadow = calculateRect(endX, endY, iconSize, iconSize);

		if (start != null && end != null && QuadRect.intersects(startRectWithoutShadow, endRectWithoutShadow)) {
			QuadRect startAndFinishRect = calculateRect(startX, startY, startAndFinishIcon.getIntrinsicWidth(), startAndFinishIcon.getIntrinsicHeight());
			drawPoint(canvas, startAndFinishRect, startAndFinishIcon);
		} else {
			if (start != null) {
				QuadRect startRect = calculateRect(startX, startY, startPointIcon.getIntrinsicWidth(), startPointIcon.getIntrinsicHeight());
				drawPoint(canvas, startRect, startPointIcon);
			}
			if (end != null) {
				QuadRect endRect = calculateRect(endX, endY, finishPointIcon.getIntrinsicWidth(), finishPointIcon.getIntrinsicHeight());
				drawPoint(canvas, endRect, finishPointIcon);
			}
		}
	}

	private void drawPoint(Canvas canvas, QuadRect rect, Drawable icon) {
		icon.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
		icon.draw(canvas);
	}

	private void drawSelectedFilesPoints(Canvas canvas, RotatedTileBox tileBox, List<SelectedGpxFile> selectedGPXFiles) {
		if (tileBox.getZoom() >= START_ZOOM) {
			float textScale = view.getSettings().TEXT_SCALE.get();
			float iconSize = getIconSize(view.getApplication());
			QuadTree<QuadRect> boundIntersections = initBoundIntersections(tileBox);

			List<LatLon> fullObjectsLatLon = new ArrayList<>();
			List<LatLon> smallObjectsLatLon = new ArrayList<>();
			Map<WptPt, SelectedGpxFile> pointFileMap = new HashMap<>();
			// request to load
			final QuadRect latLonBounds = tileBox.getLatLonBounds();
			for (SelectedGpxFile g : selectedGPXFiles) {
				List<Pair<WptPt, MapMarker>> fullObjects = new ArrayList<>();
				int fileColor = getFileColor(g);
				boolean synced = isSynced(g.getGpxFile());
				for (WptPt wpt : getListStarPoints(g)) {
					if (wpt.lat >= latLonBounds.bottom && wpt.lat <= latLonBounds.top
							&& wpt.lon >= latLonBounds.left && wpt.lon <= latLonBounds.right
							&& wpt != contextMenuLayer.getMoveableObject() && !isPointHidden(g, wpt)) {
						pointFileMap.put(wpt, g);
						MapMarker marker = null;
						if (synced && (marker = mapMarkersHelper.getMapMarker(wpt)) == null) {
							continue;
						}
						cache.add(wpt);
						float x = tileBox.getPixXFromLatLon(wpt.lat, wpt.lon);
						float y = tileBox.getPixYFromLatLon(wpt.lat, wpt.lon);

						if (intersects(boundIntersections, x, y, iconSize, iconSize)) {
							@ColorInt
							int color;
							if (marker != null && marker.history) {
								color = grayColor;
							} else {
								color = getPointColor(wpt, fileColor);
							}
							PointImageDrawable pointImageDrawable = PointImageDrawable.getFromWpt(view.getContext(), color,
									true, wpt);
							pointImageDrawable.drawSmallPoint(canvas, x, y, textScale);
							smallObjectsLatLon.add(new LatLon(wpt.lat, wpt.lon));
						} else {
							fullObjects.add(new Pair<>(wpt, marker));
							fullObjectsLatLon.add(new LatLon(wpt.lat, wpt.lon));
						}
					}
					if (wpt == contextMenuLayer.getMoveableObject()) {
						pointFileMap.put(wpt, g);
					}
				}
				for (Pair<WptPt, MapMarker> pair : fullObjects) {
					WptPt wpt = pair.first;
					float x = tileBox.getPixXFromLatLon(wpt.lat, wpt.lon);
					float y = tileBox.getPixYFromLatLon(wpt.lat, wpt.lon);
					drawBigPoint(canvas, wpt, fileColor, x, y, pair.second, textScale);
				}
			}
			if (trackChartPoints != null) {
				LatLon highlightedPoint = trackChartPoints.getHighlightedPoint();
				if (highlightedPoint != null) {
					if (highlightedPoint.getLatitude() >= latLonBounds.bottom
							&& highlightedPoint.getLatitude() <= latLonBounds.top
							&& highlightedPoint.getLongitude() >= latLonBounds.left
							&& highlightedPoint.getLongitude() <= latLonBounds.right) {
						float x = tileBox.getPixXFromLatLon(highlightedPoint.getLatitude(), highlightedPoint.getLongitude());
						float y = tileBox.getPixYFromLatLon(highlightedPoint.getLatitude(), highlightedPoint.getLongitude());
						paintIcon.setColorFilter(null);
						selectedPoint.setBounds((int) x - selectedPoint.getIntrinsicWidth() / 2,
								(int) y - selectedPoint.getIntrinsicHeight() / 2,
								(int) x + selectedPoint.getIntrinsicWidth() / 2,
								(int) y + selectedPoint.getIntrinsicHeight() / 2);
						selectedPoint.draw(canvas);
					}
				}
			}
			this.fullObjectsLatLon = fullObjectsLatLon;
			this.smallObjectsLatLon = smallObjectsLatLon;
			this.pointFileMap = pointFileMap;
		}
	}

	private boolean isSynced(@NonNull GPXFile gpxFile) {
		MapMarkersGroup markersGroup = mapMarkersHelper.getMarkersGroup(gpxFile);
		return markersGroup != null && !markersGroup.isDisabled();
	}

	private void drawXAxisPoints(Canvas canvas, RotatedTileBox tileBox) {
		int color = trackChartPoints.getSegmentColor();
		if (color == 0) {
			color = getTrackColor(trackChartPoints.getGpx(), cachedColor);
			trackChartPoints.setSegmentColor(color);
		}
		paintGridCircle.setColor(color);
		paintGridCircle.setAlpha(255);
		QuadRect latLonBounds = tileBox.getLatLonBounds();
		float r = 3 * tileBox.getDensity();
		List<LatLon> xAxisPoints = trackChartPoints.getXAxisPoints();
		if (xAxisPoints != null) {
			float density = (float) Math.ceil(tileBox.getDensity());
			float outerRadius = r + 2 * density;
			float innerRadius = r + density;
			QuadRect prevPointRect = null;
			for (int i = 0; i < xAxisPoints.size(); i++) {
				LatLon axisPoint = xAxisPoints.get(i);
				if (axisPoint != null) {
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
	}

	private int getFileColor(@NonNull SelectedGpxFile g) {
		return g.getColor() == 0 ? defPointColor : g.getColor();
	}

	private void drawBigPoint(Canvas canvas, WptPt wpt, int fileColor, float x, float y, @Nullable MapMarker marker, float textScale) {
		int pointColor = getPointColor(wpt, fileColor);
		PointImageDrawable pointImageDrawable;
		boolean history = false;
		if (marker != null) {
			pointImageDrawable = PointImageDrawable.getOrCreateSyncedIcon(view.getContext(), pointColor, wpt);
			history = marker.history;
		} else {
			pointImageDrawable = PointImageDrawable.getFromWpt(view.getContext(), pointColor, true, wpt);
		}
		pointImageDrawable.drawPoint(canvas, x, y, textScale, history);
	}

	@ColorInt
	private int getPointColor(WptPt o, @ColorInt int fileColor) {
		boolean visit = isPointVisited(o);
		return visit ? visitedColor : o.getColor(fileColor);
	}

	private void drawSelectedFilesSegments(Canvas canvas, RotatedTileBox tileBox,
										   List<SelectedGpxFile> selectedGPXFiles, DrawSettings settings) {
		SelectedGpxFile currentTrack = null;
		for (SelectedGpxFile selectedGpxFile : selectedGPXFiles) {
			String width = getTrackWidthName(selectedGpxFile.getGpxFile(), defaultTrackWidthPref.get());
			if (!cachedTrackWidth.containsKey(width)) {
				cachedTrackWidth.put(width, null);
			}
			if (selectedGpxFile.isShowCurrentTrack()) {
				currentTrack = selectedGpxFile;
			} else {
				drawSelectedFileSegments(selectedGpxFile, false, canvas, tileBox, settings);
			}
		}
		if (currentTrack != null) {
			drawSelectedFileSegments(currentTrack, true, canvas, tileBox, settings);
		}
	}

	private void drawSelectedFileSegments(SelectedGpxFile selectedGpxFile, boolean currentTrack,
										  Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		GPXFile gpxFile = selectedGpxFile.getGpxFile();
		QuadRect correctedQuadRect = getCorrectedQuadRect(tileBox.getLatLonBounds());
		String coloringTypeName = getAvailableOrDefaultColoringType(selectedGpxFile);
		ColoringType coloringType = ColoringType.getNonNullTrackColoringTypeByName(coloringTypeName);
		String routeIndoAttribute = ColoringType.getRouteInfoAttribute(coloringTypeName);

		boolean visible = QuadRect.trivialOverlap(tileBox.getLatLonBounds(),
				calculateTrackBounds(selectedGpxFile.getPointsToDisplay()));
		if (!gpxFile.hasTrkPt() && coloringType.isGradient() || !visible) {
			segmentsCache.remove(selectedGpxFile.getGpxFile().path);
			return;
		}

		List<TrkSegment> segments = new ArrayList<>();
		if (coloringType.isTrackSolid() || coloringType.isRouteInfoAttribute()) {
			segments.addAll(selectedGpxFile.getPointsToDisplay());
		} else {
			segments.addAll(getCachedSegments(selectedGpxFile, coloringType.toGradientScaleType()));
		}

		for (int segmentIdx = 0; segmentIdx < segments.size(); segmentIdx++) {
			TrkSegment ts = segments.get(segmentIdx);

			String width = getTrackWidthName(gpxFile, defaultTrackWidthPref.get());
			int color = getTrackColor(gpxFile, ts.getColor(cachedColor));
			if (ts.renderer == null && !ts.points.isEmpty()) {
				Renderable.RenderableSegment renderer;
				if (currentTrack) {
					renderer = new Renderable.CurrentTrack(ts.points);
				} else {
					renderer = new Renderable.StandardTrack(ts.points, 17.2);
				}
				ts.renderer = renderer;
				renderer.setGeometryWay(new GpxGeometryWay(wayContext));
			}
			updatePaints(color, width, selectedGpxFile.isRoutePoints(), currentTrack, settings, tileBox);
			if (ts.renderer instanceof Renderable.RenderableSegment) {
				Renderable.RenderableSegment renderableSegment = (Renderable.RenderableSegment) ts.renderer;
				renderableSegment.setTrackColoringParams(coloringType, routeIndoAttribute, borderPaint);
				if (coloringType.isRouteInfoAttribute()) {
					renderableSegment.setRoute(getCachedTrack(selectedGpxFile).getCachedRouteSegments(segmentIdx));
					renderableSegment.drawGeometry(canvas, tileBox, correctedQuadRect, Color.BLACK,
							paint.getColor(), paint.getStrokeWidth());
				} else {
					renderableSegment.drawSegment(view.getZoom(), paint, canvas, tileBox);
				}
			}
		}
	}

	private List<TrkSegment> getCachedSegments(SelectedGpxFile selectedGpxFile, GradientScaleType scaleType) {
		CachedTrack cachedTrack = getCachedTrack(selectedGpxFile);
		return cachedTrack.getCachedTrackSegments(view.getZoom(), scaleType);
	}

	private float getTrackWidth(String width, float defaultTrackWidth) {
		Float trackWidth = cachedTrackWidth.get(width);
		return trackWidth != null ? trackWidth : defaultTrackWidth;
	}

	private int getTrackColor(GPXFile gpxFile, int defaultColor) {
		int color = 0;
		if (hasTrackDrawInfoForTrack(gpxFile)) {
			color = trackDrawInfo.getColor();
		} else if (gpxFile.showCurrentTrack) {
			color = currentTrackColorPref.get();
		} else {
			GpxDataItem dataItem = gpxDbHelper.getItem(new File(gpxFile.path));
			if (dataItem != null) {
				color = dataItem.getColor();
			}
		}
		return color != 0 ? color : defaultColor;
	}

	private String getAvailableOrDefaultColoringType(SelectedGpxFile selectedGpxFile) {
		GPXFile gpxFile = selectedGpxFile.getGpxFile();

		if (hasTrackDrawInfoForTrack(gpxFile)) {
			return trackDrawInfo.getColoringType().getName(trackDrawInfo.getRouteInfoAttribute());
		}

		GpxDataItem dataItem = null;
		String defaultColoringType = ColoringType.TRACK_SOLID.getName(null);
		ColoringType coloringType = null;
		String routeInfoAttribute = null;
		boolean isCurrentTrack = gpxFile.showCurrentTrack;

		if (isCurrentTrack) {
			coloringType = currentTrackColoringTypePref.get();
			routeInfoAttribute = currentTrackRouteInfoAttributePref.get();
		} else {
			dataItem = gpxDbHelper.getItem(new File(gpxFile.path));
			if (dataItem != null) {
				coloringType = ColoringType.getNonNullTrackColoringTypeByName(dataItem.getColoringType());
				routeInfoAttribute = ColoringType.getRouteInfoAttribute(dataItem.getColoringType());
			}
		}

		if (coloringType == null) {
			return defaultColoringType;
		} else if (getCachedTrack(selectedGpxFile).isColoringTypeAvailable(coloringType, routeInfoAttribute)) {
			return coloringType.getName(routeInfoAttribute);
		} else {
			if (!isCurrentTrack) {
				gpxDbHelper.updateColoringType(dataItem, defaultColoringType);
			}
			return defaultColoringType;
		}
	}

	private String getTrackWidthName(GPXFile gpxFile, String defaultWidth) {
		String width = null;
		if (hasTrackDrawInfoForTrack(gpxFile)) {
			width = trackDrawInfo.getWidth();
		} else if (gpxFile.showCurrentTrack) {
			width = currentTrackWidthPref.get();
		} else {
			GpxDataItem dataItem = gpxDbHelper.getItem(new File(gpxFile.path));
			if (dataItem != null) {
				width = dataItem.getWidth();
			}
		}
		return width != null ? width : defaultWidth;
	}

	private boolean isShowArrowsForTrack(GPXFile gpxFile) {
		if (hasTrackDrawInfoForTrack(gpxFile)) {
			return trackDrawInfo.isShowArrows();
		} else if (gpxFile.showCurrentTrack) {
			return currentTrackShowArrowsPref.get();
		} else {
			GpxDataItem dataItem = gpxDbHelper.getItem(new File(gpxFile.path));
			if (dataItem != null) {
				return dataItem.isShowArrows();
			}
			return false;
		}
	}

	private boolean isShowStartFinishForTrack(GPXFile gpxFile) {
		return view.getApplication().getSettings().SHOW_START_FINISH_ICONS.get();
		/*
		if (hasTrackDrawInfoForTrack(gpxFile)) {
			return trackDrawInfo.isShowStartFinish();
		} else if (gpxFile.showCurrentTrack) {
			return currentTrackShowStartFinishPref.get();
		} else {
			return gpxFile.isShowStartFinish();
		}
		*/
	}

	private boolean hasTrackDrawInfoForTrack(GPXFile gpxFile) {
		return trackDrawInfo != null && (trackDrawInfo.isCurrentRecording() && gpxFile.showCurrentTrack
				|| gpxFile.path.equals(trackDrawInfo.getFilePath()));
	}

	private boolean showTrackToFollow() {
		if (view.getContext() instanceof MapActivity) {
			MapActivity mapActivity = (MapActivity) view.getContext();
			OsmandApplication app = mapActivity.getMyApplication();
			MapRouteInfoMenu routeInfoMenu = mapActivity.getMapRouteInfoMenu();
			return routeInfoMenu.isVisible()
					|| app.getRoutingHelper().isFollowingMode()
					|| MapRouteInfoMenu.followTrackVisible
					|| MapRouteInfoMenu.chooseRoutesVisible
					|| MapRouteInfoMenu.waypointsVisible;
		}
		return false;
	}

	@NonNull
	private CachedTrack getCachedTrack(SelectedGpxFile selectedGpxFile) {
		String path = selectedGpxFile.getGpxFile().path;
		CachedTrack cachedTrack = segmentsCache.get(path);
		if (cachedTrack == null) {
			cachedTrack = new CachedTrack(view.getApplication(), selectedGpxFile);
			segmentsCache.put(path, cachedTrack);
		}
		return cachedTrack;
	}

	private boolean isPointVisited(WptPt o) {
		boolean visit = false;
		String visited = o.getExtensionsToRead().get("VISITED_KEY");
		if (visited != null && !visited.equals("0")) {
			visit = true;
		}
		return visit;
	}

	private List<WptPt> getListStarPoints(SelectedGpxFile g) {
		return g.getGpxFile().getPoints();
	}

	private boolean isPointHidden(SelectedGpxFile selectedGpxFile, WptPt point) {
		if (!Algorithms.isEmpty(selectedGpxFile.getHiddenGroups())) {
			return selectedGpxFile.getHiddenGroups().contains(point.category);
		} else {
			return false;
		}
	}

	private boolean calculateBelongs(int ex, int ey, int objx, int objy, int radius) {
		return (Math.abs(objx - ex) <= radius && Math.abs(objy - ey) <= radius);
	}

	public void getWptFromPoint(RotatedTileBox tb, PointF point, List<? super WptPt> res) {
		int r = (int) (getScaledTouchRadius(view.getApplication(), getDefaultRadiusPoi(tb)) * TOUCH_RADIUS_MULTIPLIER);
		int ex = (int) point.x;
		int ey = (int) point.y;
		List<SelectedGpxFile> selectedGpxFiles = new ArrayList<>(selectedGpxHelper.getSelectedGPXFiles());
		for (SelectedGpxFile g : selectedGpxFiles) {
			List<WptPt> pts = getListStarPoints(g);
			// int fcolor = g.getColor() == 0 ? clr : g.getColor();
			for (WptPt n : pts) {
				if (isPointHidden(g, n)) {
					continue;
				}
				int x = (int) tb.getPixXFromLatLon(n.lat, n.lon);
				int y = (int) tb.getPixYFromLatLon(n.lat, n.lon);
				if (calculateBelongs(ex, ey, x, y, r)) {
					res.add(n);
				}
			}
		}
	}

	public void getTracksFromPoint(RotatedTileBox tb, PointF point, List<Object> res) {
		int r = getScaledTouchRadius(view.getApplication(), getDefaultRadiusPoi(tb));
		int mx = (int) point.x;
		int my = (int) point.y;
		List<SelectedGpxFile> selectedGpxFiles = new ArrayList<>(selectedGpxHelper.getSelectedGPXFiles());
		for (SelectedGpxFile selectedGpxFile : selectedGpxFiles) {
			Pair<WptPt, WptPt> points = findPointsNearSegments(selectedGpxFile.getPointsToDisplay(), tb, r, mx, my);
			if (points != null) {
				LatLon latLon = tb.getLatLonFromPixel(mx, my);
				res.add(createSelectedGpxPoint(selectedGpxFile, points.first, points.second, latLon));
			}
		}
	}

	private Pair<WptPt, WptPt> findPointsNearSegments(List<TrkSegment> segments, RotatedTileBox tileBox,
													  int radius, int x, int y) {
		for (TrkSegment segment : segments) {
			QuadRect trackBounds = GPXUtilities.calculateBounds(segment.points);
			if (QuadRect.trivialOverlap(tileBox.getLatLonBounds(), trackBounds)) {
				Pair<WptPt, WptPt> points = findPointsNearSegment(tileBox, segment.points, radius, x, y);
				if (points != null) {
					return points;
				}
			}
		}
		return null;
	}

	@Nullable
	public static Pair<WptPt, WptPt> findPointsNearSegment(RotatedTileBox tb, List<WptPt> points, int r, int mx, int my) {
		if (Algorithms.isEmpty(points)) {
			return null;
		}
		WptPt prevPoint = points.get(0);
		int ppx = (int) tb.getPixXFromLatLon(prevPoint.lat, prevPoint.lon);
		int ppy = (int) tb.getPixYFromLatLon(prevPoint.lat, prevPoint.lon);
		int pcross = placeInBbox(ppx, ppy, mx, my, r, r);

		for (int i = 1; i < points.size(); i++) {
			WptPt point = points.get(i);
			int px = (int) tb.getPixXFromLatLon(point.lat, point.lon);
			int py = (int) tb.getPixYFromLatLon(point.lat, point.lon);
			int cross = placeInBbox(px, py, mx, my, r, r);
			if (cross == 0) {
				return new Pair<>(prevPoint, point);
			}
			if ((pcross & cross) == 0) {
				int mpx = px;
				int mpy = py;
				int mcross = cross;
				while (Math.abs(mpx - ppx) > r || Math.abs(mpy - ppy) > r) {
					int mpxnew = mpx / 2 + ppx / 2;
					int mpynew = mpy / 2 + ppy / 2;
					int mcrossnew = placeInBbox(mpxnew, mpynew, mx, my, r, r);
					if (mcrossnew == 0) {
						return new Pair<>(prevPoint, point);
					}
					if ((mcrossnew & mcross) != 0) {
						mpx = mpxnew;
						mpy = mpynew;
						mcross = mcrossnew;
					} else if ((mcrossnew & pcross) != 0) {
						ppx = mpxnew;
						ppy = mpynew;
						pcross = mcrossnew;
					} else {
						// this should never happen theoretically
						break;
					}
				}
			}
			pcross = cross;
			ppx = px;
			ppy = py;
			prevPoint = point;
		}
		return null;
	}

	private SelectedGpxPoint createSelectedGpxPoint(SelectedGpxFile selectedGpxFile, WptPt prevPoint, WptPt nextPoint, LatLon latLon) {
		WptPt projectionPoint = createProjectionPoint(prevPoint, nextPoint, latLon);

		Location prevPointLocation = new Location("");
		prevPointLocation.setLatitude(prevPoint.lat);
		prevPointLocation.setLongitude(prevPoint.lon);

		Location nextPointLocation = new Location("");
		nextPointLocation.setLatitude(nextPoint.lat);
		nextPointLocation.setLongitude(nextPoint.lon);

		float bearing = prevPointLocation.bearingTo(nextPointLocation);

		return new SelectedGpxPoint(selectedGpxFile, projectionPoint, prevPoint, nextPoint, bearing);
	}

	public static WptPt createProjectionPoint(WptPt prevPoint, WptPt nextPoint, LatLon latLon) {
		LatLon projection = MapUtils.getProjection(latLon.getLatitude(), latLon.getLongitude(), prevPoint.lat, prevPoint.lon, nextPoint.lat, nextPoint.lon);

		WptPt projectionPoint = new WptPt();
		projectionPoint.lat = projection.getLatitude();
		projectionPoint.lon = projection.getLongitude();
		projectionPoint.heading = prevPoint.heading;
		projectionPoint.distance = prevPoint.distance + MapUtils.getDistance(projection, prevPoint.lat, prevPoint.lon);
		projectionPoint.ele = getValueByDistInterpolation(projectionPoint.distance, prevPoint.distance, prevPoint.ele, nextPoint.distance, nextPoint.ele);
		projectionPoint.speed = getValueByDistInterpolation(projectionPoint.distance, prevPoint.distance, prevPoint.speed, nextPoint.distance, nextPoint.speed);
		if (prevPoint.time != 0 && nextPoint.time != 0) {
			projectionPoint.time = (long) getValueByDistInterpolation(projectionPoint.distance, prevPoint.distance, prevPoint.time, nextPoint.distance, nextPoint.time);
		}

		return projectionPoint;
	}

	private static double getValueByDistInterpolation(double projectionDist, double prevDist, double prevVal, double nextDist, double nextVal) {
		return prevVal + (projectionDist - prevDist) * ((nextVal - prevVal) / (nextDist - prevDist));
	}

	private static int placeInBbox(int x, int y, int mx, int my, int halfw, int halfh) {
		int cross = 0;
		cross |= (x < mx - halfw ? 1 : 0);
		cross |= (x > mx + halfw ? 2 : 0);
		cross |= (y < my - halfh ? 4 : 0);
		cross |= (y > my + halfh ? 8 : 0);
		return cross;
	}

	@Override
	public PointDescription getObjectName(Object o) {
		if (o instanceof WptPt) {
			return new PointDescription(PointDescription.POINT_TYPE_WPT, ((WptPt) o).name);
		} else if (o instanceof SelectedGpxPoint) {
			SelectedGpxFile selectedGpxFile = ((SelectedGpxPoint) o).getSelectedGpxFile();
			String name;
			if (selectedGpxFile.isShowCurrentTrack()) {
				name = view.getContext().getString(R.string.shared_string_currently_recording_track);
			} else {
				name = formatName(Algorithms.getFileWithoutDirs(selectedGpxFile.getGpxFile().path));
			}
			return new PointDescription(PointDescription.POINT_TYPE_GPX, name);
		} else if (o instanceof Pair && ((Pair<?, ?>) o).first instanceof TravelGpx) {
			TravelGpx travelGpx = (TravelGpx) ((Pair<?, ?>) o).first;
			return new PointDescription(PointDescription.POINT_TYPE_GPX, travelGpx.getTitle());
		}
		return null;
	}

	private String formatName(String name) {
		int ext = name.lastIndexOf('.');
		if (ext != -1) {
			name = name.substring(0, ext);
		}
		return name.replace('_', ' ');
	}

	private void removeCachedUnselectedTracks(List<SelectedGpxFile> selectedGpxFiles) {
		Set<String> cachedTracksPaths = segmentsCache.keySet();
		List<String> selectedTracksPaths = new ArrayList<>();
		for (SelectedGpxFile gpx : selectedGpxFiles) {
			selectedTracksPaths.add(gpx.getGpxFile().path);
		}
		for (Iterator<String> iterator = cachedTracksPaths.iterator(); iterator.hasNext(); ) {
			if (!selectedTracksPaths.contains(iterator.next())) {
				iterator.remove();
			}
		}
	}

	@Override
	public boolean disableSingleTap() {
		return isInTrackAppearanceMode();
	}

	@Override
	public boolean disableLongPressOnMap(PointF point, RotatedTileBox tileBox) {
		if (isInTrackAppearanceMode()) {
			return true;
		}
		if (tileBox.getZoom() >= START_ZOOM) {
			List<Object> res = new ArrayList<>();
			getTracksFromPoint(tileBox, point, res);
			return !Algorithms.isEmpty(res);
		}
		return false;
	}

	@Override
	public boolean isObjectClickable(Object o) {
		return o instanceof WptPt || o instanceof SelectedGpxFile;
	}

	@Override
	public boolean runExclusiveAction(Object object, boolean unknownLocation) {
		return false;
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> res, boolean unknownLocation) {
		if (tileBox.getZoom() >= START_ZOOM) {
			getWptFromPoint(tileBox, point, res);
			getTracksFromPoint(tileBox, point, res);
		}
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		if (o instanceof WptPt) {
			return new LatLon(((WptPt) o).lat, ((WptPt) o).lon);
		} else if (o instanceof SelectedGpxPoint) {
			WptPt point = ((SelectedGpxPoint) o).getSelectedPoint();
			return new LatLon(point.lat, point.lon);
		} else if (o instanceof Pair && ((Pair<?, ?>) o).second instanceof SelectedGpxPoint) {
			WptPt point = ((SelectedGpxPoint) ((Pair<?, ?>) o).second).getSelectedPoint();
			return new LatLon(point.lat, point.lon);
		}
		return null;
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
		if (tileBox.getZoom() >= START_ZOOM) {
			List<Object> trackPoints = new ArrayList<>();
			getTracksFromPoint(tileBox, point, trackPoints);

			if (!Algorithms.isEmpty(trackPoints)) {
				MapActivity mapActivity = (MapActivity) view.getContext();
				LatLon latLon = tileBox.getLatLonFromPixel(point.x, point.y);
				ContextMenuLayer contextMenuLayer = mapActivity.getMapLayers().getContextMenuLayer();
				if (trackPoints.size() == 1) {
					SelectedGpxPoint gpxPoint = (SelectedGpxPoint) trackPoints.get(0);
					contextMenuLayer.showContextMenu(latLon, getObjectName(gpxPoint), gpxPoint, this);
				} else if (trackPoints.size() > 1) {
					Map<Object, IContextMenuProvider> selectedObjects = new HashMap<>();
					for (Object object : trackPoints) {
						selectedObjects.put(object, this);
					}
					contextMenuLayer.showContextMenuForSelectedObjects(latLon, selectedObjects);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean showMenuAction(@Nullable Object object) {
		OsmandApplication app = view.getApplication();
		MapActivity mapActivity = (MapActivity) view.getContext();
		if (object instanceof Pair && ((Pair<?, ?>) object).first instanceof TravelGpx
				&& ((Pair<?, ?>) object).second instanceof SelectedGpxPoint) {
			Pair<TravelGpx, SelectedGpxPoint> pair = (Pair) object;
			LatLon latLon = new LatLon(pair.second.getSelectedPoint().lat, pair.second.getSelectedPoint().lon);
			TravelHelper travelHelper = app.getTravelHelper();
			travelHelper.openTrackMenu(pair.first, mapActivity, pair.first.getRouteId(), latLon);
			return true;
		} else if (object instanceof SelectedGpxPoint) {
			SelectedGpxPoint selectedGpxPoint = (SelectedGpxPoint) object;
			SelectedGpxFile selectedGpxFile = selectedGpxPoint.getSelectedGpxFile();
			TrackMenuFragment.showInstance(mapActivity, selectedGpxFile, selectedGpxPoint);
			return true;
		}
		return false;
	}


	@Override
	public LatLon getTextLocation(WptPt o) {
		return new LatLon(o.lat, o.lon);
	}

	@Override
	public int getTextShift(WptPt o, RotatedTileBox rb) {
		return (int) (16 * rb.getDensity());
	}

	@Override
	public String getText(WptPt o) {
		return o.name;
	}

	@Override
	public boolean isTextVisible() {
		return view.getSettings().SHOW_POI_LABEL.get();
	}

	@Override
	public boolean isFakeBoldText() {
		return false;
	}


	@Override
	public boolean isObjectMovable(Object o) {
		return o instanceof WptPt;
	}

	@Override
	public void applyNewObjectPosition(@NonNull Object o,
									   @NonNull LatLon position,
									   @Nullable final ContextMenuLayer.ApplyMovedObjectCallback callback) {
		if (o instanceof WptPt) {
			final WptPt objectInMotion = (WptPt) o;
			SelectedGpxFile selectedGpxFile = pointFileMap.get(objectInMotion);
			if (selectedGpxFile != null) {
				GPXFile gpxFile = selectedGpxFile.getGpxFile();
				gpxFile.updateWptPt(objectInMotion, position.getLatitude(),
						position.getLongitude(), System.currentTimeMillis(), objectInMotion.desc,
						objectInMotion.name, objectInMotion.category, objectInMotion.getColor(),
						objectInMotion.getIconName(), objectInMotion.getBackgroundType());
				syncGpx(gpxFile);
				if (gpxFile.showCurrentTrack) {
					if (callback != null) {
						callback.onApplyMovedObject(true, objectInMotion);
					}
				} else {
					new SaveGpxAsyncTask(new File(gpxFile.path), gpxFile, new SaveGpxAsyncTask.SaveGpxListener() {

						@Override
						public void gpxSavingStarted() {

						}

						@Override
						public void gpxSavingFinished(Exception errorMessage) {
							if (callback != null) {
								callback.onApplyMovedObject(errorMessage == null, objectInMotion);
							}
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
			}
		} else if (callback != null) {
			callback.onApplyMovedObject(false, o);
		}
	}

	private void syncGpx(GPXFile gpxFile) {
		MapMarkersGroup group = view.getApplication().getMapMarkersHelper().getMarkersGroup(gpxFile);
		if (group != null) {
			mapMarkersHelper.runSynchronization(group);
		}
	}
}