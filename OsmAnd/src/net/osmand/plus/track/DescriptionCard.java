package net.osmand.plus.track;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.PicassoUtils;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.plus.wikipedia.WikiArticleHelper;
import net.osmand.util.Algorithms;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;

import static net.osmand.plus.myplaces.TrackActivityFragmentAdapter.getMetadataImageLink;

public class DescriptionCard extends MapBaseCard {

	private final Fragment targetFragment;
	private final GPXFile gpxFile;

	public DescriptionCard(@NonNull MapActivity mapActivity,
	                       @NonNull Fragment targetFragment,
	                       @NonNull GPXFile gpxFile) {
		super(mapActivity);
		this.gpxFile = gpxFile;
		this.targetFragment = targetFragment;
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.gpx_description_preview_card;
	}

	@Override
	protected void updateContent() {
		if (gpxFile.metadata == null) {
			gpxFile.metadata = new GPXUtilities.Metadata();
		}

		final String title = gpxFile.metadata.getArticleTitle();
		final String imageUrl = getMetadataImageLink(gpxFile.metadata);
		final String descriptionHtml = gpxFile.metadata.getDescription();

		setupImage(imageUrl);

		if (Algorithms.isBlank(descriptionHtml)) {
			showAddBtn();
		} else {
			showDescription(title, imageUrl, descriptionHtml);
		}
	}

	private void showAddBtn() {
		LinearLayout descriptionContainer = view.findViewById(R.id.description_container);
		View addBtn = view.findViewById(R.id.btn_add);

		setupButton(addBtn);
		addBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GpxEditDescriptionDialogFragment.showInstance(getMapActivity(), "", null);
			}
		});
		AndroidUiHelper.updateVisibility(descriptionContainer, false);
		AndroidUiHelper.updateVisibility(addBtn, true);
	}

	private void showDescription(final String title, final String imageUrl, final String descriptionHtml) {
		LinearLayout descriptionContainer = view.findViewById(R.id.description_container);
		FrameLayout addBtn = view.findViewById(R.id.btn_add);

		AndroidUiHelper.updateVisibility(descriptionContainer, true);
		AndroidUiHelper.updateVisibility(addBtn, false);

		TextViewEx tvDescription = view.findViewById(R.id.description);
		tvDescription.setText(getFirstParagraph(descriptionHtml));

		View readBtn = view.findViewById(R.id.btn_read_full);
		setupButton(readBtn);
		readBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GpxReadDescriptionDialogFragment.showInstance(mapActivity, title, imageUrl, descriptionHtml, targetFragment);
			}
		});

		View editBtn = view.findViewById(R.id.btn_edit);
		setupButton(editBtn);
		editBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GpxEditDescriptionDialogFragment.showInstance(mapActivity, descriptionHtml, null);
			}
		});
	}

	private String getFirstParagraph(String descriptionHtml) {
		if (descriptionHtml != null) {
			String firstParagraph = WikiArticleHelper.getPartialContent(descriptionHtml);
			if (!Algorithms.isEmpty(firstParagraph)) {
				return firstParagraph;
			}
		}
		return descriptionHtml;
	}

	private void setupButton(View button) {
		Context ctx = button.getContext();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			AndroidUtils.setBackground(ctx, button, nightMode, R.drawable.ripple_light, R.drawable.ripple_dark);
		} else {
			AndroidUtils.setBackground(ctx, button, nightMode, R.drawable.btn_unstroked_light, R.drawable.btn_unstroked_dark);
		}
	}

	private void setupImage(final String imageUrl) {
		if (imageUrl == null) {
			return;
		}
		final PicassoUtils picasso = PicassoUtils.getPicasso(app);
		RequestCreator rc = Picasso.get().load(imageUrl);
		final AppCompatImageView image = view.findViewById(R.id.main_image);
		rc.into(image, new Callback() {
			@Override
			public void onSuccess() {
				picasso.setResultLoaded(imageUrl, true);
				AndroidUiHelper.updateVisibility(image, true);
			}

			@Override
			public void onError(Exception e) {
				picasso.setResultLoaded(imageUrl, false);
			}
		});
	}
}