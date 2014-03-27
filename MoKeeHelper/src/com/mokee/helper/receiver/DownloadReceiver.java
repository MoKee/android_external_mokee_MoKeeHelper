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
import java.lang.reflect.Method;

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
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;
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

    private static final String ACTION_INSTALL_UPDATE = "com.mokee.mkupdater.action.INSTALL_UPDATE";
    private static final String EXTRA_FILENAME = "filename";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (ACTION_START_DOWNLOAD.equals(action)) {
            ItemInfo ui = (ItemInfo) intent.getParcelableExtra(EXTRA_UPDATE_INFO);
            int flag = intent.getIntExtra("flag", 1024);
            handleStartDownload(context, prefs, ui, flag);
        } else if (DownLoadService.ACTION_DOWNLOAD_COMPLETE.equals(action))// 接收下完通知
        {
            long id = intent.getLongExtra(DownLoadService.DOWNLOAD_ID, -1);
            int flag = intent.getIntExtra("flag", 1024);// 标识
            handleDownloadComplete(context, prefs, id, flag);
        } else if (ACTION_INSTALL_UPDATE.equals(action)) {
            Object sbservice = context.getSystemService("statusbar");
            try {
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                Method collapse = statusbarManager.getMethod("collapsePanels");
                collapse.invoke(sbservice);
            } catch (Exception e) {
                Log.e(TAG, "Cannot collapse notification drawer", e);
            }

            String fileName = intent.getStringExtra(EXTRA_FILENAME);
            int flag = intent.getIntExtra("flag", 1024);// 标识
            if(flag ==  Constants.INTENT_FLAG_GET_UPDATE) {
                if (fileName.endsWith(".zip")) {
                    try {
                        Utils.triggerUpdate(context, fileName, true);
                        Utils.cancelNotification(context);
                    } catch (IOException e) {
                        Log.e(TAG, "Unable to reboot into recovery mode", e);
                        Toast.makeText(context, R.string.apply_unable_to_reboot_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            } else if(flag == Constants.INTENT_FLAG_GET_EXTRAS) {
                if (fileName.endsWith(".zip")) {
                    try {
                        Utils.triggerUpdate(context, fileName, false);
                        Utils.cancelNotification(context);
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
                    Toast.makeText(MoKeeApplication.getContext(), "您当前的版本暂时不支持此种扩展", Toast.LENGTH_SHORT)
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
        File updateFile = null;
        int failureMessageResId = -1;
        Intent updateIntent;
        MoKeeApplication app;
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
                updateIntent = new Intent();
                updateIntent.setAction(MoKeeCenter.ACTION_MOKEE_CENTER);
                updateIntent.putExtra("flag", flag);
                updateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                if (status == DownLoader.STATUS_COMPLETE) {
                    // Get the full path name of the downloaded file and the MD5

                    // Strip off the .partial at the end to get the completed
                    // file
                    String partialFileFullPath = dli.getLocalFile();
                    String completedFileFullPath = partialFileFullPath.replace(".partial", "");

                    File partialFile = new File(partialFileFullPath);
                    updateFile = new File(completedFileFullPath);
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
                    } else {
                        // We failed. Clear the file and reset everything
                        DownLoadDao.getInstance().delete(String.valueOf(downID));
                        if (updateFile.exists()) {
                            updateFile.delete();
                        }

                        failureMessageResId = R.string.md5_verification_failed;
                    }
                    //delete thread info
                    ThreadDownLoadDao.getInstance().delete(dli.getUrl());
                } else if (status == DownLoader.STATUS_ERROR) {
                    // The download failed, reset
                    // dm.remove(id);
                    failureMessageResId = R.string.unable_to_download_file;
                }

                // Clear the shared prefs
                prefs.edit().remove(Constants.DOWNLOAD_MD5).remove(Constants.DOWNLOAD_ID).apply();
                app = (MoKeeApplication) context.getApplicationContext();
                if (app.isMainActivityActive()) {
                    if (failureMessageResId >= 0) {
                        Toast.makeText(context, failureMessageResId, Toast.LENGTH_LONG).show();
                    } else {
                        context.startActivity(updateIntent);
                    }
                } else {
                    // Get the notification ready
                    PendingIntent contentIntent = PendingIntent.getActivity(context, 1,
                            updateIntent, PendingIntent.FLAG_ONE_SHOT
                                    | PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification.Builder builder = new Notification.Builder(context)
                            .setSmallIcon(R.drawable.ic_mokee_updater)
                            .setWhen(System.currentTimeMillis()).setContentIntent(contentIntent)
                            .setAutoCancel(true);

                    if (failureMessageResId >= 0) {
                        builder.setContentTitle(context.getString(R.string.not_download_failure));
                        builder.setContentText(context.getString(failureMessageResId));
                        builder.setTicker(context.getString(R.string.not_download_failure));
                    } else {
                        String updateUiName = ItemInfo.extractUiName(updateFile.getName());

                        builder.setContentTitle(context.getString(R.string.not_download_success));
                        builder.setContentText(updateUiName);
                        builder.setTicker(context.getString(R.string.not_download_success));

                        Notification.BigTextStyle style = new Notification.BigTextStyle();
                        style.setBigContentTitle(context.getString(R.string.not_download_success));
                        style.bigText(context.getString(R.string.not_download_install_notice,
                                updateUiName));
                        builder.setStyle(style);

                        Intent installIntent = new Intent(context, DownloadReceiver.class);
                        installIntent.setAction(ACTION_INSTALL_UPDATE);
                        installIntent.putExtra("flag", flag);
                        installIntent.putExtra(EXTRA_FILENAME, updateFile.getName());

                        PendingIntent installPi = PendingIntent.getBroadcast(context, 0,
                                installIntent, PendingIntent.FLAG_ONE_SHOT
                                        | PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.addAction(R.drawable.ic_tab_install,
                                context.getString(R.string.not_action_install_update), installPi);
                    }

                    final NotificationManager nm = (NotificationManager) context
                            .getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(R.string.not_download_success, builder.build());
                }
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
                updateIntent = new Intent();
                updateIntent.setAction(MoKeeCenter.ACTION_MOKEE_CENTER);
                updateIntent.putExtra("flag", flag);
                updateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                if (status == DownLoader.STATUS_COMPLETE) {
                    // Get the full path name of the downloaded file and the MD5

                    // Strip off the .partial at the end to get the completed
                    // file
                    String partialFileFullPath = dli.getLocalFile();
                    completedFileFullPath = partialFileFullPath.replace(".partial", "");

                    File partialFile = new File(partialFileFullPath);
                    updateFile = new File(completedFileFullPath);
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
                    } else {
                        // We failed. Clear the file and reset everything
                        DownLoadDao.getInstance().delete(String.valueOf(downID));

                        if (updateFile.exists()) {
                            updateFile.delete();
                        }

                        failureMessageResId = R.string.md5_verification_failed;
                    }
                } else if (status == DownLoader.STATUS_ERROR) {
                    // The download failed, reset
                    // dm.remove(id);

                    failureMessageResId = R.string.unable_to_download_file;
                }

                // Clear the shared prefs
                prefs.edit().remove(Constants.EXTRAS_DOWNLOAD_ID)
                        .remove(Constants.EXTRAS_DOWNLOAD_MD5).apply();

                app = (MoKeeApplication) context.getApplicationContext();
                if (app.isMainActivityActive()) {
                    if (failureMessageResId >= 0) {
                        Toast.makeText(context, failureMessageResId, Toast.LENGTH_LONG).show();
                    } else {
                        context.startActivity(updateIntent);
                    }
                } else {
                    // Get the notification ready
                    PendingIntent contentIntent = PendingIntent.getActivity(context, 1,
                            updateIntent, PendingIntent.FLAG_ONE_SHOT
                                    | PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification.Builder builder = new Notification.Builder(context)
                            .setSmallIcon(R.drawable.ic_mokee_updater)
                            .setWhen(System.currentTimeMillis()).setContentIntent(contentIntent)
                            .setAutoCancel(true);

                    if (failureMessageResId >= 0) {
                        builder.setContentTitle(context
                                .getString(R.string.not_extras_download_failure));
                        builder.setContentText(context.getString(failureMessageResId));
                        builder.setTicker(context.getString(R.string.not_extras_download_failure));
                    } else {
                        String updateUiName = ItemInfo.extractUiName(updateFile.getName());

                        builder.setContentTitle(context
                                .getString(R.string.not_extras_download_success));
                        builder.setContentText(updateUiName);
                        builder.setTicker(context.getString(R.string.not_extras_download_success));
                        if (completedFileFullPath.endsWith(".zip")) {
                            Notification.BigTextStyle style = new Notification.BigTextStyle();
                            style.setBigContentTitle(context
                                    .getString(R.string.not_extras_download_success));
                            style.bigText(context.getString(
                                    R.string.not_extras_download_install_notice, updateUiName));
                            builder.setStyle(style);

                            Intent installIntent = new Intent(context, DownloadReceiver.class);
                            installIntent.setAction(ACTION_INSTALL_UPDATE);
                            installIntent.putExtra("flag", flag);
                            installIntent.putExtra(EXTRA_FILENAME, updateFile.getName());

                            PendingIntent installPi = PendingIntent.getBroadcast(context, 0,
                                    installIntent, PendingIntent.FLAG_ONE_SHOT
                                            | PendingIntent.FLAG_UPDATE_CURRENT);
                            builder.addAction(R.drawable.ic_tab_install,
                                    context.getString(R.string.not_action_install_update),
                                    installPi);
                        } else if (completedFileFullPath.endsWith(".apk")) {
                            Notification.BigTextStyle style = new Notification.BigTextStyle();
                            style.setBigContentTitle(context
                                    .getString(R.string.not_extras_download_success));
                            style.bigText(context.getString(
                                    R.string.not_extras_download_install_notice_apk, updateUiName));
                            builder.setStyle(style);

                            Intent installIntent = new Intent(context, DownloadReceiver.class);
                            installIntent.setAction(ACTION_INSTALL_UPDATE);
                            installIntent.putExtra("flag", flag);
                            installIntent.putExtra(EXTRA_FILENAME, updateFile.getName());

                            PendingIntent installPi = PendingIntent.getBroadcast(context, 0,
                                    installIntent, PendingIntent.FLAG_ONE_SHOT
                                            | PendingIntent.FLAG_UPDATE_CURRENT);
                            builder.addAction(R.drawable.ic_tab_install,
                                    context.getString(R.string.not_action_install_update_apk),
                                    installPi);
                        } else {// 待扩展

                        }

                    }

                    final NotificationManager nm = (NotificationManager) context
                            .getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(R.string.not_extras_download_success, builder.build());
                }
                break;
            default:
                break;
        }

    }
}
