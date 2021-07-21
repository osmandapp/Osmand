package net.osmand.plus.views.layers;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.routing.PreviewRouteLineInfo;
import net.osmand.plus.views.layers.geometry.GeometryWayStyle;
import net.osmand.plus.views.layers.geometry.MultiColoringGeometryWay.GeometryGradientWayStyle;
import net.osmand.plus.views.layers.geometry.MultiColoringGeometryWay.GeometrySolidWayStyle;
import net.osmand.plus.views.layers.geometry.RouteGeometryWay;
import net.osmand.plus.views.layers.geometry.RouteGeometryWayContext;
import net.osmand.render.RenderingRule;
import net.osmand.render.RenderingRuleProperty;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteColorize;
import net.osmand.router.RouteStatisticsHelper;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import static net.osmand.render.RenderingRuleStorageProperties.ADDITIONAL;
import static net.osmand.render.RenderingRuleStorageProperties.TAG;
import static net.osmand.render.RenderingRuleStorageProperties.VALUE;

public class PreviewRouteLineLayer extends BaseRouteLayer {

	private static final Log log = PlatformUtil.getLog(PreviewRouteLineLayer.class);

	private LayerDrawable previewIcon;

	private RouteGeometryWayContext previewWayContext;
	private RouteGeometryWay previewLineGeometry;

	@Override
	protected void initGeometries(float density) {
		previewWayContext = new RouteGeometryWayContext(view.getContext(), density);
		previewWayContext.updatePaints(nightMode, attrs);
		previewLineGeometry = new RouteGeometryWay(previewWayContext);
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		if (previewRouteLineInfo != null) {

			updateRouteColoringType();
			updateAttrs(settings, tileBox);
			updateRouteColors(nightMode);

			float angle = tileBox.getRotate();
			QuadPoint c = tileBox.getCenterPixelPoint();

			canvas.rotate(-angle, c.x, c.y);
			drawRouteLinePreview(canvas, tileBox, previewRouteLineInfo);
			canvas.rotate(angle, c.x, c.y);
		}
	}

	@Override
	protected void updateAttrs(DrawSettings settings, RotatedTileBox tileBox) {
		boolean updatePaints = attrs.updatePaints(view.getApplication(), settings, tileBox);
		attrs.isPaint2 = false;
		attrs.isPaint3 = false;

		nightMode = settings != null && settings.isNightMode();

		updateIsPaint_1(updatePaints);

		if (updatePaints) {
			previewWayContext.updatePaints(nightMode, attrs);
		}
	}

	@Override
	protected void updateTurnArrowColor() {
		customTurnArrowColor = routeColoringType.isGradient() ? Color.WHITE : attrs.paint3.getColor();
		paintIconAction.setColorFilter(new PorterDuffColorFilter(customTurnArrowColor, PorterDuff.Mode.MULTIPLY));
	}

	@Override
	public void setPreviewRouteLineInfo(PreviewRouteLineInfo previewRouteLineInfo) {
		super.setPreviewRouteLineInfo(previewRouteLineInfo);
		if (previewRouteLineInfo == null) {
			previewIcon = null;
		}
	}

