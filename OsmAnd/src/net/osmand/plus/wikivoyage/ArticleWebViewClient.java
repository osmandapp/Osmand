package net.osmand.plus.wikivoyage;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.data.PointDescription;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.track.GpxReadDescriptionDialogFragment;

import static net.osmand.plus.wikipedia.WikiArticleHelper.WIKIVOYAGE_DOMAIN;

public class ArticleWebViewClient extends WebViewClient {

	private static final String PAGE_PREFIX_HTTP = "http://";
	private static final String PAGE_PREFIX_HTTPS = "https://";

	private static final String PREFIX_GEO = "geo:";
	private static final String PREFIX_TEL = "tel:";

	private final OsmandApplication app;
	private final GpxReadDescriptionDialogFragment fragment;
	private final FragmentActivity activity;
	private final GPXFile gpxFile;
	private final View view;
	private final boolean usedOnMap;

	public ArticleWebViewClient(@NonNull GpxReadDescriptionDialogFragment fragment,
	                            @NonNull FragmentActivity activity,
	                            @NonNull GPXFile gpxFile,
	                            @NonNull View view,
	                            boolean usedOnMap) {
		this.fragment = fragment;
		this.activity = activity;
		this.gpxFile = gpxFile;
		this.view = view;
		this.usedOnMap = usedOnMap;
		this.app = (OsmandApplication) activity.getApplicationContext();
	}

	@Override
	public void onPageCommitVisible(WebView webView, String url) {
		super.onPageCommitVisible(webView, url);
		if (fragment.getActivity() != null) {
			fragment.setupDependentViews(view);
		}
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		boolean isWebPage = url.startsWith(PAGE_PREFIX_HTTP) || url.startsWith(PAGE_PREFIX_HTTPS);
		if (url.contains(WIKIVOYAGE_DOMAIN) && isWebPage) {
			WikivoyageUtils.processWikivoyageDomain(activity, url, isNightMode());
			fragment.dismiss();
			return true;
		} else if (url.contains(PREFIX_TEL)) {
			Intent intent = new Intent(Intent.ACTION_DIAL);
			intent.setData(Uri.parse(url));
			startActivity(intent);
			return true;
		} else if (url.contains(PREFIX_GEO)) {
			fragment.closeAll();
			String coordinates = url.replace(PREFIX_GEO, "");
			WptPt gpxPoint = WikivoyageUtils.findNearestPoint(gpxFile.getPoints(), coordinates);
			if (gpxPoint != null) {
				OsmandSettings settings = app.getSettings();
				settings.setMapLocationToShow(gpxPoint.getLatitude(), gpxPoint.getLongitude(),
						settings.getLastKnownMapZoom(),
						new PointDescription(PointDescription.POINT_TYPE_WPT, gpxPoint.name),
						false,
						gpxPoint);

				MapActivity.launchMapActivityMoveToTop(activity);
			}
			return true;
		} else {
			Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			if (AndroidUtils.isIntentSafe(activity, i)) {
				activity.startActivity(i);
				return true;
			}
		}
		return false;
	}

	private void startActivity(@NonNull Intent intent) {
		fragment.startActivity(intent);
	}

	protected boolean isNightMode() {
		if (usedOnMap) {
			return app.getDaynightHelper().isNightModeForMapControls();
		}
		return !app.getSettings().isLightContent();
	}

}
