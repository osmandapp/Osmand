
package net.osmand;


import net.osmand.binary.StringBundle;
import net.osmand.binary.StringBundleWriter;
import net.osmand.binary.StringBundleXmlWriter;
import net.osmand.data.QuadRect;
import net.osmand.router.RouteColorize.ColorizationType;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TimeZone;

public class GPXUtilities {

	public final static Log log = PlatformUtil.getLog(GPXUtilities.class);

	private static final String ICON_NAME_EXTENSION = "icon";
	private static final String DEFAULT_ICON_NAME = "special_star";
	private static final String BACKGROUND_TYPE_EXTENSION = "background";
	private static final String PROFILE_TYPE_EXTENSION = "profile";
	private static final String GAP_PROFILE_TYPE = "gap";
	private static final String TRKPT_INDEX_EXTENSION = "trkpt_idx";

	public final static String GPX_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"; //$NON-NLS-1$
	private final static String GPX_TIME_FORMAT_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; //$NON-NLS-1$

	private final static NumberFormat latLonFormat = new DecimalFormat("0.00#####", new DecimalFormatSymbols(
			new Locale("EN", "US")));
	// speed, ele, hdop
	private final static NumberFormat decimalFormat = new DecimalFormat("#.#", new DecimalFormatSymbols(
			new Locale("EN", "US")));

	public enum GPXColor {
		BLACK(0xFF000000),
		DARKGRAY(0xFF444444),
		GRAY(0xFF888888),
		LIGHTGRAY(0xFFCCCCCC),
		WHITE(0xFFFFFFFF),
		RED(0xFFFF0000),
		GREEN(0xFF00FF00),
		DARKGREEN(0xFF006400),
		BLUE(0xFF0000FF),
		YELLOW(0xFFFFFF00),
		CYAN(0xFF00FFFF),
		MAGENTA(0xFFFF00FF),
		AQUA(0xFF00FFFF),
		FUCHSIA(0xFFFF00FF),
		DARKGREY(0xFF444444),
		GREY(0xFF888888),
		LIGHTGREY(0xFFCCCCCC),
		LIME(0xFF00FF00),
		MAROON(0xFF800000),
		NAVY(0xFF000080),
		OLIVE(0xFF808000),
		PURPLE(0xFF800080),
		SILVER(0xFFC0C0C0),
		TEAL(0xFF008080);

		int color;

		GPXColor(int color) {
			this.color = color;
		}

		public static GPXColor getColorFromName(String s) {
			for (GPXColor c : values()) {
				if (c.name().equalsIgnoreCase(s)) {
					return c;
				}
			}
			return null;
		}
	}

	public interface GPXExtensionsWriter {
		void writeExtensions(XmlSerializer serializer);
	}

	public interface GPXExtensionsReader {
		boolean readExtensions(GPXFile res, XmlPullParser parser) throws IOException, XmlPullParserException;
	}

	public static class GPXExtensions {
		Map<String, String> extensions = null;
		GPXExtensionsWriter extensionsWriter = null;

		public Map<String, String> getExtensionsToRead() {
			if (extensions == null) {
				return Collections.emptyMap();
			}
			return extensions;
		}

		public Map<String, String> getExtensionsToWrite() {
			if (extensions == null) {
				extensions = new LinkedHashMap<>();
			}
			return extensions;
		}

		public void copyExtensions(GPXExtensions e) {
			Map<String, String> extensionsToRead = e.getExtensionsToRead();
			if (!extensionsToRead.isEmpty()) {
				getExtensionsToWrite().putAll(extensionsToRead);
			}
		}

		public GPXExtensionsWriter getExtensionsWriter() {
			return extensionsWriter;
		}

		public void setExtensionsWriter(GPXExtensionsWriter extensionsWriter) {
			this.extensionsWriter = extensionsWriter;
		}

		public int getColor(int defColor) {
			String clrValue = null;
			if (extensions != null) {
				clrValue = extensions.get("color");
				if (clrValue == null) {
					clrValue = extensions.get("colour");
				}
				if (clrValue == null) {
					clrValue = extensions.get("displaycolor");
				}
				if (clrValue == null) {
					clrValue = extensions.get("displaycolour");
				}
			}
			return parseColor(clrValue, defColor);
		}

		public void setColor(int color) {
			getExtensionsToWrite().put("color", Algorithms.colorToString(color));
		}

		public void removeColor() {
			getExtensionsToWrite().remove("color");
		}

		protected int parseColor(String colorString, int defColor) {
			if (!Algorithms.isEmpty(colorString)) {
				if (colorString.charAt(0) == '#') {
					long color = Long.parseLong(colorString.substring(1), 16);
					if (colorString.length() <= 7) {
						color |= 0x00000000ff000000;
					} else if (colorString.length() != 9) {
						return defColor;
					}
					return (int) color;
				} else {
					GPXColor c = GPXColor.getColorFromName(colorString);
					if (c != null) {
						return c.color;
					}
				}
			}
			return defColor;
		}
	}

	public static class Elevation {
		public float distance;
		public int time;
		public float elevation;
		public boolean firstPoint = false;
		public boolean lastPoint = false;
	}

	public static class Speed {
		public float distance;
		public int time;
		public float speed;
		public boolean firstPoint = false;
		public boolean lastPoint = false;
	}

	public static class WptPt extends GPXExtensions {
		public boolean firstPoint = false;
		public boolean lastPoint = false;
		public double lat;
		public double lon;
		public String name = null;
		public String link = null;
		// previous undocumented feature 'category' ,now 'type'
		public String category = null;
		public String desc = null;
		public String comment = null;
		// by default
		public long time = 0;
		public double ele = Double.NaN;
		public double speed = 0;
		public double hdop = Double.NaN;
		public float heading = Float.NaN;
		public boolean deleted = false;
		public int speedColor = 0;
		public int altitudeColor = 0;
		public int slopeColor = 0;
		public int colourARGB = 0;                    // point colour (used for altitude/speed colouring)
		public double distance = 0.0;                // cumulative distance, if in a track

		public WptPt() {
		}

		public WptPt(WptPt wptPt) {
			this.lat = wptPt.lat;
			this.lon = wptPt.lon;
			this.name = wptPt.name;
			this.link = wptPt.link;

			this.category = wptPt.category;
			this.desc = wptPt.desc;
			this.comment = wptPt.comment;

			this.time = wptPt.time;
			this.ele = wptPt.ele;
			this.speed = wptPt.speed;
			this.hdop = wptPt.hdop;
			this.heading = wptPt.heading;
			this.deleted = wptPt.deleted;
			this.speedColor = wptPt.speedColor;
			this.altitudeColor = wptPt.altitudeColor;
			this.slopeColor = wptPt.slopeColor;
			this.colourARGB = wptPt.colourARGB;
			this.distance = wptPt.distance;
		}

		public void setDistance(double dist) {
			distance = dist;
		}

		public double getDistance() {
			return distance;
		}

		public int getColor() {
			return getColor(0);
		}

		public double getLatitude() {
			return lat;
		}

		public double getLongitude() {
			return lon;
		}

		public float getHeading() {
			return heading;
		}

		public WptPt(double lat, double lon, long time, double ele, double speed, double hdop) {
			this(lat, lon, time, ele, speed, hdop, Float.NaN);
		}

		public WptPt(double lat, double lon, long time, double ele, double speed, double hdop, float heading) {
			this.lat = lat;
			this.lon = lon;
			this.time = time;
			this.ele = ele;
			this.speed = speed;
			this.hdop = hdop;
			this.heading = heading;
		}

		public boolean isVisible() {
			return true;
		}

		public String getIconName() {
			return getExtensionsToRead().get(ICON_NAME_EXTENSION);
		}

		public String getIconNameOrDefault() {
			String iconName = getExtensionsToRead().get(ICON_NAME_EXTENSION);
			if (iconName == null) {
				iconName = DEFAULT_ICON_NAME;
			}
			return iconName;
		}

		public void setIconName(String iconName) {
			getExtensionsToWrite().put(ICON_NAME_EXTENSION, iconName);
		}

		public int getColor(ColorizationType type) {
			if (type == ColorizationType.SPEED) {
				return speedColor;
			} else if (type == ColorizationType.ELEVATION) {
				return altitudeColor;
			} else {
				return slopeColor;
			}
		}

		public void setColor(ColorizationType type, int color) {
			if (type == ColorizationType.SPEED) {
				speedColor = color;
			} else if (type == ColorizationType.ELEVATION) {
				altitudeColor = color;
			} else if (type == ColorizationType.SLOPE) {
				slopeColor = color;
			}
		}

		public String getBackgroundType() {
			return getExtensionsToRead().get(BACKGROUND_TYPE_EXTENSION);
		}

		public void setBackgroundType(String backType) {
			getExtensionsToWrite().put(BACKGROUND_TYPE_EXTENSION, backType);
		}

		public String getProfileType() {
			return getExtensionsToRead().get(PROFILE_TYPE_EXTENSION);
		}

		public void setProfileType(String profileType) {
			getExtensionsToWrite().put(PROFILE_TYPE_EXTENSION, profileType);
		}

		public boolean hasProfile() {
			String profileType = getProfileType();
			return profileType != null && !GAP_PROFILE_TYPE.equals(profileType);
		}

		public boolean isGap() {
			String profileType = getProfileType();
			return GAP_PROFILE_TYPE.equals(profileType);
		}

		public void setGap() {
			setProfileType(GAP_PROFILE_TYPE);
		}

		public void removeProfileType() {
			getExtensionsToWrite().remove(PROFILE_TYPE_EXTENSION);
		}

		public int getTrkPtIndex() {
			try {
				return Integer.parseInt(getExtensionsToRead().get(TRKPT_INDEX_EXTENSION));
			} catch (NumberFormatException e) {
				return -1;
			}
		}

		public void setTrkPtIndex(int index) {
			getExtensionsToWrite().put(TRKPT_INDEX_EXTENSION, String.valueOf(index));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((category == null) ? 0 : category.hashCode());
			result = prime * result + ((desc == null) ? 0 : desc.hashCode());
			result = prime * result + ((comment == null) ? 0 : comment.hashCode());
			result = prime * result + ((lat == 0) ? 0 : Double.valueOf(lat).hashCode());
			result = prime * result + ((lon == 0) ? 0 : Double.valueOf(lon).hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			WptPt other = (WptPt) obj;
			return Algorithms.objectEquals(other.name, name)
					&& Algorithms.objectEquals(other.category, category)
					&& Algorithms.objectEquals(other.lat, lat)
					&& Algorithms.objectEquals(other.lon, lon)
					&& Algorithms.objectEquals(other.desc, desc);
		}

		public boolean hasLocation() {
			return (lat != 0 && lon != 0);
		}
	}

