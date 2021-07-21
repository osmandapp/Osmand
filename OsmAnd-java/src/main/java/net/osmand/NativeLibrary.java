package net.osmand;

import gnu.trove.list.array.TIntArrayList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.osmand.binary.BinaryMapRouteReaderAdapter.RouteRegion;
import net.osmand.binary.BinaryMapRouteReaderAdapter.RouteSubregion;
import net.osmand.binary.RouteDataObject;
import net.osmand.data.LatLon;
import net.osmand.data.MapObject;
import net.osmand.data.QuadRect;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.NativeTransportRoutingResult;
import net.osmand.router.RouteCalculationProgress;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RoutingContext;
import net.osmand.router.TransportRoutingConfiguration;
import net.osmand.util.Algorithms;

import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import static net.osmand.router.RoutePlannerFrontEnd.*;

import static net.osmand.IndexConstants.GPX_FILE_EXT;
import static net.osmand.IndexConstants.GPX_GZ_FILE_EXT;

public class NativeLibrary {


	public NativeLibrary() {
	}

	public static class RenderingGenerationResult {
		public RenderingGenerationResult(ByteBuffer bitmap) {
			bitmapBuffer = bitmap;
		}

		public final ByteBuffer bitmapBuffer;
	}

	public static class NativeSearchResult {

		public long nativeHandler;

		private NativeSearchResult(long nativeHandler) {
			this.nativeHandler = nativeHandler;
		}

		@Override
		protected void finalize() throws Throwable {
			deleteNativeResult();
			super.finalize();
		}

		public void deleteNativeResult() {
			if (nativeHandler != 0) {
				deleteSearchResult(nativeHandler);
				nativeHandler = 0;
			}
		}
	}

	public static class NativeRouteSearchResult {

		public long nativeHandler;
		public RouteDataObject[] objects;
		public RouteSubregion region;

		public NativeRouteSearchResult(long nativeHandler, RouteDataObject[] objects) {
			this.nativeHandler = nativeHandler;
			this.objects = objects;
		}

		@Override
		protected void finalize() throws Throwable {
			deleteNativeResult();
			super.finalize();
		}

		public void deleteNativeResult() {
			if (nativeHandler != 0) {
				deleteRouteSearchResult(nativeHandler);
				nativeHandler = 0;
			}
		}
	}

	public static class NativeDirectionPoint {
		public int x31;
		public int y31;
		public String[][] tags;
		public NativeDirectionPoint(double lat, double lon, Map<String, String> tags) {
			x31 = MapUtils.get31TileNumberX(lon);
			y31 = MapUtils.get31TileNumberY(lat);
			this.tags = new String[tags.size()][2];
			int i = 0;
			for (Map.Entry<String, String> e : tags.entrySet()) {
				this.tags[i][0] = e.getKey();
				this.tags[i][1] = e.getValue();
				i++;
			}
		}
	}

	public static class NativeGpxPointApproximation {
		public int ind;
		public double lat;
		public double lon;
		public double cumDist;
		public List<RouteSegmentResult> routeToTarget;

		NativeGpxPointApproximation(GpxPoint gpxPoint) {
			lat = gpxPoint.loc.getLatitude();
			lon = gpxPoint.loc.getLongitude();
			cumDist = gpxPoint.cumDist;
		}

		public NativeGpxPointApproximation(int ind, double lat, double lon, double cumDist) {
			this.ind = ind;
			this.lat = lat;
			this.lon = lon;
			this.cumDist = cumDist;
			routeToTarget = new ArrayList<>();
		}

		public void addRouteToTarget(RouteSegmentResult routeSegmentResult) {
			routeToTarget.add(routeSegmentResult);
		}

		public GpxPoint convertToGpxPoint() {
			GpxPoint point = new GpxPoint();
			point.ind = ind;
			point.loc = new LatLon(lat, lon);
			point.cumDist = cumDist;
			point.routeToTarget = new ArrayList<>(routeToTarget);
			return point;
		}
	}

	public static class NativeGpxRouteApproximationResult {
		public List<NativeGpxPointApproximation> finalPoints = new ArrayList<>();
		public List<RouteSegmentResult> result = new ArrayList<>();

		public NativeGpxRouteApproximationResult() {
		}

		public void addFinalPoint(NativeGpxPointApproximation finalPoint) {
			finalPoints.add(finalPoint);
		}

		public void addResultSegment(RouteSegmentResult routeSegmentResult) {
			result.add(routeSegmentResult);
		}
	}

