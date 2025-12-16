// java
package com.zybooks.d308vacationplanner;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.Manifest;

public class NotificationScheduler {
    private static final String TAG = "NotificationScheduler";
    private static final String CHANNEL_ID = "vacation_channel";
    private static final String CHANNEL_NAME = "Vacation reminders";

    // Public API: schedule start/end alarms for a vacation
    public static void scheduleVacationNotifications(Context ctx, long vacationId, String title, String startDateStr, String endDateStr) {
        if (ctx == null) return;

        // compute today's start-of-day millis for date-only comparisons
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        long todayStartMillis = todayStart.getTimeInMillis();

        // Handle start date: notify if date is today, schedule only if date > today
        Long startMillis = parseDateToMillis(startDateStr);
        if (startMillis != null) {
            if (isDateToday(startDateStr)) {
                Log.d(TAG, "scheduleVacationNotifications: start is today -> notify now vacId=" + vacationId);
                notifyNow(ctx, vacationId, title, "start");
            } else if (startMillis > todayStartMillis) {
                // start date is in the future (tomorrow or later)
                scheduleAlarm(ctx, vacationId, title, "start", startMillis);
                Log.d(TAG, "scheduleVacationNotifications: scheduled start for vacId=" + vacationId + " at " + startMillis);
            } else {
                Log.d(TAG, "scheduleVacationNotifications: start date is in the past; not scheduling vacId=" + vacationId);
            }
        }

        // Handle end date: same date-only semantics
        Long endMillis = parseDateToMillis(endDateStr);
        if (endMillis != null) {
            if (isDateToday(endDateStr)) {
                Log.d(TAG, "scheduleVacationNotifications: end is today -> notify now vacId=" + vacationId);
                notifyNow(ctx, vacationId, title, "end");
            } else if (endMillis > todayStartMillis) {
                scheduleAlarm(ctx, vacationId, title, "end", endMillis);
                Log.d(TAG, "scheduleVacationNotifications: scheduled end for vacId=" + vacationId + " at " + endMillis);
            } else {
                Log.d(TAG, "scheduleVacationNotifications: end date is in the past; not scheduling vacId=" + vacationId);
            }
        }
    }

    public static void scheduleExcursionNotification(Context ctx, long vacationId, String excursionTitle, String dateStr) {
        if (ctx == null || dateStr == null) return;

        Long whenMillis = parseDateToMillis(dateStr);
        if (whenMillis == null) return;

        // compute today's start-of-day millis
        java.util.Calendar todayStart = java.util.Calendar.getInstance();
        todayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
        todayStart.set(java.util.Calendar.MINUTE, 0);
        todayStart.set(java.util.Calendar.SECOND, 0);
        todayStart.set(java.util.Calendar.MILLISECOND, 0);
        long todayStartMillis = todayStart.getTimeInMillis();

        if (isDateToday(dateStr)) {
            // immediate notification for excursions happening today
            notifyNow(ctx, vacationId, excursionTitle != null ? ("Excursion: " + excursionTitle) : "Excursion", "excursion");
        } else if (whenMillis > todayStartMillis) {
            // schedule future alarm
            scheduleAlarm(ctx, vacationId, excursionTitle, "excursion", whenMillis);
        }
        // else: date is in the past -> do nothing
    }

