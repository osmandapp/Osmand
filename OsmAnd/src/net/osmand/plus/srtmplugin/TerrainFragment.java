package net.osmand.plus.srtmplugin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.github.ksoichiro.android.observablescrollview.ObservableListView;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.DownloadResources;
import net.osmand.plus.download.DownloadValidationManager;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.widgets.style.CustomTypefaceSpan;

import org.apache.commons.logging.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import static net.osmand.plus.UiUtilities.CustomRadioButtonType.END;
import static net.osmand.plus.UiUtilities.CustomRadioButtonType.START;
import static net.osmand.plus.download.DownloadActivityType.HILLSHADE_FILE;
import static net.osmand.plus.download.DownloadActivityType.SLOPE_FILE;
import static net.osmand.plus.srtmplugin.SRTMPlugin.TERRAIN_MAX_ZOOM;
import static net.osmand.plus.srtmplugin.SRTMPlugin.TERRAIN_MIN_ZOOM;
import static net.osmand.plus.srtmplugin.TerrainMode.HILLSHADE;
import static net.osmand.plus.srtmplugin.TerrainMode.SLOPE;


public class TerrainFragment extends BaseOsmAndFragment implements View.OnClickListener,
		DownloadIndexesThread.DownloadEvents {

	public static final String TAG = TerrainFragment.class.getSimpleName();
	private static final Log LOG = PlatformUtil.getLog(TerrainFragment.class.getSimpleName());
	private static final String SLOPES_WIKI_URL = "https://en.wikipedia.org/wiki/Grade_(slope)";
	private static final String PLUGIN_URL = "https://osmand.net/features/contour-lines-plugin";

	private OsmandApplication app;
	private UiUtilities uiUtilities;
	private OsmandSettings settings;
	private SRTMPlugin srtmPlugin;
	private boolean nightMode;
	private boolean terrainEnabled;

	private int colorProfile;

	private TextView downloadDescriptionTv;
	private TextView transparencyValueTv;
	private TextView descriptionTv;
	private LinearLayout customRadioButton;
	private TextView minZoomTv;
	private TextView maxZoomTv;
	private TextView stateTv;
	private SwitchCompat switchCompat;
	private ImageView iconIv;
	private LinearLayout emptyState;
	private LinearLayout legendContainer;
	private LinearLayout contentContainer;
	private LinearLayout downloadContainer;
	private View legendBottomDivider;
	private View titleBottomDivider;
	private View legendTopDivider;
	private View downloadTopDivider;
	private View downloadBottomDivider;
	private Slider transparencySlider;
	private RangeSlider zoomSlider;
	private ObservableListView observableListView;
	private View bottomEmptySpace;

	private ArrayAdapter<ContextMenuItem> listAdapter;

	private final Slider.OnChangeListener transparencySliderChangeListener = new Slider.OnChangeListener() {
		@Override
		public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
			if (fromUser) {
				String transparencyStr = (int) value + "%";
				transparencyValueTv.setText(transparencyStr);
				srtmPlugin.setTerrainTransparency((int) Math.ceil(value * 2.55), srtmPlugin.getTerrainMode());
				refreshMap();
			}
		}
	};

	private final RangeSlider.OnChangeListener zoomSliderChangeListener = new RangeSlider.OnChangeListener() {
		@Override
		public void onValueChange(@NonNull RangeSlider slider, float value, boolean fromUser) {
			List<Float> values = slider.getValues();
			if (values.size() > 0) {
				minZoomTv.setText(String.valueOf(values.get(0).intValue()));
				maxZoomTv.setText(String.valueOf(values.get(1).intValue()));
				srtmPlugin.setTerrainZoomValues(values.get(0).intValue(), values.get(1).intValue(), srtmPlugin.getTerrainMode());
				refreshMap();
			}
		}
	};

	@Nullable
	private MapActivity getMapActivity() {
		Activity activity = getActivity();
		if (activity instanceof MapActivity && !activity.isFinishing()) {
			return (MapActivity) activity;
		}
		return null;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		app = requireMyApplication();
		settings = app.getSettings();
		uiUtilities = app.getUIUtilities();
		nightMode = app.getDaynightHelper().isNightModeForMapControls();
		srtmPlugin = OsmandPlugin.getPlugin(SRTMPlugin.class);
		colorProfile = settings.getApplicationMode().getProfileColor(nightMode);
		terrainEnabled = srtmPlugin.isTerrainLayerEnabled();
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View root = UiUtilities.getInflater(inflater.getContext(), nightMode).inflate(R.layout.fragment_terrain, container, false);
		TextView emptyStateDescriptionTv = root.findViewById(R.id.empty_state_description);
		TextView slopeReadMoreTv = root.findViewById(R.id.slope_read_more_tv);
		TextView titleTv = root.findViewById(R.id.title_tv);
		downloadDescriptionTv = root.findViewById(R.id.download_description_tv);
		transparencyValueTv = root.findViewById(R.id.transparency_value_tv);
		legendBottomDivider = root.findViewById(R.id.legend_bottom_divider);
		transparencySlider = root.findViewById(R.id.transparency_slider);
		titleBottomDivider = root.findViewById(R.id.titleBottomDivider);
		legendTopDivider = root.findViewById(R.id.legend_top_divider);
		contentContainer = root.findViewById(R.id.content_container);
		legendContainer = root.findViewById(R.id.legend_container);
		switchCompat = root.findViewById(R.id.switch_compat);
		descriptionTv = root.findViewById(R.id.description);
		emptyState = root.findViewById(R.id.empty_state);
		stateTv = root.findViewById(R.id.state_tv);
		iconIv = root.findViewById(R.id.icon_iv);
		zoomSlider = root.findViewById(R.id.zoom_slider);
		minZoomTv = root.findViewById(R.id.zoom_value_min);
		maxZoomTv = root.findViewById(R.id.zoom_value_max);
		customRadioButton = root.findViewById(R.id.custom_radio_buttons);
		TextView hillshadeBtn = root.findViewById(R.id.left_button);
		TextView slopeBtn = root.findViewById(R.id.right_button);
		downloadContainer = root.findViewById(R.id.download_container);
		downloadTopDivider = root.findViewById(R.id.download_container_top_divider);
		downloadBottomDivider = root.findViewById(R.id.download_container_bottom_divider);
		observableListView = (ObservableListView) root.findViewById(R.id.list_view);
		bottomEmptySpace = root.findViewById(R.id.bottom_empty_space);

		titleTv.setText(R.string.shared_string_terrain);
		String wikiString = getString(R.string.shared_string_wikipedia);
		String readMoreText = String.format(
				getString(R.string.slope_read_more),
				wikiString
		);
		String emptyStateText = getString(R.string.terrain_empty_state_text) + "\n" + PLUGIN_URL;
		setupClickableText(slopeReadMoreTv, readMoreText, wikiString, SLOPES_WIKI_URL, false);
		setupClickableText(emptyStateDescriptionTv, emptyStateText, PLUGIN_URL, PLUGIN_URL, true);

		switchCompat.setChecked(terrainEnabled);
		hillshadeBtn.setOnClickListener(this);
		hillshadeBtn.setText(R.string.shared_string_hillshade);
		switchCompat.setOnClickListener(this);
		slopeBtn.setOnClickListener(this);
		slopeBtn.setText(R.string.shared_string_slope);

		UiUtilities.setupSlider(transparencySlider, nightMode, colorProfile);
		UiUtilities.setupSlider(zoomSlider, nightMode, colorProfile, true);

		transparencySlider.addOnChangeListener(transparencySliderChangeListener);
		zoomSlider.addOnChangeListener(zoomSliderChangeListener);
		transparencySlider.setValueTo(100);
		transparencySlider.setValueFrom(0);
		zoomSlider.setValueTo(TERRAIN_MAX_ZOOM);
		zoomSlider.setValueFrom(TERRAIN_MIN_ZOOM);

		UiUtilities.setupCompoundButton(switchCompat, nightMode, UiUtilities.CompoundButtonType.PROFILE_DEPENDENT);

		updateUiMode();
		return root;
	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.switch_compat) {
			onSwitchClick();
		} else if (id == R.id.left_button) {
			setupTerrainMode(HILLSHADE);
		} else if (id == R.id.right_button) {
			setupTerrainMode(SLOPE);
		}
	}

	private void updateUiMode() {
		TerrainMode mode = srtmPlugin.getTerrainMode();
		if (terrainEnabled) {
			int transparencyValue = (int) (srtmPlugin.getTerrainTransparency() / 2.55);
			String transparency = transparencyValue + "%";
			int minZoom = Math.max(srtmPlugin.getTerrainMinZoom(), TERRAIN_MIN_ZOOM);
			int maxZoom = Math.min(srtmPlugin.getTerrainMaxZoom(), TERRAIN_MAX_ZOOM);
			iconIv.setImageDrawable(uiUtilities.getPaintedIcon(R.drawable.ic_action_hillshade_dark, colorProfile));
			stateTv.setText(R.string.shared_string_enabled);
			transparencySlider.setValue(transparencyValue);
			transparencyValueTv.setText(transparency);
			zoomSlider.setValues((float) minZoom, (float) maxZoom);
			minZoomTv.setText(String.valueOf(minZoom));
			maxZoomTv.setText(String.valueOf(maxZoom));
			switch (mode) {
				case HILLSHADE:
					descriptionTv.setText(R.string.hillshade_description);
					downloadDescriptionTv.setText(R.string.hillshade_download_description);
					break;
				case SLOPE:
					descriptionTv.setText(R.string.slope_description);
					downloadDescriptionTv.setText(R.string.slope_download_description);
					break;
			}
			updateDownloadSection();
		} else {
			iconIv.setImageDrawable(uiUtilities.getIcon(
					R.drawable.ic_action_hillshade_dark,
					nightMode
							? R.color.icon_color_secondary_dark
							: R.color.icon_color_secondary_light));
			stateTv.setText(R.string.shared_string_disabled);
		}
		adjustGlobalVisibility();
		adjustLegendVisibility(mode);
		adjustModeButtons(mode);
		setupBottomEmptySpace();
	}

	private void adjustGlobalVisibility() {
		emptyState.setVisibility(terrainEnabled ? View.GONE : View.VISIBLE);
		titleBottomDivider.setVisibility(terrainEnabled ? View.GONE : View.VISIBLE);
		contentContainer.setVisibility(terrainEnabled ? View.VISIBLE : View.GONE);
	}

	private void adjustLegendVisibility(TerrainMode mode) {
		int visibility = mode == SLOPE ? View.VISIBLE : View.GONE;
		legendContainer.setVisibility(visibility);
		legendBottomDivider.setVisibility(visibility);
		legendTopDivider.setVisibility(visibility);
	}

	private void adjustModeButtons(TerrainMode mode) {
		if (mode == SLOPE) {
			UiUtilities.updateCustomRadioButtons(app, customRadioButton, nightMode, END);
		} else {
			UiUtilities.updateCustomRadioButtons(app, customRadioButton, nightMode, START);
		}
	}

	private void setupClickableText(TextView textView,
									String text,
									String clickableText,
									final String url,
									final boolean medium) {
		SpannableString spannableString = new SpannableString(text);
		ClickableSpan clickableSpan = new ClickableSpan() {
			@Override
			public void onClick(@NonNull View view) {
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				if (AndroidUtils.isIntentSafe(app, i)) {
					startActivity(i);
				}
			}

			@Override
			public void updateDrawState(@NonNull TextPaint ds) {
				super.updateDrawState(ds);
				ds.setUnderlineText(false);
			}
		};
		try {
			int startIndex = text.indexOf(clickableText);
			if (medium) {
				spannableString.setSpan(new CustomTypefaceSpan(FontCache.getRobotoMedium(app)), startIndex, startIndex + clickableText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			spannableString.setSpan(clickableSpan, startIndex, startIndex + clickableText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			textView.setText(spannableString);
			textView.setMovementMethod(LinkMovementMethod.getInstance());
			textView.setHighlightColor(nightMode
					? getResources().getColor(R.color.active_color_primary_dark)
					: getResources().getColor(R.color.active_color_primary_light));
		} catch (RuntimeException e) {
			LOG.error("Error trying to find index of " + clickableText + " " + e);
		}
	}

	private void onSwitchClick() {
		terrainEnabled = !terrainEnabled;
		switchCompat.setChecked(terrainEnabled);
		srtmPlugin.setTerrainLayerEnabled(terrainEnabled);
		updateUiMode();
		updateLayers();
	}


	private void setupTerrainMode(TerrainMode mode) {
		TerrainMode currentMode = srtmPlugin.getTerrainMode();
		if (currentMode != mode) {
			srtmPlugin.setTerrainMode(mode);
			updateUiMode();
			updateLayers();
		}
	}

	private void refreshMap() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapView().refreshMap();
		}
	}

	private void updateLayers() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			srtmPlugin.updateLayers(mapActivity.getMapView(), mapActivity);
		}
	}

	private void updateDownloadSection() {
		final ContextMenuAdapter adapter = new ContextMenuAdapter(app);
		adapter.setDefaultLayoutId(R.layout.list_item_icon_and_menu);
		adapter.setProfileDependent(true);
		adapter.setNightMode(nightMode);

		MapActivity mapActivity = getMapActivity();
		if (mapActivity == null) {
			return;
		}
		final WeakReference<MapActivity> mapActivityRef = new WeakReference<>(mapActivity);

		final DownloadIndexesThread downloadThread = app.getDownloadThread();
		if (!downloadThread.getIndexes().isDownloadedFromInternet) {
			if (settings.isInternetConnectionAvailable()) {
				downloadThread.runReloadIndexFiles();
			}
		}
		final boolean downloadIndexes = settings.isInternetConnectionAvailable()
				&& !downloadThread.getIndexes().isDownloadedFromInternet
				&& !downloadThread.getIndexes().downloadFromInternetFailed;

		if (downloadIndexes) {
			adapter.addItem(new ContextMenuItem.ItemBuilder()
					.setLayout(R.layout.list_item_icon_and_download)
					.setTitleId(R.string.downloading_list_indexes, mapActivity)
					.setLoading(true)
					.createItem());
		} else {
			try {
				TerrainMode mode = srtmPlugin.getTerrainMode();
				IndexItem currentDownloadingItem = downloadThread.getCurrentDownloadingItem();
				int currentDownloadingProgress = downloadThread.getCurrentDownloadingItemProgress();
				List<IndexItem> terrainItems = DownloadResources.findIndexItemsAt(
						app, mapActivity.getMapLocation(),
						mode == HILLSHADE ? HILLSHADE_FILE : SLOPE_FILE, false, -1, true);
				if (terrainItems.size() > 0) {
					downloadContainer.setVisibility(View.VISIBLE);
					downloadTopDivider.setVisibility(View.VISIBLE);
					downloadBottomDivider.setVisibility(View.VISIBLE);
					for (final IndexItem indexItem : terrainItems) {
						ContextMenuItem.ItemBuilder itemBuilder = new ContextMenuItem.ItemBuilder()
								.setLayout(R.layout.list_item_icon_and_download)
								.setTitle(indexItem.getVisibleName(app, app.getRegions(), false))
								.setDescription(mode == HILLSHADE
										? HILLSHADE_FILE.getString(app) + " • " + indexItem.getSizeDescription(app)
										: SLOPE_FILE.getString(app) + " • " + indexItem.getSizeDescription(app))
								.setIcon(mode == HILLSHADE ? HILLSHADE_FILE.getIconResource() : SLOPE_FILE.getIconResource())
								.setListener(new ContextMenuAdapter.ItemClickListener() {
									@Override
									public boolean onContextMenuClick(ArrayAdapter<ContextMenuItem> adapter, int itemId, int position, boolean isChecked, int[] viewCoordinates) {
										MapActivity mapActivity = mapActivityRef.get();
										if (mapActivity != null && !mapActivity.isFinishing()) {
											ContextMenuItem item = adapter.getItem(position);
											if (downloadThread.isDownloading(indexItem)) {
												downloadThread.cancelDownload(indexItem);
												if (item != null) {
													item.setProgress(ContextMenuItem.INVALID_ID);
													item.setLoading(false);
													item.setSecondaryIcon(R.drawable.ic_action_import);
													adapter.notifyDataSetChanged();
												}
											} else {
												new DownloadValidationManager(app).startDownload(mapActivity, indexItem);
												if (item != null) {
													item.setProgress(ContextMenuItem.INVALID_ID);
													item.setLoading(true);
													item.setSecondaryIcon(R.drawable.ic_action_remove_dark);
													adapter.notifyDataSetChanged();
												}
											}
										}
										return false;
									}
								})
								.setProgressListener(new ContextMenuAdapter.ProgressListener() {
									@Override
									public boolean onProgressChanged(Object progressObject, int progress,
																	 ArrayAdapter<ContextMenuItem> adapter,
																	 int itemId, int position) {
										if (progressObject instanceof IndexItem) {
											IndexItem progressItem = (IndexItem) progressObject;
											if (indexItem.compareTo(progressItem) == 0) {
												ContextMenuItem item = adapter.getItem(position);
												if (item != null) {
													item.setProgress(progress);
													item.setLoading(true);
													item.setSecondaryIcon(R.drawable.ic_action_remove_dark);
													adapter.notifyDataSetChanged();
												}
												return true;
											}
										}
										return false;
									}
								});

						if (indexItem == currentDownloadingItem) {
							itemBuilder.setLoading(true)
									.setProgress(currentDownloadingProgress)
									.setSecondaryIcon(R.drawable.ic_action_remove_dark);
						} else {
							itemBuilder.setSecondaryIcon(R.drawable.ic_action_import);
						}
						adapter.addItem(itemBuilder.createItem());
					}
				} else {
					downloadContainer.setVisibility(View.GONE);
					downloadTopDivider.setVisibility(View.GONE);
					downloadBottomDivider.setVisibility(View.GONE);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		listAdapter = adapter.createListAdapter(mapActivity, !nightMode);
		observableListView.setAdapter(listAdapter);
		observableListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ContextMenuItem item = adapter.getItem(position);
				ContextMenuAdapter.ItemClickListener click = item.getItemClickListener();
				if (click != null) {
					click.onContextMenuClick(listAdapter, item.getTitleId(), position, false, null);
				}
			}
		});
	}

	@Override
	public void newDownloadIndexes() {
		updateDownloadSection();
	}

	@Override
	public void downloadInProgress() {
		DownloadIndexesThread downloadThread = app.getDownloadThread();
		IndexItem downloadIndexItem = downloadThread.getCurrentDownloadingItem();
		if (downloadIndexItem != null && listAdapter != null) {
			int downloadProgress = downloadThread.getCurrentDownloadingItemProgress();
			ArrayAdapter<ContextMenuItem> adapter = (ArrayAdapter<ContextMenuItem>) listAdapter;
			for (int i = 0; i < adapter.getCount(); i++) {
				ContextMenuItem item = adapter.getItem(i);
				if (item != null && item.getProgressListener() != null) {
					item.getProgressListener().onProgressChanged(
							downloadIndexItem, downloadProgress, adapter, (int) adapter.getItemId(i), i);
				}
			}
		}
	}

	@Override
	public void downloadHasFinished() {
		updateDownloadSection();
	}

	private void setupBottomEmptySpace() {
		int h = terrainEnabled ? AndroidUtils.dpToPx(app, 120) : AndroidUtils.getScreenHeight(requireActivity()) / 3;
		ViewGroup.LayoutParams params = bottomEmptySpace.getLayoutParams();
		params.height = h;
		bottomEmptySpace.setLayoutParams(params);
	}
}
