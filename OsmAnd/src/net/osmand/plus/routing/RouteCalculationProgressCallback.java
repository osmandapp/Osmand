package net.osmand.plus.routing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.map.WorldRegion;

import java.util.List;

public interface RouteCalculationProgressCallback {

	void start();

	void updateProgress(int progress);

	void requestPrivateAccessRouting();

	void updateMissingMaps(@Nullable List<WorldRegion> missingMaps, boolean onlineSearch);

	void finish();
}
