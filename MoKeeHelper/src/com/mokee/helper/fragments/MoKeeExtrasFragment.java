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

package com.mokee.helper.fragments;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import mokee.support.widget.snackbar.Snackbar;
import mokee.support.widget.snackbar.SnackbarManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.mokee.utils.MoKeeUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;

import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.db.DownLoadDao;
import com.mokee.helper.db.ThreadDownLoadDao;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.DownLoadInfo;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.misc.ThreadDownLoadInfo;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.service.DownLoadService;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.DownLoader;
import com.mokee.helper.utils.UpdateFilter;
import com.mokee.helper.utils.Utils;
import com.mokee.helper.widget.EmptyListPreferenceStyle;
import com.mokee.helper.widget.ItemPreference;

public class MoKeeExtrasFragment extends PreferenceFragment implements
        ItemPreference.OnReadyListener, ItemPreference.OnActionListener {

    private static String TAG = "MoKeeExtrasFragment";
    private static final String KEY_MOKEE_LAST_CHECK = "mokee_last_check";

    private boolean mDownloading = false;
    private long mDownloadId;
    private String mFileName;

    private Activity mContext;

    private boolean mStartUpdateVisible = false;

    private static final int MENU_REFRESH = 0;
    private static final int MENU_DELETE_ALL = 1;
    private static final int MENU_DONATE = 2;

    private SharedPreferences mPrefs;
    private PreferenceCategory mExtrasList;
    private ItemPreference mDownloadingPreference;
    private File mExtrasFolder;
    private ProgressDialog mProgressDialog;
    private Handler mUpdateHandler = new Handler();

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int flag = intent.getIntExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_EXTRAS);
            if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
                if (DownloadReceiver.ACTION_DOWNLOAD_STARTED.equals(action)) {
                    mDownloadId = intent.getLongExtra(DownLoadService.DOWNLOAD_ID, -1);
                    mUpdateHandler.post(mUpdateProgress);
                } else if (UpdateCheckService.ACTION_CHECK_FINISHED.equals(action)) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        int count = intent.getIntExtra(UpdateCheckService.EXTRA_NEW_UPDATE_COUNT, -1);
                        if (count == 0) {
                            SnackbarManager.show(Snackbar.with(mContext).text(R.string.no_extras_found).colorResource(R.color.snackbar_background));
                        } else if (count < 0) {
                            SnackbarManager.show(Snackbar.with(mContext).text(R.string.update_check_failed)
                                    .duration(Snackbar.SnackbarDuration.LENGTH_LONG).colorResource(R.color.snackbar_background));
                        }
                    }
                    extrasLayout();
                } else if (MoKeeCenter.BR_ONNewIntent.equals(action)) {
                    // 唤醒
                    if (intent.getBooleanExtra(UpdateCheckService.EXTRA_UPDATE_LIST_UPDATED, false)) {
                        extrasLayout();
                    }
                    checkForDownloadCompleted(intent);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();

        // Load the layouts
        addPreferencesFromResource(R.xml.mokee_extras);

        // Load the stored preference data
        mPrefs = mContext.getSharedPreferences(Constants.DOWNLOADER_PREF, 0);

        mExtrasList = (PreferenceCategory) findPreference(Constants.EXPANG_LIST_PREF);// 扩展列表

        updateLastCheckPreference();

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_DONATE, 0, R.string.menu_donate).setShowAsActionFlags(
                MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, MENU_REFRESH, 0, R.string.menu_refresh)
                .setIcon(R.drawable.ic_menu_refresh)
                .setShowAsActionFlags(
                        MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all).setShowAsActionFlags(
                MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DONATE:
                MoKeeCenter.donateOrRemoveAdsButton(getActivity(), true);
                return true;
            case MENU_REFRESH:
                checkForUpdates(Constants.INTENT_FLAG_GET_EXTRAS);
                return true;
            case MENU_DELETE_ALL:
                confirmDeleteAll();
                return true;
        }
        return true;
    }

    // 更新进度条
    private Runnable mUpdateProgress = new Runnable() {
        public void run() {
            if (!mDownloading || mDownloadingPreference == null || mDownloadId < 0) {
                return;
            }

            ProgressBar progressBar = mDownloadingPreference.getProgressBar();
            if (progressBar == null) {
                return;
            }
            DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfo(String.valueOf(mDownloadId));
            int status;

            if (dli == null) {
                // DownloadReceiver has likely already removed the download
                // from the DB due to failure or MD5 mismatch
                status = DownLoader.STATUS_PENDING;
            } else {
                status = dli.getState();
            }
            switch (status) {
                case DownLoader.STATUS_PENDING:
                    progressBar.setIndeterminate(true);
                    break;
                case DownLoader.STATUS_DOWNLOADING:
                    List<ThreadDownLoadInfo> threadList = ThreadDownLoadDao.getInstance().getThreadInfoList(dli.getUrl());
                    int totalBytes = -1;
                    int downloadedBytes = 0;
                    for (ThreadDownLoadInfo info : threadList) {
                        downloadedBytes += info.getDownSize();
                        totalBytes += info.getEndPos() - info.getStartPos() + 1;
                    }

                    if (totalBytes < 0) {
                        progressBar.setIndeterminate(true);
                    } else {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(totalBytes);
                        progressBar.setProgress(downloadedBytes);
                    }
                    break;
                case DownLoader.STATUS_ERROR:
                case DownLoader.STATUS_PAUSED:
                    mDownloadingPreference.setStyle(ItemPreference.STYLE_EXTRAS_NEW);
                    resetDownloadState();
                    break;
            }
            if (status != DownLoader.STATUS_ERROR) {
                mUpdateHandler.postDelayed(this, 1500);
            }
        }
    };

    private void resetDownloadState() {
        mDownloadId = -1;
        mFileName = null;
        mDownloading = false;
        mDownloadingPreference = null;
    }

    private void extrasLayout() {
        updateLastCheckPreference();
        // Read existing Updates
        LinkedList<String> existingFiles = new LinkedList<String>();
        mExtrasFolder = Utils.makeExtraFolder();
        File[] files = mExtrasFolder.listFiles(new UpdateFilter(".zip|.apk"));
        if (mExtrasFolder.exists() && mExtrasFolder.isDirectory() && files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    existingFiles.add(file.getName());
                }
            }
        }
        // Clear the notification if one exists
        Utils.cancelNotification(mContext);

        // Build list of updates
        final LinkedList<ItemInfo> availableUpdates = State.loadMKState(mContext, State.EXTRAS_FILENAME);
        // Update the preference list
        refreshExtrasPreferences(availableUpdates);
    }

    private void refreshExtrasPreferences(LinkedList<ItemInfo> updates) {
        if (mExtrasList == null) {
            return;
        }
        // Clear the list
        mExtrasList.removeAll();
        // Add the updates
        for (ItemInfo ui : updates) {
            // Determine the preference style and create the preference
            boolean isDownloading = ui.getFileName().equals(mFileName);
            boolean isLocalFile = Utils.isLocaUpdateFile(ui.getFileName(), false);
            boolean isZip = ui.getFileName().endsWith(".zip");
            boolean isApk = ui.getFileName().endsWith(".apk");
            boolean isInstall = false;
            boolean isExtrasUpdate = false;
            boolean isGMS = ui.getFileName().startsWith("gapps_");
            if (isZip || isApk) {
                isInstall = MoKeeUtils.isApkInstalled(ui.getCheckflag(), getActivity());
                if (isGMS && isInstall) {
                    isInstall = MoKeeUtils.isSystemApp(ui.getCheckflag(), getActivity());
                    if (isInstall) {
                        isExtrasUpdate = Utils.checkGmsVersion(ui.getFileName());
                    }
                }
            }
            int style = 3;
            if (isDownloading) {
                // In progress download
                style = ItemPreference.STYLE_DOWNLOADING;
            } else if (isExtrasUpdate && !isLocalFile) {
                style = ItemPreference.STYLE_NEW;
            } else if (!isInstall && !isLocalFile) {
                style = ItemPreference.STYLE_EXTRAS_NEW;
            } else if (isExtrasUpdate && isLocalFile || !isInstall && isLocalFile) {
                style = ItemPreference.STYLE_DOWNLOADED;
            } else if (isInstall) {
                // This is the currently installed version
                style = ItemPreference.STYLE_INSTALLED;
            }

            ItemPreference up = new ItemPreference(mContext, ui, style);
            up.setOnActionListener(this);
            up.setKey(ui.getFileName());

            // If we have an in progress download, link the preference
            if (isDownloading) {
                mDownloadingPreference = up;
                up.setOnReadyListener(this);
                mDownloading = true;
            }
            // Add to the list
            mExtrasList.addPreference(up);
        }
        // If no updates are in the list, show the default message
        if (mExtrasList.getPreferenceCount() == 0) {
            EmptyListPreferenceStyle pref = new EmptyListPreferenceStyle(mContext, null, R.style.EmptyListPreferenceStyle);
            pref.setSummary(R.string.no_available_extras_intro);
            pref.setEnabled(false);
            mExtrasList.addPreference(pref);
        }
    }

    /**
     * 检测更新
     */
    private void checkForUpdates(final int flag) {
        if (mProgressDialog != null) {
            return;
        }
        State.saveMKState(mContext, new LinkedList<ItemInfo>(),
                State.EXTRAS_FILENAME);
        refreshExtrasPreferences(new LinkedList<ItemInfo>());
        // If there is no internet connection, display a message and return.
        if (!MoKeeUtils.isOnline(mContext)) {
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.data_connection_required).colorResource(R.color.snackbar_background));
            return;
        }
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.mokee_extras_title);
        mProgressDialog.setMessage(getString(R.string.checking_for_extras));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Intent cancelIntent = new Intent(mContext, UpdateCheckService.class);
                cancelIntent.setAction(UpdateCheckService.ACTION_CANCEL_CHECK);
                cancelIntent.putExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_EXTRAS);
                mContext.startServiceAsUser(cancelIntent, UserHandle.CURRENT);
                mProgressDialog = null;
            }
        });

        Intent checkIntent = new Intent(mContext, UpdateCheckService.class);
        checkIntent.setAction(UpdateCheckService.ACTION_CHECK);
        checkIntent.putExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_EXTRAS);
        mContext.startServiceAsUser(checkIntent, UserHandle.CURRENT);
        mProgressDialog.show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(mContext).setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_extras_all_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        onPauseDownload(mPrefs);
                        deleteOldUpdates();
                        extrasLayout();
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    private boolean deleteOldUpdates() {
        boolean success;
        // mExtrasFolder: Foldername with fullpath of SDCARD
        if (mExtrasFolder.exists() && mExtrasFolder.isDirectory()) {
            Utils.deleteDir(mExtrasFolder);
            mExtrasFolder.mkdir();
            success = true;
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.delete_extras_success_message).colorResource(R.color.snackbar_background));
        } else if (!mExtrasFolder.exists()) {
            success = false;
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.delete_extras_noFolder_message).colorResource(R.color.snackbar_background));
        } else {
            success = false;
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.delete_extras_failure_message).colorResource(R.color.snackbar_background));
        }
        return success;
    }

    private void checkForDownloadCompleted(Intent intent) {
        if (intent == null) {
            return;
        }

        long downloadId = intent.getLongExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, -1);
        if (downloadId < 0) {
            return;
        }

        String fullPathName = intent
                .getStringExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH);
        if (fullPathName == null) {
            return;
        }

        String fileName = new File(fullPathName).getName();

        // Find the matching preference so we can retrieve the ItemInfo
        ItemPreference pref = (ItemPreference) mExtrasList.findPreference(fileName);
        if (pref != null) {
            pref.setStyle(ItemPreference.STYLE_DOWNLOADED);// download over
            // Change
            onStartUpdate(pref);
        }
        resetDownloadState();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Determine if there are any in-progress downloads
        mDownloadId = mPrefs.getLong(DownLoadService.DOWNLOAD_EXTRAS_ID, -1);
        if (mDownloadId >= 0) {
            DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfo(String.valueOf(mDownloadId));
            if (dli != null) {
                int status = dli.getState();
                if (status == DownLoader.STATUS_PENDING
                        || status == DownLoader.STATUS_DOWNLOADING
                        || status == DownLoader.STATUS_PAUSED) {
                    String localFileName = dli.getLocalFile();
                    if (!TextUtils.isEmpty(localFileName)) {
                        mFileName = localFileName.substring(localFileName.lastIndexOf("/") + 1,
                                localFileName.lastIndexOf("."));
                    }
                }
            }
        }
        if (mDownloadId < 0 || mFileName == null) {
            resetDownloadState();
        }

        extrasLayout();
        IntentFilter filter = new IntentFilter(UpdateCheckService.ACTION_CHECK_FINISHED);
        filter.addAction(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
        filter.addAction(MoKeeCenter.BR_ONNewIntent);// 唤醒
        mContext.registerReceiver(mReceiver, filter);

        checkForDownloadCompleted(mContext.getIntent());
        mContext.setIntent(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        mUpdateHandler.removeCallbacks(mUpdateProgress);
        mContext.unregisterReceiver(mReceiver);
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
    }

    @Override
    public void onStartDownload(ItemPreference pref) {
        // If there is no internet connection, display a message and return.
        if (!MoKeeUtils.isOnline(mContext)) {
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.data_connection_required).colorResource(R.color.snackbar_background));
            return;
        }
        if (mDownloading) {
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.download_already_running).colorResource(R.color.snackbar_background));
            return;
        }

        // We have a match, get ready to trigger the download
        mDownloadingPreference = pref;

        ItemInfo ui = mDownloadingPreference.getItemInfo();
        if (ui == null) {
            return;
        }

        mDownloadingPreference.setStyle(ItemPreference.STYLE_DOWNLOADING);
        mFileName = ui.getFileName();
        mDownloading = true;

        // Start the download
        Intent intent = new Intent(mContext, DownloadReceiver.class);
        intent.setAction(DownloadReceiver.ACTION_DOWNLOAD_START);
        intent.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO, (Parcelable) ui);
        intent.putExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_EXTRAS);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    @Override
    public void onStopDownload(final ItemPreference pref) {
        if (!mDownloading || mFileName == null || mDownloadId < 0) {
            pref.setStyle(ItemPreference.STYLE_EXTRAS_NEW);
            resetDownloadState();
            return;
        }
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.confirm_download_cancelation_dialog_title)
                .setMessage(R.string.confirm_download_cancelation_dialog_message)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Set the preference back to new style
                        pref.setStyle(ItemPreference.STYLE_EXTRAS_NEW);
                        onPauseDownload(mPrefs);
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    public void onPauseDownload(SharedPreferences prefs) {
        // We are OK to stop download, trigger it
        if (mDownloading)
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.download_cancelled).colorResource(R.color.snackbar_background));
        resetDownloadState();
        mUpdateHandler.removeCallbacks(mUpdateProgress);
        Intent intent = new Intent(mContext, DownLoadService.class);
        intent.setAction(DownLoadService.ACTION_DOWNLOAD);
        intent.putExtra(DownLoadService.DOWNLOAD_TYPE, DownLoadService.PAUSE);
        intent.putExtra(DownLoadService.DOWNLOAD_URL, mPrefs.getString(DownLoadService.DOWNLOAD_EXTRAS_URL, ""));

        mContext.startServiceAsUser(intent, UserHandle.CURRENT);
    }

    @Override
    public void onStartUpdate(ItemPreference pref) {
        final ItemInfo itemInfo = pref.getItemInfo();

        // Prevent the dialog from being triggered more than once
        if (mStartUpdateVisible) {
            return;
        }
        mStartUpdateVisible = true;
        // Get the message body right
        String dialogBody = getString(R.string.apply_extras_dialog_text, itemInfo.getFileName());
        // Display the dialog
        new AlertDialog.Builder(mContext).setTitle(R.string.apply_extras_dialog_title)
                .setMessage(dialogBody)
                .setPositiveButton(R.string.dialog_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TextUtils.isEmpty(itemInfo.getDescription())
                                || itemInfo.getFileName().endsWith(".zip")) {
                            try {
                                Utils.triggerUpdate(mContext, itemInfo.getFileName(), false);
                            } catch (IOException e) {
                                Log.e(TAG, "Unable to reboot into recovery mode", e);
                                SnackbarManager.show(Snackbar.with(mContext).text(R.string.apply_unable_to_reboot_toast).colorResource(R.color.snackbar_background));
                            }
                        } else if (itemInfo.getFileName().endsWith(".apk")) {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(
                                    Uri.parse("file://" + Utils.makeExtraFolder().getAbsolutePath()
                                            + "/" + itemInfo.getFileName()),
                                    "application/vnd.android.package-archive");
                            startActivity(i);
                        }
                    }
                }).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mStartUpdateVisible = false;
                    }
                }).setCancelable(false).show();
    }

    @Override
    public void onDeleteUpdate(ItemPreference pref) {
        final String fileName = pref.getKey();

        if (mExtrasFolder.exists() && mExtrasFolder.isDirectory()) {
            File zipFileToDelete = new File(mExtrasFolder, fileName);

            if (zipFileToDelete.exists()) {
                zipFileToDelete.delete();
            } else {
                Log.d(TAG, "Update to delete not found");
                return;
            }

            String message = getString(R.string.delete_single_update_success_message, fileName);
            SnackbarManager.show(Snackbar.with(mContext).text(message).colorResource(R.color.snackbar_background));
        } else if (!mExtrasFolder.exists()) {
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.delete_extras_noFolder_message).colorResource(R.color.snackbar_background));
        } else {
            SnackbarManager.show(Snackbar.with(mContext).text(R.string.delete_extras_failure_message).colorResource(R.color.snackbar_background));
        }

        // Update the list
        extrasLayout();
    }

    @Override
    public void onReady(ItemPreference pref) {
        pref.setOnReadyListener(null);
        mUpdateHandler.post(mUpdateProgress);
    }

    public void updateLastCheckPreference() {
        long lastCheckTime = mPrefs.getLong(Constants.LAST_EXTRAS_CHECK_PREF, 0);
        if (lastCheckTime == 0) {
            Utils.setSummaryFromString(this, KEY_MOKEE_LAST_CHECK, getString(R.string.mokee_last_check_never));
        } else {
            Date lastCheck = new Date(lastCheckTime);
            String date = DateFormat.getLongDateFormat(mContext).format(lastCheck);
            String time = DateFormat.getTimeFormat(mContext).format(lastCheck);
            Utils.setSummaryFromString(this, KEY_MOKEE_LAST_CHECK, date + " " + time);
        }
    }
}
