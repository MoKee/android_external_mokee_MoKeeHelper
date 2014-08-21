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

package com.mokee.helper.misc;

public class Constants {

    // Download related
    public static final String UPDATES_FOLDER = "mkupdater";
    public static final String EXTRAS_FOLDER = "mkextras";

    // Preferences
    public static final String DOWNLOADER_PREF = "downloader";
    public static final String ENABLE_PREF = "pref_enable_updates";
    public static final String BACKUP_PREF = "pref_backup_rom";
    public static final String UPDATE_CHECK_PREF = "pref_update_check_interval";
    public static final String UPDATE_TYPE_PREF = "pref_update_types";
    public static final String CHECK_ALL_PREF = "pref_check_all";
    public static final String CHECK_OTA_PREF = "pref_check_ota";
    public static final String EXPANG_LIST_PREF = "extras_category";
    public static final String LAST_UPDATE_CHECK_PREF = "pref_last_update_check";
    public static final String LAST_EXTRAS_CHECK_PREF = "pref_last_extras_check";

    // Update Check items
    public static final String BOOT_CHECK_COMPLETED = "boot_check_completed";
    public static final int UPDATE_FREQ_AT_BOOT = -1;
    public static final int UPDATE_FREQ_NONE = -2;
    public static final int UPDATE_FREQ_TWICE_DAILY = 43200;
    public static final int UPDATE_FREQ_DAILY = 86400;
    public static final int UPDATE_FREQ_TWICE_WEEKLY = 302400;
    public static final int UPDATE_FREQ_WEEKLY = 604800;
    public static final int UPDATE_FREQ_BI_WEEKLY = 1209600;
    public static final int UPDATE_FREQ_MONTHLY = 2419200;

    // Update types
    public static final int UPDATE_TYPE_RELEASE = 0;
    public static final int UPDATE_TYPE_NIGHTLY = 1;
    public static final int UPDATE_TYPE_EXPERIMENTAL = 2;
    public static final int UPDATE_TYPE_UNOFFICIAL = 3;
    public static final int UPDATE_TYPE_ALL = 4;

    // intentFlag
    public static final int INTENT_FLAG_GET_UPDATE = 1024;
    public static final int INTENT_FLAG_GET_EXTRAS = 1025;

}