	public static class TrkSegment extends GPXExtensions {
		public boolean generalSegment = false;
		public List<WptPt> points = new ArrayList<>();

		public Object renderer;

		public List<RouteSegment> routeSegments = new ArrayList<>();
		public List<RouteType> routeTypes = new ArrayList<>();

		public boolean hasRoute() {
			return !routeSegments.isEmpty() && !routeTypes.isEmpty();
		}

		public List<GPXTrackAnalysis> splitByDistance(double meters, boolean joinSegments) {
			return split(getDistanceMetric(), getTimeSplit(), meters, joinSegments);
		}

		public List<GPXTrackAnalysis> splitByTime(int seconds, boolean joinSegments) {
			return split(getTimeSplit(), getDistanceMetric(), seconds, joinSegments);
		}

		private List<GPXTrackAnalysis> split(SplitMetric metric, SplitMetric secondaryMetric, double metricLimit, boolean joinSegments) {
			List<SplitSegment> splitSegments = new ArrayList<>();
			splitSegment(metric, secondaryMetric, metricLimit, splitSegments, this, joinSegments);
			return convert(splitSegments);
		}
	}

	public static class Track extends GPXExtensions {
		public String name = null;
		public String desc = null;
		public List<TrkSegment> segments = new ArrayList<>();
		public boolean generalTrack = false;

	}

	public static class Route extends GPXExtensions {
		public String name = null;
		public String desc = null;
		public List<WptPt> points = new ArrayList<>();

	}

	public static class Metadata extends GPXExtensions {

		public String name;
		public String desc;
		public String link;
		public String keywords;
		public long time = 0;
		public Author author = null;
		public Copyright copyright = null;
		public Bounds bounds = null;

		public String getArticleTitle() {
			return getExtensionsToRead().get("article_title");
		}

		public String getArticleLang() {
			return getExtensionsToRead().get("article_lang");
		}

		public String getDescription() {
			return getExtensionsToRead().get("desc");
		}
	}

	public static class Author extends GPXExtensions {
		public String name;
		public String email;
		public String link;
	}

	public static class Copyright extends GPXExtensions {
		public String author;
		public String year;
		public String license;
	}

	public static class Bounds extends GPXExtensions {
		public double minlat;
		public double minlon;
		public double maxlat;
		public double maxlon;
	}

	public static class RouteSegment {
		public String id;
		public String length;
		public String segmentTime;
		public String speed;
		public String turnType;
		public String turnAngle;
		public String types;
		public String pointTypes;
		public String names;

		public static RouteSegment fromStringBundle(StringBundle bundle) {
			RouteSegment s = new RouteSegment();
			s.id = bundle.getString("id", null);
			s.length = bundle.getString("length", null);
			s.segmentTime = bundle.getString("segmentTime", null);
			s.speed = bundle.getString("speed", null);
			s.turnType = bundle.getString("turnType", null);
			s.turnAngle = bundle.getString("turnAngle", null);
			s.types = bundle.getString("types", null);
			s.pointTypes = bundle.getString("pointTypes", null);
			s.names = bundle.getString("names", null);
			return s;
		}

		public StringBundle toStringBundle() {
			StringBundle bundle = new StringBundle();
			bundle.putString("id", id);
			bundle.putString("length", length);
			bundle.putString("segmentTime", segmentTime);
			bundle.putString("speed", speed);
			bundle.putString("turnType", turnType);
			bundle.putString("turnAngle", turnAngle);
			bundle.putString("types", types);
			bundle.putString("pointTypes", pointTypes);
			bundle.putString("names", names);
			return bundle;
		}
	}

	public static class RouteType {
		public String tag;
		public String value;

		public static RouteType fromStringBundle(StringBundle bundle) {
			RouteType t = new RouteType();
			t.tag = bundle.getString("t", null);
			t.value = bundle.getString("v", null);
			return t;
		}

		public StringBundle toStringBundle() {
			StringBundle bundle = new StringBundle();
			bundle.putString("t", tag);
			bundle.putString("v", value);
			return bundle;
		}
	}

	public static class GPXTrackAnalysis {
		public float totalDistance = 0;
		public float totalDistanceWithoutGaps = 0;
		public int totalTracks = 0;
		public long startTime = Long.MAX_VALUE;
		public long endTime = Long.MIN_VALUE;
		public long timeSpan = 0;
		public long timeSpanWithoutGaps = 0;
		//Next few lines for Issue 3222 heuristic testing only
		//public long timeMoving0 = 0;
		//public float totalDistanceMoving0 = 0;
		public long timeMoving = 0;
		public long timeMovingWithoutGaps = 0;
		public float totalDistanceMoving = 0;
		public float totalDistanceMovingWithoutGaps = 0;

		public double diffElevationUp = 0;
		public double diffElevationDown = 0;
		public double avgElevation = 0;
		public double minElevation = 99999;
		public double maxElevation = -100;

		public float minSpeed = Float.MAX_VALUE;
		public float maxSpeed = 0;
		public float avgSpeed;

		public int points;
		public int wptPoints = 0;

		public Set<String> wptCategoryNames;

		public double metricEnd;
		public double secondaryMetricEnd;
		public WptPt locationStart;
		public WptPt locationEnd;

		public double left = 0;
		public double right = 0;
		public double top = 0;
		public double bottom = 0;

		public boolean isTimeSpecified() {
			return startTime != Long.MAX_VALUE && startTime != 0;
		}

		public boolean isTimeMoving() {
			return timeMoving != 0;
		}

		public boolean isElevationSpecified() {
			return maxElevation != -100;
		}

		public boolean hasSpeedInTrack() {
			return hasSpeedInTrack;
		}

		public boolean isBoundsCalculated() {
			return left !=0 && right != 0 && top != 0 && bottom != 0;
		}

		public List<Elevation> elevationData;
		public List<Speed> speedData;

		public boolean hasElevationData;
		public boolean hasSpeedData;
		public boolean hasSpeedInTrack = false;

		public boolean isSpeedSpecified() {
			return avgSpeed > 0;
		}

		public boolean isColorizationTypeAvailable(ColorizationType colorizationType) {
			if (colorizationType == ColorizationType.SPEED) {
				return isSpeedSpecified();
			} else if (colorizationType == ColorizationType.ELEVATION || colorizationType == ColorizationType.SLOPE) {
				if (!isElevationSpecified()) {
					return false;
				}
				for (Elevation elevation : elevationData) {
					if (Float.isNaN(elevation.elevation)) {
						return false;
					}
				}
				return true;
			} else {
				return true;
			}
		}

		public static GPXTrackAnalysis segment(long filetimestamp, TrkSegment segment) {
			return new GPXTrackAnalysis().prepareInformation(filetimestamp, new SplitSegment(segment));
		}

