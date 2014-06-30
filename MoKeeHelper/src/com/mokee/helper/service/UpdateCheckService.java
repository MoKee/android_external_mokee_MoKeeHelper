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

package com.mokee.helper.service;

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

import org.mokee.util.MoKeeUtils;

import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.fragments.MoKeeExtrasFragment;
import com.mokee.helper.fragments.MoKeeUpdaterFragment;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.State;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.utils.HttpRequestExecutor;
import com.mokee.helper.utils.Utils;
import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;

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
import java.util.Locale;

public class UpdateCheckService extends IntentService {
    private static final String TAG = "UpdateCheckService";
    // request actions
    public static final String ACTION_CHECK = "com.mokee.mkupdater.action.CHECK";
    public static final String ACTION_CANCEL_CHECK = "com.mokee.mkupdater.action.CANCEL_CHECK";

    // broadcast actions
    public static final String ACTION_CHECK_FINISHED = "com.mokee.mkupdater.action.UPDATE_CHECK_FINISHED";
    // extra for ACTION_CHECK_FINISHED: total amount of found updates
    public static final String EXTRA_UPDATE_COUNT = "update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that are newer than what is installed
    public static final String EXTRA_REAL_UPDATE_COUNT = "real_update_count";
    // extra for ACTION_CHECK_FINISHED: amount of updates that were found for the first time
    public static final String EXTRA_NEW_UPDATE_COUNT = "new_update_count";

    // add intent extras
    public static final String EXTRA_UPDATE_LIST_UPDATED = "update_list_updated";
    public static final String EXTRA_EXTRAS_LIST_UPDATED = "extras_list_updated";
    public static final String EXTRA_FINISHED_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_FINISHED_DOWNLOAD_PATH = "download_path";
    // max. number of updates listed in the extras notification
    private static final int EXTRAS_NOTIF_UPDATE_COUNT = 4;
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
                    cleanupHttpExecutor(mHttpExecutor);
                    mHttpExecutor = null;
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

        if (!MoKeeUtils.isOnline(this)) {
            // Only check for updates if the device is actually connected to a network
            Log.i(TAG, "Could not check for updates. Not connected to the network.");
            return;
        }

        // Start the update check
        Intent finishedIntent = new Intent(ACTION_CHECK_FINISHED);
        LinkedList<ItemInfo> availableUpdates;
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

            // Store the last update check time and ensure boot check completed is true
            Date d = new Date();
            PreferenceManager.getDefaultSharedPreferences(UpdateCheckService.this).edit()
                    .putLong(Constants.PREF_LAST_UPDATE_CHECK, d.getTime())
                    .putBoolean(Constants.BOOT_CHECK_COMPLETED, true).apply();

            int realUpdateCount = finishedIntent.getIntExtra(EXTRA_REAL_UPDATE_COUNT, 0);
            MoKeeApplication app = (MoKeeApplication) getApplicationContext();

            // Write to log
            Log.i(TAG, "The update check successfully completed at " + d + " and found "
                    + availableUpdates.size() + " updates ("
                    + realUpdateCount + " newer than installed)");

            if (realUpdateCount != 0 && !app.isMainActivityActive()) {
                // There are updates available
                // The notification should launch the main app
                Intent i = new Intent(DownloadReceiver.ACTION_NOTIFICATION_CLICKED);
                i.putExtra(EXTRA_UPDATE_LIST_UPDATED, true);
                i.putExtra("flag", flag);
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_ONE_SHOT);

                Resources res = getResources();
                String text = res.getQuantityString(R.plurals.not_new_updates_found_body,
                        realUpdateCount, realUpdateCount);

