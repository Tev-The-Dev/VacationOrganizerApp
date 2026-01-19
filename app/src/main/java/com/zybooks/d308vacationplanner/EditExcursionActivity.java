package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditExcursionActivity extends AppCompatActivity {
    private static final String TAG = "EditExcursionActivity";
    private static final int REQ_NOTIF = 7001;
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    public static final String EXTRA_EXCURSION_ID = "excursion_id";
    public static final String EXTRA_EXCURSION_TITLE = "excursion_title";
    public static final String EXTRA_EXCURSION_DATE = "excursion_date";
    public static final String EXTRA_VACATION_START = "vacation_start_date";
    public static final String EXTRA_VACATION_END = "vacation_end_date";

    private boolean mPendingScheduleAfterPermission = false;
    private long mPendingVacationId = -1L;
    private String mPendingExcursionTitle = null;
    private String mPendingExcursionDate = null;

    private EditText mTitleInput;
    private EditText mDateInput;
    private long mExcursionId = -1L;
    private long mParentVacationId = -1L;
    private String mVacationStartStr = null;
    private String mVacationEndStr = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_excursion);

        mTitleInput = findViewById(R.id.edit_excursion_title);
        mDateInput = findViewById(R.id.edit_excursion_date);
        Button saveBtn = findViewById(R.id.btn_save_excursion);
        Button cancelBtn = findViewById(R.id.btn_cancel_excursion);

        Intent intent = getIntent();
        if (intent != null) {
            mExcursionId = intent.getLongExtra(EXTRA_EXCURSION_ID, -1L);
            mParentVacationId = intent.getLongExtra("vacation_id", -1L);
            String title = intent.getStringExtra(EXTRA_EXCURSION_TITLE);
            String date = intent.getStringExtra(EXTRA_EXCURSION_DATE);
            mVacationStartStr = intent.getStringExtra(EXTRA_VACATION_START);
            mVacationEndStr = intent.getStringExtra(EXTRA_VACATION_END);
            if (title != null) mTitleInput.setText(title);
            if (date != null) mDateInput.setText(date);
        }

        saveBtn.setOnClickListener(v -> {
            String title = safeText(mTitleInput);
            String date = safeText(mDateInput);

            if (title.isEmpty() || date.isEmpty()) {
                setResult(RESULT_CANCELED);
                return;
            }

            // Validate date format and that date is on or after today
            ValidationResult dateRes = validateDateOnOrAfterTodayStrict(date);
            if (!dateRes.valid) {
                mDateInput.setError(dateRes.message);
                Toast.makeText(this, dateRes.message, Toast.LENGTH_LONG).show();
                return;
            } else {
                mDateInput.setError(null);
            }

            // Validate date is within vacation start/end bounds (if provided)
            Long dateMillis = parseYmdToMillis(date);
            Long startMillis = parseYmdToMillis(mVacationStartStr);
            Long endMillis = parseYmdToMillis(mVacationEndStr);
            if (startMillis != null && dateMillis != null && dateMillis < startMillis) {
                String msg = "Excursion date must be on or after vacation start date";
                mDateInput.setError(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }
            if (endMillis != null && dateMillis != null && dateMillis > endMillis) {
                String msg = "Excursion date must be on or before vacation end date";
                mDateInput.setError(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }
            mDateInput.setError(null);

            VacationRepository repo = VacationRepository.getInstance(this);
            boolean updated = false;
            Excursions target = null;

            if (repo != null) {
                try {
                    List<Excursions> list = repo.getExcursions();
                    if (list != null) {
                        for (Excursions ex : list) {
                            if (ex == null) continue;
                            Long id = ex.getId();
                            if (id != null && id.longValue() == mExcursionId) {
                                // update in-memory object
                                try {
                                    ex.setTitle(title);
                                } catch (Exception ignored) { }
                                try {
                                    ex.setExcursionDate(date);
                                } catch (Exception ignored) { }
                                target = ex;
                                updated = true;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed locating excursion to edit", e);
                }
            }

            if (target == null) {
                Log.w(TAG, "Excursion to edit not found");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            boolean persisted = persistEditedExcursion(repo, target);
            if (!persisted) {
                Log.w(TAG, "Failed to persist edited excursion via repository");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            // schedule or defer notification similar to add flow
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    mPendingScheduleAfterPermission = true;
                    mPendingVacationId = mParentVacationId;
                    mPendingExcursionTitle = title;
                    mPendingExcursionDate = date;
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    return;
                }
            }

            NotificationScheduler.scheduleExcursionNotification(this, mParentVacationId, title, date);

            Intent result = new Intent();
            result.putExtra(EXTRA_EXCURSION_ID, mExcursionId);
            result.putExtra("vacation_id", mParentVacationId);
            // Add updated fields so caller can refresh immediately
            result.putExtra(EXTRA_EXCURSION_TITLE, title);
            result.putExtra(EXTRA_EXCURSION_DATE, date);
            setResult(RESULT_OK, result);
            finish();
        });

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    // Try common repository update/save method names via reflection.
    private boolean persistEditedExcursion(VacationRepository repo, Excursions ex) {
        if (repo == null || ex == null) return false;
        Class<?> cls = repo.getClass();

        // Try updateExcursion(Excursions) - often void
        try {
            Method m = cls.getMethod("updateExcursion", Excursions.class);
            m.invoke(repo, ex);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Log.w(TAG, "invoke updateExcursion failed", e);
        }

        // Try other names
        String[] names = new String[]{"update", "editExcursion", "saveExcursion", "save", "upsertExcursion"};
        for (String name : names) {
            try {
                Method m = cls.getMethod(name, Excursions.class);
                Object res = m.invoke(repo, ex);
                if (res == null) return true;
                if (res instanceof Boolean) return (Boolean) res;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                Log.w(TAG, "invoke " + name + " failed", e);
            }
        }

        // Try (long id, Excursions) variants
        Long idVal = ex.getId();
        long idPrim = (idVal == null) ? -1L : idVal.longValue();
        String[] twoArgNames = new String[]{"updateExcursion", "update", "replaceExcursion"};
        for (String nm : twoArgNames) {
            try {
                Method m = cls.getMethod(nm, long.class, Excursions.class);
                Object res = m.invoke(repo, idPrim, ex);
                if (res == null) return true;
                if (res instanceof Boolean) return (Boolean) res;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                Log.w(TAG, "invoke " + nm + "(long, Excursions) failed", e);
            }
            try {
                Method m = cls.getMethod(nm, Long.class, Excursions.class);
                Object res = m.invoke(repo, idVal, ex);
                if (res == null) return true;
                if (res instanceof Boolean) return (Boolean) res;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                Log.w(TAG, "invoke " + nm + "(Long, Excursions) failed", e);
            }
        }

        // Fallback: replace in getExcursions() list or update fields on existing object
        try {
            Method gm = cls.getMethod("getExcursions");
            Object listObj = gm.invoke(repo);
            if (listObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Excursions> list = (List<Excursions>) listObj;
                if (idVal != null) {
                    for (int i = 0; i < list.size(); i++) {
                        Excursions cur = list.get(i);
                        if (cur != null && cur.getId() != null && cur.getId().longValue() == idVal.longValue()) {
                            // Try direct replace
                            try {
                                list.set(i, ex);
                                return true;
                            } catch (UnsupportedOperationException uoe) {
                                // Try updating fields on existing object via setters
                                try {
                                    Method setTitle = cur.getClass().getMethod("setTitle", String.class);
                                    setTitle.invoke(cur, ex.getTitle());
                                } catch (Exception ignored) { }
                                try {
                                    Method setDate = cur.getClass().getMethod("setExcursionDate", String.class);
                                    setDate.invoke(cur, ex.getExcursionDate());
                                } catch (Exception ignored) { }
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Log.w(TAG, "Fallback update to list failed", e);
        }

        return false;
    }

    private String safeText(EditText et) {
        return et == null ? "" : (et.getText() == null ? "" : et.getText().toString().trim());
    }

    // Validation: require format YYYY-MM-DD and date on or after today (local).
    private ValidationResult validateDateOnOrAfterTodayStrict(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new ValidationResult(false, "Date required");
        }

        String s = dateStr.trim();

        // enforce exact format YYYY-MM-DD
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return new ValidationResult(false, "Use format YYYY-MM-DD");
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());
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
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && mPendingScheduleAfterPermission && mPendingVacationId != -1L) {
                NotificationScheduler.scheduleExcursionNotification(this, mPendingVacationId, mPendingExcursionTitle, mPendingExcursionDate);
            }
            mPendingScheduleAfterPermission = false;
            mPendingVacationId = -1L;
            mPendingExcursionTitle = null;
            mPendingExcursionDate = null;

            Intent result = new Intent();
            result.putExtra(EXTRA_EXCURSION_ID, mExcursionId);
            result.putExtra("vacation_id", mParentVacationId);
            // Provide updated title/date so caller refreshes immediately
            result.putExtra(EXTRA_EXCURSION_TITLE, safeText(mTitleInput));
            result.putExtra(EXTRA_EXCURSION_DATE, safeText(mDateInput));
            setResult(RESULT_OK, result);
            finish();
        }
    }
}
