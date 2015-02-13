package com.althyk.watchface;


import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.althyk.watchfacecommon.DataMapUtil;
import com.althyk.watchfacecommon.DataSyncUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;

public class WearableConfigActivity  extends Activity implements
        WearableListView.ClickListener, WearableListView.OnScrollListener {
    private static final String TAG = "WearableConfig";

    private GoogleApiClient mGoogleApiClient;
    private TextView mHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_config);

        mHeader = (TextView) findViewById(R.id.header);
        WearableListView listView = (WearableListView) findViewById(R.id.weather_picker);
        BoxInsetLayout content = (BoxInsetLayout) findViewById(R.id.content);
        // BoxInsetLayout adds padding by default on round devices. Add some on square devices.
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (!insets.isRound()) {
                    v.setPaddingRelative(
                            (int) getResources().getDimensionPixelSize(R.dimen.content_padding_start),
                            v.getPaddingTop(),
                            v.getPaddingEnd(),
                            v.getPaddingBottom());
                }
                return v.onApplyWindowInsets(insets);
            }
        });

        listView.setHasFixedSize(true);
        listView.setClickListener(this);
        listView.addOnScrollListener(this);

        String[] areas = getResources().getStringArray(R.array.area_array);
        listView.setAdapter(new AreaListAdapter(areas));

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnected: " + connectionHint);
                        }
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnectionSuspended: " + cause);
                        }
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnectionFailed: " + result);
                        }
                    }
                })
                .addApi(Wearable.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override // WearableListView.ClickListener
    public void onClick(WearableListView.ViewHolder viewHolder) {
        AreaItemViewHolder areaItemViewHolder = (AreaItemViewHolder) viewHolder;
        updateConfigDataItem(areaItemViewHolder.mAreaItem.getArea());
        finish();
    }

    @Override // WearableListView.ClickListener
    public void onTopEmptyRegionClick() {}

    @Override // WearableListView.OnScrollListener
    public void onScroll(int scroll) {}

    @Override // WearableListView.OnScrollListener
    public void onAbsoluteScrollChange(int scroll) {
        float newTranslation = Math.min(-scroll, 0);
        mHeader.setTranslationY(newTranslation);
    }

    @Override // WearableListView.OnScrollListener
    public void onScrollStateChanged(int scrollState) {}

    @Override // WearableListView.OnScrollListener
    public void onCentralPositionChanged(int centralPosition) {}

    private void updateConfigDataItem(final int areaId) {
        DataMap configKeysToOverwrite = new DataMap();
        configKeysToOverwrite.putInt(DataSyncUtil.KEY_WEATHER_AREA, areaId);
        DataMapUtil.overwriteKeysInDataMap(mGoogleApiClient,
                DataSyncUtil.PATH_DATA_AREA, configKeysToOverwrite);
    }

    private class AreaListAdapter extends WearableListView.Adapter {
        private final String[] mAreas;

        public AreaListAdapter(String[] areas) {
            mAreas = areas;
        }

        @Override
        public AreaItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AreaItemViewHolder(new AreaItem(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
            AreaItemViewHolder areaItemViewHolder = (AreaItemViewHolder) holder;
            String areaName = mAreas[position];
            areaItemViewHolder.mAreaItem.setArea(areaName, position);

            RecyclerView.LayoutParams layoutParams =
                    new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            int areaPickerItemMargin = (int) getResources()
                    .getDimension(R.dimen.config_area_picker_item_margin);
            // Add margins to first and last item to make it possible for user to tap on them.
            if (position == 0) {
                layoutParams.setMargins(0, areaPickerItemMargin, 0, 0);
            } else if (position == mAreas.length - 1) {
                layoutParams.setMargins(0, 0, 0, areaPickerItemMargin);
            } else {
                layoutParams.setMargins(0, 0, 0, 0);
            }
            areaItemViewHolder.itemView.setLayoutParams(layoutParams);
        }

        @Override
        public int getItemCount() {
            return mAreas.length;
        }
    }

    /** The layout of a color item including image and label. */
    private static class AreaItem extends LinearLayout implements
            WearableListView.OnCenterProximityListener {
        /** The duration of the expand/shrink animation. */
        private static final int ANIMATION_DURATION_MS = 150;

        private static final float SHRINK_ICON_SCALE = .7f;
        private static final float EXPAND_ICON_SCALE = 1f;
        private static final float SHRINK_LABEL_ALPHA = .5f;
        private static final float EXPAND_LABEL_ALPHA = 1f;

        private int mAreaId;
        private final TextView mLabel;
        private final CircledImageView mIcon;

        private final ObjectAnimator mExpandIconXAnimator;
        private final ObjectAnimator mExpandIconYAnimator;
        private final ObjectAnimator mExpandLabelAnimator;
        private final AnimatorSet mExpandAnimator;

        private final ObjectAnimator mShrinkIconXAnimator;
        private final ObjectAnimator mShrinkIconYAnimator;
        private final ObjectAnimator mShrinkLabelAnimator;
        private final AnimatorSet mShrinkAnimator;

        public AreaItem(Context context) {
            super(context);
            View.inflate(context, R.layout.area_picker_item, this);

            mAreaId = -1;
            mLabel = (TextView) findViewById(R.id.label);
            mIcon = (CircledImageView) findViewById(R.id.icon);

            mShrinkIconXAnimator = ObjectAnimator.ofFloat(mIcon, "scaleX",
                    EXPAND_ICON_SCALE, SHRINK_ICON_SCALE);
            mShrinkIconYAnimator = ObjectAnimator.ofFloat(mIcon, "scaleY",
                    EXPAND_ICON_SCALE, SHRINK_ICON_SCALE);
            mShrinkLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    EXPAND_LABEL_ALPHA, SHRINK_LABEL_ALPHA);
            mShrinkAnimator = new AnimatorSet().setDuration(ANIMATION_DURATION_MS);
            mShrinkAnimator.playTogether(mShrinkIconXAnimator, mShrinkIconYAnimator,
                    mShrinkLabelAnimator);

            mExpandIconXAnimator = ObjectAnimator.ofFloat(mIcon, "scaleX",
                    SHRINK_ICON_SCALE, EXPAND_ICON_SCALE);
            mExpandIconYAnimator = ObjectAnimator.ofFloat(mIcon, "scaleY",
                    SHRINK_ICON_SCALE, EXPAND_ICON_SCALE);
            mExpandLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    SHRINK_LABEL_ALPHA, EXPAND_LABEL_ALPHA);
            mExpandAnimator = new AnimatorSet().setDuration(ANIMATION_DURATION_MS);
            mExpandAnimator.playTogether(mExpandIconXAnimator, mExpandIconYAnimator,
                    mExpandLabelAnimator);
        }

        @Override
        public void onCenterPosition(boolean animate) {
            if (animate) {
                mShrinkAnimator.cancel();
                if (!mExpandAnimator.isRunning()) {
                    mExpandIconXAnimator.setFloatValues(mIcon.getScaleX(), EXPAND_ICON_SCALE);
                    mExpandIconYAnimator.setFloatValues(mIcon.getScaleY(), EXPAND_ICON_SCALE);
                    mExpandLabelAnimator.setFloatValues(mLabel.getAlpha(), EXPAND_LABEL_ALPHA);
                    mExpandAnimator.start();
                }
            } else {
                mExpandAnimator.cancel();
                mIcon.setScaleX(EXPAND_ICON_SCALE);
                mIcon.setScaleY(EXPAND_ICON_SCALE);
                mLabel.setAlpha(EXPAND_LABEL_ALPHA);
            }
        }

        @Override
        public void onNonCenterPosition(boolean animate) {
            if (animate) {
                mExpandAnimator.cancel();
                if (!mShrinkAnimator.isRunning()) {
                    mShrinkIconXAnimator.setFloatValues(mIcon.getScaleX(), SHRINK_ICON_SCALE);
                    mShrinkIconYAnimator.setFloatValues(mIcon.getScaleY(), SHRINK_ICON_SCALE);
                    mShrinkLabelAnimator.setFloatValues(mLabel.getAlpha(), SHRINK_LABEL_ALPHA);
                    mShrinkAnimator.start();
                }
            } else {
                mShrinkAnimator.cancel();
                mIcon.setScaleX(SHRINK_ICON_SCALE);
                mIcon.setScaleY(SHRINK_ICON_SCALE);
                mLabel.setAlpha(SHRINK_LABEL_ALPHA);
            }
        }

        private void setArea(String label, int areaId) {
            mAreaId = areaId;
            mLabel.setText(label);
            if (areaId == 0) {
                mIcon.setImageResource(R.drawable.no_weather);
            } else if (1 <= areaId && areaId <= 9) {
                mIcon.setImageResource(R.drawable.limsa_lominsa_crest);
            } else if (10 <= areaId && areaId <= 15) {
                mIcon.setImageResource(R.drawable.gridania_crest);
            } else if (16 <= areaId && areaId <= 22) {
                mIcon.setImageResource(R.drawable.uldah_crest);
            } else if (areaId == 23) {
                mIcon.setImageResource(R.drawable.ishgard_crest);
            } else if (areaId == 24) {
                mIcon.setImageResource(R.drawable.other_crest);
            }
        }

        private int getArea() {
            return mAreaId;
        }
    }

    private static class AreaItemViewHolder extends WearableListView.ViewHolder {
        private final AreaItem mAreaItem;

        public AreaItemViewHolder(AreaItem areaItem) {
            super(areaItem);
            mAreaItem = areaItem;
        }
    }
}
