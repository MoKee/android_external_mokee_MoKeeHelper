
package com.mokee.helper.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbManager extends SQLiteOpenHelper {
    public DbManager(Context context) {
        super(context, "download.db", null, 1024);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS thread_info(_id integer PRIMARY KEY AUTOINCREMENT, thread_id integer, "
                + "start_pos long, end_pos long, down_size long,url text)");
        db.execSQL("CREATE TABLE IF NOT EXISTS download_info(_id integer PRIMARY KEY AUTOINCREMENT, down_id integer, "
                + "url text,flag integer,local_file text,file_name text,file_size long,state integer)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

}
