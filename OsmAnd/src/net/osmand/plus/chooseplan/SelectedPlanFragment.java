package net.osmand.plus.chooseplan;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.wikipedia.WikipediaDialogFragment;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.liveupdates.LiveUpdatesSettingsBottomSheet.getActiveColorId;

public abstract class SelectedPlanFragment extends BasePurchaseFragment {

	public static final String TAG = SelectedPlanFragment.class.getSimpleName();
	private static final Log LOG = PlatformUtil.getLog(SelectedPlanFragment.class);

	private static final String PURCHASES_INFO = "https://docs.osmand.net/en/main@latest/osmand/purchases/android";

	protected List<OsmAndFeature> includedFeatures = new ArrayList<>();
	protected List<OsmAndFeature> noIncludedFeatures = new ArrayList<>();
	private String selectedItemTag;

	@Override
	protected int getLayoutId() {
		return R.layout.fragment_osmand_purchase_base;
	}

	@Override
	protected void initView() {
		setupToolbar();
		setupHeader();
		createFeaturesPreview();
		setupLearnMoreButton();
		setupPlanButtons();
		setupApplyButton();
		setupRestoreButton();
		createIncludesList();
	}

	private void setupToolbar() {
		ImageView backBtn = view.findViewById(R.id.button_back);
		backBtn.setImageResource(AndroidUtils.getNavigationIconResId(app));
		backBtn.setOnClickListener(v -> dismiss());

		ImageView helpBtn = view.findViewById(R.id.button_help);
		helpBtn.setOnClickListener(v ->
				WikipediaDialogFragment.showFullArticle(requireActivity(), Uri.parse(PURCHASES_INFO), nightMode));
	}

	private void setupHeader() {
		View mainPart = view.findViewById(R.id.main_part);
		int bgColor = ContextCompat.getColor(app, getHeaderBgColorId());
		mainPart.setBackgroundColor(bgColor);

		TextView tvTitle = view.findViewById(R.id.header_title);
		tvTitle.setText(getHeader());

		TextView tvDescription = view.findViewById(R.id.header_descr);
		tvDescription.setText(getTagline());

		ImageView ivIcon = view.findViewById(R.id.header_icon);
		ivIcon.setImageResource(getHeaderIconId());
	}

	private void createFeaturesPreview() {
		LinearLayout container = view.findViewById(R.id.features_list);
		for (OsmAndFeature feature : features) {
			View itemView = inflater.inflate(R.layout.purchase_dialog_preview_list_item, container, false);
			bindFeatureItem(itemView, feature);
			container.addView(itemView);
		}
	}

	private void setupLearnMoreButton() {
		View btn = view.findViewById(R.id.learn_more_button);
		ScrollView scrollView = view.findViewById(R.id.scroll_view);
		btn.setOnClickListener(v -> {
			View includesContainer = view.findViewById(R.id.list_included);
			scrollView.smoothScrollTo(0, includesContainer.getTop());
		});
	}

