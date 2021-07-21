package net.osmand.plus.wikivoyage;


import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.wikipedia.WikiArticleHelper;
import net.osmand.plus.wikivoyage.article.WikivoyageArticleDialogFragment;
import net.osmand.plus.wikivoyage.data.TravelArticle;
import net.osmand.plus.wikivoyage.data.TravelArticle.TravelArticleIdentifier;
import net.osmand.plus.wikivoyage.explore.WikivoyageExploreActivity;

import java.io.File;
import java.util.List;

import static net.osmand.plus.wikipedia.WikiArticleHelper.WIKIVOYAGE_DOMAIN;
import static net.osmand.plus.wikipedia.WikiArticleHelper.WIKI_DOMAIN;


/**
 * Custom WebView client to handle the internal links.
 */

public class WikivoyageWebViewClient extends WebViewClient {

	private static final String TAG = WikivoyageWebViewClient.class.getSimpleName();

	private OsmandApplication app;
	private FragmentManager fragmentManager;
	private FragmentActivity activity;
	private TravelArticle article;
	private boolean nightMode;

	private static final String GEO_PARAMS = "?lat=";
	private static final String PREFIX_GEO = "geo:";
	private static final String PAGE_PREFIX_HTTP = "http://";
	private static final String PAGE_PREFIX_HTTPS = "https://";
	private WikiArticleHelper wikiArticleHelper;


	public WikivoyageWebViewClient(@NonNull FragmentActivity activity, @NonNull FragmentManager fm, boolean nightMode) {
		app = (OsmandApplication) activity.getApplication();
		fragmentManager = fm;
		this.activity = activity;
		this.nightMode = nightMode;
		wikiArticleHelper = new WikiArticleHelper(activity, nightMode);
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		url = WikiArticleHelper.normalizeFileUrl(url);
		boolean isWebPage = url.startsWith(PAGE_PREFIX_HTTP) || url.startsWith(PAGE_PREFIX_HTTPS);
		if (url.contains(WIKIVOYAGE_DOMAIN) && isWebPage) {
			WikivoyageUtils.processWikivoyageDomain(activity, url, nightMode);
			return true;
		} else if (url.contains(WIKI_DOMAIN) && isWebPage && article != null) {
			LatLon articleCoordinates = parseCoordinates(url);
			url = url.contains(GEO_PARAMS) ? url.substring(0, url.indexOf(GEO_PARAMS)) : url;
			wikiArticleHelper.showWikiArticle(articleCoordinates == null ?
					new LatLon(article.getLat(), article.getLon()) : articleCoordinates, url);
		} else if (isWebPage) {
			WikiArticleHelper.warnAboutExternalLoad(url, activity, nightMode);
		} else if (url.startsWith(PREFIX_GEO)) {
			if (article != null && article.getGpxFile() != null) {
				GPXFile gpxFile = article.getGpxFile();
				List<WptPt> points = gpxFile.getPoints();
				String coordinates = url.replace(PREFIX_GEO, "");
				WptPt gpxPoint = WikivoyageUtils.findNearestPoint(points, coordinates);

				if (gpxPoint != null) {
					OsmandSettings settings = app.getSettings();
					settings.setMapLocationToShow(gpxPoint.getLatitude(), gpxPoint.getLongitude(),
							settings.getLastKnownMapZoom(),
							new PointDescription(PointDescription.POINT_TYPE_WPT, gpxPoint.name),
							false,
							gpxPoint);

					if (activity instanceof WikivoyageExploreActivity) {
						WikivoyageExploreActivity exploreActivity = (WikivoyageExploreActivity) activity;
						exploreActivity.setArticle(article);
					}

					fragmentManager.popBackStackImmediate();

					File path = app.getTravelHelper().createGpxFile(article);
					gpxFile.path = path.getAbsolutePath();
					app.getSelectedGpxHelper().setGpxFileToDisplay(gpxFile);
					MapActivity.launchMapActivityMoveToTop(activity);
				}
			}
		} else {
			Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			if (AndroidUtils.isIntentSafe(activity, i)) {
				activity.startActivity(i);
			}
		}
		return true;
	}

	@Nullable
	private LatLon parseCoordinates(@NonNull String url) {
		if (url.contains(GEO_PARAMS)) {
			String geoPart = url.substring(url.indexOf(GEO_PARAMS));
			int firstValueStart = geoPart.indexOf("=");
			int firstValueEnd = geoPart.indexOf("&");
			int secondValueStart = geoPart.indexOf("=", firstValueEnd);
			if (firstValueStart != -1 && firstValueEnd != -1 && secondValueStart != -1
					&& firstValueEnd > firstValueStart) {
				String lat = geoPart.substring(firstValueStart + 1, firstValueEnd);
				String lon = geoPart.substring(secondValueStart + 1);
				try {
					return new LatLon(Double.parseDouble(lat), Double.parseDouble(lon));
				} catch (NumberFormatException e) {
					Log.w(TAG, e.getMessage(), e);
				}
			}
		}
		return null;
	}

	public void setArticle(@NonNull TravelArticle article) {
		this.article = article;
	}

	public void stopRunningAsyncTasks() {
		if (wikiArticleHelper != null) {
			wikiArticleHelper.stopSearchAsyncTask();
		}
	}
}
