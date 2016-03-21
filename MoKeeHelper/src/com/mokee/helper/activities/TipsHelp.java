/*
 * Copyright (C) 2014-2016 The MoKee Open Source Project
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

package com.mokee.helper.activities;

import android.app.Activity;
import android.content.Intent;
import android.mokee.utils.MoKeeUtils;
import android.net.Uri;
import android.os.Bundle;

public class TipsHelp extends Activity {

    private static final String URL_MOKEE_TIPS_HELP = "http://bbs.mfunz.com/forum-280-1.html";
    private static final String URL_MOKEE_TIPS_HELP_GLOBAL = "http://issues.mokeedev.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = Uri.parse(MoKeeUtils.isSupportLanguage(false) ? URL_MOKEE_TIPS_HELP : URL_MOKEE_TIPS_HELP_GLOBAL);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
        finish();
    }
}