	protected void bindFeatureItem(@NonNull View itemView,
	                               @NonNull OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, false);
		ImageView ivCheckmark = itemView.findViewById(R.id.checkmark);
		if (includedFeatures.contains(feature)) {
			ivCheckmark.setVisibility(View.VISIBLE);
			ivCheckmark.setImageDrawable(getPreviewListCheckmark());
		} else {
			ivCheckmark.setVisibility(View.GONE);
		}
	}

	private void setupPlanButtons() {
		LinearLayout container = view.findViewById(R.id.price_block);
		container.removeAllViews();
		container.addView(createPurchaseButton("Monthly subscription",
				"€ 3 / Month"));
		container.addView(createPurchaseButton("Annual subscription",
				"1 month free, then € 24,99 /year "));
		selectedItemTag = "Annual subscription";
		updateButtons();
	}

	private View createPurchaseButton(String title,
	                                  String description) {
		View itemView = inflater.inflate(R.layout.purchase_dialog_btn_payment, null);

		TextView tvTitle = itemView.findViewById(R.id.title);
		tvTitle.setText(title);

		TextView tvDesc = itemView.findViewById(R.id.description);
		tvDesc.setText(description);

		itemView.setTag(title);
		itemView.setOnClickListener(v -> {
			selectedItemTag = title;
			updateButtons();
		});

		return itemView;
	}

	private void updateButtons() {
		LinearLayout container = view.findViewById(R.id.price_block);
		for (int i = 0; i < container.getChildCount(); i++) {
			View itemView = container.getChildAt(i);
			ImageView ivCheckmark = itemView.findViewById(R.id.icon);
			boolean selected = itemView.getTag().equals(selectedItemTag);

			if (selected) {
				ivCheckmark.setImageDrawable(getCheckmark());

				Drawable stroke = getActiveStrokeDrawable();
				int colorNoAlpha = ContextCompat.getColor(app, getActiveColorId(nightMode));
				int colorWithAlpha = getAlphaColor(colorNoAlpha, 0.1f);

				Drawable bgDrawable = app.getUIUtilities().getPaintedIcon(R.drawable.rectangle_rounded, colorWithAlpha);
				Drawable[] layers = {bgDrawable, stroke};
				LayerDrawable layerDrawable = new LayerDrawable(layers);
				AndroidUtils.setBackground(itemView, layerDrawable);
			} else {
				ivCheckmark.setImageDrawable(getEmptyCheckmark());
				itemView.setBackground(null);
			}
		}
	}

	private void setupApplyButton() {
		View itemView = view.findViewById(R.id.apply_button);

		TextView tvTitle = itemView.findViewById(R.id.title);
		tvTitle.setText("Complete purchase");

		TextView tvDesc = itemView.findViewById(R.id.description);
		tvDesc.setText("€ 11,99 / year");

		itemView.setOnClickListener(v -> app.showShortToastMessage("Purchase"));
	}

	private void setupRestoreButton() {
		View button = view.findViewById(R.id.button_restore);
		button.setOnClickListener(v -> purchaseHelper.requestInventory());
		setupButtonBackground(button);
	}

	private void createIncludesList() {
		LinearLayout container = view.findViewById(R.id.list_included);
		Map<String, List<OsmAndFeature>> chapters = new LinkedHashMap<>();
		chapters.put(getString(R.string.shared_string_includes_with_columns), includedFeatures);
		chapters.put(getString(R.string.shared_string_not_included_with_columns), noIncludedFeatures);

		for (String key : chapters.keySet()) {
			List<OsmAndFeature> features = chapters.get(key);
			if (features != null && features.size() > 0) {
				View v = inflater.inflate(R.layout.purchase_dialog_includes_block_header, container, false);
				TextView tvTitle = v.findViewById(R.id.title);
				tvTitle.setText(key);
				container.addView(v);
				for (OsmAndFeature feature : features) {
					View itemView = inflater.inflate(R.layout.purchase_dialog_includes_block_item, container, false);
					bindIncludesFeatureItem(itemView, feature);
					container.addView(itemView);
				}
			}
		}
	}

	private void bindIncludesFeatureItem(View itemView, OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, true);

		TextView tvDescription = itemView.findViewById(R.id.description);
		tvDescription.setText(getString(feature.getDescriptionId()));

		int iconBgColor = ContextCompat.getColor(app, feature.isAvailableInMapsPlus() ?
				R.color.maps_plus_item_bg :
				R.color.osmand_pro_item_bg);
		setupIconBackground(itemView.findViewById(R.id.icon_background), getAlphaColor(iconBgColor, 0.2f));
	}

	protected Drawable getCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_checkmark_colored_night :
				R.drawable.ic_action_checkmark_colored_day);
	}

	protected Drawable getEmptyCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_radio_button_night :
				R.drawable.ic_action_radio_button_day);
	}

	protected abstract Drawable getPreviewListCheckmark();

	protected abstract int getHeaderBgColorId();

	protected abstract String getHeader();

	protected abstract String getTagline();

	protected abstract int getHeaderIconId();

}