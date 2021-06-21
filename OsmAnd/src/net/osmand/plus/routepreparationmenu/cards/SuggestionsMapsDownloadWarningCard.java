package net.osmand.plus.routepreparationmenu.cards;

import androidx.annotation.NonNull;

import net.osmand.map.WorldRegion;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.MultipleSelectionBottomSheet;
import net.osmand.plus.base.SelectionBottomSheet;
import net.osmand.plus.base.SelectionBottomSheet.DialogStateListener;
import net.osmand.plus.base.SelectionBottomSheet.SelectableItem;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.DownloadItem;
import net.osmand.plus.download.DownloadValidationManager;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.util.Algorithms;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import static net.osmand.plus.download.MultipleDownloadItem.getIndexItem;

public class SuggestionsMapsDownloadWarningCard extends WarningCard {
	private SelectionBottomSheet dialog;
	private final List<WorldRegion> suggestedMaps;

	public SuggestionsMapsDownloadWarningCard(@NonNull MapActivity mapActivity) {
		super(mapActivity);
		MapRouteInfoMenu mapRouteInfoMenu = mapActivity.getMapRouteInfoMenu();
		boolean hasPrecalculatedMissingMaps = !Algorithms.isEmpty(mapRouteInfoMenu.getSuggestedMaps());
		if (hasPrecalculatedMissingMaps) {
			suggestedMaps = mapActivity.getMapRouteInfoMenu().getSuggestedMaps();
			imageId = R.drawable.ic_action_time_span;
			title = mapRouteInfoMenu.isSuggestedMapsOnlineSearch()
					? mapActivity.getString(R.string.online_maps_required_descr)
					: mapActivity.getString(R.string.direct_line_maps_required_descr);
			linkText = mapActivity.getString(R.string.online_direct_line_maps_link);
		} else {
			suggestedMaps = app.getRoutingHelper().getRoute().getMissingMaps();
			imageId = R.drawable.ic_map;
			title = mapActivity.getString(R.string.offline_maps_required_descr);
			linkText = mapActivity.getString(R.string.welmode_download_maps);
		}
	}

	private void showMultipleSelectionDialog() {
		List<DownloadItem> downloadItems = getSuggestedMapDownloadItems();
		List<SelectableItem> allItems = new ArrayList<>();
		List<SelectableItem> selectedItems = new ArrayList<>();
		for (DownloadItem di : downloadItems) {
			boolean isMap = di.getType().getTag().equals("map");
			SelectableItem si = createSelectableItem(di);
			if (isMap) {
				allItems.add(si);
				selectedItems.add(si);
			}
		}

		MultipleSelectionBottomSheet msDialog =
				MultipleSelectionBottomSheet.showInstance(mapActivity, allItems, selectedItems, true);
		this.dialog = msDialog;
		msDialog.setDialogStateListener(new DialogStateListener() {
			@Override
			public void onDialogCreated() {
				dialog.setTitle(app.getString(R.string.welmode_download_maps));
			}

			@Override
			public void onCloseDialog() {

			}
		});
		msDialog.setSelectionUpdateListener(this::updateSize);
		msDialog.setOnApplySelectionListener(selectedItems1 -> {
			List<IndexItem> indexes = new ArrayList<>();
			for (SelectableItem item : selectedItems1) {
				IndexItem index = getIndexItem((DownloadItem) item.getObject());
				if (index != null) {
					indexes.add(index);
				}
			}
			IndexItem[] indexesArray = new IndexItem[indexes.size()];
			new DownloadValidationManager(app).startDownload(mapActivity, indexes.toArray(indexesArray));
		});
	}

	private void updateSize() {
		double sizeToDownload = getDownloadSizeInMb(dialog.getSelectedItems());
		String size = DownloadItem.getFormattedMb(app, sizeToDownload);
		String total = app.getString(R.string.shared_string_total);
		String description = app.getString(R.string.ltr_or_rtl_combine_via_colon, total, size);
		dialog.setTitleDescription(description);
		String btnTitle = app.getString(R.string.shared_string_download);
		if (sizeToDownload > 0) {
			btnTitle = app.getString(R.string.ltr_or_rtl_combine_via_dash, btnTitle, size);
		}
		dialog.setApplyButtonTitle(btnTitle);
	}

	private double getDownloadSizeInMb(@NonNull List<SelectableItem> selectableItems) {
		double totalSizeMb = 0.0d;
		for (SelectableItem i : selectableItems) {
			Object obj = i.getObject();
			totalSizeMb += ((DownloadItem) obj).getSizeToDownloadInMb();
		}
		return totalSizeMb;
	}

	@NonNull
	public List<DownloadItem> getSuggestedMapDownloadItems() {
		DownloadIndexesThread downloadThread = app.getDownloadThread();
		if (!downloadThread.getIndexes().isDownloadedFromInternet) {
			if (mapActivity.getMyApplication().getSettings().isInternetConnectionAvailable()) {
				downloadThread.runReloadIndexFiles();
			}
		}

		boolean downloadIndexes = app.getSettings().isInternetConnectionAvailable()
				&& !downloadThread.getIndexes().isDownloadedFromInternet
				&& !downloadThread.getIndexes().downloadFromInternetFailed;

		List<DownloadItem> suggestedDownloadsMaps = new ArrayList<>();
		if (!downloadIndexes && !Algorithms.isEmpty(suggestedMaps)) {
			for (WorldRegion suggestedDownloadMap : suggestedMaps) {
				suggestedDownloadsMaps.addAll(app.getDownloadThread().getIndexes().getDownloadItems(suggestedDownloadMap));
			}
		}
		return suggestedDownloadsMaps;
	}

	@NonNull
	private SelectableItem createSelectableItem(@NonNull DownloadItem item) {
		SelectableItem selectableItem = new SelectableItem();
		updateSelectableItem(selectableItem, item);
		selectableItem.setObject(item);
		return selectableItem;
	}

	private void updateSelectableItem(@NonNull SelectableItem selectableItem,
									  @NonNull DownloadItem downloadItem) {
		selectableItem.setTitle(app.getRegions().getLocaleName(downloadItem.getBasename(), true, true));
		DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(app);
		String size = downloadItem.getSizeDescription(app);
		String additionalDescription = downloadItem.getAdditionalDescription(app);
		if (additionalDescription != null) {
			size += " " + additionalDescription;
		}
		String date = downloadItem.getDate(dateFormat, true);
		String description = app.getString(R.string.ltr_or_rtl_combine_via_bold_point, size, date);
		selectableItem.setDescription(description);

		selectableItem.setIconId(downloadItem.getType().getIconResource());
	}

	@Override
	protected void onLinkClicked() {
		showMultipleSelectionDialog();
	}

}