    // Parse YYYY-MM-DD (and a few fallbacks). Return millis at local start-of-day or null.
    public static Long parseDateToMillis(String dateStr) {
        if (dateStr == null) return null;
        String s = dateStr.trim();
        if (s.isEmpty()) return null;

        String[] patterns = new String[] {
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "MMM d, yyyy",
                "yyyy/MM/dd"
        };

        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.getDefault());
                sdf.setLenient(false);
                Date d = sdf.parse(s);
                if (d != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(d.getTime());
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long millis = cal.getTimeInMillis();
                    Log.d(TAG, "parseDateToMillis: parsed '" + s + "' using " + p + " -> " + millis);
                    return millis;
                }
            } catch (ParseException ignored) { }
        }

        // try epoch fallback
        try {
            long v = Long.parseLong(s);
            Log.d(TAG, "parseDateToMillis: parsed epoch '" + s + "' -> " + v);
            return v;
        } catch (Exception ignored) { }

        Log.d(TAG, "parseDateToMillis: unable to parse '" + s + "'");
        return null;
    }

    // True if provided date string corresponds to today's local date
    public static boolean isDateToday(String dateStr) {
        Long millis = parseDateToMillis(dateStr);
        if (millis == null) {
            Log.d(TAG, "isDateToday: null for '" + dateStr + "'");
            return false;
        }
        Calendar now = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(millis);
        boolean same = now.get(Calendar.YEAR) == d.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == d.get(Calendar.MONTH)
                && now.get(Calendar.DAY_OF_MONTH) == d.get(Calendar.DAY_OF_MONTH);
        Log.d(TAG, "isDateToday: '" + dateStr + "' -> " + millis + " sameToday=" + same);
        return same;
    }

    // check start/end and post immediate notifications for today
    public static void checkDatabaseAndNotifyToday(Context ctx) {
        if (ctx == null) return;
        try {
            VacationRepository repo = VacationRepository.getInstance(ctx);
            if (repo == null) {
                Log.d(TAG, "checkDatabaseAndNotifyToday: no repository");
                return;
            }

            List<Vacations> vacations = repo.getVacations();
            if (vacations != null && !vacations.isEmpty()) {
                for (Vacations v : vacations) {
                    if (v == null) continue;
                    Long vacId = null;
                    try {
                        Method getId = v.getClass().getMethod("getId");
                        Object idVal = getId.invoke(v);
                        if (idVal != null) vacId = Long.parseLong(String.valueOf(idVal));
                    } catch (Exception ignored) { }

                    if (vacId == null) continue;

                    String title = reflectString(v, "getTitle", "getVacationTitle");
                    String start = reflectString(v, "getStartDate", "getStart");
                    String end = reflectString(v, "getEndDate", "getEnd");

                    if (isDateToday(start)) {
                        Log.d(TAG, "checkDatabaseAndNotifyToday: vacation start is today vacId=" + vacId);
                        notifyNow(ctx, vacId, title, "start");
                    }
                    if (isDateToday(end)) {
                        Log.d(TAG, "checkDatabaseAndNotifyToday: vacation end is today vacId=" + vacId);
                        notifyNow(ctx, vacId, title, "end");
                    }
                }
            }

            // Check excursions and notify if any excursion date is today
            List<Excursions> excursions = repo.getExcursions();
            if (excursions != null && !excursions.isEmpty()) {
                for (Excursions ex : excursions) {
                    if (ex == null) continue;
                    Long exVacId = null;
                    try {
                        Method gv = ex.getClass().getMethod("getVacationId");
                        Object val = gv.invoke(ex);
                        if (val != null) exVacId = Long.parseLong(String.valueOf(val));
                    } catch (Exception ignored) { }

                    if (exVacId == null) {
                        try {
                            Method gv2 = ex.getClass().getMethod("getVacation");
                            Object val2 = gv2.invoke(ex);
                            if (val2 != null) exVacId = Long.parseLong(String.valueOf(val2));
                        } catch (Exception ignored) { }
                    }

                    if (exVacId == null) continue;

                    String edate = reflectString(ex, "getExcursionDate", "getDate");
                    if (isDateToday(edate)) {
                        String etitle = reflectString(ex, "getTitle", "getExcursionTitle");
                        Log.d(TAG, "checkDatabaseAndNotifyToday: excursion is today vacId=" + exVacId + " exTitle=" + etitle);
                        notifyNow(ctx, exVacId, "Excursion: " + etitle, "excursion");
                    }
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "checkDatabaseAndNotifyToday: exception " + ex.getMessage());
        }
    }

    // notifyNow checks POST_NOTIFICATIONS on Android 13+ and posts a notification using a high importance channel
    public static void notifyNow(Context ctx, long vacationId, String title, String type) {
        if (ctx == null) return;

        // runtime permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "notifyNow: POST_NOTIFICATIONS not granted - skipping notify vacId=" + vacationId + " type=" + type);
                return;
            }
        }

        createChannelIfNeeded(ctx);

        String safeTitle = (title != null && !title.isEmpty()) ? title : "";
        String contentTitle;
        String contentText;

        if ("excursion".equals(type)) {
            contentTitle = "Excursion";
            contentText = safeTitle.isEmpty() ? "Excursion is today" : safeTitle + " is today";
        } else {
            // vacation start/end or other
            contentTitle = "Vacation";
            if ("start".equals(type)) {
                contentText = safeTitle.isEmpty() ? "Start date is now" : safeTitle + " start date is now";
            } else if ("end".equals(type)) {
                contentText = safeTitle.isEmpty() ? "End date is now" : safeTitle + " end date is now";
            } else {
                contentText = safeTitle.isEmpty() ? "Reminder" : safeTitle + " reminder";
            }
        }

        Intent detail = new Intent(ctx, VacationDetailActivity.class);
        detail.putExtra("vacation_id", vacationId);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx);
        stackBuilder.addNextIntentWithParentStack(detail);

        int requestCode = buildRequestCode(vacationId, type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pending = stackBuilder.getPendingIntent(requestCode, flags);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // ask for default sound/vibrate so system may show a heads-up when appropriate
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                // ensure visibility on lock screen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pending)
                .setAutoCancel(true);

        Log.d(TAG, "notifyNow: posting notification for vacId=" + vacationId + " type=" + type + " text=\"" + contentText + "\"");
        NotificationManagerCompat.from(ctx).notify(requestCode, nb.build());
    }

    // Internal alarm scheduling
    private static void scheduleAlarm(Context ctx, long vacationId, String title, String type, long whenMillis) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, NotificationReceiver.class);
        i.putExtra("vacation_id", vacationId);
        i.putExtra("vacation_title", title);
        i.putExtra("type", type);

        int requestCode = buildRequestCode(vacationId, type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, i, flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
        }
    }

    private static void cancelAlarm(Context ctx, long vacationId, String type) {
        if (ctx == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, NotificationReceiver.class);
        int requestCode = buildRequestCode(vacationId, type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, i, flags);
        am.cancel(pi);
        try { pi.cancel(); } catch (Exception ignored) { }
    }

    // used by NotificationReceiver as well
    public static int buildRequestCode(long id, String type) {
        int base = (int) (id & 0xffff);
        if (type == null) return base ^ 0x1234;
        if ("end".equals(type)) return base ^ 0x7777;
        if ("excursion".equals(type)) return base ^ 0x3333;
        return base ^ 0x1234;
    }

    private static void createChannelIfNeeded(Context ctx) {
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                // allow notifications to be visible on lock screen
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                // enable vibration by default (user can change later)
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            } else {
                // ensure visibility set
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            }
        }
    }

    // reflection helper to read common string getters (returns empty string if not found)
    private static String reflectString(Object obj, String... methodNames) {
        if (obj == null) return "";
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object val = m.invoke(obj);
                if (val != null) return String.valueOf(val);
            } catch (Exception ignored) { }
        }
        return "";
    }
}
