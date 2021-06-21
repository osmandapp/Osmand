package net.osmand.map;

import net.osmand.OsmAndCollator;
import net.osmand.PlatformUtil;
import net.osmand.ResultMatcher;
import net.osmand.binary.BinaryMapDataObject;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.BinaryMapIndexReader.MapIndex;
import net.osmand.binary.BinaryMapIndexReader.TagValuePair;
import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;
import net.osmand.data.QuadTree;
import net.osmand.util.Algorithms;
import net.osmand.util.MapAlgorithms;
import net.osmand.util.MapUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.array.TIntArrayList;

public class OsmandRegions {

	public static final String MAP_TYPE = "region_map";

	public static final String FIELD_DOWNLOAD_NAME = "download_name";
	public static final String FIELD_NAME = "name";
	public static final String FIELD_NAME_EN = "name:en";
	public static final String FIELD_REGION_PARENT_NAME = "region_parent_name";
	public static final String FIELD_REGION_FULL_NAME = "region_full_name";
	public static final String FIELD_LANG = "region_lang";
	public static final String FIELD_METRIC = "region_metric";
	public static final String FIELD_ROAD_SIGNS = "region_road_signs";
	public static final String FIELD_LEFT_HAND_DRIVING = "region_left_hand_navigation";
	public static final String FIELD_WIKI_LINK = "region_wiki_link";
	public static final String FIELD_POPULATION = "region_population";
	public static final String LOCALE_NAME_DEFAULT_FORMAT = "%1$s %2$s";
	public static final String LOCALE_NAME_REVERSED_FORMAT = "%2$s, %1$s";

	private BinaryMapIndexReader reader;
	private String locale = "en";
	// locale including region
	private String locale2 = null;
	private static final org.apache.commons.logging.Log LOG = PlatformUtil.getLog(OsmandRegions.class);

	WorldRegion worldRegion = new WorldRegion(WorldRegion.WORLD);
	Map<String, WorldRegion> fullNamesToRegionData = new HashMap<String, WorldRegion>();
	Map<String, String> downloadNamesToFullNames = new HashMap<String, String>();
	Map<String, LinkedList<BinaryMapDataObject>> countriesByDownloadName = new HashMap<String, LinkedList<BinaryMapDataObject>>();


	QuadTree<String> quadTree;
	MapIndexFields mapIndexFields;
	RegionTranslation translator;

	private class MapIndexFields {
		Integer parentFullName = null;
		Integer fullNameType = null;
		Integer downloadNameType = null;
		Integer nameEnType = null;
		Integer nameType = null;
		Integer nameLocaleType = null;
		Integer nameLocale2Type = null;
		Integer langType = null;
		Integer metricType = null;
		Integer leftHandDrivingType = null;
		Integer roadSignsType = null;
		Integer wikiLinkType = null;
		Integer populationType = null;

		public String get(Integer tp, BinaryMapDataObject object) {
			if (tp == null) {
				return null;
			}
			return object.getNameByType(tp);
		}
	}


	public void setTranslator(RegionTranslation translator) {
		this.translator = translator;
	}


	
	public BinaryMapIndexReader prepareFile() throws IOException {
		File regions = new File("regions.ocbf");
		// internal version could be updated
//		if (!regions.exists()) {
			InputStream is = OsmandRegions.class.getResourceAsStream("regions.ocbf");
			FileOutputStream fous = new FileOutputStream(regions);
			Algorithms.streamCopy(is, fous);
			fous.close();
//		}
		return prepareFile(regions.getAbsolutePath());
	}
	
