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

package com.mokee.helper.fragments;

import com.mokee.helper.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

public class MoKeeSupportFragment extends PreferenceFragment
{

    private static final String KEY_MOKEE_WEBSITE = "mokee_website";
    private static final String KEY_MOKEE_FORUM = "mokee_forum";
    private static final String KEY_MOKEE_ISSUES = "mokee_issues";
    private static final String KEY_MOKEE_STATISTICS = "mokee_statistics";
    private static final String KEY_MOKEE_REVIEW = "mokee_review";
    private static final String KEY_MOKEE_GITHUB = "mokee_github";
    private static final String KEY_MOKEE_CONTRIBUTOR = "mokee_contributor";
    private static final String KEY_MOKEE_WIKI = "mokee_wiki";

    private static final String URL_MOKEE_WEBSITE = "http://www.mfunz.com";
    private static final String URL_MOKEE_FORUM = "http://bbs.mfunz.com";
    private static final String URL_MOKEE_ISSUES = "http://issues.mfunz.com";
    private static final String URL_MOKEE_STATISTICS = "http://stats.mfunz.com";
    private static final String URL_MOKEE_REVIEW = "http://review.mfunz.com";
    private static final String URL_MOKEE_GITHUB = "https://github.com/MoKee";
    private static final String URL_MOKEE_CONTRIBUTOR = "http://www.mfunz.com/contributors/";
    private static final String URL_MOKEE_WIKI = "http://wiki.mfunz.com";

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.mokee_service);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        String key = preference.getKey();
        if (key.equals(KEY_MOKEE_WEBSITE))
        {
            goToURL(URL_MOKEE_WEBSITE);
        } else if (key.equals(KEY_MOKEE_FORUM))
        {
            goToURL(URL_MOKEE_FORUM);
        } else if (key.equals(KEY_MOKEE_ISSUES))
        {
            goToURL(URL_MOKEE_ISSUES);
        } else if (key.equals(KEY_MOKEE_STATISTICS))
        {
            goToURL(URL_MOKEE_STATISTICS);
        } else if (key.equals(KEY_MOKEE_REVIEW))
        {
            goToURL(URL_MOKEE_REVIEW);
        } else if (key.equals(KEY_MOKEE_GITHUB))
        {
            goToURL(URL_MOKEE_GITHUB);
        } else if (key.equals(KEY_MOKEE_CONTRIBUTOR))
        {
            goToURL(URL_MOKEE_CONTRIBUTOR);
        } else if (key.equals(KEY_MOKEE_WIKI))
        {
            goToURL(URL_MOKEE_WIKI);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void goToURL(String url)
    {
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }
}
