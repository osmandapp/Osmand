package net.osmand.plus.settings.backend.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.GPXUtilities.GPXTrackAnalysis;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.util.Algorithms;

import org.json.JSONException;
import org.json.JSONObject;

public class GpxAppearanceInfo {

	public String width;
	public GradientScaleType scaleType;
	public int color;
	public int[] gradientSpeedPalette;
	public int[] gradientAltitudePalette;
	public int[] gradientSlopePalette;
	public int splitType;
	public double splitInterval;
	public boolean showArrows;
	public boolean showStartFinish;

	public long timeSpan;
	public int wptPoints;
	public float totalDistance;

	public GpxAppearanceInfo() {

	}

	public GpxAppearanceInfo(@NonNull GpxDataItem dataItem) {
		color = dataItem.getColor();
		width = dataItem.getWidth();
		showArrows = dataItem.isShowArrows();
		showStartFinish = dataItem.isShowStartFinish();
		splitType = dataItem.getSplitType();
		splitInterval = dataItem.getSplitInterval();
		scaleType = dataItem.getGradientScaleType();
		gradientSpeedPalette = dataItem.getGradientSpeedPalette();
		gradientSlopePalette = dataItem.getGradientSlopePalette();
		gradientAltitudePalette = dataItem.getGradientAltitudePalette();

		GPXTrackAnalysis analysis = dataItem.getAnalysis();
		if (analysis != null) {
			timeSpan = analysis.timeSpan;
			wptPoints = analysis.wptPoints;
			totalDistance = analysis.totalDistance;
		}
	}

	public void toJson(@NonNull JSONObject json) throws JSONException {
		writeParam(json, "color", color);
		writeParam(json, "width", width);
		writeParam(json, "show_arrows", showArrows);
		writeParam(json, "show_start_finish", showStartFinish);
		writeParam(json, "split_type", GpxSplitType.getSplitTypeByTypeId(splitType).getTypeName());
		writeParam(json, "split_interval", splitInterval);
		writeParam(json, "gradient_scale_type", scaleType);
		writeParam(json, GradientScaleType.SPEED.getColorTypeName(),
				Algorithms.gradientPaletteToString(gradientSpeedPalette, GradientScaleType.SPEED.getColorTypeName()));
		writeParam(json, GradientScaleType.ALTITUDE.getColorTypeName(),
				Algorithms.gradientPaletteToString(gradientAltitudePalette, GradientScaleType.ALTITUDE.getColorTypeName()));
		writeParam(json, GradientScaleType.SLOPE.getColorTypeName(),
				Algorithms.gradientPaletteToString(gradientSlopePalette, GradientScaleType.SLOPE.getColorTypeName()));

		writeParam(json, "time_span", timeSpan);
		writeParam(json, "wpt_points", wptPoints);
		writeParam(json, "total_distance", totalDistance);
	}

	public static GpxAppearanceInfo fromJson(@NonNull JSONObject json) {
		GpxAppearanceInfo gpxAppearanceInfo = new GpxAppearanceInfo();
		boolean hasAnyParam = json.has("color");
		gpxAppearanceInfo.color = json.optInt("color");
		hasAnyParam |= json.has("width");
		gpxAppearanceInfo.width = json.optString("width");
		hasAnyParam |= json.has("show_arrows");
		gpxAppearanceInfo.showArrows = json.optBoolean("show_arrows");
		hasAnyParam |= json.has("show_start_finish");
		gpxAppearanceInfo.showStartFinish = json.optBoolean("show_start_finish");
		hasAnyParam |= json.has("split_type");
		gpxAppearanceInfo.splitType = GpxSplitType.getSplitTypeByName(json.optString("split_type")).getType();
		hasAnyParam |= json.has("split_interval");
		gpxAppearanceInfo.splitInterval = json.optDouble("split_interval");
		hasAnyParam |= json.has("gradient_scale_type");
		gpxAppearanceInfo.scaleType = getScaleType(json.optString("gradient_scale_type"));
		hasAnyParam |= json.has(GradientScaleType.SPEED.getColorTypeName());
		gpxAppearanceInfo.gradientSpeedPalette = getGradientPalette(json, GradientScaleType.SPEED);
		hasAnyParam |= json.has(GradientScaleType.ALTITUDE.getColorTypeName());
		gpxAppearanceInfo.gradientAltitudePalette = getGradientPalette(json, GradientScaleType.ALTITUDE);
		hasAnyParam |= json.has(GradientScaleType.SLOPE.getColorTypeName());
		gpxAppearanceInfo.gradientSlopePalette = getGradientPalette(json, GradientScaleType.SLOPE);

		hasAnyParam |= json.has("time_span");
		gpxAppearanceInfo.timeSpan = json.optLong("time_span");
		hasAnyParam |= json.has("wpt_points");
		gpxAppearanceInfo.wptPoints = json.optInt("wpt_points");
		hasAnyParam |= json.has("total_distance");
		gpxAppearanceInfo.totalDistance = (float) json.optDouble("total_distance");

		return hasAnyParam ? gpxAppearanceInfo : null;
	}

	private static GradientScaleType getScaleType(String name) {
		if (!Algorithms.isEmpty(name)) {
			try {
				return GradientScaleType.valueOf(name);
			} catch (IllegalStateException e) {
				SettingsHelper.LOG.error("Failed to read gradientScaleType", e);
			}
		}
		return null;
	}

	private static int[] getGradientPalette(@NonNull JSONObject json, @NonNull GradientScaleType scaleType) {
		return Algorithms.stringToGradientPalette(json.optString(scaleType.getColorTypeName()), scaleType.getColorTypeName());
	}

	private static void writeParam(@NonNull JSONObject json, @NonNull String name, @Nullable Object value) throws JSONException {
		if (value instanceof Integer) {
			if ((Integer) value != 0) {
				json.putOpt(name, value);
			}
		} else if (value instanceof Long) {
			if ((Long) value != 0) {
				json.putOpt(name, value);
			}
		} else if (value instanceof Double) {
			if ((Double) value != 0.0 && !Double.isNaN((Double) value)) {
				json.putOpt(name, value);
			}
		} else if (value instanceof String) {
			if (!Algorithms.isEmpty((String) value)) {
				json.putOpt(name, value);
			}
		} else if (value != null) {
			json.putOpt(name, value);
		}
	}
}