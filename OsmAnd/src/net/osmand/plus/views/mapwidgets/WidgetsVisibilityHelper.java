package net.osmand.plus.views.mapwidgets;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.MapActivityLayers;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapcontextmenu.MapContextMenuFragment;
import net.osmand.plus.mapcontextmenu.other.MapMultiSelectionMenu;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.layers.MapQuickActionLayer;

import java.lang.ref.WeakReference;

public class WidgetsVisibilityHelper {

	private final MapActivity mapActivity;
	private final OsmandSettings settings;
	private final RoutingHelper routingHelper;
	private final MapActivityLayers mapLayers;

	public WidgetsVisibilityHelper(@NonNull MapActivity mapActivity) {
		this.mapActivity = mapActivity;
		this.settings = mapActivity.getMyApplication().getSettings();
		this.routingHelper = mapActivity.getRoutingHelper();
		this.mapLayers = mapActivity.getMapLayers();
	}

	public boolean shouldShowQuickActionButton() {
		return isQuickActionLayerOn()
				&& !isInChangeMarkerPositionMode()
				&& !isInGpxDetailsMode()
				&& !isInMeasurementToolMode()
				&& !isInPlanRouteMode()
				&& !isInTrackAppearanceMode()
				&& !isInTrackMenuMode()
				&& !isInRouteLineAppearanceMode()
				&& !isMapRouteInfoMenuVisible()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode()
				&& !isContextMenuFragmentVisible()
				&& !isMultiSelectionMenuFragmentVisible();
	}

	public boolean shouldShowTopCoordinatesWidget() {
		return settings.SHOW_COORDINATES_WIDGET.get()
				&& !mapActivity.shouldHideTopControls()
				&& mapActivity.getMapRouteInfoMenu().shouldShowTopControls()
				&& !mapActivity.isTopToolbarActive()
				&& !isInTrackAppearanceMode()
				&& !isInRouteLineAppearanceMode()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode();
	}

	public boolean shouldHideMapMarkersWidget() {
		View addressTopBar = mapActivity.findViewById(R.id.map_top_bar);
		return !settings.MARKERS_DISTANCE_INDICATION_ENABLED.get()
				|| !settings.MAP_MARKERS_MODE.get().isToolbar()
				|| addressTopBar != null && addressTopBar.getVisibility() == View.VISIBLE
				|| routingHelper.isFollowingMode()
				|| routingHelper.isRoutePlanningMode()
				|| isMapRouteInfoMenuVisible()
				|| mapActivity.isTopToolbarActive()
				|| mapActivity.shouldHideTopControls()
				|| isInTrackAppearanceMode()
				|| isInPlanRouteMode()
				|| isInRouteLineAppearanceMode();
	}

	public boolean shouldShowBottomMenuButtons() {
		return !mapActivity.shouldHideTopControls()
				&& !isInMovingMarkerMode()
				&& !isInGpxDetailsMode()
				&& !isInMeasurementToolMode()
				&& !isInPlanRouteMode()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode()
				&& !isInTrackAppearanceMode()
				&& !isInRouteLineAppearanceMode();
	}

	public boolean shouldShowZoomButtons() {
		boolean additionalDialogsHide = !isInGpxApproximationMode()
				&& !isInTrackAppearanceMode()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInRouteLineAppearanceMode();
		return !mapActivity.shouldHideTopControls()
				&& !isInFollowTrackMode()
				&& (additionalDialogsHide || !isPortrait());
	}

	public boolean shouldHideCompass() {
		return mapActivity.shouldHideTopControls()
				|| isTrackDetailsMenuOpened()
				|| isInPlanRouteMode()
				|| isInChoosingRoutesMode()
				|| isInTrackAppearanceMode()
				|| isInWaypointsChoosingMode()
				|| isInFollowTrackMode()
				|| isInRouteLineAppearanceMode();
	}

	public boolean shouldShowTopButtons() {
		return !mapActivity.shouldHideTopControls()
				&& !isTrackDetailsMenuOpened()
				&& !isInPlanRouteMode()
				&& !isInChoosingRoutesMode()
				&& !isInTrackAppearanceMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode()
				&& !isInRouteLineAppearanceMode();
	}

	public boolean shouldShowBackToLocationButton() {
		boolean additionalDialogsHide = !isInTrackAppearanceMode()
				&& !isInGpxApproximationMode()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode()
				&& !isInRouteLineAppearanceMode();
		return !mapActivity.shouldHideTopControls()
				&& !isInPlanRouteMode()
				&& !(isMapLinkedToLocation() && routingHelper.isFollowingMode())
				&& (additionalDialogsHide || !isPortrait());
	}