		public GPXTrackAnalysis prepareInformation(long filestamp, SplitSegment... splitSegments) {
			float[] calculations = new float[1];

			long startTimeOfSingleSegment = 0;
			long endTimeOfSingleSegment = 0;

			float distanceOfSingleSegment = 0;
			float distanceMovingOfSingleSegment = 0;
			long timeMovingOfSingleSegment = 0;

			float totalElevation = 0;
			int elevationPoints = 0;
			int speedCount = 0;
			int timeDiff = 0;
			double totalSpeedSum = 0;
			points = 0;

			double channelThresMin = 10;           // Minimum oscillation amplitude considered as relevant or as above noise for accumulated Ascent/Descent analysis
			double channelThres = channelThresMin; // Actual oscillation amplitude considered as above noise (dynamic channel adjustment, accomodates depedency on current VDOP/getAccuracy if desired)
			double channelBase;
			double channelTop;
			double channelBottom;
			boolean climb = false;

			elevationData = new ArrayList<>();
			speedData = new ArrayList<>();

			for (SplitSegment s : splitSegments) {
				final int numberOfPoints = s.getNumberOfPoints();

				channelBase = 99999;
				channelTop = channelBase;
				channelBottom = channelBase;
				//channelThres = channelThresMin; //only for dynamic channel adjustment

				float segmentDistance = 0f;
				metricEnd += s.metricEnd;
				secondaryMetricEnd += s.secondaryMetricEnd;
				points += numberOfPoints;
				for (int j = 0; j < numberOfPoints; j++) {
					WptPt point = s.get(j);
					if (j == 0 && locationStart == null) {
						locationStart = point;
					}
					if (j == numberOfPoints - 1) {
						locationEnd = point;
					}
					long time = point.time;
					if (time != 0) {
						if (s.metricEnd == 0) {
							if (s.segment.generalSegment) {
								if (point.firstPoint) {
									startTimeOfSingleSegment = time;
								} else if (point.lastPoint) {
									endTimeOfSingleSegment = time;
								}
								if (startTimeOfSingleSegment != 0 && endTimeOfSingleSegment != 0) {
									timeSpanWithoutGaps += endTimeOfSingleSegment - startTimeOfSingleSegment;
									startTimeOfSingleSegment = 0;
									endTimeOfSingleSegment = 0;
								}
							}
						}
						startTime = Math.min(startTime, time);
						endTime = Math.max(endTime, time);
					}

					if (left == 0 && right == 0) {
						left = point.getLongitude();
						right = point.getLongitude();
						top = point.getLatitude();
						bottom = point.getLatitude();
					} else {
						left = Math.min(left, point.getLongitude());
						right = Math.max(right, point.getLongitude());
						top = Math.max(top, point.getLatitude());
						bottom = Math.min(bottom, point.getLatitude());
					}

					double elevation = point.ele;
					Elevation elevation1 = new Elevation();
					if (!Double.isNaN(elevation)) {
						totalElevation += elevation;
						elevationPoints++;
						minElevation = Math.min(elevation, minElevation);
						maxElevation = Math.max(elevation, maxElevation);

						elevation1.elevation = (float) elevation;
					} else {
						elevation1.elevation = Float.NaN;
					}

					float speed = (float) point.speed;
					if (speed > 0) {
						hasSpeedInTrack = true;
					}

					// Trend channel analysis for elevation gain/loss, Hardy 2015-09-22, LPF filtering added 2017-10-26:
					// - Detect the consecutive elevation trend channels: Only use the net elevation changes of each trend channel (i.e. between the turnarounds) to accumulate the Ascent/Descent values.
					// - Perform the channel evaluation on Low Pass Filter (LPF) smoothed ele data instead of on the raw ele data
					// Parameters:
					// - channelThresMin (in meters): defines the channel turnaround detection, i.e. oscillations smaller than this are ignored as irrelevant or noise.
					// - smoothWindow (number of points): is the LPF window
					// NOW REMOVED, as no relevant examples found: Dynamic channel adjustment: To suppress unreliable measurement points, could relax the turnaround detection from the constant channelThresMin to channelThres which is e.g. based on the maximum VDOP of any point which contributed to the current trend. (Good assumption is VDOP=2*HDOP, which accounts for invisibility of lower hemisphere satellites.)

					// LPF smooting of ele data, usually smooth over odd number of values like 5
					final int smoothWindow = 5;
					double eleSmoothed = Double.NaN;
					int j2 = 0;
					for (int j1 = - smoothWindow + 1; j1 <= 0; j1++) {
						if ((j + j1 >= 0) && !Double.isNaN(s.get(j + j1).ele)) {
							j2++;
							if (!Double.isNaN(eleSmoothed)) {
								eleSmoothed = eleSmoothed + s.get(j + j1).ele;
							} else {
								eleSmoothed = s.get(j + j1).ele;
							}
						}
					}
					if (!Double.isNaN(eleSmoothed)) {
						eleSmoothed = eleSmoothed / j2;
					}

					if (!Double.isNaN(eleSmoothed)) {
						// Init channel
						if (channelBase == 99999) {
							channelBase = eleSmoothed;
							channelTop = channelBase;
							channelBottom = channelBase;
							//channelThres = channelThresMin; //only for dynamic channel adjustment
						}
						// Channel maintenance
						if (eleSmoothed > channelTop) {
							channelTop = eleSmoothed;
							//if (!Double.isNaN(point.hdop)) {
							//	channelThres = Math.max(channelThres, 2.0 * point.hdop); //only for dynamic channel adjustment
							//}
						} else if (eleSmoothed < channelBottom) {
							channelBottom = eleSmoothed;
							//if (!Double.isNaN(point.hdop)) {
							//	channelThres = Math.max(channelThres, 2.0 * point.hdop); //only for dynamic channel adjustment
							//}
						}
						// Turnaround (breakout) detection
						if ((eleSmoothed <= (channelTop - channelThres)) && (climb == true)) {
							if ((channelTop - channelBase) >= channelThres) {
								diffElevationUp += channelTop - channelBase;
							}
							channelBase = channelTop;
							channelBottom = eleSmoothed;
							climb = false;
							//channelThres = channelThresMin; //only for dynamic channel adjustment
						} else if ((eleSmoothed >= (channelBottom + channelThres)) && (climb == false)) {
							if ((channelBase - channelBottom) >= channelThres) {
								diffElevationDown += channelBase - channelBottom;
							}
							channelBase = channelBottom;
							channelTop = eleSmoothed;
							climb = true;
							//channelThres = channelThresMin; //only for dynamic channel adjustment
						}
						// End detection without breakout
						if (j == (numberOfPoints - 1)) {
							if ((channelTop - channelBase) >= channelThres) {
								diffElevationUp += channelTop - channelBase;
							}
							if ((channelBase - channelBottom) >= channelThres) {
								diffElevationDown += channelBase - channelBottom;
							}
						}
					}

					if (j > 0) {
						WptPt prev = s.get(j - 1);

						// Old complete summation approach for elevation gain/loss
						//if (!Double.isNaN(point.ele) && !Double.isNaN(prev.ele)) {
						//	double diff = point.ele - prev.ele;
						//	if (diff > 0) {
						//		diffElevationUp += diff;
						//	} else {
						//		diffElevationDown -= diff;
						//	}
						//}

						// totalDistance += MapUtils.getDistance(prev.lat, prev.lon, point.lat, point.lon);
						// using ellipsoidal 'distanceBetween' instead of spherical haversine (MapUtils.getDistance) is
						// a little more exact, also seems slightly faster:
						net.osmand.Location.distanceBetween(prev.lat, prev.lon, point.lat, point.lon, calculations);
						totalDistance += calculations[0];
						segmentDistance += calculations[0];
						point.distance = segmentDistance;
						timeDiff = (int)((point.time - prev.time) / 1000);

						//Last resort: Derive speed values from displacement if track does not originally contain speed
						if (!hasSpeedInTrack && speed == 0 && timeDiff > 0) {
							speed = calculations[0] / timeDiff;
						}

						// Motion detection:
						//   speed > 0  uses GPS chipset's motion detection
						//   calculations[0] > minDisplacment * time  is heuristic needed because tracks may be filtered at recording time, so points at rest may not be present in file at all
						if ((speed > 0) && (calculations[0] > 0.1 / 1000f * (point.time - prev.time)) && point.time != 0 && prev.time != 0) {
							timeMoving = timeMoving + (point.time - prev.time);
							totalDistanceMoving += calculations[0];
							if (s.segment.generalSegment && !point.firstPoint) {
								timeMovingOfSingleSegment += point.time - prev.time;
								distanceMovingOfSingleSegment += calculations[0];
							}
						}

						//Next few lines for Issue 3222 heuristic testing only
						//	if (speed > 0 && point.time != 0 && prev.time != 0) {
						//		timeMoving0 = timeMoving0 + (point.time - prev.time);
						//		totalDistanceMoving0 += calculations[0];
						//	}
					}

					elevation1.time = timeDiff;
					elevation1.distance = (j > 0) ? calculations[0] : 0;
					elevationData.add(elevation1);
					if (!hasElevationData && !Float.isNaN(elevation1.elevation) && totalDistance > 0) {
						hasElevationData = true;
					}

					minSpeed = Math.min(speed, minSpeed);
					if (speed > 0) {
						totalSpeedSum += speed;
						maxSpeed = Math.max(speed, maxSpeed);
						speedCount++;
					}

					Speed speed1 = new Speed();
					speed1.speed = speed;
					speed1.time = timeDiff;
					speed1.distance = elevation1.distance;
					speedData.add(speed1);
					if (!hasSpeedData && speed1.speed > 0 && totalDistance > 0) {
						hasSpeedData = true;
					}
					if (s.segment.generalSegment) {
						distanceOfSingleSegment += calculations[0];
						if (point.firstPoint) {
							distanceOfSingleSegment = 0;
							timeMovingOfSingleSegment = 0;
							distanceMovingOfSingleSegment = 0;
							if (j > 0) {
								elevation1.firstPoint = true;
								speed1.firstPoint = true;
							}
						}
						if (point.lastPoint) {
							totalDistanceWithoutGaps += distanceOfSingleSegment;
							timeMovingWithoutGaps += timeMovingOfSingleSegment;
							totalDistanceMovingWithoutGaps += distanceMovingOfSingleSegment;
							if (j < numberOfPoints - 1) {
								elevation1.lastPoint = true;
								speed1.lastPoint = true;
							}
						}
					}
				}
			}
			if (totalDistance < 0) {
				hasElevationData = false;
				hasSpeedData = false;
			}
			if (!isTimeSpecified()) {
				startTime = filestamp;
				endTime = filestamp;
			}

			// OUTPUT:
			// 1. Total distance, Start time, End time
			// 2. Time span
			if (timeSpan == 0) {
				timeSpan = endTime - startTime;
			}

			// 3. Time moving, if any
			// 4. Elevation, eleUp, eleDown, if recorded
			if (elevationPoints > 0) {
				avgElevation = totalElevation / elevationPoints;
			}


			// 5. Max speed and Average speed, if any. Average speed is NOT overall (effective) speed, but only calculated for "moving" periods.
			//    Averaging speed values is less precise than totalDistanceMoving/timeMoving
			if (speedCount > 0) {
				if (timeMoving > 0) {
					avgSpeed = (float) totalDistanceMoving / (float) timeMoving * 1000f;
				} else {
					avgSpeed = (float) totalSpeedSum / (float) speedCount;
				}
			} else {
				avgSpeed = -1;
			}
			return this;
		}

	}

	private static class SplitSegment {
		TrkSegment segment;
		double startCoeff = 0;
		int startPointInd;
		double endCoeff = 0;
		int endPointInd;
		double metricEnd;
		double secondaryMetricEnd;

		public SplitSegment(TrkSegment s) {
			startPointInd = 0;
			startCoeff = 0;
			endPointInd = s.points.size() - 2;
			endCoeff = 1;
			this.segment = s;
		}

		public SplitSegment(int startInd, int endInd, TrkSegment s) {
			startPointInd = startInd;
			startCoeff = 0;
			endPointInd = endInd - 2;
			endCoeff = 1;
			this.segment = s;
		}

		public SplitSegment(TrkSegment s, int pointInd, double cf) {
			this.segment = s;
			this.startPointInd = pointInd;
			this.startCoeff = cf;
		}


		public int getNumberOfPoints() {
			return endPointInd - startPointInd + 2;
		}

		public WptPt get(int j) {
			final int ind = j + startPointInd;
			if (j == 0) {
				if (startCoeff == 0) {
					return segment.points.get(ind);
				}
				return approx(segment.points.get(ind), segment.points.get(ind + 1), startCoeff);
			}
			if (j == getNumberOfPoints() - 1) {
				if (endCoeff == 1) {
					return segment.points.get(ind);
				}
				return approx(segment.points.get(ind - 1), segment.points.get(ind), endCoeff);
			}
			return segment.points.get(ind);
		}


		private WptPt approx(WptPt w1, WptPt w2, double cf) {
			long time = value(w1.time, w2.time, 0, cf);
			double speed = value(w1.speed, w2.speed, 0, cf);
			double ele = value(w1.ele, w2.ele, 0, cf);
			double hdop = value(w1.hdop, w2.hdop, 0, cf);
			double lat = value(w1.lat, w2.lat, -360, cf);
			double lon = value(w1.lon, w2.lon, -360, cf);
			return new WptPt(lat, lon, time, ele, speed, hdop);
		}

		private double value(double vl, double vl2, double none, double cf) {
			if (vl == none || Double.isNaN(vl)) {
				return vl2;
			} else if (vl2 == none || Double.isNaN(vl2)) {
				return vl;
			}
			return vl + cf * (vl2 - vl);
		}

		private long value(long vl, long vl2, long none, double cf) {
			if (vl == none) {
				return vl2;
			} else if (vl2 == none) {
				return vl;
			}
			return vl + ((long) (cf * (vl2 - vl)));
		}


		public double setLastPoint(int pointInd, double endCf) {
			endCoeff = endCf;
			endPointInd = pointInd;
			return endCoeff;
		}

	}

	private static SplitMetric getDistanceMetric() {
		return new SplitMetric() {

			private float[] calculations = new float[1];

			@Override
			public double metric(WptPt p1, WptPt p2) {
				net.osmand.Location.distanceBetween(p1.lat, p1.lon, p2.lat, p2.lon, calculations);
				return calculations[0];
			}
		};
	}

