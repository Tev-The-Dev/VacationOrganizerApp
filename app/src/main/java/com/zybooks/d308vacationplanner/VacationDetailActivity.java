package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

public class VacationDetailActivity extends AppCompatActivity {
    private static final int REQUEST_ADD_EXCURSION = 1001;
    private static final int REQUEST_EDIT_VACATION = 2001;
    private static final int REQUEST_DELETE_VACATION = 3001;
    private static final int REQUEST_VIEW_EXCURSIONS = 4001;

    private static final String PREFS_NAME = "vacation_prefs";
    private static final String PREF_NOTIFY_PREFIX = "notify_";

    private static final String KEY_VACATION_ID = "key_vacation_id";
    private static final String KEY_VACATION_TITLE = "key_vacation_title";
    private static final String KEY_VACATION_ACCOM = "key_vacation_accom";
    private static final String KEY_VACATION_START = "key_vacation_start";
    private static final String KEY_VACATION_END = "key_vacation_end";

    private long mId = -1L;
    private Vacations mFound = null;

    private String mTitle = null;
    private String mAccommodation = null;
    private String mStartDate = null;
    private String mEndDate = null;

    private SwitchCompat notifySwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_detail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            mId = savedInstanceState.getLong(KEY_VACATION_ID, -1L);
            mTitle = savedInstanceState.getString(KEY_VACATION_TITLE);
            mAccommodation = savedInstanceState.getString(KEY_VACATION_ACCOM);
            mStartDate = savedInstanceState.getString(KEY_VACATION_START);
            mEndDate = savedInstanceState.getString(KEY_VACATION_END);
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                mId = intent.getLongExtra("vacation_id", -1L);
                if (mId == -1L) {
                    // try alternative key
                    mId = intent.getLongExtra("vacationId", -1L);
                }
                mTitle = intent.getStringExtra("vacation_title");
                mAccommodation = intent.getStringExtra("vacation_accom");
                mStartDate = intent.getStringExtra("vacation_start_date");
                mEndDate = intent.getStringExtra("vacation_end_date");
            }
        }

        // Wire notification toggle (manual-only)
        notifySwitch = findViewById(R.id.switch_notifications);
        if (notifySwitch != null) {
            boolean enabled = getNotifyPref(mId);
            notifySwitch.setChecked(enabled);

            // IMPORTANT: removed automatic scheduling on load.
            // Manual-only: only act when user toggles the switch.
            notifySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setNotifyPref(mId, isChecked);
                if (isChecked) {
                    // If today is either start or end date, notify immediately; otherwise schedule for start date
                    if (isDateToday(mStartDate) || isDateToday(mEndDate)) {
                        sendImmediateNotification();
                    } else {
                        scheduleNotificationForStartDate();
                    }
                    Toast.makeText(this, "Vacation notifications enabled", Toast.LENGTH_SHORT).show();
                } else {
                    // turn off: cancel any scheduled alarm
                    cancelScheduledNotification();
                    Toast.makeText(this, "Vacation notifications disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Edit/delete/view excursions wiring (kept)
        Button viewExcursionsBtn = findViewById(R.id.btn_view_excursions);
        if (viewExcursionsBtn != null) {
            viewExcursionsBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, ExcursionListActivity.class);
                TextView titleView = findViewById(R.id.detail_title);
                String titleText = (titleView != null && titleView.getText() != null) ? titleView.getText().toString() : "";
                intent.putExtra("vacation_title", titleText);

                long vacationIdToSend = -1L;
                if (mFound != null && mFound.getId() != null) {
                    vacationIdToSend = mFound.getId();
                } else if (mId != -1L) {
                    vacationIdToSend = mId;
                }
                intent.putExtra("vacation_id", vacationIdToSend);

                startActivityForResult(intent, REQUEST_VIEW_EXCURSIONS);
            });
        }

        Button editVacationBtn = findViewById(R.id.edit_vacation_button);
        if (editVacationBtn != null) {
            editVacationBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, EditVacationActivity.class);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ID, mId);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_TITLE, mTitle != null ? mTitle : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ACCOM, mAccommodation != null ? mAccommodation : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_START, mStartDate != null ? mStartDate : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_END, mEndDate != null ? mEndDate : "");
                startActivityForResult(intent, REQUEST_EDIT_VACATION);
            });
        }

        Button deleteVacationBtn = findViewById(R.id.delete_vacation_button);
        if (deleteVacationBtn != null) {
            deleteVacationBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, DeleteVacationActivity.class);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ID, mId);
                startActivityForResult(intent, REQUEST_DELETE_VACATION);
            });
        }

        reloadVacationDetails();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        android.view.MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_vacation_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_share) {
            sendVacationByEmail();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendVacationByEmail() {
        String subject = buildShareSubject();
        String body = buildShareBody();

        String uriText = "mailto:?subject=" + android.net.Uri.encode(subject) + "&body=" + android.net.Uri.encode(body);
        android.net.Uri mailUri = android.net.Uri.parse(uriText);
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, mailUri);

        try {
            startActivity(emailIntent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No email app available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If the delete activity reported success, propagate result to parent and finish
        if (requestCode == REQUEST_DELETE_VACATION) {
            if (resultCode == RESULT_OK) {
                Intent out = new Intent();
                if (data != null && data.getExtras() != null) {
                    out.putExtras(data.getExtras());
                }
                setResult(RESULT_OK, out);
                finish();
                return;
            }
            // If delete was canceled, continue to reload details below
        }

        // keep existing flows unchanged; refresh displayed data
        reloadVacationDetails();
        // do NOT auto-schedule on return; leave manual control to the switch listener
        // update switch checked state to reflect prefs after possible edits/deletes
        if (notifySwitch != null) {
            boolean enabled = getNotifyPref(mId);
            notifySwitch.setChecked(enabled);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_VACATION_ID, mId);
        outState.putString(KEY_VACATION_TITLE, mTitle);
        outState.putString(KEY_VACATION_ACCOM, mAccommodation);
        outState.putString(KEY_VACATION_START, mStartDate);
        outState.putString(KEY_VACATION_END, mEndDate);
    }

    private void reloadVacationDetails() {
        mFound = null;
        VacationRepository repo = VacationRepository.getInstance(this);

        if (mId != -1L && repo != null) {
            List<Vacations> list = repo.getVacations();
            if (list != null) {
                for (Vacations v : list) {
                    if (v != null && v.getId() != null && v.getId().longValue() == mId) {
                        mFound = v;
                        break;
                    }
                }
            }
        }

        TextView titleView = findViewById(R.id.detail_title);
        TextView accomView = findViewById(R.id.detail_accommodation);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView endView = findViewById(R.id.detail_end_date);

        if (mFound != null) {
            if (titleView != null) titleView.setText(safeString(mFound, "getTitle"));
            if (accomView != null) accomView.setText(safeString(mFound, "getAccommodation", "getAccomodation"));
            if (startView != null) startView.setText(safeString(mFound, "getStartDate", "getStart"));
            if (endView != null) endView.setText(safeString(mFound, "getEndDate", "getEnd"));

            mTitle = mFound.getTitle();
            mAccommodation = safeString(mFound, "getAccommodation", "getAccomodation");
            mStartDate = safeString(mFound, "getStartDate", "getStart");
            mEndDate = safeString(mFound, "getEndDate", "getEnd");
        } else {
            if (titleView != null) titleView.setText(mTitle != null ? mTitle : "");
            if (accomView != null) accomView.setText(mAccommodation != null ? mAccommodation : "");
            if (startView != null) startView.setText(mStartDate != null ? mStartDate : "");
            if (endView != null) endView.setText(mEndDate != null ? mEndDate : "");
        }
    }

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

    private String buildShareSubject() {
        String title = mTitle != null ? mTitle : "";
        String start = mStartDate != null ? mStartDate : "";
        String end = mEndDate != null ? mEndDate : "";
        if (!start.isEmpty() || !end.isEmpty()) {
            return "Vacation: " + title + " (" + start + " - " + end + ")";
        }
        return "Vacation: " + title;
    }

    private String buildShareBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(mTitle != null ? mTitle : "").append("\n");
        sb.append("Accommodation: ").append(mAccommodation != null ? mAccommodation : "").append("\n");
        sb.append("Start: ").append(mStartDate != null ? mStartDate : "").append("\n");
        sb.append("End: ").append(mEndDate != null ? mEndDate : "").append("\n\n");
        sb.append("Excursions:\n");

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo != null) {
            try {
                List<Excursions> excursions = repo.getExcursions();
                if (excursions != null && !excursions.isEmpty()) {
                    for (Excursions e : excursions) {
                        if (e != null && e.getVacationId() != null && e.getVacationId().longValue() == mId) {
                            String title = safeString(e, "getTitle");
                            String date = safeString(e, "getExcursionDate");
                            sb.append("  - ").append(title);
                            if (date != null && !date.isEmpty()) {
                                sb.append(" (").append(date).append(")");
                            }
                            sb.append("\n");
                        }
                    }
                } else {
                    sb.append("  (no excursions)\n");
                }
            } catch (Exception ignored) {
                sb.append("  (unable to read excursions)\n");
            }
        } else {
            sb.append("  (no repository)\n");
        }

        return sb.toString();
    }

    private boolean getNotifyPref(long vacationId) {
        if (vacationId == -1L) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_NOTIFY_PREFIX + vacationId, false);
    }

    private void setNotifyPref(long vacationId, boolean enabled) {
        if (vacationId == -1L) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_NOTIFY_PREFIX + vacationId, enabled).apply();
    }

    // Existing scheduling helpers are retained but are only triggered by the manual switch.
    private int alarmRequestCodeForId(long id) {
        return (int) (id ^ (id >>> 32));
    }

    private void scheduleNotificationForStartDate() {
        if (mStartDate == null || mId == -1L) return;

        Date date = parseDateFlexible(mStartDate);
        if (date == null) return;

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long triggerAt = cal.getTimeInMillis();
        if (triggerAt <= System.currentTimeMillis()) {
            // if the scheduled time has already passed, show immediate notification
            sendImmediateNotification();
            return;
        }

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("vacation_id", mId);
        intent.putExtra("vacation_title", mTitle != null ? mTitle : "");
        intent.putExtra("vacation_start", mStartDate != null ? mStartDate : "");
        PendingIntent pi = PendingIntent.getBroadcast(this, alarmRequestCodeForId(mId), intent,
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

    private void cancelScheduledNotification() {
        if (mId == -1L) return;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NotificationReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, alarmRequestCodeForId(mId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    private void sendImmediateNotification() {
        NotificationReceiver.createNotificationChannel(this);

        Intent intent = new Intent(this, VacationDetailActivity.class);
        intent.putExtra("vacation_id", mId);
        PendingIntent contentIntent = PendingIntent.getActivity(this, alarmRequestCodeForId(mId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Choose appropriate message depending on whether today is start or end
        String whenText = "Vacation";
        if (isDateToday(mStartDate)) {
            whenText = "Vacation starts: " + (mStartDate != null ? mStartDate : "");
        } else if (isDateToday(mEndDate)) {
            whenText = "Vacation ends: " + (mEndDate != null ? mEndDate : "");
        } else {
            whenText = "Vacation starts: " + (mStartDate != null ? mStartDate : "");
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, NotificationReceiver.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(mTitle != null ? mTitle : "Vacation")
                .setContentText(whenText)
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
            nm.notify(alarmRequestCodeForId(mId), builder.build());
        } catch (SecurityException ignored) { }
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
}
