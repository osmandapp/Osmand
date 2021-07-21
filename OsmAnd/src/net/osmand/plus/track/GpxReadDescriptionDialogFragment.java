package net.osmand.plus.track;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.PicassoUtils;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.base.BaseOsmAndDialogFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.widgets.WebViewEx;
import net.osmand.plus.wikivoyage.ArticleWebViewClient;
import net.osmand.plus.wikivoyage.WikivoyageUtils;
import net.osmand.util.Algorithms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class GpxReadDescriptionDialogFragment extends BaseOsmAndDialogFragment {

	public static final String TAG = GpxReadDescriptionDialogFragment.class.getSimpleName();

	private static final String TITLE_KEY = "title_key";
	private static final String IMAGE_URL_KEY = "image_url_key";
	private static final String CONTENT_KEY = "content_key";

	private WebViewEx webView;

	private String title;
	private String imageUrl;
	private String contentHtml;
	private GPXFile gpxFile;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = getArguments();
		if (savedInstanceState != null) {
			readBundle(savedInstanceState);
		} else if (args != null) {
			readBundle(args);
		}
		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof TrackMenuFragment) {
			TrackDisplayHelper displayHelper = ((TrackMenuFragment) targetFragment).getDisplayHelper();
			if (displayHelper != null) {
				gpxFile = displayHelper.getGpx();
			}
		}
	}

	private void readBundle(Bundle bundle) {
		title = bundle.getString(TITLE_KEY);
		imageUrl = bundle.getString(IMAGE_URL_KEY);
		contentHtml = bundle.getString(CONTENT_KEY);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		OsmandApplication app = getMyApplication();
		boolean nightMode = app.getDaynightHelper().isNightModeForMapControls();
		LayoutInflater themedInflater = UiUtilities.getInflater(requireContext(), nightMode);
		View view = themedInflater.inflate(R.layout.dialog_read_gpx_description, container, false);

		setupToolbar(view);
		setupImage(view);
		setupWebView(view);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		loadWebviewData();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(TITLE_KEY, title);
		outState.putString(IMAGE_URL_KEY, imageUrl);
		outState.putString(CONTENT_KEY, contentHtml);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Activity ctx = getActivity();
		int themeId = isNightMode(true) ? R.style.OsmandDarkTheme_DarkActionbar : R.style.OsmandLightTheme_DarkActionbar_LightStatusBar;
		Dialog dialog = new Dialog(ctx, themeId);
		Window window = dialog.getWindow();
		if (window != null) {
			if (!getSettings().DO_NOT_USE_ANIMATIONS.get()) {
				window.getAttributes().windowAnimations = R.style.Animations_Alpha;
			}
			if (Build.VERSION.SDK_INT >= 21) {
				int statusBarColor = isNightMode(true) ? R.color.status_bar_color_dark : R.color.status_bar_color_light;
				window.setStatusBarColor(ContextCompat.getColor(ctx, statusBarColor));
			}
		}
		return dialog;
	}

	private void setupToolbar(View view) {
		View back = view.findViewById(R.id.toolbar_back);
		back.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		View edit = view.findViewById(R.id.toolbar_edit);
		edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					GpxEditDescriptionDialogFragment.showInstance(activity, contentHtml, GpxReadDescriptionDialogFragment.this);
				}
			}
		});

		TextView toolbarTitle = view.findViewById(R.id.toolbar_title);
		if (!Algorithms.isEmpty(title)) {
			toolbarTitle.setText(title);
		}
	}

	private void setupImage(View view) {
		if (imageUrl == null) {
			return;
		}

		final OsmandApplication app = getMyApplication();
		final PicassoUtils picasso = PicassoUtils.getPicasso(app);
		final AppCompatImageView image = view.findViewById(R.id.main_image);
		RequestCreator rc = Picasso.get().load(imageUrl);
		WikivoyageUtils.setupNetworkPolicy(app.getSettings(), rc);

		rc.into(image, new Callback() {
			@Override
			public void onSuccess() {
				picasso.setResultLoaded(imageUrl, true);
				AndroidUiHelper.updateVisibility(image, true);
			}

			@Override
			public void onError(Exception e) {
				picasso.setResultLoaded(imageUrl, false);
			}
		});
	}

	private void setupWebView(View view) {
		webView = view.findViewById(R.id.content);
		webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		if (Build.VERSION.SDK_INT >= 19) {
			webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		} else {
			webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
		webView.setScrollbarFadingEnabled(true);
		webView.setVerticalScrollBarEnabled(false);
		webView.setBackgroundColor(Color.TRANSPARENT);
		webView.getSettings().setTextZoom((int) (getResources().getConfiguration().fontScale * 100f));
		webView.getSettings().setDomStorageEnabled(true);
		webView.getSettings().setLoadWithOverviewMode(true);
		webView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
		setupWebViewClient(view);
		loadWebviewData();
	}

	public void updateContent(String content) {
		contentHtml = content;
		loadWebviewData();
	}

	public void setupWebViewClient(View view) {
		FragmentActivity activity = getActivity();
		if (activity != null && gpxFile != null) {
			webView.setWebViewClient(new ArticleWebViewClient(this, activity, gpxFile, view, true));
		}
	}

	private void loadWebviewData() {
		String content = contentHtml;
		if (content != null) {
			content = isNightMode(true) ? getColoredContent(content) : content;
			String encoded = Base64.encodeToString(content.getBytes(), Base64.NO_PADDING);
			webView.loadData(encoded, "text/html", "base64");
		}
	}

	public void setupDependentViews(final View view) {
		View editBtn = view.findViewById(R.id.btn_edit);

		Context ctx = editBtn.getContext();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			AndroidUtils.setBackground(ctx, editBtn, isNightMode(true), R.drawable.ripple_light, R.drawable.ripple_dark);
		} else {
			AndroidUtils.setBackground(ctx, editBtn, isNightMode(true), R.drawable.btn_unstroked_light, R.drawable.btn_unstroked_dark);
		}

		editBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					GpxEditDescriptionDialogFragment.showInstance(activity, contentHtml, GpxReadDescriptionDialogFragment.this);
				}
			}
		});
		AndroidUiHelper.setVisibility(View.VISIBLE,
				editBtn, view.findViewById(R.id.divider), view.findViewById(R.id.bottom_empty_space));
		int backgroundColor = isNightMode(false) ?
				R.color.activity_background_color_dark : R.color.activity_background_color_light;
		view.findViewById(R.id.root).setBackgroundResource(backgroundColor);
	}

	private String getColoredContent(String content) {
		return "<body style=\"color:white;\">\n" + content + "</body>\n";
	}

	public void closeAll() {
		Fragment target = getTargetFragment();
		if (target instanceof TrackMenuFragment) {
			((TrackMenuFragment) target).dismiss();
		}
		dismiss();
	}

	public static void showInstance(@NonNull FragmentActivity activity,
	                                @NonNull String title,
	                                @Nullable String imageUrl,
	                                @NonNull String description,
	                                @NonNull Fragment targetFragment) {
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			Bundle args = new Bundle();
			args.putString(TITLE_KEY, title);
			args.putString(IMAGE_URL_KEY, imageUrl);
			args.putString(CONTENT_KEY, description);

			GpxReadDescriptionDialogFragment fragment = new GpxReadDescriptionDialogFragment();
			fragment.setArguments(args);
			fragment.setTargetFragment(targetFragment, 0);
			fragment.show(fragmentManager, GpxReadDescriptionDialogFragment.TAG);
		}
	}
}