	private static SplitMetric getTimeSplit() {
		return new SplitMetric() {

			@Override
			public double metric(WptPt p1, WptPt p2) {
				if (p1.time != 0 && p2.time != 0) {
					return (int) Math.abs((p2.time - p1.time) / 1000l);
				}
				return 0;
			}
		};
	}

	private abstract static class SplitMetric {

		public abstract double metric(WptPt p1, WptPt p2);

	}

	private static void splitSegment(SplitMetric metric, SplitMetric secondaryMetric,
									 double metricLimit, List<SplitSegment> splitSegments,
									 TrkSegment segment, boolean joinSegments) {
		double currentMetricEnd = metricLimit;
		double secondaryMetricEnd = 0;
		SplitSegment sp = new SplitSegment(segment, 0, 0);
		double total = 0;
		WptPt prev = null;
		for (int k = 0; k < segment.points.size(); k++) {
			WptPt point = segment.points.get(k);
			if (k > 0) {
				double currentSegment = 0;
				if (!(segment.generalSegment && !joinSegments && point.firstPoint)) {
					currentSegment = metric.metric(prev, point);
					secondaryMetricEnd += secondaryMetric.metric(prev, point);
				}
				while (total + currentSegment > currentMetricEnd) {
					double p = currentMetricEnd - total;
					double cf = (p / currentSegment);
					sp.setLastPoint(k - 1, cf);
					sp.metricEnd = currentMetricEnd;
					sp.secondaryMetricEnd = secondaryMetricEnd;
					splitSegments.add(sp);

					sp = new SplitSegment(segment, k - 1, cf);
					currentMetricEnd += metricLimit;
				}
				total += currentSegment;
			}
			prev = point;
		}
		if (segment.points.size() > 0
				&& !(sp.endPointInd == segment.points.size() - 1 && sp.startCoeff == 1)) {
			sp.metricEnd = total;
			sp.secondaryMetricEnd = secondaryMetricEnd;
			sp.setLastPoint(segment.points.size() - 2, 1);
			splitSegments.add(sp);
		}
	}

	private static List<GPXTrackAnalysis> convert(List<SplitSegment> splitSegments) {
		List<GPXTrackAnalysis> ls = new ArrayList<>();
		for (SplitSegment s : splitSegments) {
			GPXTrackAnalysis a = new GPXTrackAnalysis();
			a.prepareInformation(0, s);
			ls.add(a);
		}
		return ls;
	}

