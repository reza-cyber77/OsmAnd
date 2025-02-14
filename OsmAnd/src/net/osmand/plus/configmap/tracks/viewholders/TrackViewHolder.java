package net.osmand.plus.configmap.tracks.viewholders;

import static net.osmand.plus.configmap.tracks.TracksSortMode.DATE_ASCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.DATE_DESCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.DISTANCE_ASCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.DISTANCE_DESCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.DURATION_ASCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.DURATION_DESCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.LAST_MODIFIED;
import static net.osmand.plus.configmap.tracks.TracksSortMode.NAME_ASCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.NAME_DESCENDING;
import static net.osmand.plus.configmap.tracks.TracksSortMode.NEAREST;
import static net.osmand.plus.track.fragments.TrackAppearanceFragment.getTrackIcon;
import static net.osmand.plus.utils.ColorUtilities.getSecondaryTextColor;

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.data.LatLon;
import net.osmand.gpx.GPXTrackAnalysis;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.configmap.tracks.SelectedTracksHelper;
import net.osmand.plus.configmap.tracks.TrackItem;
import net.osmand.plus.configmap.tracks.TrackTab;
import net.osmand.plus.configmap.tracks.TracksAdapter;
import net.osmand.plus.configmap.tracks.TracksFragment;
import net.osmand.plus.configmap.tracks.TracksSortMode;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.track.GpxAppearanceAdapter;
import net.osmand.plus.track.helpers.GPXDatabase.GpxDataItem;
import net.osmand.plus.track.helpers.GpxDbHelper;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.utils.UpdateLocationUtils;
import net.osmand.plus.utils.UpdateLocationUtils.UpdateLocationInfo;
import net.osmand.plus.utils.UpdateLocationUtils.UpdateLocationViewCache;
import net.osmand.plus.widgets.style.CustomTypefaceSpan;
import net.osmand.util.Algorithms;

import java.io.File;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;

public class TrackViewHolder extends RecyclerView.ViewHolder {

	private final OsmandApplication app;
	private final GpxDbHelper gpxDbHelper;

	private final UpdateLocationViewCache locationViewCache;
	private final TracksFragment fragment;
	private final boolean nightMode;

	private final TextView title;
	private final TextView description;
	private final ImageView imageView;
	private final CompoundButton checkbox;
	private final View divider;
	private final ImageView directionIcon;

	public TrackViewHolder(@NonNull View itemView, @NonNull TracksFragment fragment,
	                       @NonNull UpdateLocationViewCache viewCache, boolean nightMode) {
		super(itemView);
		this.app = (OsmandApplication) itemView.getContext().getApplicationContext();
		this.gpxDbHelper = app.getGpxDbHelper();
		this.locationViewCache = viewCache;
		this.fragment = fragment;
		this.nightMode = nightMode;

		title = itemView.findViewById(R.id.title);
		description = itemView.findViewById(R.id.description);
		directionIcon = itemView.findViewById(R.id.direction_icon);
		checkbox = itemView.findViewById(R.id.checkbox);
		imageView = itemView.findViewById(R.id.track_icon);
		divider = itemView.findViewById(R.id.divider);
	}

	public void bindView(@NonNull TracksAdapter adapter, @NonNull TrackItem trackItem, boolean showDivider) {
		title.setText(trackItem.getName());

		SelectedTracksHelper helper = fragment.getSelectedTracksHelper();
		boolean selected = helper.getSelectedTracks().contains(trackItem);

		checkbox.setChecked(selected);
		int activeColor = ColorUtilities.getActiveColor(app, nightMode);
		UiUtilities.setupCompoundButton(nightMode, activeColor, checkbox);
		itemView.setOnClickListener(v -> fragment.onTrackItemsSelected(Collections.singleton(trackItem), !selected));

		AndroidUiHelper.updateVisibility(divider, showDivider);
		bindInfoRow(adapter, trackItem);
	}