                // Get the notification ready
                Notification.Builder builder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_mokee_updater)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(res.getString(R.string.not_new_updates_found_ticker))
                        .setContentTitle(res.getString(R.string.not_new_updates_found_title))
                        .setContentText(text).setContentIntent(contentIntent).setAutoCancel(true);

                LinkedList<ItemInfo> realUpdates = new LinkedList<ItemInfo>();
                realUpdates.addAll(availableUpdates);
                // ota暂时不进行排序
                if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                        Constants.PREF_ROM_OTA, true)) {
                    Collections.sort(realUpdates, new Comparator<ItemInfo>() {
                        @Override
                        public int compare(ItemInfo lhs, ItemInfo rhs) {
                            /* sort by date descending */
                            int lhsDate = Integer.valueOf(Utils.subBuildDate(lhs.getName(), false));
                            int rhsDate = Integer.valueOf(Utils.subBuildDate(rhs.getName(), false));
                            if (lhsDate == rhsDate) {
                                return 0;
                            }
                            return lhsDate < rhsDate ? 1 : -1;
                        }
                    });
                }
                Notification.InboxStyle inbox = new Notification.InboxStyle(builder)
                        .setBigContentTitle(text);
                int added = 0, count = realUpdates.size();

                for (ItemInfo ui : realUpdates) {
                    if (added < EXTRAS_NOTIF_UPDATE_COUNT) {
                        inbox.addLine(ui.getName());
                        added++;
                    }
                }
                if (added != count) {
                    inbox.setSummaryText(res.getQuantityString(R.plurals.not_additional_count,
                            count - added, count - added));
                }
                builder.setStyle(inbox);
                builder.setNumber(availableUpdates.size());

                if (count == 1) {
                    i = new Intent(this, DownloadReceiver.class);
                    i.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
                    i.putExtra("flag", flag);
                    i.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO,
                            (Parcelable) realUpdates.getFirst());
                    PendingIntent downloadIntent = PendingIntent.getBroadcast(this, 0, i,
                            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

                    builder.addAction(R.drawable.ic_tab_download,
                            res.getString(R.string.not_action_download), downloadIntent);
                }

                // Trigger the notification
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(R.string.not_new_updates_found_title, builder.build());
            }
        } else if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
            try {
                availableUpdates = getMKAvailableExtrasAndFillIntent(finishedIntent);
            } catch (IOException e) {
                Log.e(TAG, "Could not check for updates", e);
                availableUpdates = null;
            }

            if (availableUpdates == null || mHttpExecutor.isAborted()) {
                sendBroadcast(finishedIntent);
                return;
            }

            // Store the last update check time and ensure boot check completed is true
            Date d = new Date();
            PreferenceManager.getDefaultSharedPreferences(UpdateCheckService.this).edit()
                    .putLong(Constants.PREF_LAST_EXTRAS_CHECK, d.getTime()).apply();

            int realUpdateCount = finishedIntent.getIntExtra(EXTRA_REAL_UPDATE_COUNT, 0);
            MoKeeApplication app = (MoKeeApplication) getApplicationContext();

            // Write to log
            Log.i(TAG, "The update check successfully completed at " + d + " and found "
                    + availableUpdates.size() + " updates (" + realUpdateCount
                    + " newer than installed)");

            if (realUpdateCount != 0 && !app.isMainActivityActive()) {
                // There are updates available
                // The notification should launch the main app
                Intent i = new Intent(DownloadReceiver.ACTION_NOTIFICATION_CLICKED);
                i.putExtra(EXTRA_EXTRAS_LIST_UPDATED, true);
                i.putExtra("flag", flag);
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_ONE_SHOT);
                Resources res = getResources();
                String text = res.getQuantityString(R.plurals.not_new_updates_found_body,
                        realUpdateCount, realUpdateCount);

                // Get the notification ready
                Notification.Builder builder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_mokee_updater)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(res.getString(R.string.not_new_updates_found_ticker))
                        .setContentTitle(res.getString(R.string.not_new_updates_found_title))
                        .setContentText(text).setContentIntent(contentIntent).setAutoCancel(true);

                LinkedList<ItemInfo> realUpdates = new LinkedList<ItemInfo>();
                realUpdates.addAll(availableUpdates);
                Notification.InboxStyle inbox = new Notification.InboxStyle(builder)
                        .setBigContentTitle(text);
                int added = 0, count = realUpdates.size();
                for (ItemInfo ui : realUpdates) {
                    if (added < EXTRAS_NOTIF_UPDATE_COUNT) {
                        inbox.addLine(ui.getName());
                        added++;
                    }
                }
                if (added != count) {
                    inbox.setSummaryText(res.getQuantityString(R.plurals.not_additional_count,
                            count - added, count - added));
                }
                builder.setStyle(inbox);
                builder.setNumber(availableUpdates.size());

                if (count == 1) {
                    i = new Intent(this, DownloadReceiver.class);
                    i.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
                    i.putExtra("flag", flag);
                    i.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO,
                            (Parcelable) realUpdates.getFirst());
                    PendingIntent downloadIntent = PendingIntent.getBroadcast(this, 0, i,
                            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

                    builder.addAction(R.drawable.ic_tab_download,
                            res.getString(R.string.not_action_download), downloadIntent);
                }
                // Trigger the notification
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(R.string.not_new_updates_found_title, builder.build());
            }
        }
        finishedIntent.putExtra("flag", flag);
        sendBroadcast(finishedIntent);
    }

    private void cleanupHttpExecutor(final HttpRequestExecutor executor) {
        final Thread abortThread = new Thread(new Runnable() {
            @Override
            public void run() {
                executor.abort();
            }
        });
        abortThread.start();
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
    private LinkedList<ItemInfo> getMKAvailableUpdatesAndFillIntent(Intent intent)
            throws IOException {
        // Get the type of update we should check for
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String MoKeeVersionType = Utils.getMoKeeVersionType();
        boolean isExperimental = TextUtils.equals(MoKeeVersionType, "experimental");
        boolean isUnofficial = TextUtils.equals(MoKeeVersionType, "unofficial");
        boolean experimentalShow = prefs.getBoolean(MoKeeUpdaterFragment.EXPERIMENTAL_SHOW, isExperimental);
        int updateType = prefs.getInt(Constants.UPDATE_TYPE_PREF, isUnofficial ? 3
                : isExperimental ? 2 : 0);// 版本类型参数
        if (updateType == 2  && !experimentalShow) {
            prefs.edit().putBoolean(MoKeeUpdaterFragment.EXPERIMENTAL_SHOW, false)
                    .putInt(Constants.UPDATE_TYPE_PREF, 0).apply();
            updateType = 0;
        }
        if (!isUnofficial && updateType == 3) {
            prefs.edit().putInt(Constants.UPDATE_TYPE_PREF, 0).apply();
            updateType = 0;
        }
        int rom_all = prefs.getBoolean(Constants.PREF_ROM_ALL, false) ? 1 : 0;// 全部获取参数
        boolean isOTA = prefs.getBoolean(Constants.PREF_ROM_OTA, true);
        // Get the actual ROM Update Server URL
        URI updateServerUri;
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("device_name", Utils.getDeviceType()));
        params.add(new BasicNameValuePair("device_version", Utils.getInstalledVersion()));
        params.add(new BasicNameValuePair("build_user", Utils.getBuildUser()));
        if (!isOTA) {
            updateServerUri = URI.create(getString(R.string.conf_update_server_url_def));
            // params.add(new BasicNameValuePair("device_version",
            // "MK43.1-edison-131106-RELEASE.zip"));
            params.add(new BasicNameValuePair("device_officail", String.valueOf(updateType)));
            params.add(new BasicNameValuePair("rom_all", String.valueOf(rom_all)));
        } else {
            updateServerUri = URI.create(getString(R.string.conf_update_ota_server_url_def));
        }
        HttpPost request = new HttpPost(updateServerUri);
        request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
        addRequestHeaders(request);
        HttpEntity entity = mHttpExecutor.execute(request);
        if (entity == null || mHttpExecutor.isAborted()) {
            return null;
        }
        // Read the ROM Infos
        String json = EntityUtils.toString(entity, "UTF-8");
        LinkedList<ItemInfo> updates = parseMKJSON(json, updateType, isOTA);
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
    private LinkedList<ItemInfo> getMKAvailableExtrasAndFillIntent(Intent intent)
            throws IOException {
        // Get the type of update we should check for
        // Get the actual ROM Update Server URL
        URI updateServerUri;
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        updateServerUri = URI.create(getString(R.string.conf_update_extras_server_url_def));
        params.add(new BasicNameValuePair("mk_version", String.valueOf(Utils.getInstalledVersion()
                .split("-")[0])));
        HttpPost request = new HttpPost(updateServerUri);
        request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
        Locale mLocale = MoKeeApplication.getContext().getResources().getConfiguration().locale;
        String language = mLocale.getLanguage();
        String country = mLocale.getCountry();
        request.setHeader("Accept-Language", (language + "-" + country).toLowerCase());
        addRequestHeaders(request);
        HttpEntity entity = mHttpExecutor.execute(request);
        if (entity == null || mHttpExecutor.isAborted()) {
            return null;
        }
        // LinkedList<ItemInfo> lastUpdates =
        // State.loadMKState(this,State.EXTRAS_FILENAME);
        // Read the ROM Infos
        String json = EntityUtils.toString(entity, "UTF-8");
        LinkedList<ItemInfo> updates = parseMKExtrasJSON(json);
        if (mHttpExecutor.isAborted()) {
            return null;
        }
        intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_REAL_UPDATE_COUNT, updates.size());
        intent.putExtra(EXTRA_NEW_UPDATE_COUNT, updates.size());
        intent.putExtra("flag", Constants.INTENT_FLAG_GET_EXTRAS);
        State.saveMKState(this, updates, State.EXTRAS_FILENAME);

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
    private LinkedList<ItemInfo> parseMKJSON(String jsonString, int updateType, boolean isOTA) {
        LinkedList<ItemInfo> updates = new LinkedList<ItemInfo>();
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
                        ItemInfo info = parseUpdateMKJSONObject(item, updateType);
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

    private LinkedList<ItemInfo> parseMKExtrasJSON(String jsonString) {
        LinkedList<ItemInfo> updates = new LinkedList<ItemInfo>();
        try {
            JSONArray[] jsonArrays = new JSONArray[2];
            // 判断全部

            JSONObject jsonObject = new JSONObject(jsonString);
            if (jsonObject.has("gms")) {
                jsonArrays[0] = jsonObject.getJSONArray("gms");
            }
            if (jsonObject.has("application")) {
                jsonArrays[1] = jsonObject.getJSONArray("application");
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
                        ItemInfo info = parseExtrasMKJSONObject(item);
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
    private ItemInfo parseExtrasMKJSONObject(JSONObject obj) throws JSONException {
        String name = obj.getString("name");
        String rom = obj.getString("download");
        String md5 = obj.getString("md5");
        String log = obj.getString("changelog");
        String description = obj.getString("description");
        String checkflag = obj.getString("checkflag");
        String length = obj.getString("length");
        ItemInfo mui = new ItemInfo(log, md5, name, rom, description, checkflag, length);
        fetchMKChangeLog(mui, mui.getLog());
        return mui;
    }

    private ItemInfo parseUpdateMKJSONObject(JSONObject obj, int updateType) throws JSONException {
        String name = obj.getString("name");
        String rom = obj.getString("rom");
        String md5 = obj.getString("md5");
        String log = obj.getString("log");
        String length = obj.getString("length");
        ItemInfo mui = new ItemInfo(log, md5, name, rom, length);
        fetchMKChangeLog(mui, mui.getLog());
        return mui;
    }

    private void fetchMKChangeLog(ItemInfo info, String url) {
        Log.d(TAG, "Getting change log for " + info + ", url " + url);

        BufferedReader reader = null;
        BufferedWriter writer = null;
        boolean finished = false;

        try {
            HttpGet request = new HttpGet(URI.create(url));
            addRequestHeaders(request);

            HttpEntity entity = mHttpExecutor.execute(request);
            writer = new BufferedWriter(new FileWriter(info.getChangeLogFile(this)));

            if (entity != null) {
                reader = new BufferedReader(new InputStreamReader(entity.getContent()), 2 * 1024);
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
            // keeping finished at false will delete the partially written file below
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
}
