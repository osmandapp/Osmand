package net.osmand.plus.views.layers.geometry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.render.RenderingIcons;
import net.osmand.plus.views.OsmandMapLayer.RenderingLineAttributes;

public abstract class GeometryWayContext {

	public static final int DEFAULT_SIMPLIFICATION_ZOOM = 16;

	private final Context ctx;
	private final float density;
	private boolean nightMode;
	private int simplificationZoom = DEFAULT_SIMPLIFICATION_ZOOM;

	private final Paint paintIcon;
	private final Paint paintIconCustom;

	private RenderingLineAttributes attrs;

	private final Bitmap arrowBitmap;

	public GeometryWayContext(Context ctx, float density) {
		this.ctx = ctx;
		this.density = density;

		paintIcon = new Paint();
		paintIcon.setFilterBitmap(true);
		paintIcon.setAntiAlias(true);
		paintIcon.setColor(Color.BLACK);
		paintIcon.setStrokeWidth(1f * density);

		paintIconCustom = new Paint();
		paintIconCustom.setFilterBitmap(true);
		paintIconCustom.setAntiAlias(true);
		paintIconCustom.setColor(Color.BLACK);
		paintIconCustom.setStrokeWidth(1f * density);

		arrowBitmap = RenderingIcons.getBitmapFromVectorDrawable(ctx, getArrowBitmapResId());
	}

	public OsmandApplication getApp() {
		return (OsmandApplication) ctx.getApplicationContext();
	}

	public Context getCtx() {
		return ctx;
	}

	public RenderingLineAttributes getAttrs() {
		return attrs;
	}

	public float getDensity() {
		return density;
	}

	public boolean isNightMode() {
		return nightMode;
	}

	public int getSimplificationZoom() {
		return simplificationZoom;
	}

	public void setSimplificationZoom(int simplificationZoom) {
		this.simplificationZoom = simplificationZoom;
	}

	@DrawableRes
	protected abstract int getArrowBitmapResId();

	public void updatePaints(boolean nightMode, @NonNull RenderingLineAttributes attrs) {
		this.attrs = attrs;
		paintIcon.setColorFilter(new PorterDuffColorFilter(attrs.paint2.getColor(), PorterDuff.Mode.MULTIPLY));
		this.nightMode = nightMode;
		recreateBitmapsInternal();
	}

	protected boolean hasAttrs() {
		return attrs != null;
	}

	protected void recreateBitmaps() {
	}

	private void recreateBitmapsInternal() {
		if (hasAttrs()) {
			recreateBitmaps();
		}
	}

	public void clearCustomColor() {
		if (attrs != null) {
			attrs.customColor = 0;
		}
	}

	public void clearCustomShader() {
		if (attrs != null) {
			attrs.customColorPaint.setShader(null);
		}
	}

	public int getStrokeColor(int sourceColor) {
		return ColorUtils.blendARGB(sourceColor, Color.BLACK, 0.6f);
	}

	public Paint getPaintIcon() {
		return paintIcon;
	}

	public Paint getPaintIconCustom() {
		return paintIconCustom;
	}

	public Bitmap getArrowBitmap() {
		return arrowBitmap;
	}

}