	public void bindInfoRow(@NonNull TracksAdapter adapter, @NonNull TrackItem trackItem) {
		File file = trackItem.getFile();
		GpxDataItem dataItem = trackItem.getDataItem();
		if (dataItem != null) {
			buildDescriptionRow(adapter, dataItem, trackItem);
		} else if (file != null) {
			dataItem = gpxDbHelper.getItem(file, item -> {
				trackItem.setDataItem(item);
				if (fragment.isAdded()) {
					adapter.notifyItemChanged(getAdapterPosition());
				}
			});
			if (dataItem != null) {
				trackItem.setDataItem(dataItem);
				buildDescriptionRow(adapter, dataItem, trackItem);
			}
		}
	}

	private void buildDescriptionRow(@NonNull TracksAdapter adapter, @NonNull GpxDataItem dataItem, @NonNull TrackItem trackItem) {
		setupIcon(dataItem);

		TrackTab trackTab = adapter.getTrackTab();
		TracksSortMode sortMode = trackTab.getSortMode();
		GPXTrackAnalysis analysis = dataItem.getAnalysis();
		if (analysis != null) {
			SpannableStringBuilder builder = new SpannableStringBuilder();
			if (sortMode == NAME_ASCENDING || sortMode == NAME_DESCENDING) {
				appendNameDescription(builder, trackTab, trackItem, analysis);
			} else if (sortMode == DATE_ASCENDING || sortMode == DATE_DESCENDING) {
				appendCreationTimeDescription(builder, analysis);
			} else if (sortMode == DISTANCE_ASCENDING || sortMode == DISTANCE_DESCENDING) {
				appendDistanceDescription(builder, trackTab, trackItem, analysis);
			} else if (sortMode == DURATION_ASCENDING || sortMode == DURATION_DESCENDING) {
				appendDurationDescription(builder, trackTab, trackItem, analysis);
			} else if (sortMode == NEAREST) {
				appendNearestDescription(adapter, builder, trackItem, analysis);
			} else if (sortMode == LAST_MODIFIED) {
				appendLastModifiedDescription(builder, trackItem, analysis);
			}
			description.setText(builder);
		}
		boolean showDirection = sortMode == NEAREST && analysis != null && analysis.latLonStart != null;
		AndroidUiHelper.updateVisibility(directionIcon, showDirection);
	}

	private void setupIcon(@NonNull GpxDataItem dataItem) {
		int color = dataItem.getColor();
		if (color == 0) {
			color = GpxAppearanceAdapter.getTrackColor(app);
		}
		imageView.setImageDrawable(getTrackIcon(app, dataItem.getWidth(), dataItem.isShowArrows(), color));
	}

	private void appendNameDescription(@NonNull SpannableStringBuilder builder, @NonNull TrackTab trackTab, @NonNull TrackItem trackItem, @NonNull GPXTrackAnalysis analysis) {
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
		if (analysis.isTimeSpecified()) {
			builder.append(" • ");
			appendDuration(builder, analysis);
		}
		appendPoints(builder, analysis);
		appendFolderName(builder, trackTab, trackItem);
	}

	private void appendCreationTimeDescription(@NonNull SpannableStringBuilder builder, @NonNull GPXTrackAnalysis analysis) {
		if (analysis.startTime > 0) {
			DateFormat format = app.getResourceManager().getDateFormat();
			builder.append(format.format(new Date(analysis.startTime)));
			setupTextSpan(builder);
			builder.append(" | ");
		}
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
		if (analysis.isTimeSpecified()) {
			builder.append(" • ");
			appendDuration(builder, analysis);
		}
		appendPoints(builder, analysis);
	}

	private void appendLastModifiedDescription(@NonNull SpannableStringBuilder builder, @NonNull TrackItem trackItem, @NonNull GPXTrackAnalysis analysis) {
		long lastModified = trackItem.getLastModified();
		if (lastModified > 0) {
			DateFormat format = app.getResourceManager().getDateFormat();
			builder.append(format.format(new Date(lastModified)));
			setupTextSpan(builder);
			builder.append(" | ");
		}
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
		if (analysis.isTimeSpecified()) {
			builder.append(" • ");
			appendDuration(builder, analysis);
		}
		appendPoints(builder, analysis);
	}

