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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import android.content.Intent;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeUpdater;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.UpdateInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.utils.Utils;

public class MKDashClockExtension extends DashClockExtension {
    private static final String TAG = "MKDashClockExtension";

    public static final String ACTION_DATA_UPDATE = "com.mokee.mkupdater.action.DASHCLOCK_DATA_UPDATE";

    private static final int MAX_BODY_ITEMS = 3;

    private boolean mInitialized = false;

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        mInitialized = true;
    }

    @Override
    protected void onUpdateData(int reason) {
        LinkedList<UpdateInfo> updates = State.loadMKState(this,
                State.UPDATE_FILENAME);

        Log.d(TAG, "Update dash clock for " + updates.size() + " updates");

        Intent intent = new Intent(this, MoKeeUpdater.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                Constants.PREF_ROM_OTA, true))// ota暂时不进行排序
        {
            Collections.sort(updates, new Comparator<UpdateInfo>() {
                @Override
                public int compare(UpdateInfo lhs, UpdateInfo rhs) {
                    /* sort by date descending */
                    int lhsDate = Integer.valueOf(Utils.subBuildDate(lhs
                            .getName()));
                    int rhsDate = Integer.valueOf(Utils.subBuildDate(rhs
                            .getName()));
                    if (lhsDate == rhsDate) {
                        return 0;
                    }
                    return lhsDate < rhsDate ? 1 : -1;
                }
            });
        }
        final int count = updates.size();
        final Resources res = getResources();
        StringBuilder expandedBody = new StringBuilder();

        for (int i = 0; i < count && i < MAX_BODY_ITEMS; i++) {
            if (expandedBody.length() > 0) {
                expandedBody.append("\n");
            }
            expandedBody.append(updates.get(i).getName());
        }

        // Publish the extension data update.
        publishUpdate(new ExtensionData()
                .visible(!updates.isEmpty())
                .icon(R.drawable.ic_tab_installed)
                .status(res.getQuantityString(R.plurals.extension_status,
                        count, count))
                .expandedTitle(
                        res.getQuantityString(
                                R.plurals.extension_expandedTitle, count, count))
                .expandedBody(expandedBody.toString()).clickIntent(intent));
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
            final int startId) {
        if (TextUtils.equals(intent.getAction(), ACTION_DATA_UPDATE)) {
            if (mInitialized) {
                onUpdateData(UPDATE_REASON_CONTENT_CHANGED);
            }
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }
}
