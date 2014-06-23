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

package com.mokee.helper.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.mokee.helper.R;
import com.mokee.helper.activities.MoKeeCenter;

public class MoKeeSupportFragment extends PreferenceFragment {

    private static final int MENU_DONATE = 0;

    private static final String KEY_MOKEE_WEBSITE = "mokee_website";
    private static final String KEY_MOKEE_FORUM = "mokee_forum";
    private static final String KEY_MOKEE_ISSUES = "mokee_issues";
    private static final String KEY_MOKEE_STATISTICS = "mokee_statistics";
    private static final String KEY_MOKEE_CHANGELOG = "mokee_changelog";
    private static final String KEY_MOKEE_NIGHTLY_STATUS = "mokee_nightly_status";
    private static final String KEY_MOKEE_GITHUB = "mokee_github";
    private static final String KEY_MOKEE_WIKI = "mokee_wiki";
    private static final String KEY_MOKEE_DONATE = "mokee_donate";

    private static final String URL_MOKEE_WEBSITE = "http://www.mokeedev.com";
    private static final String URL_MOKEE_FORUM = "http://bbs.mfunz.com";
    private static final String URL_MOKEE_ISSUES = "http://issues.mokeedev.com";
    private static final String URL_MOKEE_STATISTICS = "http://stats.mokeedev.com";
    private static final String URL_MOKEE_CHANGELOG = "http://changelog.mokeedev.com";
    private static final String URL_MOKEE_NIGHTLY_STATUS = "http://build.mokeedev.com";
    private static final String URL_MOKEE_GITHUB = "https://github.com/MoKee";
    private static final String URL_MOKEE_WIKI = "http://wiki.mfunz.com";
    private static final String URL_MOKEE_DONATE = "http://www.mokeedev.com/donate/";
    
    private Activity mContext;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mContext = getActivity();
        addPreferencesFromResource(R.xml.mokee_support);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (key.equals(KEY_MOKEE_WEBSITE)) {
            goToURL(URL_MOKEE_WEBSITE);
        } else if (key.equals(KEY_MOKEE_FORUM)) {
            goToURL(URL_MOKEE_FORUM);
        } else if (key.equals(KEY_MOKEE_ISSUES)) {
            goToURL(URL_MOKEE_ISSUES);
        } else if (key.equals(KEY_MOKEE_STATISTICS)) {
            goToURL(URL_MOKEE_STATISTICS);
        } else if (key.equals(KEY_MOKEE_CHANGELOG)) {
            goToURL(URL_MOKEE_CHANGELOG);
        } else if (key.equals(KEY_MOKEE_NIGHTLY_STATUS)) {
            goToURL(URL_MOKEE_NIGHTLY_STATUS);
        } else if (key.equals(KEY_MOKEE_GITHUB)) {
            goToURL(URL_MOKEE_GITHUB);
        } else if (key.equals(KEY_MOKEE_WIKI)) {
            goToURL(URL_MOKEE_WIKI);
        } else if (key.equals(KEY_MOKEE_DONATE)) {
            goToURL(URL_MOKEE_DONATE);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void goToURL(String url) {
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_DONATE, 0 ,R.string.menu_donate).setShowAsActionFlags(
                MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DONATE:
                MoKeeCenter.donateButton(mContext);
                return true;
            case android.R.id.home:
                mContext.onBackPressed();
                return true;
        }
        return true;
    }

}