	/**
	 * @param - must be null if there is no need to append to previous results returns native handle to results
	 */
	public NativeSearchResult searchObjectsForRendering(int sleft, int sright, int stop, int sbottom, int zoom,
	                                                    RenderingRuleSearchRequest request, boolean skipDuplicates,
	                                                    Object objectWithInterruptedField, String msgIfNothingFound) {
		int renderRouteDataFile = 0;
		if (request.searchRenderingAttribute("showRoadMapsAttribute")) {
			renderRouteDataFile = request.getIntPropertyValue(request.ALL.R_ATTR_INT_VALUE);
		}
		return new NativeSearchResult(searchNativeObjectsForRendering(sleft, sright, stop, sbottom, zoom, request, skipDuplicates,
				renderRouteDataFile, objectWithInterruptedField, msgIfNothingFound));
	}

	public RouteDataObject[] getDataObjects(NativeRouteSearchResult rs, int x31, int y31) {
		if (rs.nativeHandler == 0) {
			// do not throw exception because it is expected situation
			return new RouteDataObject[0];
		}
		return getRouteDataObjects(rs.region.routeReg, rs.nativeHandler, x31, y31);
	}

	public boolean initMapFile(String filePath, boolean useLive) {
        return initBinaryMapFile(filePath, useLive, false);
	}

	public boolean initCacheMapFile(String filePath) {
		return initCacheMapFiles(filePath);
	}

	public boolean closeMapFile(String filePath) {
		return closeBinaryMapFile(filePath);
	}

	public NativeTransportRoutingResult[] runNativePTRouting(int sx31, int sy31, int ex31, int ey31,
	                                                         TransportRoutingConfiguration cfg, RouteCalculationProgress progress) {
		return nativeTransportRouting(new int[]{sx31, sy31, ex31, ey31}, cfg, progress);
	}

	public RouteSegmentResult[] runNativeRouting(RoutingContext c, RouteRegion[] regions, boolean basemap) {
//		config.router.printRules(System.out);
		return nativeRouting(c, c.config.initialDirection == null ? -360 : c.config.initialDirection.floatValue(),
				regions, basemap);
	}

	public GpxRouteApproximation runNativeSearchGpxRoute(GpxRouteApproximation gCtx, List<GpxPoint> gpxPoints) {
		RouteRegion[] regions = gCtx.ctx.reverseMap.keySet().toArray(new RouteRegion[0]);
		int listSize = gpxPoints.size();
		NativeGpxPointApproximation[] nativePoints = new NativeGpxPointApproximation[listSize];
		for (int i = 0; i < listSize; i++) {
			nativePoints[i] = new NativeGpxPointApproximation(gpxPoints.get(i));
		}
		NativeGpxRouteApproximationResult nativeResult = nativeSearchGpxRoute(gCtx.ctx, nativePoints, regions);
		for (NativeGpxPointApproximation point : nativeResult.finalPoints) {
			gCtx.finalPoints.add(point.convertToGpxPoint());
		}
		gCtx.result.addAll(nativeResult.result);
		return gCtx;
	}

	public NativeRouteSearchResult loadRouteRegion(RouteSubregion sub, boolean loadObjects) {
		NativeRouteSearchResult lr = loadRoutingData(sub.routeReg, sub.routeReg.getName(), sub.routeReg.getFilePointer(), sub, loadObjects);
		if (lr != null && lr.nativeHandler != 0) {
			lr.region = sub;
		}
		return lr;
	}

	/**/
	protected static native NativeGpxRouteApproximationResult nativeSearchGpxRoute(RoutingContext c,
	                                                                               NativeGpxPointApproximation[] gpxPoints,
	                                                                               RouteRegion[] regions);

	protected static native NativeRouteSearchResult loadRoutingData(RouteRegion reg, String regName, int regfp, RouteSubregion subreg,
	                                                                boolean loadObjects);

	public static native void deleteNativeRoutingContext(long handle);

	protected static native void deleteRenderingContextHandle(long handle);

	protected static native void deleteRouteSearchResult(long searchResultHandle);

	protected static native RouteDataObject[] getRouteDataObjects(RouteRegion reg, long rs, int x31, int y31);

	protected static native RouteSegmentResult[] nativeRouting(RoutingContext c,  float initDirection, RouteRegion[] regions, boolean basemap);

	protected static native NativeTransportRoutingResult[] nativeTransportRouting(int[] coordinates, TransportRoutingConfiguration cfg,
																				  RouteCalculationProgress progress);

	protected static native void deleteSearchResult(long searchResultHandle);

	protected static native boolean initBinaryMapFile(String filePath, boolean useLive, boolean routingOnly);

	protected static native boolean initCacheMapFiles(String filePath);

	protected static native boolean closeBinaryMapFile(String filePath);

	protected static native void initRenderingRulesStorage(RenderingRulesStorage storage);

	protected static native RenderingGenerationResult generateRenderingIndirect(RenderingContext rc, long searchResultHandler,
			boolean isTransparent, RenderingRuleSearchRequest render, boolean encodePng);

