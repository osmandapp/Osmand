package net.osmand.plus.routing.cards;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.ksoichiro.android.observablescrollview.ScrollUtils;
import com.google.android.material.slider.Slider;

import net.osmand.AndroidUtils;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.routing.PreviewRouteLineInfo;
import net.osmand.plus.settings.fragments.HeaderInfo;
import net.osmand.plus.settings.fragments.HeaderUiAdapter;
import net.osmand.plus.track.AppearanceViewHolder;
import net.osmand.plus.track.TrackAppearanceFragment.OnNeedScrollListener;
import net.osmand.util.Algorithms;

import java.util.Arrays;
import java.util.List;

public class RouteLineWidthCard extends MapBaseCard implements HeaderInfo {

	private final static int CUSTOM_WIDTH_MIN = 1;
	private final static int CUSTOM_WIDTH_MAX = 36;

	private PreviewRouteLineInfo previewRouteLineInfo;
	private OnNeedScrollListener onNeedScrollListener;
	private HeaderUiAdapter headerUiAdapter;

	private WidthMode selectedMode;

	private WidthAdapter widthAdapter;
	private View sliderContainer;
	private RecyclerView groupRecyclerView;
	private TextView tvSelectedType;
	private TextView tvDescription;

	private enum WidthMode {
		DEFAULT(R.string.map_widget_renderer, R.drawable.ic_action_map_style, null),
		THIN(R.string.rendering_value_thin_name, R.drawable.ic_action_track_line_thin_color, "thin"),
		MEDIUM(R.string.rendering_value_medium_name, R.drawable.ic_action_track_line_medium_color, "medium"),
		THICK(R.string.rendering_value_bold_name, R.drawable.ic_action_track_line_bold_color, "bold"),
		CUSTOM(R.string.shared_string_custom, R.drawable.ic_action_settings, null);

		WidthMode(int titleId, int iconId, String widthKey) {
			this.titleId = titleId;
			this.iconId = iconId;
			this.widthKey = widthKey;
		}

		int titleId;
		int iconId;
		String widthKey;
	}

	public RouteLineWidthCard(@NonNull MapActivity mapActivity,
	                          @NonNull PreviewRouteLineInfo previewRouteLineInfo,
	                          @NonNull OnNeedScrollListener onNeedScrollListener,
	                          @NonNull HeaderUiAdapter headerUiAdapter) {
		super(mapActivity);
		this.previewRouteLineInfo = previewRouteLineInfo;
		this.onNeedScrollListener = onNeedScrollListener;
		this.headerUiAdapter = headerUiAdapter;
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.route_line_width_card;
	}

	@Override
	protected void updateContent() {
		widthAdapter = new WidthAdapter();
		groupRecyclerView = view.findViewById(R.id.recycler_view);
		groupRecyclerView.setAdapter(widthAdapter);
		groupRecyclerView.setLayoutManager(new LinearLayoutManager(app, RecyclerView.HORIZONTAL, false));

		updateTopDividerVisibility(!previewRouteLineInfo.getRouteColoringType().isRouteInfoAttribute());
		((TextView) view.findViewById(R.id.title)).setText(R.string.select_track_width);
		tvSelectedType = view.findViewById(R.id.descr);
		tvDescription = view.findViewById(R.id.description);
		sliderContainer = view.findViewById(R.id.slider_container);

		initSelectedMode();
	}

