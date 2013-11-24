/*
 * Copyright (C) 2012 The Mokee OpenSource Project 
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.mokee.helper.updater;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.mokee.helper.activities.MoKeeCenter;

public class UpdateApplication extends Application implements Application.ActivityLifecycleCallbacks
	{
		private static Context context;
		private boolean mMainActivityActive;

		@Override
		public void onCreate()
			{
				mMainActivityActive = false;
				registerActivityLifecycleCallbacks(this);
				context = getApplicationContext();
			}

		public static Context getContext()
			{
				return context;
			}

		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState)
			{
			}

		@Override
		public void onActivityDestroyed(Activity activity)
			{
			}

		@Override
		public void onActivityPaused(Activity activity)
			{
			}

		@Override
		public void onActivityResumed(Activity activity)
			{
			}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle outState)
			{
			}

		@Override
		public void onActivityStarted(Activity activity)
			{
				if (activity instanceof MoKeeCenter)
					{
						mMainActivityActive = true;
					}
			}

		@Override
		public void onActivityStopped(Activity activity)
			{
				if (activity instanceof MoKeeCenter)
					{
						mMainActivityActive = false;
					}
			}

		public boolean isMainActivityActive()
			{
				return mMainActivityActive;
			}
	}
