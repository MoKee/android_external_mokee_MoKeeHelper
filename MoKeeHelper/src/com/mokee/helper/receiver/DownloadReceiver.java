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

package com.mokee.helper.receiver;

import java.io.File;
import java.io.IOException;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.mokee.helper.R;
import com.mokee.helper.MokeeApplication;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.fragments.MoKeeUpdaterFragment;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.UpdateInfo;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.MD5;
import com.mokee.helper.utils.Utils;

public class DownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "DownloadReceiver";

    public static final String ACTION_START_DOWNLOAD = "com.mokee.mkupdater.action.START_DOWNLOAD";
    public static final String EXTRA_UPDATE_INFO = "update_info";

    public static final String ACTION_DOWNLOAD_STARTED = "com.mokee.mkupdater.action.DOWNLOAD_STARTED";

    private static final String ACTION_INSTALL_UPDATE = "com.mokee.mkupdater.action.INSTALL_UPDATE";
    private static final String EXTRA_FILENAME = "filename";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (ACTION_START_DOWNLOAD.equals(action)) {
            UpdateInfo ui = (UpdateInfo) intent.getParcelableExtra(EXTRA_UPDATE_INFO);
            handleStartDownload(context, prefs, ui);
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            handleDownloadComplete(context, prefs, id);
        } else if (ACTION_INSTALL_UPDATE.equals(action)) {
            String fileName = intent.getStringExtra(EXTRA_FILENAME);
            try {
                Utils.triggerUpdate(context, fileName);
            } catch (IOException e) {
                Log.e(TAG, "Unable to reboot into recovery mode", e);
                Toast.makeText(context, R.string.apply_unable_to_reboot_toast, Toast.LENGTH_SHORT)
                        .show();
                Utils.cancelNotification(context);
            }
        }
    }

    private void handleStartDownload(Context context, SharedPreferences prefs, UpdateInfo ui) {
        // If directory doesn't exist, create it
        File directory;
        if (TextUtils.isEmpty(ui.getDescription())) {
            directory = Utils.makeUpdateFolder();
        } else {
            directory = Utils.makeExtraFolder();
        }

        if (!directory.exists()) {
            directory.mkdirs();
            Log.d(TAG, "UpdateFolder created");
        }

        // Build the name of the file to download, adding .partial at the end.
        // It will get
        // stripped off when the download completes
        String fullFilePath = "file://" + directory.getAbsolutePath() + "/" + ui.getName()
                + ".partial";

        Request request = new Request(Uri.parse(ui.getRom()));
        String userAgent = Utils.getUserAgentString(context);
        if (userAgent != null) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        request.addRequestHeader("Cache-Control", "no-cache");

        request.setTitle(context.getString(R.string.mokee_updater_title));
        request.setDestinationUri(Uri.parse(fullFilePath));
        request.setAllowedOverRoaming(false);
        request.setVisibleInDownloadsUi(false);

        // TODO: this could/should be made configurable
        request.setAllowedOverMetered(true);

        // Start the download
        final DownloadManager dm = (DownloadManager) context
                .getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);

        // Store in shared preferences
        prefs.edit().putLong(Constants.DOWNLOAD_ID, downloadId)
                .putString(Constants.DOWNLOAD_MD5, ui.getMd5()).apply();

        Utils.cancelNotification(context);

        Intent intent = new Intent(ACTION_DOWNLOAD_STARTED);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        context.sendBroadcast(intent);
    }

    private void handleDownloadComplete(Context context, SharedPreferences prefs, long id) {
        long enqueued = prefs.getLong(Constants.DOWNLOAD_ID, -1);

        if (enqueued < 0 || id < 0 || id != enqueued) {
            return;
        }

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Query query = new Query();
        query.setFilterById(id);

        Cursor c = dm.query(query);
        if (c == null) {
            return;
        }

        if (!c.moveToFirst()) {
            c.close();
            return;
        }

        final int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
        int failureMessageResId = -1;
        File updateFile = null;
        Intent updateIntent = new Intent();
        updateIntent.setAction(MoKeeCenter.ACTION_MOKEE_CENTER);
        updateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            // Get the full path name of the downloaded file and the MD5

            // Strip off the .partial at the end to get the completed file
            String partialFileFullPath = c.getString(c
                    .getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
            String completedFileFullPath = partialFileFullPath.replace(".partial", "");

            File partialFile = new File(partialFileFullPath);
            updateFile = new File(completedFileFullPath);
            partialFile.renameTo(updateFile);

            String downloadedMD5 = prefs.getString(Constants.DOWNLOAD_MD5, "");
            // Start the MD5 check of the downloaded file
            if (MD5.checkMD5(downloadedMD5, updateFile)) {
                // We passed. Bring the main app to the foreground and trigger
                // download completed
                updateIntent.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, id);
                updateIntent.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH,
                        completedFileFullPath);
            } else {
                // We failed. Clear the file and reset everything
                dm.remove(id);

                if (updateFile.exists()) {
                    updateFile.delete();
                }

                failureMessageResId = R.string.md5_verification_failed;
            }
        } else if (status == DownloadManager.STATUS_FAILED) {
            // The download failed, reset
            dm.remove(id);

            failureMessageResId = R.string.unable_to_download_file;
        }

        // Clear the shared prefs
        prefs.edit().remove(Constants.DOWNLOAD_MD5).remove(Constants.DOWNLOAD_ID).apply();

        c.close();

        final MokeeApplication app = (MokeeApplication) context.getApplicationContext();
        if (app.isMainActivityActive()) {
            if (failureMessageResId >= 0) {
                Toast.makeText(context, failureMessageResId, Toast.LENGTH_LONG).show();
            } else {
                context.startActivity(updateIntent);
            }
        } else {
            // Get the notification ready
            PendingIntent contentIntent = PendingIntent.getActivity(context, 1, updateIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder builder = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_mokee_updater).setWhen(System.currentTimeMillis())
                    .setContentIntent(contentIntent).setAutoCancel(true);

            if (failureMessageResId >= 0) {
                builder.setContentTitle(context.getString(R.string.not_download_failure));
                builder.setContentText(context.getString(failureMessageResId));
                builder.setTicker(context.getString(R.string.not_download_failure));
            } else {
                String updateUiName = UpdateInfo.extractUiName(updateFile.getName());

                builder.setContentTitle(context.getString(R.string.not_download_success));
                builder.setContentText(updateUiName);
                builder.setTicker(context.getString(R.string.not_download_success));

                Notification.BigTextStyle style = new Notification.BigTextStyle();
                style.setBigContentTitle(context.getString(R.string.not_download_success));
                style.bigText(context.getString(R.string.not_download_install_notice, updateUiName));
                builder.setStyle(style);

                Intent installIntent = new Intent(context, DownloadReceiver.class);
                installIntent.setAction(ACTION_INSTALL_UPDATE);
                installIntent.putExtra(EXTRA_FILENAME, updateFile.getName());

                PendingIntent installPi = PendingIntent.getBroadcast(context, 0, installIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(R.drawable.ic_tab_install,
                        context.getString(R.string.not_action_install_update), installPi);
            }

            final NotificationManager nm = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(R.string.not_download_success, builder.build());
        }
    }
}