	protected static native long searchNativeObjectsForRendering(int sleft, int sright, int stop, int sbottom, int zoom,
			RenderingRuleSearchRequest request, boolean skipDuplicates, int renderRouteDataFile, Object objectWithInterruptedField,
			String msgIfNothingFound);

	protected static native boolean initFontType(String pathToFont, String name, boolean bold, boolean italic);
	
	protected static native RenderedObject[] searchRenderedObjects(RenderingContext context, int x, int y, boolean notvisible);
	
	public RenderedObject[] searchRenderedObjectsFromContext(RenderingContext context, int x, int y) {
		return searchRenderedObjects(context, x, y, false);
	}
	
	public RenderedObject[] searchRenderedObjectsFromContext(RenderingContext context, int x, int y, boolean notvisible) {
		return searchRenderedObjects(context, x, y, notvisible);
	}

	/**/
	// Empty native impl
	/*
	 * protected static NativeRouteSearchResult loadRoutingData(RouteRegion reg, String regName, int regfp,RouteSubregion subreg, boolean
	 * loadObjects) { return null;}
	 * 
	 * protected static void deleteRouteSearchResult(long searchResultHandle) {}
	 * 
	 * protected static RouteDataObject[] getRouteDataObjects(RouteRegion reg, long rs, int x31, int y31){return null;}
	 * 
	 * protected static RouteSegmentResult[] nativeRouting(int[] coordinates, int[] state, String[] keyConfig, String[] valueConfig, float
	 * initDirection, RouteRegion[] regions, RouteCalculationProgress progress) {return null;}
	 * 
	 * protected static void deleteSearchResult(long searchResultHandle) {}
	 * 
	 * protected static boolean initBinaryMapFile(String filePath) {return false;}
	 * 
	 * protected static boolean initCacheMapFiles(String filePath) {return false;}
	 * 
	 * protected static boolean closeBinaryMapFile(String filePath) {return false;}
	 * 
	 * protected static void initRenderingRulesStorage(RenderingRulesStorage storage) {}
	 * 
	 * 
	 * protected static RenderingGenerationResult generateRenderingIndirect(RenderingContext rc, long searchResultHandler, boolean
	 * isTransparent, RenderingRuleSearchRequest render, boolean encodePng) { return null; }
	 * 
	 * protected static long searchNativeObjectsForRendering(int sleft, int sright, int stop, int sbottom, int zoom,
	 * RenderingRuleSearchRequest request, boolean skipDuplicates, int renderRouteDataFile, Object objectWithInterruptedField, String
	 * msgIfNothingFound) { return 0; }
	 * 
	 * public static void testRoutingPing() {}
	 * 
	 * public static int testNativeRouting(String obfPath, double sLat, double sLon, double eLat, double eLon) {return 0;} /*
	 */

	private static final Log log = PlatformUtil.getLog(NativeLibrary.class);
	
	
	public static boolean loadNewLib(String path) {
		return load("OsmAndJNI", path);
	}
	
	public static boolean loadOldLib(String path) {
		boolean b = true;
		b &= load("osmand", path);
		return b;
	}