	private void appendDistanceDescription(@NonNull SpannableStringBuilder builder, @NonNull TrackTab trackTab, @NonNull TrackItem trackItem, @NonNull GPXTrackAnalysis analysis) {
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
		setupTextSpan(builder);

		if (analysis.isTimeSpecified()) {
			builder.append(" • ");
			appendDuration(builder, analysis);
		}
		appendPoints(builder, analysis);
		appendFolderName(builder, trackTab, trackItem);
	}

	private void appendDurationDescription(@NonNull SpannableStringBuilder builder, @NonNull TrackTab trackTab, @NonNull TrackItem trackItem, @NonNull GPXTrackAnalysis analysis) {
		if (analysis.isTimeSpecified()) {
			appendDuration(builder, analysis);
			setupTextSpan(builder);
			builder.append(" • ");
		}
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));

		appendPoints(builder, analysis);
		appendFolderName(builder, trackTab, trackItem);
	}

	private void appendNearestDescription(@NonNull TracksAdapter adapter,
	                                      @NonNull SpannableStringBuilder builder,
	                                      @NonNull TrackItem trackItem,
	                                      @NonNull GPXTrackAnalysis analysis) {
		if (analysis.latLonStart != null) {
			UpdateLocationInfo locationInfo = new UpdateLocationInfo(app, null, analysis.latLonStart);
			builder.append(UpdateLocationUtils.getFormattedDistance(app, locationInfo, locationViewCache));
			appendRegionName(adapter, analysis.latLonStart, builder, trackItem);
			builder.append(" | ");
			UpdateLocationUtils.updateDirectionDrawable(app, directionIcon, locationInfo, locationViewCache);
		}
		builder.append(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
		if (analysis.isTimeSpecified()) {
			builder.append(" • ");
			appendDuration(builder, analysis);
		}
		appendPoints(builder, analysis);
	}

	private void appendRegionName(@NonNull TracksAdapter adapter, @NonNull LatLon latLon,
	                              @NonNull SpannableStringBuilder builder, @NonNull TrackItem trackItem) {
		String regionName = trackItem.getRegionName();
		if (regionName != null) {
			builder.append(", ").append(regionName);
		} else {
			app.getMapViewTrackingUtilities().detectCurrentRegion(latLon, region -> {
				trackItem.setRegionName(region != null ? region.getLocaleName() : "");
				if (fragment.isAdded()) {
					adapter.notifyItemChanged(getAdapterPosition());
				}
				return true;
			});
		}
	}

	private void appendDuration(@NonNull SpannableStringBuilder builder, @NonNull GPXTrackAnalysis analysis) {
		if (analysis.isTimeSpecified()) {
			int duration = (int) (analysis.timeSpan / 1000.0f + 0.5);
			builder.append(Algorithms.formatDuration(duration, app.accessibilityEnabled()));
		}
	}

	private void appendPoints(@NonNull SpannableStringBuilder builder, @NonNull GPXTrackAnalysis analysis) {
		if (analysis.wptPoints > 0) {
			builder.append(" • ");
			builder.append(String.valueOf(analysis.wptPoints));
		}
	}

	private void appendFolderName(@NonNull SpannableStringBuilder builder, @NonNull TrackTab trackTab, @NonNull TrackItem trackItem) {
		String folderName = getFolderName(trackTab, trackItem);
		if (!Algorithms.isEmpty(folderName)) {
			builder.append(" | ");
			builder.append(Algorithms.capitalizeFirstLetter(folderName));
		}
	}

	@Nullable
	private String getFolderName(@NonNull TrackTab trackTab, @NonNull TrackItem trackItem) {
		String folderName = null;
		File file = trackItem.getFile();
		if (trackTab.type.shouldShowFolder() && file != null) {
			String[] path = file.getAbsolutePath().split(File.separator);
			folderName = path.length > 1 ? path[path.length - 2] : null;
		}
		return folderName;
	}

	private void setupTextSpan(@NonNull SpannableStringBuilder builder) {
		int length = builder.length();
		builder.setSpan(new ForegroundColorSpan(getSecondaryTextColor(app, nightMode)), 0, length, 0);
		builder.setSpan(new CustomTypefaceSpan(FontCache.getRobotoMedium(app)), 0, length, 0);
	}
}
