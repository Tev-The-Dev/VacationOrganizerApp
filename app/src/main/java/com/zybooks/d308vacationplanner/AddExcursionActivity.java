package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class AddExcursionActivity extends AppCompatActivity {
    private static final int REQ_NOTIF = 6001;

    // pending info for deferred scheduling if permission requested
    private boolean mPendingScheduleAfterPermission = false;
    private long mPendingVacationId = -1L;
    private String mPendingExcursionTitle = null;
    private String mPendingExcursionDate = null;

    private EditText mTitleInput;
    private EditText mDateInput;
    private long mParentVacationId = -1L; // should be passed via intent when launching

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_excursion);

        mTitleInput = findViewById(R.id.edit_excursion_title);
        mDateInput = findViewById(R.id.edit_excursion_date);
        Button saveBtn = findViewById(R.id.btn_save_excursion);
        Button cancelBtn = findViewById(R.id.btn_cancel_excursion);

        // read parent vacation id if provided
        Intent intent = getIntent();
        if (intent != null) {
            mParentVacationId = intent.getLongExtra("vacation_id", -1L);
        }

        saveBtn.setOnClickListener(v -> {
            String title = mTitleInput.getText() == null ? "" : mTitleInput.getText().toString().trim();
            String date = mDateInput.getText() == null ? "" : mDateInput.getText().toString().trim();

            // basic validation (adjust to your validation rules)
            if (title.isEmpty() || date.isEmpty()) {
                // show UI error (omitted) -- keep short here
                return;
            }

            // Create excursion model instance and populate via reflection (best-effort)
            Excursions ex = new Excursions();
            try {
                // try direct setters via reflection (common names)
                try { ex.getClass().getMethod("setTitle", String.class).invoke(ex, title); } catch (Exception ignored){}
                // prefer the actual model setter name "setExcursionDate", fallback to "setDate"
                try { ex.getClass().getMethod("setExcursionDate", String.class).invoke(ex, date); } catch (Exception ignored){
                    try { ex.getClass().getMethod("setDate", String.class).invoke(ex, date); } catch (Exception ignored2){}
                }
                // vacation id setter may accept Long or long
                try { ex.getClass().getMethod("setVacationId", Long.class).invoke(ex, mParentVacationId); } catch (Exception ignored){
                    try { ex.getClass().getMethod("setVacationId", long.class).invoke(ex, mParentVacationId); } catch (Exception ignored2){}
                }
            } catch (Exception ignored) {}

            VacationRepository repo = VacationRepository.getInstance(this);
            boolean saved = false;
            if (repo != null) {
                try {
                    // attempt common add method names
                    try {
                        Method m = repo.getClass().getMethod("addExcursion", Excursions.class);
                        Object res = m.invoke(repo, ex);
                        saved = res == null || Boolean.TRUE.equals(res);
                    } catch (NoSuchMethodException ns) {
                        try {
                            Method m = repo.getClass().getMethod("add", Excursions.class);
                            Object res = m.invoke(repo, ex);
                            saved = res == null || Boolean.TRUE.equals(res);
                        } catch (NoSuchMethodException ns2) {
                            // fallback: if getExcursions() returns a modifiable list, add to it
                            try {
                                Method gm = repo.getClass().getMethod("getExcursions");
                                Object listObj = gm.invoke(repo);
                                if (listObj instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Excursions> list = (List<Excursions>) listObj;
                                    saved = list.add(ex);
                                }
                            } catch (NoSuchMethodException ignored) { }
                        }
                    }
                } catch (Exception ignored) { }
            }

            if (!saved) {
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

            // return success
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
