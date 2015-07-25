/*
 * Copyright (C) 2015 The MoKee OpenSource Project
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

package com.mokee.helper.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.mokee.utils.MoKeeUtils;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.ads.Ad;
import com.google.ads.AdListener;
import com.google.ads.AdRequest;
import com.google.ads.AdRequest.ErrorCode;
import com.google.ads.AdView;
import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.R;
import com.mokee.helper.misc.Constants;

public class AdmobPreference extends Preference implements AdListener {

    private static AdView adView;
    private static View admobCustomView;
    private SharedPreferences prefs;
    private Context mContext;

    public AdmobPreference(Context context) {
        super(context);
        mContext = context;
    }

    public AdmobPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public AdmobPreference(Context context, AttributeSet ui, int style) {
        super(context, ui, style);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        if (admobCustomView == null) {
            prefs = mContext.getSharedPreferences(Constants.DONATION_PREF, 0);
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            admobCustomView = inflater.inflate(R.layout.preference_admob, null);
            adView = (AdView) admobCustomView.findViewById(R.id.adView);
            adView.loadAd(new AdRequest());
            adView.setAdListener(this);
        }
        return admobCustomView;
    }

    @Override
    public void onDismissScreen(Ad ad) {
    }

    @Override
    public void onFailedToReceiveAd(Ad ad, ErrorCode errorCode) {
        if (errorCode.equals(ErrorCode.INTERNAL_ERROR) || errorCode.equals(ErrorCode.NETWORK_ERROR)) {
            if (MoKeeUtils.isOnline(MoKeeApplication.getContext())) {
                prefs.edit().putBoolean(Constants.DONATION_BLOCKED_PREF, true).apply();
            }
        }
    }



    @Override
    public void onLeaveApplication(Ad ad) {
    }

    @Override
    public void onPresentScreen(Ad ad) {
    }

    @Override
    public void onReceiveAd(Ad ad) {
        if (prefs.getBoolean(Constants.DONATION_BLOCKED_PREF, false)) {
            prefs.edit().putBoolean(Constants.DONATION_BLOCKED_PREF, false).apply();
        }
    }

}