	public static QuadRect calculateBounds(List<WptPt> pts) {
		QuadRect trackBounds = new QuadRect(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		updateBounds(trackBounds, pts, 0);

		return trackBounds;
	}

	public static QuadRect calculateTrackBounds(List<TrkSegment> segments) {
		QuadRect trackBounds = new QuadRect(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		for (TrkSegment segment : segments) {
			updateBounds(trackBounds, segment.points, 0);
		}
		return trackBounds;
	}

	public static void updateBounds(QuadRect trackBounds, List<WptPt> pts, int startIndex) {
		for (int i = startIndex; i < pts.size(); i++) {
			WptPt pt = pts.get(i);
			trackBounds.right = Math.max(trackBounds.right, pt.lon);
			trackBounds.left = Math.min(trackBounds.left, pt.lon);
			trackBounds.top = Math.max(trackBounds.top, pt.lat);
			trackBounds.bottom = Math.min(trackBounds.bottom, pt.lat);
		}
	}

	public static class GPXFile extends GPXExtensions {
		public String author;
		public Metadata metadata;
		public List<Track> tracks = new ArrayList<>();
		private List<WptPt> points = new ArrayList<>();
		public List<Route> routes = new ArrayList<>();

		public Exception error = null;
		public String path = "";
		public boolean showCurrentTrack;
		public boolean hasAltitude;
		public long modifiedTime = 0;

		private Track generalTrack;
		private TrkSegment generalSegment;

		public GPXFile(String author) {
			this.author = author;
		}

		public GPXFile(String title, String lang, String description) {
			this.metadata = new Metadata();
			if(description != null) {
				metadata.getExtensionsToWrite().put("desc", description);
			}
			if(lang != null) {
				metadata.getExtensionsToWrite().put("article_lang", lang);
			}
			if(title != null) {
				metadata.getExtensionsToWrite().put("article_title", title);
			}
		}

		public boolean hasRoute() {
			return getNonEmptyTrkSegments(true).size() > 0;
		}

		public List<WptPt> getPoints() {
			return Collections.unmodifiableList(points);
		}

		public Map<String, List<WptPt>> getPointsByCategories() {
			Map<String, List<WptPt>> res = new HashMap<>();
			for (WptPt pt : points) {
				String category = pt.category == null ? "" : pt.category;
				List<WptPt> list = res.get(category);
				if (list != null) {
					list.add(pt);
				} else {
					list = new ArrayList<>();
					list.add(pt);
					res.put(category, list);
				}
			}
			return res;
		}

		public boolean isPointsEmpty() {
			return points.isEmpty();
		}

		public int getPointsSize() {
			return points.size();
		}

		public boolean containsPoint(WptPt point) {
			return points.contains(point);
		}

		public void clearPoints() {
			points.clear();
			modifiedTime = System.currentTimeMillis();
		}

		public void addPoint(WptPt point) {
			points.add(point);
			modifiedTime = System.currentTimeMillis();
		}

		public void addPoint(int position, WptPt point) {
			points.add(position, point);
			modifiedTime = System.currentTimeMillis();
		}

		public void addPoints(Collection<? extends WptPt> collection) {
			points.addAll(collection);
			modifiedTime = System.currentTimeMillis();
		}

		public boolean isCloudmadeRouteFile() {
			return "cloudmade".equalsIgnoreCase(author);
		}

		public void addGeneralTrack() {
			Track generalTrack = getGeneralTrack();
			if (generalTrack != null && !tracks.contains(generalTrack)) {
				tracks.add(0, generalTrack);
			}
		}

		public Track getGeneralTrack() {
			TrkSegment generalSegment = getGeneralSegment();
			if (generalTrack == null && generalSegment != null) {
				Track track = new Track();
				track.segments = new ArrayList<>();
				track.segments.add(generalSegment);
				generalTrack = track;
				track.generalTrack = true;
			}
			return generalTrack;
		}

		public TrkSegment getGeneralSegment() {
			if (generalSegment == null && getNonEmptySegmentsCount() > 1) {
				buildGeneralSegment();
			}
			return generalSegment;
		}

		private void buildGeneralSegment() {
			TrkSegment segment = new TrkSegment();
			for (Track track : tracks) {
				for (TrkSegment s : track.segments) {
					if (s.points.size() > 0) {
						List<WptPt> waypoints = new ArrayList<>(s.points.size());
						for (WptPt wptPt : s.points) {
							waypoints.add(new WptPt(wptPt));
						}
						waypoints.get(0).firstPoint = true;
						waypoints.get(waypoints.size() - 1).lastPoint = true;
						segment.points.addAll(waypoints);
					}
				}
			}
			if (segment.points.size() > 0) {
				segment.generalSegment = true;
				generalSegment = segment;
			}
		}

		public GPXTrackAnalysis getAnalysis(long fileTimestamp) {
			return getAnalysis(fileTimestamp, null, null);
		}

		public GPXTrackAnalysis getAnalysis(long fileTimestamp,
		                                    Double fromDistance,
		                                    Double toDistance) {
			GPXTrackAnalysis g = new GPXTrackAnalysis();
			g.wptPoints = points.size();
			g.wptCategoryNames = getWaypointCategories(true);
			List<SplitSegment> segments = getSplitSegments(g, fromDistance, toDistance);
			g.prepareInformation(fileTimestamp, segments.toArray(new SplitSegment[0]));
			return g;
		}

		private List<SplitSegment> getSplitSegments(GPXTrackAnalysis g,
		                                            Double fromDistance,
		                                            Double toDistance) {
			List<SplitSegment> splitSegments = new ArrayList<>();
			for (int i = 0; i < tracks.size(); i++) {
				Track subtrack = tracks.get(i);
				for (TrkSegment segment : subtrack.segments) {
					if (!segment.generalSegment) {
						g.totalTracks++;
						if (segment.points.size() > 1) {
							splitSegments.add(createSplitSegment(segment, fromDistance, toDistance));
						}
					}
				}
			}
			return splitSegments;
		}

		private SplitSegment createSplitSegment(TrkSegment segment,
		                                        Double fromDistance,
		                                        Double toDistance) {
			if (fromDistance != null && toDistance != null) {
				int startInd = getPointIndexByDistance(segment.points, fromDistance);
				int endInd = getPointIndexByDistance(segment.points, toDistance);
				return new SplitSegment(startInd, endInd, segment);
			} else {
				return new SplitSegment(segment);
			}
		}

		public int getPointIndexByDistance(List<WptPt> points, double distance) {
			int index = 0;
			double minDistanceChange = 99999;
			for (int i = 0; i < points.size(); i++) {
				WptPt point = points.get(i);
				double currentDistanceChange = Math.abs(point.distance - distance);
				if (currentDistanceChange < minDistanceChange) {
					minDistanceChange = currentDistanceChange;
					index = i;
				}
			}
			return index;
		}

		public boolean containsRoutePoint(WptPt point) {
			return getRoutePoints().contains(point);
		}

		public List<WptPt> getRoutePoints() {
			List<WptPt> points = new ArrayList<>();
			for (int i = 0; i < routes.size(); i++) {
				Route rt = routes.get(i);
				points.addAll(rt.points);
			}
			return points;
		}

		public List<WptPt> getRoutePoints(int routeIndex) {
			List<WptPt> points = new ArrayList<>();
			if (routes.size() > routeIndex) {
				Route rt = routes.get(routeIndex);
				points.addAll(rt.points);
			}
			return points;
		}

		public boolean hasRtePt() {
			for (Route r : routes) {
				if (r.points.size() > 0) {
					return true;
				}
			}
			return false;
		}

		public boolean hasWptPt() {
			return points.size() > 0;
		}

		public boolean hasTrkPt() {
			for (Track t : tracks) {
				for (TrkSegment ts : t.segments) {
					if (ts.points.size() > 0) {
						return true;
					}
				}
			}
			return false;
		}

		public WptPt addWptPt(double lat, double lon, long time, String description, String name, String category, int color) {
			return addWptPt(lat, lon, time, description, name, category, color, null, null);
		}

		public WptPt addWptPt(double lat, double lon, long time, String description, String name, String category,
		                      int color, String iconName, String backgroundType) {
			double latAdjusted = Double.parseDouble(latLonFormat.format(lat));
			double lonAdjusted = Double.parseDouble(latLonFormat.format(lon));
			final WptPt pt = new WptPt(latAdjusted, lonAdjusted, time, Double.NaN, 0, Double.NaN);
			pt.name = name;
			pt.category = category;
			pt.desc = description;
			if (color != 0) {
				pt.setColor(color);
			}
			if (iconName != null) {
				pt.setIconName(iconName);
			}
			if (backgroundType != null) {
				pt.setBackgroundType(backgroundType);
			}

			points.add(pt);

			modifiedTime = System.currentTimeMillis();

			return pt;
		}

		public WptPt addRtePt(double lat, double lon, long time, String description, String name, String category, int color) {
			double latAdjusted = Double.parseDouble(latLonFormat.format(lat));
			double lonAdjusted = Double.parseDouble(latLonFormat.format(lon));
			final WptPt pt = new WptPt(latAdjusted, lonAdjusted, time, Double.NaN, 0, Double.NaN);
			pt.name = name;
			pt.category = category;
			pt.desc = description;
			if (color != 0) {
				pt.setColor(color);
			}

			if (routes.size() == 0) {
				routes.add(new Route());
			}
			Route currentRoute = routes.get(routes.size() - 1);
			currentRoute.points.add(pt);

			modifiedTime = System.currentTimeMillis();

			return pt;
		}

		public List<TrkSegment> getNonEmptyTrkSegments(boolean routesOnly) {
			List<TrkSegment> segments = new ArrayList<>();
			for (Track t : tracks) {
				for (TrkSegment s : t.segments) {
					if (!s.generalSegment && s.points.size() > 0 && (!routesOnly || s.hasRoute())) {
						segments.add(s);
					}
				}
			}
			return segments;
		}

		public void addTrkSegment(List<WptPt> points) {
			removeGeneralTrackIfExists();

			TrkSegment segment = new TrkSegment();
			segment.points.addAll(points);

			if (tracks.size() == 0) {
				tracks.add(new Track());
			}
			Track lastTrack = tracks.get(tracks.size() - 1);
			lastTrack.segments.add(segment);

			modifiedTime = System.currentTimeMillis();
		}

		public boolean replaceSegment(TrkSegment oldSegment, TrkSegment newSegment) {
			removeGeneralTrackIfExists();

			for (int i = 0; i < tracks.size(); i++) {
				Track currentTrack = tracks.get(i);
				for (int j = 0; j < currentTrack.segments.size(); j++) {
					int segmentIndex = currentTrack.segments.indexOf(oldSegment);
					if (segmentIndex != -1) {
						currentTrack.segments.remove(segmentIndex);
						currentTrack.segments.add(segmentIndex, newSegment);
						addGeneralTrack();
						modifiedTime = System.currentTimeMillis();
						return true;
					}
				}
			}

			addGeneralTrack();
			return false;
		}

		public void addRoutePoints(List<WptPt> points, boolean addRoute) {
			if (routes.size() == 0 || addRoute) {
				Route route = new Route();
				routes.add(route);
			}

			Route lastRoute = routes.get(routes.size() - 1);
			lastRoute.points.addAll(points);
			modifiedTime = System.currentTimeMillis();
		}

		public void replaceRoutePoints(List<WptPt> points) {
			routes.clear();
			routes.add(new Route());
			Route currentRoute = routes.get(routes.size() - 1);
			currentRoute.points.addAll(points);
			modifiedTime = System.currentTimeMillis();
		}

		public void updateWptPt(WptPt pt, double lat, double lon, long time, String description, String name, String category,
		                        int color) {
			updateWptPt(pt, lat, lon, System.currentTimeMillis(), description, name, category, color, null, null);
		}

		public void updateWptPt(WptPt pt, double lat, double lon, long time, String description, String name, String category,
		                        int color, String iconName, String backgroundType) {
			int index = points.indexOf(pt);
			double latAdjusted = Double.parseDouble(latLonFormat.format(lat));
			double lonAdjusted = Double.parseDouble(latLonFormat.format(lon));
			pt.lat = latAdjusted;
			pt.lon = lonAdjusted;
			pt.time = time;
			pt.desc = description;
			pt.name = name;
			pt.category = category;
			if (color != 0) {
				pt.setColor(color);
			}
			if (iconName != null) {
				pt.setIconName(iconName);
			}
			if (backgroundType != null) {
				pt.setBackgroundType(backgroundType);
			}

			if (index != -1) {
				points.set(index, pt);
			}
			modifiedTime = System.currentTimeMillis();
		}

		private void removeGeneralTrackIfExists() {
			if (generalTrack != null) {
				tracks.remove(generalTrack);
				this.generalTrack = null;
				this.generalSegment = null;
			}
		}

		public boolean removeTrkSegment(TrkSegment segment) {
			removeGeneralTrackIfExists();

			for (int i = 0; i < tracks.size(); i++) {
				Track currentTrack = tracks.get(i);
				for (int j = 0; j < currentTrack.segments.size(); j++) {
					if (currentTrack.segments.remove(segment)) {
						addGeneralTrack();
						modifiedTime = System.currentTimeMillis();
						return true;
					}
				}
			}
			addGeneralTrack();
			return false;
		}

		public boolean deleteWptPt(WptPt pt) {
			modifiedTime = System.currentTimeMillis();
			return points.remove(pt);
		}

		public boolean deleteRtePt(WptPt pt) {
			modifiedTime = System.currentTimeMillis();
			for (Route route : routes) {
				if (route.points.remove(pt)) {
					return true;
				}
			}
			return false;
		}

		public List<TrkSegment> processRoutePoints() {
			List<TrkSegment> tpoints = new ArrayList<TrkSegment>();
			if (routes.size() > 0) {
				for (Route r : routes) {
					int routeColor = r.getColor(getColor(0));
					if (r.points.size() > 0) {
						TrkSegment sgmt = new TrkSegment();
						tpoints.add(sgmt);
						sgmt.points.addAll(r.points);
						sgmt.setColor(routeColor);
					}
				}
			}
			return tpoints;
		}

		public List<TrkSegment> proccessPoints() {
			List<TrkSegment> tpoints = new ArrayList<TrkSegment>();
			for (Track t : tracks) {
				int trackColor = t.getColor(getColor(0));
				for (TrkSegment ts : t.segments) {
					if (!ts.generalSegment && ts.points.size() > 0) {
						TrkSegment sgmt = new TrkSegment();
						tpoints.add(sgmt);
						sgmt.points.addAll(ts.points);
						sgmt.setColor(trackColor);
					}
				}
			}
			return tpoints;
		}

		public WptPt getLastPoint() {
			if (tracks.size() > 0) {
				Track tk = tracks.get(tracks.size() - 1);
				if (tk.segments.size() > 0) {
					TrkSegment ts = tk.segments.get(tk.segments.size() - 1);
					if (ts.points.size() > 0) {
						return ts.points.get(ts.points.size() - 1);
					}
				}
			}
			return null;
		}

		public WptPt findPointToShow() {
			for (Track t : tracks) {
				for (TrkSegment s : t.segments) {
					if (s.points.size() > 0) {
						return s.points.get(0);
					}
				}
			}
			for (Route s : routes) {
				if (s.points.size() > 0) {
					return s.points.get(0);
				}
			}
			if (points.size() > 0) {
				return points.get(0);
			}
			return null;
		}

		public boolean isEmpty() {
			for (Track t : tracks) {
				if (t.segments != null) {
					for (TrkSegment s : t.segments) {
						boolean tracksEmpty = s.points.isEmpty();
						if (!tracksEmpty) {
							return false;
						}
					}
				}
			}
			return points.isEmpty() && routes.isEmpty();
		}

		public int getNonEmptySegmentsCount() {
			int count = 0;
			for (Track t : tracks) {
				for (TrkSegment s : t.segments) {
					if (s.points.size() > 0) {
						count++;
					}
				}
			}
			return count;
		}

		public Set<String> getWaypointCategories(boolean withDefaultCategory) {
			Set<String> categories = new HashSet<>();
			for (WptPt pt : points) {
				String category = pt.category == null ? "" : pt.category;
				if (withDefaultCategory || !Algorithms.isEmpty(category)) {
					categories.add(category);
				}
			}
			return categories;
		}

		public Map<String, Integer> getWaypointCategoriesWithColors(boolean withDefaultCategory) {
			Map<String, Integer> categories = new HashMap<>();
			for (WptPt pt : points) {
				String category = pt.category == null ? "" : pt.category;
				int color = pt.category == null ? 0 : pt.getColor();
				boolean emptyCategory = Algorithms.isEmpty(category);
				if (!emptyCategory) {
					Integer existingColor = categories.get(category);
					if (existingColor == null || (existingColor == 0 && color != 0)) {
						categories.put(category, color);
					}
				} else if (withDefaultCategory) {
					categories.put(category, 0);
				}
			}
			return categories;
		}

		public QuadRect getRect() {
			double left = 0, right = 0;
			double top = 0, bottom = 0;
			for (Track track : tracks) {
				for (TrkSegment segment : track.segments) {
					for (WptPt p : segment.points) {
						if (left == 0 && right == 0) {
							left = p.getLongitude();
							right = p.getLongitude();
							top = p.getLatitude();
							bottom = p.getLatitude();
						} else {
							left = Math.min(left, p.getLongitude());
							right = Math.max(right, p.getLongitude());
							top = Math.max(top, p.getLatitude());
							bottom = Math.min(bottom, p.getLatitude());
						}
					}
				}
			}
			for (WptPt p : points) {
				if (left == 0 && right == 0) {
					left = p.getLongitude();
					right = p.getLongitude();
					top = p.getLatitude();
					bottom = p.getLatitude();
				} else {
					left = Math.min(left, p.getLongitude());
					right = Math.max(right, p.getLongitude());
					top = Math.max(top, p.getLatitude());
					bottom = Math.min(bottom, p.getLatitude());
				}
			}
			for (Route route : routes) {
				for (WptPt p : route.points) {
					if (left == 0 && right == 0) {
						left = p.getLongitude();
						right = p.getLongitude();
						top = p.getLatitude();
						bottom = p.getLatitude();
					} else {
						left = Math.min(left, p.getLongitude());
						right = Math.max(right, p.getLongitude());
						top = Math.max(top, p.getLatitude());
						bottom = Math.min(bottom, p.getLatitude());
					}
				}
			}
			return new QuadRect(left, top, right, bottom);
		}

		public int[] getGradientScaleColor(String gradientScaleType) {
			String clrValue = null;
			if (extensions != null) {
				clrValue = extensions.get(gradientScaleType);
			}
			return Algorithms.stringToGradientPalette(clrValue);
		}

		public void setGradientScaleColor(String gradientScaleType, int[] gradientScalePalette) {
			getExtensionsToWrite().put(gradientScaleType, Algorithms.gradientPaletteToString(gradientScalePalette));
		}

		public String getGradientScaleType() {
			if (extensions != null) {
				return extensions.get("gradient_scale_type");
			}
			return null;
		}

		public void setGradientScaleType(String gradientScaleType) {
			getExtensionsToWrite().put("gradient_scale_type", gradientScaleType);
		}

		public void removeGradientScaleType() {
			getExtensionsToWrite().remove("gradient_scale_type");
		}

		public String getSplitType() {
			if (extensions != null) {
				return extensions.get("split_type");
			}
			return null;
		}

		public void setSplitType(String gpxSplitType) {
			getExtensionsToWrite().put("split_type", gpxSplitType);
		}

		public double getSplitInterval() {
			if (extensions != null) {
				String splitIntervalStr = extensions.get("split_interval");
				if (!Algorithms.isEmpty(splitIntervalStr)) {
					try {
						return Double.parseDouble(splitIntervalStr);
					} catch (NumberFormatException e) {
						log.error("Error reading split_interval", e);
					}
				}
			}
			return 0;
		}

		public void setSplitInterval(double splitInterval) {
			getExtensionsToWrite().put("split_interval", String.valueOf(splitInterval));
		}

		public String getWidth(String defWidth) {
			String widthValue = null;
			if (extensions != null) {
				widthValue = extensions.get("width");
			}
			return widthValue != null ? widthValue : defWidth;
		}

		public void setWidth(String width) {
			getExtensionsToWrite().put("width", width);
		}

		public boolean isShowArrows() {
			String showArrows = null;
			if (extensions != null) {
				showArrows = extensions.get("show_arrows");
			}
			return Boolean.parseBoolean(showArrows);
		}

		public void setShowArrows(boolean showArrows) {
			getExtensionsToWrite().put("show_arrows", String.valueOf(showArrows));
		}

		public boolean isShowStartFinish() {
			if (extensions != null && extensions.containsKey("show_start_finish")) {
				return Boolean.parseBoolean(extensions.get("show_start_finish"));
			}
			return true;
		}

		public void setShowStartFinish(boolean showStartFinish) {
			getExtensionsToWrite().put("show_start_finish", String.valueOf(showStartFinish));
		}
	}

	public static String asString(GPXFile file) {
		final Writer writer = new StringWriter();
		writeGpx(writer, file);
		return writer.toString();
	}

	public static Exception writeGpxFile(File fout, GPXFile file) {
		Writer output = null;
		try {
			if (fout.getParentFile() != null) {
				fout.getParentFile().mkdirs();
			}
			output = new OutputStreamWriter(new FileOutputStream(fout), "UTF-8"); //$NON-NLS-1$
			if (Algorithms.isEmpty(file.path)) {
				file.path = fout.getAbsolutePath();
			}
			return writeGpx(output, file);
		} catch (Exception e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			return e;
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException ignore) {
					// ignore
				}
			}
		}
	}

