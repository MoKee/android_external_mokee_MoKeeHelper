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

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.mokee.utils.MoKeeUtils;
import android.os.Parcelable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.ItemInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.receiver.DownloadReceiver;
import com.mokee.helper.requests.ExtrasRequest;
import com.mokee.helper.requests.UpdatesRequest;
import com.mokee.helper.utils.Utils;

public class UpdateCheckService extends IntentService
        implements Response.ErrorListener, Listener<String> {

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

    // DefaultRetryPolicy values for Volley
    private static final int UPDATE_REQUEST_TIMEOUT = 5000; // 5 seconds
    private static final int UPDATE_REQUEST_MAX_RETRIES = 3;

    public UpdateCheckService() {
        super("UpdateCheckService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_CHECK)) {
            ((MoKeeApplication) getApplicationContext()).getQueue().cancelAll(TAG);
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!MoKeeUtils.isOnline(this)) {
            // Only check for updates if the device is actually connected to a network
            Log.i(TAG, "Could not check for updates. Not connected to the network.");
            return;
        }
        flag = intent.getIntExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_UPDATE);
        getAvailableUpdates(flag);
    }

    private void recordAvailableUpdates(LinkedList<ItemInfo> availableUpdates,
            Intent finishedIntent) {
        String lastUpdateCheckPref = null, updateListUpdatedPref = null;
        if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
            lastUpdateCheckPref = Constants.LAST_UPDATE_CHECK_PREF;
            updateListUpdatedPref = EXTRA_UPDATE_LIST_UPDATED;
        } else if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
            lastUpdateCheckPref = Constants.LAST_EXTRAS_CHECK_PREF;
            updateListUpdatedPref = EXTRA_EXTRAS_LIST_UPDATED;
        }
        if (availableUpdates == null) {
            sendBroadcastAsUser(finishedIntent, UserHandle.CURRENT);
            return;
        }

        // Store the last update check time and ensure boot check completed is true
        Date d = new Date();
        if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
            getSharedPreferences(Constants.DOWNLOADER_PREF, 0).edit()
                    .putLong(lastUpdateCheckPref, d.getTime())
                    .putBoolean(Constants.BOOT_CHECK_COMPLETED, true).apply();
        } else if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
            getSharedPreferences(Constants.DOWNLOADER_PREF, 0).edit()
                    .putLong(lastUpdateCheckPref, d.getTime()).apply();
        }

        int realUpdateCount = finishedIntent.getIntExtra(EXTRA_REAL_UPDATE_COUNT, 0);
        MoKeeApplication app = (MoKeeApplication) getApplicationContext();

        // Write to log
        Log.i(TAG, "The update check successfully completed at " + d + " and found "
                + availableUpdates.size() + " updates ("
                + realUpdateCount + " newer than installed)");

        if (realUpdateCount != 0 && !app.isMainActivityActive()) {
            // There are updates available
            // The notification should launch the main app
            Intent i = new Intent(MoKeeCenter.ACTION_MOKEE_CENTER);
            i.putExtra(updateListUpdatedPref, true);
            i.putExtra(DownLoadService.DOWNLOAD_FLAG, flag);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_ONE_SHOT);

            Resources res = getResources();
            String text = res.getQuantityString(R.plurals.not_new_updates_found_body,
                    realUpdateCount, realUpdateCount);

            // Get the notification ready
            Notification.Builder builder = new Notification.Builder(this)
                    .setColor(getResources().getColor(com.android.internal.R.color.system_notification_accent_color))
                    .setSmallIcon(R.drawable.ic_mokee_updater)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(res.getString(R.string.not_new_updates_found_ticker))
                    .setContentTitle(res.getString(R.string.not_new_updates_found_title))
                    .setContentText(text).setContentIntent(contentIntent).setAutoCancel(true);

            LinkedList<ItemInfo> realUpdates = new LinkedList<ItemInfo>();
            realUpdates.addAll(availableUpdates);
            if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
                // ota暂时不进行排序
                if (!getSharedPreferences(Constants.DOWNLOADER_PREF, 0).getBoolean(Constants.OTA_CHECK_PREF, false)) {
                    Collections.sort(realUpdates, new Comparator<ItemInfo>() {
                        @Override
                        public int compare(ItemInfo lhs, ItemInfo rhs) {
                            /* sort by date descending */
                            int lhsDate = Integer.valueOf(Utils.subBuildDate(lhs.getFileName(),
                                    false));
                            int rhsDate = Integer.valueOf(Utils.subBuildDate(rhs.getFileName(),
                                    false));
                            if (lhsDate == rhsDate) {
                                return 0;
                            }
                            return lhsDate < rhsDate ? 1 : -1;
                        }
                    });
                }
            }
            Notification.InboxStyle inbox = new Notification.InboxStyle(builder)
                    .setBigContentTitle(text);
            int added = 0, count = realUpdates.size();

            for (ItemInfo ui : realUpdates) {
                if (added < EXTRAS_NOTIF_UPDATE_COUNT) {
                    inbox.addLine(ui.getFileName());
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
                i.putExtra(DownLoadService.DOWNLOAD_FLAG, flag);
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
        finishedIntent.putExtra(DownLoadService.DOWNLOAD_FLAG, flag);
        sendBroadcastAsUser(finishedIntent, UserHandle.CURRENT);
    }

    /**
     * 获取更新数据
     */
    private void getAvailableUpdates(int flag) {
        // Get the actual ROM Update Server URL
        URI updateServerUri = null;
        switch (flag) {
            case Constants.INTENT_FLAG_GET_UPDATE:
                boolean isOTA = getSharedPreferences(Constants.DOWNLOADER_PREF, 0).getBoolean(Constants.OTA_CHECK_PREF, false);
                if (!isOTA) {
                    updateServerUri = URI.create(getString(R.string.conf_update_server_url_def));
                } else {
                    updateServerUri = URI.create(getString(R.string.conf_update_ota_server_url_def));
                }
                UpdatesRequest updateRequest = new UpdatesRequest(Request.Method.POST,
                        updateServerUri.toASCIIString(), Utils.getUserAgentString(this), this, this);
                // Improve request error tolerance 
                updateRequest.setRetryPolicy(new DefaultRetryPolicy(UPDATE_REQUEST_TIMEOUT,
                        UPDATE_REQUEST_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                // Set the tag for the request, reuse logging tag
                updateRequest.setTag(TAG);
                ((MoKeeApplication) getApplicationContext()).getQueue().add(updateRequest);
                break;
            case Constants.INTENT_FLAG_GET_EXTRAS:
                updateServerUri = URI.create(getString(R.string.conf_update_extras_server_url_def));
                ExtrasRequest extrasRequest = new ExtrasRequest(Request.Method.POST,
                        updateServerUri.toASCIIString(), Utils.getUserAgentString(this), this, this);
                // Improve request error tolerance 
                extrasRequest.setRetryPolicy(new DefaultRetryPolicy(UPDATE_REQUEST_TIMEOUT,
                        UPDATE_REQUEST_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                // Set the tag for the request, reuse logging tag
                extrasRequest.setTag(TAG);
                ((MoKeeApplication) getApplicationContext()).getQueue().add(extrasRequest);
                break;
        }
    }

    /**
     * 判断解析更新数据
     * 
     * @param jsonString
     * @param updateType
     * @param isOTA
     * @return
     */
    private LinkedList<ItemInfo> parseUpdatesJSONObject(String jsonString, int updateType) {
        LinkedList<ItemInfo> updates = new LinkedList<ItemInfo>();

        boolean isOTA = getSharedPreferences(Constants.DOWNLOADER_PREF, 0).getBoolean(Constants.OTA_CHECK_PREF, false);
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
                        if (jsonArray.isNull(j)) {
                            continue;
                        }
                        JSONObject item = jsonArray.getJSONObject(j);
                        ItemInfo info = parseUpdatesJSON(item);
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

    private ItemInfo parseUpdatesJSON(JSONObject obj) throws JSONException {
        ItemInfo mii = new ItemInfo.Builder()
                .setFileName(obj.getString("name"))
                .setFileSize(obj.getString("length"))
                .setDownloadUrl(obj.getString("rom"))
                .setMD5Sum(obj.getString("md5"))
                .setChangelog(obj.getString("log")).build();
        return mii;
    }

    private LinkedList<ItemInfo> parseExtrasJSONObject(String jsonString) {
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
                        if (jsonArray.isNull(j)) {
                            continue;
                        }
                        JSONObject item = jsonArray.getJSONObject(j);
                        ItemInfo info = parseExtrasJSON(item);
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

    private ItemInfo parseExtrasJSON(JSONObject obj) throws JSONException {
        ItemInfo mii = new ItemInfo.Builder()
                .setFileName(obj.getString("name"))
                .setFileSize(obj.getString("length"))
                .setDownloadUrl(obj.getString("download"))
                .setMD5Sum(obj.getString("md5"))
                .setChangelog(obj.getString("changelog"))
                .setDescription(obj.getString("description"))
                .setCheckflag(obj.getString("checkflag")).build();
        return mii;
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        VolleyLog.e("Error: ", volleyError.getMessage());
        VolleyLog.e("Error type: " + volleyError.toString());
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        sendBroadcast(intent);
    }

    @Override
    public void onResponse(String response) {
        int updateType = getSharedPreferences(Constants.DOWNLOADER_PREF, 0).getInt(Constants.UPDATE_TYPE_PREF, 0);
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        LinkedList<ItemInfo> updates = null;
        switch (flag) {
            case Constants.INTENT_FLAG_GET_UPDATE:
                updates = parseUpdatesJSONObject(response, updateType);
                intent.putExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_UPDATE);
                intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
                intent.putExtra(EXTRA_REAL_UPDATE_COUNT, updates.size());
                intent.putExtra(EXTRA_NEW_UPDATE_COUNT, updates.size());
                recordAvailableUpdates(updates, intent);
                State.saveMKState(this, updates, State.UPDATE_FILENAME);
                break;
            case Constants.INTENT_FLAG_GET_EXTRAS:
                updates = parseExtrasJSONObject(response);
                intent.putExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_EXTRAS);
                intent.putExtra(EXTRA_UPDATE_COUNT, updates.size());
                intent.putExtra(EXTRA_REAL_UPDATE_COUNT, updates.size());
                intent.putExtra(EXTRA_NEW_UPDATE_COUNT, updates.size());
                recordAvailableUpdates(updates, intent);
                State.saveMKState(this, updates, State.EXTRAS_FILENAME);
                break;
        }
    }
}
