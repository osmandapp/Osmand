package net.osmand.plus;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.util.MapAlgorithms;

import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import gnu.trove.list.array.TIntArrayList;



public class RenderableConveyor extends RenderableSegment {

    static Timer t = null;
    static private int conveyor = 0;
    private double zoom = 0;

    RenderableConveyor(final OsmandMapTileView view, RenderType type, List<WptPt> pt, double param1, double param2) {
        super(view, type, pt, param1, param2);

        if (t==null) {
            t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    RenderableConveyor.conveyor++;
                    view.refreshMap();
                }
            }, 0, 200);     // 4 Hz
        }

    }

    public void recalculateRenderScale(OsmandMapTileView view, double zoom) {
        this.zoom = zoom;
        if (culled == null) {       // i.e., do NOT resample when scaling - only allow a one-off generation
            if (culler != null)
                culler.cancel(true);
            culler = new AsyncRamerDouglasPeucer(renderType, view, this, param1, param2);
            culler.execute("");
        }
    }

    public static int getComplementaryColor(int colorToInvert) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(colorToInvert), Color.green(colorToInvert),
                Color.blue(colorToInvert), hsv);
        hsv[0] = (hsv[0] + 180) % 360;
        return Color.HSVToColor(hsv);
    }

    public void drawSingleSegment(Paint p, Canvas canvas, RotatedTileBox tileBox) {

        assert (p != null);
        assert (canvas != null);

        if (culled != null) {

//            conveyor++;

            canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
            float sw = p.getStrokeWidth();
            int col = p.getColor();

            p.setStrokeWidth(sw*1.25f);
            p.setColor(getComplementaryColor(col));

            float lastx = 0;
            float lasty = 0;
            boolean first = true;
            Path path = new Path();

            int h = tileBox.getPixHeight();
            int w = tileBox.getPixWidth();

            int intp = conveyor;
            for (WptPt pt : culled) {
                intp--;

                float x = tileBox.getPixXFromLatLon(pt.lat, pt.lon);
                float y = tileBox.getPixYFromLatLon(pt.lat, pt.lon);

                if (!first &&  (isIn(x,y,w,h) || isIn(lastx,lasty,w,h)) && (intp & 15) < 3) {
                    path.moveTo(lastx, lasty);
                    path.lineTo(x, y);
                }

                first = false;
                lastx = x;
                lasty = y;
            }
            canvas.drawPath(path, p);

            canvas.rotate(tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
            p.setStrokeWidth(sw);
            p.setColor(col);
        }
    }


}
