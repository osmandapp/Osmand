package net.osmand.plus.views.layers.geometry;

import android.content.Context;
import android.graphics.Paint;

import net.osmand.plus.R;
import net.osmand.plus.routing.ColoringType;

import androidx.annotation.NonNull;

public class RouteGeometryWayContext extends MultiColoringGeometryWayContext {

	public RouteGeometryWayContext(Context ctx, float density) {
		super(ctx, density);
	}

	@NonNull
	@Override
	public Paint getCustomPaint() {
		return getAttrs().customColorPaint;
	}

	@NonNull
	@Override
	public Paint getDefaultPaint() {
		return getAttrs().paint;
	}

	@NonNull
	@Override
	protected ColoringType getDefaultColoringType() {
		return ColoringType.DEFAULT;
	}

	@Override
	protected int getArrowBitmapResId() {
		return R.drawable.map_route_direction_arrow;
	}
}