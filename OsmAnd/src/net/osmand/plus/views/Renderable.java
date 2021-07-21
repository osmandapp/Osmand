package net.osmand.plus.views;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;

import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.plus.views.layers.geometry.GpxGeometryWay;
import net.osmand.router.RouteSegmentResult;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Renderable {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "Renderable #" + mCount.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<>(128);
    public static final Executor THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    public static abstract class RenderableSegment {

        private static final boolean DRAW_BORDER = true;

        protected static final int MIN_CULLER_ZOOM = 16;
        protected static final int BORDER_TYPE_ZOOM_THRESHOLD = MapTileLayer.DEFAULT_MAX_ZOOM + MapTileLayer.OVERZOOM_IN;

        public List<WptPt> points = null;                           // Original list of points
        protected List<WptPt> culled = new ArrayList<>();           // Reduced/resampled list of points
        protected int pointSize;
        protected double segmentSize;

        protected List<RouteSegmentResult> routeSegments;

        protected QuadRect trackBounds;
        protected double zoom = -1;
        protected AsynchronousResampler culler = null;                        // The currently active resampler
        protected Paint paint = null;                               // MUST be set by 'updateLocalPaint' before use
        protected Paint borderPaint;

        @NonNull
        protected ColoringType coloringType = ColoringType.TRACK_SOLID;
        protected String routeInfoAttribute = null;

        protected GpxGeometryWay geometryWay;

        public RenderableSegment(List<WptPt> points, double segmentSize) {
            this.points = points;
            this.segmentSize = segmentSize;
            trackBounds = GPXUtilities.calculateBounds(points);
        }

        protected void updateLocalPaint(Paint p) {
            if (paint == null) {
                paint = new Paint(p);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStyle(Paint.Style.STROKE);
            }
            paint.setColor(p.getColor());
            paint.setStrokeWidth(p.getStrokeWidth());
            if (coloringType.isGradient()) {
                paint.setAlpha(0xFF);
            }
        }

        public void setTrackColoringParams(@NonNull ColoringType coloringType,
                                           @Nullable String routeInfoAttribute,
                                           @NonNull Paint borderPaint) {
            this.coloringType = coloringType;
            this.routeInfoAttribute = routeInfoAttribute;
            this.borderPaint = borderPaint;
        }

        public GpxGeometryWay getGeometryWay() {
            return geometryWay;
        }

        public void setGeometryWay(GpxGeometryWay geometryWay) {
            this.geometryWay = geometryWay;
        }

        protected abstract void startCuller(double newZoom);

        protected void drawSingleSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox) {
            if (points.size() < 2) {
                return;
            }

            updateLocalPaint(p);
            canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
            if (coloringType.isGradient()) {
                if (DRAW_BORDER && zoom < BORDER_TYPE_ZOOM_THRESHOLD) {
                    drawSolid(points, borderPaint, canvas, tileBox);
                }
                drawGradient(zoom, points, paint, canvas, tileBox);
            } else {
                drawSolid(getPointsForDrawing(), paint, canvas, tileBox);
            }
            canvas.rotate(tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
        }

        public void drawSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox) {
            if (QuadRect.trivialOverlap(tileBox.getLatLonBounds(), trackBounds)) { // is visible?
                if (coloringType.isTrackSolid()) {
                    startCuller(zoom);
                }
                drawSingleSegment(zoom, p, canvas, tileBox);
            }
        }

        public void setRDP(List<WptPt> cull) {
            culled = cull;
        }

        public List<WptPt> getPointsForDrawing() {
            return culled.isEmpty() ? points : culled;
        }

        public void setRoute(List<RouteSegmentResult> routeSegments) {
            this.routeSegments = routeSegments;
        }

        public void drawGeometry(Canvas canvas, RotatedTileBox tileBox, QuadRect quadRect, int arrowColor, int trackColor, float trackWidth) {
            if (geometryWay != null) {
                List<WptPt> points = coloringType.isRouteInfoAttribute() ? this.points : getPointsForDrawing();
                if (!Algorithms.isEmpty(points)) {
                    geometryWay.setStyleParams(trackColor, trackWidth, arrowColor, coloringType, routeInfoAttribute);
                    geometryWay.updateSegment(tileBox, points, routeSegments);
                    geometryWay.drawSegments(tileBox, canvas, quadRect.top, quadRect.left, quadRect.bottom, quadRect.right, null, 0);
                }
            }
        }

        protected void drawSolid(List<WptPt> pts, Paint p, Canvas canvas, RotatedTileBox tileBox) {
            QuadRect tileBounds = tileBox.getLatLonBounds();
            WptPt lastPt = pts.get(0);
            boolean recalculateLastXY = true;
            Path path = new Path();
            for (int i = 1; i < pts.size(); i++) {
                WptPt pt = pts.get(i);
                if (arePointsInsideTile(pt, lastPt, tileBounds)) {
                    if (recalculateLastXY) {
                        recalculateLastXY = false;
                        float lastX = tileBox.getPixXFromLatLon(lastPt.lat, lastPt.lon);
                        float lastY = tileBox.getPixYFromLatLon(lastPt.lat, lastPt.lon);
                        if (!path.isEmpty()) {
                            canvas.drawPath(path, p);
                        }
                        path.reset();
                        path.moveTo(lastX, lastY);
                    }
                    float x = tileBox.getPixXFromLatLon(pt.lat, pt.lon);
                    float y = tileBox.getPixYFromLatLon(pt.lat, pt.lon);
                    path.lineTo(x, y);
                } else {
                    recalculateLastXY = true;
                }
                lastPt = pt;
            }
            if (!path.isEmpty()) {
                canvas.drawPath(path, p);
            }
        }

        protected void drawGradient(double zoom, List<WptPt> pts, Paint p, Canvas canvas, RotatedTileBox tileBox) {
            QuadRect tileBounds = tileBox.getLatLonBounds();
            boolean drawSegmentBorder = DRAW_BORDER && zoom >= BORDER_TYPE_ZOOM_THRESHOLD;
            GradientScaleType scaleType = coloringType.toGradientScaleType();
            Path path = new Path();
            boolean recalculateLastXY = true;
            WptPt lastPt = pts.get(0);

            List<PointF> gradientPoints = new ArrayList<>();
            List<Integer> gradientColors = new ArrayList<>();
            float gradientAngle = 0;

            List<Path> paths = new ArrayList<>();
            List<LinearGradient> gradients = new ArrayList<>();

            for (int i = 1; i < pts.size(); i++) {
                WptPt pt = pts.get(i);
                WptPt nextPt = i + 1 < pts.size() ? pts.get(i + 1) : null;
                float nextX = nextPt == null ? 0 : tileBox.getPixXFromLatLon(nextPt.lat, nextPt.lon);
                float nextY = nextPt == null ? 0 : tileBox.getPixYFromLatLon(nextPt.lat, nextPt.lon);
                float lastX = 0;
                float lastY = 0;
                if (arePointsInsideTile(pt, lastPt, tileBounds)) {
                    if (recalculateLastXY) {
                        recalculateLastXY = false;
                        lastX = tileBox.getPixXFromLatLon(lastPt.lat, lastPt.lon);
                        lastY = tileBox.getPixYFromLatLon(lastPt.lat, lastPt.lon);
                        if (!path.isEmpty()) {
                            paths.add(new Path(path));
                            gradients.add(createGradient(gradientPoints, gradientColors));
                        }
                        path.reset();
                        path.moveTo(lastX, lastY);

                        gradientPoints.clear();
                        gradientColors.clear();
                        gradientPoints.add(new PointF(lastX, lastY));
                        gradientColors.add(lastPt.getColor(scaleType.toColorizationType()));
                    }
                    float x = tileBox.getPixXFromLatLon(pt.lat, pt.lon);
                    float y = tileBox.getPixYFromLatLon(pt.lat, pt.lon);
                    path.lineTo(x, y);
                    gradientPoints.add(new PointF(x, y));
                    gradientColors.add(pt.getColor(scaleType.toColorizationType()));

                    if (gradientColors.size() == 2) {
                        gradientAngle = calculateAngle(lastX, lastY, x, y);
                    }
                    if (nextPt != null) {
                        float nextAngle = calculateAngle(x, y, nextX, nextY);
                        if (Math.abs(nextAngle - gradientAngle) > 20) {
                            recalculateLastXY = true;
                        }
                    }
                } else {
                    recalculateLastXY = true;
                }
                lastPt = pt;
            }
            if (!path.isEmpty()) {
                paths.add(new Path(path));
                gradients.add(createGradient(gradientPoints, gradientColors));
            }

            if (!paths.isEmpty()) {
                if (drawSegmentBorder) {
                    canvas.drawPath(paths.get(0), borderPaint);
                }
                for (int i = 0; i < paths.size(); i++) {
                    if (drawSegmentBorder && i + 1 < paths.size()) {
                        canvas.drawPath(paths.get(i + 1), borderPaint);
                    }
                    p.setShader(gradients.get(i));
                    canvas.drawPath(paths.get(i), p);
                }
            }
        }

        private LinearGradient createGradient(List<PointF> gradientPoints, List<Integer> gradientColors) {
            float gradientLength = 0;
            List<Float> pointsLength = new ArrayList<>(gradientPoints.size() - 1);
            for (int i = 1; i < gradientPoints.size(); i++) {
                PointF start = gradientPoints.get(i - 1);
                PointF end = gradientPoints.get(i);
                pointsLength.add((float) MapUtils.getSqrtDistance(start.x, start.y, end.x, end.y));
                gradientLength += pointsLength.get(i - 1);
            }

            float[] positions = new float[gradientPoints.size()];
            positions[0] = 0;
            for (int i = 1; i < gradientPoints.size(); i++) {
                positions[i] = positions[i - 1] + pointsLength.get(i - 1) / gradientLength;
            }

            int[] colors = new int[gradientColors.size()];
            for (int i = 0; i < gradientColors.size(); i++) {
                colors[i] = gradientColors.get(i);
            }

            PointF gradientStart = gradientPoints.get(0);
            PointF gradientEnd = gradientPoints.get(gradientPoints.size() - 1);
            return new LinearGradient(gradientStart.x, gradientStart.y, gradientEnd.x, gradientEnd.y,
                    colors, positions, Shader.TileMode.CLAMP);
        }

        private float calculateAngle(float startX, float startY, float endX, float endY) {
            return (float) Math.abs(Math.toDegrees(Math.atan2(endY - startY, endX - startX)));
        }

        protected boolean arePointsInsideTile(WptPt first, WptPt second, QuadRect tileBounds) {
            if (first == null || second == null) {
                return false;
            }
            return Math.min(first.lon, second.lon) < tileBounds.right && Math.max(first.lon, second.lon) > tileBounds.left
                    && Math.min(first.lat, second.lat) < tileBounds.top && Math.max(first.lat, second.lat) > tileBounds.bottom;
        }
    }

    public static class StandardTrack extends RenderableSegment {

        public StandardTrack(List<WptPt> pt, double base) {
            super(pt, base);
        }

        @Override public void startCuller(double newZoom) {

            if (zoom != newZoom) {
                if (culler != null) {
                    culler.cancel(true);
                }
                if (zoom < newZoom) {            // if line would look worse (we're zooming in) then...
                    culled.clear();              // use full-resolution until re-cull complete
                }
                zoom = newZoom;
                if (newZoom >= MIN_CULLER_ZOOM) {
                    return;
                }

                double cullDistance = Math.pow(2.0, segmentSize - zoom);    // segmentSize == epsilon
                culler = new AsynchronousResampler.RamerDouglasPeucer(this, cullDistance);
                try {
                    culler.executeOnExecutor(THREAD_POOL_EXECUTOR, "");
                } catch (RejectedExecutionException e) {
                    culler = null;
                }
            }
        }
    }

    public static class CurrentTrack extends RenderableSegment {

        public CurrentTrack(List<WptPt> pt) {
            super(pt, 0);
        }

        @Override
        public void drawSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox) {
            if (points.size() != pointSize) {
                int prevSize = pointSize;
                pointSize = points.size();
                GPXUtilities.updateBounds(trackBounds, points, prevSize);
            }
            drawSingleSegment(zoom, p, canvas, tileBox);
        }

        @Override protected void startCuller(double newZoom) {}
    }
}