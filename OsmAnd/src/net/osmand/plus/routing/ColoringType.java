package net.osmand.plus.routing;

import android.content.Context;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.Location;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteExporter;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RouteStatisticsHelper;
import net.osmand.router.RouteStatisticsHelper.RouteStatistics;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public enum ColoringType {

	// For route only
	DEFAULT("default", R.string.map_widget_renderer, R.drawable.ic_action_map_style),
	CUSTOM_COLOR("custom_color", R.string.shared_string_custom, R.drawable.ic_action_settings),
	// For gpx track only
	TRACK_SOLID("solid", R.string.track_coloring_solid, R.drawable.ic_action_circle),
	SPEED("speed", R.string.map_widget_speed, R.drawable.ic_action_speed),
	// For both route and gpx file
	ALTITUDE("altitude", R.string.altitude, R.drawable.ic_action_hillshade_dark),
	SLOPE("slope", R.string.shared_string_slope, R.drawable.ic_action_altitude_ascent),
	ATTRIBUTE("attribute", R.string.attribute, R.drawable.ic_action_hillshade_dark);

	private static final List<ColoringType> ROUTE_COLORING_TYPES = new ArrayList<>();
	private static final List<ColoringType> TRACK_COLORING_TYPES = new ArrayList<>();

	static {
		ROUTE_COLORING_TYPES.add(DEFAULT);
		ROUTE_COLORING_TYPES.add(CUSTOM_COLOR);
		ROUTE_COLORING_TYPES.add(ALTITUDE);
		ROUTE_COLORING_TYPES.add(SLOPE);
		ROUTE_COLORING_TYPES.add(ATTRIBUTE);

		TRACK_COLORING_TYPES.add(TRACK_SOLID);
		TRACK_COLORING_TYPES.add(SPEED);
		TRACK_COLORING_TYPES.add(ALTITUDE);
		TRACK_COLORING_TYPES.add(SLOPE);
		TRACK_COLORING_TYPES.add(ATTRIBUTE);
	}

	private final String name;
	@StringRes
	private final int titleId;
	@DrawableRes
	private final int iconId;

	ColoringType(String name, int titleId, int iconId) {
		this.name = name;
		this.titleId = titleId;
		this.iconId = iconId;
	}

	@Nullable
	public String getName(@Nullable String routeInfoAttribute) {
		if (!isRouteInfoAttribute()) {
			return name;
		} else {
			return Algorithms.isEmpty(routeInfoAttribute) ? null : routeInfoAttribute;
		}
	}

	@StringRes
	public int getTitleId() {
		return titleId;
	}

	@DrawableRes
	public int getIconId() {
		return iconId;
	}

	@NonNull
	public String getHumanString(@NonNull Context context, @Nullable String routeInfoAttribute) {
		return isRouteInfoAttribute()
				? getHumanStringRouteInfoAttribute(context, routeInfoAttribute)
				: context.getString(titleId);
	}

	@NonNull
	private String getHumanStringRouteInfoAttribute(@NonNull Context context, @Nullable String routeInfoAttribute) {
		String routeInfoPrefix = RouteStatisticsHelper.ROUTE_INFO_PREFIX;
		if (!isRouteInfoAttribute() || routeInfoAttribute == null
				|| !routeInfoAttribute.startsWith(routeInfoPrefix)) {
			return "";
		}
		return AndroidUtils.getStringRouteInfoPropertyValue(context, routeInfoAttribute.replace(routeInfoPrefix, ""));
	}

	public boolean isDefault() {
		return this == DEFAULT;
	}

	public boolean isCustomColor() {
		return this == CUSTOM_COLOR;
	}

	public boolean isTrackSolid() {
		return this == TRACK_SOLID;
	}

	public boolean isSolidSingleColor() {
		return isDefault() || isCustomColor() || isTrackSolid();
	}

	public boolean isGradient() {
		return this == SPEED || this == ALTITUDE || this == SLOPE;
	}

	public boolean isRouteInfoAttribute() {
		return this == ATTRIBUTE;
	}

	public boolean isAvailableForDrawingRoute(@NonNull OsmandApplication app,
											  @NonNull RouteCalculationResult route,
	                                          @Nullable String attributeName) {
		if (isGradient()) {
			List<Location> locations = route.getImmutableAllLocations();
			for (Location location : locations) {
				if (location.hasAltitude()) {
					return true;
				}
			}
			return false;
		}

		if (isRouteInfoAttribute() && !Algorithms.isEmpty(route.getOriginalRoute())) {
			return isAttributeAvailableForDrawing(app, route.getOriginalRoute(), attributeName);
		}

		return true;
	}

	public boolean isAvailableForDrawingTrack(@NonNull OsmandApplication app,
	                                          @NonNull SelectedGpxFile selectedGpxFile,
	                                          @Nullable String attributeName) {
		if (isGradient()) {
			return selectedGpxFile.getTrackAnalysis(app)
					.isColorizationTypeAvailable(toGradientScaleType().toColorizationType());
		}

		if (isRouteInfoAttribute()) {
			List<RouteSegmentResult> routeSegments = getRouteSegmentsInTrack(selectedGpxFile.getGpxFile());
			if (Algorithms.isEmpty(routeSegments)) {
				return false;
			}
			return isAttributeAvailableForDrawing(app, routeSegments, attributeName);
		}

		return true;
	}

	@Nullable
	private List<RouteSegmentResult> getRouteSegmentsInTrack(@NonNull GPXFile gpxFile) {
		if (!RouteExporter.OSMAND_ROUTER_V2.equals(gpxFile.author)) {
			return null;
		}
		List<RouteSegmentResult> routeSegments = new ArrayList<>();
		for (int i = 0; i < gpxFile.getNonEmptyTrkSegments(false).size(); i++) {
			TrkSegment segment = gpxFile.getNonEmptyTrkSegments(false).get(i);
			if (segment.hasRoute()) {
				routeSegments.addAll(RouteProvider.parseOsmAndGPXRoute(new ArrayList<>(), gpxFile, new ArrayList<>(), i));
			}
		}
		return routeSegments;
	}

	private boolean isAttributeAvailableForDrawing(@NonNull OsmandApplication app,
	                                               @NonNull List<RouteSegmentResult> routeSegments,
	                                               @Nullable String attributeName) {
		if (Algorithms.isEmpty(routeSegments) || Algorithms.isEmpty(attributeName)) {
			return false;
		}

		RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
		RenderingRulesStorage defaultRenderer = app.getRendererRegistry().defaultRender();
		List<String> rendererAttrs = RouteStatisticsHelper
				.getRouteStatisticAttrsNames(currentRenderer, defaultRenderer, true);
		if (Algorithms.isEmpty(rendererAttrs) || !rendererAttrs.contains(attributeName)) {
			return false;
		}

		boolean night = app.getDaynightHelper().isNightModeForMapControls();
		MapRenderRepositories maps = app.getResourceManager().getRenderer();
		RenderingRuleSearchRequest currentSearchRequest =
				maps.getSearchRequestWithAppliedCustomRules(currentRenderer, night);
		RenderingRuleSearchRequest defaultSearchRequest =
				maps.getSearchRequestWithAppliedCustomRules(defaultRenderer, night);

		List<RouteStatistics> routeStatisticsList =
				RouteStatisticsHelper.calculateRouteStatistic(routeSegments,
						Collections.singletonList(attributeName), currentRenderer,
						defaultRenderer, currentSearchRequest, defaultSearchRequest);
		return !Algorithms.isEmpty(routeStatisticsList);
	}

	@Nullable
	public GradientScaleType toGradientScaleType() {
		if (this == SPEED) {
			return GradientScaleType.SPEED;
		} else if (this == ALTITUDE) {
			return GradientScaleType.ALTITUDE;
		} else if (this == SLOPE) {
			return GradientScaleType.SLOPE;
		} else {
			return null;
		}
	}

	@Nullable
	public static ColoringType fromGradientScaleType(@Nullable GradientScaleType scaleType) {
		if (scaleType == GradientScaleType.SPEED) {
			return SPEED;
		} else if (scaleType == GradientScaleType.ALTITUDE) {
			return ALTITUDE;
		} else if (scaleType == GradientScaleType.SLOPE) {
			return SLOPE;
		}
		return null;
	}

	@Nullable
	public static String getRouteInfoAttribute(@Nullable String name) {
		return !Algorithms.isEmpty(name) && name.startsWith(RouteStatisticsHelper.ROUTE_INFO_PREFIX) ?
				name : null;
	}

	@NonNull
	public static ColoringType getRouteColoringTypeByName(@Nullable String name) {
		ColoringType defined = getColoringTypeByName(ROUTE_COLORING_TYPES, name);
		return defined == null ? DEFAULT : defined;
	}

	@NonNull
	public static ColoringType getNonNullTrackColoringTypeByName(@Nullable String name) {
		ColoringType defined = getColoringTypeByName(TRACK_COLORING_TYPES, name);
		return defined == null ? TRACK_SOLID : defined;
	}

	@Nullable
	public static ColoringType getNullableTrackColoringTypeByName(@Nullable String name) {
		return getColoringTypeByName(TRACK_COLORING_TYPES, name);
	}

	@Nullable
	private static ColoringType getColoringTypeByName(List<ColoringType> from, @Nullable String name) {
		if (!Algorithms.isEmpty(getRouteInfoAttribute(name))) {
			return ATTRIBUTE;
		}
		for (ColoringType coloringType : from) {
			if (coloringType.name.equalsIgnoreCase(name) && coloringType != ATTRIBUTE) {
				return coloringType;
			}
		}
		return null;
	}

	public static List<ColoringType> getRouteColoringTypes() {
		return Collections.unmodifiableList(ROUTE_COLORING_TYPES);
	}

	public static List<ColoringType> getTrackColoringTypes() {
		return Collections.unmodifiableList(TRACK_COLORING_TYPES);
	}
}