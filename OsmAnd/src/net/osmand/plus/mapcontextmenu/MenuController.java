package net.osmand.plus.mapcontextmenu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import net.osmand.GPXUtilities.WptPt;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.NativeLibrary.RenderedObject;
import net.osmand.PlatformUtil;
import net.osmand.aidl.AidlMapPointWrapper;
import net.osmand.binary.BinaryMapDataObject;
import net.osmand.binary.BinaryMapIndexReader.TagValuePair;
import net.osmand.data.Amenity;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.TransportStop;
import net.osmand.map.OsmandRegions;
import net.osmand.map.WorldRegion;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.mapmarkers.MapMarker;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper.TargetPoint;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.audionotes.AudioVideoNoteMenuController;
import net.osmand.plus.audionotes.AudioVideoNotesPlugin.Recording;
import net.osmand.plus.download.DownloadActivityType;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.DownloadValidationManager;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.helpers.AvoidSpecificRoads;
import net.osmand.plus.helpers.SearchHistoryHelper;
import net.osmand.plus.mapcontextmenu.MenuBuilder.CollapseExpandListener;
import net.osmand.plus.mapcontextmenu.controllers.AMapPointMenuController;
import net.osmand.plus.mapcontextmenu.controllers.AmenityMenuController;
import net.osmand.plus.mapcontextmenu.controllers.FavouritePointMenuController;
import net.osmand.plus.mapcontextmenu.controllers.GpxItemMenuController;
import net.osmand.plus.mapcontextmenu.controllers.HistoryMenuController;
import net.osmand.plus.mapcontextmenu.controllers.ImpassibleRoadsMenuController;
import net.osmand.plus.mapcontextmenu.controllers.MapDataMenuController;
import net.osmand.plus.mapcontextmenu.controllers.MapMarkerMenuController;
import net.osmand.plus.mapcontextmenu.controllers.MyLocationMenuController;
import net.osmand.plus.mapcontextmenu.controllers.PointDescriptionMenuController;
import net.osmand.plus.mapcontextmenu.controllers.RenderedObjectMenuController;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.plus.mapcontextmenu.controllers.TargetPointMenuController;
import net.osmand.plus.mapcontextmenu.controllers.TransportRouteController;
import net.osmand.plus.mapcontextmenu.controllers.TransportStopController;
import net.osmand.plus.mapcontextmenu.controllers.WptPtMenuController;
import net.osmand.plus.mapcontextmenu.other.ShareMenu;
import net.osmand.plus.mapillary.MapillaryImage;
import net.osmand.plus.mapillary.MapillaryMenuController;
import net.osmand.plus.osmedit.EditPOIMenuController;
import net.osmand.plus.osmedit.OsmBugMenuController;
import net.osmand.plus.osmedit.OsmBugsLayer.OpenStreetNote;
import net.osmand.plus.osmedit.OsmPoint;
import net.osmand.plus.parkingpoint.ParkingPositionMenuController;
import net.osmand.plus.resources.ResourceManager;
import net.osmand.plus.transport.TransportStopRoute;
import net.osmand.plus.views.layers.DownloadedRegionsLayer.DownloadMapObject;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarController;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarControllerType;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;
import net.osmand.util.OpeningHoursParser.OpeningHours;

