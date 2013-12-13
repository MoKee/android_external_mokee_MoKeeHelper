
package com.mokee.helper.db;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.misc.ThreadDownLoadInfo;

/**
 * 线程记录
 * 
 * @author wszfer
 */
public class ThreadDownLoadDao {
    private static ThreadDownLoadDao threadDownLoadDao = null;
    private Context context;

    private ThreadDownLoadDao(Context context) {
        this.context = context;
    }

    public static ThreadDownLoadDao getInstance() {
        if (threadDownLoadDao == null) {
            threadDownLoadDao = new ThreadDownLoadDao(MoKeeApplication.getContext());
        }
        return threadDownLoadDao;
    }

    public SQLiteDatabase getConnection() {
        SQLiteDatabase sqliteDatabase = null;
        try {
            sqliteDatabase = new DbManager(context).getWritableDatabase();
        } catch (Exception e) {
        }
        return sqliteDatabase;
    }

    /**
     * 查看数据库中是否有数据
     */
    public synchronized boolean isHasInfos(String fileUrl) {
        SQLiteDatabase database = getConnection();
        int count = -1;
        Cursor cursor = null;
        try {
            String sql = "select count(*)  from thread_info where url=?";
            cursor = database.rawQuery(sql, new String[] {
                fileUrl
            });
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != database) {
                database.close();
            }
            if (null != cursor) {
                cursor.close();
            }
        }
        return count == 0;
    }

    /**
     * 保存 下载的具体信息
     */
    public synchronized void saveInfos(List<ThreadDownLoadInfo> infos) {
        SQLiteDatabase database = getConnection();
        try {
            for (ThreadDownLoadInfo info : infos) {
                String sql = "insert into thread_info(thread_id,start_pos, end_pos,down_size,url) values (?,?,?,?,?)";
                Object[] bindArgs = {
                        info.getThreadId(), info.getStartPos(), info.getEndPos(),
                        info.getDownSize(), info.getUrl()
                };
                database.execSQL(sql, bindArgs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != database) {
                database.close();
            }
        }
    }

    /**
     * 获取此URL的线程信息
     * 
     * @param fileUrl
     * @return
     */
    public synchronized List<ThreadDownLoadInfo> getThreadInfoList(String fileUrl) {
        List<ThreadDownLoadInfo> list = new ArrayList<ThreadDownLoadInfo>();
        SQLiteDatabase database = getConnection();
        Cursor cursor = null;
        try {
            String sql = "select thread_id, start_pos, end_pos, down_size, url from thread_info where url=?";
            cursor = database.rawQuery(sql, new String[] {
                fileUrl
            });
            while (cursor.moveToNext()) {
                ThreadDownLoadInfo info = new ThreadDownLoadInfo(cursor.getInt(0),
                        cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4));
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != database) {
                database.close();
            }
            if (null != cursor) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * 更新线程信息
     * 
     * @param threadId
     * @param downSize
     * @param fileUrl
     */
    public synchronized void updataInfo(int threadId, long downSize, String fileUrl) {
        SQLiteDatabase database = getConnection();
        try {
            String sql = "update thread_info set down_size=? where thread_id=? and url=?";
            Object[] bindArgs = {
                    downSize, threadId, fileUrl
            };
            database.execSQL(sql, bindArgs);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != database) {
                database.close();
            }
        }
    }

    /**
     * 删除与url相关的记录
     * 
     * @param fileUrl
     */
    public synchronized void delete(String fileUrl) {
        SQLiteDatabase database = getConnection();
        try {
            database.delete("thread_info", "url=?", new String[] {
                fileUrl
            });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != database) {
                database.close();
            }
        }
    }
}
