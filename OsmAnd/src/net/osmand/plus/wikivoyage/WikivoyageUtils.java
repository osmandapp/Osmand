package net.osmand.plus.wikivoyage;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.RequestCreator;

import net.osmand.GPXUtilities.WptPt;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.wikipedia.WikiArticleHelper;
import net.osmand.plus.wikivoyage.article.WikivoyageArticleDialogFragment;
import net.osmand.plus.wikivoyage.data.TravelArticle.TravelArticleIdentifier;
import net.osmand.plus.wikivoyage.explore.WikivoyageExploreActivity;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.List;

import static net.osmand.plus.wikivoyage.article.WikivoyageArticleNavigationFragment.ARTICLE_ID_KEY;
import static net.osmand.plus.wikivoyage.article.WikivoyageArticleNavigationFragment.SELECTED_LANG_KEY;
import static net.osmand.util.MapUtils.ROUNDING_ERROR;

public class WikivoyageUtils {

	private final static Log LOG = PlatformUtil.getLog(WikivoyageUtils.class);

	public static void setupNetworkPolicy(OsmandSettings settings, RequestCreator rc) {
		switch (settings.WIKI_ARTICLE_SHOW_IMAGES.get()) {
			case ON:
				break;
			case OFF:
				rc.networkPolicy(NetworkPolicy.OFFLINE);
				break;
			case WIFI:
				if (!settings.isWifiConnected()) {
					rc.networkPolicy(NetworkPolicy.OFFLINE);
				}
				break;
		}
	}

	public static WptPt findNearestPoint(@NonNull List<WptPt> points, @NonNull String coordinates) {
		double lat;
		double lon;
		try {
			lat = Double.parseDouble(coordinates.substring(0, coordinates.indexOf(",")));
			lon = Double.parseDouble(coordinates.substring(coordinates.indexOf(",") + 1));
		} catch (NumberFormatException e) {
			LOG.debug(e.getMessage(), e);
			return null;
		}
		for (WptPt point : points) {
			if (MapUtils.getDistance(point.getLatitude(), point.getLongitude(), lat, lon) < ROUNDING_ERROR) {
				return point;
			}
		}
		return null;
	}

	public static void processWikivoyageDomain(@NonNull FragmentActivity activity,
	                                           @NonNull String url, boolean nightMode) {
		OsmandApplication app = (OsmandApplication) activity.getApplicationContext();
		String lang = WikiArticleHelper.getLang(url);
		String articleName = WikiArticleHelper.getArticleNameFromUrl(url, lang);
		TravelArticleIdentifier articleId = app.getTravelHelper().getArticleId(articleName, lang);
		if (articleId != null) {
			if (activity instanceof WikivoyageExploreActivity) {
				WikivoyageArticleDialogFragment.showInstance(app, activity.getSupportFragmentManager(), articleId, lang);
			} else {
				Intent intent = new Intent(activity, WikivoyageExploreActivity.class);
				intent.putExtra(ARTICLE_ID_KEY, articleId);
				intent.putExtra(SELECTED_LANG_KEY, lang);
				activity.startActivity(intent);
			}
		} else {
			WikiArticleHelper.warnAboutExternalLoad(url, activity, nightMode);
		}
	}

}
