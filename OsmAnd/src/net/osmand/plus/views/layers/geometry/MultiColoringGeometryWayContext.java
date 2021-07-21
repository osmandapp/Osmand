package net.osmand.plus.views.layers.geometry;

import android.content.Context;
import android.graphics.Paint;

import net.osmand.plus.routing.ColoringType;

import androidx.annotation.NonNull;

public abstract class MultiColoringGeometryWayContext extends GeometryWayContext {

	private static final int SHADOW_COLOR = 0x80000000;

	private final Paint borderPaint;

	public MultiColoringGeometryWayContext(Context ctx, float density) {
		super(ctx, density);
		borderPaint = createBorderPaint();
	}

	@NonNull
	public abstract Paint getCustomPaint();

	@NonNull
	public abstract Paint getDefaultPaint();

	@NonNull
	protected abstract ColoringType getDefaultColoringType();

	private Paint createBorderPaint() {
		Paint borderPaint = new Paint();
		borderPaint.setColor(SHADOW_COLOR);
		borderPaint.setAntiAlias(true);
		borderPaint.setStrokeCap(Paint.Cap.ROUND);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeJoin(Paint.Join.ROUND);
		return borderPaint;
	}

	@NonNull
	public Paint getBorderPaint() {
		return borderPaint;
	}

	public void updateBorderWidth(float routeLineWidth) {
		borderPaint.setStrokeWidth(routeLineWidth + 2 * getDensity());
	}
}