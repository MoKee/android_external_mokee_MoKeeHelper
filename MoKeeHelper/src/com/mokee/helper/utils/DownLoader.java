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

package com.mokee.helper.utils;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.mokee.helper.db.DownLoadDao;
import com.mokee.helper.db.ThreadDownLoadDao;
import com.mokee.helper.misc.DownLoadInfo;
import com.mokee.helper.misc.ThreadDownLoadInfo;

public class DownLoader {
    public String fileUrl;// 下载的地址
    private String localFile;// 保存路径
    private int threadCount;// 线程数
    private Handler mHandler;
    private long fileSize;// 所要下载的文件的大小
    private List<ThreadDownLoadInfo> downInfoList;// 存放下载信息类的集合
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_PAUSED = 3;
    public static final int STATUS_DELETE = 4;
    public static final int STATUS_ERROR = 5;
    public static final int STATUS_COMPLETE = 6;
    public static final int STATUS_FAILED = 7;
    private int state = STATUS_PENDING;
    private int notificationID = -1;// 存储对应通知ID;
    public long allDownSize = 0;// 总体下载大小
    public long downloadedSize = 0;
    private int endThreadNum = 0;
    private long startDown;

    public DownLoader(String fileUrl, String localfile, int threadcount, Handler mHandler,
            long startDown) {
        this.fileUrl = fileUrl;
        this.localFile = localfile;
        this.threadCount = threadcount;
        this.mHandler = mHandler;
        this.startDown = startDown;
    }

    public long getStartDown() {
        return startDown;
    }

    public int getNotificationID() {
        return notificationID;
    }

