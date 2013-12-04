/*
 * Copyright (C) 2014 The MoKee OpenSource Project
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

package com.mokee.helper.fragments;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;

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
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.State;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.UpdateFilter;
import com.mokee.helper.utils.Utils;
import com.mokee.helper.widget.ItemPreference;

public class MoKeeUpdaterFragment extends PreferenceFragment implements OnPreferenceChangeListener,
        ItemPreference.OnReadyListener, ItemPreference.OnActionListener {

    private static String TAG = "MoKeeUpdater";

    private static final String KEY_MOKEE_VERSION = "mokee_version";
    private static final String KEY_MOKEE_VERSION_TYPE = "mokee_version_type";
    private static final String KEY_MOKEE_LAST_CHECK = "mokee_last_check";

    private DownloadManager mDownloadManager;
    private boolean mDownloading = false;
    private long mDownloadId;
    private String mFileName;

    private Activity mContext;

    private boolean mStartUpdateVisible = false;

    private static final String UPDATES_CATEGORY = "updates_category";

    private static final int TAPS_TO_BE_A_EXPERIMENTER = 7;
    private static final String EXPERIMENTAL_SHOW = "experimental_show";
    long[] mHits = new long[3];
    int mExpHitCountdown;
    Toast mExpHitToast;

    private static final int MENU_REFRESH = 0;
    private static final int MENU_DELETE_ALL = 1;
    private SharedPreferences mPrefs;
    private CheckBoxPreference mUpdateAll, mUpdateOTA;
    private ListPreference mUpdateCheck;
    private ListPreference mUpdateType;
    // private PreferenceScreen mEXTRASUpdate;
    private PreferenceCategory mUpdatesList;
    private ItemPreference mDownloadingPreference;
    private File mUpdateFolder;// ,mEXTRASFolder;
    private ProgressDialog mProgressDialog;
    private Handler mUpdateHandler = new Handler();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int flag = intent.getIntExtra("flag", Constants.INTENT_FLAG_GET_UPDATE);
            if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
                if (DownloadReceiver.ACTION_DOWNLOAD_STARTED.equals(action)) {
                    mDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    mUpdateHandler.post(mUpdateProgress);
                } else if (UpdateCheckService.ACTION_CHECK_FINISHED.equals(action)) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                        int count = intent.getIntExtra(UpdateCheckService.EXTRA_NEW_UPDATE_COUNT,
                                -1);
                        if (count == 0) {
                            Toast.makeText(mContext, R.string.no_updates_found, Toast.LENGTH_SHORT)
                                    .show();
                        } else if (count < 0) {
                            Toast.makeText(mContext, R.string.update_check_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    updateLayout();
                } else if (MoKeeCenter.BR_ONNewIntent.equals(action)) {
                    // 唤醒
                    if (intent.getBooleanExtra(UpdateCheckService.EXTRA_UPDATE_LIST_UPDATED, false)) {
                        updateLayout();
                    }
                    checkForDownloadCompleted(intent);
                }
            }
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(KEY_MOKEE_VERSION_TYPE)) {
            // Don't enable experimental option for secondary users.
            if (UserHandle.myUserId() != UserHandle.USER_OWNER)
                return true;

            if (mExpHitCountdown > 0) {
                mExpHitCountdown--;
                if (mExpHitCountdown == 0) {
                    mPrefs.edit().putBoolean(EXPERIMENTAL_SHOW, true).apply();
                    if (mExpHitToast != null) {
                        mExpHitToast.cancel();
                    }
                    mExpHitToast = Toast
                            .makeText(mContext, R.string.show_exp_on, Toast.LENGTH_LONG);
                    mExpHitToast.show();
                    String[] entries = mContext.getResources().getStringArray(
                            R.array.update_type_entries);
                    String[] entryValues = mContext.getResources().getStringArray(
                            R.array.update_type_values);
                    mUpdateType.setEntries(entries);
                    mUpdateType.setEntryValues(entryValues);
                } else if (mExpHitCountdown > 0
                        && mExpHitCountdown < (TAPS_TO_BE_A_EXPERIMENTER - 2)) {
                    if (mExpHitToast != null) {
                        mExpHitToast.cancel();
                    }
                    mExpHitToast = Toast.makeText(
                            mContext,
                            getResources().getQuantityString(R.plurals.show_exp_countdown,
                                    mExpHitCountdown, mExpHitCountdown), Toast.LENGTH_SHORT);
                    mExpHitToast.show();
                }
            } else if (mExpHitCountdown < 0) {
                if (mExpHitToast != null) {
                    mExpHitToast.cancel();
                }
                mExpHitToast = Toast.makeText(mContext, R.string.show_exp_already,
                        Toast.LENGTH_LONG);
                mExpHitToast.show();
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mDownloadManager = (DownloadManager) mContext.getSystemService(mContext.DOWNLOAD_SERVICE);
        // Load the layouts
        addPreferencesFromResource(R.xml.mokee_updater);
        mUpdatesList = (PreferenceCategory) findPreference(UPDATES_CATEGORY);
        mUpdateCheck = (ListPreference) findPreference(Constants.UPDATE_CHECK_PREF);
        mUpdateType = (ListPreference) findPreference(Constants.UPDATE_TYPE_PREF);
        mUpdateAll = (CheckBoxPreference) findPreference(Constants.PREF_ROM_ALL);// 所有更新
        mUpdateOTA = (CheckBoxPreference) findPreference(Constants.PREF_ROM_OTA);// OTA更新
        // Load the stored preference data
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Restore normal type list
        String MoKeeVersionType = Utils.getMoKeeVersionType();
        boolean isExperimental = TextUtils.equals(MoKeeVersionType, "experimental");
        if (!isExperimental) {
            mPrefs.edit().putBoolean(EXPERIMENTAL_SHOW, false).putInt(Constants.UPDATE_TYPE_PREF, 0).apply();
        }

        if (mUpdateCheck != null) {
            int check = mPrefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);
            mUpdateCheck.setValue(String.valueOf(check));
            mUpdateCheck.setSummary(mapCheckValue(check));
            mUpdateCheck.setOnPreferenceChangeListener(this);
        }

        if (mUpdateType != null) {
            int type = mPrefs.getInt(Constants.UPDATE_TYPE_PREF, 0);
            mUpdateType.setValue(String.valueOf(type));
            mUpdateType.setSummary(mUpdateType.getEntries()[type]);
            mUpdateType.setOnPreferenceChangeListener(this);
            if (!mPrefs.getBoolean(EXPERIMENTAL_SHOW, isExperimental)) {
                setNormalTypeEntiries();
            }
        }
        mUpdateOTA.setChecked(mPrefs.getBoolean(Constants.PREF_ROM_OTA, true));
        mUpdateAll.setChecked(mPrefs.getBoolean(Constants.PREF_ROM_ALL, false));
        mUpdateAll.setOnPreferenceChangeListener(this);
        mUpdateOTA.setOnPreferenceChangeListener(this);
        isOTA(mUpdateOTA.isChecked());
        isRomALl(mUpdateAll.isChecked());
        setSummaryFromProperty(KEY_MOKEE_VERSION, "ro.mk.version");
        Utils.setSummaryFromString(this, KEY_MOKEE_VERSION_TYPE,
                Utils.getMoKeeVersionTypeString(mContext));
        updateLastCheckPreference();

        // Set 'HomeAsUp' feature of the actionbar to fit better into Settings
        final ActionBar bar = mContext.getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mExpHitCountdown = mPrefs.getBoolean(EXPERIMENTAL_SHOW,
                TextUtils.equals(Utils.getMoKeeVersionType(), "experimental")) ? -1
                : TAPS_TO_BE_A_EXPERIMENTER;
        mExpHitToast = null;
    }    

    public void setNormalTypeEntiries() {
        int index = 2;
        String[] entries = mContext.getResources().getStringArray(R.array.update_type_entries);
        String[] newEntries = new String[entries.length - 1];
        System.arraycopy(entries, 0, newEntries, 0, index);
        System.arraycopy(entries, index + 1, newEntries, index, newEntries.length - index);
        String[] entryValues = mContext.getResources().getStringArray(R.array.update_type_values);
        String[] newEntriesValues = new String[entryValues.length - 1];
        System.arraycopy(entryValues, 0, newEntriesValues, 0, index);
        System.arraycopy(entryValues, index + 1, newEntriesValues, index, newEntriesValues.length
                - index);
        mUpdateType.setEntries(newEntries);
        mUpdateType.setEntryValues(newEntriesValues);
    }

    public void updateLastCheckPreference() {
        long lastCheckTime = mPrefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, 0);
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
                checkForUpdates(Constants.INTENT_FLAG_GET_UPDATE);
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

    @Override
    public void onReady(ItemPreference pref) {
        pref.setOnReadyListener(null);
        mUpdateHandler.post(mUpdateProgress);
    }

    private void setSummaryFromProperty(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property, getString(R.string.mokee_info_default)));
        } catch (RuntimeException e) {
            // No recovery
        }
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

            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(mDownloadId);
            Cursor cursor = mDownloadManager.query(q);
            int status;

            if (cursor == null || !cursor.moveToFirst()) {
                // DownloadReceiver has likely already removed the download
                // from the DB due to failure or MD5 mismatch
                status = DownloadManager.STATUS_FAILED;
            } else {
                status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }

            switch (status) {
                case DownloadManager.STATUS_PENDING:
                    progressBar.setIndeterminate(true);
                    break;
                case DownloadManager.STATUS_PAUSED:
                case DownloadManager.STATUS_RUNNING:
                    int downloadedBytes = cursor.getInt(cursor
                            .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int totalBytes = cursor.getInt(cursor
                            .getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (totalBytes < 0) {
                        progressBar.setIndeterminate(true);
                    } else {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(totalBytes);
                        progressBar.setProgress(downloadedBytes);
                    }
                    break;
                case DownloadManager.STATUS_FAILED:
                    mDownloadingPreference.setStyle(ItemPreference.STYLE_NEW);
                    resetDownloadState();
                    break;
            }
            if (cursor != null) {
                cursor.close();
            }
            if (status != DownloadManager.STATUS_FAILED) {
                mUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    private void resetDownloadState() {
        mDownloadId = -1;
        mFileName = null;
        mDownloading = false;
        mDownloadingPreference = null;
    }

    private void updateLayout() {
        updateLastCheckPreference();
        // Read existing Updates
        LinkedList<String> existingFiles = new LinkedList<String>();
        mUpdateFolder = Utils.makeUpdateFolder();
        File[] files = mUpdateFolder.listFiles(new UpdateFilter(".zip"));
        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory() && files != null) {
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
                MoKeeApplication.getContext(), State.UPDATE_FILENAME);

        if (!mPrefs.getBoolean(Constants.PREF_ROM_OTA, true)) {
            Collections.sort(availableUpdates, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    /* sort by date descending */
                    int lhsDate = Integer.valueOf(Utils.subBuildDate(lhs.getName()));
                    int rhsDate = Integer.valueOf(Utils.subBuildDate(rhs.getName()));
                    if (lhsDate == rhsDate) {
                        return 0;
                    }
                    return lhsDate < rhsDate ? 1 : -1;
                }
            });
        }
        // Update the preference list
        refreshPreferences(availableUpdates);
    }

    private void refreshPreferences(LinkedList<ItemInfo> updates) {
        if (mUpdatesList == null) {
            return;
        }
        // Clear the list
        mUpdatesList.removeAll();
        // Convert the installed version name to the associated filename
        String installedZip = Utils.getInstalledVersion() + ".zip";
        boolean isNew = true;// 判断新旧版本
        int nowDate = Integer.valueOf(Utils.subBuildDate(installedZip));
        int nowVersion = Integer.valueOf(Utils.subMoKeeVersion(installedZip));
        boolean isRomAll = mPrefs.getBoolean(Constants.PREF_ROM_ALL, true);
        // Add the updates
        for (ItemInfo ui : updates) {
            // Determine the preference style and create the preference
            boolean isDownloading = ui.getName().equals(mFileName);
            boolean isLocalFile = Utils.isLocaUpdateFile(ui.getName(), true);
            int style = 3;
            if (isRomAll && !mPrefs.getBoolean(Constants.PREF_ROM_OTA, true)) {
                int itemDate = Integer.valueOf(Utils.subBuildDate(ui.getName()));
                int itemVersion = Integer.valueOf(Utils.subMoKeeVersion(ui.getName()));
                isNew = itemDate > nowDate && itemVersion >= nowVersion ? true : false;
            }
            if (isDownloading) {
                // In progress download
                style = ItemPreference.STYLE_DOWNLOADING;
            } else if (ui.getName().equals(installedZip)) {
                // This is the currently installed version
                style = ItemPreference.STYLE_INSTALLED;
            } else if (!isLocalFile && isNew) {
                style = ItemPreference.STYLE_NEW;
            } else if (!isLocalFile && !isNew) {
                style = ItemPreference.STYLE_OLD;
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
            mUpdatesList.addPreference(up);
        }
        // If no updates are in the list, show the default message
        if (mUpdatesList.getPreferenceCount() == 0) {
            Preference pref = new Preference(mContext);
            pref.setLayoutResource(R.layout.preference_empty_list);
            pref.setTitle(R.string.no_available_updates_intro);
            pref.setEnabled(false);
            mUpdatesList.addPreference(pref);
        }
    }

    private String mapCheckValue(Integer value) {
        Resources resources = getResources();
        String[] checkNames = resources.getStringArray(R.array.update_check_entries);
        String[] checkValues = resources.getStringArray(R.array.update_check_values);
        for (int i = 0; i < checkValues.length; i++) {
            if (Integer.decode(checkValues[i]).equals(value)) {
                return checkNames[i];
            }
        }
        return getString(R.string.unknown);
    }

    private void isOTA(boolean value) {
        if (value) {
            mUpdateType.setEnabled(false);
        } else {
            mUpdateType.setEnabled(true);
        }
    }

    /**
     * 检测更新
     */
    private void checkForUpdates(final int flag) {
        if (mProgressDialog != null) {
            return;
        }

        // If there is no internet connection, display a message and return.
        if (!Utils.isOnline(mContext)) {
            Toast.makeText(mContext, R.string.data_connection_required, Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.mokee_updater_title);
        mProgressDialog.setMessage(getString(R.string.checking_for_updates));
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
                .setMessage(R.string.confirm_delete_updates_all_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        deleteOldUpdates();
                        updateLayout();
                    }
                }).setNegativeButton(R.string.dialog_cancel, null).show();
    }

    private boolean deleteOldUpdates() {
        boolean success;
        // mUpdateFolder: Foldername with fullpath of SDCARD
        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
            deleteDir(mUpdateFolder);
            mUpdateFolder.mkdir();
            success = true;
            Toast.makeText(mContext, R.string.delete_updates_success_message, Toast.LENGTH_SHORT)
                    .show();
        } else if (!mUpdateFolder.exists()) {
            success = false;
            Toast.makeText(mContext, R.string.delete_updates_noFolder_message, Toast.LENGTH_SHORT)
                    .show();
        } else {
            success = false;
            Toast.makeText(mContext, R.string.delete_updates_failure_message, Toast.LENGTH_SHORT)
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
        }
        // The directory is now empty so delete it
        return dir.delete();
    }

    private void updateUpdatesType(int type) {
        mPrefs.edit().putInt(Constants.UPDATE_TYPE_PREF, type).apply();
        mUpdateType.setValue(String.valueOf(type));
         if (type==3&&!mPrefs.getBoolean(EXPERIMENTAL_SHOW, true)) {
            mUpdateType.setSummary(mUpdateType.getEntries()[type-1]);
        }
        else{
            mUpdateType.setSummary(mUpdateType.getEntries()[type]);
        }
        checkForUpdates(Constants.INTENT_FLAG_GET_UPDATE);
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
        ItemPreference pref = (ItemPreference) mUpdatesList.findPreference(fileName);
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
        mDownloadId = mPrefs.getLong(Constants.DOWNLOAD_ID, -1);
        if (mDownloadId >= 0) {
            Cursor c = mDownloadManager.query(new DownloadManager.Query()
                    .setFilterById(mDownloadId));
            if (c == null || !c.moveToFirst()) {
                Toast.makeText(mContext, R.string.download_not_found, Toast.LENGTH_LONG).show();
            } else {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Uri uri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI)));
                if (status == DownloadManager.STATUS_PENDING
                        || status == DownloadManager.STATUS_RUNNING
                        || status == DownloadManager.STATUS_PAUSED) {
                    String localFileName = c.getString(c
                            .getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                    if (!TextUtils.isEmpty(localFileName)) {
                        mFileName = localFileName.substring(localFileName.lastIndexOf("/") + 1,
                                localFileName.lastIndexOf("."));
                    }
                }
            }
            if (c != null) {
                c.close();
            }
        }
        if (mDownloadId < 0 || mFileName == null) {
            resetDownloadState();
        }

        updateLayout();
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
        if (!Utils.isOnline(mContext)) {
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
        mContext.sendBroadcast(intent);

        mUpdateHandler.post(mUpdateProgress);
    }

    @Override
    public void onStopDownload(final ItemPreference pref) {
        if (!mDownloading || mFileName == null || mDownloadId < 0) {
            pref.setStyle(ItemPreference.STYLE_NEW);
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
                        pref.setStyle(ItemPreference.STYLE_NEW);

                        // We are OK to stop download, trigger it
                        mDownloadManager.remove(mDownloadId);
                        mUpdateHandler.removeCallbacks(mUpdateProgress);
                        resetDownloadState();

                        // Clear the stored data from shared preferences
                        mPrefs.edit().remove(Constants.DOWNLOAD_ID).remove(Constants.DOWNLOAD_MD5)
                                .apply();

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
        String dialogBody = getString(R.string.apply_update_dialog_text, itemInfo.getName());
        // Display the dialog
        new AlertDialog.Builder(mContext).setTitle(R.string.apply_update_dialog_title)
                .setMessage(dialogBody)
                .setPositiveButton(R.string.dialog_update, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Utils.triggerUpdate(mContext, itemInfo.getName());
                        } catch (IOException e) {
                            Log.e(TAG, "Unable to reboot into recovery mode", e);
                            Toast.makeText(mContext, R.string.apply_unable_to_reboot_toast,
                                    Toast.LENGTH_SHORT).show();
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

        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
            File zipFileToDelete = new File(mUpdateFolder, fileName);

            if (zipFileToDelete.exists()) {
                zipFileToDelete.delete();
            } else {
                Log.d(TAG, "Update to delete not found");
                return;
            }

            String message = getString(R.string.delete_single_update_success_message, fileName);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        } else if (!mUpdateFolder.exists()) {
            Toast.makeText(mContext, R.string.delete_updates_noFolder_message, Toast.LENGTH_SHORT)
                    .show();
        } else {
            Toast.makeText(mContext, R.string.delete_updates_failure_message, Toast.LENGTH_SHORT)
                    .show();
        }

        // Update the list
        updateLayout();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mUpdateCheck) {
            int value = Integer.valueOf((String) newValue);
            mPrefs.edit().putInt(Constants.UPDATE_CHECK_PREF, value).apply();
            mUpdateCheck.setSummary(mapCheckValue(value));
            Utils.scheduleUpdateService(mContext, value * 1000);
            return true;
        } else if (preference == mUpdateType) {
            final int value = Integer.valueOf((String) newValue);
            if (value == Constants.UPDATE_TYPE_NIGHTLY
                    || value == Constants.UPDATE_TYPE_EXPERIMENTAL
                    || value == Constants.UPDATE_TYPE_ALL) {
                int messageId = 0;
                switch (value) {
                    case 1:
                        messageId = R.string.nightly_alert;
                        break;
                    case 2:
                        messageId = R.string.experimenter_alert;
                        break;
                    case 3:
                        messageId = R.string.all_alert;
                        break;
                }
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.alert_title)
                        .setMessage(messageId)
                        .setPositiveButton(getString(R.string.dialog_ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        updateUpdatesType(value);
                                    }
                                }).setNegativeButton(R.string.dialog_cancel, null).show();
                return false;
            } else {
                updateUpdatesType(value);
            }
            return true;
        } else if (preference == mUpdateAll) {
            boolean value = (Boolean) newValue;
            isRomALl(value);
            return true;
        } else if (preference == mUpdateOTA) {
            boolean value = (Boolean) newValue;
            isOTA(value);
            checkForUpdates(Constants.INTENT_FLAG_GET_UPDATE);
            return true;
        }
        return false;
    }

    private void isRomALl(boolean value) {
        if (value) {
            mUpdateAll
                    .setSummary(mContext.getResources().getText(R.string.pref_update_all_summary));
        } else {
            mUpdateAll.setSummary(mContext.getResources().getText(
                    R.string.pref_update_all_new_summary));
        }
    }
}