	public BinaryMapIndexReader prepareFile(String fileName) throws IOException {
		reader = new BinaryMapIndexReader(new RandomAccessFile(fileName, "r"), new File(fileName));
//		final Collator clt = OsmAndCollator.primaryCollator();
		final Map<String, String> parentRelations = new LinkedHashMap<String, String>();
		final ResultMatcher<BinaryMapDataObject> resultMatcher = new ResultMatcher<BinaryMapDataObject>() {

			@Override
			public boolean publish(BinaryMapDataObject object) {
				initTypes(object);
				int[] types = object.getTypes();
				for (int i = 0; i < types.length; i++) {
					TagValuePair tp = object.getMapIndex().decodeType(types[i]);
					if ("boundary".equals(tp.value)) {
						return false;
					}
				}
				WorldRegion rd = initRegionData(parentRelations, object);
				if (rd == null) {
					return false;
				}
				if (rd.regionDownloadName != null) {
					downloadNamesToFullNames.put(rd.regionDownloadName, rd.regionFullName);
				}
				fullNamesToRegionData.put(rd.regionFullName, rd);
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}
		};
		iterateOverAllObjects(resultMatcher);
		// post process download names
		for (Map.Entry<String, String> e : parentRelations.entrySet()) {
			String fullName = e.getKey();
			String parentFullName = e.getValue();
			// String parentParentFulName = parentRelations.get(parentFullName); // could be used for japan/russia
			WorldRegion rd = fullNamesToRegionData.get(fullName);
			WorldRegion parent = fullNamesToRegionData.get(parentFullName);
			if (parent != null && rd != null) {
				parent.addSubregion(rd);
			}
		}
		structureWorldRegions(new ArrayList<WorldRegion>(fullNamesToRegionData.values()));
		return reader;
	}

	public boolean containsCountry(String name) {
		return countriesByDownloadName.containsKey(name);
	}


	public String getLocaleName(String downloadName, boolean includingParent) {
		return getLocaleName(downloadName, includingParent, false);
	}

	public String getLocaleName(String downloadName, boolean includingParent, boolean reversed) {
		final String lc = downloadName.toLowerCase();
		if (downloadNamesToFullNames.containsKey(lc)) {
			String fullName = downloadNamesToFullNames.get(lc);
			return getLocaleNameByFullName(fullName, includingParent, reversed
					? LOCALE_NAME_REVERSED_FORMAT : LOCALE_NAME_DEFAULT_FORMAT);
		}
		return downloadName.replace('_', ' ');
	}

	public String getLocaleNameByFullName(String fullName, boolean includingParent, String format) {
		WorldRegion rd = fullNamesToRegionData.get(fullName);
		if (rd == null) {
			return fullName.replace('_', ' ');
		}
		if (includingParent && rd.getSuperregion() != null && rd.getSuperregion().getSuperregion() != null) {
			WorldRegion parentParent = rd.getSuperregion().getSuperregion();
			WorldRegion parent = rd.getSuperregion();
			if (parentParent.getRegionId().equals(WorldRegion.WORLD) &&
					!parent.getRegionId().equals(WorldRegion.RUSSIA_REGION_ID)) {
				return rd.getLocaleName();
			}
			if (parentParent.getRegionId().equals(WorldRegion.RUSSIA_REGION_ID)) {
				return String.format(format, parentParent.getLocaleName(), rd.getLocaleName());
			}
			if (parentParent.getRegionId().equals(WorldRegion.JAPAN_REGION_ID)) {
				return String.format(format, parentParent.getLocaleName(), rd.getLocaleName());
			}
			return String.format(format, parent.getLocaleName(), rd.getLocaleName());
		} else {
			return rd.getLocaleName();
		}
	}

	public WorldRegion getWorldRegion() {
		return worldRegion;
	}

	public boolean isInitialized() {
		return reader != null;
	}

	public boolean contain(BinaryMapDataObject bo, int tx, int ty) {
		int t = 0;
		for (int i = 1; i < bo.getPointsLength(); i++) {
			int fx = MapAlgorithms.ray_intersect_x(bo.getPoint31XTile(i - 1),
					bo.getPoint31YTile(i - 1),
					bo.getPoint31XTile(i),
					bo.getPoint31YTile(i), ty);
			if (Integer.MIN_VALUE != fx && tx >= fx) {
				t++;
			}
		}
		return t % 2 == 1;
	}

