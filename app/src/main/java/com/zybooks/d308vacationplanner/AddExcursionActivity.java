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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddExcursionActivity extends AppCompatActivity {
    private static final String TAG = "AddExcursionActivity";
    private static final int REQ_NOTIF = 6001;
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    // pending info for deferred scheduling if permission requested
    private boolean mPendingScheduleAfterPermission = false;
    private long mPendingVacationId = -1L;
    private String mPendingExcursionTitle = null;
    private String mPendingExcursionDate = null;

    private EditText mTitleInput;
    private EditText mDateInput;
    private long mParentVacationId = -1L; // passed via intent
    private String mVacationStartStr = null;
    private String mVacationEndStr = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_excursion);

        mTitleInput = findViewById(R.id.edit_excursion_title);
        mDateInput = findViewById(R.id.edit_excursion_date);
        Button saveBtn = findViewById(R.id.btn_save_excursion);
        Button cancelBtn = findViewById(R.id.btn_cancel_excursion);

        Intent intent = getIntent();
        if (intent != null) {
            mParentVacationId = intent.getLongExtra("vacation_id", -1L);
            mVacationStartStr = intent.getStringExtra("vacation_start_date");
            mVacationEndStr = intent.getStringExtra("vacation_end_date");
        }

        saveBtn.setOnClickListener(v -> {
            String title = mTitleInput.getText() == null ? "" : mTitleInput.getText().toString().trim();
            String date = mDateInput.getText() == null ? "" : mDateInput.getText().toString().trim();

            if (title.isEmpty() || date.isEmpty()) {
                setResult(RESULT_CANCELED);
                return;
            }

            // Validate date against vacation range
            if (!isDateInRange(date, mVacationStartStr, mVacationEndStr)) {
                Toast.makeText(this, "Excursion date must be between vacation start and end.", Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                return;
            }

            // Use model setters directly
            Excursions ex = new Excursions();
            ex.setTitle(title);
            ex.setExcursionDate(date);
            ex.setVacationId(mParentVacationId == -1L ? null : Long.valueOf(mParentVacationId));

            VacationRepository repo = VacationRepository.getInstance(this);
            boolean saved = persistNewExcursion(repo, ex);

            if (!saved) {
                Log.w(TAG, "Failed to persist new excursion");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            // If runtime permission required, request and defer scheduling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    mPendingScheduleAfterPermission = true;
                    mPendingVacationId = mParentVacationId;
                    mPendingExcursionTitle = title;
                    mPendingExcursionDate = date;
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    return; // wait for permission result before finishing so we can schedule if granted
                }
            }

            // permission already granted or not required -> schedule now
            NotificationScheduler.scheduleExcursionNotification(this, mParentVacationId, title, date);

            Intent result = new Intent();
            result.putExtra("vacation_id", mParentVacationId);
            setResult(RESULT_OK, result);
            finish();
        });

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    // Helpers: try common repo/DAO insert method names, handle common return types, fallback to list add
    @SuppressWarnings("unchecked")
    private boolean persistNewExcursion(VacationRepository repo, Excursions ex) {
        if (repo == null || ex == null) return false;
        Class<?> cls = repo.getClass();

        // Try insertExcursion(Excursions)
        try {
            Method m = cls.getMethod("insertExcursion", Excursions.class);
            Object res = m.invoke(repo, ex);
            if (res instanceof Number) return ((Number) res).longValue() > 0;
            if (res == null) return true; // void-returning DAO method
            if (res instanceof Boolean) return (Boolean) res;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Log.w(TAG, "invoke insertExcursion failed", e);
        }

        // Try other single-arg names
        String[] names = new String[]{"addExcursion", "add", "insert", "saveExcursion", "save"};
        for (String name : names) {
            try {
                Method m = cls.getMethod(name, Excursions.class);
                Object res = m.invoke(repo, ex);
                if (res instanceof Number) return ((Number) res).longValue() > 0;
                if (res == null) return true;
                if (res instanceof Boolean) return (Boolean) res;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                Log.w(TAG, "invoke " + name + " failed", e);
            }
        }

        // Fallback: try getExcursions() and add to modifiable list
        try {
            Method gm = cls.getMethod("getExcursions");
            Object listObj = gm.invoke(repo);
            if (listObj instanceof List) {
                List<Excursions> list = (List<Excursions>) listObj;
                try {
                    return list.add(ex);
                } catch (UnsupportedOperationException uoe) {
                    Log.w(TAG, "Excursion list not modifiable", uoe);
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Log.w(TAG, "Fallback add to list failed", e);
        }

        return false;
    }

    private boolean isDateInRange(String dateStr, String startStr, String endStr) {
        Date d = parseDateStrict(dateStr);
        if (d == null) return false; // invalid date format -> treat as out of range
        Date s = parseDateStrict(startStr);
        Date e = parseDateStrict(endStr);
        if (s != null && d.before(s)) return false;
        if (e != null && d.after(e)) return false;
        return true;
    }

    private Date parseDateStrict(String s) {
        if (s == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN, Locale.US);
        sdf.setLenient(false);
        try {
            return sdf.parse(s);
        } catch (ParseException e) {
            Log.w(TAG, "Failed to parse date: " + s, e);
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && mPendingScheduleAfterPermission && mPendingVacationId != -1L) {
                NotificationScheduler.scheduleExcursionNotification(this, mPendingVacationId, mPendingExcursionTitle, mPendingExcursionDate);
            }
            // clear pending and finish activity (we already saved excursion)
            mPendingScheduleAfterPermission = false;
            mPendingVacationId = -1L;
            mPendingExcursionTitle = null;
            mPendingExcursionDate = null;

            Intent result = new Intent();
            result.putExtra("vacation_id", mParentVacationId);
            setResult(RESULT_OK, result);
            finish();
        }
    }
}
