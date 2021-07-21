package net.osmand.plus.track;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.routing.ColoringType;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteStatisticsHelper;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static net.osmand.plus.routing.ColoringType.ALTITUDE;
import static net.osmand.plus.routing.ColoringType.ATTRIBUTE;
import static net.osmand.plus.routing.ColoringType.SLOPE;
import static net.osmand.plus.routing.ColoringType.SPEED;

public class TrackColoringCard extends MapBaseCard {

	private static final Log log = PlatformUtil.getLog(TrackColoringCard.class);

	private final SelectedGpxFile selectedGpxFile;
	private final TrackDrawInfo trackDrawInfo;

	private TrackColoringAdapter coloringAdapter;
	private TrackAppearanceItem selectedAppearanceItem;
	private List<TrackAppearanceItem> appearanceItems;

	public TrackColoringCard(@NonNull MapActivity mapActivity,
	                         @NonNull SelectedGpxFile selectedGpxFile,
	                         @NonNull TrackDrawInfo trackDrawInfo) {
		super(mapActivity);
		this.trackDrawInfo = trackDrawInfo;
		this.selectedGpxFile = selectedGpxFile;
		appearanceItems = listTrackAppearanceItems();
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.track_coloring_card;
	}

	@Override
	protected void updateContent() {
		updateHeader();

		coloringAdapter = new TrackColoringAdapter(appearanceItems);
		RecyclerView groupRecyclerView = view.findViewById(R.id.recycler_view);
		groupRecyclerView.setLayoutManager(new LinearLayoutManager(app, RecyclerView.HORIZONTAL, false));
		groupRecyclerView.setAdapter(coloringAdapter);

		AndroidUiHelper.updateVisibility(view.findViewById(R.id.top_divider), isShowDivider());
	}

	public void updateColor() {
		if (coloringAdapter != null) {
			// Provide empty object to update item without animation
			coloringAdapter.notifyItemChanged(0, new Object());
		}
	}

	@NonNull
	public ColoringType getSelectedColoringType() {
		return ColoringType.getNonNullTrackColoringTypeByName(selectedAppearanceItem.getAttrName());
	}

	@Nullable
	public String getRouteInfoAttribute() {
		return ColoringType.getRouteInfoAttribute(selectedAppearanceItem.getAttrName());
	}

	private List<TrackAppearanceItem> listTrackAppearanceItems() {
		List<TrackAppearanceItem> items = new ArrayList<>();
		items.addAll(listStaticAppearanceItems());
		items.addAll(listRouteInfoAttributes());
		return items;
	}

	private List<TrackAppearanceItem> listStaticAppearanceItems() {
		List<TrackAppearanceItem> staticItems = new ArrayList<>();
		for (ColoringType coloringType : ColoringType.getTrackColoringTypes()) {
			if (coloringType.isRouteInfoAttribute()) {
				continue;
			}
			boolean isAvailable = coloringType.isAvailableForDrawingTrack(app, selectedGpxFile, null);
			staticItems.add(new TrackAppearanceItem(coloringType.getName(null),
					coloringType.getHumanString(app, null),
					coloringType.getIconId(), isAvailable || trackDrawInfo.isCurrentRecording()));
		}
		return staticItems;
	}

	private List<TrackAppearanceItem> listRouteInfoAttributes() {
		List<TrackAppearanceItem> routeInfoAttributes = new ArrayList<>();
		RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
		RenderingRulesStorage defaultRenderer = app.getRendererRegistry().defaultRender();
		List<String> attributes = RouteStatisticsHelper
				.getRouteStatisticAttrsNames(currentRenderer, defaultRenderer, true);

		for (String attribute : attributes) {
			boolean isAvailable = ATTRIBUTE.isAvailableForDrawingTrack(app, selectedGpxFile, attribute);
			String property = attribute.replace(RouteStatisticsHelper.ROUTE_INFO_PREFIX, "");
			routeInfoAttributes.add(new TrackAppearanceItem(attribute,
					AndroidUtils.getStringRouteInfoPropertyValue(app, property),
					ATTRIBUTE.getIconId(), isAvailable));
		}

		return routeInfoAttributes;
	}

	private TrackAppearanceItem getSelectedAppearanceItem() {
		if (selectedAppearanceItem == null) {
			ColoringType coloringType = trackDrawInfo.getColoringType();
			String routeInfoAttribute = trackDrawInfo.getRouteInfoAttribute();
			for (TrackAppearanceItem item : appearanceItems) {
				if (item.getAttrName().equals(coloringType.getName(routeInfoAttribute))) {
					selectedAppearanceItem = item;
					break;
				}
			}
		}
		return selectedAppearanceItem;
	}

