
package com.mokee.helper.misc;

/**
 * 整体下载信息
 * 
 * @author wszfer
 */
public class DownLoadInfo {
    public long fileSize;// 文件大小
    private long complete;// 已下载长度
    private String url;// 下载器标识
    private String downID;
    private String localFile;
    private String fileName;
    private int state;
    private int flag;

    public DownLoadInfo(long fileSize, long complete, String url) {
        this.fileSize = fileSize;
        this.complete = complete;
        this.url = url;
    }

    public DownLoadInfo(String url, int flag, String downID, String localFile, String fileName,
            long fileSize, int state) {
        super();
        this.url = url;
        this.flag = flag;
        this.downID = downID;
        this.localFile = localFile;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.state = state;
    }

    public DownLoadInfo() {
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public long getComplete() {
        return complete;
    }

    public void setComplete(int complete) {
        this.complete = complete;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDownID() {
        return downID;
    }

    public void setDownID(String downID) {
        this.downID = downID;
    }

    public String getLocalFile() {
        return localFile;
    }

    public void setLocalFile(String localFile) {
        this.localFile = localFile;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return "DownLoadInfo [fileSize=" + fileSize + ", complete=" + complete + ", url=" + url
                + ", downID=" + downID + ", localFile=" + localFile + ", fileName=" + fileName
                + ", state=" + state + ", flag=" + flag + "]";
    }

}
