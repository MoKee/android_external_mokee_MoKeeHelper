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

package com.mokee.helper.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.mokee.helper.R;
import com.mokee.helper.UpdateApplication;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.activities.MoKeeUpdater;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.UpdateInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.utils.Utils;

public class UpdateCheckService extends IntentService {
    private static final String TAG = "UpdateCheckService";

    // Set this to true if the update service should check for smaller, test
    // updates
    // This is for internal testing only
    private static final boolean TESTING_DOWNLOAD = false;

    // request actions
    public static final String ACTION_CHECK = "com.mokee.mkupdater.action.CHECK";
    public static final String ACTION_CANCEL_CHECK = "com.mokee.mkupdater.action.CANCEL_CHECK";

    // broadcast actions
    public static final String ACTION_CHECK_FINISHED = "com.mokee.mkupdater.action.UPDATE_CHECK_FINISHED";
    // extra for ACTION_CHECK_FINISHED: total amount of found updates
    public static final String EXTRA_UPDATE_COUNT = "update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that are newer than
    // what is installed
    public static final String EXTRA_REAL_UPDATE_COUNT = "real_update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that were found for
    // the first time
    public static final String EXTRA_NEW_UPDATE_COUNT = "new_update_count";

    // max. number of updates listed in the expanded notification
    private static final int EXPANDED_NOTIF_UPDATE_COUNT = 4;
    private int flag;
    private HttpRequestExecutor mHttpExecutor;

    public UpdateCheckService() {
        super("UpdateCheckService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        flag = intent.getFlags();
        if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_CHECK)) {
            synchronized (this) {
                if (mHttpExecutor != null) {
                    mHttpExecutor.abort();
                }
            }

            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        synchronized (this) {
            mHttpExecutor = new HttpRequestExecutor();
        }

        if (!Utils.isOnline(this)) {
            // Only check for updates if the device is actually connected to a
            // network
            Log.i(TAG,
                    "Could not check for updates. Not connected to the network.");
            return;
        }
        // Start the update check
        Intent finishedIntent = new Intent(ACTION_CHECK_FINISHED);
        // LinkedList<UpdateInfo> availableUpdates;
        LinkedList<UpdateInfo> availableUpdates;
        if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
            try {
                availableUpdates = getMKAvailableUpdatesAndFillIntent(finishedIntent);
            } catch (IOException e) {
                Log.e(TAG, "Could not check for updates", e);
                availableUpdates = null;
            }

            if (availableUpdates == null || mHttpExecutor.isAborted()) {
                sendBroadcast(finishedIntent);
                return;
            }

            // Store the last update check time and ensure boot check completed
            // is
            // true
            Date d = new Date();
            PreferenceManager
                    .getDefaultSharedPreferences(UpdateCheckService.this)
                    .edit()
                    .putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime())
                    .putBoolean(Constants.BOOT_CHECK_COMPLETED, true).apply();

            int realUpdateCount = finishedIntent.getIntExtra(
                    EXTRA_REAL_UPDATE_COUNT, 0);
            UpdateApplication app = (UpdateApplication) getApplicationContext();

            // Write to log
            Log.i(TAG, "The update check successfully completed at " + d
                    + " and found " + availableUpdates.size() + " updates ("
                    + realUpdateCount + " newer than installed)");

            if (realUpdateCount != 0 && !app.isMainActivityActive()) {
                // There are updates available
                // The notification should launch the main app
                Intent i = new Intent();
                i.setAction(MoKeeCenter.ACTION_MOKEE_CENTER);
                i.putExtra(MoKeeUpdater.EXTRA_UPDATE_LIST_UPDATED, true);
                PendingIntent contentIntent = PendingIntent.getActivity(this,
                        0, i, PendingIntent.FLAG_ONE_SHOT);

                Resources res = getResources();
                String text = res.getQuantityString(
                        R.plurals.not_new_updates_found_body, realUpdateCount,
                        realUpdateCount);

                // Get the notification ready
                Notification.Builder builder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_mokee_updater)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(
                                res.getString(R.string.not_new_updates_found_ticker))
                        .setContentTitle(
                                res.getString(R.string.not_new_updates_found_title))
                        .setContentText(text).setContentIntent(contentIntent)
                        .setAutoCancel(true);

