/*
 * Copyright (C) 2012 The Mokee OpenSource Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.mokee.helper.updater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mokee.helper.updater.service.MKDashClockExtension;

public class CheckFinishReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, MKDashClockExtension.class);
        i.setAction(MKDashClockExtension.ACTION_DATA_UPDATE);
        context.startService(i);
    }
}