	public static boolean load(String libBaseName, String path) {
		// look for a pre-installed library
		if (path != null && path.length() > 0) {
			try {
				System.load(path + "/" + System.mapLibraryName(libBaseName));
				return true;
			} catch (UnsatisfiedLinkError e) {
				log.error(e);
			} // fall through
		}

		// guess what a bundled library would be called
		String osname = System.getProperty("os.name").toLowerCase();
		String osarch = System.getProperty("os.arch");
		if (osname.startsWith("mac os")) {
			osname = "mac";
			osarch = "universal";
		}
		if (osname.startsWith("windows"))
			osname = "win";
		if (osname.startsWith("sunos"))
			osname = "solaris";
		if (osarch.startsWith("i") && osarch.endsWith("86"))
			osarch = "x86";
		String libname = libBaseName + "-" + osname + '-' + osarch + ".lib";

		// try a bundled library
		try {
			ClassLoader cl = NativeLibrary.class.getClassLoader();
			InputStream in = cl.getResourceAsStream( libname);
			if (in == null) {
				log.error("libname: " + libname + " not found");
				return false;
			}
			File tmplib = File.createTempFile(libBaseName + "-", ".lib");
			tmplib.deleteOnExit();
			OutputStream out = new FileOutputStream(tmplib);
			byte[] buf = new byte[1024];
			for (int len; (len = in.read(buf)) != -1;)
				out.write(buf, 0, len);
			in.close();
			out.close();

			System.load(tmplib.getAbsolutePath());
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage(), e);
		} catch (UnsatisfiedLinkError e) {
			log.error(e.getMessage(), e);
		} // fall through
		return false;
	}

	
	/**
     * Compares two {@code int} values.
     * @return 0 if lhs = rhs, less than 0 if lhs &lt; rhs, and greater than 0 if lhs &gt; rhs.
     * @since 1.7
     */
    public static int ccmp(int lhs, int rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }
    
	public void loadFontData(File dr) {
		File[] lf = dr.listFiles();
		if (lf == null) {
			System.err.println("No fonts loaded from " + dr.getAbsolutePath());
			return;
		}
		ArrayList<File> lst = new ArrayList<File>(Arrays.asList(lf));
		Collections.sort(lst, new Comparator<File>() {
			

			@Override
			public int compare(File arg0, File arg1) {
				return ccmp(order(arg0), order(arg1));
			}

			private int order(File a) {
				final String nm = a.getName().toLowerCase();
				boolean hasNumber = Character.isDigit(nm.charAt(0)) && Character.isDigit(nm.charAt(1));
				if (hasNumber) {
					// numeric fonts 05_NotoSans .. 65_NotoSansNastaliqUrdu
					return Integer.parseInt(nm.substring(0,1));
				} else if (nm.contains("NotoSans".toLowerCase())) {
					// downloaded fonts (e.g. NotoSans-Japanese.otf)
					return 100;
				}
				// other (e.g. DroidSansFallback.ttf for Chinese and Japanese)
				return 101;
			}
		});
		for (File f : lst) {
			final String name = f.getName();
			if (!name.endsWith(".ttf") && !name.endsWith(".otf")) {
				continue;
			}
			if (name.contains("Roboto".toLowerCase())) {
				// Roboto-Regular.ttf Roboto-Medium.ttf used in Android UI only
				continue;
			}
			initFontType(f.getAbsolutePath(), name.substring(0, name.length() - 4), name.toLowerCase().contains("bold"),
					name.toLowerCase().contains("italic"));
		}
		
	}
	


	public static class RenderedObject extends MapObject {
		private Map<String, String> tags = new LinkedHashMap<>();
		private QuadRect bbox = new QuadRect();
		private TIntArrayList x = new TIntArrayList();
		private TIntArrayList y = new TIntArrayList();
		private String iconRes;
		private int order;
		private boolean visible;
		private boolean drawOnPath;
		private LatLon labelLatLon;
		private int labelX = 0;
		private int labelY = 0;

		public Map<String, String> getTags() {
			return tags;
		}

		public String getTagValue(String tag) {
			return getTags().get(tag);
		}
		
		public boolean isText() {
			return !getName().isEmpty();
		}
		
		public int getOrder() {
			return order;
		}
		
		public void setLabelLatLon(LatLon labelLatLon) {
			this.labelLatLon = labelLatLon;
		}
		
		public LatLon getLabelLatLon() {
			return labelLatLon;
		}
		
		public void setOrder(int order) {
			this.order = order;
		}
		
		public void addLocation(int x, int y) {
			this.x.add(x);
			this.y.add(y);
		}
		
		public TIntArrayList getX() {
			return x;
		}
		
		public String getIconRes() {
			return iconRes;
		}
		
		public void setIconRes(String iconRes) {
			this.iconRes = iconRes;
		}

		public void setVisible(boolean visible) {
			this.visible = visible;
		}

		public boolean isVisible() {
			return visible;
		}

		public void setDrawOnPath(boolean drawOnPath) {
			this.drawOnPath = drawOnPath;
		}

		public boolean isDrawOnPath() {
			return drawOnPath;
		}

		public TIntArrayList getY() {
			return y;
		}

		public void setBbox(int left, int top, int right, int bottom) {
			bbox = new QuadRect(left, top, right, bottom);
		}

		public QuadRect getBbox() {
			return bbox;
		}

		public void setNativeId(long id) {
			setId(id);
		}

		public void putTag(String t, String v) {
			tags.put(t, v);
		}

		public int getLabelX() {
			return labelX;
		}

		public int getLabelY() {
			return labelY;
		}

		public void setLabelX(int labelX) {
			this.labelX = labelX;
		}

		public void setLabelY(int labelY) {
			this.labelY = labelY;
		}

		public List<String> getOriginalNames() {
			List<String> names = new ArrayList<>();
			if (!Algorithms.isEmpty(name)) {
				names.add(name);
			}
			for (Map.Entry<String, String> entry : tags.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if ((key.startsWith("name:") || key.equals("name")) && !value.isEmpty()) {
					names.add(value);
				}
			}
			return names;
		}

		public String getRouteID() {
			for (Map.Entry<String, String> entry : getTags().entrySet()) {
				if ("route_id".equals(entry.getKey())) {
					return entry.getValue();
				}
			}
			return null;
		}

		public String getGpxFileName() {
			for (String name : getOriginalNames()) {
				if (name.endsWith(GPX_FILE_EXT) || name.endsWith(GPX_GZ_FILE_EXT)) {
					return name;
				}
			}
			return null;
		}
	}
}
