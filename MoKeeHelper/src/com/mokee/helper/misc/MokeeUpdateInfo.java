package com.mokee.helper.misc;

import java.io.File;
import java.io.Serializable;

import com.mokee.helper.utils.Utils;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * mokeeUpdateInfoDomain
 * 
 * @author wszfer
 * 
 */
public class MokeeUpdateInfo implements Parcelable, Serializable
	{
		/**
	 * 
	 */
		private static final long serialVersionUID = 1L;
		public String log;
		public String md5;
		public String name;
		public String rom;

		public MokeeUpdateInfo(String log, String md5, String name, String rom)
			{
				super();
				this.log = log;
				this.md5 = md5;
				this.name = name;
				this.rom = rom;
			}
		public MokeeUpdateInfo(String name)
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
				return new File(context.getCacheDir(), name + ".changelog");
			}

		@Override
		public int describeContents()
			{
				// TODO Auto-generated method stub
				return 0;
			}
		 public static final Parcelable.Creator<MokeeUpdateInfo> CREATOR = new Parcelable.Creator<MokeeUpdateInfo>() {
		        public MokeeUpdateInfo createFromParcel(Parcel in) {
		            return new MokeeUpdateInfo(in);
		        }

		        public MokeeUpdateInfo[] newArray(int size) {
		            return new MokeeUpdateInfo[size];
		        }
		    };
		@Override
		public void writeToParcel(Parcel dest, int flags)
			{
				dest.writeString(log);
				dest.writeString(name);
				dest.writeString(md5);
				dest.writeString(rom);
			}

		private void readFromParcel(Parcel in)
			{
				log = in.readString();
				name = in.readString();
				md5 = in.readString();
				rom = in.readString();
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
		 private MokeeUpdateInfo(Parcel in) {
		        readFromParcel(in);
		    }
	}
