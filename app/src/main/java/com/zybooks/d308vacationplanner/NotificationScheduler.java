package com.zybooks.d308vacationplanner;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
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
    private static final String PREFS_NAME = "vacation_prefs";
    private static final String PREF_AUTO_STARTUP = "auto_notify_on_startup";

    // per-item pref prefixes (match keys used elsewhere in the app)
    private static final String PREF_NOTIFY_PREFIX_VAC = "notify_vac_";
    private static final String PREF_NOTIFY_PREFIX_EXC = "notify_exc_";

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
                // Only send immediate notification for today if user enabled vacation notifications
                if (isNotifyEnabled(ctx, vacationId, /*type=*/"vacation")) {
                    notifyNow(ctx, vacationId, title, /*type=*/null);
                } else {
                    Log.d(TAG, "start date is today but user disabled notifications for vacationId=" + vacationId);
                }
            } else if (startMillis > todayStartMillis) {
                // schedule morning alarm for start day (9:00)
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(startMillis);
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                scheduleAlarm(ctx, vacationId, title, /*type=*/null, cal.getTimeInMillis());
            }
        }

        // Handle end date: same date-only semantics
        Long endMillis = parseDateToMillis(endDateStr);
        if (endMillis != null) {
            if (isDateToday(endDateStr)) {
                if (isNotifyEnabled(ctx, vacationId, /*type=*/"vacation")) {
                    // use type "end" so requestCode differs
                    notifyNow(ctx, vacationId, title, "end");
                } else {
                    Log.d(TAG, "end date is today but user disabled notifications for vacationId=" + vacationId);
                }
            } else if (endMillis > todayStartMillis) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(endMillis);
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                scheduleAlarm(ctx, vacationId, title, "end", cal.getTimeInMillis());
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
            // Only send if user enabled excursion notifications
            if (isNotifyEnabled(ctx, vacationId, "excursion")) {
                notifyNow(ctx, vacationId, excursionTitle, "excursion");
            } else {
                Log.d(TAG, "excursion is today but user disabled notifications for excursionId=" + vacationId);
            }
        } else if (whenMillis > todayStartMillis) {
            // schedule at 9:00 local time on the excursion date
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(whenMillis);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            scheduleAlarm(ctx, vacationId, excursionTitle, "excursion", cal.getTimeInMillis());
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
                SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.getDefault());
                fmt.setLenient(false);
                Date d = fmt.parse(s);
                if (d != null) {
                    // normalize to start-of-day local time
                    Calendar c = Calendar.getInstance();
                    c.setTime(d);
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    long millis = c.getTimeInMillis();
                    Log.d(TAG, "parseDateToMillis: pattern=" + p + " -> " + millis);
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

    // Public compatibility method (existing callers)
    public static void checkDatabaseAndNotifyToday(Context ctx) {
        checkDatabaseAndNotifyToday(ctx, false);
    }

    // Guarded method: only runs if manualTrigger=true OR pref auto_notify_on_startup is true
    public static void checkDatabaseAndNotifyToday(Context ctx, boolean manualTrigger) {
        if (ctx == null) return;

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoOnStartup = prefs.getBoolean(PREF_AUTO_STARTUP, false);
            if (!manualTrigger && !autoOnStartup) {
                Log.d(TAG, "Auto-notify on startup disabled; skipping database check");
                return;
            }

            // Example: check vacations and excursions and trigger today's notifications only if
            // their individual notification switch is enabled. The repository methods below are
            // assumed; adapt if your repo API differs.
            VacationRepository repo = VacationRepository.getInstance(ctx);
            if (repo == null) return;

            List<Vacations> vacs = repo.getVacations();
            if (vacs != null) {
                for (Vacations v : vacs) {
                    try {
                        long id = v.getId();
                        String title = reflectString(v, "getTitle", "getName");
                        String start = reflectString(v, "getStartDate", "getStart", "getStart_date");
                        String end = reflectString(v, "getEndDate", "getEnd", "getEnd_date");

                        if (isDateToday(start)) {
                            if (manualTrigger || isNotifyEnabled(ctx, id, "vacation")) {
                                notifyNow(ctx, id, title, null);
                            } else {
                                Log.d(TAG, "Skipping start notification for vacation " + id + " (user disabled)");
                            }
                        }
                        if (isDateToday(end)) {
                            if (manualTrigger || isNotifyEnabled(ctx, id, "vacation")) {
                                notifyNow(ctx, id, title, "end");
                            } else {
                                Log.d(TAG, "Skipping end notification for vacation " + id + " (user disabled)");
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }

            List<Excursions> exs = repo.getExcursions();
            if (exs != null) {
                for (Excursions ex : exs) {
                    try {
                        long id = ex.getId();
                        String title = reflectString(ex, "getTitle", "getName");
                        String date = reflectString(ex, "getDate", "getWhen");
                        if (isDateToday(date)) {
                            if (manualTrigger || isNotifyEnabled(ctx, id, "excursion")) {
                                notifyNow(ctx, id, title, "excursion");
                            } else {
                                Log.d(TAG, "Skipping excursion notification for " + id + " (user disabled)");
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "checkDatabaseAndNotifyToday failed", ex);
        }
    }

    // notifyNow checks POST_NOTIFICATIONS on Android 13+ and posts a notification using a high importance channel
    public static void notifyNow(Context ctx, long vacationId, String title, String type) {
        if (ctx == null) return;

        // runtime permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing POST_NOTIFICATIONS permission - aborting notifyNow");
                return;
            }
        }

        createChannelIfNeeded(ctx);

        String safeTitle = (title != null && !title.isEmpty()) ? title : "";
        String contentTitle;
        String contentText;

        if ("excursion".equals(type)) {
            contentTitle = safeTitle.isEmpty() ? "Excursion" : safeTitle;
            contentText = "Excursion today";
        } else if ("end".equals(type)) {
            contentTitle = safeTitle.isEmpty() ? "Vacation" : safeTitle;
            contentText = "Vacation ends today";
        } else {
            contentTitle = safeTitle.isEmpty() ? "Vacation" : safeTitle;
            contentText = "Vacation starts today";
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

    // Internal alarm scheduling with canScheduleExactAlarms / SecurityException handling
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

        // Use exact alarms when possible; on Android S+ check canScheduleExactAlarms()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            }
        } catch (SecurityException se) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } catch (Exception ignored) { }
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm failed", e);
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

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                // allow notifications to be visible on lock screen
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                // enable vibration by default (user can change later)
                channel.enableVibration(true);
                // set default notification sound
                channel.setSound(soundUri, aa);
                nm.createNotificationChannel(channel);
            } else {
                // ensure visibility set and channel has the sound
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setSound(soundUri, aa);
                nm.createNotificationChannel(channel);
            }
        }
    }

    // check per-item user preference
    private static boolean isNotifyEnabled(Context ctx, long id, String type) {
        if (ctx == null) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if ("excursion".equals(type)) {
            return prefs.getBoolean(PREF_NOTIFY_PREFIX_EXC + id, false);
        } else {
            return prefs.getBoolean(PREF_NOTIFY_PREFIX_VAC + id, false);
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