	private void drawRouteLinePreview(Canvas canvas,
	                                  RotatedTileBox tileBox,
	                                  PreviewRouteLineInfo previewInfo) {
		Rect previewBounds = previewInfo.getLineBounds();
		if (previewBounds == null) {
			return;
		}
		float startX = previewBounds.left;
		float startY = previewBounds.bottom;
		float endX = previewBounds.right;
		float endY = previewBounds.top;
		float centerX = previewInfo.getCenterX();
		float centerY = previewInfo.getCenterY();

		List<Float> tx = new ArrayList<>();
		List<Float> ty = new ArrayList<>();
		tx.add(startX);
		tx.add(centerX);
		tx.add(centerX);
		tx.add(endX);
		ty.add(startY);
		ty.add(startY);
		ty.add(endY);
		ty.add(endY);

		List<Double> angles = new ArrayList<>();
		List<Double> distances = new ArrayList<>();
		List<GeometryWayStyle<?>> styles = new ArrayList<>();
		previewLineGeometry.setStyleParams(getRouteLineColor(), getRouteLineWidth(tileBox),
				directionArrowsColor, routeColoringType, routeInfoAttribute);
		fillPreviewLineArrays(tx, ty, angles, distances, styles);
		canvas.rotate(+tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
		previewLineGeometry.drawRouteSegment(tileBox, canvas, tx, ty, angles, distances, 0, styles);
		canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());

		Matrix matrix = new Matrix();
		Path path = new Path();
		int lineLength = AndroidUtils.dpToPx(view.getContext(), 24);
		int offset = AndroidUtils.isLayoutRtl(view.getContext()) ? lineLength : -lineLength;
		int attrsTurnArrowColor = attrs.paint3.getColor();
		if (customTurnArrowColor != 0) {
			attrs.paint3.setColor(customTurnArrowColor);
		}
		path.moveTo(centerX + offset, startY);
		path.lineTo(centerX, startY);
		path.lineTo(centerX, startY - lineLength);
		canvas.drawPath(path, attrs.paint3);
		drawTurnArrow(canvas, matrix, centerX, startY - lineLength, centerX, startY);
		path.reset();
		path.moveTo(centerX, endY + lineLength);
		path.lineTo(centerX, endY);
		path.lineTo(centerX - offset, endY);
		canvas.drawPath(path, attrs.paint3);
		drawTurnArrow(canvas, matrix, centerX - offset, endY, centerX, endY);
		attrs.paint3.setColor(attrsTurnArrowColor);

		if (previewIcon == null) {
			previewIcon = (LayerDrawable) AppCompatResources.getDrawable(view.getContext(), previewInfo.getIconId());
			DrawableCompat.setTint(previewIcon.getDrawable(1), previewInfo.getIconColor());
		}
		canvas.rotate(-90, centerX, centerY);
		drawIcon(canvas, previewIcon, (int) centerX, (int) centerY);
		canvas.rotate(90, centerX, centerY);
	}

	private void fillPreviewLineArrays(List<Float> tx, List<Float> ty, List<Double> angles,
	                                   List<Double> distances, List<GeometryWayStyle<?>> styles) {
		fillDistancesAngles(tx, ty, angles, distances);

		if (routeColoringType.isSolidSingleColor()) {
			for (int i = 0; i < tx.size(); i++) {
				styles.add(previewLineGeometry.getDefaultWayStyle());
			}
		} else if (routeColoringType == ColoringType.ALTITUDE) {
			fillAltitudeGradientArrays(distances, styles);
		} else if (routeColoringType == ColoringType.SLOPE) {
			fillSlopeGradientArrays(tx, ty, angles, distances, styles);
		} else if (routeColoringType.isRouteInfoAttribute()) {
			boolean success = fillRouteInfoAttributeArrays(tx, ty, angles, distances, styles);
			if (!success) {
				fillSolidSingeColorArrays(tx, styles);
			}
		}
	}

	private void fillSolidSingeColorArrays(List<Float> tx, List<GeometryWayStyle<?>> styles) {
		for (int i = 0; i < tx.size(); i++) {
			styles.add(previewLineGeometry.getDefaultWayStyle());
		}
	}

	private void fillAltitudeGradientArrays(List<Double> distances, List<GeometryWayStyle<?>> styles) {
		int[] colors = RouteColorize.COLORS;
		for (int i = 1; i < distances.size(); i++) {
			RouteGeometryWay.GeometryGradientWayStyle style = previewLineGeometry.getGradientWayStyle();
			styles.add(style);
			double prevDist = distances.get(i - 1);
			double currDist = distances.get(i);
			double nextDist = i + 1 == distances.size() ? 0 : distances.get(i + 1);
			style.currColor = getPreviewColor(colors, i - 1, (prevDist + currDist / 2) / (prevDist + currDist));
			style.nextColor = getPreviewColor(colors, i, (currDist + nextDist / 2) / (currDist + nextDist));
		}
		styles.add(styles.get(styles.size() - 1));
	}

	private void fillSlopeGradientArrays(List<Float> tx, List<Float> ty, List<Double> angles,
	                                     List<Double> distances, List<GeometryWayStyle<?>> styles) {
		List<Integer> palette = new ArrayList<>();
		for (int color : RouteColorize.SLOPE_COLORS) {
			palette.add(color);
		}
		List<Double> gradientLengthsRatio = Arrays.asList(0.145833, 0.130209, 0.291031);
		List<Integer> colors = new ArrayList<>();

		fillMultiColorLineArrays(palette, gradientLengthsRatio, tx, ty, angles, distances, colors);

		for (int i = 1; i < tx.size(); i++) {
			GeometryGradientWayStyle style = previewLineGeometry.getGradientWayStyle();
			styles.add(style);
			double currDist = distances.get(i);
			double nextDist = i + 1 == distances.size() ? 0 : distances.get(i + 1);
			style.currColor = i == 1 ? colors.get(0) : ((GeometryGradientWayStyle) styles.get(i - 2)).nextColor;
			if (colors.get(i) != 0) {
				style.nextColor = colors.get(i);
			} else {
				double coeff = currDist / (currDist + nextDist);
				style.nextColor = RouteColorize.getIntermediateColor(colors.get(i - 1), colors.get(i + 1), coeff);
			}
		}
		styles.add(styles.get(styles.size() - 1));
	}

