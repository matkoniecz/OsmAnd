package net.osmand.plus.track;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.ColorUtilities;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmAndLocationProvider.OsmAndCompassListener;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.UiUtilities.UpdateLocationViewCache;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.OsmandBaseExpandableListAdapter;
import net.osmand.plus.base.PointImageDrawable;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.myplaces.DeletePointsTask;
import net.osmand.plus.myplaces.DeletePointsTask.OnPointsDeleteListener;
import net.osmand.plus.myplaces.EditTrackGroupDialogFragment;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.track.DisplayPointsGroupsHelper.DisplayGroupsHolder;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrackPointsCard extends MapBaseCard implements OnChildClickListener, OnPointsDeleteListener,
		OsmAndCompassListener, OsmAndLocationListener {

	public static final int ADD_WAYPOINT_INDEX = 0;
	public static final int DELETE_WAYPOINTS_INDEX = 1;
	public static final int OPEN_WAYPOINT_INDEX = 2;

	private final TrackDisplayHelper displayHelper;
	private final SelectedGpxFile selectedGpxFile;

	private GpxDisplayGroup selectedGroup;
	private final Set<Integer> selectedGroups = new LinkedHashSet<>();
	private final LinkedHashMap<GpxDisplayItemType, Set<GpxDisplayItem>> selectedItems = new LinkedHashMap<>();
	private boolean selectionMode;

	private final PointGPXAdapter adapter;
	private ExpandableListView listView;
	private View addActionsView;
	private View addWaypointActionView;
	private View deleteWaypointActionView;

	private Location lastLocation;
	private float lastHeading;
	private boolean locationDataUpdateAllowed = true;

	public TrackPointsCard(@NonNull MapActivity mapActivity,
	                       @NonNull TrackDisplayHelper displayHelper,
	                       @NonNull SelectedGpxFile selectedGpxFile) {
		super(mapActivity);
		this.displayHelper = displayHelper;
		this.selectedGpxFile = selectedGpxFile;
		adapter = new PointGPXAdapter();
	}

	public boolean isSelectionMode() {
		return selectionMode;
	}

	public void setSelectionMode(boolean selectionMode) {
		this.selectionMode = selectionMode;
		adapter.notifyDataSetInvalidated();
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.track_points_card;
	}

	@Override
	protected void updateContent() {
		listView = view.findViewById(android.R.id.list);
		listView.setOnChildClickListener(this);

		List<GpxDisplayGroup> displayGroups = getOriginalGroups();
		adapter.setFilterResults(null);
		adapter.synchronizeGroups(displayGroups);
		if (listView.getAdapter() == null) {
			listView.setAdapter(adapter);
		}

		listView.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				locationDataUpdateAllowed = scrollState == SCROLL_STATE_IDLE;
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			}
		});

		LayoutInflater inflater = UiUtilities.getInflater(mapActivity, nightMode);
		if (addActionsView == null && addWaypointActionView == null) {
			listView.addFooterView(inflater.inflate(R.layout.list_shadow_footer, listView, false));
			addActions(inflater);
			addWaypointAction(inflater);
		}
		if (!adapter.isEmpty() && deleteWaypointActionView == null) {
			AndroidUiHelper.updateVisibility(addWaypointActionView.findViewById(R.id.divider), true);
			deleteWaypointAction(inflater);
		} else if (adapter.isEmpty() && deleteWaypointActionView != null) {
			AndroidUiHelper.updateVisibility(addWaypointActionView.findViewById(R.id.divider), false);
			listView.removeFooterView(deleteWaypointActionView);
			deleteWaypointActionView = null;
		}
		expandAllGroups();
	}

	public void setSelectedGroup(GpxDisplayGroup selectedGroup) {
		this.selectedGroup = selectedGroup;
		onSelectedGroupChanged();
	}

	private void onSelectedGroupChanged() {
		if (selectedGroup != null) {
			scrollToGroup(selectedGroup);
		} else {
			scrollToInitialPosition();
		}
	}

	public void updateGroups() {
		selectedItems.clear();
		selectedGroups.clear();
	}

	private void scrollToGroup(@NonNull GpxDisplayGroup group) {
		int index = adapter.getGroupIndex(group);
		if (index >= 0) {
			listView.setSelectedGroup(index);
		}
	}

	private void scrollToInitialPosition() {
		if (listView.getCount() > 0) {
			listView.setSelectedGroup(0);
		}
	}

	public List<GpxDisplayGroup> getGroups() {
		return adapter.groups;
	}

	public void onGroupVisibilityChanged() {
		adapter.notifyDataSetChanged();
	}

	public void startListeningLocationUpdates() {
		OsmAndLocationProvider locationProvider = app.getLocationProvider();
		locationProvider.resumeAllUpdates();
		locationProvider.addCompassListener(this);
		locationProvider.addLocationListener(this);
		locationProvider.removeCompassListener(locationProvider.getNavigationInfo());
		onLocationDataUpdate();
	}

	public void stopListeningLocationUpdates() {
		OsmAndLocationProvider locationProvider = app.getLocationProvider();
		locationProvider.removeCompassListener(this);
		locationProvider.removeLocationListener(this);
		locationProvider.addCompassListener(locationProvider.getNavigationInfo());
	}

	private void addActions(LayoutInflater inflater) {
		addActionsView = inflater.inflate(R.layout.preference_category_with_descr, listView, false);
		TextView title = addActionsView.findViewById(android.R.id.title);
		title.setText(R.string.shared_string_actions);

		AndroidUiHelper.updateVisibility(addActionsView.findViewById(android.R.id.icon), false);
		AndroidUiHelper.updateVisibility(addActionsView.findViewById(android.R.id.summary), false);
		listView.addFooterView(addActionsView);
	}

	private void addWaypointAction(LayoutInflater inflater) {
		addWaypointActionView = inflater.inflate(R.layout.preference_button, listView, false);
		TextView addWaypointTitle = addWaypointActionView.findViewById(android.R.id.title);
		ImageView addWaypointIcon = addWaypointActionView.findViewById(android.R.id.icon);

		addWaypointTitle.setText(R.string.add_waypoint);
		addWaypointIcon.setImageDrawable(getContentIcon(R.drawable.ic_action_name_field));


		addWaypointActionView.setOnClickListener(v -> notifyButtonPressed(ADD_WAYPOINT_INDEX));
		listView.addFooterView(addWaypointActionView);
	}

	private void deleteWaypointAction(LayoutInflater inflater) {
		deleteWaypointActionView = inflater.inflate(R.layout.preference_button, listView, false);
		TextView deleteWaypointsTitle = deleteWaypointActionView.findViewById(android.R.id.title);
		ImageView deleteWaypointsIcon = deleteWaypointActionView.findViewById(android.R.id.icon);

		deleteWaypointsTitle.setText(R.string.delete_waypoints);
		deleteWaypointsIcon.setImageDrawable(getColoredIcon(R.drawable.ic_action_delete_dark, R.color.color_osm_edit_delete));

		deleteWaypointActionView.setOnClickListener(v -> notifyButtonPressed(DELETE_WAYPOINTS_INDEX));
		listView.addFooterView(deleteWaypointActionView);
	}

	private void expandAllGroups() {
		for (int i = 0; i < adapter.getGroupCount(); i++) {
			listView.expandGroup(i);
		}
	}

	private List<GpxDisplayGroup> getOriginalGroups() {
		return displayHelper.getPointsOriginalGroups();
	}

	private List<GpxDisplayGroup> getDisplayGroups() {
		if (selectedGroup != null) {
			List<GpxDisplayGroup> res = new ArrayList<>();
			res.add(selectedGroup);
			return res;
		} else {
			return getOriginalGroups();
		}
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		GpxDisplayItem item = adapter.getChild(groupPosition, childPosition);
		if (item != null && item.locationStart != null) {
			notifyButtonPressed(OPEN_WAYPOINT_INDEX);
			LatLon location = new LatLon(item.locationStart.lat, item.locationStart.lon);
			PointDescription description = new PointDescription(PointDescription.POINT_TYPE_WPT, item.name);

			MapContextMenu contextMenu = mapActivity.getContextMenu();
			contextMenu.setCenterMarker(true);
			contextMenu.show(location, description, item.locationStart);
		}
		return true;
	}

	public void deleteItemsAction() {
		int size = getSelectedItemsCount();
		if (size > 0) {
			AlertDialog.Builder b = new AlertDialog.Builder(mapActivity);
			b.setMessage(app.getString(R.string.points_delete_multiple, size));
			b.setPositiveButton(R.string.shared_string_delete, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					deleteItems();
					setSelectionMode(false);
					adapter.notifyDataSetInvalidated();
				}
			});
			b.setNegativeButton(R.string.shared_string_cancel, null);
			b.show();
		}
	}

	private void deleteItems() {
		new DeletePointsTask(app, displayHelper.getGpx(), getSelectedItems(), this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private Set<GpxDisplayItem> getSelectedItems() {
		Set<GpxDisplayItem> result = new LinkedHashSet<>();
		for (Set<GpxDisplayItem> set : selectedItems.values()) {
			if (set != null) {
				result.addAll(set);
			}
		}
		return result;
	}

	private void updateSelectionMode() {
		int size = getSelectedItemsCount();
		app.showToastMessage(size + " " + app.getString(R.string.shared_string_selected_lowercase));
	}

	private int getSelectedItemsCount() {
		int count = 0;
		for (Set<GpxDisplayItem> set : selectedItems.values()) {
			if (set != null) {
				count += set.size();
			}
		}
		return count;
	}

	@Override
	public void onPointsDeletionStarted() {

	}

	@Override
	public void onPointsDeleted() {
		updateGroups();
		update();
	}

	public void filter(String text) {
		adapter.getFilter().filter(text);
	}

	@Override
	public void updateCompassValue(float heading) {
		if (Math.abs(MapUtils.degreesDiff(lastHeading, heading)) > 5) {
			lastHeading = heading;
			onLocationDataUpdate();
		}
	}

	@Override
	public void updateLocation(Location location) {
		if (!MapUtils.areLatLonEqual(lastLocation, location)) {
			lastLocation = location;
			onLocationDataUpdate();
		}
	}

	private void onLocationDataUpdate() {
		if (locationDataUpdateAllowed) {
			app.runInUIThread(adapter::notifyDataSetChanged);
		}
	}

	private class PointGPXAdapter extends OsmandBaseExpandableListAdapter implements Filterable {

		private static final int SPANNED_FLAG = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

		private final List<GpxDisplayGroup> groups = new ArrayList<>();
		private final Map<GpxDisplayGroup, List<GpxDisplayItem>> itemGroups = new LinkedHashMap<>();
		private Filter pointsFilter;
		private Set<?> filteredItems;

		private final UpdateLocationViewCache locationViewCache;

		PointGPXAdapter() {
			locationViewCache = app.getUIUtilities().getUpdateLocationViewCache();
		}

		public void synchronizeGroups(@NonNull List<GpxDisplayGroup> displayGroups) {
			DisplayGroupsHolder displayGroupsHolder = DisplayPointsGroupsHelper.getGroups(app, displayGroups, filteredItems);
			groups.clear();
			itemGroups.clear();
			groups.addAll(displayGroupsHolder.groups);
			itemGroups.putAll(displayGroupsHolder.itemGroups);
			notifyDataSetChanged();
		}

		@Override
		public int getGroupCount() {
			return groups.size();
		}

		@Override
		public GpxDisplayGroup getGroup(int groupPosition) {
			return groups.get(groupPosition);
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return itemGroups.get(groups.get(groupPosition)).size();
		}

		@Override
		public GpxDisplayItem getChild(int groupPosition, int childPosition) {
			return itemGroups.get(groups.get(groupPosition)).get(childPosition);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return groupPosition * 10000 + childPosition;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			final GpxDisplayGroup group = getGroup(groupPosition);
			Context context = view.getContext();
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = LayoutInflater.from(context);
				row = inflater.inflate(R.layout.wpt_list_item, parent, false);
			}

			row.setOnClickListener(v -> {
				if (listView.isGroupExpanded(groupPosition)) {
					listView.collapseGroup(groupPosition);
				} else {
					listView.expandGroup(groupPosition);
				}
			});

			String groupName = group.getName();
			String nameToDisplay = Algorithms.isEmpty(groupName)
					? app.getString(R.string.shared_string_gpx_points)
					: groupName;

			TextView title = row.findViewById(R.id.label);
			title.setText(createStyledGroupTitle(context, nameToDisplay, groupPosition));

			Drawable icon = selectedGpxFile.isGroupHidden(Algorithms.isEmpty(groupName) ? null : groupName)
					? getColoredIcon(R.drawable.ic_action_folder_hidden, ColorUtilities.getSecondaryTextColorId(nightMode))
					: getContentIcon(R.drawable.ic_action_folder);
			ImageView groupImage = row.findViewById(R.id.icon);
			groupImage.setImageDrawable(icon);

			boolean expanded = listView.isGroupExpanded(groupPosition);
			ImageView expandImage = row.findViewById(R.id.expand_image);
			expandImage.setImageDrawable(getContentIcon(expanded ? R.drawable.ic_action_arrow_up : R.drawable.ic_action_arrow_down));

			final CheckBox checkBox = row.findViewById(R.id.toggle_item);
			if (selectionMode) {
				checkBox.setChecked(selectedGroups.contains(groupPosition));
				checkBox.setOnClickListener(v -> {
					List<GpxDisplayItem> items = itemGroups.get(group);
					setGroupSelection(items, groupPosition, checkBox.isChecked());
					adapter.notifyDataSetInvalidated();
					updateSelectionMode();
				});
				AndroidUiHelper.updateVisibility(checkBox, true);
			} else {
				AndroidUiHelper.updateVisibility(checkBox, false);
			}

			ImageView options = row.findViewById(R.id.options);
			options.setImageDrawable(getContentIcon(R.drawable.ic_overflow_menu_with_background));
			options.setOnClickListener(v ->
					EditTrackGroupDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
							group, mapActivity.getTrackMenuFragment()));

			AndroidUiHelper.updateVisibility(expandImage, true);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.divider), true);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.waypoint_description), false);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.list_divider), false);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.group_divider), true);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.vertical_divider), true);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.location_data), false);

			return row;
		}

		private CharSequence createStyledGroupTitle(Context context, String groupName, int groupPosition) {
			SpannableStringBuilder spannedName = new SpannableStringBuilder(groupName)
					.append(" – ")
					.append(String.valueOf(getChildrenCount(groupPosition)));

			if (selectedGpxFile.isGroupHidden(groupName)) {
				int secondaryTextColor = ColorUtilities.getSecondaryTextColor(context, nightMode);
				spannedName.setSpan(new ForegroundColorSpan(secondaryTextColor), 0, spannedName.length(), SPANNED_FLAG);
				spannedName.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannedName.length(), SPANNED_FLAG);
			} else {
				int nameColor = AndroidUtils.getColorFromAttr(context, R.attr.wikivoyage_primary_text_color);
				int countColor = ContextCompat.getColor(context, R.color.wikivoyage_secondary_text);

				spannedName.setSpan(new ForegroundColorSpan(nameColor), 0, groupName.length(), SPANNED_FLAG);
				spannedName.setSpan(new ForegroundColorSpan(countColor), groupName.length() + 1,
						spannedName.length(), SPANNED_FLAG);
			}

			spannedName.setSpan(new StyleSpan(Typeface.BOLD), 0, groupName.length(), SPANNED_FLAG);

			return spannedName;
		}

		private void setGroupSelection(List<GpxDisplayItem> items, int groupPosition, boolean select) {
			GpxDisplayGroup group = groups.get(groupPosition);
			if (select) {
				selectedGroups.add(groupPosition);
				if (items != null) {
					Set<GpxDisplayItem> set = selectedItems.get(group.getType());
					if (set != null) {
						set.addAll(items);
					} else {
						set = new LinkedHashSet<>(items);
						selectedItems.put(group.getType(), set);
					}
				}
			} else {
				selectedGroups.remove(groupPosition);
				selectedItems.remove(group.getType());
			}
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = LayoutInflater.from(view.getContext());
				row = inflater.inflate(R.layout.wpt_list_item, parent, false);
			}

			final GpxDisplayGroup group = getGroup(groupPosition);
			final GpxDisplayItem gpxItem = getChild(groupPosition, childPosition);

			TextView title = row.findViewById(R.id.label);
			title.setText(gpxItem.name);

			TextView description = row.findViewById(R.id.waypoint_description);
			if (!Algorithms.isEmpty(gpxItem.description)) {
				description.setText(gpxItem.description);
				AndroidUiHelper.updateVisibility(description, true);
			} else {
				AndroidUiHelper.updateVisibility(description, false);
			}

			final CheckBox checkBox = row.findViewById(R.id.toggle_item);
			if (selectionMode) {
				checkBox.setVisibility(View.VISIBLE);
				checkBox.setChecked(selectedItems.get(group.getType()) != null && selectedItems.get(group.getType()).contains(gpxItem));
				checkBox.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						if (checkBox.isChecked()) {
							Set<GpxDisplayItem> set = selectedItems.get(group.getType());
							if (set != null) {
								set.add(gpxItem);
							} else {
								set = new LinkedHashSet<>();
								set.add(gpxItem);
								selectedItems.put(group.getType(), set);
							}
						} else {
							Set<GpxDisplayItem> set = selectedItems.get(group.getType());
							if (set != null) {
								set.remove(gpxItem);
							}
						}
						updateSelectionMode();
					}
				});
				AndroidUiHelper.updateVisibility(checkBox, true);
				AndroidUiHelper.updateVisibility(row.findViewById(R.id.icon), false);
			} else {
				ImageView icon = row.findViewById(R.id.icon);
				if (GpxDisplayItemType.TRACK_POINTS == group.getType()) {
					WptPt wpt = gpxItem.locationStart;
					int groupColor = wpt.getColor(group.getColor());
					if (groupColor == 0) {
						groupColor = ContextCompat.getColor(app, R.color.gpx_color_point);
					}
					icon.setImageDrawable(PointImageDrawable.getFromWpt(app, groupColor, false, wpt));
				} else {
					icon.setImageDrawable(getContentIcon(R.drawable.ic_action_marker_dark));
				}
				AndroidUiHelper.updateVisibility(icon, true);
				AndroidUiHelper.updateVisibility(checkBox, false);
			}

			setupLocationData(row, gpxItem.locationStart);

			AndroidUiHelper.updateVisibility(row.findViewById(R.id.divider), false);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.vertical_divider), false);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.options), false);
			AndroidUiHelper.updateVisibility(row.findViewById(R.id.list_divider), childPosition != 0);

			return row;
		}

		private void setupLocationData(@NonNull View container, @NonNull WptPt point) {
			AppCompatImageView directionArrow = container.findViewById(R.id.direction_arrow);
			TextView distanceText = container.findViewById(R.id.distance);
			app.getUIUtilities().updateLocationView(locationViewCache, directionArrow, distanceText, point.lat, point.lon);

			TextView addressContainer = container.findViewById(R.id.address);
			addressContainer.setText(point.getAddress());
		}

		public int getGroupIndex(@NonNull GpxDisplayGroup group) {
			String name = group.getName();
			for (GpxDisplayGroup g : groups) {
				if (Algorithms.objectEquals(name, g.getName())) {
					return groups.indexOf(g);
				}
			}
			return -1;
		}

		@Override
		public Filter getFilter() {
			if (pointsFilter == null) {
				pointsFilter = new PointsFilter();
			}
			return pointsFilter;
		}

		public void setFilterResults(Set<?> values) {
			this.filteredItems = values;
		}
	}

	public class PointsFilter extends Filter {

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();
			if (constraint == null || constraint.length() == 0) {
				results.values = null;
				results.count = 1;
			} else {
				Set<Object> filter = new HashSet<>();
				String cs = constraint.toString().toLowerCase();
				for (GpxDisplayGroup g : getOriginalGroups()) {
					for (GpxDisplayItem i : g.getModifiableList()) {
						if (i.name.toLowerCase().contains(cs)) {
							filter.add(i);
						} else if (i.locationStart != null && !TextUtils.isEmpty(i.locationStart.category)
								&& i.locationStart.category.toLowerCase().contains(cs)) {
							filter.add(i.locationStart.category);
						}
					}
				}
				results.values = filter;
				results.count = filter.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			synchronized (adapter) {
				adapter.setFilterResults((Set<?>) results.values);
				adapter.synchronizeGroups(getOriginalGroups());
			}
			adapter.notifyDataSetChanged();
			expandAllGroups();
			onSelectedGroupChanged();
		}
	}
}