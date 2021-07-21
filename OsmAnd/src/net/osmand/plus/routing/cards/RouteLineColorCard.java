package net.osmand.plus.routing.cards;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.osmand.AndroidUtils;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.ColorDialogs;
import net.osmand.plus.helpers.enums.DayNightMode;
import net.osmand.plus.routepreparationmenu.cards.BaseCard;
import net.osmand.plus.routepreparationmenu.cards.BaseCard.CardListener;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.routing.PreviewRouteLineInfo;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.settings.backend.ListStringPreference;
import net.osmand.plus.settings.fragments.HeaderInfo;
import net.osmand.plus.settings.fragments.HeaderUiAdapter;
import net.osmand.plus.track.AppearanceViewHolder;
import net.osmand.plus.track.ColorsCard;
import net.osmand.plus.track.CustomColorBottomSheet.ColorPickerListener;
import net.osmand.plus.track.GradientCard;
import net.osmand.plus.widgets.multistatetoggle.RadioItem;
import net.osmand.plus.widgets.multistatetoggle.RadioItem.OnRadioItemClickListener;
import net.osmand.plus.widgets.multistatetoggle.TextToggleButton;
import net.osmand.plus.widgets.multistatetoggle.TextToggleButton.TextRadioItem;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteStatisticsHelper;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


public class RouteLineColorCard extends MapBaseCard implements CardListener, ColorPickerListener, HeaderInfo {

	private static final int DAY_TITLE_ID = R.string.day;
	private static final int NIGHT_TITLE_ID = R.string.night;

	private final Fragment targetFragment;
	private final HeaderUiAdapter headerUiAdapter;

	private ColorsCard colorsCard;
	private GradientCard gradientCard;
	private ColorTypeAdapter colorAdapter;
	private RecyclerView groupRecyclerView;
	private TextView tvDescription;
	private View themeToggleContainer;
	private ViewGroup cardsContainer;

	private ColoringType selectedType;
	private String selectedRouteInfoAttribute;
	private final PreviewRouteLineInfo previewRouteLineInfo;
	private final DayNightMode initMapTheme;
	private DayNightMode selectedMapTheme;

	public RouteLineColorCard(@NonNull MapActivity mapActivity,
	                          @NonNull Fragment targetFragment,
	                          @NonNull PreviewRouteLineInfo previewRouteLineInfo,
	                          @NonNull DayNightMode initMapTheme,
	                          @NonNull DayNightMode selectedMapTheme,
	                          @NonNull HeaderUiAdapter headerUiAdapter) {
		super(mapActivity);
		this.targetFragment = targetFragment;
		this.previewRouteLineInfo = previewRouteLineInfo;
		this.initMapTheme = initMapTheme;
		this.selectedMapTheme = selectedMapTheme;
		this.headerUiAdapter = headerUiAdapter;
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.route_line_color_card;
	}

	@Override
	protected void updateContent() {
		tvDescription = view.findViewById(R.id.description);

		colorAdapter = new ColorTypeAdapter();
		groupRecyclerView = view.findViewById(R.id.recycler_view);
		groupRecyclerView.setAdapter(colorAdapter);
		groupRecyclerView.setLayoutManager(new LinearLayoutManager(app, RecyclerView.HORIZONTAL, false));

		themeToggleContainer = view.findViewById(R.id.theme_toggle_container);
		LinearLayout radioGroup = view.findViewById(R.id.custom_radio_buttons);
		setupRadioGroup(radioGroup);

		cardsContainer = view.findViewById(R.id.colors_card_container);
		createCards(cardsContainer);

		initSelectedMode();
	}

	private void initSelectedMode() {
		selectedType = previewRouteLineInfo.getRouteColoringType();
		selectedRouteInfoAttribute = previewRouteLineInfo.getRouteInfoAttribute();
		modeChanged();
	}

	private void modeChanged() {
		AndroidUiHelper.updateVisibility(themeToggleContainer, selectedType.isCustomColor());
		colorsCard.updateVisibility(selectedType.isCustomColor());
		gradientCard.setSelectedScaleType(selectedType.toGradientScaleType());
		changeMapTheme(!selectedType.isCustomColor() ? initMapTheme : isNightMap() ? DayNightMode.NIGHT : DayNightMode.DAY);

		previewRouteLineInfo.setRouteColoringType(selectedType);
		previewRouteLineInfo.setRouteInfoAttribute(selectedRouteInfoAttribute);
		updateColorItems();
		updateDescription();
	}

	private void setupRadioGroup(LinearLayout buttonsContainer) {
		TextRadioItem day = createMapThemeButton(false);
		TextRadioItem night = createMapThemeButton(true);

		TextToggleButton radioGroup = new TextToggleButton(app, buttonsContainer, nightMode);
		radioGroup.setItems(day, night);

		radioGroup.setSelectedItem(!isNightMap() ? day : night);
	}