	private boolean fillRouteInfoAttributeArrays(List<Float> tx, List<Float> ty, List<Double> angles,
	                                          List<Double> distances, List<GeometryWayStyle<?>> styles) {
		List<Integer> palette = fetchColorsOfRouteInfoAttribute();
		if (Algorithms.isEmpty(palette)) {
			return false;
		}
		int ratiosAmount = palette.size() - 1;
		double lengthRatio = 1d / palette.size();
		List<Double> attributesLengthsRatio = new ArrayList<>(Collections.nCopies(ratiosAmount, lengthRatio));
		List<Integer> colors = new ArrayList<>();

		fillMultiColorLineArrays(palette, attributesLengthsRatio, tx, ty, angles, distances, colors);

		for (int i = 0; i < tx.size() - 1; i++) {
			GeometrySolidWayStyle style = previewLineGeometry.getSolidWayStyle(colors.get(i));
			styles.add(style);
		}
		styles.add(styles.get(styles.size() - 1));
		return true;
	}

	private void fillMultiColorLineArrays(List<Integer> palette, List<Double> lengthRatios,
	                                      List<Float> tx, List<Float> ty, List<Double> angles,
	                                      List<Double> distances, List<Integer> colors) {
		double totalDist = 0;
		for (Double d : distances) {
			totalDist += d;
		}

		boolean rtl = AndroidUtils.isLayoutRtl(view.getContext());
		List<Float> srcTx = new ArrayList<>(tx);
		List<Float> srcTy = new ArrayList<>(ty);
		int[] colorsArray = new int[tx.size() + lengthRatios.size()];
		colorsArray[0] = palette.get(0);
		colorsArray[colorsArray.length - 1] = palette.get(palette.size() - 1);
		double passedDist = 0;

		for (int i = 0; i < lengthRatios.size(); i++) {
			double ratio = lengthRatios.get(i);
			double length = passedDist + totalDist * ratio;
			passedDist += totalDist * ratio;
			int insertIdx;
			for (insertIdx = 1; insertIdx < distances.size() && length - distances.get(insertIdx) > 0; insertIdx++) {
				length -= distances.get(insertIdx);
			}

			float px = srcTx.get(insertIdx - 1);
			float py = srcTy.get(insertIdx - 1);
			float nx = srcTx.get(insertIdx);
			float ny = srcTy.get(insertIdx);
			float r = (float) (length / distances.get(insertIdx));
			float x = (float) Math.ceil(rtl ? px - (px - nx) * r : px + (nx - px) * r);
			float y = (float) Math.ceil(py + (ny - py) * r);
			int idx = findNextPrevPointIdx(x, y, tx, ty, !rtl);
			tx.add(idx, x);
			ty.add(idx, y);
			int color = palette.get(i + 1);
			colorsArray[idx] = color;
			if (idx - 2 >= 0 && colorsArray[idx - 1] == 0) {
				colorsArray[idx - 1] = colorsArray[idx - 2];
			}
		}

		distances.clear();
		angles.clear();
		fillDistancesAngles(tx, ty, angles, distances);

		for (int color : colorsArray) {
			colors.add(color);
		}
	}

	@NonNull
	private List<Integer> fetchColorsOfRouteInfoAttribute() {
		RenderingRulesStorage renderer = getValidRenderer();
		RenderingRuleSearchRequest request = new RenderingRuleSearchRequest(renderer);

		applyProfileFiltersToRequest(request, renderer);

		if (request.searchRenderingAttribute(routeInfoAttribute)) {
			return fetchColorsInternal(renderer, request);
		}
		return Collections.emptyList();
	}

	private RenderingRulesStorage getValidRenderer() {
		OsmandApplication app = view.getApplication();
		RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
		RenderingRulesStorage defaultRenderer = app.getRendererRegistry().defaultRender();
		if (currentRenderer == null) {
			return defaultRenderer;
		}
		for (String attributeName : currentRenderer.getRenderingAttributeNames()) {
			if (attributeName.startsWith(RouteStatisticsHelper.ROUTE_INFO_PREFIX)) {
				return currentRenderer;
			}
		}
		return defaultRenderer;
	}

