package net.osmand.plus.views.layers.geometry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import net.osmand.AndroidUtils;
import net.osmand.plus.views.layers.geometry.GpxGeometryWay.GeometryArrowsStyle;

public class GpxGeometryWayDrawer extends MultiColoringGeometryWayDrawer<GpxGeometryWayContext> {

	public GpxGeometryWayDrawer(GpxGeometryWayContext context) {
		super(context);
	}

	@Override
	public void drawPath(Canvas canvas, DrawPathData pathData) {
		if (coloringType.isRouteInfoAttribute()) {
			drawCustomSolid(canvas, pathData);
		}
	}

	@Override
	protected PathPoint getArrowPathPoint(float iconx, float icony, GeometryWayStyle<?> style, double angle) {
		return new ArrowPathPoint(iconx, icony, angle, style);
	}

	private static class ArrowPathPoint extends ColorDependentArrowPathPoint {

		ArrowPathPoint(float x, float y, double angle, GeometryWayStyle<?> style) {
			super(x, y, angle, style);
		}

		@Override
		void draw(Canvas canvas, GeometryWayContext context) {
			if (style instanceof GeometryArrowsStyle && shouldDrawArrow()) {
				Context ctx = style.getCtx();
				GeometryArrowsStyle arrowsWayStyle = (GeometryArrowsStyle) style;
				Bitmap bitmap = style.getPointBitmap();
				boolean useSpecialArrow = arrowsWayStyle.useSpecialArrow();

				float newWidth = useSpecialArrow ? AndroidUtils.dpToPx(ctx, 12) : arrowsWayStyle.getTrackWidth() / 2f;
				float paintH2 = bitmap.getHeight() / 2f;
				float paintW2 = newWidth / 2f;

				Matrix matrix = getMatrix();
				matrix.reset();
				float sy = useSpecialArrow ? newWidth / bitmap.getHeight() : 1;
				matrix.postScale(newWidth / bitmap.getWidth(), sy);
				matrix.postRotate((float) angle, paintW2, paintH2);
				matrix.postTranslate(x - paintW2, y - paintH2);

				if (useSpecialArrow) {
					drawCircle(canvas, arrowsWayStyle);
				}

				Paint paint = context.getPaintIconCustom();
				Integer pointColor = style.getPointColor();
				paint.setColorFilter(new PorterDuffColorFilter(pointColor, PorterDuff.Mode.SRC_IN));
				canvas.drawBitmap(bitmap, matrix, paint);
			}
		}

		private void drawCircle(Canvas canvas, GeometryArrowsStyle style) {
			Paint paint = style.getContext().getCirclePaint();
			paint.setColor(GeometryArrowsStyle.OUTER_CIRCLE_COLOR);
			canvas.drawCircle(x, y, style.getOuterCircleRadius(), paint);
			paint.setColor(style.getTrackColor());
			canvas.drawCircle(x, y, style.getInnerCircleRadius(), paint);
		}
	}
}