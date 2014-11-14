/*
 * Copyright (C) 2014 The MoKee OpenSource Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mokee.helper.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.mokee.utils.MoKeeUtils;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mokee.helper.R;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.FetchChangeLogTask;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.utils.Utils;

public class ItemPreference extends Preference implements OnClickListener, OnLongClickListener {
    private static final float DISABLED_ALPHA = 0.4f;
    public static final int STYLE_NEW = 1;
    public static final int STYLE_DOWNLOADING = 2;
    public static final int STYLE_DOWNLOADED = 3;
    public static final int STYLE_INSTALLED = 4;
    public static final int STYLE_EXTRAS_NEW = 5;
    public static final int STYLE_OLD = 0;// 旧版本

    public interface OnActionListener {
        void onStartDownload(ItemPreference pref);

        void onStopDownload(ItemPreference pref);

        void onStartUpdate(ItemPreference pref);

        void onDeleteUpdate(ItemPreference pref);
    }

    public interface OnReadyListener {
        void onReady(ItemPreference pref);
    }

    private OnActionListener mOnActionListener;
    private OnReadyListener mOnReadyListener;

    private ItemInfo mItemInfo = null;
    private int mStyle;

    private ImageView mUpdatesButton;
    private TextView mTitleText;
    private TextView mSummaryText;
    private TextView mFileSizeText;
    private View mUpdatesPref;
    private ProgressBar mProgressBar;

    private OnClickListener mButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnActionListener == null) {
                return;
            }

            switch (mStyle) {
                case STYLE_DOWNLOADED:
                    mOnActionListener.onStartUpdate(ItemPreference.this);
                    break;
                case STYLE_DOWNLOADING:
                    mOnActionListener.onStopDownload(ItemPreference.this);
                    break;
                case STYLE_NEW:
                case STYLE_OLD:
                case STYLE_EXTRAS_NEW:
                    mOnActionListener.onStartDownload(ItemPreference.this);
                    break;
            }
        }
    };

    public ItemPreference(Context context, ItemInfo ui, int style) {
        super(context, null, R.style.ItemPreferenceStyle);
        setLayoutResource(R.layout.preference_item);
        mStyle = style;
        mItemInfo = ui;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        // Store the views from the layout
        mUpdatesButton = (ImageView) view.findViewById(R.id.updates_button);
        mUpdatesButton.setOnClickListener(mButtonClickListener);

        mTitleText = (TextView) view.findViewById(android.R.id.title);
        mSummaryText = (TextView) view.findViewById(android.R.id.summary);
        mFileSizeText = (TextView) view.findViewById(R.id.file_size);
        mProgressBar = (ProgressBar) view.findViewById(R.id.download_progress_bar);

        mUpdatesPref = view.findViewById(R.id.updates_pref);
        mUpdatesPref.setOnClickListener(this);
        mUpdatesPref.setOnLongClickListener(this);

        // Update the views
        updatePreferenceViews();

        if (mOnReadyListener != null) {
            mOnReadyListener.onReady(this);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        switch (mStyle) {
            case STYLE_DOWNLOADED:
            case STYLE_INSTALLED:
                confirmDelete((Integer) v.getTag());
                break;

            case STYLE_DOWNLOADING:
            case STYLE_NEW:
            case STYLE_EXTRAS_NEW:
            case STYLE_OLD:
            default:
                // Do nothing for now
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        final Context context = getContext();
        new FetchChangeLogTask(context).execute(mItemInfo);
    }

    private void confirmDelete(int flag) {
        new AlertDialog.Builder(getContext()).setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(flag == Constants.INTENT_FLAG_GET_UPDATE ? R.string.confirm_delete_updates_dialog_message : R.string.confirm_delete_extras_dialog_message)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        if (mOnActionListener != null) {
                            mOnActionListener.onDeleteUpdate(ItemPreference.this);
                        }
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    @Override
    public String toString() {
        return "UpdatePreference [mItemInfo=" + mItemInfo + ", mStyle=" + mStyle + "]";
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            updatePreferenceViews();
        } else {
            disablePreferenceViews();
        }
    }

    public void setOnActionListener(OnActionListener listener) {
        mOnActionListener = listener;
    }

    public void setOnReadyListener(OnReadyListener listener) {
        mOnReadyListener = listener;
        if (mUpdatesPref != null && listener != null) {
            listener.onReady(this);
        }
    }

    public void setStyle(int style) {
        mStyle = style;
        if (mUpdatesPref != null) {
            showStyle();
        }
    }

    public int getStyle() {
        return mStyle;
    }

    public void setProgress(int max, int progress) {
        if (mStyle != STYLE_DOWNLOADING) {
            return;
        }
        mProgressBar.setMax(max);
        mProgressBar.setProgress(progress);
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

    public ImageView getUpdatesButton() {
        return mUpdatesButton;
    }

    public ItemInfo getItemInfo() {
        return mItemInfo;
    }

    private void disablePreferenceViews() {
        if (mUpdatesButton != null) {
            mUpdatesButton.setEnabled(false);
            mUpdatesButton.setAlpha(DISABLED_ALPHA);
        }
        if (mUpdatesPref != null) {
            mUpdatesPref.setEnabled(false);
            mUpdatesPref.setBackgroundColor(0);
        }
    }

    private void updatePreferenceViews() {
        if (mUpdatesPref != null) {
            mUpdatesPref.setEnabled(true);
            mUpdatesPref.setLongClickable(true);

            final boolean enabled = isEnabled();
            mUpdatesPref.setOnClickListener(enabled ? this : null);
            if (!enabled) {
                mUpdatesPref.setBackgroundColor(0);
            }

            // Set the title text
            if (mItemInfo.getFileName().startsWith("OTA")) {
                mTitleText.setText(mItemInfo.getFileName());
                mSummaryText.setText(R.string.new_update_summary);
                mUpdatesPref.setTag(Constants.INTENT_FLAG_GET_UPDATE);
            } else if (TextUtils.isEmpty(mItemInfo.getDescription())) {
                mTitleText.setText(mItemInfo.getFileName());
                mSummaryText.setText(Utils.isNewVersion(mItemInfo.getFileName()) ? R.string.new_update_summary : R.string.old_update_summary);
                mUpdatesPref.setTag(Constants.INTENT_FLAG_GET_UPDATE);
            } else {
                mTitleText.setText(mItemInfo.getDescription());
                mSummaryText.setText(mItemInfo.getFileName());
                mUpdatesPref.setTag(Constants.INTENT_FLAG_GET_EXTRAS);
            }
            mFileSizeText.setText(!TextUtils.isEmpty(mItemInfo.getFileSize()) ? MoKeeUtils.formatFileSize(Long.valueOf(mItemInfo.getFileSize())) : "");
            mTitleText.setVisibility(View.VISIBLE);
            // Show the proper style view
            showStyle();
        }
    }

    private void showStyle() {
        // Display the appropriate preference style
        switch (mStyle) {
            case STYLE_DOWNLOADED:
                // Show the install image and summary of 'Downloaded'
                mUpdatesButton.setImageResource(R.drawable.ic_tab_install);
                mUpdatesButton.setEnabled(true);
                mSummaryText.setText(R.string.downloaded_update_summary);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                break;
            case STYLE_DOWNLOADING:
                // Show the cancel button image and progress bar
                mUpdatesButton.setImageResource(R.drawable.ic_tab_cancel);
                mUpdatesButton.setEnabled(true);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                break;
            case STYLE_INSTALLED:
                // Show the installed button image and summary of 'Installed'
                mUpdatesButton.setImageResource(R.drawable.ic_tab_installed);
                mUpdatesButton.setEnabled(false);
                mSummaryText.setText(R.string.installed_update_summary);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                mFileSizeText.setVisibility(View.INVISIBLE);
                break;
            case STYLE_NEW:
            case STYLE_OLD:
            case STYLE_EXTRAS_NEW:
            default:
                // Show the download button image and summary of 'New'
                mUpdatesButton.setImageResource(R.drawable.ic_tab_download);
                mUpdatesButton.setEnabled(true);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                break;
        }
    }
}
