
package com.mokee.helper.misc;

/**
 * 每个线程下载信息
 * 
 * @author wszfer
 */
public class ThreadDownLoadInfo {
    private int threadId;
    private long startPos;// 开始点
    private long endPos;// 结束点
    private long downSize;// 已下载数据
    private String url;

    public ThreadDownLoadInfo(int threadId, long startPos, long endPos, long downSize, String url) {
        this.threadId = threadId;
        this.startPos = startPos;
        this.endPos = endPos;
        this.downSize = downSize;
        this.url = url;
    }

    public ThreadDownLoadInfo() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getThreadId() {
        return threadId;
    }

    public void setThreadId(int threadId) {
        this.threadId = threadId;
    }

    public long getStartPos() {
        return startPos;
    }

    public void setStartPos(int startPos) {
        this.startPos = startPos;
    }

    public long getEndPos() {
        return endPos;
    }

    public void setEndPos(int endPos) {
        this.endPos = endPos;
    }

    public long getDownSize() {
        return downSize;
    }

    public void setDownSize(int downSize) {
        this.downSize = downSize;
    }

    @Override
    public String toString() {
        return "DownLoadInfo [threadId=" + threadId + ", startPos=" + startPos + ", endPos="
                + endPos + ", downSize=" + downSize + ", url=" + url + "]";
    }

}
