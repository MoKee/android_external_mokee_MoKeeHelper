/*
 * Copyright (C) 2014-2015 The MoKee OpenSource Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.mokee.helper.R;
import com.mokee.helper.adapters.TabsAdapter;
import com.mokee.helper.fragments.MoKeeExtrasFragment;
import com.mokee.helper.fragments.MoKeeSupportFragment;
import com.mokee.helper.fragments.MoKeeUpdaterFragment;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.service.DownLoadService;
import com.mokee.helper.service.UpdateCheckService;
import com.mokee.helper.utils.Utils;

public class MoKeeCenter extends FragmentActivity {

    public static final String ACTION_MOKEE_CENTER = "com.mokee.mkupdater.action.MOKEE_CENTER";
    public static final String KEY_MOKEE_SERVICE = "key_mokee_service";
    public static final String KEY_MOKEE_UPDATER = "key_mokee_updater";
    public static final String BR_ONNewIntent = "onNewIntent";

    private ActionBar bar;
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mViewPager = (ViewPager) findViewById(R.id.viewPager);
        bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);
        bar.setTitle(R.string.mokee_center_title);

        mTabsAdapter = new TabsAdapter(this, mViewPager);
        mTabsAdapter.addTab(bar.newTab().setText(R.string.mokee_extras_title), MoKeeExtrasFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText(R.string.mokee_updater_title), MoKeeUpdaterFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText(R.string.mokee_support_title), MoKeeSupportFragment.class, null);
        if (savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 1));
        }
        bar.setSelectedNavigationItem(1);
        // Turn on the Options Menu
        invalidateOptionsMenu();

        SharedPreferences prefs = getSharedPreferences(Constants.DONATION_PREF, 0);
        if (prefs.getBoolean(Constants.DONATION_BLOCKED_PREF, false)) {
            showAdBlockedAlert();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null) {
            int flag = intent.getIntExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_UPDATE);
            if (flag == Constants.INTENT_FLAG_GET_EXTRAS) {
                bar.setSelectedNavigationItem(0);
            } else if (flag == Constants.INTENT_FLAG_GET_UPDATE) {
                bar.setSelectedNavigationItem(1);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }

    @Override
    protected void onNewIntent(Intent intent) {

        // super.onNewIntent(intent);
        Intent send = new Intent(BR_ONNewIntent);
        send.putExtra(UpdateCheckService.EXTRA_UPDATE_LIST_UPDATED, intent.getBooleanExtra(UpdateCheckService.EXTRA_UPDATE_LIST_UPDATED, false));
        send.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, intent.getLongExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_ID, -1));
        send.putExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH, intent.getStringExtra(UpdateCheckService.EXTRA_FINISHED_DOWNLOAD_PATH));
        send.putExtra(DownLoadService.DOWNLOAD_FLAG, intent.getIntExtra(DownLoadService.DOWNLOAD_FLAG, Constants.INTENT_FLAG_GET_UPDATE));
        sendBroadcastAsUser(send, UserHandle.CURRENT);
    }

    public static void donateOrRemoveAdsButton(final Activity mContext, final boolean isDonate) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout donateView = (LinearLayout)inflater.inflate(R.layout.donate, null);
        final TextView mRequest = (TextView) donateView.findViewById(R.id.request);
        SeekBar mSeekBar = (SeekBar) donateView.findViewById(R.id.price);
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBar.setProgress(progress / 10 * 10);
                mRequest.setText(String.valueOf(progress / 10 * 10 + 10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }});
        ProgressBar mProgressBar = (ProgressBar) donateView.findViewById(R.id.progress);
        int paid = Utils.getPaidTotal(mContext);
        final int unPaid = Constants.DONATION_TOTAL - paid;
        if (isDonate) {
            mSeekBar.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
            mRequest.setText("10");
        } else {
            mSeekBar.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setMax(Constants.DONATION_TOTAL);
            mProgressBar.setProgress(paid);
            mRequest.setText(String.format(mContext.getString(R.string.remove_ads_request_price), paid, unPaid));
        }

        DialogInterface.OnClickListener mDialogButton = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String price = isDonate ? mRequest.getText().toString() : String.valueOf(which == DialogInterface.BUTTON_POSITIVE ? unPaid / 6 : unPaid);
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        sendPaymentRequest(mContext, "paypal", mContext.getString(isDonate ? R.string.donate_money_name : R.string.remove_ads_name), mContext.getString(isDonate ? R.string.donate_money_description : R.string.remove_ads_description), price);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        sendPaymentRequest(mContext, "alipay", mContext.getString(isDonate ? R.string.donate_money_name : R.string.remove_ads_name), mContext.getString(isDonate ? R.string.donate_money_description : R.string.remove_ads_description), price);
                        break;
                }
            }
        };

        new AlertDialog.Builder(mContext)
                .setTitle(isDonate ? R.string.donate_dialog_title : R.string.remove_ads_dialog_title)
                .setMessage(isDonate ? R.string.donate_dialog_message : R.string.remove_ads_dialog_message)
                .setView(donateView)
                .setPositiveButton(R.string.donate_dialog_via_paypal, mDialogButton)
                .setNegativeButton(R.string.donate_dialog_via_alipay, mDialogButton).show();
    }

    private static void sendPaymentRequest (Activity mContext, String channel, String name, String description, String price) {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName("com.mokee.pay", "com.mokee.pay.payment.MoKeePaymentActivity");
        intent.setComponent(componentName);
        intent.putExtra("packagename", Utils.getPackageName(mContext));
        intent.putExtra("channel", channel);
        intent.putExtra("type", "donation");
        intent.putExtra("name", name);
        intent.putExtra("description", description);
        intent.putExtra("price", price);
        mContext.startActivity(intent);
    }

    public void showAdBlockedAlert() {
        new AlertDialog.Builder(this).setCancelable(false).setMessage(R.string.ads_blocked_dialog_message).setPositiveButton(android.R.string.ok, null).show();
    }

}