	private void applyProfileFiltersToRequest(RenderingRuleSearchRequest request, RenderingRulesStorage renderer) {
		request.setBooleanFilter(renderer.PROPS.R_NIGHT_MODE, nightMode);

		SharedPreferences preferences = (SharedPreferences) view.getSettings().getProfilePreferences(getAppMode());
		for (RenderingRuleProperty property : renderer.PROPS.getCustomRules()) {
			String preferenceKey = "nrenderer_" + property.getAttrName();
			if (property.isString()) {
				request.setStringFilter(property, preferences.getString(preferenceKey, null));
			} else if (property.isBoolean()) {
				request.setBooleanFilter(property, preferences.getBoolean(preferenceKey, false));
			} else if (property.isInt() || property.isColor()) {
				request.setIntFilter(property, preferences.getInt(preferenceKey, 0));
			} else if (property.isFloat()) {
				request.setFloatFilter(property, preferences.getFloat(preferenceKey, 0));
			}
		}
	}

	private List<Integer> fetchColorsInternal(RenderingRulesStorage renderer, RenderingRuleSearchRequest request) {
		List<RenderingRule> renderingRules = renderer.getRenderingAttributeRule(routeInfoAttribute)
				.getIfElseChildren();
		if (Algorithms.isEmpty(renderingRules)) {
			Collections.emptyList();
		}

		List<Integer> colors = new ArrayList<>();
		for (RenderingRule rule : renderingRules) {
			setTagValueAdditional(renderer, request, rule);
			if (request.searchRenderingAttribute(routeInfoAttribute)) {
				String stringColor = request.getColorStringPropertyValue(renderer.PROPS.R_ATTR_COLOR_VALUE);
				int color = 0;
				try {
					color = Algorithms.parseColor(stringColor);
				} catch (IllegalArgumentException e) {
					log.error(e);
				}
				if (color != 0 && !colors.contains(color)) {
					colors.add(color);
				}
			}
		}

		return colors;
	}

	private void setTagValueAdditional(RenderingRulesStorage renderer, RenderingRuleSearchRequest request,
	                                   RenderingRule rule) {
		RenderingRuleProperty[] properties = rule.getProperties();
		if (properties == null) {
			return;
		}

		for (RenderingRuleProperty property : properties) {
			String attribute = property.getAttrName();
			if (TAG.equals(attribute)) {
				request.setStringFilter(renderer.PROPS.R_TAG, rule.getStringPropertyValue(property.getAttrName()));
			} else if (VALUE.equals(attribute)) {
				request.setStringFilter(renderer.PROPS.R_VALUE, rule.getStringPropertyValue(property.getAttrName()));
			} else if (ADDITIONAL.equals(attribute)) {
				request.setStringFilter(renderer.PROPS.R_ADDITIONAL, rule.getStringPropertyValue(property.getAttrName()));
			}
		}
	}

	private void fillDistancesAngles(List<Float> tx, List<Float> ty, List<Double> angles,
	                                 List<Double> distances) {
		angles.add(0d);
		distances.add(0d);
		for (int i = 1; i < tx.size(); i++) {
			float x = tx.get(i);
			float y = ty.get(i);
			float px = tx.get(i - 1);
			float py = ty.get(i - 1);
			double angleRad = Math.atan2(y - py, x - px);
			Double angle = (angleRad * 180 / Math.PI) + 90f;
			angles.add(angle);
			double dist = Math.sqrt((y - py) * (y - py) + (x - px) * (x - px));
			distances.add(dist);
		}
	}

	private int findNextPrevPointIdx(float x, float y, List<Float> tx, List<Float> ty, boolean next) {
		for (int i = 0; i < tx.size(); i++) {
			if (next && tx.get(i) >= x || !next && tx.get(i) <= x) {
				if (ty.get(i) == y) {
					return i;
				} else if (ty.get(i) <= y) {
					return i;
				}
			}
		}
		return tx.size() - 1;
	}

	private int getPreviewColor(int[] colors, int index, double coeff) {
		if (index == 0) {
			return colors[0];
		} else if (index > 0 && index < colors.length) {
			return RouteColorize.getIntermediateColor(colors[index - 1], colors[index], coeff);
		} else if (index == colors.length) {
			return colors[index - 1];
		}
		return 0;
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}
}