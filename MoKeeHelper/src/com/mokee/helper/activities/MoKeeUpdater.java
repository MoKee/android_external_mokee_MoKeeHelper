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

package com.mokee.helper.activities;

import com.mokee.helper.R;
import com.mokee.helper.misc.Constants;
import com.mokee.helper.misc.ExtraInfo;
import com.mokee.helper.misc.MokeeUpdateInfo;
import com.mokee.helper.misc.State;
import com.mokee.helper.updater.UpdateApplication;
import com.mokee.helper.updater.UpdatePreference;
import com.mokee.helper.updater.receiver.DownloadReceiver;
import com.mokee.helper.updater.service.UpdateCheckService;
import com.mokee.helper.utils.UpdateFilter;
import com.mokee.helper.utils.Utils;
import com.mokee.helper.widget.ExtraPreference;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;

public class MoKeeUpdater extends PreferenceFragment implements OnPreferenceChangeListener, UpdatePreference.OnReadyListener,
		UpdatePreference.OnActionListener
	{
		private static String TAG = "MoKeeUpdater";

		private static final String KEY_MOKEE_VERSION = "mokee_version";
		private static final String KEY_MOKEE_VERSION_TYPE = "mokee_version_type";
		private static final String KEY_MOKEE_EXTRAS = "mokee_extras_title";

		private static final String GOOGLE_MOBILE_SERVICE_PACKAGE_NAME = "com.google.android.gms";

		private PreferenceCategory mExtrasList;
		private ExtraPreference mExtraPreference;

		private DownloadManager mDownloadManager;
		private boolean mDownloading = false;
		private long mDownloadId;
		private String mFileName;

		// Building Demo
		public static PreferenceScreen mTmpEntry;
		private static final String KEY_TMP_ENTRY = "tmp_entry";

		private Context mContext;

		private File mExtraFolder;

		private boolean mStartUpdateVisible = false;
		// add
		// intent extras
		public static final String EXTRA_UPDATE_LIST_UPDATED = "update_list_updated";
		public static final String EXTRA_FINISHED_DOWNLOAD_ID = "download_id";
		public static final String EXTRA_FINISHED_DOWNLOAD_PATH = "download_path";

		private static final String UPDATES_CATEGORY = "updates_category";

		private static final int MENU_REFRESH = 0;
		private static final int MENU_DELETE_ALL = 1;
		private static final int MENU_SYSTEM_INFO = 2;
		private SharedPreferences mPrefs;
		private CheckBoxPreference mUpdateAll, mUpdateOTA;
		private ListPreference mUpdateCheck;
		private ListPreference mUpdateType;

		private PreferenceCategory mUpdatesList;
		private UpdatePreference mDownloadingPreference;
		private File mUpdateFolder;
		private ProgressDialog mProgressDialog;
		private Handler mUpdateHandler = new Handler();

		private BroadcastReceiver mReceiver = new BroadcastReceiver()
			{
				@Override
				public void onReceive(Context context, Intent intent)
					{
						String action = intent.getAction();

						if (DownloadReceiver.ACTION_DOWNLOAD_STARTED.equals(action))
							{
								mDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
								mUpdateHandler.post(mUpdateProgress);
							} else if (UpdateCheckService.ACTION_CHECK_FINISHED.equals(action))
							{
								if (mProgressDialog != null)
									{
										mProgressDialog.dismiss();
										mProgressDialog = null;

										int count = intent.getIntExtra(UpdateCheckService.EXTRA_NEW_UPDATE_COUNT, -1);
										if (count == 0)
											{
												Toast.makeText(getActivity(), R.string.no_updates_found, Toast.LENGTH_SHORT)
														.show();
											} else if (count < 0)
											{
												Toast.makeText(getActivity(), R.string.update_check_failed, Toast.LENGTH_LONG)
														.show();
											}
									}
								updateLayout();
							} else if (MoKeeCenter.BR_ONNewIntent.equals(action))// 唤醒
							{
								if (intent.getBooleanExtra(MoKeeUpdater.EXTRA_UPDATE_LIST_UPDATED, false))
									{
										updateLayout();
									}
								checkForDownloadCompleted(intent);
							}
					}
			};

		@Override
		public void onCreate(Bundle savedInstanceState)
			{
				super.onCreate(savedInstanceState);
				mContext = getActivity();
				mDownloadManager = (DownloadManager) getActivity().getSystemService(getActivity().DOWNLOAD_SERVICE);
				// Load the layouts
				addPreferencesFromResource(R.xml.mokee_updater);
				mUpdatesList = (PreferenceCategory) findPreference(UPDATES_CATEGORY);
				mUpdateCheck = (ListPreference) findPreference(Constants.UPDATE_CHECK_PREF);
				mUpdateType = (ListPreference) findPreference(Constants.UPDATE_TYPE_PREF);
				mUpdateAll = (CheckBoxPreference) findPreference(Constants.PREF_ROM_ALL);// 所有更新
				mUpdateOTA = (CheckBoxPreference) findPreference(Constants.PREF_ROM_OTA);// OTA更新
				// Load the stored preference data
				mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
				if (mUpdateCheck != null)
					{
						int check = mPrefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);
						mUpdateCheck.setValue(String.valueOf(check));
						mUpdateCheck.setSummary(mapCheckValue(check));
						mUpdateCheck.setOnPreferenceChangeListener(this);
					}

				if (mUpdateType != null)
					{
						int type = mPrefs.getInt(Constants.UPDATE_TYPE_PREF, 0);
						mUpdateType.setValue(String.valueOf(type));
						mUpdateType.setSummary(mUpdateType.getEntries()[type]);
						mUpdateType.setOnPreferenceChangeListener(this);
					}
				mUpdateOTA.setChecked(mPrefs.getBoolean(Constants.PREF_ROM_OTA, true));
				mUpdateAll.setChecked(mPrefs.getBoolean(Constants.PREF_ROM_ALL, false));
				mUpdateAll.setOnPreferenceChangeListener(this);
				mUpdateOTA.setOnPreferenceChangeListener(this);
				isOTA(mUpdateOTA.isChecked());
				isRomALl(mUpdateAll.isChecked());
				setValueSummary(KEY_MOKEE_VERSION, "ro.mk.version");
				setStringSummary(KEY_MOKEE_VERSION_TYPE, getMoKeeVersionType());

				mExtrasList = (PreferenceCategory) findPreference(KEY_MOKEE_EXTRAS);
				mTmpEntry = (PreferenceScreen) findPreference(KEY_TMP_ENTRY);
				/*
				 * TODO: add this back once we have a way of doing backups that is not recovery specific mBackupRom =
				 * (CheckBoxPreference) findPreference(Constants.BACKUP_PREF);
				 * mBackupRom.setChecked(mPrefs.getBoolean(Constants.BACKUP_PREF, true));
				 */

				// Set 'HomeAsUp' feature of the actionbar to fit better into Settings
				final ActionBar bar = getActivity().getActionBar();
				bar.setDisplayHomeAsUpEnabled(true);
				this.setHasOptionsMenu(true);
				// Turn on the Options Menu
			}

		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
			{
				menu.add(0, MENU_REFRESH, 0, R.string.menu_refresh).setIcon(R.drawable.ic_menu_refresh)
						.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

				menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all).setShowAsActionFlags(
						MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

				menu.add(0, MENU_SYSTEM_INFO, 0, R.string.menu_system_info).setShowAsActionFlags(
						MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
				super.onCreateOptionsMenu(menu, inflater);
			}

		@Override
		public boolean onOptionsItemSelected(MenuItem item)
			{
				switch (item.getItemId())
					{
					case MENU_REFRESH:
						checkForUpdates();
						return true;

					case MENU_DELETE_ALL:
						confirmDeleteAll();
						return true;

					case MENU_SYSTEM_INFO:
						showSysInfo();
						return true;

					case android.R.id.home:
						getActivity().onBackPressed();
						return true;
					}
				return true;
			}

		@Override
		public void onReady(UpdatePreference pref)
			{
				pref.setOnReadyListener(null);
				mUpdateHandler.post(mUpdateProgress);
			}

		private String getMoKeeVersionType()
			{
				String MoKeeVersion = Utils.getInstalledVersion();
				String MoKeeVersionType;
				if (MoKeeVersion.equals(getString(R.string.mokee_info_default)))
					{
						return MoKeeVersion;
					} else
					{
						MoKeeVersionType = Utils.getMoKeeVersionTypeString(MoKeeVersion, mContext);
					}
				return MoKeeVersionType;
			}

		private void setStringSummary(String preference, String value)
			{
				try
					{
						findPreference(preference).setSummary(value);
					} catch (RuntimeException e)
					{
						findPreference(preference).setSummary(getString(R.string.mokee_info_default));
					}
			}

		private void setValueSummary(String preference, String property)
			{
				try
					{
						findPreference(preference).setSummary(
								SystemProperties.get(property, getString(R.string.mokee_info_default)));
					} catch (RuntimeException e)
					{
						// No recovery
					}
			}

		// add
		private Runnable mUpdateProgress = new Runnable()
			{
				public void run()
					{
						if (!mDownloading || mDownloadingPreference == null || mDownloadId < 0)
							{
								return;
							}

						ProgressBar progressBar = mDownloadingPreference.getProgressBar();
						if (progressBar == null)
							{
								return;
							}

						DownloadManager.Query q = new DownloadManager.Query();
						q.setFilterById(mDownloadId);

						Cursor cursor = mDownloadManager.query(q);
						int status;

						if (cursor == null || !cursor.moveToFirst())
							{
								// DownloadReceiver has likely already removed the download
								// from the DB due to failure or MD5 mismatch
								status = DownloadManager.STATUS_FAILED;
							} else
							{
								status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
							}

						switch (status)
							{
							case DownloadManager.STATUS_PENDING:
								progressBar.setIndeterminate(true);
								break;
							case DownloadManager.STATUS_PAUSED:
							case DownloadManager.STATUS_RUNNING:
								int downloadedBytes = cursor.getInt(cursor
										.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
								int totalBytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

								if (totalBytes < 0)
									{
										progressBar.setIndeterminate(true);
									} else
									{
										progressBar.setIndeterminate(false);
										progressBar.setMax(totalBytes);
										progressBar.setProgress(downloadedBytes);
									}
								break;
							case DownloadManager.STATUS_FAILED:
								mDownloadingPreference.setStyle(UpdatePreference.STYLE_NEW);
								resetDownloadState();
								break;
							}
						if (cursor != null)
							{
								cursor.close();
							}
						if (status != DownloadManager.STATUS_FAILED)
							{
								mUpdateHandler.postDelayed(this, 1000);
							}
					}
			};

		private void resetDownloadState()
			{
				mDownloadId = -1;
				mFileName = null;
				mDownloading = false;
				mDownloadingPreference = null;
			}

		private void updateLayout()
			{
				// Read existing Updates
				LinkedList<String> existingFiles = new LinkedList<String>();

				mUpdateFolder = Utils.makeUpdateFolder();
				File[] files = mUpdateFolder.listFiles(new UpdateFilter(".zip"));
				if (mUpdateFolder.exists() && mUpdateFolder.isDirectory() && files != null)
					{
						for (File file : files)
							{
								if (file.isFile())
									{
										existingFiles.add(file.getName());
									}
							}
					}
				// Clear the notification if one exists
				Utils.cancelNotification(UpdateApplication.getContext());

				// Build list of updates
				final LinkedList<MokeeUpdateInfo> availableUpdates = State.loadMKState(UpdateApplication.getContext());
//				final LinkedList<MokeeUpdateInfo> updates = new LinkedList<MokeeUpdateInfo>();

//				 for (String fileName : existingFiles)
//				 {
//				 updates.add(new UpdateInfo(fileName));
//				 }
//				for (MokeeUpdateInfo update : availableUpdates)
//					{
//						// Only add updates to the list that are not already downloaded
////						if (existingFiles.contains(update.name))
////							{
////								continue;
////							}
//						updates.add(update);
//					}
				// Collections.sort(updates, new Comparator<UpdateInfo>()
				// {
				// @Override
				// public int compare(UpdateInfo lhs, UpdateInfo rhs)
				// {
				// // sort in descending 'UI name' order (newest first)
				// return -lhs.getName().compareTo(rhs.getName());
				// }
				// });
				if(!mPrefs.getBoolean(Constants.PREF_ROM_OTA, true))
						{
							Collections.sort(availableUpdates, new Comparator<MokeeUpdateInfo>()
										{
											@Override
											public int compare(MokeeUpdateInfo lhs, MokeeUpdateInfo rhs)
												{
													/* sort by date descending */
													int lhsDate = Integer.valueOf(Utils.subBuildDate(lhs.getName()));
													int rhsDate = Integer.valueOf(Utils.subBuildDate(rhs.getName()));
													if (lhsDate == rhsDate)
														{
															return 0;
														}
													return lhsDate < rhsDate ? 1 : -1;
												}
										});
						}
				// Update the preference list
				refreshPreferences(availableUpdates);

				// Prune obsolete change log files
				new Thread()
					{
						@Override
						public void run()
							{
								File[] files = getActivity().getCacheDir().listFiles(new UpdateFilter(".changelog"));
								if (files == null)
									{
										return;
									}

								for (File file : files)
									{
										boolean updateExists = false;
										for (MokeeUpdateInfo info : availableUpdates)
											{
												if (file.getName().startsWith(info.name))
													{
														updateExists = true;
														break;
													}
											}
										if (!updateExists)
											{
												file.delete();
											}
									}
							}
					}.start();
			}

		private void refreshPreferences(LinkedList<MokeeUpdateInfo> updates)
			{
				System.out.println("refreshPreferences:"+updates.size());
				if (mUpdatesList == null)
					{
						return;
					}
				// Clear the list
				mUpdatesList.removeAll();
				// Convert the installed version name to the associated filename
				String installedZip =Utils.getInstalledVersion() + ".zip";
				boolean isNew=true;//判断新旧版本
				int nowDate=Integer.valueOf(Utils.subBuildDate(installedZip));
				boolean isRomAll=mPrefs.getBoolean(Constants.PREF_ROM_ALL, true);
				// Add the updates
				for (MokeeUpdateInfo ui : updates)
					{
						// Determine the preference style and create the preference
						boolean isDownloading = ui.getName().equals(mFileName);
						boolean isLocalFile=Utils.isLocaUpdateFile(ui.getName());
						int style=3;
						if(isRomAll&&!mPrefs.getBoolean(Constants.PREF_ROM_OTA, true))
							{
								int itemDate=Integer.valueOf(Utils.subBuildDate(ui.getName()));
								isNew=itemDate>nowDate?true:false;
							}
						if (isDownloading)
							{
								// In progress download
								style = UpdatePreference.STYLE_DOWNLOADING;
							} else if (ui.getName().equals(installedZip))
							{
								// This is the currently installed version
								style = UpdatePreference.STYLE_INSTALLED;
							} else if (!isLocalFile&&isNew)
							{
								style = UpdatePreference.STYLE_NEW;
							} else if(!isLocalFile&&!isNew)	
							{
								style = UpdatePreference.STYLE_OLD;
							}
							else if(isLocalFile)
							{
								style = UpdatePreference.STYLE_DOWNLOADED;
							}
						UpdatePreference up = new UpdatePreference(getActivity(), ui, style);
						up.setOnActionListener(this);
						up.setKey(ui.getName());

						// If we have an in progress download, link the preference
						if (isDownloading)
							{
								mDownloadingPreference = up;
								up.setOnReadyListener(this);
								mDownloading = true;
							}
						// Add to the list
						mUpdatesList.addPreference(up);
					}
				// If no updates are in the list, show the default message
				if (mUpdatesList.getPreferenceCount() == 0)
					{
						Preference pref = new Preference(getActivity());
						pref.setLayoutResource(R.layout.preference_empty_list);
						pref.setTitle(R.string.no_available_updates_intro);
						pref.setEnabled(false);
						mUpdatesList.addPreference(pref);
					}
			}

		private String mapCheckValue(Integer value)
			{
				Resources resources = getResources();
				String[] checkNames = resources.getStringArray(R.array.update_check_entries);
				String[] checkValues = resources.getStringArray(R.array.update_check_values);
				for (int i = 0; i < checkValues.length; i++)
					{
						if (Integer.decode(checkValues[i]).equals(value))
							{
								return checkNames[i];
							}
					}
				return getString(R.string.unknown);
			}

		private void isOTA(boolean value)
			{
				if (value)
					{
						mUpdateType.setEnabled(false);
					} else
					{
						mUpdateType.setEnabled(true);
					}
			}

		private void checkForUpdates()
			{
				if (mProgressDialog != null)
					{
						return;
					}

				// If there is no internet connection, display a message and return.
				if (!Utils.isOnline(mContext))
					{
						Toast.makeText(mContext, R.string.data_connection_required, Toast.LENGTH_SHORT).show();
						return;
					}

				mProgressDialog = new ProgressDialog(mContext);
				mProgressDialog.setTitle(R.string.checking_for_updates);
				mProgressDialog.setMessage(getString(R.string.checking_for_updates));
				mProgressDialog.setIndeterminate(true);
				mProgressDialog.setCancelable(true);
				mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener()
					{
						@Override
						public void onCancel(DialogInterface dialog)
							{
								Intent cancelIntent = new Intent(mContext, UpdateCheckService.class);
								cancelIntent.setAction(UpdateCheckService.ACTION_CANCEL_CHECK);
								UpdateApplication.getContext().startService(cancelIntent);
								mProgressDialog = null;
							}
					});

				Intent checkIntent = new Intent(mContext, UpdateCheckService.class);
				checkIntent.setAction(UpdateCheckService.ACTION_CHECK);
				UpdateApplication.getContext().startService(checkIntent);

				mProgressDialog.show();
			}

		private void confirmDeleteAll()
			{
				new AlertDialog.Builder(mContext).setTitle(R.string.confirm_delete_dialog_title)
						.setMessage(R.string.confirm_delete_all_dialog_message)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
									{
										// We are OK to delete, trigger it
										deleteOldUpdates();
										updateLayout();
									}
							}).setNegativeButton(R.string.dialog_cancel, null).show();
			}

		private boolean deleteOldUpdates()
			{
				boolean success;
				// mUpdateFolder: Foldername with fullpath of SDCARD
				if (mUpdateFolder.exists() && mUpdateFolder.isDirectory())
					{
						deleteDir(mUpdateFolder);
						mUpdateFolder.mkdir();
						success = true;
						Toast.makeText(mContext, R.string.delete_updates_success_message, Toast.LENGTH_SHORT).show();
					} else if (!mUpdateFolder.exists())
					{
						success = false;
						Toast.makeText(mContext, R.string.delete_updates_noFolder_message, Toast.LENGTH_SHORT).show();
					} else
					{
						success = false;
						Toast.makeText(mContext, R.string.delete_updates_failure_message, Toast.LENGTH_SHORT).show();
					}
				return success;
			}

		private static boolean deleteDir(File dir)
			{
				if (dir.isDirectory())
					{
						String[] children = dir.list();
						for (String aChildren : children)
							{
								boolean success = deleteDir(new File(dir, aChildren));
								if (!success)
									{
										return false;
									}
							}
					}
				// The directory is now empty so delete it
				return dir.delete();
			}

		private void showSysInfo()
			{
				// Build the message
				Date lastCheck = new Date(mPrefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0));
				String date = DateFormat.getLongDateFormat(mContext).format(lastCheck);
				String time = DateFormat.getTimeFormat(mContext).format(lastCheck);

				String message = getString(R.string.sysinfo_device) + " " + Utils.getDeviceType() + "\n\n"
						+ getString(R.string.sysinfo_running) + " " + Utils.getInstalledVersion() + "\n\n"
						+ getString(R.string.sysinfo_last_check) + " " + date + " " + time;

				AlertDialog.Builder builder = new AlertDialog.Builder(mContext).setTitle(R.string.menu_system_info)
						.setMessage(message).setPositiveButton(R.string.dialog_ok, null);

				AlertDialog dialog = builder.create();
				dialog.show();

				TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
				messageView.setTextAppearance(mContext, android.R.style.TextAppearance_DeviceDefault_Small);
			}

		private void updateUpdatesType(int type)
			{
				mPrefs.edit().putInt(Constants.UPDATE_TYPE_PREF, type).apply();
				mUpdateType.setValue(String.valueOf(type));
				mUpdateType.setSummary(mUpdateType.getEntries()[type]);
				checkForUpdates();
			}

		private void checkForDownloadCompleted(Intent intent)
			{
				if (intent == null)
					{
						return;
					}

				long downloadId = intent.getLongExtra(EXTRA_FINISHED_DOWNLOAD_ID, -1);
				if (downloadId < 0)
					{
						return;
					}

				String fullPathName = intent.getStringExtra(EXTRA_FINISHED_DOWNLOAD_PATH);
				if (fullPathName == null)
					{
						return;
					}

				String fileName = new File(fullPathName).getName();

				// Find the matching preference so we can retrieve the UpdateInfo
				UpdatePreference pref = (UpdatePreference) mUpdatesList.findPreference(fileName);
				if (pref != null)
					{
						pref.setStyle(UpdatePreference.STYLE_DOWNLOADED);//download over Change
						onStartUpdate(pref);
					}

				resetDownloadState();
			}

		@Override
		public void onStart()
			{
				super.onStart();

				// Determine if there are any in-progress downloads
				mDownloadId = mPrefs.getLong(Constants.DOWNLOAD_ID, -1);
				if (mDownloadId >= 0)
					{
						Cursor c = mDownloadManager.query(new DownloadManager.Query().setFilterById(mDownloadId));
						if (c == null || !c.moveToFirst())
							{
								Toast.makeText(mContext, R.string.download_not_found, Toast.LENGTH_LONG).show();
							} else
							{
								int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
								Uri uri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI)));
								if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
										|| status == DownloadManager.STATUS_PAUSED)
									{
										String localFileName=c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
										if(!TextUtils.isEmpty(localFileName))
											{
												mFileName =localFileName.substring(localFileName.lastIndexOf("/")+1, localFileName.lastIndexOf("."));	
											}
									}
							}
						if (c != null)
							{
								c.close();
							}
					}
				if (mDownloadId < 0 || mFileName == null)
					{
						resetDownloadState();
					}

				updateLayout();

				IntentFilter filter = new IntentFilter(UpdateCheckService.ACTION_CHECK_FINISHED);
				filter.addAction(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
				filter.addAction(MoKeeCenter.BR_ONNewIntent);// 唤醒
				mContext.registerReceiver(mReceiver, filter);

				checkForDownloadCompleted(getActivity().getIntent());
				getActivity().setIntent(null);
			}

		@Override
		public void onStop()
			{
				super.onStop();
				mUpdateHandler.removeCallbacks(mUpdateProgress);
				mContext.unregisterReceiver(mReceiver);
				if (mProgressDialog != null)
					{
						mProgressDialog.cancel();
						mProgressDialog = null;
					}
			}

		@Override
		public void onStartDownload(UpdatePreference pref)
			{
				// If there is no internet connection, display a message and return.
				if (!Utils.isOnline(mContext))
					{
						Toast.makeText(mContext, R.string.data_connection_required, Toast.LENGTH_SHORT).show();
						return;
					}

				if (mDownloading)
					{
						Toast.makeText(mContext, R.string.download_already_running, Toast.LENGTH_LONG).show();
						return;
					}

				// We have a match, get ready to trigger the download
				mDownloadingPreference = pref;

				MokeeUpdateInfo ui = mDownloadingPreference.getUpdateInfo();
				if (ui == null)
					{
						return;
					}

				mDownloadingPreference.setStyle(UpdatePreference.STYLE_DOWNLOADING);
				mFileName = ui.getName();
				mDownloading = true;

				// Start the download
				Intent intent = new Intent(mContext, DownloadReceiver.class);
				intent.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
				intent.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO, (Parcelable) ui);
				mContext.sendBroadcast(intent);

				mUpdateHandler.post(mUpdateProgress);
			}

		@Override
		public void onStopDownload(final UpdatePreference pref)
			{
				if (!mDownloading || mFileName == null || mDownloadId < 0)
					{
						pref.setStyle(UpdatePreference.STYLE_NEW);
						resetDownloadState();
						return;
					}

				new AlertDialog.Builder(mContext).setTitle(R.string.confirm_download_cancelation_dialog_title)
						.setMessage(R.string.confirm_download_cancelation_dialog_message)
						.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
									{
										// Set the preference back to new style
										pref.setStyle(UpdatePreference.STYLE_NEW);

										// We are OK to stop download, trigger it
										mDownloadManager.remove(mDownloadId);
										mUpdateHandler.removeCallbacks(mUpdateProgress);
										resetDownloadState();

										// Clear the stored data from shared preferences
										mPrefs.edit().remove(Constants.DOWNLOAD_ID).remove(Constants.DOWNLOAD_MD5).apply();

										Toast.makeText(mContext, R.string.download_cancelled, Toast.LENGTH_SHORT).show();
									}
							}).setNegativeButton(R.string.dialog_cancel, null).show();
			}

		@Override
		public void onStartUpdate(UpdatePreference pref)
			{
				final MokeeUpdateInfo updateInfo = pref.getUpdateInfo();

				// Prevent the dialog from being triggered more than once
				if (mStartUpdateVisible)
					{
						return;
					}

				mStartUpdateVisible = true;

				// Get the message body right
				String dialogBody = getString(R.string.apply_update_dialog_text, updateInfo.getName());

				// Display the dialog
				new AlertDialog.Builder(mContext).setTitle(R.string.apply_update_dialog_title).setMessage(dialogBody)
						.setPositiveButton(R.string.dialog_update, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
									{
										try
											{
												Utils.triggerUpdate(mContext, updateInfo.getName());
											} catch (IOException e)
											{
												Log.e(TAG, "Unable to reboot into recovery mode", e);
												Toast.makeText(mContext, R.string.apply_unable_to_reboot_toast,
														Toast.LENGTH_SHORT).show();
											}
									}
							}).setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
									{
										mStartUpdateVisible = false;
									}
							}).show();
			}

		@Override
		public void onDeleteUpdate(UpdatePreference pref)
			{
				final String fileName = pref.getKey();

				if (mUpdateFolder.exists() && mUpdateFolder.isDirectory())
					{
						File zipFileToDelete = new File(mUpdateFolder, fileName);

						if (zipFileToDelete.exists())
							{
								zipFileToDelete.delete();
							} else
							{
								Log.d(TAG, "Update to delete not found");
								return;
							}

						String message = getString(R.string.delete_single_update_success_message, fileName);
						Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
					} else if (!mUpdateFolder.exists())
					{
						Toast.makeText(mContext, R.string.delete_updates_noFolder_message, Toast.LENGTH_SHORT).show();
					} else
					{
						Toast.makeText(mContext, R.string.delete_updates_failure_message, Toast.LENGTH_SHORT).show();
					}

				// Update the list
				updateLayout();
			}

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				if (preference == mUpdateCheck)
					{
						int value = Integer.valueOf((String) newValue);
						mPrefs.edit().putInt(Constants.UPDATE_CHECK_PREF, value).apply();
						mUpdateCheck.setSummary(mapCheckValue(value));
						Utils.scheduleUpdateService(mContext, value * 1000);
						return true;
					} else if (preference == mUpdateType)
					{
						final int value = Integer.valueOf((String) newValue);
						if (value == Constants.UPDATE_TYPE_NIGHTLY || value == Constants.UPDATE_TYPE_BETA
								|| value == Constants.UPDATE_TYPE_ALL)
							{
								new AlertDialog.Builder(mContext).setTitle(R.string.nightly_alert_title)
										.setMessage(R.string.nightly_alert)
										.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener()
											{
												public void onClick(DialogInterface dialog, int id)
													{
														updateUpdatesType(value);
													}
											}).setNegativeButton(R.string.dialog_cancel, null).show();
								return false;
							} else
							{
								updateUpdatesType(value);
							}
						return true;
					} else if (preference == mUpdateAll)
					{
						boolean value = (Boolean) newValue;
						isRomALl(value);
						return true;
					} else if (preference == mUpdateOTA)
					{
						boolean value = (Boolean) newValue;
						isOTA(value);
						checkForUpdates();
						return true;
					}

				return false;
			}
		private void isRomALl(boolean value)
			{
				if (value)
					{
						mUpdateAll.setSummary(mContext.getResources().getText(R.string.pref_update_all_summary));
					} else
					{
						mUpdateAll.setSummary(mContext.getResources().getText(R.string.pref_update_all_new_summary));
					}
			}
	}