	public static Exception writeGpx(Writer output, GPXFile file) {
		try {
			SimpleDateFormat format = new SimpleDateFormat(GPX_TIME_FORMAT, Locale.US);
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
			XmlSerializer serializer = PlatformUtil.newSerializer();
			serializer.setOutput(output);
			serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true); //$NON-NLS-1$
			serializer.startDocument("UTF-8", true); //$NON-NLS-1$
			serializer.startTag(null, "gpx"); //$NON-NLS-1$
			serializer.attribute(null, "version", "1.1"); //$NON-NLS-1$ //$NON-NLS-2$
			if (file.author != null) {
				serializer.attribute(null, "creator", file.author); //$NON-NLS-1$
			}
			serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1"); //$NON-NLS-1$ //$NON-NLS-2$
			serializer.attribute(null, "xmlns:osmand", "https://osmand.net");
			serializer.attribute(null, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			serializer.attribute(null, "xsi:schemaLocation",
					"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd");

			String trackName = file.metadata != null ? file.metadata.name : getFilename(file.path);
			serializer.startTag(null, "metadata");
			writeNotNullText(serializer, "name", trackName);
			if (file.metadata != null) {
				writeNotNullText(serializer, "desc", file.metadata.desc);
				if (file.metadata.author != null) {
					serializer.startTag(null, "author");
					writeAuthor(serializer, file.metadata.author);
					serializer.endTag(null, "author");
				}
				if (file.metadata.copyright != null) {
					serializer.startTag(null, "copyright");
					writeCopyright(serializer, file.metadata.copyright);
					serializer.endTag(null, "copyright");
				}
				writeNotNullTextWithAttribute(serializer, "link", "href", file.metadata.link);
				if (file.metadata.time != 0) {
					writeNotNullText(serializer, "time", format.format(new Date(file.metadata.time)));
				}
				writeNotNullText(serializer, "keywords", file.metadata.keywords);
				if (file.metadata.bounds != null) {
					writeBounds(serializer, file.metadata.bounds);
				}
				writeExtensions(serializer, file.metadata);
			}
			serializer.endTag(null, "metadata");

			for (WptPt l : file.points) {
				serializer.startTag(null, "wpt"); //$NON-NLS-1$
				writeWpt(format, serializer, l);
				serializer.endTag(null, "wpt"); //$NON-NLS-1$
			}

			for (Route track : file.routes) {
				serializer.startTag(null, "rte"); //$NON-NLS-1$
				writeNotNullText(serializer, "name", track.name);
				writeNotNullText(serializer, "desc", track.desc);

				for (WptPt p : track.points) {
					serializer.startTag(null, "rtept"); //$NON-NLS-1$
					writeWpt(format, serializer, p);
					serializer.endTag(null, "rtept"); //$NON-NLS-1$
				}
				writeExtensions(serializer, track);
				serializer.endTag(null, "rte"); //$NON-NLS-1$
			}

			for (Track track : file.tracks) {
				if (!track.generalTrack) {
					serializer.startTag(null, "trk"); //$NON-NLS-1$
					writeNotNullText(serializer, "name", track.name);
					writeNotNullText(serializer, "desc", track.desc);
					for (TrkSegment segment : track.segments) {
						serializer.startTag(null, "trkseg"); //$NON-NLS-1$
						for (WptPt p : segment.points) {
							serializer.startTag(null, "trkpt"); //$NON-NLS-1$
							writeWpt(format, serializer, p);
							serializer.endTag(null, "trkpt"); //$NON-NLS-1$
						}
						assignRouteExtensionWriter(segment);
						writeExtensions(serializer, segment);
						serializer.endTag(null, "trkseg"); //$NON-NLS-1$
					}
					writeExtensions(serializer, track);
					serializer.endTag(null, "trk"); //$NON-NLS-1$
				}
			}

			writeExtensions(serializer, file);

			serializer.endTag(null, "gpx"); //$NON-NLS-1$
			serializer.endDocument();
			serializer.flush();
		} catch (Exception e) {
			log.error("Error saving gpx", e); //$NON-NLS-1$
			return e;
		}
		return null;
	}

	private static void assignRouteExtensionWriter(final TrkSegment segment) {
		if (segment.hasRoute() && segment.getExtensionsWriter() == null) {
			segment.setExtensionsWriter(new GPXExtensionsWriter() {
				@Override
				public void writeExtensions(XmlSerializer serializer) {
					StringBundle bundle = new StringBundle();
					List<StringBundle> segmentsBundle = new ArrayList<>();
					for (RouteSegment segment : segment.routeSegments) {
						segmentsBundle.add(segment.toStringBundle());
					}
					bundle.putBundleList("route", "segment", segmentsBundle);
					List<StringBundle> typesBundle = new ArrayList<>();
					for (RouteType routeType : segment.routeTypes) {
						typesBundle.add(routeType.toStringBundle());
					}
					bundle.putBundleList("types", "type", typesBundle);
					StringBundleWriter bundleWriter = new StringBundleXmlWriter(bundle, serializer);
					bundleWriter.writeBundle();
				}
			});
		}
	}

	private static String getFilename(String path) {
		if(path != null) {
			int i = path.lastIndexOf('/');
			if(i > 0) {
				path = path.substring(i + 1);
			}
			i = path.lastIndexOf('.');
			if(i > 0) {
				path = path.substring(0, i);
			}
		}
		return path;
	}

	private static void writeNotNullTextWithAttribute(XmlSerializer serializer, String tag, String attribute, String value) throws IOException {
		if (value != null) {
			serializer.startTag(null, tag);
			serializer.attribute(null, attribute, value);
			serializer.endTag(null, tag);
		}
	}

	public static void writeNotNullText(XmlSerializer serializer, String tag, String value) throws IOException {
		if (value != null) {
			serializer.startTag(null, tag);
			serializer.text(value);
			serializer.endTag(null, tag);
		}
	}

	private static void writeExtensions(XmlSerializer serializer, GPXExtensions p) throws IOException {
		writeExtensions(serializer, p.getExtensionsToRead(), p);
	}

	private static void writeExtensions(XmlSerializer serializer, Map<String, String> extensions, GPXExtensions p) throws IOException {
		GPXExtensionsWriter extensionsWriter = p.getExtensionsWriter();
		if (!extensions.isEmpty() || extensionsWriter != null) {
			serializer.startTag(null, "extensions");
			if (!extensions.isEmpty()) {
				for (Entry<String, String> s : extensions.entrySet()) {
					writeNotNullText(serializer,"osmand:" + s.getKey(), s.getValue());
				}
			}
			if (extensionsWriter != null) {
				extensionsWriter.writeExtensions(serializer);
			}
			serializer.endTag(null, "extensions");
		}
	}

