package net.osmand.plus.resources;

import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.map.ITileSource;
import net.osmand.plus.SQLiteTileSource;
import net.osmand.plus.resources.AsyncLoadingThread.TileLoadDownloadRequest;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class TilesCache<T> {

	private AsyncLoadingThread asyncLoadingThread;
	protected static final Log log = PlatformUtil.getLog(TilesCache.class);

	Map<String, CacheEntry<T>> cache = new LinkedHashMap<>();
	Map<String, Boolean> tilesOnFS = new LinkedHashMap<>();

	protected File dirWithTiles;
	protected int maxCacheSize = 30;

	public TilesCache(AsyncLoadingThread asyncLoadingThread) {
		this.asyncLoadingThread = asyncLoadingThread;
	}

	protected StringBuilder builder = new StringBuilder(40);

	public int getMaxCacheSize() {
		return maxCacheSize;
	}

	public void setMaxCacheSize(int maxCacheSize) {
		this.maxCacheSize = maxCacheSize;
	}

	public File getDirWithTiles() {
		return dirWithTiles;
	}

	public void setDirWithTiles(File dirWithTiles) {
		this.dirWithTiles = dirWithTiles;
	}

	public abstract boolean isTileSourceSupported(ITileSource tileSource);

	public synchronized String calculateTileId(ITileSource map, int x, int y, int zoom) {
		builder.setLength(0);
		if (map == null) {
			builder.append(IndexConstants.TEMP_SOURCE_TO_LOAD);
		} else {
			builder.append(map.getName());
		}

		if (map instanceof SQLiteTileSource) {
			builder.append('@');
		} else {
			builder.append('/');
		}
		builder.append(zoom).append('/').append(x).append('/').append(y).
				append(map == null ? ".jpg" : map.getTileFormat()).append(".tile"); //$NON-NLS-1$ //$NON-NLS-2$
		return builder.toString();
	}

	public synchronized boolean tileExistOnFileSystem(String file, ITileSource map, int x, int y, int zoom) {
		if (!tilesOnFS.containsKey(file)) {
			boolean ex = false;
			if (map instanceof SQLiteTileSource){
				if (((SQLiteTileSource) map).isLocked()){
					return false;
				}
				ex = ((SQLiteTileSource) map).exists(x, y, zoom);
			} else {
				if (file == null){
					file = calculateTileId(map, x, y, zoom);
				}
				ex = new File(dirWithTiles, file).exists();
			}
			if (ex) {
				tilesOnFS.put(file, Boolean.TRUE);
			} else {
				tilesOnFS.put(file, null);
			}
		}
		return tilesOnFS.get(file) != null || cache.get(file) != null;
	}

	public T getTileForMapAsync(String file, ITileSource map, int x, int y, int zoom,
								boolean loadFromInternetIfNeeded, long timestamp) {
		return getTileForMap(file, map, x, y, zoom, loadFromInternetIfNeeded, false, true, timestamp);
	}

	public T getTileForMapSync(String file, ITileSource map, int x, int y, int zoom,
							   boolean loadFromInternetIfNeeded, long timestamp) {
		return getTileForMap(file, map, x, y, zoom, loadFromInternetIfNeeded, true, true, timestamp);
	}

	/**
	 * @param file - null could be passed if you do not call very often with that param
	 */
	protected T getTileForMap(String file, ITileSource map, int x, int y, int zoom,
							  boolean loadFromInternetIfNeeded, boolean sync, boolean loadFromFs,
							  long timestamp) {
		return getTileForMap(file, map, x, y, zoom, loadFromInternetIfNeeded, sync, loadFromFs, false, timestamp);
	}

	protected synchronized T getTileForMap(String tileId, ITileSource map, int x, int y, int zoom,
										   boolean loadFromInternetIfNeeded, boolean sync,
										   boolean loadFromFs, boolean deleteBefore, long timestamp) {
		if (tileId == null) {
			tileId = calculateTileId(map, x, y, zoom);
			if (tileId == null) {
				return null;
			}
		}

		if (deleteBefore) {
			cache.remove(tileId);
			if (map instanceof SQLiteTileSource) {
				((SQLiteTileSource) map).deleteImage(x, y, zoom);
			} else {
				File f = new File(dirWithTiles, tileId);
				if (f.exists()) {
					f.delete();
				}
			}
			tilesOnFS.put(tileId, null);
		}

		if (loadFromFs && cache.get(tileId) == null && map != null) {
			boolean locked = map instanceof SQLiteTileSource && ((SQLiteTileSource) map).isLocked();
			if (!loadFromInternetIfNeeded && !locked && !tileExistOnFileSystem(tileId, map, x, y, zoom)){
				return null;
			}
			String url = loadFromInternetIfNeeded ? map.getUrlToLoad(x, y, zoom) : null;
			File toSave = null;
			if (url != null) {
				if (map instanceof SQLiteTileSource) {
					toSave = new File(dirWithTiles, calculateTileId(((SQLiteTileSource) map).getBase(), x, y, zoom));
				} else {
					toSave = new File(dirWithTiles, tileId);
				}
			}
			TileLoadDownloadRequest req = new TileLoadDownloadRequest(dirWithTiles, url, toSave,
					tileId, map, x, y, zoom, timestamp, map.getReferer(), map.getUserAgent());
			if (sync) {
				return getRequestedTile(req);
			} else {
				asyncLoadingThread.requestToLoadTile(req);
			}
		}
		return get(tileId, timestamp);
	}

	protected T getRequestedTile(TileLoadDownloadRequest req) {
		if (req.tileId == null || req.dirWithTiles == null) {
			return null;
		}
		T cacheObject = get(req.tileId, req.timestamp);
		if (cacheObject != null) {
			if (isExpired(req)) {
				cache.remove(req.tileId);
			} else {
				return cacheObject;
			}
		}
		if (cache.size() > maxCacheSize) {
			clearTiles();
		}
		if (req.dirWithTiles.canRead() && !asyncLoadingThread.isFileCurrentlyDownloaded(req.fileToSave)
				&& !asyncLoadingThread.isFilePendingToDownload(req.fileToSave)) {
			long time = System.currentTimeMillis();
			if (log.isDebugEnabled()) {
				log.debug("Start loaded file : " + req.tileId + " " + Thread.currentThread().getName());
			}

			T tileObject = getTileObject(req);

			if (tileObject != null) {
				put(req.tileId, tileObject, req.timestamp);
				if (log.isDebugEnabled()) {
					log.debug("Loaded file : " + req.tileId + " " + -(time - System.currentTimeMillis()) + " ms " + cache.size());
				}
			}

			if (cache.get(req.tileId) == null && req.url != null) {
				asyncLoadingThread.requestToDownload(req);
			}

		}
		return get(req.tileId, req.timestamp);
	}

	protected abstract T getTileObject(TileLoadDownloadRequest req);

	protected boolean isExpired(TileLoadDownloadRequest req) {
		if (req.tileSource.getExpirationTimeMillis() != -1 && req.url != null && req.dirWithTiles.canRead()) {
			File en = new File(req.dirWithTiles, req.tileId);
			return en.exists() && isExpired(req, en.lastModified());
		}
		return false;
	}

	protected boolean isExpired(TileLoadDownloadRequest req, long lastModified) {
		long time = System.currentTimeMillis();
		long ts = req.tileSource.getExpirationTimeMillis();
		return ts != -1 && req.url != null && time - lastModified > ts;
	}

	protected void downloadIfExpired(TileLoadDownloadRequest req, long lastModified) {
		if (isExpired(req, lastModified)) {
			asyncLoadingThread.requestToDownload(req);
		}
	}

	protected synchronized void clearTiles() {
		log.info("Cleaning tiles - size = " + cache.size());
		List<Map.Entry<String, CacheEntry<T>>> list = new ArrayList<>(cache.entrySet());
		Collections.sort(list, (left, right) -> Long.compare(left.getValue().accessTime, right.getValue().accessTime));
		for (int i = 0; i < list.size() / 2; i++) {
			cache.remove(list.get(i).getKey());
		}
	}

	public synchronized T get(String key, long accessTime) {
		CacheEntry<T> entry = cache.get(key);
		if (entry == null) {
			return null;
		}
		entry.accessTime = accessTime;
		return entry.tile;
	}

	public synchronized void put(String key, T value, long timestamp) {
		cache.put(key, new CacheEntry<>(value, timestamp));
	}

	public synchronized T remove(String key) {
		CacheEntry<T> entry = cache.remove(key);
		return entry == null ? null : entry.tile;
	}

	public synchronized int size() {
		return cache.size();
	}

	public synchronized Set<String> keySet() {
		return cache.keySet();
	}

	public void close() {
		tilesOnFS.clear();
	}

	private static class CacheEntry<T> {

		T tile;
		long accessTime;

		public CacheEntry(T tile, long accessTime) {
			this.tile = tile;
			this.accessTime = accessTime;
		}
	}
}