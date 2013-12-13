
package com.mokee.helper.service;

import java.util.HashMap;
import java.util.Map;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import com.mokee.helper.MoKeeApplication;
import com.mokee.helper.activities.MoKeeCenter;
import com.mokee.helper.db.DownLoadDao;
import com.mokee.helper.misc.DownLoadInfo;
import com.mokee.helper.utils.DownLoader;

public class DownLoadService extends IntentService {
    private static final String TAG = "DownLoadService";

    public DownLoadService() {
        super("DownLoadService");
        // TODO Auto-generated constructor stub
    }

    public static final String ACTION_DOWNLOAD = "download";
    public static final String ACTION_DOWNLOAD_COMPLETE = "DownLoadService_download_complete";
    public static final String DOWNLOAD_TYPE = "download_type";
    public static final String DOWN_URL = "down_url";
    public static final String FILE_PATH = "file_path";
    public static final String DOWNLOAD_ID = "download_id";
    public static final String DOWNLOAD_FLAG = "flag";

    public static final int START = 2;
    public static final int PAUSE = 3;
    public static final int DELETE = 4;
    public static final int CONTINUE = 5;
    public static final int ADD = 6;
    public static final int STOP = 7;
    private static Map<String, DownLoader> downloaders = new HashMap<String, DownLoader>();
    private static Map<Integer, NotificationCompat.Builder> notifications = new HashMap<Integer, NotificationCompat.Builder>();// 通知队列
    private int i = 1;
    private NotificationManager manager;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent action, int flags, int startId) {

        return super.onStartCommand(action, flags, startId);
    }

    /* 显示下载 */
    private void addNotification(int id, String title) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(title);
        builder.setContentText("正在下载..." + "0%");
        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        /* 设置点击消息时，显示的界面 */
        Intent nextIntent = new Intent(this, MoKeeCenter.class);
        TaskStackBuilder task = TaskStackBuilder.create(this);
        task.addNextIntent(nextIntent);
        PendingIntent pengdingIntent = task.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pengdingIntent);
        builder.setProgress(100, 0, false);
        builder.setAutoCancel(true);
        builder.setTicker("下载中....");
        notifications.put(id, builder);
        // Notification not = builder.build();
        // not.flags = Notification.FLAG_NO_CLEAR;
        manager.notify(id, builder.build());
    }

    // 更新下载进度
    private void updateNotification(int id, int progress) {
        NotificationCompat.Builder notification = notifications.get(id);
        notification.setContentText("正在下载..." + progress + "%");
        notification.setProgress(100, progress, false);
        manager.notify(id, notification.build());
    }

    public Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            DownLoader di;
            String url;
            switch (msg.what) {
                case DownLoader.STATUS_DOWNLOADING:// 更新通知
                    url = (String) msg.obj;
                    di = downloaders.get(url);
                    if (di.allDownSize > 0 & di.getFileSize() > 0) {
                        updateNotification(
                                msg.arg2,
                                Integer.valueOf(String.valueOf(di.allDownSize * 100
                                        / di.getFileSize())));
                    }
                    break;

                // case DownLoader.STATUS_ERROR:// 下载错误
                // Toast.makeText(MoKeeApplication.getContext(), "下载失败,请重试",
                // Toast.LENGTH_SHORT).show();
                // url = (String) msg.obj;// 暂时不处理错误
                // di = downloaders.get(url);
                // manager.cancel(di.getNotificationID());
                // di.delete(url);
                // DownLoadDao.getInstance().updataState(url,
                // DownLoader.STATUS_ERROR);
                // // di.reset();
                // downloaders.remove(msg.obj);
                // break;
                // case DownLoader.STATUS_COMPLETE:// 完成任务
                // Toast.makeText(MoKeeApplication.getContext(), "下载完成",
                // Toast.LENGTH_SHORT).show();
                // url = (String) msg.obj;
                // di = downloaders.get(url);
                // manager.cancel(di.getNotificationID());// 清除通知
                // di.delete(url);
                // DownLoadDao.getInstance().updataState(url,
                // DownLoader.STATUS_COMPLETE);
                //
                // // di.reset();
                // downloaders.remove(msg.obj);
                // if (downloaders.size() == 0)
                // {
                // stopSelf();
                // }
                // break;
                default:
                    url = (String) msg.obj;
                    di = downloaders.get(url);
                    if (notifications.containsKey(di.getNotificationID())) {
                        manager.cancel(di.getNotificationID());
                        notifications.remove(di.getNotificationID());
                    }
                    di.delete(url);
                    DownLoadDao.getInstance().updataState(url, msg.what);
                    // di.reset();
                    downloaders.remove(msg.obj);
                    DownLoadInfo dli = DownLoadDao.getInstance().getDownLoadInfoByUrl(url);
                    Intent intent = new Intent();
                    intent.setAction(ACTION_DOWNLOAD_COMPLETE);
                    intent.putExtra(DOWNLOAD_ID, Long.valueOf(dli.getDownID()));
                    intent.putExtra(DOWNLOAD_FLAG, dli.getFlag());
                    sendBroadcast(intent);
                    if (downloaders.size() == 0) {
                        stopSelf();
                    }
                    break;
            }
            return false;
        }
    });

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void onHandleIntent(Intent action) {
        if (action != null && ACTION_DOWNLOAD.equals(action.getAction())) {
            int type = action.getIntExtra(DOWNLOAD_TYPE, 6);
            String url = action.getStringExtra(DOWN_URL);
            String filePath = action.getStringExtra(FILE_PATH);
            long download_id = action.getLongExtra(DOWNLOAD_ID, System.currentTimeMillis());
            int flag = action.getIntExtra(DOWNLOAD_FLAG, 1024);
            DownLoader downloader = null;
            switch (type) {
                case ADD:
                    i++;
                    downloader = downloaders.get(url);
                    if (downloader == null) {
                        downloader = new DownLoader(url, filePath, 4, handler);
                        downloaders.put(url, downloader);
                        if (!DownLoadDao.getInstance().isHasInfos(url)) {
                            // 初次添加,初始状态
                            DownLoadDao.getInstance().saveInfo(
                                    new DownLoadInfo(url, flag, String.valueOf(download_id),
                                            filePath, filePath.substring(
                                                    filePath.lastIndexOf("/") + 1,
                                                    filePath.length()), 0,
                                            DownLoader.STATUS_PENDING));
                        }
                    }
                    if (downloader.isDownLoading())
                        return;
                    DownLoadDao.getInstance().updataState(url, DownLoader.STATUS_DOWNLOADING);
                    DownLoadInfo loadInfo = downloader.getDownLoadInfo();
                    if (loadInfo != null) {
                        if (!notifications.containsKey(downloader.getNotificationID())) {
                            addNotification(i, "测试广播");
                            downloader.setNotificationID(i);
                        }
                        // 调用方法开始下载
                        downloader.download();
                    }
                    break;
                case PAUSE:
                    downloader = downloaders.get(url);
                    if (downloader != null) {
                        downloader.pause();
                        manager.cancel(downloader.getNotificationID());
                        notifications.remove(downloader.getNotificationID());
                    }
                    break;
            // case DELETE:
            // downloader = downloaders.get(url);
            // if (downloader != null)
            // {
            // downloader.delete(url);
            // downloaders.remove(url);
            // }
            // break;
            // case CONTINUE:
            // break;
            }
        }

    }
}