	public boolean intersect(BinaryMapDataObject bo, int lx, int ty, int rx, int by) {
		// 1. polygon in object 
		if (contain(bo, lx, ty)) {
			return true;
		}
		// 2. object in polygon
		if (bo.getPointsLength() == 0) {
			return false;
		}
		if (bo.getPoint31XTile(0) >= lx && bo.getPoint31XTile(0) <= rx &&
				bo.getPoint31YTile(0) >= ty && bo.getPoint31YTile(0) <= by) {
			return true;
		}

		// 3. find intersection
		for (int i = 1; i < bo.getPointsLength(); i++) {
			int px = bo.getPoint31XTile(i - 1);
			int x = bo.getPoint31XTile(i);
			int py = bo.getPoint31YTile(i - 1);
			int y = bo.getPoint31YTile(i);
			if (x < lx && px < lx) {
				continue;
			} else if (x > rx && px > rx) {
				continue;
			} else if (y > by && py > by) {
				continue;
			} else if (y < ty && py < ty) {
				continue;
			}
			long in = MapAlgorithms.calculateIntersection(px, py, x, y, lx, rx, by, ty);
			if (in != -1) {
				return true;
			}
		}

		return false;
	}

	public static double getArea(BinaryMapDataObject bo) {
		double area = 0.0;
		if (bo.getPointsLength() > 0) {
			for (int i = 1; i < bo.getPointsLength(); i++) {
				double ax = bo.getPoint31XTile(i - 1);
				double bx = bo.getPoint31XTile(i);
				double ay = bo.getPoint31YTile(i - 1);
				double by = bo.getPoint31YTile(i);
				area += (bx + ax) * (by - ay) / 1.631E10;
			}
		}
		return Math.abs(area);
	}

	private List<BinaryMapDataObject> getCountries(int lx, int rx, int ty, int by,  final boolean checkCenter) throws IOException {
		HashSet<String> set = new HashSet<String>(quadTree.queryInBox(new QuadRect(lx, ty, rx, by),
				new ArrayList<String>()));
		List<BinaryMapDataObject> result = new ArrayList<BinaryMapDataObject>();
		Iterator<String> it = set.iterator();
		int mx = lx / 2 + rx / 2;
		int my = ty / 2 + by / 2;
		while (it.hasNext()) {
			String cname = it.next();
			BinaryMapDataObject container = null;
			int count = 0;
			for (BinaryMapDataObject bo : countriesByDownloadName.get(cname)) {
				if (!checkCenter || contain(bo, mx, my)) {
					count++;
					container = bo;
					break;
				}
			}
			if (count % 2 == 1) {
				result.add(container);
			}
		}
		return result;
	}
	
