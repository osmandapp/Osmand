package net.osmand.plus.views.layers.geometry;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.views.MapTileLayer;
import net.osmand.plus.views.layers.geometry.MultiColoringGeometryWay.GeometryGradientWayStyle;
import net.osmand.util.Algorithms;

import java.util.List;

import androidx.annotation.NonNull;

public class MultiColoringGeometryWayDrawer<T extends MultiColoringGeometryWayContext>
		extends GeometryWayDrawer<T> {

	private static final int BORDER_TYPE_ZOOM_THRESHOLD = MapTileLayer.DEFAULT_MAX_ZOOM + MapTileLayer.OVERZOOM_IN;
	private static final boolean DRAW_BORDER = true;

	@NonNull
	protected ColoringType coloringType;

	public MultiColoringGeometryWayDrawer(T context) {
		super(context);
		coloringType = context.getDefaultColoringType();
	}

	public void setColoringType(@NonNull ColoringType coloringType) {
		this.coloringType = coloringType;
	}

	@Override
	protected void drawFullBorder(Canvas canvas, int zoom, List<DrawPathData> pathsData) {
		if (DRAW_BORDER && zoom < BORDER_TYPE_ZOOM_THRESHOLD && requireDrawingBorder()) {
			Path fullPath = new Path();
			for (DrawPathData data : pathsData) {
				if (data.style.color != 0) {
					fullPath.addPath(data.path);
				}
			}
			canvas.drawPath(fullPath, getContext().getBorderPaint());
		}
	}

	@Override
	public void drawPath(Canvas canvas, DrawPathData pathData) {
		Paint strokePaint = getContext().getCustomPaint();

		if (coloringType.isCustomColor() || coloringType.isTrackSolid() || coloringType.isRouteInfoAttribute()) {
			drawCustomSolid(canvas, pathData);
		} else if (coloringType.isDefault()) {
			super.drawPath(canvas, pathData);
		} else if (coloringType.isGradient()) {
			GeometryGradientWayStyle style = (GeometryGradientWayStyle) pathData.style;
			LinearGradient gradient = new LinearGradient(pathData.start.x, pathData.start.y,
					pathData.end.x, pathData.end.y, style.currColor, style.nextColor, Shader.TileMode.CLAMP);
			strokePaint.setShader(gradient);
			strokePaint.setStrokeWidth(style.width);
			strokePaint.setAlpha(0xFF);
			canvas.drawPath(pathData.path, strokePaint);
		}
	}

	protected void drawCustomSolid(Canvas canvas, DrawPathData pathData) {
		Paint paint = getContext().getCustomPaint();
		paint.setColor(pathData.style.color);
		paint.setStrokeWidth(pathData.style.width);
		canvas.drawPath(pathData.path, paint);
	}

	@Override
	protected void drawSegmentBorder(Canvas canvas, int zoom, DrawPathData pathData) {
		if (DRAW_BORDER && zoom >= BORDER_TYPE_ZOOM_THRESHOLD && requireDrawingBorder()) {
			if (pathData.style.color != 0) {
				canvas.drawPath(pathData.path, getContext().getBorderPaint());
			}
		}
	}

	private boolean requireDrawingBorder() {
		return coloringType.isGradient() || coloringType.isRouteInfoAttribute();
	}

	@Override
	protected PathPoint getArrowPathPoint(float iconx, float icony, GeometryWayStyle<?> style, double angle) {
		return new ColorDependentArrowPathPoint(iconx, icony, angle, style);
	}

	protected static class ColorDependentArrowPathPoint extends PathPoint {

		public ColorDependentArrowPathPoint(float x, float y, double angle, GeometryWayStyle<?> style) {
			super(x, y, angle, style);
		}

		@Override
		void draw(Canvas canvas, GeometryWayContext context) {
			if (shouldDrawArrow()) {
				super.draw(canvas, context);
			}
		}

		protected boolean shouldDrawArrow() {
			return !Algorithms.objectEquals(style.color, Color.TRANSPARENT);
		}
	}
}