package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class NotificationReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "vacation_channel_01";
    private static final String CHANNEL_NAME = "Vacation reminders";
    private static final String TAG = "NotifReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Debugging: log intent and extras to trace origin
        try {
            long vacId = intent != null ? intent.getLongExtra("vacation_id", -1L) : -1L;
            String title = intent != null ? intent.getStringExtra("vacation_title") : null;
            String start = intent != null ? intent.getStringExtra("vacation_start") : null;

            Log.d(TAG, "onReceive intent=" + intent + " vacId=" + vacId + " title=" + title + " start=" + start);

            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            Log.d(TAG, "stack (top 8): " + Arrays.toString(Arrays.copyOfRange(st, 0, Math.min(8, st.length))));
        } catch (Exception e) {
            Log.e(TAG, "Error while logging onReceive info", e);
        }

        createNotificationChannel(context);

        long vacId = intent != null ? intent.getLongExtra("vacation_id", -1L) : -1L;
        String title = intent != null ? intent.getStringExtra("vacation_title") : null;
        String start = intent != null ? intent.getStringExtra("vacation_start") : null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing POST_NOTIFICATIONS permission - aborting notify");
                return;
            }
        }

        int iconId = context.getResources().getIdentifier("ic_notification", "drawable", context.getPackageName());
        if (iconId == 0) {
            iconId = android.R.drawable.ic_dialog_info;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconId)
                .setContentTitle(title != null ? title : "Vacation")
                .setContentText("Starts: " + (start != null ? start : ""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                // request default sound + vibration for pre-O devices; O+ uses the channel sound
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        int id = (int) (vacId ^ (vacId >>> 32));

        try {
            nm.notify(id, builder.build());
            Log.d(TAG, "nm.notify called id=" + id);
        } catch (RuntimeException ignored) {
            Log.e(TAG, "nm.notify threw", ignored);
        }
    }

    public static void createNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Reminders for vacation start dates");
                channel.setSound(soundUri, aa);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            } else {
                // Ensure channel has a sound and appropriate importance
                channel.setSound(soundUri, aa);
                channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(channel);
            }
        }
    }
}