	public boolean shouldShowElevationProfileWidget() {
		return settings.SHOW_ELEVATION_PROFILE_WIDGET.get()
				&& isRouteCalculated()
				&& !isInChangeMarkerPositionMode()
				&& !isInGpxDetailsMode()
				&& !isInMeasurementToolMode()
				&& !isInPlanRouteMode()
				&& !isInTrackAppearanceMode()
				&& !isInTrackMenuMode()
				&& !isInRouteLineAppearanceMode()
				&& !isMapRouteInfoMenuVisible()
				&& !isInChoosingRoutesMode()
				&& !isInWaypointsChoosingMode()
				&& !isInFollowTrackMode()
				&& !isContextMenuFragmentVisible()
				&& !isMultiSelectionMenuFragmentVisible();
	}

	private boolean isQuickActionLayerOn() {
		return mapLayers.getMapQuickActionLayer().isLayerOn();
	}

	private boolean isMapRouteInfoMenuVisible() {
		return mapActivity.getMapRouteInfoMenu().isVisible();
	}

	private boolean isInMovingMarkerMode() {
		MapQuickActionLayer quickActionLayer = mapLayers.getMapQuickActionLayer();
		boolean isInMovingMarkerMode = quickActionLayer != null && quickActionLayer.isInMovingMarkerMode();
		return isInMovingMarkerMode || isInChangeMarkerPositionMode() || isInAddGpxPointMode();
	}

	private boolean isInGpxDetailsMode() {
		return mapLayers.getContextMenuLayer().isInGpxDetailsMode();
	}

	private boolean isInAddGpxPointMode() {
		return mapLayers.getContextMenuLayer().isInAddGpxPointMode();
	}

	private boolean isInChangeMarkerPositionMode() {
		return mapLayers.getContextMenuLayer().isInChangeMarkerPositionMode();
	}

	private boolean isInMeasurementToolMode() {
		return mapLayers.getMeasurementToolLayer().isInMeasurementMode();
	}

	private boolean isInPlanRouteMode() {
		return mapLayers.getMapMarkersLayer().isInPlanRouteMode();
	}

	private boolean isInTrackAppearanceMode() {
		return mapLayers.getGpxLayer().isInTrackAppearanceMode();
	}

	private boolean isInGpxApproximationMode() {
		return mapLayers.getMeasurementToolLayer().isTapsDisabled();
	}

	public boolean isInTrackMenuMode() {
		return mapActivity.getTrackMenuFragment() != null && mapActivity.getTrackMenuFragment().isVisible();
	}

	private boolean isInChoosingRoutesMode() {
		return MapRouteInfoMenu.chooseRoutesVisible;
	}

	private boolean isInWaypointsChoosingMode() {
		return MapRouteInfoMenu.waypointsVisible;
	}

	private boolean isInRouteLineAppearanceMode() {
		return mapLayers.getRouteLayer().isPreviewRouteLineVisible();
	}

	private boolean isInFollowTrackMode() {
		return MapRouteInfoMenu.followTrackVisible;
	}

	private boolean isContextMenuFragmentVisible() {
		MapContextMenu contextMenu = mapActivity.getContextMenu();
		WeakReference<MapContextMenuFragment> contextMenuMenuFragmentRef = contextMenu.findMenuFragment();
		MapContextMenuFragment contextMenuMenuFragment = contextMenuMenuFragmentRef != null ? contextMenuMenuFragmentRef.get() : null;
		if (contextMenuMenuFragment != null && contextMenu.isVisible()) {
			return !contextMenuMenuFragment.isRemoving() || contextMenuMenuFragment.isAdded();
		}
		return false;
	}

	private boolean isMultiSelectionMenuFragmentVisible() {
		MapContextMenu contextMenu = mapActivity.getContextMenu();
		MapMultiSelectionMenu multiSelectionMenu = contextMenu.getMultiSelectionMenu();
		Fragment multiMenuFragment = multiSelectionMenu.getFragmentByTag();
		if (multiMenuFragment != null && multiSelectionMenu.isVisible()) {
			return multiMenuFragment.isAdded() || !multiMenuFragment.isRemoving();
		}
		return false;
	}

	private boolean isMapLinkedToLocation() {
		return mapActivity.getMapViewTrackingUtilities().isMapLinkedToLocation();
	}

	private boolean isTrackDetailsMenuOpened() {
		return mapActivity.getTrackDetailsMenu().isVisible();
	}

	private boolean isRouteCalculated() {
		return mapActivity.getRoutingHelper().isRouteCalculated();
	}

	private boolean isPortrait() {
		return AndroidUiHelper.isOrientationPortrait(mapActivity);
	}

	public void updateControlsVisibility(boolean topControlsVisible, boolean bottomControlsVisible) {
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_center_info), topControlsVisible);
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_left_widgets_panel), topControlsVisible);
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_right_widgets_panel), topControlsVisible);
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.bottom_controls_container), bottomControlsVisible);
	}
}