	private void updateHeader() {
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.icon), false);

		View headerView = view.findViewById(R.id.header_view);
		headerView.setBackgroundDrawable(null);

		TextView titleView = view.findViewById(R.id.title);
		titleView.setText(R.string.shared_string_color);

		TextView descriptionView = view.findViewById(R.id.description);
		descriptionView.setText(getSelectedAppearanceItem().getLocalizedValue());
	}

	public void setColoringType(TrackAppearanceItem item) {
		selectedAppearanceItem = item;
		trackDrawInfo.setColoringType(ColoringType.getNonNullTrackColoringTypeByName(item.getAttrName()));
		trackDrawInfo.setRouteInfoAttribute(ColoringType.getRouteInfoAttribute(item.getAttrName()));
		mapActivity.refreshMap();

		updateHeader();
	}

	private class TrackColoringAdapter extends RecyclerView.Adapter<AppearanceViewHolder> {

		private final List<TrackAppearanceItem> items;

		private TrackColoringAdapter(List<TrackAppearanceItem> items) {
			this.items = items;
		}

		@NonNull
		@Override
		public AppearanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater themedInflater = UiUtilities.getInflater(parent.getContext(), nightMode);
			View view = themedInflater.inflate(R.layout.point_editor_group_select_item, parent, false);
			view.getLayoutParams().width = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_width);
			view.getLayoutParams().height = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_height);
			((TextView) view.findViewById(R.id.groupName)).setMaxLines(1);
			return new AppearanceViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull final AppearanceViewHolder holder, int position) {
			final TrackAppearanceItem item = items.get(position);

			if (item.isActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				AndroidUtils.setBackground(app, holder.button, nightMode, R.drawable.ripple_solid_light_6dp,
						R.drawable.ripple_solid_dark_6dp);
			}

			updateButtonBg(holder, item);
			updateTextAndIconColor(holder, item);

			holder.title.setText(item.getLocalizedValue());
			holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					if (!item.isActive()) {
						showSnackbar(view, item.getAttrName());
						return;
					}

					int prevSelectedPosition = getItemPosition(getSelectedAppearanceItem());
					selectedAppearanceItem = items.get(holder.getAdapterPosition());
					notifyItemChanged(holder.getAdapterPosition());
					notifyItemChanged(prevSelectedPosition);
					setColoringType(selectedAppearanceItem);
					if (getListener() != null) {
						getListener().onCardPressed(TrackColoringCard.this);
					}
				}
			});
		}

		private void updateButtonBg(AppearanceViewHolder holder, TrackAppearanceItem item) {
			GradientDrawable rectContourDrawable = (GradientDrawable) AppCompatResources.getDrawable(app, R.drawable.bg_select_group_button_outline);
			if (rectContourDrawable != null) {
				Context ctx = holder.itemView.getContext();
				boolean itemSelected = getSelectedAppearanceItem() != null && getSelectedAppearanceItem().equals(item);

				int strokeColor;
				int backgroundColor;
				int strokeWidth;

				if (itemSelected) {
					strokeColor = AndroidUtils.getColorFromAttr(ctx, R.attr.colorPrimary);
					backgroundColor = 0;
					strokeWidth = 2;
				} else if (!item.isActive()) {
					strokeColor = AndroidUtils.getColorFromAttr(ctx, R.attr.stroked_buttons_and_links_outline);
					backgroundColor = AndroidUtils.getColorFromAttr(ctx, R.attr.ctx_menu_card_btn);
					strokeWidth = 2;
				} else {
					strokeColor = AndroidUtils.getColorFromAttr(ctx, R.attr.stroked_buttons_and_links_outline);
					backgroundColor = 0;
					strokeWidth = 1;
				}

				rectContourDrawable.mutate();
				rectContourDrawable.setColor(backgroundColor);
				rectContourDrawable.setStroke(AndroidUtils.dpToPx(ctx, strokeWidth), strokeColor);
				holder.button.setImageDrawable(rectContourDrawable);
			}
		}

		private void updateTextAndIconColor(AppearanceViewHolder holder, TrackAppearanceItem item) {
			Context ctx = holder.itemView.getContext();
			boolean isSelected = item.getAttrName().equals(getSelectedAppearanceItem().getAttrName());
			int iconColorId;
			int textColorId;

			if (isSelected) {
				iconColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.default_icon_color);
				textColorId = AndroidUtils.getColorFromAttr(ctx, android.R.attr.textColor);
			} else if (!item.isActive()) {
				iconColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.default_icon_color);
				textColorId = AndroidUtils.getColorFromAttr(ctx, android.R.attr.textColorSecondary);
			} else {
				iconColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.colorPrimary);
				textColorId = iconColorId;
			}

			if (item.getAttrName().equals(ColoringType.TRACK_SOLID.getName(null))) {
				iconColorId = trackDrawInfo.getColor();
			}

			holder.icon.setImageDrawable(app.getUIUtilities().getPaintedIcon(item.getIconId(), iconColorId));
			holder.title.setTextColor(textColorId);
		}

		private void showSnackbar(View view, String attrName) {
			if (view == null || mapActivity == null) {
				return;
			}
			String text = "";
			if (attrName.equals(SPEED.getName(null))) {
				text = app.getString(R.string.track_has_no_speed);
			} else if (attrName.equals(ALTITUDE.getName(null))
					|| attrName.equals(SLOPE.getName(null))) {
				text = app.getString(R.string.track_has_no_altitude);
			} else if (attrName.startsWith(RouteStatisticsHelper.ROUTE_INFO_PREFIX)) {
				text = app.getString(R.string.track_has_no_needed_data);
			}
			text += " " + app.getString(R.string.select_another_colorization);
			Snackbar snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG)
					.setAnchorView(mapActivity.findViewById(R.id.dismiss_button));
			UiUtilities.setupSnackbar(snackbar, nightMode);
			snackbar.show();
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		int getItemPosition(TrackAppearanceItem name) {
			return items.indexOf(name);
		}
	}

	public static class TrackAppearanceItem {

		private String attrName;
		private String localizedValue;

		@DrawableRes
		private int iconId;

		private boolean isActive;

		public TrackAppearanceItem(String attrName, String localizedValue, int iconId, boolean isActive) {
			this.attrName = attrName;
			this.localizedValue = localizedValue;
			this.iconId = iconId;
			this.isActive = isActive;
		}

		public String getAttrName() {
			return attrName;
		}

		public String getLocalizedValue() {
			return localizedValue;
		}

		public int getIconId() {
			return iconId;
		}

		public boolean isActive() {
			return isActive;
		}
	}
}