	public void updateTopDividerVisibility(boolean visible) {
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.top_divider), visible);
	}

	private void initSelectedMode() {
		selectedMode = findAppropriateMode(getRouteLineWidth());
		modeChanged();
	}
	
	private void modeChanged() {
		updateHeader();
		updateDescription();
		updateCustomWidthSlider();
		scrollMenuToSelectedItem();
	}

	public void updateItems() {
		if (widthAdapter != null) {
			widthAdapter.notifyDataSetChanged();
		}
	}

	private void setRouteLineWidth(String widthKey) {
		previewRouteLineInfo.setWidth(widthKey);
		mapActivity.refreshMap();
	}

	private String getRouteLineWidth() {
		return previewRouteLineInfo.getWidth();
	}

	@Override
	public void onNeedUpdateHeader() {
		updateHeader();
	}

	private void updateHeader() {
		String title = app.getString(R.string.select_track_width);
		String description = app.getString(selectedMode.titleId);
		tvSelectedType.setText(description);
		headerUiAdapter.onUpdateHeader(this, title, description);
	}

	private void updateDescription() {
		if (selectedMode == WidthMode.DEFAULT) {
			String pattern = app.getString(R.string.route_line_use_map_style_width);
			String description = String.format(pattern, app.getRendererRegistry().getSelectedRendererName());
			tvDescription.setText(description);
			tvDescription.setVisibility(View.VISIBLE);
		} else {
			tvDescription.setVisibility(View.GONE);
		}
	}

	private void updateCustomWidthSlider() {
		if (selectedMode == WidthMode.CUSTOM) {
			Slider slider = view.findViewById(R.id.width_slider);
			final TextView tvCustomWidth = view.findViewById(R.id.width_value_tv);

			slider.setValueTo(CUSTOM_WIDTH_MAX);
			slider.setValueFrom(CUSTOM_WIDTH_MIN);

			((TextView) view.findViewById(R.id.width_value_min)).setText(String.valueOf(CUSTOM_WIDTH_MIN));
			((TextView) view.findViewById(R.id.width_value_max)).setText(String.valueOf(CUSTOM_WIDTH_MAX));

			String widthKey = getRouteLineWidth();
			int width = Algorithms.parseIntSilently(widthKey, 1);
			widthKey = String.valueOf(width);
			setRouteLineWidth(widthKey);
			tvCustomWidth.setText(widthKey);
			slider.setValue(width);

			slider.addOnChangeListener(new Slider.OnChangeListener() {
				@Override
				public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
					if (fromUser) {
						String newWidth = String.valueOf((int) value);
						setRouteLineWidth(newWidth);
						tvCustomWidth.setText(newWidth);
					}
				}
			});
			UiUtilities.setupSlider(slider, nightMode, null, true);
			ScrollUtils.addOnGlobalLayoutListener(sliderContainer, new Runnable() {
				@Override
				public void run() {
					if (sliderContainer.getVisibility() == View.VISIBLE) {
						onNeedScrollListener.onVerticalScrollNeeded(sliderContainer.getBottom());
					}
				}
			});
			AndroidUiHelper.updateVisibility(sliderContainer, true);
		} else {
			AndroidUiHelper.updateVisibility(sliderContainer, false);
		}
	}

	private void scrollMenuToSelectedItem() {
		int position = widthAdapter.getItemPosition(selectedMode);
		if (position != -1) {
			groupRecyclerView.scrollToPosition(position);
		}
	}

	private static WidthMode findAppropriateMode(@Nullable String widthKey) {
		if (widthKey != null) {
			for (WidthMode mode : WidthMode.values()) {
				if (mode.widthKey != null && mode.widthKey.equals(widthKey)) {
					return mode;
				}
			}
			return WidthMode.CUSTOM;
		}
		return WidthMode.DEFAULT;
	}

	private boolean isNightMode() {
		return app.getDaynightHelper().isNightModeForMapControls();
	}

	private class WidthAdapter extends RecyclerView.Adapter<AppearanceViewHolder> {

		private final List<WidthMode> items = Arrays.asList(WidthMode.values());

		@NonNull
		@Override
		public AppearanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater themedInflater = UiUtilities.getInflater(parent.getContext(), nightMode);
			View view = themedInflater.inflate(R.layout.point_editor_group_select_item, parent, false);
			view.getLayoutParams().width = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_width);
			view.getLayoutParams().height = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_height);

			AppearanceViewHolder holder = new AppearanceViewHolder(view);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				AndroidUtils.setBackground(app, holder.button, nightMode, R.drawable.ripple_solid_light_6dp,
						R.drawable.ripple_solid_dark_6dp);
			}
			return holder;
		}

		@Override
		public void onBindViewHolder(@NonNull final AppearanceViewHolder holder, int position) {
			WidthMode item = items.get(position);
			holder.title.setText(app.getString(item.titleId));

			updateButtonBg(holder, item);
			updateTextAndIconColor(holder, item);

			holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					int prevSelectedPosition = getItemPosition(selectedMode);
					selectedMode = items.get(holder.getAdapterPosition());
					notifyItemChanged(holder.getAdapterPosition());
					notifyItemChanged(prevSelectedPosition);

					if (selectedMode != WidthMode.CUSTOM) {
						setRouteLineWidth(selectedMode.widthKey);
					}
					modeChanged();

					CardListener listener = getListener();
					if (listener != null) {
						listener.onCardPressed(RouteLineWidthCard.this);
					}
				}
			});
		}

		private void updateTextAndIconColor(AppearanceViewHolder holder, WidthMode item) {
			Context ctx = holder.itemView.getContext();
			int iconColor;
			int textColorId;

			if (selectedMode == item) {
				iconColor = getIconColor(item, AndroidUtils.getColorFromAttr(ctx, R.attr.default_icon_color));
				textColorId = AndroidUtils.getColorFromAttr(ctx, android.R.attr.textColor);
			} else {
				iconColor = getIconColor(item, AndroidUtils.getColorFromAttr(ctx, R.attr.colorPrimary));
				textColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.colorPrimary);
			}

			holder.icon.setImageDrawable(app.getUIUtilities().getPaintedIcon(item.iconId, iconColor));
			holder.title.setTextColor(textColorId);
		}

		private int getIconColor(@NonNull WidthMode mode, @ColorInt int defaultColor) {
			return mode.widthKey != null ?
					mapActivity.getMapLayers().getRouteLayer().getRouteLineColor(isNightMode()) :
					defaultColor;
		}

		private void updateButtonBg(AppearanceViewHolder holder, WidthMode item) {
			GradientDrawable rectContourDrawable = (GradientDrawable) AppCompatResources.getDrawable(app, R.drawable.bg_select_group_button_outline);
			if (rectContourDrawable != null) {
				if (selectedMode == item) {
					int strokeColor = ContextCompat.getColor(app, nightMode ? R.color.active_color_primary_dark : R.color.active_color_primary_light);
					rectContourDrawable.setStroke(AndroidUtils.dpToPx(app, 2), strokeColor);
				} else {
					int strokeColor = ContextCompat.getColor(app, nightMode ?
							R.color.stroked_buttons_and_links_outline_dark
							: R.color.stroked_buttons_and_links_outline_light);
					rectContourDrawable.setStroke(AndroidUtils.dpToPx(app, 1), strokeColor);
				}
				holder.button.setImageDrawable(rectContourDrawable);
			}
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		int getItemPosition(WidthMode widthMode) {
			return items.indexOf(widthMode);
		}
	}
}
