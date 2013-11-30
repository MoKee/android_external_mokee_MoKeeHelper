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

package com.mokee.helper.misc;

import java.io.File;
import java.io.Serializable;

import com.mokee.helper.utils.Utils;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public class UpdateInfo implements Parcelable, Serializable
{

    private static final long serialVersionUID = 1L;
    public String log;
    public String md5;
    public String name;
    public String rom;
    // expand
    public String description;
    public String checkflag;

    public UpdateInfo(String log, String md5, String name, String rom)
    {
        super();
        this.log = log;
        this.md5 = md5;
        this.name = name;
        this.rom = rom;
    }

    public UpdateInfo(String log, String md5, String name, String rom, String description,
            String checkflag)
    {
        super();
        this.log = log;
        this.md5 = md5;
        this.name = name;
        this.rom = rom;
        this.description = description;
        this.checkflag = checkflag;
    }

    public UpdateInfo(String name)
    {
        this(null, null, name, null);
        initializeName(name);
    }

    public String getLog()
    {
        return log;
    }

    public String getMd5()
    {
        return md5;
    }

    public String getName()
    {
        return name;
    }

    public String getRom()
    {
        return rom;
    }

    public File getChangeLogFile(Context context)
    {
        return new File(context.getCacheDir(), name + ".html");
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public static final Parcelable.Creator<UpdateInfo> CREATOR = new Parcelable.Creator<UpdateInfo>()
    {
        public UpdateInfo createFromParcel(Parcel in)
        {
            return new UpdateInfo(in);
        }

        public UpdateInfo[] newArray(int size)
        {
            return new UpdateInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(log);
        dest.writeString(name);
        dest.writeString(md5);
        dest.writeString(rom);
        dest.writeString(description);
        dest.writeString(checkflag);
    }

    private void readFromParcel(Parcel in)
    {
        log = in.readString();
        name = in.readString();
        md5 = in.readString();
        rom = in.readString();
        description = in.readString();
        checkflag = in.readString();
    }

    private void initializeName(String fileName)
    {
        name = fileName;
        if (!TextUtils.isEmpty(fileName))
        {
            name = extractUiName(fileName);
        } else
        {
            name = null;
        }
    }

    public static String extractUiName(String fileName)
    {
        String deviceType = Utils.getDeviceType();
        String uiName = fileName.replaceAll("\\.zip$", "");
        return uiName.replaceAll("-" + deviceType + "-?", "");
    }

    private UpdateInfo(Parcel in)
    {
        readFromParcel(in);
    }

    public String getDescription()
    {
        return description;
    }

    public String getCheckflag()
    {
        return checkflag;
    }

}