    public void setNotificationID(int notificationID) {
        this.notificationID = notificationID;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isDownLoading() {
        return state == STATUS_DOWNLOADING;
    }

    /**
     * 装载DownloadInfo
     * 
     * @return
     */
    public DownLoadInfo getDownLoadInfo() {
        if (Utils.isNetworkAvailable())// 执行时简单判断网络状态
        {
            if (isFirst(fileUrl)) {
                if (!init()) {//judge init is success
                    return null;
                }
                long range = fileSize / threadCount;
                downInfoList = new ArrayList<ThreadDownLoadInfo>();
                for (int i = 0; i < threadCount - 1; i++) {
                    ThreadDownLoadInfo info = new ThreadDownLoadInfo(i, i * range, (i + 1) * range
                            - 1, 0, fileUrl);
                    downInfoList.add(info);
                }
                ThreadDownLoadInfo info = new ThreadDownLoadInfo(threadCount - 1, (threadCount - 1)
                        * range, fileSize - 1, 0, fileUrl);
                downInfoList.add(info);
                ThreadDownLoadDao.getInstance().saveInfos(downInfoList);
                allDownSize = 0;
                downloadedSize = 0;
                DownLoadInfo loadInfo = new DownLoadInfo(fileSize, 0, fileUrl);
                return loadInfo;
            } else {
                // 获取URL的相关线程信息
                downInfoList = ThreadDownLoadDao.getInstance().getThreadInfoList(fileUrl);
                Log.v("TAG", "not isFirst size=" + downInfoList.size());
                int size = 0;
                int complete = 0;
                fileSize = DownLoadDao.getInstance().getDownLoadInfoByUrl(fileUrl).getFileSize();
                allDownSize = 0;
                downloadedSize = 0;
                for (ThreadDownLoadInfo info : downInfoList) {
                    complete += info.getDownSize();
                    allDownSize += info.getDownSize();
                    downloadedSize += info.getDownSize();
                    size += info.getEndPos() - info.getStartPos() + 1;
                }
                return new DownLoadInfo(size, complete, fileUrl);
            }
        } else {
            state = STATUS_ERROR;
            sendMsg(state, fileUrl, 0);
            return null;
        }
    }

    /**
     * 初始化
     */
    private boolean init() {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            fileSize = connection.getContentLength();
            connection.disconnect();
            if (fileSize > 0) {
                DownLoadDao.getInstance().updataFileSize(fileUrl, fileSize);// 更新文件长度
                File file = new File(localFile);
                if (!file.exists()) {
                    file.createNewFile();
                }
                RandomAccessFile accessFile = new RandomAccessFile(file, "rwd");
                accessFile.setLength(fileSize);
                accessFile.close();
                return true;
            } else
            // 文件长度错误
            {
                state = STATUS_ERROR;
                sendMsg(state, fileUrl, 0);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            state = STATUS_ERROR;
            sendMsg(state, fileUrl, 0);
            return false;
        }
    }

    /**
     * 判断是否是第一次 下载
     */
    private boolean isFirst(String fileUrl) {
        if (!ThreadDownLoadDao.getInstance().isHasInfos(fileUrl) | !new File(localFile).exists())
        {
            ThreadDownLoadDao.getInstance().delete(fileUrl);// 清理未完成线程记录
            return true;
        }
        return false;
    }

    /**
     * 准备分段下载
     */
    public void download() {
        if (downInfoList != null) {
            if (state == STATUS_DOWNLOADING)
                return;
            state = STATUS_DOWNLOADING;
            DownLoadDao.getInstance().updataState(fileUrl, state);
            for (ThreadDownLoadInfo info : downInfoList) {
                new DonwLoadThread(info.getThreadId(), info.getStartPos(), info.getEndPos(),
                        info.getDownSize(), info.getUrl()).start();
            }
        }
    }

    public class DonwLoadThread extends Thread {
        private int threadId;
        private long startPos;
        private long endPos;
        private long downSize;
        private long sectionSize;
        private String fileUrl;

        public DonwLoadThread(int threadId, long startPos, long endPos, long downSize,
                String fileUrl) {
            this.threadId = threadId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.downSize = downSize;
            this.sectionSize = endPos - startPos;
            this.fileUrl = fileUrl;
        }

        @Override
        public void run() {
            if (downSize < sectionSize) {
                HttpURLConnection connection = null;
                RandomAccessFile randomAccessFile = null;
                InputStream is = null;
                try {
                    URL url = new URL(fileUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Connection", "Keep-Alive");
                    // 设置分段读取范围
                    connection.setRequestProperty("Range", "bytes=" + (startPos + downSize) + "-"
                            + endPos);
                    // 随机存储
                    randomAccessFile = new RandomAccessFile(localFile, "rwd");
                    randomAccessFile.seek(startPos + downSize);
                    is = connection.getInputStream();
                    byte[] buffer = new byte[4096];
                    int length = -1;
                    int i = 0;
                    while ((length = is.read(buffer)) != -1)
                    // while ((length = is.read(buffer, 0, 1024)) != -1)
                    {
                        i++;
                        randomAccessFile.write(buffer, 0, length);
                        downSize += length;
                        allDownSize += length;
                        // 线程更新进度
                        ThreadDownLoadDao.getInstance().updataInfo(threadId, downSize, fileUrl);
                        if (i == 5) {
                            sendMsg(STATUS_DOWNLOADING, fileUrl, length);
                            i = 0;
                        }
                        if (state == STATUS_PAUSED || state == STATUS_DELETE
                                || state == STATUS_ERROR) {
                            return;
                        }
                    }
                    randomAccessFile.close();
                    is.close();
                    isOver();
                } catch (Exception e) {
                    state = STATUS_ERROR;
                    sendMsg(state, fileUrl, 0);
                    e.printStackTrace();
                }
            } else {
                allDownSize += downSize;
                isOver();
            }
        }
    }

    // 删除
    public void delete(String fileUrl) {
        ThreadDownLoadDao.getInstance().delete(fileUrl);
    }

    // 暂停
    public void pause() {
        DownLoadDao.getInstance().updataState(fileUrl, DownLoader.STATUS_PAUSED);
        state = STATUS_PAUSED;
    }

    // 删除
    public void delete() {
        state = STATUS_DELETE;
    }

    /**
     * 发送msg
     * 
     * @param msgID
     * @param url
     * @param length
     */
    private synchronized void sendMsg(int msgID, String url, int length) {
        Message msg = Message.obtain();
        if(msgID==STATUS_DOWNLOADING)
        {
            msg.obj = url;
        }
        else{
            msg.obj = this;
        }
        msg.what = msgID;
        msg.arg1 = length;
        msg.arg2 = getNotificationID();
        mHandler.sendMessage(msg);
    }

    /**
     * 判断线程是否全部完成
     */
    public synchronized void isOver() {
        endThreadNum++;
        if (endThreadNum == threadCount && allDownSize == fileSize) {
            sendMsg(STATUS_COMPLETE, fileUrl, 0);
        }
        else if(endThreadNum == threadCount && allDownSize != fileSize){//maybe thread info error then delete
            ThreadDownLoadDao.getInstance().delete(fileUrl);
            sendMsg(STATUS_ERROR, fileUrl, 0);
        }
    }

    @Override
    public String toString() {
        return "DownLoader [fileUrl=" + fileUrl + ", localfile=" + localFile + ", threadCount="
                + threadCount + ", mHandler=" + mHandler + ", fileSize=" + fileSize
                + ", downInfoList=" + downInfoList + ", state=" + state + ", notificationID="
                + notificationID + ", allDownSize=" + allDownSize + ", downloadedSize="
                + downloadedSize + ", endThreadNum=" + endThreadNum + ", startDown=" + startDown
                + "]";
    }
    
}
