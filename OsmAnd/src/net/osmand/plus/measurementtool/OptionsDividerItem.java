package net.osmand.plus.measurementtool;

import android.content.Context;

import net.osmand.plus.R;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.DividerItem;

public class OptionsDividerItem extends DividerItem {

	public OptionsDividerItem(Context context) {
		super(context);
	}

	@Override
	protected int getBottomMargin(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.bottom_sheet_divider_margin_top);
	}

	@Override
	protected int getStartMargin(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.measurement_tool_options_divider_margin_start);
	}
}