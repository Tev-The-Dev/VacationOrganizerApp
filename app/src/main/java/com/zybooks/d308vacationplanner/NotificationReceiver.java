// java
package com.zybooks.d308vacationplanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "vacation_channel";
    private static final String CHANNEL_NAME = "Vacation reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long vacId = intent.getLongExtra("vacation_id", -1L);
        String title = intent.getStringExtra("vacation_title");
        String type = intent.getStringExtra("type"); // "start", "end", or "excursion"

        if (vacId == -1L) return;

        createChannelIfNeeded(context);

        String safeTitle = (title != null && !title.isEmpty()) ? title : "";
        String contentTitle;
        String contentText;

        if ("excursion".equals(type)) {
            contentTitle = "Excursion";
            contentText = safeTitle.isEmpty() ? "Excursion is today" : safeTitle + " is today";
        } else {
            contentTitle = "Vacation";
            if ("end".equals(type)) contentText = safeTitle.isEmpty() ? "End date is now" : safeTitle + " end date is now";
            else if ("start".equals(type)) contentText = safeTitle.isEmpty() ? "Start date is now" : safeTitle + " start date is now";
            else contentText = safeTitle.isEmpty() ? "Reminder" : safeTitle + " reminder";
        }

        Intent detail = new Intent(context, VacationDetailActivity.class);
        detail.putExtra("vacation_id", vacId);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(detail);

        int requestCode = NotificationScheduler.buildRequestCode(vacId, type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pending = stackBuilder.getPendingIntent(requestCode, flags);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pending)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(requestCode, nb.build());
    }

    private void createChannelIfNeeded(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                // use HIGH importance so notifications are prominent
                channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            } else {
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            }
        }
    }
}
