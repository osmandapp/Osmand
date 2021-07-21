package net.osmand.plus.routing;

import android.graphics.Rect;
import android.os.Bundle;

import net.osmand.util.Algorithms;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PreviewRouteLineInfo {

	private static final String CUSTOM_COLOR_DAY = "custom_color_day";
	private static final String CUSTOM_COLOR_NIGHT = "custom_color_night";
	private static final String ROUTE_COLORING_TYPE = "route_coloring_type";
	private static final String LINE_WIDTH = "line_width";
	private static final String NAVIGATION_ICON_ID = "navigation_icon_id";
	private static final String NAVIGATION_ICON_COLOR = "navigation_icon_color";
	private static final String LINE_BOUNDS = "line_bounds";
	private static final String CENTER_X = "center_x";
	private static final String CENTER_Y = "center_y";
	private static final String SCREEN_HEIGHT = "screen_height";

	// parameters to save
	@ColorInt
	private int customColorDay;
	@ColorInt
	private int customColorNight;
	private ColoringType coloringType = ColoringType.DEFAULT;
	private String routeInfoAttribute;
	private String width;

	// temporally parameters to show in preview
	@ColorInt
	private int iconColor;
	@DrawableRes
	private int iconId;
	private Rect lineBounds;
	private int centerX;
	private int centerY;
	private int screenHeight;

	public PreviewRouteLineInfo(@ColorInt int customColorDay,
	                            @ColorInt int customColorNight,
	                            @NonNull ColoringType coloringType,
	                            @Nullable String routeInfoAttribute,
	                            @Nullable String width) {
		this.customColorDay = customColorDay;
		this.customColorNight = customColorNight;
		this.coloringType = coloringType;
		this.routeInfoAttribute = routeInfoAttribute;
		this.width = width;
	}

	public PreviewRouteLineInfo(@NonNull Bundle bundle) {
		readBundle(bundle);
	}

	public PreviewRouteLineInfo(@NonNull PreviewRouteLineInfo existed) {
		this.customColorDay = existed.customColorDay;
		this.customColorNight = existed.customColorNight;
		this.coloringType = existed.coloringType;
		this.routeInfoAttribute = existed.routeInfoAttribute;
		this.width = existed.width;
		this.iconId = existed.iconId;
		this.iconColor = existed.iconColor;
		this.lineBounds = existed.lineBounds;
		this.centerX = existed.centerX;
		this.centerY = existed.centerY;
		this.screenHeight = existed.screenHeight;
	}

	public void setCustomColor(@ColorInt int color, boolean nightMode) {
		if (nightMode) {
			customColorNight = color;
		} else {
			customColorDay = color;
		}
	}

	public void setRouteColoringType(@NonNull ColoringType coloringType) {
		this.coloringType = coloringType;
	}

	public void setRouteInfoAttribute(@Nullable String routeInfoAttribute) {
		this.routeInfoAttribute = routeInfoAttribute;
	}

	public void setWidth(@Nullable String width) {
		this.width = width;
	}

	public void setIconId(int iconId) {
		this.iconId = iconId;
	}

	public void setIconColor(int iconColor) {
		this.iconColor = iconColor;
	}

	public void setLineBounds(Rect lineBounds) {
		this.lineBounds = lineBounds;
	}

	public void setCenterX(int centerX) {
		this.centerX = centerX;
	}

	public void setCenterY(int centerY) {
		this.centerY = centerY;
	}

	public void setScreenHeight(int screenHeight) {
		this.screenHeight = screenHeight;
	}

	public int getCustomColor(boolean nightMode) {
		return nightMode ? customColorNight : customColorDay;
	}

	@NonNull
	public ColoringType getRouteColoringType() {
		return coloringType;
	}

	@Nullable
	public String getRouteInfoAttribute() {
		return routeInfoAttribute;
	}

	@Nullable
	public String getWidth() {
		return width;
	}

	public int getIconId() {
		return iconId;
	}

	@ColorInt
	public int getIconColor() {
		return iconColor;
	}

	public Rect getLineBounds() {
		return lineBounds;
	}

	public int getCenterX() {
		return centerX;
	}

	public int getCenterY() {
		return centerY;
	}

	public int getScreenHeight() {
		return screenHeight;
	}

	private void readBundle(@NonNull Bundle bundle) {
		if (bundle.containsKey(CUSTOM_COLOR_DAY)) {
			customColorDay = bundle.getInt(CUSTOM_COLOR_DAY);
		}
		if (bundle.containsKey(CUSTOM_COLOR_NIGHT)) {
			customColorNight = bundle.getInt(CUSTOM_COLOR_NIGHT);
		}
		coloringType = ColoringType.getRouteColoringTypeByName(bundle.getString(ROUTE_COLORING_TYPE));
		routeInfoAttribute = ColoringType.getRouteInfoAttribute(bundle.getString(ROUTE_COLORING_TYPE));
		width = bundle.getString(LINE_WIDTH);
		iconId = bundle.getInt(NAVIGATION_ICON_ID);
		iconColor = bundle.getInt(NAVIGATION_ICON_COLOR);
		lineBounds = bundle.getParcelable(LINE_BOUNDS);
		centerX = bundle.getInt(CENTER_X);
		centerY = bundle.getInt(CENTER_Y);
		screenHeight = bundle.getInt(SCREEN_HEIGHT);
	}

	public void saveToBundle(@NonNull Bundle bundle) {
		bundle.putInt(CUSTOM_COLOR_DAY, customColorDay);
		bundle.putInt(CUSTOM_COLOR_NIGHT, customColorNight);
		bundle.putString(ROUTE_COLORING_TYPE, coloringType.getName(routeInfoAttribute));
		if (width != null) {
			bundle.putString(LINE_WIDTH, width);
		}
		bundle.putInt(NAVIGATION_ICON_ID, iconId);
		bundle.putInt(NAVIGATION_ICON_COLOR, iconColor);
		bundle.putParcelable(LINE_BOUNDS, lineBounds);
		bundle.putInt(CENTER_X, centerX);
		bundle.putInt(CENTER_Y, centerY);
		bundle.putInt(SCREEN_HEIGHT, screenHeight);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PreviewRouteLineInfo)) return false;

		PreviewRouteLineInfo that = (PreviewRouteLineInfo) o;

		if (getCustomColor(false) != that.getCustomColor(false)) return false;
		if (getCustomColor(true) != that.getCustomColor(true)) return false;
		if (!Algorithms.objectEquals(coloringType, that.coloringType)) return false;
		if (!Algorithms.objectEquals(routeInfoAttribute, that.routeInfoAttribute)) return false;
		return Algorithms.objectEquals(width, that.width);
	}

	@Override
	public int hashCode() {
		int result = customColorDay;
		result = 31 * result + customColorNight;
		result = 31 * result + coloringType.ordinal();
		result = 31 * result + (routeInfoAttribute != null ? routeInfoAttribute.hashCode() : 0);
		result = 31 * result + (width != null ? width.hashCode() : 0);
		return result;
	}
}