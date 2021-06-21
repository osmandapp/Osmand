package net.osmand.plus.download;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.LocalIndexInfo;
import net.osmand.plus.helpers.enums.MetricsConstants;
import net.osmand.util.Algorithms;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static net.osmand.IndexConstants.BINARY_SRTM_MAP_INDEX_EXT;
import static net.osmand.IndexConstants.BINARY_SRTM_MAP_INDEX_EXT_ZIP;
import static net.osmand.plus.activities.LocalIndexHelper.LocalIndexType.SRTM_DATA;
import static net.osmand.plus.download.DownloadActivityType.SRTM_COUNTRY_FILE;

public class SrtmDownloadItem extends DownloadItem {

	private final List<IndexItem> indexes;
	private boolean useMetric;

	public SrtmDownloadItem(List<IndexItem> indexes, boolean useMetric) {
		super(SRTM_COUNTRY_FILE);
		this.indexes = indexes;
		this.useMetric = useMetric;
	}

	public void setUseMetric(boolean useMetric) {
		this.useMetric = useMetric;
	}

	public boolean isUseMetric() {
		for (IndexItem index : indexes) {
			if (index.isDownloaded()) {
				return isMetricItem(index);
			}
		}
		return useMetric;
	}

	@NonNull
	public IndexItem getIndexItem() {
		for (IndexItem index : indexes) {
			if (index.isDownloaded()) {
				return index;
			}
		}
		return getDefaultIndexItem();
	}

	@NonNull
	public IndexItem getDefaultIndexItem() {
		for (IndexItem index : indexes) {
			if (useMetric && isMetricItem(index) || !useMetric && !isMetricItem(index)) {
				return index;
			}
		}
		return indexes.get(0);
	}

	@Override
	public double getSizeToDownloadInMb() {
		return getIndexItem().getSizeToDownloadInMb();
	}

	@Override
	public double getArchiveSizeMB() {
		return getIndexItem().getArchiveSizeMB();
	}

	@Override
	public boolean isOutdated() {
		for (DownloadItem item : indexes) {
			if (item.isOutdated()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isDownloaded() {
		for (DownloadItem item : indexes) {
			if (item.isDownloaded()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasActualDataToDownload() {
		for (IndexItem item : indexes) {
			if (!item.hasActualDataToDownload()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isDownloading(@NonNull DownloadIndexesThread thread) {
		for (IndexItem item : indexes) {
			if (thread.isDownloading(item)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getFileName() {
		return getIndexItem().getFileName();
	}

	@NonNull
	@Override
	public List<File> getDownloadedFiles(@NonNull OsmandApplication app) {
		List<File> result = new ArrayList<>();
		for (IndexItem index : indexes) {
			result.addAll(index.getDownloadedFiles(app));
		}
		return result;
	}

	public String getDate(@NonNull DateFormat dateFormat, boolean remote) {
		return getIndexItem().getDate(dateFormat, remote);
	}

	@Override
	public @Nullable String getAdditionalDescription(Context ctx) {
		return getAbbreviationInScopes(ctx, this);
	}

	public static boolean isUseMetricByDefault(@NonNull OsmandApplication app) {
		MetricsConstants metricSystem = app.getSettings().METRIC_SYSTEM.get();
		return metricSystem != MetricsConstants.MILES_AND_FEET;
	}

	@NonNull
	public static String getAbbreviationInScopes(Context ctx, Object obj) {
		String abbreviation = ctx.getString(isMetricItem(obj) ? R.string.m : R.string.foot);
		return "(" + abbreviation + ")";
	}

	public static boolean containsSrtmExtension(@NonNull String fileName) {
		return fileName.contains(IndexConstants.BINARY_SRTM_MAP_INDEX_EXT)
				|| fileName.contains(IndexConstants.BINARY_SRTM_FEET_MAP_INDEX_EXT);
	}

	public static boolean isSrtmFile(@NonNull String fileName) {
		return fileName.endsWith(IndexConstants.BINARY_SRTM_MAP_INDEX_EXT)
				|| fileName.endsWith(IndexConstants.BINARY_SRTM_FEET_MAP_INDEX_EXT);
	}

	@NonNull
	public static String getExtension(IndexItem indexItem) {
		return isMetricItem(indexItem) ?
				IndexConstants.BINARY_SRTM_MAP_INDEX_EXT :
				IndexConstants.BINARY_SRTM_FEET_MAP_INDEX_EXT;
	}

	public static boolean isSRTMItem(Object item) {
		if (item instanceof DownloadItem) {
			return ((DownloadItem) item).getType() == SRTM_COUNTRY_FILE;
		} else if (item instanceof LocalIndexInfo) {
			return ((LocalIndexInfo) item).getType() == SRTM_DATA;
		}
		return false;
	}

	private static boolean isMetricItem(Object item) {
		if (item instanceof IndexItem) {
			return ((IndexItem) item).getFileName().endsWith(BINARY_SRTM_MAP_INDEX_EXT_ZIP);
		} else if (item instanceof LocalIndexInfo) {
			return ((LocalIndexInfo) item).getFileName().endsWith(BINARY_SRTM_MAP_INDEX_EXT);
		} else if (item instanceof SrtmDownloadItem) {
			return isMetricItem(((SrtmDownloadItem) item).getIndexItem());
		} else if (item instanceof MultipleDownloadItem) {
			List<DownloadItem> items = ((MultipleDownloadItem) item).getAllItems();
			if (!Algorithms.isEmpty(items)) {
				return isMetricItem(items.get(0));
			}
		}
		return false;
	}

}
