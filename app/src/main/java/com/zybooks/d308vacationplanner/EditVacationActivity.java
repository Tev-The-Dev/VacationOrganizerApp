package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Date;

public class EditVacationActivity extends AppCompatActivity {
    private static final int REQ_NOTIF = 2001;

    public static final String EXTRA_VACATION_ID = "vacation_id";
    public static final String EXTRA_VACATION_TITLE = "vacation_title";
    public static final String EXTRA_VACATION_ACCOM = "vacation_accommodation";
    public static final String EXTRA_VACATION_START = "vacation_start_date";
    public static final String EXTRA_VACATION_END = "vacation_end_date";
    public static final String EXTRA_VACATION_DELETE = "vacation_delete";

    private EditText mTitle;
    private EditText mAccommodation;
    private EditText mStartDate;
    private EditText mEndDate;
    private long mVacationId = -1L;

    // pending data used if we must request runtime permission before scheduling
    private boolean mPendingScheduleAfterPermission = false;
    private String mPendingTitle;
    private String mPendingStart;
    private String mPendingEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_vacation);

        mTitle = findViewById(R.id.detail_title);
        mAccommodation = findViewById(R.id.detail_accommodation);
        mStartDate = findViewById(R.id.detail_start_date);
        mEndDate = findViewById(R.id.detail_end_date);

        Button btnSave = findViewById(R.id.btn_save_vacation);
        Button btnCancel = findViewById(R.id.btn_cancel_vacation);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_VACATION_ID)) {
            mVacationId = intent.getLongExtra(EXTRA_VACATION_ID, -1L);
            // Try to populate fields from repository (best-effort).
            VacationRepository repo = VacationRepository.getInstance(this);
            if (repo != null) {
                List<Vacations> vacations = repo.getVacations();
                if (vacations != null) {
                    for (Vacations v : vacations) {
                        if (v != null && v.getId() != null && v.getId() == mVacationId) {
                            if (v.getTitle() != null) mTitle.setText(v.getTitle());
                            if (v.getAccommodation() != null) mAccommodation.setText(v.getAccommodation());
                            if (v.getStartDate() != null) mStartDate.setText(v.getStartDate());
                            if (v.getEndDate() != null) mEndDate.setText(v.getEndDate());
                            break;
                        }
                    }
                }
            }
        }

        btnSave.setOnClickListener(v -> {
            // validate dates: if a date is entered it must be on or after today (YYYY-MM-DD)
            // and if both entered the end must be strictly after the start.
            String startText = safeText(mStartDate);
            String endText = safeText(mEndDate);

            boolean hasError = false;

            ValidationResult startRes = validateDateOnOrAfterTodayStrict(startText);
            if (!startRes.valid) {
                mStartDate.setError(startRes.message);
                hasError = true;
            } else {
                mStartDate.setError(null);
            }

            ValidationResult endRes = validateDateOnOrAfterTodayStrict(endText);
            if (!endRes.valid) {
                mEndDate.setError(endRes.message);
                hasError = true;
            } else {
                mEndDate.setError(null);
            }

            // If both dates present and individually valid, ensure end > start
            if (!hasError && !startText.isEmpty() && !endText.isEmpty()) {
                Long startMillis = parseYmdToMillis(startText);
                Long endMillis = parseYmdToMillis(endText);
                if (startMillis == null || endMillis == null) {
                    if (startMillis == null) { mStartDate.setError("Invalid date"); hasError = true; }
                    if (endMillis == null) { mEndDate.setError("Invalid date"); hasError = true; }
                } else if (endMillis <= startMillis) {
                    mEndDate.setError("End date must be after start date");
                    hasError = true;
                } else {
                    mEndDate.setError(null);
                }
            }

            if (hasError) {
                return;
            }

            // Persist edits into repository (best-effort) before scheduling/returning.
            persistEditsToRepository();

            // Prepare values for result and scheduling
            String title = safeText(mTitle);
            String start = safeText(mStartDate);
            String end = safeText(mEndDate);

            // If runtime notification permission is required and not granted, request it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(EditVacationActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    // store pending data, request permission, and defer scheduling until result
                    mPendingScheduleAfterPermission = true;
                    mPendingTitle = title;
                    mPendingStart = start;
                    mPendingEnd = end;
                    ActivityCompat.requestPermissions(EditVacationActivity.this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQ_NOTIF);
                    // do not finish now — wait for user response so we can schedule if granted
                    return;
                }
            }

            // permission already granted or not required -> schedule now and finish
            NotificationScheduler.scheduleVacationNotifications(
                    EditVacationActivity.this,
                    mVacationId,
                    title,
                    start,
                    end
            );

            Intent result = new Intent();
            result.putExtra(EXTRA_VACATION_ID, mVacationId);
            result.putExtra(EXTRA_VACATION_TITLE, title);
            result.putExtra(EXTRA_VACATION_ACCOM, safeText(mAccommodation));
            result.putExtra(EXTRA_VACATION_START, start);
            result.putExtra(EXTRA_VACATION_END, end);
            setResult(RESULT_OK, result);
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            // If we had a pending schedule, either schedule now (granted) or skip scheduling (denied),
            // but always finish save and return result.
            Intent result = new Intent();
            result.putExtra(EXTRA_VACATION_ID, mVacationId);
            result.putExtra(EXTRA_VACATION_TITLE, mPendingTitle != null ? mPendingTitle : safeText(mTitle));
            result.putExtra(EXTRA_VACATION_ACCOM, safeText(mAccommodation));
            result.putExtra(EXTRA_VACATION_START, mPendingStart != null ? mPendingStart : safeText(mStartDate));
            result.putExtra(EXTRA_VACATION_END, mPendingEnd != null ? mPendingEnd : safeText(mEndDate));
            setResult(RESULT_OK, result);

            if (mPendingScheduleAfterPermission && granted) {
                NotificationScheduler.scheduleVacationNotifications(
                        EditVacationActivity.this,
                        mVacationId,
                        mPendingTitle,
                        mPendingStart,
                        mPendingEnd
                );
            }

            // clear pending and finish
            mPendingScheduleAfterPermission = false;
            mPendingTitle = mPendingStart = mPendingEnd = null;
            finish();
        }
    }

    private String safeText(EditText et) {
        return et == null ? "" : (et.getText() == null ? "" : et.getText().toString().trim());
    }

    // Best-effort persistence: find the matching vacation object and call common setters via reflection.
    private void persistEditsToRepository() {
        if (mVacationId == -1L) {
            // No ID -> likely a new vacation; repository create API varies so skip here.
            return;
        }

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return;

        try {
            Method getVacationsMethod = repo.getClass().getMethod("getVacations");
            Object res = getVacationsMethod.invoke(repo);
            if (!(res instanceof List)) return;
            List<?> list = (List<?>) res;
            for (Object obj : list) {
                if (obj == null) continue;
                try {
                    Method getId = obj.getClass().getMethod("getId");
                    Object idVal = getId.invoke(obj);
                    if (idVal != null && Long.parseLong(String.valueOf(idVal)) == mVacationId) {
                        // Found the vacation object; attempt to set fields via common setter names.
                        safeInvokeSetter(obj, new String[]{"setTitle", "set_name", "set_name_"}, safeText(mTitle));
                        safeInvokeSetter(obj, new String[]{"setAccommodation", "setAccomodation", "setAccommodationName"}, safeText(mAccommodation));
                        safeInvokeSetter(obj, new String[]{"setStartDate", "setStart"}, safeText(mStartDate));
                        safeInvokeSetter(obj, new String[]{"setEndDate", "setEnd"}, safeText(mEndDate));

                        // Try to invoke repository update method if present
                        try {
                            Method update = repo.getClass().getMethod("updateVacation", obj.getClass());
                            update.invoke(repo, obj);
                        } catch (NoSuchMethodException ignored) {
                            // no update method with that signature - ignore
                        } catch (Exception ignored) { }

                        break;
                    }
                } catch (Exception ignored) { }
            }
        } catch (NoSuchMethodException nsme) {
            // repository has no getVacations method signature we expected — nothing we can do reliably
        } catch (Exception ignored) { }
    }

    // Try invoking a setter from candidate names with a single String parameter.
    private void safeInvokeSetter(Object target, String[] candidateNames, String value) {
        if (target == null || candidateNames == null) return;
        for (String name : candidateNames) {
            try {
                Method m = target.getClass().getMethod(name, String.class);
                m.invoke(target, value);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) { }
        }
    }

    // Validation: require format YYYY-MM-DD and date on or after today (local).
    private ValidationResult validateDateOnOrAfterTodayStrict(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            // empty allowed (no date entered)
            return new ValidationResult(true, null);
        }

        String s = dateStr.trim();

        // enforce exact format YYYY-MM-DD
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return new ValidationResult(false, "Use format YYYY-MM-DD");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setLenient(false);
        Date parsed;
        try {
            parsed = sdf.parse(s);
            if (parsed == null) {
                return new ValidationResult(false, "Invalid date");
            }
        } catch (ParseException e) {
            return new ValidationResult(false, "Invalid date");
        }

        Calendar parsedCal = Calendar.getInstance();
        parsedCal.setTime(parsed);
        parsedCal.set(Calendar.HOUR_OF_DAY, 0);
        parsedCal.set(Calendar.MINUTE, 0);
        parsedCal.set(Calendar.SECOND, 0);
        parsedCal.set(Calendar.MILLISECOND, 0);

        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        // allow date equal to today or in the future
        if (parsedCal.getTimeInMillis() < todayStart.getTimeInMillis()) {
            return new ValidationResult(false, "Date must be today or later");
        }

        return new ValidationResult(true, null);
    }

    // Helper to parse YYYY-MM-DD to start-of-day millis (returns null on parse failure)
    private Long parseYmdToMillis(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setLenient(false);
        try {
            Date d = sdf.parse(t);
            if (d == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (ParseException e) {
            return null;
        }
    }

    private static class ValidationResult {
        final boolean valid;
        final String message;
        ValidationResult(boolean v, String m) { valid = v; message = m; }
    }
}
