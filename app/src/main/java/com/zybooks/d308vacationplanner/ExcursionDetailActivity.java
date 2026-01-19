package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
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

public class ExcursionDetailActivity extends AppCompatActivity {

    private static final int REQUEST_EDIT_EXCURSION = 2002;
    private static final int REQUEST_DELETE_EXCURSION = 3002;

    private static final String PREFS_NAME = "vacation_prefs";
    private static final String PREF_NOTIFY_PREFIX_EXC = "notify_exc_";

    private long mVacationId = -1L;
    private String mVacationTitle = null;
    private String mVacationStart = null;
    private String mVacationEnd = null;

    private long mExcursionId = -1L;
    private String mExcursionTitle = null;
    private String mExcursionDate = null;

    private SwitchCompat notificationToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_detail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            mVacationId = savedInstanceState.getLong("vacation_id", -1L);
            mVacationTitle = savedInstanceState.getString("vacation_title");
            mVacationStart = savedInstanceState.getString("vacation_start_date");
            mVacationEnd = savedInstanceState.getString("vacation_end_date");
            mExcursionId = savedInstanceState.getLong("excursion_id", -1L);
            mExcursionTitle = savedInstanceState.getString("excursion_title");
            mExcursionDate = savedInstanceState.getString("excursion_date");
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                mVacationId = intent.getLongExtra("vacation_id", -1L);
                mVacationTitle = intent.getStringExtra("vacation_title");
                mVacationStart = intent.getStringExtra("vacation_start_date");
                mVacationEnd = intent.getStringExtra("vacation_end_date");
                mExcursionId = intent.getLongExtra("excursion_id", -1L);
                mExcursionTitle = intent.getStringExtra("excursion_title");
                mExcursionDate = intent.getStringExtra("excursion_date");
            }
        }

        // Wire notification toggle (manual with immediate-send-if-today)
        notificationToggle = findViewById(R.id.switch_notifications_excursion);
        if (notificationToggle != null) {
            boolean enabled = getNotifyPrefExc(mExcursionId);
            notificationToggle.setChecked(enabled);

            // If already enabled and the excursion is today, deliver immediately
            if (enabled && isDateToday(mExcursionDate)) {
                sendImmediateNotificationForExcursion();
            }

            notificationToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setNotifyPrefExc(mExcursionId, isChecked);

                if (isChecked) {
                    // If the excursion date is today, notify immediately; otherwise schedule
                    if (isDateToday(mExcursionDate)) {
                        sendImmediateNotificationForExcursion();
                    } else {
                        scheduleNotificationForExcursion();
                    }
                    Toast.makeText(this, "Excursion notifications enabled", Toast.LENGTH_SHORT).show();
                } else {
                    // Turn off: cancel any scheduled alarm
                    cancelScheduledNotificationForExcursion();
                    Toast.makeText(this, "Excursion notifications disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Restore edit/delete button flows
        Button editExcursionBtn = findViewById(R.id.edit_excursion_button);
        if (editExcursionBtn != null) {
            editExcursionBtn.setOnClickListener(view -> {
                Intent intent = new Intent(ExcursionDetailActivity.this, EditExcursionActivity.class);
                intent.putExtra("excursion_id", mExcursionId);
                intent.putExtra("excursion_title", mExcursionTitle != null ? mExcursionTitle : "");
                intent.putExtra("excursion_date", mExcursionDate != null ? mExcursionDate : "");
                // include vacation context if available
                intent.putExtra("vacation_id", mVacationId);
                intent.putExtra("vacation_title", mVacationTitle != null ? mVacationTitle : "");
                startActivityForResult(intent, REQUEST_EDIT_EXCURSION);
            });
        }

        Button deleteExcursionBtn = findViewById(R.id.delete_excursion_button);
        if (deleteExcursionBtn != null) {
            deleteExcursionBtn.setOnClickListener(view -> {
                Intent intent = new Intent(ExcursionDetailActivity.this, DeleteExcursionActivity.class);
                intent.putExtra("excursion_id", mExcursionId);
                startActivityForResult(intent, REQUEST_DELETE_EXCURSION);
            });
        }

        reloadVacationInfo();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("vacation_id", mVacationId);
        outState.putString("vacation_title", mVacationTitle);
        outState.putString("vacation_start_date", mVacationStart);
        outState.putString("vacation_end_date", mVacationEnd);
        outState.putLong("excursion_id", mExcursionId);
        outState.putString("excursion_title", mExcursionTitle);
        outState.putString("excursion_date", mExcursionDate);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_EXCURSION) {
            if (data != null) {
                if (data.hasExtra("excursion_id")) mExcursionId = data.getLongExtra("excursion_id", mExcursionId);
                if (data.hasExtra("excursion_title")) mExcursionTitle = data.getStringExtra("excursion_title");
                if (data.hasExtra("excursion_date")) mExcursionDate = data.getStringExtra("excursion_date");
                if (data.hasExtra("vacation_id")) mVacationId = data.getLongExtra("vacation_id", mVacationId);
                if (data.hasExtra("vacation_title")) mVacationTitle = data.getStringExtra("vacation_title");
            }

            reloadVacationInfo();
            if (notificationToggle != null) {
                notificationToggle.setChecked(getNotifyPrefExc(mExcursionId));
            }

            if (resultCode == RESULT_OK) {
                // Propagate success to parent so ExcursionListActivity will reload
                setResult(RESULT_OK);
            }
            return;
        }

        if (requestCode == REQUEST_DELETE_EXCURSION) {
            if (resultCode == RESULT_OK) {
                // Deletion succeeded in DeleteExcursionActivity -> propagate and finish this detail screen
                setResult(RESULT_OK);
                finish();
                return;
            } else {
                // Deletion cancelled/failed: refresh UI in case any fields changed
                if (data != null) {
                    if (data.hasExtra("excursion_id")) mExcursionId = data.getLongExtra("excursion_id", mExcursionId);
                    if (data.hasExtra("excursion_title")) mExcursionTitle = data.getStringExtra("excursion_title");
                    if (data.hasExtra("excursion_date")) mExcursionDate = data.getStringExtra("excursion_date");
                    if (data.hasExtra("vacation_id")) mVacationId = data.getLongExtra("vacation_id", mVacationId);
                    if (data.hasExtra("vacation_title")) mVacationTitle = data.getStringExtra("vacation_title");
                }
                reloadVacationInfo();
                if (notificationToggle != null) {
                    notificationToggle.setChecked(getNotifyPrefExc(mExcursionId));
                }
                return;
            }
        }

        // other request codes - keep existing handling if any
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Ensure we just finish to return to the previous activity that has the correct vacation context
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reloadVacationInfo() {
        TextView titleView = findViewById(R.id.detail_title);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView excursionBodyView = findViewById(R.id.detail_accommodation);
        TextView pageTitleView = findViewById(R.id.excursion_detail_title);

        // If either title or date missing, try to load from repository/model using robust getters
        if (mExcursionId != -1L && (mExcursionTitle == null || mExcursionTitle.isEmpty() || mExcursionDate == null || mExcursionDate.isEmpty())) {
            Excursions ex = findExcursionById(mExcursionId);
            if (ex != null) {
                if (mExcursionTitle == null || mExcursionTitle.isEmpty()) {
                    mExcursionTitle = safeString(ex, "getTitle", "getName", "getExcursionTitle");
                }
                if (mExcursionDate == null || mExcursionDate.isEmpty()) {
                    // try multiple possible getter names; parseDateFlexible handles millis too
                    mExcursionDate = safeString(ex, "getDate", "getStartDate", "getExcursionDate", "getDateString", "getWhen");
                }
                // optional: get vacation id from excursion model if available
                if (mVacationId == -1L) {
                    String vid = safeString(ex, "getVacationId", "getVacation", "getVacation_id");
                    try {
                        if (!vid.isEmpty()) mVacationId = Long.parseLong(vid);
                    } catch (Exception ignored) { }
                }
            }
        }

        if (excursionBodyView != null) excursionBodyView.setText(mExcursionTitle != null ? mExcursionTitle : "");
        if (pageTitleView != null && mExcursionTitle != null && !mExcursionTitle.isEmpty()) pageTitleView.setText(mExcursionTitle);
        if (startView != null) startView.setText(mExcursionDate != null ? mExcursionDate : "");
        if (titleView != null && mVacationTitle != null) titleView.setText(mVacationTitle);
    }

    // Add this helper to the class (same implementation style as in VacationDetailActivity)
    private String safeString(Object obj, String... methodNames) {
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

    private Excursions findExcursionById(long id) {
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return null;
        try {
            return repo.getExcursion(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    // --- Notification prefs & helpers for excursions ---

    private boolean getNotifyPrefExc(long excursionId) {
        if (excursionId == -1L) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_NOTIFY_PREFIX_EXC + excursionId, false);
    }

    private void setNotifyPrefExc(long excursionId, boolean enabled) {
        if (excursionId == -1L) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_NOTIFY_PREFIX_EXC + excursionId, enabled).apply();
    }

    private int alarmRequestCodeForExcId(long id) {
        return (int) ((id ^ (id >>> 32)) ^ 0xFEED1234);
    }

    private void scheduleNotificationForExcursion() {
        if (mExcursionDate == null || mExcursionId == -1L) return;
        Date date = parseDateFlexible(mExcursionDate);
        if (date == null) return;

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long triggerAt = cal.getTimeInMillis();
        if (triggerAt <= System.currentTimeMillis()) {
            sendImmediateNotificationForExcursion();
            return;
        }

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, NotificationReceiver.class);
        // send as the receiver expects: use the same keys so receiver can render text
        intent.putExtra("vacation_id", mExcursionId);
        intent.putExtra("vacation_title", mExcursionTitle != null ? mExcursionTitle : "");
        intent.putExtra("vacation_start", mExcursionDate != null ? mExcursionDate : "");
        PendingIntent pi = PendingIntent.getBroadcast(this, alarmRequestCodeForExcId(mExcursionId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException se) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    private void cancelScheduledNotificationForExcursion() {
        if (mExcursionId == -1L) return;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NotificationReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, alarmRequestCodeForExcId(mExcursionId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    private void sendImmediateNotificationForExcursion() {
        NotificationReceiver.createNotificationChannel(this);

        Intent intent = new Intent(this, ExcursionDetailActivity.class);
        intent.putExtra("vacation_id", mExcursionId);
        PendingIntent contentIntent = PendingIntent.getActivity(this, alarmRequestCodeForExcId(mExcursionId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, NotificationReceiver.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(mExcursionTitle != null ? mExcursionTitle : "Excursion")
                        .setContentText("Excursion date: " + (mExcursionDate != null ? mExcursionDate : ""))
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true);

        NotificationManagerCompat nm = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        try {
            nm.notify(alarmRequestCodeForExcId(mExcursionId), builder.build());
        } catch (SecurityException ignored) { }
    }

    // small local date parser (matches VacationDetailActivity patterns)
    private Date parseDateFlexible(String s) {
        if (s == null) return null;
        String[] patterns = {"yyyy-MM-dd", "MM/dd/yyyy", "MMM d, yyyy", "M/d/yyyy"};
        for (String p : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.getDefault());
                fmt.setLenient(false);
                return fmt.parse(s);
            } catch (ParseException ignored) { }
        }
        try {
            long millis = Long.parseLong(s);
            return new Date(millis);
        } catch (Exception ignored) { }
        return null;
    }

    private boolean isDateToday(String dateStr) {
        if (dateStr == null) return false;
        Date d = parseDateFlexible(dateStr);
        if (d == null) return false;
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c2.setTime(d);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}