	public String getCountryName(LatLon ll) {
		double lat = ll.getLatitude();
		double lon = ll.getLongitude();
		int y = MapUtils.get31TileNumberY(lat);
		int x = MapUtils.get31TileNumberX(lon);
		try {
			List<BinaryMapDataObject> list = query(x, y);
			for(BinaryMapDataObject o : list) {
				if(contain(o, x, y)) {
					String name = mapIndexFields.get(mapIndexFields.nameType, o);
					if(name != null) {
						return name;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<BinaryMapDataObject> query(int lx, int rx, int ty, int by) throws IOException {
		return query(lx, rx, ty, by, true);
	}

	public List<BinaryMapDataObject> query(int lx, int rx, int ty, int by, boolean checkCenter) throws IOException {
		if (quadTree != null) {
			return getCountries(lx, rx, ty, by, checkCenter);
		}
		return queryBboxNoInit(lx, rx, ty, by, checkCenter);
	}
	
	
	public List<BinaryMapDataObject> query(final int tile31x, final int tile31y) throws IOException {
		if (quadTree != null) {
			return getCountries(tile31x, tile31x, tile31y, tile31y, true);
		}
		return queryBboxNoInit(tile31x, tile31x, tile31y, tile31y, true);
	}

	
	private synchronized List<BinaryMapDataObject> queryBboxNoInit(int lx, int rx, int ty, int by, final boolean checkCenter) throws IOException {
		final List<BinaryMapDataObject> result = new ArrayList<BinaryMapDataObject>();
		final int mx = lx / 2 + rx / 2;
		final int my = ty / 2 + by / 2;
		BinaryMapIndexReader.SearchRequest<BinaryMapDataObject> sr = BinaryMapIndexReader.buildSearchRequest(lx, rx, ty, by,
				5, new BinaryMapIndexReader.SearchFilter() {
					@Override
					public boolean accept(TIntArrayList types, BinaryMapIndexReader.MapIndex index) {
						return true;
					}
				}, new ResultMatcher<BinaryMapDataObject>() {

					@Override
					public boolean publish(BinaryMapDataObject object) {
						if (object.getPointsLength() < 1) {
							return false;
						}
						initTypes(object);
						if (!checkCenter || contain(object, mx, my)) {
							result.add(object);
						}
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				}
		);
		sr.log = false;
		if (reader != null) {
			reader.searchMapIndex(sr);
		} else {
			throw new IOException("Reader == null");
		}
		return result;
	}

	public void setLocale(String locale) {
		setLocale(locale, null);
	}

	public void setLocale(String locale, String country) {
		this.locale = locale;
		// Check locale and give 2 locale names 
		if("zh".equals(locale)) {
			if("TW".equalsIgnoreCase(country)) {
				this.locale2 = "zh-hant";
			} else if("CN".equalsIgnoreCase(country)) {
				this.locale2 = "zh-hans";
			}
		}
	}


	public WorldRegion getRegionData(String fullname) {
		return fullNamesToRegionData.get(fullname);
	}

	public WorldRegion getRegionDataByDownloadName(String downloadName) {
		if (downloadName == null) {
			return null;
		} else {
			return getRegionData(downloadNamesToFullNames.get(downloadName.toLowerCase()));
		}
	}

	public String getDownloadName(BinaryMapDataObject o) {
		return mapIndexFields.get(mapIndexFields.downloadNameType, o);
	}

	public String getFullName(BinaryMapDataObject o) {
		return mapIndexFields.get(mapIndexFields.fullNameType, o);
	}

	public List<WorldRegion> getAllRegionData() {
		return new ArrayList<WorldRegion>(fullNamesToRegionData.values());
	}


	private WorldRegion initRegionData(final Map<String, String> parentRelations, BinaryMapDataObject object) {
		String regionDownloadName = mapIndexFields.get(mapIndexFields.downloadNameType, object);
		String regionFullName = mapIndexFields.get(mapIndexFields.fullNameType, object);
		if (Algorithms.isEmpty(regionFullName)) {
			return null;
		}
		WorldRegion rd = new WorldRegion(regionFullName, regionDownloadName);
		double cx = 0;
		double cy = 0;
		for (int i = 0; i < object.getPointsLength(); i++) {
			cx += object.getPoint31XTile(i);
			cy += object.getPoint31YTile(i);
		}
		if (object.getPointsLength() > 0) {
			cx /= object.getPointsLength();
			cy /= object.getPointsLength();
			rd.regionCenter = new LatLon(MapUtils.get31LatitudeY((int) cy), MapUtils.get31LongitudeX((int) cx));
			findBoundaries(rd, object);
		}

		rd.regionParentFullName = mapIndexFields.get(mapIndexFields.parentFullName, object);
		if (!Algorithms.isEmpty(rd.regionParentFullName)) {
			parentRelations.put(rd.regionFullName, rd.regionParentFullName);
		}
		rd.regionName = mapIndexFields.get(mapIndexFields.nameType, object);
		if(mapIndexFields.nameLocale2Type != null) {
			rd.regionNameLocale = mapIndexFields.get(mapIndexFields.nameLocale2Type, object);
		}
		if (rd.regionNameLocale == null) {
			rd.regionNameLocale = mapIndexFields.get(mapIndexFields.nameLocaleType, object);
		}
		rd.regionNameEn = mapIndexFields.get(mapIndexFields.nameEnType, object);
		rd.params.regionLang = mapIndexFields.get(mapIndexFields.langType, object);
		rd.params.regionLeftHandDriving = mapIndexFields.get(mapIndexFields.leftHandDrivingType, object);
		rd.params.regionMetric = mapIndexFields.get(mapIndexFields.metricType, object);
		rd.params.regionRoadSigns = mapIndexFields.get(mapIndexFields.roadSignsType, object);
		rd.params.wikiLink = mapIndexFields.get(mapIndexFields.wikiLinkType, object);
		rd.params.population = mapIndexFields.get(mapIndexFields.populationType, object);
		rd.regionSearchText = getSearchIndex(object);
		rd.regionMapDownload = isDownloadOfType(object, MAP_TYPE);
		return rd;
	}

	private void findBoundaries(WorldRegion rd, BinaryMapDataObject object) {
		if (object.getPointsLength() == 0) {
			return;
		}

		List<LatLon> polygon = new ArrayList<>();
		double currentX = object.getPoint31XTile(0);
		double currentY = object.getPoint31YTile(0);
		polygon.add(new LatLon(currentX, currentY));
		double minX = currentX;
		double maxX = currentX;
		double minY = currentY;
		double maxY = currentY;

		if (object.getPointsLength() > 1) {
			for (int i = 1; i < object.getPointsLength(); i++) {
				currentX = object.getPoint31XTile(i);
				currentY = object.getPoint31YTile(i);
				if (currentX > maxX) {
					maxX = currentX;
				} else if (currentX < minX) {
					minX = currentX;
				}
				if (currentY > maxY) {
					maxY = currentY;
				} else if (currentY < minY) {
					minY = currentY;
				}
				polygon.add(new LatLon(currentX, currentY));
			}
		}

		minX = MapUtils.get31LongitudeX((int) minX);
		maxX = MapUtils.get31LongitudeX((int) maxX);
		double revertedMinY = MapUtils.get31LatitudeY((int) maxY);
		double revertedMaxY = MapUtils.get31LatitudeY((int) minY);

		rd.boundingBox = new QuadRect(minX, revertedMinY, maxX, revertedMaxY);
		rd.polygon = polygon;
	}

	private String getSearchIndex(BinaryMapDataObject object) {
		MapIndex mi = object.getMapIndex();
		TIntObjectIterator<String> it = object.getObjectNames().iterator();
		StringBuilder ind = new StringBuilder();
		while (it.hasNext()) {
			it.advance();
			TagValuePair tp = mi.decodeType(it.key());
			if (tp.tag.startsWith("name") || tp.tag.equals("key_name")) {
				final String vl = it.value().toLowerCase();
//				if (!CollatorStringMatcher.ccontains(clt, ind.toString(), vl)) {
				if (ind.indexOf(vl) == -1) {
					ind.append(" ").append(vl);
				}
			}
		}
		return ind.toString();
	}


	public boolean isDownloadOfType(BinaryMapDataObject object, String type) {
		int[] addtypes = object.getAdditionalTypes();
		for (int i = 0; i < addtypes.length; i++) {
			TagValuePair tp = object.getMapIndex().decodeType(addtypes[i]);
			if (type.equals(tp.tag) && "yes".equals(tp.value)) {
				return true;
			}
		}
		return false;
	}


	public Map<String, LinkedList<BinaryMapDataObject>> cacheAllCountries() throws IOException {
		quadTree = new QuadTree<String>(new QuadRect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE),
				8, 0.55f);
		final ResultMatcher<BinaryMapDataObject> resultMatcher = new ResultMatcher<BinaryMapDataObject>() {
//			int c = 0;
			@Override
			public boolean publish(BinaryMapDataObject object) {
				if (object.getPointsLength() < 1) {
					return false;
				}
				initTypes(object);
				String nm = mapIndexFields.get(mapIndexFields.downloadNameType, object);
				if (!countriesByDownloadName.containsKey(nm)) {
					LinkedList<BinaryMapDataObject> ls = new LinkedList<BinaryMapDataObject>();
					countriesByDownloadName.put(nm, ls);
					ls.add(object);
				} else {
					countriesByDownloadName.get(nm).add(object);
				}
				int maxx = object.getPoint31XTile(0);
				int maxy = object.getPoint31YTile(0);
				int minx = maxx;
				int miny = maxy;
				for (int i = 1; i < object.getPointsLength(); i++) {
					int x = object.getPoint31XTile(i);
					int y = object.getPoint31YTile(i);
					if (y < miny) {
						miny = y;
					} else if (y > maxy) {
						maxy = y;
					}
					if (x < minx) {
						minx = x;
					} else if (x > maxx) {
						maxx = x;
					}
				}
				quadTree.insert(nm, new QuadRect(minx, miny, maxx, maxy));
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}
		};
		iterateOverAllObjects(resultMatcher);
		return countriesByDownloadName;
	}

	private synchronized void iterateOverAllObjects(final ResultMatcher<BinaryMapDataObject> resultMatcher) throws IOException {
		BinaryMapIndexReader.SearchRequest<BinaryMapDataObject> sr = BinaryMapIndexReader.buildSearchRequest(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
				5, new BinaryMapIndexReader.SearchFilter() {
					@Override
					public boolean accept(TIntArrayList types, BinaryMapIndexReader.MapIndex index) {
						return true;
					}
				}, resultMatcher);
		if (reader != null) {
			reader.searchMapIndex(sr);
		}
	}


	private void initTypes(BinaryMapDataObject object) {
		if (mapIndexFields == null) {
			mapIndexFields = new MapIndexFields();
			// mapIndexFields.mapIndex = object.getMapIndex();
			mapIndexFields.downloadNameType = object.getMapIndex().getRule(FIELD_DOWNLOAD_NAME, null);
			mapIndexFields.nameType = object.getMapIndex().getRule(FIELD_NAME, null);
			mapIndexFields.nameEnType = object.getMapIndex().getRule(FIELD_NAME_EN, null);
			mapIndexFields.nameLocaleType = object.getMapIndex().getRule(FIELD_NAME + ":" + locale, null);
			if(locale2 != null) {
				mapIndexFields.nameLocale2Type = object.getMapIndex().getRule(FIELD_NAME + ":" + locale2, null);
			}
			mapIndexFields.parentFullName = object.getMapIndex().getRule(FIELD_REGION_PARENT_NAME, null);
			mapIndexFields.fullNameType = object.getMapIndex().getRule(FIELD_REGION_FULL_NAME, null);
			mapIndexFields.langType = object.getMapIndex().getRule(FIELD_LANG, null);
			mapIndexFields.metricType = object.getMapIndex().getRule(FIELD_METRIC, null);
			mapIndexFields.leftHandDrivingType = object.getMapIndex().getRule(FIELD_LEFT_HAND_DRIVING, null);
			mapIndexFields.roadSignsType = object.getMapIndex().getRule(FIELD_ROAD_SIGNS, null);
			mapIndexFields.wikiLinkType = object.getMapIndex().getRule(FIELD_WIKI_LINK, null);
			mapIndexFields.populationType = object.getMapIndex().getRule(FIELD_POPULATION, null);
		}
	}


	private static void testCountry(OsmandRegions or, double lat, double lon, String... test) throws IOException {
		long t = System.currentTimeMillis();
		List<BinaryMapDataObject> cs = or.query(MapUtils.get31TileNumberX(lon), MapUtils.get31TileNumberY(lat));
		Set<String> expected = new TreeSet<String>(Arrays.asList(test));
		Set<String> found = new TreeSet<String>();

		for (BinaryMapDataObject b : cs) {
			String nm = b.getNameByType(or.mapIndexFields.nameEnType);
			if (nm == null) {
				nm = b.getName();
				System.out.println(or.getLocaleName(or.getDownloadName(b), false));
			}
			if (or.isDownloadOfType(b, MAP_TYPE)) {
				found.add(nm.toLowerCase());
				String localName = b.getNameByType(or.mapIndexFields.nameLocaleType);
				if(or.mapIndexFields.nameLocale2Type != null) {
					localName = b.getNameByType(or.mapIndexFields.nameLocale2Type);
				}
				System.out.printf("Region %s %s%n", b.getName(), localName);
			}
		}

		if (!found.equals(expected)) {
			throw new IllegalStateException(" Expected " + expected + " but was " + found);
		}
		System.out.println("Found " + expected + " in " + (System.currentTimeMillis() - t) + " ms");
	}


	public static void main(String[] args) throws IOException {
		OsmandRegions or = new OsmandRegions();
		Locale tw = Locale.CHINA;
		or.setLocale(tw.getLanguage(), null);
//		or.setLocale(tw.getLanguage(), tw.getCountry());
		or.prepareFile("/Users/victorshcherb/osmand/repos/resources/countries-info/regions.ocbf");
		LinkedList<WorldRegion> lst = new LinkedList<WorldRegion>();
		lst.add(or.getWorldRegion());
//		int i =0;
		while (!lst.isEmpty()) {
			WorldRegion wd = lst.pollFirst();
			System.out.println((wd.superregion == null ? "" : wd.superregion.getLocaleName()) + " "
					+ wd.getLocaleName() + " " + wd.getRegionDownloadName());
//			if(i++ <=5)
//				lst.addAll(wd.getSubregions());
		}

		
		or.cacheAllCountries();
//		long t = System.currentTimeMillis();
//		or.cacheAllCountries();
//		System.out.println("Init " + (System.currentTimeMillis() - t));

		testCountry(or, 53.8820, 27.5726, "belarus", "minsk");
//		testCountry(or, 52.10, 4.92, "the netherlands", "utrecht");
//		testCountry(or, 52.15, 7.50, "north rhine-westphalia");
//		testCountry(or, 28.8056, 29.9858, "egypt");
//		testCountry(or, 40.0760, 9.2807, "italy", "sardinia");
//		testCountry(or, 35.7521, 139.7887, "japan");
//		testCountry(or, 46.5145, 102.2580, "mongolia");
//		testCountry(or, 62.54, 43.36, "arkhangelsk oblast", "northwestern federal district");
	}


	public interface RegionTranslation {

		public String getTranslation(String id);
	}

	private void initWorldRegion(WorldRegion world, String id) {
		WorldRegion rg = new WorldRegion(id);
		rg.regionParentFullName = world.regionFullName;
		if (translator != null) {
			rg.regionName = translator.getTranslation(id);
		}
		world.addSubregion(rg);

	}

	public void structureWorldRegions(List<WorldRegion> loadedItems) {
		if (loadedItems.size() == 0) {
			return;
		}
		WorldRegion world = new WorldRegion(WorldRegion.WORLD);
		initWorldRegion(world, WorldRegion.ANTARCTICA_REGION_ID);
		initWorldRegion(world, WorldRegion.AFRICA_REGION_ID);
		initWorldRegion(world, WorldRegion.ASIA_REGION_ID);
		initWorldRegion(world, WorldRegion.CENTRAL_AMERICA_REGION_ID);
		initWorldRegion(world, WorldRegion.EUROPE_REGION_ID);
		initWorldRegion(world, WorldRegion.NORTH_AMERICA_REGION_ID);
		initWorldRegion(world, WorldRegion.RUSSIA_REGION_ID);
		initWorldRegion(world, WorldRegion.SOUTH_AMERICA_REGION_ID);
		initWorldRegion(world, WorldRegion.AUSTRALIA_AND_OCEANIA_REGION_ID);
		Iterator<WorldRegion> it = loadedItems.iterator();
		while (it.hasNext()) {
			WorldRegion region = it.next();
			if (region.superregion == null) {
				boolean found = false;
				for (WorldRegion worldSubregion : world.subregions) {
					if (worldSubregion.getRegionId().equalsIgnoreCase(region.regionFullName)) {
						for (WorldRegion rg : region.subregions) {
							worldSubregion.addSubregion(rg);
						}
						found = true;
						break;
					}
				}
				if (found) {
					it.remove();
				} else if (region.getRegionId().contains("basemap")) {
					it.remove();
				}
			} else {
				it.remove();
			}
		}
		Comparator<WorldRegion> nameComparator = new Comparator<WorldRegion>() {
			final net.osmand.Collator collator = OsmAndCollator.primaryCollator();

			@Override
			public int compare(WorldRegion w1, WorldRegion w2) {
				return collator.compare(w1.getLocaleName(), w2.getLocaleName());
			}
		};
		sortSubregions(world, nameComparator);

		this.worldRegion = world;
		if (loadedItems.size() > 0) {
			LOG.warn("Found orphaned regions: " + loadedItems.size());
			for (WorldRegion regionId : loadedItems) {
				LOG.warn("FullName = " + regionId.regionFullName +
						" parent=" + regionId.regionParentFullName);
			}
		}
	}


	private void sortSubregions(WorldRegion region, Comparator<WorldRegion> comparator) {
		Collections.sort(region.subregions, comparator);
		for (WorldRegion r : region.subregions) {
			if (r.subregions.size() > 0) {
				sortSubregions(r, comparator);
			}
		}
	}

	public List<WorldRegion> getWorldRegionsAt(LatLon latLon) throws IOException {
		Map<WorldRegion, BinaryMapDataObject> mapDataObjects = getBinaryMapDataObjectsWithRegionsAt(latLon);
		return new ArrayList<>(mapDataObjects.keySet());
	}

	public Map.Entry<WorldRegion, BinaryMapDataObject> getSmallestBinaryMapDataObjectAt(LatLon latLon) throws IOException {
		Map<WorldRegion, BinaryMapDataObject> mapDataObjectsWithRegions = getBinaryMapDataObjectsWithRegionsAt(latLon);
		return getSmallestBinaryMapDataObjectAt(mapDataObjectsWithRegions);
	}

	public Map.Entry<WorldRegion, BinaryMapDataObject> getSmallestBinaryMapDataObjectAt(Map<WorldRegion, BinaryMapDataObject> mapDataObjectsWithRegions) {
		Map.Entry<WorldRegion, BinaryMapDataObject> res = null;
		double smallestArea = -1;
		for (Map.Entry<WorldRegion, BinaryMapDataObject> o : mapDataObjectsWithRegions.entrySet()) {
			double area = OsmandRegions.getArea(o.getValue());
			if (smallestArea == -1) {
				smallestArea = area;
				res = o;
			} else if (area < smallestArea) {
				smallestArea = area;
				res = o;
			}
		}
		return res;
	}

	private Map<WorldRegion, BinaryMapDataObject> getBinaryMapDataObjectsWithRegionsAt(LatLon latLon) throws IOException {
		int point31x = MapUtils.get31TileNumberX(latLon.getLongitude());
		int point31y = MapUtils.get31TileNumberY(latLon.getLatitude());
		Map<WorldRegion, BinaryMapDataObject> foundObjects = new LinkedHashMap<>();
		List<BinaryMapDataObject> mapDataObjects;
		try {
			mapDataObjects = queryBboxNoInit(point31x, point31x, point31y, point31y, true);
		} catch (IOException e) {
			throw new IOException("Error while calling queryBbox");
		}
		Iterator<BinaryMapDataObject> it = mapDataObjects.iterator();
		while (it.hasNext()) {
			BinaryMapDataObject o = it.next();
			if (o.getTypes() != null) {
				WorldRegion downloadRegion = getRegionData(getFullName(o));
				if ( downloadRegion == null
						|| !downloadRegion.isRegionMapDownload()
						|| !contain(o, point31x, point31y)) {
					it.remove();
				} else {
					foundObjects.put(downloadRegion, o);
				}
			}
		}
		return foundObjects;
	}



	public List<String> getRegionsToDownload(double lat, double lon, List<String> keyNames) throws IOException {
		keyNames.clear();
		int x31 = MapUtils.get31TileNumberX(lon);
		int y31 = MapUtils.get31TileNumberY(lat);
		List<BinaryMapDataObject> cs = query(x31, y31);
		for (BinaryMapDataObject b : cs) {
			if (contain(b, x31, y31)) {
				String downloadName = getDownloadName(b);
				if(!Algorithms.isEmpty(downloadName)) {
					keyNames.add(downloadName);
				}
			}
		}
		return keyNames;
	}
}