	private static void writeWpt(SimpleDateFormat format, XmlSerializer serializer, WptPt p) throws IOException {
		serializer.attribute(null, "lat", latLonFormat.format(p.lat)); //$NON-NLS-1$ //$NON-NLS-2$
		serializer.attribute(null, "lon", latLonFormat.format(p.lon)); //$NON-NLS-1$ //$NON-NLS-2$

		if (!Double.isNaN(p.ele)) {
			writeNotNullText(serializer, "ele", decimalFormat.format(p.ele));
		}
		if (p.time != 0) {
			writeNotNullText(serializer, "time", format.format(new Date(p.time)));
		}
		writeNotNullText(serializer, "name", p.name);
		writeNotNullText(serializer, "desc", p.desc);
		writeNotNullTextWithAttribute(serializer, "link", "href", p.link);
		writeNotNullText(serializer, "type", p.category);
		if (p.comment != null) {
			writeNotNullText(serializer, "cmt", p.comment);
		}
		if (!Double.isNaN(p.hdop)) {
			writeNotNullText(serializer, "hdop", decimalFormat.format(p.hdop));
		}
		if (p.speed > 0) {
			p.getExtensionsToWrite().put("speed", decimalFormat.format(p.speed));
		}
		if (!Float.isNaN(p.heading)) {
			p.getExtensionsToWrite().put("heading", String.valueOf(Math.round(p.heading)));
		}
		Map<String, String> extensions = p.getExtensionsToRead();
		if (!"rtept".equals(serializer.getName())) {
			// Leave "profile" and "trkpt" tags for rtept only
			extensions.remove(PROFILE_TYPE_EXTENSION);
			extensions.remove(TRKPT_INDEX_EXTENSION);
			writeExtensions(serializer, extensions, p);
		} else {
			// Remove "gap" profile
			String profile = extensions.get(PROFILE_TYPE_EXTENSION);
			if (GAP_PROFILE_TYPE.equals(profile)) {
				extensions.remove(PROFILE_TYPE_EXTENSION);
			}
			writeExtensions(serializer, p);
		}
	}

	private static void writeAuthor(XmlSerializer serializer, Author author) throws IOException {
		writeNotNullText(serializer, "name", author.name);
		if (author.email != null && author.email.contains("@")) {
			String[] idAndDomain = author.email.split("@");
			if (idAndDomain.length == 2 && !idAndDomain[0].isEmpty() && !idAndDomain[1].isEmpty()) {
				serializer.startTag(null, "email");
				serializer.attribute(null, "id", idAndDomain[0]);
				serializer.attribute(null, "domain", idAndDomain[1]);
				serializer.endTag(null, "email");
			}
		}
		writeNotNullTextWithAttribute(serializer, "link", "href", author.link);
	}

	private static void writeCopyright(XmlSerializer serializer, Copyright copyright) throws IOException {
		serializer.attribute(null, "author", copyright.author);
		writeNotNullText(serializer, "year", copyright.year);
		writeNotNullText(serializer, "license", copyright.license);
	}

	private static void writeBounds(XmlSerializer serializer, Bounds bounds) throws IOException {
		serializer.startTag(null, "bounds");
		serializer.attribute(null, "minlat", latLonFormat.format(bounds.minlat));
		serializer.attribute(null, "minlon", latLonFormat.format(bounds.minlon));
		serializer.attribute(null, "maxlat", latLonFormat.format(bounds.maxlat));
		serializer.attribute(null, "maxlon", latLonFormat.format(bounds.maxlon));
		serializer.endTag(null, "bounds");
	}

	public static class GPXFileResult {
		public ArrayList<List<Location>> locations = new ArrayList<List<Location>>();
		public ArrayList<WptPt> wayPoints = new ArrayList<>();
		// special case for cloudmate gpx : they discourage common schema
		// by using waypoint as track points and rtept are not very close to real way
		// such as wpt. However they provide additional information into gpx.
		public boolean cloudMadeFile;
		public String error;

		public Location findFistLocation() {
			for (List<Location> l : locations) {
				for (Location ls : l) {
					if (ls != null) {
						return ls;
					}
				}
			}
			return null;
		}
	}

	public static String readText(XmlPullParser parser, String key) throws XmlPullParserException, IOException {
		int tok;
		StringBuilder text = null;
		while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
			if (tok == XmlPullParser.END_TAG && parser.getName().equals(key)) {
				break;
			} else if (tok == XmlPullParser.TEXT) {
				if (text == null) {
					text = new StringBuilder(parser.getText());
				} else {
					text.append(parser.getText());
				}
			}
		}
		return text == null ? null : text.toString();
	}

