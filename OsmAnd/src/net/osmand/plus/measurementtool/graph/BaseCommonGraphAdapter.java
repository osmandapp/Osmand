package net.osmand.plus.measurementtool.graph;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;

import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;

public class BaseCommonGraphAdapter extends BaseGraphAdapter<LineChart, LineData, GpxDisplayItem>{

	public BaseCommonGraphAdapter(LineChart chart, boolean usedOnMap) {
		super(chart, usedOnMap);
	}

	@Override
	public void updateView() {
		chart.setData(chartData);
		updateHighlight();
	}

}
