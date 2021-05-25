package net.osmand.plus.views.mapwidgets.widgetstates;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.settings.backend.OsmandPreference;

public class ElevationProfileWidgetState extends WidgetState {

	public static final int SHOW_SLOPES_ID = R.id.elevation_widget_show_slopes;
	public static final int HIDE_SLOPES_ID = R.id.elevation_widget_hide_slopes;

	private final OsmandPreference<Boolean> showSlopes;

	public ElevationProfileWidgetState(OsmandApplication ctx) {
		super(ctx);
		this.showSlopes = ctx.getSettings().SHOW_SLOPES_ON_ELEVATION_WIDGET;
	}

	@Override
	public int getMenuTitleId() {
		return R.string.elevation_profile;
	}

	@Override
	public int getMenuIconId() {
		return R.drawable.ic_action_elevation;
	}

	@Override
	public int getMenuItemId() {
		return showSlopes.get() ? SHOW_SLOPES_ID : HIDE_SLOPES_ID;
	}

	@Override
	public int[] getMenuTitleIds() {
		return new int[] {R.string.shared_string_show_slope, R.string.shared_string_hide_slope};
	}

	@Override
	public int[] getMenuIconIds() {
		return new int[] {R.drawable.ic_action_slope, R.drawable.ic_action_slope_hide};
	}

	@Override
	public int[] getMenuItemIds() {
		return new int[] {SHOW_SLOPES_ID, HIDE_SLOPES_ID};
	}

	@Override
	public void changeState(int stateId) {
		showSlopes.set(stateId == SHOW_SLOPES_ID);
	}
}