	private static Map<String, String> readTextMap(XmlPullParser parser, String key)
			throws XmlPullParserException, IOException {
		int tok;
		StringBuilder text = null;
		Map<String, String> result = new HashMap<>();
		while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
			if (tok == XmlPullParser.END_TAG) {
				String tag = parser.getName();
				if (text != null && !Algorithms.isEmpty(text.toString().trim())) {
					result.put(tag, text.toString());
				}
				if (tag.equals(key)) {
					break;
				}
				text = null;
			} else if (tok == XmlPullParser.START_TAG) {
				text = null;
			} else if (tok == XmlPullParser.TEXT) {
				if (text == null) {
					text = new StringBuilder(parser.getText());
				} else {
					text.append(parser.getText());
				}
			}
		}
		return result;
	}

	private static long parseTime(String text,SimpleDateFormat format,SimpleDateFormat formatMillis) {
		long time = 0;
		if (text != null) {
			try {
				time = format.parse(text).getTime();
			} catch (ParseException e1) {
				try {
					time = formatMillis.parse(text).getTime();
				} catch (ParseException e2) {

				}
			}
		}
		return time;
	}

	public static GPXFile loadGPXFile(File file) {
		return loadGPXFile(file, null);
	}

	public static GPXFile loadGPXFile(File file, GPXExtensionsReader extensionsReader) {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			GPXFile gpxFile = loadGPXFile(fis, extensionsReader);
			gpxFile.path = file.getAbsolutePath();
			gpxFile.modifiedTime = file.lastModified();

			Algorithms.closeStream(fis);
			return gpxFile;
		} catch (IOException e) {
			GPXFile gpxFile = new GPXFile(null);
			gpxFile.path = file.getAbsolutePath();
			log.error("Error reading gpx " + gpxFile.path, e); //$NON-NLS-1$
			gpxFile.error = e;
			return gpxFile;
		} finally {
			Algorithms.closeStream(fis);
		}
	}

	public static GPXFile loadGPXFile(InputStream stream) {
		return loadGPXFile(stream, null);
	}

	public static GPXFile loadGPXFile(InputStream stream, GPXExtensionsReader extensionsReader) {
		GPXFile gpxFile = new GPXFile(null);
		SimpleDateFormat format = new SimpleDateFormat(GPX_TIME_FORMAT, Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		SimpleDateFormat formatMillis = new SimpleDateFormat(GPX_TIME_FORMAT_MILLIS, Locale.US);
		formatMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
		try {
			XmlPullParser parser = PlatformUtil.newXMLPullParser();
			parser.setInput(getUTF8Reader(stream));
			Track routeTrack = new Track();
			TrkSegment routeTrackSegment = new TrkSegment();
			routeTrack.segments.add(routeTrackSegment);
			Stack<GPXExtensions> parserState = new Stack<>();
			TrkSegment firstSegment = null;
			boolean extensionReadMode = false;
			boolean routePointExtension = false;
			List<RouteSegment> routeSegments = new ArrayList<>();
			List<RouteType> routeTypes = new ArrayList<>();
			boolean routeExtension = false;
			boolean typesExtension = false;
			parserState.push(gpxFile);
			int tok;
			while ((tok = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if (tok == XmlPullParser.START_TAG) {
					GPXExtensions parse = parserState.peek();
					String tag = parser.getName();
					if (extensionReadMode && parse != null && !routePointExtension) {
						String tagName = tag.toLowerCase();
						if (routeExtension) {
							if (tagName.equals("segment")) {
								RouteSegment segment = parseRouteSegmentAttributes(parser);
								routeSegments.add(segment);
							}
						} else if (typesExtension) {
							if (tagName.equals("type")) {
								RouteType type = parseRouteTypeAttributes(parser);
								routeTypes.add(type);
							}
						}
						switch (tagName) {
							case "routepointextension":
								routePointExtension = true;
								if (parse instanceof WptPt) {
									parse.getExtensionsToWrite().put("offset", routeTrackSegment.points.size() + "");
								}
								break;

							case "route":
								routeExtension = true;
								break;

							case "types":
								typesExtension = true;
								break;

							default:
								if (extensionsReader == null || !extensionsReader.readExtensions(gpxFile, parser)) {
									Map<String, String> values = readTextMap(parser, tag);
									if (values.size() > 0) {
										for (Entry<String, String> entry : values.entrySet()) {
											String t = entry.getKey().toLowerCase();
											String value = entry.getValue();
											parse.getExtensionsToWrite().put(t, value);
											if (tag.equals("speed") && parse instanceof WptPt) {
												try {
													((WptPt) parse).speed = Float.parseFloat(value);
												} catch (NumberFormatException e) {
													log.debug(e.getMessage(), e);
												}
											}
										}
									}
								}
								break;
						}
					} else if (parse != null && tag.equals("extensions")) {
						extensionReadMode = true;
					} else if (routePointExtension) {
						if (tag.equals("rpt")) {
							WptPt wptPt = parseWptAttributes(parser);
							routeTrackSegment.points.add(wptPt);
							parserState.push(wptPt);
						}
					} else {
						if (parse instanceof GPXFile) {
							if (tag.equals("gpx")) {
								((GPXFile) parse).author = parser.getAttributeValue("", "creator");
							}
							if (tag.equals("metadata")) {
								Metadata metadata = new Metadata();
								((GPXFile) parse).metadata = metadata;
								parserState.push(metadata);
							}
							if (tag.equals("trk")) {
								Track track = new Track();
								((GPXFile) parse).tracks.add(track);
								parserState.push(track);
							}
							if (tag.equals("rte")) {
								Route route = new Route();
								((GPXFile) parse).routes.add(route);
								parserState.push(route);
							}
							if (tag.equals("wpt")) {
								WptPt wptPt = parseWptAttributes(parser);
								((GPXFile) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
						} else if (parse instanceof Metadata) {
							if (tag.equals("name")) {
								((Metadata) parse).name = readText(parser, "name");
							}
							if (tag.equals("desc")) {
								((Metadata) parse).desc = readText(parser, "desc");
							}
							if (tag.equals("author")) {
								Author author = new Author();
								author.name = parser.getText();
								((Metadata) parse).author = author;
								parserState.push(author);
							}
							if (tag.equals("copyright")) {
								Copyright copyright = new Copyright();
								copyright.license = parser.getText();
								copyright.author = parser.getAttributeValue("", "author");
								((Metadata) parse).copyright = copyright;
								parserState.push(copyright);
							}
							if (tag.equals("link")) {
								((Metadata) parse).link = parser.getAttributeValue("", "href");
							}
							if (tag.equals("time")) {
								String text = readText(parser, "time");
								((Metadata) parse).time = parseTime(text, format, formatMillis);
							}
							if (tag.equals("keywords")) {
								((Metadata) parse).keywords = readText(parser, "keywords");
							}
							if (tag.equals("bounds")) {
								Bounds bounds = parseBoundsAttributes(parser);
								((Metadata) parse).bounds = bounds;
								parserState.push(bounds);
							}
						} else if (parse instanceof Author) {
							if (tag.equals("name")) {
								((Author) parse).name = readText(parser, "name");
							}
							if (tag.equals("email")) {
								String id = parser.getAttributeValue("", "id");
								String domain = parser.getAttributeValue("", "domain");
								if (!Algorithms.isEmpty(id) && !Algorithms.isEmpty(domain)) {
									((Author) parse).email = id + "@" + domain;
								}
							}
							if (tag.equals("link")) {
								((Author) parse).link = parser.getAttributeValue("", "href");
							}
						} else if (parse instanceof Copyright) {
							if (tag.equals("year")) {
								((Copyright) parse).year = readText(parser, "year");
							}
							if (tag.equals("license")) {
								((Copyright) parse).license = readText(parser, "license");
							}
						} else if (parse instanceof Route) {
							if (tag.equals("name")) {
								((Route) parse).name = readText(parser, "name");
							}
							if (tag.equals("desc")) {
								((Route) parse).desc = readText(parser, "desc");
							}
							if (tag.equals("rtept")) {
								WptPt wptPt = parseWptAttributes(parser);
								((Route) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
						} else if (parse instanceof Track) {
							if (tag.equals("name")) {
								((Track) parse).name = readText(parser, "name");
							} else if (tag.equals("desc")) {
								((Track) parse).desc = readText(parser, "desc");
							} else if (tag.equals("trkseg")) {
								TrkSegment trkSeg = new TrkSegment();
								((Track) parse).segments.add(trkSeg);
								parserState.push(trkSeg);
							} else if (tag.equals("trkpt") || tag.equals("rpt")) {
								WptPt wptPt = parseWptAttributes(parser);
								int size = ((Track) parse).segments.size();
								if (size == 0) {
									((Track) parse).segments.add(new TrkSegment());
									size++;
								}
								((Track) parse).segments.get(size - 1).points.add(wptPt);
								parserState.push(wptPt);
							}
						} else if (parse instanceof TrkSegment) {
							if (tag.equals("trkpt") || tag.equals("rpt")) {
								WptPt wptPt = parseWptAttributes(parser);
								((TrkSegment) parse).points.add(wptPt);
								parserState.push(wptPt);
							}
							if (tag.equals("csvattributes")) {
								String segmentPoints = readText(parser, "csvattributes");
								String[] pointsArr = segmentPoints.split("\n");
								for (int i = 0; i < pointsArr.length; i++) {
									String[] pointAttrs = pointsArr[i].split(",");
									try {
										int arrLength = pointsArr.length;
										if (arrLength > 1) {
											WptPt wptPt = new WptPt();
											wptPt.lon = Double.parseDouble(pointAttrs[0]);
											wptPt.lat = Double.parseDouble(pointAttrs[1]);
											((TrkSegment) parse).points.add(wptPt);
											if (arrLength > 2) {
												wptPt.ele = Double.parseDouble(pointAttrs[2]);
											}
										}
									} catch (NumberFormatException e) {
									}
								}
							}
							// main object to parse
						} else if (parse instanceof WptPt) {
							if (tag.equals("name")) {
								((WptPt) parse).name = readText(parser, "name");
							} else if (tag.equals("desc")) {
								((WptPt) parse).desc = readText(parser, "desc");
							} else if (tag.equals("cmt")) {
								((WptPt) parse).comment = readText(parser, "cmt");
							} else if (tag.equals("speed")) {
								try {
									String value = readText(parser, "speed");
									if (!Algorithms.isEmpty(value)) {
										((WptPt) parse).speed = Float.parseFloat(value);
										parse.getExtensionsToWrite().put("speed", value);
									}
								} catch (NumberFormatException e) {
								}
							} else if (tag.equals("link")) {
								((WptPt) parse).link = parser.getAttributeValue("", "href");
							} else if (tag.equals("category")) {
								((WptPt) parse).category = readText(parser, "category");
							} else if (tag.equals("type")) {
								if (((WptPt) parse).category == null) {
									((WptPt) parse).category = readText(parser, "type");
								}
							} else if (tag.equals("ele")) {
								String text = readText(parser, "ele");
								if (text != null) {
									try {
										((WptPt) parse).ele = Float.parseFloat(text);
									} catch (NumberFormatException e) {
									}
								}
							} else if (tag.equals("hdop")) {
								String text = readText(parser, "hdop");
								if (text != null) {
									try {
										((WptPt) parse).hdop = Float.parseFloat(text);
									} catch (NumberFormatException e) {
									}
								}
							} else if (tag.equals("time")) {
								String text = readText(parser, "time");
								((WptPt) parse).time = parseTime(text, format, formatMillis);
							}
						}
					}

				} else if (tok == XmlPullParser.END_TAG) {
					Object parse = parserState.peek();
					String tag = parser.getName();

					if (tag.toLowerCase().equals("routepointextension")) {
						routePointExtension = false;
					}
					if (parse != null && tag.equals("extensions")) {
						extensionReadMode = false;
					}
					if (extensionReadMode && tag.equals("route")) {
						routeExtension = false;
						continue;
					}
					if (extensionReadMode && tag.equals("types")) {
						typesExtension = false;
						continue;
					}

					if (tag.equals("metadata")) {
						Object pop = parserState.pop();
						assert pop instanceof Metadata;
					} else if (tag.equals("author")) {
						if (parse instanceof Author) {
							parserState.pop();
						}
					} else if (tag.equals("copyright")) {
						if (parse instanceof Copyright) {
							parserState.pop();
						}
					} else if (tag.equals("bounds")) {
						if (parse instanceof Bounds) {
							parserState.pop();
						}
					} else if (tag.equals("trkpt")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("wpt")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("rtept")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					} else if (tag.equals("trk")) {
						Object pop = parserState.pop();
						assert pop instanceof Track;
					} else if (tag.equals("rte")) {
						Object pop = parserState.pop();
						assert pop instanceof Route;
					} else if (tag.equals("trkseg")) {
						Object pop = parserState.pop();
						if (pop instanceof TrkSegment) {
							TrkSegment segment = (TrkSegment) pop;
							segment.routeSegments = routeSegments;
							segment.routeTypes = routeTypes;
							routeSegments = new ArrayList<>();
							routeTypes = new ArrayList<>();
							if (firstSegment == null) {
								firstSegment = segment;
							}
						}
						assert pop instanceof TrkSegment;
					} else if (tag.equals("rpt")) {
						Object pop = parserState.pop();
						assert pop instanceof WptPt;
					}
				}
			}
			if (!routeTrackSegment.points.isEmpty()) {
				gpxFile.tracks.add(routeTrack);
			}
			if (!routeSegments.isEmpty() && !routeTypes.isEmpty() && firstSegment != null) {
				firstSegment.routeSegments = routeSegments;
				firstSegment.routeTypes = routeTypes;
			}
		gpxFile.addGeneralTrack();
		} catch (Exception e) {
			gpxFile.error = e;
			log.error("Error reading gpx", e); //$NON-NLS-1$
		}
		return gpxFile;
	}

	private static Reader getUTF8Reader(InputStream f) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(f);
		assert bis.markSupported();
		bis.mark(3);
		boolean reset = true;
		byte[] t = new byte[3];
		bis.read(t);
		if (t[0] == ((byte) 0xef) && t[1] == ((byte) 0xbb) && t[2] == ((byte) 0xbf)) {
			reset = false;
		}
		if (reset) {
			bis.reset();
		}
		return new InputStreamReader(bis, "UTF-8");
	}

	private static WptPt parseWptAttributes(XmlPullParser parser) {
		WptPt wpt = new WptPt();
		try {
			wpt.lat = Double.parseDouble(parser.getAttributeValue("", "lat")); //$NON-NLS-1$ //$NON-NLS-2$
			wpt.lon = Double.parseDouble(parser.getAttributeValue("", "lon")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (NumberFormatException e) {
			// ignore
		}
		return wpt;
	}

	private static RouteSegment parseRouteSegmentAttributes(XmlPullParser parser) {
		RouteSegment segment = new RouteSegment();
		segment.id = parser.getAttributeValue("", "id");
		segment.length = parser.getAttributeValue("", "length");
		segment.segmentTime = parser.getAttributeValue("", "segmentTime");
		segment.speed = parser.getAttributeValue("", "speed");
		segment.turnType = parser.getAttributeValue("", "turnType");
		segment.turnAngle = parser.getAttributeValue("", "turnAngle");
		segment.types = parser.getAttributeValue("", "types");
		segment.pointTypes = parser.getAttributeValue("", "pointTypes");
		segment.names = parser.getAttributeValue("", "names");
		return segment;
	}

	private static RouteType parseRouteTypeAttributes(XmlPullParser parser) {
		RouteType type = new RouteType();
		type.tag = parser.getAttributeValue("", "t");
		type.value = parser.getAttributeValue("", "v");
		return type;
	}

	private static Bounds parseBoundsAttributes(XmlPullParser parser) {
		Bounds bounds = new Bounds();
		try {
			String minlat = parser.getAttributeValue("", "minlat");
			String minlon = parser.getAttributeValue("", "minlon");
			String maxlat = parser.getAttributeValue("", "maxlat");
			String maxlon = parser.getAttributeValue("", "maxlon");

			if (minlat == null) {
				minlat = parser.getAttributeValue("", "minLat");
			}
			if (minlon == null) {
				minlon = parser.getAttributeValue("", "minLon");
			}
			if (maxlat == null) {
				maxlat = parser.getAttributeValue("", "maxLat");
			}
			if (maxlon == null) {
				maxlon = parser.getAttributeValue("", "maxLon");
			}

			if (minlat != null) {
				bounds.minlat = Double.parseDouble(minlat);
			}
			if (minlon != null) {
				bounds.minlon = Double.parseDouble(minlon);
			}
			if (maxlat != null) {
				bounds.maxlat = Double.parseDouble(maxlat);
			}
			if (maxlon != null) {
				bounds.maxlon = Double.parseDouble(maxlon);
			}
		} catch (NumberFormatException e) {
			// ignore
		}
		return bounds;
	}
	
	public static void mergeGPXFileInto(GPXFile to, GPXFile from) {
		if (from == null) {
			return;
		}
		if (from.showCurrentTrack) {
			to.showCurrentTrack = true;
		}
		if (from.points != null) {
			to.points.addAll(from.points);
		}
		if (from.tracks != null) {
			to.tracks.addAll(from.tracks);
		}
		if (from.routes != null) {
			to.routes.addAll(from.routes);
		}
		if (from.error != null) {
			to.error = from.error;
		}
	}
}
