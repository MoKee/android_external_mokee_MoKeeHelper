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

package com.mokee.helper.receiver;

import java.io.File;
import java.io.IOException;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.db.DownLoadDao;
import com.mokee.helper.db.ThreadDownLoadDao;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.DownLoadInfo;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.service.DownLoadService;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.DownLoader;
import com.mokee.helper.utils.MD5;
import com.mokee.helper.utils.Utils;

public class DownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "DownloadReceiver";

    public static final String ACTION_START_DOWNLOAD = "com.mokee.mkupdater.action.START_DOWNLOAD";
    public static final String EXTRA_UPDATE_INFO = "update_info";
    public static final String ACTION_DOWNLOAD_STARTED = "com.mokee.mkupdater.action.DOWNLOAD_STARTED";
    public static final String ACTION_NOTIFICATION_CLICKED = "com.mokee.mkupdater.action.NOTIFICATION_CLICKED";

    public static final String ACTION_INSTALL_UPDATE = "com.mokee.mkupdater.action.INSTALL_UPDATE";
    public static final String EXTRA_FILENAME = "filename";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences(Constants.DOWNLOADER_PREF, 0);
        if (ACTION_START_DOWNLOAD.equals(action)) {
            ItemInfo ui = (ItemInfo) intent.getParcelableExtra(EXTRA_UPDATE_INFO);
            int flag = intent.getIntExtra("flag", 1024);
            handleStartDownload(context, prefs, ui, flag);
        } else if (DownLoadService.ACTION_DOWNLOAD_COMPLETE.equals(action)) {// 接收下完通知
            long id = intent.getLongExtra(DownLoadService.DOWNLOAD_ID, -1);
            int flag = intent.getIntExtra("flag", 1024);// 标识
            handleDownloadComplete(context, prefs, id, flag);
        } else if (ACTION_INSTALL_UPDATE.equals(action)) {
            String fileName = intent.getStringExtra(EXTRA_FILENAME);
            int flag = intent.getIntExtra("flag", 1024);// 标识
            if(flag ==  Constants.INTENT_FLAG_GET_UPDATE) {
                if (fileName.endsWith(".zip")) {
                    try {
                        StatusBarManager sb = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
                        sb.collapsePanels();
                        Utils.cancelNotification(context);
                        Utils.triggerUpdate(context, fileName, true);
                    } catch (IOException e) {
                        Log.e(TAG, "Unable to reboot into recovery mode", e);
                        Toast.makeText(context, R.string.apply_unable_to_reboot_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
                if (fileName.endsWith(".zip")) {
                    try {
                        StatusBarManager sb = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
                        sb.collapsePanels();
                        Utils.cancelNotification(context);
                        Utils.triggerUpdate(context, fileName, false);
                    } catch (IOException e) {
                        Log.e(TAG, "Unable to reboot into recovery mode", e);
                        Toast.makeText(context, R.string.apply_unable_to_reboot_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                } else if (fileName.endsWith(".apk")) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(
                            Uri.parse("file://" + Utils.makeExtraFolder().getAbsolutePath() + "/"
                                    + fileName), "application/vnd.android.package-archive");
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MoKeeApplication.getContext().startActivity(i);
                    Utils.cancelNotification(context);
                } else {
                    Toast.makeText(MoKeeApplication.getContext(), R.string.extras_unsupported_toast, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private void handleStartDownload(Context context, SharedPreferences prefs, ItemInfo ui, int flag) {
        // If directory doesn't exist, create it
        File directory;
        if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
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
        String fullFilePath = directory.getAbsolutePath() + "/" + ui.getName() + ".partial";

        DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfoByUrl(ui.getRom());
      
        long downloadId;
        if (dli != null) {
            downloadId = Long.valueOf(dli.getDownID());
        } else {
            downloadId = System.currentTimeMillis();
        }

        // Store in shared preferences
        if (flag == Constants.INTENT_FLAG_GET_UPDATE)// 区分扩展&更新
        {
            prefs.edit().putLong(Constants.DOWNLOAD_ID, downloadId)
                    .putString(Constants.DOWNLOAD_MD5, ui.getMd5()).apply();
        } else {
            prefs.edit().putLong(Constants.EXTRAS_DOWNLOAD_ID, downloadId)
                    .putString(Constants.EXTRAS_DOWNLOAD_MD5, ui.getMd5()).apply();
        }
        Intent intentService = new Intent(context, DownLoadService.class);
        intentService.setAction(DownLoadService.ACTION_DOWNLOAD);
        intentService.putExtra(DownLoadService.DOWNLOAD_TYPE, DownLoadService.ADD);
        intentService.putExtra(DownLoadService.DOWNLOAD_URL, ui.getRom());
        intentService.putExtra(DownLoadService.DOWNLOAD_FILE_PATH, fullFilePath);
        intentService.putExtra(DownLoadService.DOWNLOAD_FLAG, flag);
        intentService.putExtra(DownLoadService.DOWNLOAD_ID, downloadId);
        MoKeeApplication.getContext().startServiceAsUser(intentService, UserHandle.CURRENT);
        Utils.cancelNotification(context);

        Intent intentBroadcast = new Intent(ACTION_DOWNLOAD_STARTED);
        intentBroadcast.putExtra("flag", flag);
        intentBroadcast.putExtra(DownLoadService.DOWNLOAD_ID, downloadId);
        context.sendBroadcastAsUser(intentBroadcast, UserHandle.CURRENT);
    }

    private void handleDownloadComplete(Context context, SharedPreferences prefs, long downID,
            int flag) {
        long enqueued;
        DownLoadInfo dli;
        int status;
        Intent updateIntent;
        switch (flag) {
            case Constants.INTENT_FLAG_GET_UPDATE:
                enqueued = prefs.getLong(Constants.DOWNLOAD_ID, -1);
                if (enqueued < 0 || downID < 0 || downID != enqueued) {
                    return;
                }
                dli = DownLoadDao.getInstance().getDownLoadInfo(String.valueOf(downID));
                if (dli == null) {
                    return;
                }
                status = dli.getState();
                updateIntent = new Intent(ACTION_NOTIFICATION_CLICKED);
                updateIntent.putExtra("flag", flag);

                if (status == DownLoader.STATUS_COMPLETE) {
                    // Get the full path name of the downloaded file and the MD5

                    // Strip off the .partial at the end to get the completed
                    // file
                    String partialFileFullPath = dli.getLocalFile();
                    String completedFileFullPath = partialFileFullPath.replace(".partial", "");

                    File partialFile = new File(partialFileFullPath);
                    File updateFile = new File(completedFileFullPath);
                    partialFile.renameTo(updateFile);

                    String downloadedMD5 = prefs.getString(Constants.DOWNLOAD_MD5, "");
                    // Start the MD5 check of the downloaded file
                    if (MD5.checkMD5(downloadedMD5, updateFile)) {
                        // We passed. Bring the main app to the foreground and
                        // trigger
                        // download completed
                        updateIntent
                                .putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, downID);
                        updateIntent.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH,
                                completedFileFullPath);
                        displaySuccessResult(context, updateIntent, updateFile, flag);
                    } else {
                        // We failed. Clear the file and reset everything
                        DownLoadDao.getInstance().delete(String.valueOf(downID));
                        if (updateFile.exists()) {
                            updateFile.delete();
                        }
                        displayErrorResult(context, updateIntent, R.string.md5_verification_failed);
                    }
                    //delete thread info
                    ThreadDownLoadDao.getInstance().delete(dli.getUrl());
                } else if (status == DownLoader.STATUS_ERROR) {
                    // The download failed, reset
                    displayErrorResult(context, updateIntent, R.string.unable_to_download_file);
                }

                // Clear the shared prefs
                prefs.edit().remove(Constants.DOWNLOAD_MD5).remove(Constants.DOWNLOAD_ID).apply();
                break;
            case Constants.INTENT_FLAG_GET_EXTRAS:
                String completedFileFullPath = null;
                enqueued = prefs.getLong(Constants.EXTRAS_DOWNLOAD_ID, -1);
                if (enqueued < 0 || downID < 0 || downID != enqueued) {
                    return;
                }
                dli = DownLoadDao.getInstance().getDownLoadInfo(String.valueOf(downID));
                if (dli == null) {
                    return;
                }
                status = dli.getState();
                updateIntent = new Intent(ACTION_NOTIFICATION_CLICKED);
                updateIntent.putExtra("flag", flag);

                if (status == DownLoader.STATUS_COMPLETE) {
                    // Get the full path name of the downloaded file and the MD5
                    // Strip off the .partial at the end to get the completed
                    // file
                    String partialFileFullPath = dli.getLocalFile();
                    completedFileFullPath = partialFileFullPath.replace(".partial", "");

                    File partialFile = new File(partialFileFullPath);
                    File updateFile = new File(completedFileFullPath);
                    partialFile.renameTo(updateFile);

                    String downloadedMD5 = prefs.getString(Constants.EXTRAS_DOWNLOAD_MD5, "");
                    // Start the MD5 check of the downloaded file
                    if (MD5.checkMD5(downloadedMD5, updateFile)) {
                        // We passed. Bring the main app to the foreground and
                        // trigger
                        // download completed
                        updateIntent
                                .putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, downID);
                        updateIntent.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH,
                                completedFileFullPath);
                        displaySuccessResult(context, updateIntent, updateFile, flag);
                    } else {
                        // We failed. Clear the file and reset everything
                        DownLoadDao.getInstance().delete(String.valueOf(downID));
                        if (updateFile.exists()) {
                            updateFile.delete();
                        }
                        displayErrorResult(context, updateIntent, R.string.md5_verification_failed);
                    }
                } else if (status == DownLoader.STATUS_ERROR) {
                    // The download failed, reset
                    displayErrorResult(context, updateIntent, R.string.unable_to_download_file);
                }

                // Clear the shared prefs
                prefs.edit().remove(Constants.EXTRAS_DOWNLOAD_ID).remove(Constants.EXTRAS_DOWNLOAD_MD5).apply();
                break;
            default:
                break;
        }
    }

    private void displayErrorResult(Context context, Intent updateIntent, int failureMessageResId) {
        final MoKeeApplication app = (MoKeeApplication) context.getApplicationContext();
        if (app.isMainActivityActive()) {
            Toast.makeText(context, failureMessageResId, Toast.LENGTH_LONG).show();
        } else {
            DownloadNotifier.notifyDownloadError(context, updateIntent, failureMessageResId);
        }
    }

    private void displaySuccessResult(Context context, Intent updateIntent, File updateFile, int flag) {
        final MoKeeApplication app = (MoKeeApplication) context.getApplicationContext();
        if (app.isMainActivityActive()) {
            context.sendBroadcastAsUser(updateIntent, UserHandle.CURRENT_OR_SELF);
        } else {
            DownloadNotifier.notifyDownloadComplete(context, updateIntent, updateFile, flag);
        }
    }
}