import org.apache.commons.logging.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class MenuController extends BaseMenuController implements CollapseExpandListener {

	public static class MenuState {
		public static final int HEADER_ONLY = 1;
		public static final int HALF_SCREEN = 2;
		public static final int FULL_SCREEN = 4;
	}

	public enum MenuType {
		STANDARD,
		MULTI_LINE
	}

	protected MapContextMenu mapContextMenu;
	protected MenuBuilder builder;
	private int currentMenuState;
	private MenuType menuType = MenuType.STANDARD;
	private PointDescription pointDescription;
	private LatLon latLon;
	private boolean active;

	protected TitleButtonController leftTitleButtonController;
	protected TitleButtonController rightTitleButtonController;
	protected TitleButtonController bottomTitleButtonController;

	protected TitleButtonController leftDownloadButtonController;
	protected TitleButtonController rightDownloadButtonController;
	protected List<Pair<TitleButtonController, TitleButtonController>> additionalButtonsControllers;
	protected TitleProgressController titleProgressController;

	protected TopToolbarController toolbarController;

	protected IndexItem indexItem;
	protected boolean downloaded;
	private BinaryMapDataObject downloadMapDataObject;
	private WorldRegion downloadRegion;
	private DownloadIndexesThread downloadThread;

	protected List<OpeningHours.Info> openingHoursInfo;

	private static final Log LOG = PlatformUtil.getLog(MenuController.class);

	public MenuController(MenuBuilder builder, PointDescription pointDescription, MapActivity mapActivity) {
		super(mapActivity);
		this.pointDescription = pointDescription;
		this.builder = builder;
		this.builder.setCollapseExpandListener(this);
		this.currentMenuState = getInitialMenuState();
		this.builder.setLight(isLight());
	}

	protected void onCreated() {
	}

	@Override
	public void onCollapseExpand(boolean collapsed) {
		if (mapContextMenu != null) {
			mapContextMenu.updateLayout();
		}
	}

	public String getPreferredMapLang() {
		return builder.getPreferredMapLang();
	}

	public String getPreferredMapAppLang() {
		return builder.getPreferredMapAppLang();
	}

	public boolean isTransliterateNames() {
		return builder.isTransliterateNames();
	}

	public void setMapContextMenu(MapContextMenu mapContextMenu) {
		this.mapContextMenu = mapContextMenu;
		builder.setMapContextMenu(mapContextMenu);
	}

	public void build(View rootView) {
		for (OsmandPlugin plugin : OsmandPlugin.getEnabledPlugins()) {
			if (plugin.isMenuControllerSupported(this.getClass())) {
				builder.addMenuPlugin(plugin);
			}
		}
		builder.build((ViewGroup) rootView);
	}

	public static MenuController getMenuController(@NonNull MapActivity mapActivity,
												   @NonNull LatLon latLon,
												   @NonNull PointDescription pointDescription,
												   @Nullable Object object,
												   @NonNull MenuType menuType) {
		MenuController menuController = null;
		if (object != null) {
			if (object instanceof Amenity) {
				menuController = new AmenityMenuController(mapActivity, pointDescription, (Amenity) object);
			} else if (object instanceof FavouritePoint) {
				if (pointDescription.isParking()
						|| (FavouritePoint.SpecialPointType.PARKING.equals(((FavouritePoint) object).getSpecialPointType()))) {
					menuController = new ParkingPositionMenuController(mapActivity, pointDescription, (FavouritePoint) object);
				} else {
					menuController = new FavouritePointMenuController(mapActivity, pointDescription, (FavouritePoint) object);
				}
			} else if (object instanceof SearchHistoryHelper.HistoryEntry) {
				menuController = new HistoryMenuController(mapActivity, pointDescription, (SearchHistoryHelper.HistoryEntry) object);
			} else if (object instanceof TargetPoint) {
				menuController = new TargetPointMenuController(mapActivity, pointDescription, (TargetPoint) object);
			} else if (object instanceof Recording) {
				menuController = new AudioVideoNoteMenuController(mapActivity, pointDescription, (Recording) object);
			} else if (object instanceof OsmPoint) {
				menuController = new EditPOIMenuController(mapActivity, pointDescription, (OsmPoint) object);
			} else if (object instanceof WptPt) {
				menuController = WptPtMenuController.getInstance(mapActivity, pointDescription, (WptPt) object);
			} else if (object instanceof DownloadMapObject) {
				menuController = new MapDataMenuController(mapActivity, pointDescription, (DownloadMapObject) object);
			} else if (object instanceof OpenStreetNote) {
				menuController = new OsmBugMenuController(mapActivity, pointDescription, (OpenStreetNote) object);
			} else if (object instanceof GpxDisplayItem) {
				menuController = new GpxItemMenuController(mapActivity, pointDescription, (GpxDisplayItem) object);
			} else if (object instanceof MapMarker) {
				menuController = new MapMarkerMenuController(mapActivity, pointDescription, (MapMarker) object);
			} else if (object instanceof TransportStopRoute) {
				menuController = new TransportRouteController(mapActivity, pointDescription, (TransportStopRoute) object);
			} else if (object instanceof TransportStop) {
				menuController = new TransportStopController(mapActivity, pointDescription, (TransportStop) object);
			} else if (object instanceof AidlMapPointWrapper) {
				menuController = new AMapPointMenuController(mapActivity, pointDescription, (AidlMapPointWrapper) object);
			} else if (object instanceof LatLon) {
				if (pointDescription.isMyLocation()) {
					menuController = new MyLocationMenuController(mapActivity, pointDescription);
				}
			} else if (object instanceof AvoidSpecificRoads.AvoidRoadInfo) {
				menuController = new ImpassibleRoadsMenuController(mapActivity, pointDescription, (AvoidSpecificRoads.AvoidRoadInfo) object);
			} else if (object instanceof RenderedObject) {
				menuController = new RenderedObjectMenuController(mapActivity, pointDescription, (RenderedObject) object);
			} else if (object instanceof MapillaryImage) {
				menuController = new MapillaryMenuController(mapActivity, pointDescription, (MapillaryImage) object);
			} else if (object instanceof SelectedGpxPoint) {
				menuController = new SelectedGpxMenuController(mapActivity, pointDescription, (SelectedGpxPoint) object);
			} else if (object instanceof Pair && ((Pair<?, ?>) object).second instanceof SelectedGpxPoint) {
				menuController = new SelectedGpxMenuController(mapActivity, pointDescription, (SelectedGpxPoint) ((Pair<?, ?>) object).second);
			}
		}
		if (menuController == null) {
			menuController = new PointDescriptionMenuController(mapActivity, pointDescription);
		}
		menuController.menuType = menuType;
		menuController.setLatLon(latLon);
		menuController.onCreated();
		return menuController;
	}

	public void update(PointDescription pointDescription, Object object) {
		setPointDescription(pointDescription);
		setObject(object);
	}

	protected void setPointDescription(PointDescription pointDescription) {
		this.pointDescription = pointDescription;
	}

	protected abstract void setObject(Object object);

	protected abstract Object getObject();

	protected Object getCorrespondingMapObject() {
		return null;
	}

	public boolean isActive() {
		return active;
	}

	public boolean setActive(boolean active) {
		this.active = active;
		return true;
	}

	public void addPlainMenuItem(int iconId, String buttonText, String text, boolean needLinks, boolean isUrl, OnClickListener onClickListener) {
		builder.addPlainMenuItem(iconId, buttonText, text, needLinks, isUrl, onClickListener);
	}

	public void addPlainMenuItem(int iconId, String text, boolean needLinks, boolean isUrl,
		boolean collapsable, CollapsableView collapsableView, OnClickListener onClickListener) {

		builder.addPlainMenuItem(iconId, text, needLinks, isUrl, collapsable, collapsableView, onClickListener);
	}

	public void clearPlainMenuItems() {
		builder.clearPlainMenuItems();
	}

	public void addPlainMenuItems(String typeStr, PointDescription pointDescription, LatLon latLon) {
		if (pointDescription.isMyLocation()) {
			addSpeedToPlainItems();
			addAltitudeToPlainItems();
			addPrecisionToPlainItems();
		}
	}

	protected void addSpeedToPlainItems() {
		final MapActivity mapActivity = getMapActivity();
		if (getMapActivity() != null) {
			final OsmandApplication app = mapActivity.getMyApplication();
			Location l = app.getLocationProvider().getLastKnownLocation();
			if (l != null && l.hasSpeed() && l.getSpeed() > 0f) {
				String speed = OsmAndFormatter.getFormattedSpeed(l.getSpeed(), app);
				addPlainMenuItem(R.drawable.ic_action_speed, null, speed, false, false, null);
			}
		}
	}

	protected void addAltitudeToPlainItems() {
		final MapActivity mapActivity = getMapActivity();
		if (getMapActivity() != null) {
			final OsmandApplication app = mapActivity.getMyApplication();
			Location l = app.getLocationProvider().getLastKnownLocation();
			if (l != null && l.hasAltitude()) {
				String alt = OsmAndFormatter.getFormattedAlt(l.getAltitude(), app);
				addPlainMenuItem(R.drawable.ic_action_altitude_average, null, alt, false, false, null);
			}
		}
	}

	protected void addPrecisionToPlainItems() {
		final MapActivity mapActivity = getMapActivity();
		if (getMapActivity() != null ) {
			final OsmandApplication app = mapActivity.getMyApplication();
			Location l = app.getLocationProvider().getLastKnownLocation();
			if (l != null && l.hasAccuracy()) {
				String acc;
				if (l.hasVerticalAccuracy()) {
					acc = String.format(app.getString(R.string.precision_hdop_and_vdop),
						OsmAndFormatter.getFormattedDistance(l.getAccuracy(), app),
						OsmAndFormatter.getFormattedDistance(l.getVerticalAccuracy(), app));
				} else {
					acc = String.format(app.getString(R.string.precision_hdop),
						OsmAndFormatter.getFormattedDistance(l.getAccuracy(), app));
				}

				addPlainMenuItem(R.drawable.ic_action_ruler_circle, null, acc, false, false, null);
			}
		}
	}

	public PointDescription getPointDescription() {
		return pointDescription;
	}

	public int getInitialMenuState() {
		if (isLandscapeLayout()) {
			return MenuState.FULL_SCREEN;
		} else {
			return getInitialMenuStatePortrait();
		}
	}

	public int getSupportedMenuStates() {
		if (isLandscapeLayout()) {
			return MenuState.FULL_SCREEN;
		} else {
			return getSupportedMenuStatesPortrait();
		}
	}

	protected int getInitialMenuStatePortrait() {
		return MenuState.HEADER_ONLY;
	}

	protected int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	public int getCurrentMenuState() {
		return currentMenuState;
	}

	public MenuType getMenuType() {
		return menuType;
	}

	public boolean slideUp() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v << 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public boolean slideDown() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v >> 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public void setCurrentMenuState(int currentMenuState) {
		this.currentMenuState = currentMenuState;
	}

	public TitleButtonController getLeftTitleButtonController() {
		return leftTitleButtonController;
	}

	public TitleButtonController getRightTitleButtonController() {
		return rightTitleButtonController;
	}

	public TitleButtonController getBottomTitleButtonController() {
		return bottomTitleButtonController;
	}

	public TitleButtonController getLeftDownloadButtonController() {
		return leftDownloadButtonController;
	}

	public TitleButtonController getRightDownloadButtonController() {
		return rightDownloadButtonController;
	}

	public List<Pair<TitleButtonController, TitleButtonController>> getAdditionalButtonsControllers() {
		return additionalButtonsControllers;
	}

	public TitleProgressController getTitleProgressController() {
		return titleProgressController;
	}

	public TopToolbarController getToolbarController() {
		return toolbarController;
	}

	public boolean hasBackAction() {
		return toolbarController != null;
	}

	public LatLon getLatLon() {
		return latLon;
	}

	public boolean supportZoomIn() {
		return true;
	}

	public boolean navigateButtonVisible() {
		return true;
	}

	public boolean zoomButtonsVisible() {
		return !isLandscapeLayout();
	}

	public boolean buttonsVisible() {
		return true;
	}

	public boolean handleSingleTapOnMap() {
		return false;
	}

	public boolean isClosable() {
		return true;
	}

	public boolean needStreetName() {
		return !displayDistanceDirection();
	}

	public boolean needTypeStr() {
		return true;
	}

	public boolean displayStreetNameInTitle() {
		return false;
	}

	public boolean displayDistanceDirection() {
		return false;
	}

	public int getRightIconId() {
		return 0;
	}

	@Nullable
	public Drawable getRightIcon() {
		return null;
	}

	public boolean isBigRightIcon() {
		return false;
	}

	@Nullable
	public Drawable getSecondLineTypeIcon() {
		return null;
	}

	@Nullable
	public Drawable getSubtypeIcon() {
		return null;
	}

	public boolean navigateInPedestrianMode() {
		return false;
	}

	public int getFavActionIconId() {
		return R.drawable.ic_action_favorite_stroke;
	}

	public int getFavActionStringId() {
		return R.string.shared_string_add;
	}

	public boolean isFavButtonEnabled() {
		return true;
	}

	public int getWaypointActionIconId() {
		return R.drawable.ic_action_flag_stroke;
	}

	public int getWaypointActionStringId() {
		return R.string.shared_string_marker;
	}

	public boolean isWaypointButtonEnabled() {
		return true;
	}

	@NonNull
	public String getTypeStr() {
		return "";
	}

	@NonNull
	public CharSequence getSubtypeStr() {
		return "";
	}

	@ColorRes
	public int getAdditionalInfoColorId() {
		if (openingHoursInfo != null) {
			boolean open = false;
			for (OpeningHours.Info info : openingHoursInfo) {
				if (info.isOpened() || info.isOpened24_7()) {
					open = true;
					break;
				}
			}
			return open ? R.color.ctx_menu_amenity_opened_text_color : R.color.ctx_menu_amenity_closed_text_color;
		} else if (shouldShowMapSize()) {
			return R.color.icon_color_default_light;
		}
		return 0;
	}

	public CharSequence getAdditionalInfoStr() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (openingHoursInfo != null) {
				int colorOpen = mapActivity.getResources().getColor(R.color.ctx_menu_amenity_opened_text_color);
				int colorClosed = mapActivity.getResources().getColor(R.color.ctx_menu_amenity_closed_text_color);
				return getSpannableOpeningHours(openingHoursInfo, colorOpen, colorClosed);
			} else if (shouldShowMapSize()) {
				return mapActivity.getString(R.string.file_size_in_mb, indexItem.getArchiveSizeMB());
			}
		}
		return "";
	}

	@DrawableRes
	public int getAdditionalInfoIconRes() {
		if (openingHoursInfo != null) {
			return R.drawable.ic_action_opening_hour_16;
		} else if (shouldShowMapSize()) {
			return R.drawable.ic_sdcard_16;
		}
		return 0;
	}

	public static SpannableString getSpannableOpeningHours(List<OpeningHours.Info> openingHoursInfo,
														   int colorOpen,
														   int colorClosed) {
		StringBuilder sb = new StringBuilder();
		int[] pos = new int[openingHoursInfo.size()];
		for (int i = 0; i < openingHoursInfo.size(); i++) {
			OpeningHours.Info info = openingHoursInfo.get(i);
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(info.getInfo());
			pos[i] = sb.length();
		}
		SpannableString infoStr = new SpannableString(sb.toString());
		int k = 0;
		for (int i = 0; i < openingHoursInfo.size(); i++) {
			OpeningHours.Info info = openingHoursInfo.get(i);
			infoStr.setSpan(new ForegroundColorSpan(info.isOpened() ? colorOpen : colorClosed), k, pos[i], 0);
			k = pos[i];
		}
		return infoStr;
	}

	private boolean shouldShowMapSize() {
		return indexItem != null && !downloaded;
	}

	@NonNull
	public String getCommonTypeStr() {
		return "";
	}

	@NonNull
	public String getNameStr() {
		return pointDescription.getName();
	}

	@NonNull
	public String getFirstNameStr() {
		return "";
	}

	@Nullable
	public List<TransportStopRoute> getTransportStopRoutes() {
		return null;
	}

	@Nullable
	protected List<TransportStopRoute> getSubTransportStopRoutes(boolean nearby) {
		return null;
	}

	@Nullable
	public List<TransportStopRoute> getLocalTransportStopRoutes() {
		return getSubTransportStopRoutes(false);
	}

	@Nullable
	public List<TransportStopRoute> getNearbyTransportStopRoutes() {
		return getSubTransportStopRoutes(true);
	}

	public void share(LatLon latLon, String title, String address) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			ShareMenu.show(latLon, title, address, mapActivity);
		}
	}

	public void updateData() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && downloadMapDataObject != null) {
			if (indexItem == null) {
				List<IndexItem> indexItems = new LinkedList<>(downloadThread.getIndexes().getIndexItems(downloadRegion));
				for (IndexItem item : indexItems) {
					if (item.getType() == DownloadActivityType.NORMAL_FILE) {
						indexItem = item;
						break;
					}
				}
			}

			if (indexItem != null) {
				downloaded = indexItem.isDownloaded();
			}

			leftDownloadButtonController.visible = !downloaded;
			leftDownloadButtonController.startIconId = R.drawable.ic_action_import;

			boolean internetConnectionAvailable =
					mapActivity.getMyApplication().getSettings().isInternetConnectionAvailable();
			boolean downloadIndexes = internetConnectionAvailable
					&& !downloadThread.getIndexes().isDownloadedFromInternet
					&& !downloadThread.getIndexes().downloadFromInternetFailed;

			boolean isDownloading = indexItem != null && downloadThread.isDownloading(indexItem);
			if (isDownloading) {
				titleProgressController.setMapDownloadMode();
				if (downloadThread.getCurrentDownloadingItem() == indexItem) {
					titleProgressController.indeterminate = false;
					titleProgressController.progress = downloadThread.getCurrentDownloadingItemProgress();
				} else {
					titleProgressController.indeterminate = true;
					titleProgressController.progress = 0;
				}
				double mb = indexItem.getArchiveSizeMB();
				String v;
				if (titleProgressController.progress != -1) {
					v = mapActivity.getString(R.string.value_downloaded_of_max, mb * titleProgressController.progress / 100, mb);
				} else {
					v = mapActivity.getString(R.string.file_size_in_mb, mb);
				}
				if (indexItem.getType() == DownloadActivityType.ROADS_FILE) {
					titleProgressController.caption = indexItem.getType().getString(mapActivity) + " • " + v;
				} else {
					titleProgressController.caption = v;
				}
				titleProgressController.visible = true;
			} else if (downloadIndexes) {
				titleProgressController.setIndexesDownloadMode(mapActivity);
				titleProgressController.visible = true;
			} else if (!internetConnectionAvailable) {
				titleProgressController.setNoInternetConnectionMode(mapActivity);
				titleProgressController.visible = true;
			} else {
				titleProgressController.visible = false;
			}
		}
	}

	public boolean hasCustomAddressLine() {
		return builder.hasCustomAddressLine();
	}

	public void buildCustomAddressLine(LinearLayout ll) {
		builder.buildCustomAddressLine(ll);
	}

	public abstract class TitleButtonController {
		public String caption = "";
		public int startIconId = 0;
		public int endIconId = 0;
		public boolean needRightText = false;
		public String rightTextCaption = "";
		public boolean visible = true;
		public boolean tintIcon = true;
		public Drawable startIcon;
		public Drawable endIcon;
		public boolean enabled = true;

		@Nullable
		public Drawable getStartIcon() {
			return getIconDrawable(true);
		}

		@Nullable
		public Drawable getEndIcon() {
			return getIconDrawable(false);
		}

		@Nullable
		private Drawable getIconDrawable(boolean start) {
			Drawable drawable = start ? startIcon : endIcon;
			if (drawable != null) {
				return drawable;
			}
			int resId = start ? startIconId : endIconId;
			if (resId != 0) {
				if (tintIcon) {
					return enabled ? getNormalIcon(resId) : getDisabledIcon(resId);
				}
				MapActivity mapActivity = getMapActivity();
				return mapActivity != null ? AppCompatResources.getDrawable(mapActivity, resId) : null;
			}
			return null;
		}

		public void clearIcon(boolean left) {
			if (left) {
				startIcon = null;
				startIconId = 0;
			} else {
				endIcon = null;
				endIconId = 0;
			}
		}

		private Drawable getDisabledIcon(@DrawableRes int iconResId) {
			return getIcon(iconResId, isLight() ? R.color.text_color_secondary_light : R.color.text_color_secondary_dark);
		}

		private Drawable getNormalIcon(@DrawableRes int iconResId) {
			return getIcon(iconResId, isLight() ? R.color.active_color_primary_light : R.color.active_color_primary_dark);
		}

		public abstract void buttonPressed();
	}

	public abstract class TitleProgressController {
		public String caption = "";
		public int progress = 0;
		public boolean indeterminate;
		public boolean visible;
		public boolean progressVisible;
		public boolean buttonVisible;

		public void setIndexesDownloadMode(@NonNull Context ctx) {
			caption = ctx.getString(R.string.downloading_list_indexes);
			indeterminate = true;
			progressVisible = true;
			buttonVisible = false;
		}

		public void setNoInternetConnectionMode(@NonNull Context ctx) {
			caption = ctx.getString(R.string.no_index_file_to_download);
			progressVisible = false;
			buttonVisible = false;
		}

		public void setMapDownloadMode() {
			indeterminate = false;
			progressVisible = true;
			buttonVisible = true;
		}

		public abstract void buttonPressed();
	}

	public void onShow() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && toolbarController != null) {
			mapActivity.showTopToolbar(toolbarController);
		}
	}

	public void onHide() {
		if (builder != null) {
			builder.onHide();
		}
	}

	public void onClose() {
		if (builder != null) {
			builder.onClose();
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null && toolbarController != null) {
			mapActivity.hideTopToolbar(toolbarController);
		}
	}

	public void onAcquireNewController(PointDescription pointDescription, Object object) {
	}

	public boolean isMapDownloaded() {
		return downloaded;
	}

	public void setLatLon(@NonNull LatLon latLon) {
		this.latLon = latLon;
		if (builder != null) {
			builder.setLatLon(latLon);
		}
	}

	public static class ContextMenuToolbarController extends TopToolbarController {

		private MenuController menuController;

		public ContextMenuToolbarController(MenuController menuController) {
			super(TopToolbarControllerType.CONTEXT_MENU);
			this.menuController = menuController;
			setBgIds(R.color.app_bar_color_light, R.color.app_bar_color_dark,
					R.color.app_bar_color_light, R.color.app_bar_color_dark);
			setBackBtnIconClrIds(R.color.color_white, R.color.color_white);
			setCloseBtnIconClrIds(R.color.color_white, R.color.color_white);
			setTitleTextClrIds(R.color.color_white, R.color.color_white);
		}

		public MenuController getMenuController() {
			return menuController;
		}
	}

	public void requestMapDownloadInfo(final LatLon latLon) {
		new SearchOsmandRegionTask(this, latLon).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void createMapDownloadControls(BinaryMapDataObject binaryMapDataObject, String selectedFullName) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			OsmandRegions osmandRegions = mapActivity.getMyApplication().getResourceManager().getOsmandRegions();
			downloadMapDataObject = binaryMapDataObject;
			downloaded = downloadMapDataObject == null;
			if (!downloaded) {
				downloadThread = mapActivity.getMyApplication().getDownloadThread();
				downloadRegion = osmandRegions.getRegionData(selectedFullName);
				if (downloadRegion != null && downloadRegion.isRegionMapDownload()) {
					List<IndexItem> indexItems = downloadThread.getIndexes().getIndexItems(downloadRegion);
					for (IndexItem item : indexItems) {
						if (item.getType() == DownloadActivityType.NORMAL_FILE
								&& (item.isDownloaded() || downloadThread.isDownloading(item))) {
							indexItem = item;
						}
					}
				}

				leftDownloadButtonController = new TitleButtonController() {
					@Override
					public void buttonPressed() {
						MapActivity mapActivity = getMapActivity();
						if (indexItem != null && mapActivity != null) {
							if (indexItem.getType() == DownloadActivityType.NORMAL_FILE) {
								new DownloadValidationManager(mapActivity.getMyApplication())
										.startDownload(mapActivity, indexItem);
							}
						}
					}
				};
				leftDownloadButtonController.caption =
						downloadRegion != null ? downloadRegion.getLocaleName() : mapActivity.getString(R.string.shared_string_download);
				leftDownloadButtonController.startIconId = R.drawable.ic_action_import;

				titleProgressController = new TitleProgressController() {
					@Override
					public void buttonPressed() {
						if (indexItem != null) {
							downloadThread.cancelDownload(indexItem);
						}
					}
				};

				if (!downloadThread.getIndexes().isDownloadedFromInternet) {
					if (mapActivity.getMyApplication().getSettings().isInternetConnectionAvailable()) {
						downloadThread.runReloadIndexFiles();
					}
				}

				if (mapContextMenu != null) {
					mapContextMenu.updateMenuUI();
				}
			}
		}
	}

	private static class SearchOsmandRegionTask extends AsyncTask<Void, Void, BinaryMapDataObject> {

		private WeakReference<MenuController> controllerRef;
		private final LatLon latLon;
		ResourceManager rm;
		OsmandRegions osmandRegions;
		String selectedFullName;

		SearchOsmandRegionTask(@NonNull MenuController controller, LatLon latLon) {
			this.controllerRef = new WeakReference<>(controller);
			this.latLon = latLon;
			selectedFullName = "";
		}

		@Nullable
		private MenuController getController() {
			return controllerRef.get();
		}

		@Nullable
		private MapActivity getMapActivity() {
			MenuController controller = getController();
			return controller != null ? controller.getMapActivity() : null;
		}

		@Override
		protected void onPreExecute() {
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				rm = mapActivity.getMyApplication().getResourceManager();
				osmandRegions = rm.getOsmandRegions();
			}
		}

		@Override
		protected BinaryMapDataObject doInBackground(Void... voids) {

			int point31x = MapUtils.get31TileNumberX(latLon.getLongitude());
			int point31y = MapUtils.get31TileNumberY(latLon.getLatitude());

			List<BinaryMapDataObject> mapDataObjects = null;
			try {
				mapDataObjects = osmandRegions.query(point31x, point31x, point31y, point31y);
			} catch (IOException e) {
				e.printStackTrace();
			}

			BinaryMapDataObject binaryMapDataObject = null;
			if (mapDataObjects != null) {
				Iterator<BinaryMapDataObject> it = mapDataObjects.iterator();
				while (it.hasNext()) {
					BinaryMapDataObject o = it.next();
					if (o.getTypes() != null) {
						boolean isRegion = true;
						for (int i = 0; i < o.getTypes().length; i++) {
							TagValuePair tp = o.getMapIndex().decodeType(o.getTypes()[i]);
							if ("boundary".equals(tp.value)) {
								isRegion = false;
								break;
							}
						}
						if (!isRegion || !osmandRegions.contain(o, point31x, point31y)) {
							it.remove();
						}
					}
				}
				double smallestArea = -1;
				for (BinaryMapDataObject o : mapDataObjects) {
					String downloadName = osmandRegions.getDownloadName(o);
					if (!Algorithms.isEmpty(downloadName)) {
						boolean downloaded = checkIfObjectDownloaded(rm, downloadName);
						if (downloaded) {
							binaryMapDataObject = null;
							break;
						} else {
							String fullName = osmandRegions.getFullName(o);
							WorldRegion region = osmandRegions.getRegionData(fullName);
							if (region != null && region.isRegionMapDownload()) {
								double area = OsmandRegions.getArea(o);
								if (smallestArea == -1) {
									smallestArea = area;
									selectedFullName = fullName;
									binaryMapDataObject = o;
								} else if (area < smallestArea) {
									smallestArea = area;
									selectedFullName = fullName;
									binaryMapDataObject = o;
								}
							}
						}
					}
				}
			}

			return binaryMapDataObject;
		}

		@Override
		protected void onPostExecute(BinaryMapDataObject binaryMapDataObject) {
			MenuController controller = getController();
			if (controller != null) {
				controller.createMapDownloadControls(binaryMapDataObject, selectedFullName);
			}
		}

		private boolean checkIfObjectDownloaded(ResourceManager rm, String downloadName) {
			boolean downloaded = rm.checkIfObjectDownloaded(downloadName);
			if (!downloaded) {
				WorldRegion region = rm.getOsmandRegions().getRegionDataByDownloadName(downloadName);
				if (region != null && region.getSuperregion() != null && region.getSuperregion().isRegionMapDownload()) {
					return checkIfObjectDownloaded(rm, region.getSuperregion().getRegionDownloadName());
				}
			}
			return downloaded;
		}
	}
}