	private TextRadioItem createMapThemeButton(final boolean isNight) {
		TextRadioItem item = new TextRadioItem(app.getString(!isNight ? DAY_TITLE_ID : NIGHT_TITLE_ID));
		item.setOnClickListener(new OnRadioItemClickListener() {
			@Override
			public boolean onRadioItemClick(RadioItem radioItem, View view) {
				selectedMapTheme = isNight ? DayNightMode.NIGHT : DayNightMode.DAY;
				changeMapTheme(selectedMapTheme);
				updateDescription();
				return true;
			}
		});
		return item;
	}

	private void changeMapTheme(DayNightMode mapTheme) {
		if (targetFragment instanceof OnMapThemeUpdateListener) {
			((OnMapThemeUpdateListener) targetFragment).onMapThemeUpdated(mapTheme);
		}
		if (selectedType == ColoringType.CUSTOM_COLOR) {
			colorsCard.setSelectedColor(getCustomRouteColor());
		}
	}

	private void createCards(ViewGroup container) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			List<Integer> colors = new ArrayList<>();
			for (int color : ColorDialogs.pallette) {
				colors.add(color);
			}
			int selectedColorDay = getSelectedColorForTheme(colors, false);
			int selectedColorNight = getSelectedColorForTheme(colors, true);
			int selectedColor = isNightMap() ? selectedColorNight : selectedColorDay;
			ListStringPreference preference = app.getSettings().CUSTOM_ROUTE_LINE_COLORS;
			colorsCard = new ColorsCard(mapActivity, selectedColor, targetFragment, colors, preference, null);
			colorsCard.setListener(this);
			container.addView(colorsCard.build(mapActivity));