                LinkedList<UpdateInfo> realUpdates = new LinkedList<UpdateInfo>();
                // for (UpdateInfo ui : availableUpdates)
                // {
                // if (ui.isNewerThanInstalled())
                // {
                // realUpdates.add(ui);
                // }
                // }
                realUpdates.addAll(availableUpdates);
                // ota暂时不进行排序
                if (!PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean(Constants.PREF_ROM_OTA, true)) {
                    Collections.sort(realUpdates, new Comparator<UpdateInfo>() {
                        @Override
                        public int compare(UpdateInfo lhs, UpdateInfo rhs) {
                            /* sort by date descending */
                            int lhsDate = Integer.valueOf(Utils
                                    .subBuildDate(lhs.getName()));
                            int rhsDate = Integer.valueOf(Utils
                                    .subBuildDate(rhs.getName()));
                            if (lhsDate == rhsDate) {
                                return 0;
                            }
                            return lhsDate < rhsDate ? 1 : -1;
                        }
                    });
                }
                Notification.InboxStyle inbox = new Notification.InboxStyle(
                        builder).setBigContentTitle(text);
                int added = 0, count = realUpdates.size();

                for (UpdateInfo ui : realUpdates) {
                    if (added < EXPANDED_NOTIF_UPDATE_COUNT) {
                        inbox.addLine(ui.getName());
                        added++;
                    }
                }
                if (added != count) {
                    inbox.setSummaryText(res.getQuantityString(
                            R.plurals.not_additional_count, count - added,
                            count - added));
                }
                builder.setStyle(inbox);
                builder.setNumber(availableUpdates.size());

                if (count == 1) {
                    i = new Intent(this, DownloadReceiver.class);
                    i.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
                    i.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO,
                            (Parcelable) realUpdates.getFirst());
                    PendingIntent downloadIntent = PendingIntent.getBroadcast(
                            this, 0, i, PendingIntent.FLAG_ONE_SHOT
                                    | PendingIntent.FLAG_UPDATE_CURRENT);

