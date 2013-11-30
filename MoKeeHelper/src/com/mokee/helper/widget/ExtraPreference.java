/*
 * Copyright (C) 2013 The MoKee OpenSource Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mokee.helper.widget;

import com.mokee.helper.R;
import com.mokee.helper.misc.ExtraInfo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ExtraPreference extends Preference implements OnClickListener, OnLongClickListener
{
    private static final float DISABLED_ALPHA = 0.4f;
    public static final int STYLE_NEW = 1;
    public static final int STYLE_DOWNLOADING = 2;
    public static final int STYLE_DOWNLOADED = 3;
    public static final int STYLE_INSTALLED = 4;

    public interface OnActionListener
    {
        void onStartDownload(ExtraPreference pref);

        void onStopDownload(ExtraPreference pref);

        void onStartUpdate(ExtraPreference pref);

        void onDeleteUpdate(ExtraPreference pref);
    }

    public interface OnReadyListener
    {
        void onReady(ExtraPreference pref);
    }

    private OnActionListener mOnActionListener;
    private OnReadyListener mOnReadyListener;

    private ExtraInfo mExtraInfo = null;
    private int mStyle;

    private ImageView mUpdatesButton;
    private TextView mTitleText;
    private TextView mSummaryText;
    private View mExtrasPref;
    private ProgressBar mProgressBar;

    private OnClickListener mButtonClickListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            if (mOnActionListener == null)
            {
                return;
            }

            switch (mStyle)
            {
                case STYLE_DOWNLOADED:
                    mOnActionListener.onStartUpdate(ExtraPreference.this);
                    break;
                case STYLE_DOWNLOADING:
                    mOnActionListener.onStopDownload(ExtraPreference.this);
                    break;
                case STYLE_NEW:
                    mOnActionListener.onStartDownload(ExtraPreference.this);
                    break;
            }
        }
    };

    public ExtraPreference(Context context, ExtraInfo ui, int style)
    {
        super(context, null, R.style.UpdatesPreferenceStyle);
        setLayoutResource(R.layout.preference_updates);
        mStyle = style;
        mExtraInfo = ui;
    }

    @Override
    protected void onBindView(View view)
    {
        super.onBindView(view);

        // Store the views from the layout
        mUpdatesButton = (ImageView) view.findViewById(R.id.updates_button);
        mUpdatesButton.setOnClickListener(mButtonClickListener);

        mTitleText = (TextView) view.findViewById(android.R.id.title);
        mSummaryText = (TextView) view.findViewById(android.R.id.summary);
        mProgressBar = (ProgressBar) view.findViewById(R.id.download_progress_bar);

        mExtrasPref = view.findViewById(R.id.updates_pref);
        mExtrasPref.setOnClickListener(this);
        mExtrasPref.setOnLongClickListener(this);

        // Update the views
        updatePreferenceViews();

        if (mOnReadyListener != null)
        {
            mOnReadyListener.onReady(this);
        }
    }

    @Override
    public boolean onLongClick(View v)
    {
        switch (mStyle)
        {
            case STYLE_DOWNLOADED:
            case STYLE_INSTALLED:
                confirmDelete();
                break;

            case STYLE_DOWNLOADING:
            case STYLE_NEW:
            default:
                // Do nothing for now
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v)
    {
        final Context context = getContext();
        final File changeLog = mExtraInfo.getChangeLogFile(context);

        if (!changeLog.exists())
        {
            // Change log could not be fetched
            Toast.makeText(context, R.string.failed_to_load_changelog, Toast.LENGTH_SHORT).show();
        } else if (changeLog.length() == 0)
        {
            // Change log is empty
            Toast.makeText(context, R.string.no_changelog_alert, Toast.LENGTH_SHORT).show();
        } else
        {
            // Prepare the dialog box content
            final LayoutInflater inflater = LayoutInflater.from(context);
            final View view = inflater.inflate(R.layout.change_log_dialog, null);
            final View progressContainer = view.findViewById(R.id.progress);
            final NotifyingWebView changeLogView = (NotifyingWebView) view
                    .findViewById(R.id.changelog);

            changeLogView
                    .setOnInitialContentReadyListener(new NotifyingWebView.OnInitialContentReadyListener()
                    {
                        @Override
                        public void onInitialContentReady(WebView webView)
                        {
                            progressContainer.setVisibility(View.GONE);
                            changeLogView.setVisibility(View.VISIBLE);
                        }
                    });
            changeLogView.getSettings().setTextZoom(80);
            changeLogView.setBackgroundColor(context.getResources().getColor(
                    android.R.color.darker_gray));
            changeLogView.loadUrl(Uri.fromFile(changeLog).toString());

            // Prepare the dialog box
            new AlertDialog.Builder(context).setTitle(R.string.changelog_dialog_title)
                    .setView(view)
                    .setPositiveButton(R.string.dialog_close, null).show();
        }
    }

    private void confirmDelete()
    {
        new AlertDialog.Builder(getContext()).setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // We are OK to delete, trigger it
                        if (mOnActionListener != null)
                        {
                            mOnActionListener.onDeleteUpdate(ExtraPreference.this);
                        }
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override
    public String toString()
    {
        return "ExtraPreference [mExtraInfo=" + mExtraInfo + ", mStyle=" + mStyle + "]";
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        if (enabled)
        {
            updatePreferenceViews();
        } else
        {
            disablePreferenceViews();
        }
    }

    public void setOnActionListener(OnActionListener listener)
    {
        mOnActionListener = listener;
    }

    public void setOnReadyListener(OnReadyListener listener)
    {
        mOnReadyListener = listener;
        if (mExtrasPref != null && listener != null)
        {
            listener.onReady(this);
        }
    }

    public void setStyle(int style)
    {
        mStyle = style;
        if (mExtrasPref != null)
        {
            showStyle();
        }
    }

    public int getStyle()
    {
        return mStyle;
    }

    public void setProgress(int max, int progress)
    {
        if (mStyle != STYLE_DOWNLOADING)
        {
            return;
        }
        mProgressBar.setMax(max);
        mProgressBar.setProgress(progress);
    }

    public ProgressBar getProgressBar()
    {
        return mProgressBar;
    }

    public ImageView getUpdatesButton()
    {
        return mUpdatesButton;
    }

    public ExtraInfo getExtraInfo()
    {
        return mExtraInfo;
    }

    private void disablePreferenceViews()
    {
        if (mUpdatesButton != null)
        {
            mUpdatesButton.setEnabled(false);
            mUpdatesButton.setAlpha(DISABLED_ALPHA);
        }
        if (mExtrasPref != null)
        {
            mExtrasPref.setEnabled(false);
            mExtrasPref.setBackgroundColor(0);
        }
    }

    private void updatePreferenceViews()
    {
        if (mExtrasPref != null)
        {
            mExtrasPref.setEnabled(true);
            mExtrasPref.setLongClickable(true);

            final boolean enabled = isEnabled();
            mExtrasPref.setOnClickListener(enabled ? this : null);
            if (!enabled)
            {
                mExtrasPref.setBackgroundColor(0);
            }

            // Set the title text
            mTitleText.setText(mExtraInfo.getmUiName());
            mTitleText.setVisibility(View.VISIBLE);

            // Show the proper style view
            showStyle();
        }
    }

    private void showStyle()
    {
        // Display the appropriate preference style
        switch (mStyle)
        {
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
                mProgressBar.setVisibility(View.VISIBLE);
                mSummaryText.setVisibility(View.GONE);
                break;

            case STYLE_INSTALLED:
                // Show the installed button image and summary of 'Installed'
                mUpdatesButton.setImageResource(R.drawable.ic_tab_installed);
                mUpdatesButton.setEnabled(false);
                mSummaryText.setText(R.string.installed_update_summary);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                break;

            case STYLE_NEW:
            default:
                // Show the download button image and summary of 'New'
                mUpdatesButton.setImageResource(R.drawable.ic_tab_download);
                mUpdatesButton.setEnabled(true);
                mSummaryText.setText(R.string.new_update_summary);
                mSummaryText.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                break;
        }
    }
}