			gradientCard = new GradientCard(mapActivity, previewRouteLineInfo.getRouteColoringType().toGradientScaleType());
			container.addView(gradientCard.build(mapActivity));
		}
	}

	private int getSelectedColorForTheme(List<Integer> colors, boolean nightMode) {
		int color = previewRouteLineInfo.getCustomColor(nightMode);
		if (!ColorDialogs.isPaletteColor(color)) {
			colors.add(color);
		}
		return color;
	}

	@Override
	public void onColorSelected(Integer prevColor, int newColor) {
		colorsCard.onColorSelected(prevColor, newColor);
		updateSelectedCustomColor();
	}

	private int getCustomRouteColor() {
		return previewRouteLineInfo.getCustomColor(isNightMap());
	}

	private void updateSelectedCustomColor() {
		int selectedColor = colorsCard.getSelectedColor();
		previewRouteLineInfo.setCustomColor(selectedColor, isNightMap());
		updateColorItems();
	}

	private void updateColorItems() {
		if (targetFragment instanceof OnSelectedColorChangeListener) {
			((OnSelectedColorChangeListener) targetFragment).onSelectedColorChanged();
		}
		updateHeader();
	}

	@Override
	public void onNeedUpdateHeader() {
		updateHeader();
	}

	private void updateHeader() {
		String title = app.getString(R.string.shared_string_color);
		String colorName = getColorName();
		headerUiAdapter.onUpdateHeader(this, title, colorName);
	}

	@NonNull
	private String getColorName() {
		String colorName = "";
		if (selectedType.isDefault() || selectedType.isGradient() || selectedType.isRouteInfoAttribute()) {
			colorName = selectedType.getHumanString(app, selectedRouteInfoAttribute);
		} else {
			int colorNameId = ColorDialogs.getColorName(getCustomRouteColor());
			colorName = app.getString(colorNameId);
		}
		return colorName;
	}

	private void updateDescription() {
		if (selectedType.isRouteInfoAttribute()) {
			AndroidUiHelper.updateVisibility(tvDescription, false);
			return;
		}
		String description;
		if (selectedType.isDefault()) {
			String pattern = app.getString(R.string.route_line_use_map_style_color);
			description = String.format(pattern, app.getRendererRegistry().getSelectedRendererName());
		} else if (selectedType.isCustomColor()) {
			String pattern = app.getString(R.string.specify_color_for_map_mode);
			String mapModeTitle = app.getString(isNightMap() ? NIGHT_TITLE_ID : DAY_TITLE_ID);
			description = String.format(pattern, mapModeTitle.toLowerCase());
		} else {
			description = app.getString(R.string.route_line_use_gradient_coloring);
		}
		AndroidUiHelper.updateVisibility(tvDescription, true);
		tvDescription.setText(description);
	}

	private boolean isNightMap() {
		return selectedMapTheme.isNight();
	}

	@Override
	public void onCardLayoutNeeded(@NonNull BaseCard card) {
	}

	@Override
	public void onCardPressed(@NonNull BaseCard card) {
		if (card instanceof ColorsCard) {
			updateSelectedCustomColor();
		}
	}

	@Override
	public void onCardButtonPressed(@NonNull BaseCard card, int buttonIndex) {
	}

	private class ColorTypeAdapter extends RecyclerView.Adapter<AppearanceViewHolder> {

		private final List<String> coloringTypes = new ArrayList<>();

		public ColorTypeAdapter() {
			coloringTypes.addAll(listStaticColoringTypes());
			coloringTypes.addAll(listRouteInfoAttributes());
		}

		private List<String> listStaticColoringTypes() {
			List<String> coloringTypes = new ArrayList<>();
			for (ColoringType coloringType : ColoringType.getRouteColoringTypes()) {
				if (!coloringType.isRouteInfoAttribute()) {
					coloringTypes.add(coloringType.getName(null));
				}
			}
			return coloringTypes;
		}

		private List<String> listRouteInfoAttributes() {
			RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
			RenderingRulesStorage defaultRenderer = app.getRendererRegistry().defaultRender();
			return RouteStatisticsHelper.getRouteStatisticAttrsNames(currentRenderer, defaultRenderer, true);
		}

		@NonNull
		@Override
		public AppearanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater themedInflater = UiUtilities.getInflater(parent.getContext(), nightMode);
			View view = themedInflater.inflate(R.layout.point_editor_group_select_item, parent, false);
			view.getLayoutParams().width = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_width);
			view.getLayoutParams().height = app.getResources().getDimensionPixelSize(R.dimen.gpx_group_button_height);
			((TextView) view.findViewById(R.id.groupName)).setMaxLines(1);

			AppearanceViewHolder holder = new AppearanceViewHolder(view);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				AndroidUtils.setBackground(app, holder.button, nightMode, R.drawable.ripple_solid_light_6dp,
						R.drawable.ripple_solid_dark_6dp);
			}
			return holder;
		}

		@Override
		public void onBindViewHolder(@NonNull final AppearanceViewHolder holder, int position) {
			String coloringTypeName = coloringTypes.get(position);
			ColoringType coloringType = ColoringType.getRouteColoringTypeByName(coloringTypeName);

			holder.title.setText(coloringType.getHumanString(app, coloringTypeName));
			updateButtonBg(holder, coloringType, coloringTypeName);
			updateTextAndIconColor(holder, coloringType, coloringTypeName);

			holder.itemView.setOnClickListener(view -> {
				selectedType = coloringType;
				selectedRouteInfoAttribute = coloringTypeName;
				notifyItemRangeChanged(0, getItemCount());

				modeChanged();

				CardListener listener = getListener();
				if (listener != null) {
					listener.onCardPressed(RouteLineColorCard.this);
				}
			});
		}

		private void updateButtonBg(AppearanceViewHolder holder, ColoringType coloringType,
		                            String coloringTypeName) {
			GradientDrawable rectContourDrawable = (GradientDrawable) AppCompatResources
					.getDrawable(app, R.drawable.bg_select_group_button_outline);
			if (rectContourDrawable != null) {
				if (isItemSelected(coloringType, coloringTypeName)) {
					int strokeColorRes = nightMode ?
							R.color.active_color_primary_dark : R.color.active_color_primary_light;
					int strokeColor = ContextCompat.getColor(app, strokeColorRes);
					rectContourDrawable.setStroke(AndroidUtils.dpToPx(app, 2), strokeColor);
				} else {
					int strokeColor = ContextCompat.getColor(app, nightMode ?
							R.color.stroked_buttons_and_links_outline_dark :
							R.color.stroked_buttons_and_links_outline_light);
					rectContourDrawable.setStroke(AndroidUtils.dpToPx(app, 1), strokeColor);
				}
				holder.button.setImageDrawable(rectContourDrawable);
			}
		}

		private void updateTextAndIconColor(AppearanceViewHolder holder, ColoringType coloringType,
		                                    String coloringTypeName) {
			Context ctx = holder.itemView.getContext();
			int iconColorId;
			int textColorId;

			if (isItemSelected(coloringType, coloringTypeName)) {
				iconColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.default_icon_color);
				textColorId = AndroidUtils.getColorFromAttr(ctx, android.R.attr.textColor);
			} else {
				iconColorId = AndroidUtils.getColorFromAttr(ctx, R.attr.colorPrimary);
				textColorId = iconColorId;
			}

			holder.icon.setImageDrawable(app.getUIUtilities().getPaintedIcon(coloringType.getIconId(), iconColorId));
			holder.title.setTextColor(textColorId);
		}

		private boolean isItemSelected(ColoringType coloringType, String coloringTypeName) {
			if (coloringType.isRouteInfoAttribute()) {
				return Algorithms.objectEquals(selectedRouteInfoAttribute, coloringTypeName)
						&& selectedType == coloringType;
			} else {
				return selectedType == coloringType;
			}
		}

		@Override
		public int getItemCount() {
			return coloringTypes.size();
		}
	}

	public interface OnSelectedColorChangeListener {
		void onSelectedColorChanged();
	}

	public interface OnMapThemeUpdateListener {
		void onMapThemeUpdated(@NonNull DayNightMode mapTheme);
	}
}