                    builder.addAction(R.drawable.ic_tab_download,
                            res.getString(R.string.not_action_download),
                            downloadIntent);
                }

                // Trigger the notification
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(R.string.not_new_updates_found_title, builder.build());
            }
        } else if (flag == Constants.INTENT_FLAG_GET_EXPAND) {
            try {
                availableUpdates = getMKAvailableExpandAndFillIntent(finishedIntent);
            } catch (IOException e) {
                Log.e(TAG, "Could not check for updates", e);
                availableUpdates = null;
            }

            if (availableUpdates == null || mHttpExecutor.isAborted()) {
                sendBroadcast(finishedIntent);
                return;
            }

            // Store the last update check time and ensure boot check completed
            // is
            // true
            Date d = new Date();
            PreferenceManager
                    .getDefaultSharedPreferences(UpdateCheckService.this)
                    .edit()
                    .putLong(Constants.PREF_LAST_EXPAND_CHECK, d.getTime())
                    .apply();

            int realUpdateCount = finishedIntent.getIntExtra(
                    EXTRA_REAL_UPDATE_COUNT, 0);
            UpdateApplication app = (UpdateApplication) getApplicationContext();

            // Write to log
            Log.i(TAG, "The update check successfully completed at " + d
                    + " and found " + availableUpdates.size() + " updates ("
                    + realUpdateCount + " newer than installed)");

            if (realUpdateCount != 0 && !app.isMainActivityActive()) {
                // There are updates available
                // The notification should launch the main app
                Intent i = new Intent();
                i.setAction(MoKeeCenter.ACTION_MOKEE_CENTER);
                i.putExtra(MoKeeUpdater.EXTRA_EXPAND_LIST_UPDATED, true);
                PendingIntent contentIntent = PendingIntent.getActivity(this,
                        0, i, PendingIntent.FLAG_ONE_SHOT);
                Resources res = getResources();
                String text = res.getQuantityString(
                        R.plurals.not_new_updates_found_body, realUpdateCount,
                        realUpdateCount);

                // Get the notification ready
                Notification.Builder builder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_mokee_updater)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(
                                res.getString(R.string.not_new_updates_found_ticker))
                        .setContentTitle(
                                res.getString(R.string.not_new_updates_found_title))
                        .setContentText(text).setContentIntent(contentIntent)
                        .setAutoCancel(true);

                LinkedList<UpdateInfo> realUpdates = new LinkedList<UpdateInfo>();
                // for (UpdateInfo ui : availableUpdates)
                // {
                // if (ui.isNewerThanInstalled())
                // {
                // realUpdates.add(ui);
                // }
                // }
                realUpdates.addAll(availableUpdates);
                Notification.InboxStyle inbox = new Notification.InboxStyle(
                        builder).setBigContentTitle(text);
                int added = 0, count = realUpdates.size();
                for (UpdateInfo ui : realUpdates) {
                    if (added < EXPANDED_NOTIF_UPDATE_COUNT) {
                        inbox.addLine(ui.getName());
                        added++;
                    }
                }
                if (added != count) {
                    inbox.setSummaryText(res.getQuantityString(
                            R.plurals.not_additional_count, count - added,
                            count - added));
                }
                builder.setStyle(inbox);
                builder.setNumber(availableUpdates.size());

                if (count == 1) {
                    i = new Intent(this, DownloadReceiver.class);
                    i.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
                    i.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO,
                            (Parcelable) realUpdates.getFirst());
                    PendingIntent downloadIntent = PendingIntent.getBroadcast(
                            this, 0, i, PendingIntent.FLAG_ONE_SHOT
                                    | PendingIntent.FLAG_UPDATE_CURRENT);

                    builder.addAction(R.drawable.ic_tab_download,
                            res.getString(R.string.not_action_download),
                            downloadIntent);
                }
                // Trigger the notification
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(R.string.not_new_updates_found_title, builder.build());
            }
        }
        finishedIntent.putExtra("flag", flag);
        sendBroadcast(finishedIntent);
    }

    private void addRequestHeaders(HttpRequestBase request) {
        String userAgent = Utils.getUserAgentString(this);
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        request.addHeader("Cache-Control", "no-cache");
    }

    /**
     * 获取更新数据
     * 
     * @param intent
     * @return
     * @throws IOException
     */
    private LinkedList<UpdateInfo> getMKAvailableUpdatesAndFillIntent(
            Intent intent) throws IOException {
        // Get the type of update we should check for
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        int updateType = prefs.getInt(Constants.UPDATE_TYPE_PREF, 0);// 版本类型参数
        int rom_all = prefs.getBoolean(Constants.PREF_ROM_ALL, false) ? 1 : 0;// 全部获取参数
        boolean isOTA = prefs.getBoolean(Constants.PREF_ROM_OTA, true);
        // Get the actual ROM Update Server URL
        URI updateServerUri;
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("device_name", Utils.getDeviceType()));// 设备名称
        params.add(new BasicNameValuePair("device_version", Utils
                .getInstalledVersion()));
        if (!isOTA) {
            updateServerUri = URI
                    .create(getString(R.string.conf_update_server_url_def));
            // params.add(new BasicNameValuePair("device_version",
            // "MK43.1-edison-131106-RELEASE.zip"));
            params.add(new BasicNameValuePair("device_officail", String
                    .valueOf(updateType)));
            params.add(new BasicNameValuePair("rom_all", String
                    .valueOf(rom_all)));
        } else {
            updateServerUri = URI
                    .create(getString(R.string.conf_update_ota_server_url_def));
        }
        HttpPost request = new HttpPost(updateServerUri);
        request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
        addRequestHeaders(request);
        HttpEntity entity = mHttpExecutor.execute(request);
        if (entity == null || mHttpExecutor.isAborted()) {
            return null;
        }
        // LinkedList<UpdateInfo> lastUpdates =
        // State.loadMKState(this,State.UPDATE_FILENAME);
        // Read the ROM Infos
        String json = EntityUtils.toString(entity, "UTF-8");
        LinkedList<UpdateInfo> updates = parseMKJSON(json, updateType, isOTA);
        if (mHttpExecutor.isAborted()) {
            return null;
        }
        intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_REAL_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_NEW_UPDATE_COUNT, updates.size());
        intent.putExtra("flag", Constants.INTENT_FLAG_GET_UPDATE);
        State.saveMKState(this, updates, State.UPDATE_FILENAME);

        return updates;
    }

    /**
     * 获取扩展数据
     * 
     * @param intent
     * @return
     * @throws IOException
     */
    private LinkedList<UpdateInfo> getMKAvailableExpandAndFillIntent(
            Intent intent) throws IOException {
        // Get the type of update we should check for
        // Get the actual ROM Update Server URL
        URI updateServerUri;
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        updateServerUri = URI
                .create(getString(R.string.conf_update_expand_server_url_def));
        params.add(new BasicNameValuePair("mk_version", String.valueOf(Utils
                .getInstalledVersion().split("-")[0])));
        HttpPost request = new HttpPost(updateServerUri);
        request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
        addRequestHeaders(request);
        HttpEntity entity = mHttpExecutor.execute(request);
        if (entity == null || mHttpExecutor.isAborted()) {
            return null;
        }
        // LinkedList<UpdateInfo> lastUpdates =
        // State.loadMKState(this,State.EXPAND_FILENAME);
        // Read the ROM Infos
        String json = EntityUtils.toString(entity, "UTF-8");
        LinkedList<UpdateInfo> updates = parseMKExpandJSON(json);
        if (mHttpExecutor.isAborted()) {
            return null;
        }
        intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_REAL_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_NEW_UPDATE_COUNT, updates.size());
        intent.putExtra("flag", Constants.INTENT_FLAG_GET_EXPAND);
        State.saveMKState(this, updates, State.EXPAND_FILENAME);

        return updates;
    }

    /**
     * 判断解析更新数据
     * 
     * @param jsonString
     * @param updateType
     * @param isOTA
     * @return
     */
    private LinkedList<UpdateInfo> parseMKJSON(String jsonString,
            int updateType, boolean isOTA) {
        LinkedList<UpdateInfo> updates = new LinkedList<UpdateInfo>();
        try {
            JSONArray[] jsonArrays = new JSONArray[2];
            // 判断全部
            if (!isOTA && updateType == Constants.UPDATE_TYPE_ALL) {
                JSONObject jsonObject = new JSONObject(jsonString);
                if (jsonObject.has("RELEASE")) {
                    jsonArrays[0] = jsonObject.getJSONArray("RELEASE");
                }
                if (jsonObject.has("NIGHTLY")) {
                    jsonArrays[1] = jsonObject.getJSONArray("NIGHTLY");
                }
            } else {
                JSONArray updateList = new JSONArray(jsonString);
                jsonArrays[0] = updateList;
                int length = updateList.length();
                Log.d(TAG, "Got update JSON data with " + length + " entries");
            }
            for (int i = 0; i < jsonArrays.length; i++) {
                JSONArray jsonArray = jsonArrays[i];
                if (jsonArray != null) {
                    for (int j = 0; j < jsonArray.length(); j++) {
                        if (mHttpExecutor.isAborted()) {
                            break;
                        }
                        if (jsonArray.isNull(j)) {
                            continue;
                        }
                        JSONObject item = jsonArray.getJSONObject(j);
                        UpdateInfo info = parseUpdateMKJSONObject(item,
                                updateType);
                        if (info != null) {
                            updates.add(info);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error in JSON result", e);
        }
        return updates;
    }

    private LinkedList<UpdateInfo> parseMKExpandJSON(String jsonString) {
        LinkedList<UpdateInfo> updates = new LinkedList<UpdateInfo>();
        try {
            JSONArray[] jsonArrays = new JSONArray[2];
            // 判断全部

            JSONObject jsonObject = new JSONObject(jsonString);
            if (jsonObject.has("gms")) {
                jsonArrays[0] = jsonObject.getJSONArray("gms");
            }

            for (int i = 0; i < jsonArrays.length; i++) {
                JSONArray jsonArray = jsonArrays[i];
                if (jsonArray != null) {
                    for (int j = 0; j < jsonArray.length(); j++) {
                        if (mHttpExecutor.isAborted()) {
                            break;
                        }
                        if (jsonArray.isNull(j)) {
                            continue;
                        }
                        JSONObject item = jsonArray.getJSONObject(j);
                        UpdateInfo info = parseExpandMKJSONObject(item);
                        if (info != null) {
                            updates.add(info);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error in JSON result", e);
        }
        return updates;
    }

    /**
     * 解析扩展数据
     * 
     * @param obj
     * @return
     * @throws JSONException
     */
    private UpdateInfo parseExpandMKJSONObject(JSONObject obj)
            throws JSONException {
        String name = obj.getString("name");
        String rom = obj.getString("download");
        String md5 = obj.getString("md5");
        String log = obj.getString("changelog");
        String description = obj.getString("description");
        String checkflag = obj.getString("checkflag");
        UpdateInfo mui = new UpdateInfo(log, md5, name, rom, description,
                checkflag);
        // fetch change log after checking whether to include this build to
        // avoid useless network traffic
        if (!mui.getChangeLogFile(this).exists()) {
            fetchMkChangeLog(mui, mui.getLog());
        }
        return mui;
    }

    private UpdateInfo parseUpdateMKJSONObject(JSONObject obj, int updateType)
            throws JSONException {
        String name = obj.getString("name");
        String rom = obj.getString("rom");
        String md5 = obj.getString("md5");
        String log = obj.getString("log");
        UpdateInfo mui = new UpdateInfo(log, md5, name, rom);
        // fetch change log after checking whether to include this build to
        // avoid useless network traffic
        if (!mui.getChangeLogFile(this).exists()) {
            fetchMkChangeLog(mui, mui.getLog());
        }
        return mui;
    }

    private void fetchMkChangeLog(UpdateInfo info, String url) {
        Log.d(TAG, "Getting change log for " + info + ", url " + url);

        BufferedReader reader = null;
        BufferedWriter writer = null;
        boolean finished = false;

        try {
            HttpGet request = new HttpGet(URI.create(url));
            addRequestHeaders(request);

            HttpEntity entity = mHttpExecutor.execute(request);
            writer = new BufferedWriter(new FileWriter(
                    info.getChangeLogFile(this)));

            if (entity != null) {
                reader = new BufferedReader(new InputStreamReader(
                        entity.getContent()), 2 * 1024);
                boolean categoryMatch = false, hasData = false;
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (mHttpExecutor.isAborted()) {
                        break;
                    }
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (line.startsWith("=")) {
                        categoryMatch = !categoryMatch;
                    } else if (categoryMatch) {
                        if (hasData) {
                            writer.append("<br />");
                        }
                        writer.append("<b><u>");
                        writer.append(line);
                        writer.append("</u></b>");
                        writer.append("<br />");
                        hasData = true;
                    } else if (line.startsWith("*")) {
                        writer.append("<br /><b>");
                        writer.append(line.replaceAll("\\*", ""));
                        writer.append("</b>");
                        writer.append("<br />");
                        hasData = true;
                    } else {
                        writer.append("&#8226;&nbsp;");
                        writer.append(line);
                        writer.append("<br />");
                        hasData = true;
                    }
                }
            } else {
                writer.write("");
            }
            finished = true;
        } catch (IOException e) {
            Log.e(TAG, "Downloading change log for " + info + " failed", e);
            // keeping finished at false will delete the partially written file
            // below
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore, not much we can do anyway
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignore, not much we can do anyway
                }
            }
        }

        if (!finished) {
            info.getChangeLogFile(this).delete();
        }
    }

    private static class HttpRequestExecutor {
        private HttpClient mHttpClient;
        private HttpRequestBase mRequest;
        private boolean mAborted;

        public HttpRequestExecutor() {
            mHttpClient = new DefaultHttpClient();
            mAborted = false;
        }

        public HttpEntity execute(HttpRequestBase request) throws IOException {
            synchronized (this) {
                mAborted = false;
                mRequest = request;
            }

            HttpResponse response = mHttpClient.execute(request);
            HttpEntity entity = null;

            if (!mAborted
                    && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                entity = response.getEntity();
            }

            synchronized (this) {
                mRequest = null;
            }

            return entity;
        }

        public synchronized void abort() {
            if (mRequest != null) {
                mRequest.abort();
            }
            mAborted = true;
        }

        public synchronized boolean isAborted() {
            return mAborted;
        }
    }
}
