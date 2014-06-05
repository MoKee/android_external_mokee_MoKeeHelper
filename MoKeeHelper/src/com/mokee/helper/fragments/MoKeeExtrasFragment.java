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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.mokee.util.MoKeeUtils;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.db.DownLoadDao;
import com.mokee.helper.db.ThreadDownLoadDao;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.DownLoadInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.misc.ThreadDownLoadInfo;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.service.DownLoadService;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.DownLoader;
import com.mokee.helper.utils.UpdateFilter;
import com.mokee.helper.utils.Utils;
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

    public static final String EXTRA_EXTRAS_LIST_UPDATED = "extras_list_updated";
    private SharedPreferences mPrefs;
    private PreferenceCategory mMokeeExtrasList;
    private ItemPreference mDownloadingPreference;
    private File mExtrasFolder;
    private ProgressDialog mProgressDialog;
    private Handler mUpdateHandler = new Handler();

    private static final int MENU_REFRESH = 0;
    private static final int MENU_DELETE_ALL = 1;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int flag = intent.getIntExtra("flag", Constants.INTENT_FLAG_GET_EXTRAS);
            if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
                if (DownloadReceiver.ACTION_DOWNLOAD_STARTED.equals(action)) {
                    mDownloadId = intent.getLongExtra(DownLoadService.DOWNLOAD_ID, -1);
                    mUpdateHandler.post(mUpdateProgress);
                } else if (UpdateCheckService.ACTION_CHECK_FINISHED.equals(action)) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        int count = intent.getIntExtra(UpdateCheckService.EXTRA_NEW_UPDATE_COUNT,
                                -1);
                        if (count == 0) {
                            Toast.makeText(mContext, R.string.no_extras_found, Toast.LENGTH_SHORT)
                                    .show();
                        } else if (count < 0) {
                            Toast.makeText(mContext, R.string.update_check_failed,
                                    Toast.LENGTH_LONG).show();
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
        mMokeeExtrasList = (PreferenceCategory) findPreference(Constants.PREF_EXPANG_LIST);// 扩展列表
        // mExtrasUpdate = (PreferenceScreen)
        // findPreference(Constants.PREF_EXTRAS_UPDATE);// 获取扩展
        // mExtrasUpdate.setOnPreferenceClickListener(this);
        // Load the stored preference data
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        updateLastCheckPreference();
        // Set 'HomeAsUp' feature of the actionbar to fit better into Settings
        final ActionBar bar = mContext.getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
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
            case MENU_REFRESH:
                checkForUpdates(Constants.INTENT_FLAG_GET_EXTRAS);
                return true;
            case MENU_DELETE_ALL:
                confirmDeleteAll();
                return true;
            case android.R.id.home:
                mContext.onBackPressed();
                return true;
        }
        return true;
    }

    // add
    private Runnable mUpdateProgress = new Runnable() {
        public void run() {
            if (!mDownloading || mDownloadingPreference == null || mDownloadId < 0) {
                return;
            }

            ProgressBar progressBar = mDownloadingPreference.getProgressBar();
            if (progressBar == null) {
                return;
            }
            DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfo(
                    String.valueOf(mDownloadId));
            int status;
            if (dli == null) {
                // DownloadReceiver has likely already removed the download
                // from the DB due to failure or MD5 mismatch
                status = DownLoader.STATUS_ERROR;
            } else {
                status = dli.getState();
            }
            switch (status) {
                case DownLoader.STATUS_PENDING:
                    progressBar.setIndeterminate(true);
                    break;
                case DownLoader.STATUS_PAUSED:
                case DownLoader.STATUS_DOWNLOADING:
                    List<ThreadDownLoadInfo> threadList = ThreadDownLoadDao.getInstance()
                            .getThreadInfoList(dli.getUrl());
                    int totalBytes = -1;
                    int downloadedBytes = 0;
                    for (ThreadDownLoadInfo info : threadList)
                    {
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
                    mDownloadingPreference.setStyle(ItemPreference.STYLE_EXTRAS_NEW);
                    resetDownloadState();
                    break;
            }
            if (status != DownLoader.STATUS_ERROR) {
                mUpdateHandler.postDelayed(this, 2000);
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
        Utils.cancelNotification(MoKeeApplication.getContext());

        // Build list of updates
        final LinkedList<ItemInfo> availableUpdates = State.loadMKState(
                MoKeeApplication.getContext(), State.EXTRAS_FILENAME);
        // Update the preference list
        refreshExtrasPreferences(availableUpdates);
    }

    private void refreshExtrasPreferences(LinkedList<ItemInfo> updates) {
        if (mMokeeExtrasList == null) {
            return;
        }
        // Clear the list
        mMokeeExtrasList.removeAll();
        // Add the updates
        for (ItemInfo ui : updates) {
            // Determine the preference style and create the preference
            boolean isDownloading = ui.getName().equals(mFileName);
            boolean isLocalFile = Utils.isLocaUpdateFile(ui.getName(), false);
            boolean isZip = ui.getName().endsWith(".zip");
            boolean isApk = ui.getName().endsWith(".apk");
            boolean isInstall = false;
            boolean isExtrasUpdate = false;
            boolean isGMS = ui.getName().contains("gapps");
            if (isZip || isApk) {
                isInstall = MoKeeUtils.isApkInstalled(ui.getCheckflag(), getActivity());
                if (isGMS && isInstall) {
                    isInstall = MoKeeUtils.isSystemApp(ui.getCheckflag(), getActivity());
                    if (isInstall) {
                        isExtrasUpdate = Utils.checkGmsVersion(ui.getName());
                    }
                }
            }
            int style = 3;
            if (isDownloading) {
                // In progress download
                style = ItemPreference.STYLE_DOWNLOADING;
            } else if (isExtrasUpdate) {
                style = ItemPreference.STYLE_NEW;
            } else if (isInstall) {
                // This is the currently installed version
                style = ItemPreference.STYLE_INSTALLED;
            } else if (!isLocalFile) {
                style = ItemPreference.STYLE_EXTRAS_NEW;
            } else if (isLocalFile) {
                style = ItemPreference.STYLE_DOWNLOADED;
            }

            ItemPreference up = new ItemPreference(mContext, ui, style);
            up.setOnActionListener(this);
            up.setKey(ui.getName());

            // If we have an in progress download, link the preference
            if (isDownloading) {
                mDownloadingPreference = up;
                up.setOnReadyListener(this);
                mDownloading = true;
            }
            // Add to the list
            mMokeeExtrasList.addPreference(up);
        }
        // If no updates are in the list, show the default message
        if (mMokeeExtrasList.getPreferenceCount() == 0) {
            Preference pref = new Preference(mContext);
            pref.setLayoutResource(R.layout.preference_empty_list);
            pref.setTitle(R.string.no_available_extras_intro);
            pref.setEnabled(false);
            mMokeeExtrasList.addPreference(pref);
        }
    }

    /**
     * 检测更新
     */
    private void checkForUpdates(final int flag) {
        if (mProgressDialog != null) {
            return;
        }
        State.saveMKState(MoKeeApplication.getContext(), new LinkedList<ItemInfo>(), State.EXTRAS_FILENAME);
        refreshExtrasPreferences(new LinkedList<ItemInfo>());
        // If there is no internet connection, display a message and return.
        if (!MoKeeUtils.isOnline(mContext)) {
            Toast.makeText(mContext, R.string.data_connection_required, Toast.LENGTH_SHORT).show();
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
                cancelIntent.setFlags(flag);
                MoKeeApplication.getContext().startService(cancelIntent);
                mProgressDialog = null;
            }
        });

        Intent checkIntent = new Intent(mContext, UpdateCheckService.class);
        checkIntent.setAction(UpdateCheckService.ACTION_CHECK);
        checkIntent.setFlags(flag);// 服务识别
        MoKeeApplication.getContext().startService(checkIntent);
        mProgressDialog.show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(mContext).setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_extras_all_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        deleteOldUpdates();
                        extrasLayout();
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    private boolean deleteOldUpdates() {
        boolean success;
        // mExtrasFolder: Foldername with fullpath of SDCARD
        if (mExtrasFolder.exists() && mExtrasFolder.isDirectory()) {
            deleteDir(mExtrasFolder);
            mExtrasFolder.mkdir();
            success = true;
            Toast.makeText(mContext, R.string.delete_extras_success_message, Toast.LENGTH_SHORT)
                    .show();
        } else if (!mExtrasFolder.exists()) {
            success = false;
            Toast.makeText(mContext, R.string.delete_extras_noFolder_message, Toast.LENGTH_SHORT)
                    .show();
        } else {
            success = false;
            Toast.makeText(mContext, R.string.delete_extras_failure_message, Toast.LENGTH_SHORT)
                    .show();
        }
        return success;
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        } else {
            DownLoadInfo dli = null;
            if (dir.getName().endsWith(".partial")) {
                 dli = DownLoadDao.getInstance().getDownLoadInfoByName(dir.getName());
            } else {
                 dli = DownLoadDao.getInstance().getDownLoadInfoByName(dir.getName() + ".partial");
            }
            if (dli != null) {
              ThreadDownLoadDao.getInstance().delete(dli.getUrl());
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
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
        ItemPreference pref = (ItemPreference) mMokeeExtrasList.findPreference(fileName);
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
        mDownloadId = mPrefs.getLong(Constants.EXTRAS_DOWNLOAD_ID, -1);
        if (mDownloadId >= 0) {
            DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfo(
                    String.valueOf(mDownloadId));
            if (dli == null) {
                Toast.makeText(mContext, R.string.download_not_found, Toast.LENGTH_LONG).show();
            } else {
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
            Toast.makeText(mContext, R.string.data_connection_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mDownloading) {
            Toast.makeText(mContext, R.string.download_already_running, Toast.LENGTH_LONG).show();
            return;
        }

        // We have a match, get ready to trigger the download
        mDownloadingPreference = pref;

        ItemInfo ui = mDownloadingPreference.getItemInfo();
        if (ui == null) {
            return;
        }

        mDownloadingPreference.setStyle(ItemPreference.STYLE_DOWNLOADING);
        mFileName = ui.getName();
        mDownloading = true;

        // Start the download
        Intent intent = new Intent(mContext, DownloadReceiver.class);
        intent.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
        intent.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO, (Parcelable) ui);
        intent.putExtra("flag", Constants.INTENT_FLAG_GET_EXTRAS);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);

        mUpdateHandler.post(mUpdateProgress);
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

                        // We are OK to stop download, trigger it
                        Intent intent = new Intent(mContext, DownLoadService.class);
                        intent.setAction(DownLoadService.ACTION_DOWNLOAD);
                        intent.putExtra(DownLoadService.DOWNLOAD_TYPE, DownLoadService.PAUSE);
                        intent.putExtra(DownLoadService.DOWNLOAD_URL, pref.getItemInfo().getRom());

                        MoKeeApplication.getContext()
                                .startServiceAsUser(intent, UserHandle.CURRENT);
                        mUpdateHandler.removeCallbacks(mUpdateProgress);
                        resetDownloadState();

                        // Clear the stored data from shared preferences
                        mPrefs.edit().remove(Constants.EXTRAS_DOWNLOAD_ID)
                                .remove(Constants.EXTRAS_DOWNLOAD_MD5).apply();

                        Toast.makeText(mContext, R.string.download_cancelled, Toast.LENGTH_SHORT)
                                .show();
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
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
        String dialogBody = getString(R.string.apply_extras_dialog_text, itemInfo.getName());
        // Display the dialog
        new AlertDialog.Builder(mContext).setTitle(R.string.apply_extras_dialog_title)
                .setMessage(dialogBody)
                .setPositiveButton(R.string.dialog_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TextUtils.isEmpty(itemInfo.getDescription())
                                || itemInfo.getName().endsWith(".zip")) {
                            try {
                                Utils.triggerUpdate(mContext, itemInfo.getName(), false);
                            } catch (IOException e) {
                                Log.e(TAG, "Unable to reboot into recovery mode", e);
                                Toast.makeText(mContext, R.string.apply_unable_to_reboot_toast,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else if (itemInfo.getName().endsWith(".apk")) {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(
                                    Uri.parse("file://" + Utils.makeExtraFolder().getAbsolutePath()
                                            + "/" + itemInfo.getName()),
                                    "application/vnd.android.package-archive");
                            startActivity(i);
                        }
                    }
                }).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mStartUpdateVisible = false;
                    }
                }).show();
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
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        } else if (!mExtrasFolder.exists()) {
            Toast.makeText(mContext, R.string.delete_extras_noFolder_message, Toast.LENGTH_SHORT)
                    .show();
        } else {
            Toast.makeText(mContext, R.string.delete_extras_failure_message, Toast.LENGTH_SHORT)
                    .show();
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
        long lastCheckTime = mPrefs.getLong(Constants.PREF_LAST_EXTRAS_CHECK, 0);
        if (lastCheckTime == 0) {
            Utils.setSummaryFromString(this, KEY_MOKEE_LAST_CHECK,
                    getString(R.string.mokee_last_check_never));
        } else {
            Date lastCheck = new Date(lastCheckTime);
            String date = DateFormat.getLongDateFormat(mContext).format(lastCheck);
            String time = DateFormat.getTimeFormat(mContext).format(lastCheck);
            Utils.setSummaryFromString(this, KEY_MOKEE_LAST_CHECK, date + " " + time);
        }
    }
}
