// java
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

import java.util.List;

public class EditExcursionActivity extends AppCompatActivity {
    private static final int REQ_NOTIF = 7001;

    // Public extras used by other activities (ExcursionListActivity expects these)
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
            if (title != null) mTitleInput.setText(title);
            if (date != null) mDateInput.setText(date);
        }

        saveBtn.setOnClickListener(v -> {
            String title = mTitleInput.getText() == null ? "" : mTitleInput.getText().toString().trim();
            String date = mDateInput.getText() == null ? "" : mDateInput.getText().toString().trim();

            if (title.isEmpty() || date.isEmpty()) {
                setResult(RESULT_CANCELED);
                return;
            }

            // persist edits to repo (best-effort)
            VacationRepository repo = VacationRepository.getInstance(this);
            boolean updated = false;
            if (repo != null) {
                try {
                    java.lang.reflect.Method gm = repo.getClass().getMethod("getExcursions");
                    Object res = gm.invoke(repo);
                    if (res instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Excursions> list = (List<Excursions>) res;
                        for (Excursions ex : list) {
                            Long id = null;
                            try {
                                java.lang.reflect.Method mid = ex.getClass().getMethod("getId");
                                Object o = mid.invoke(ex);
                                if (o instanceof Long) id = (Long) o;
                            } catch (Exception ignored) { }
                            if (id != null && id == mExcursionId) {
                                // try common setters
                                try {
                                    java.lang.reflect.Method setTitle = ex.getClass().getMethod("setTitle", String.class);
                                    setTitle.invoke(ex, title);
                                } catch (Exception ignored) { }
                                try {
                                    // candidate names for date field
                                    java.lang.reflect.Method setDate = null;
                                    try { setDate = ex.getClass().getMethod("setExcursionDate", String.class); } catch (NoSuchMethodException ignored){}
                                    try { if (setDate == null) setDate = ex.getClass().getMethod("setDate", String.class); } catch (NoSuchMethodException ignored){}
                                    if (setDate != null) setDate.invoke(ex, date);
                                } catch (Exception ignored) { }
                                updated = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!updated) {
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
            mPendingScheduleAfterPermission = false;
            mPendingVacationId = -1L;
            mPendingExcursionTitle = null;
            mPendingExcursionDate = null;

            Intent result = new Intent();
            result.putExtra(EXTRA_EXCURSION_ID, mExcursionId);
            result.putExtra("vacation_id", mParentVacationId);
            setResult(RESULT_OK, result);
            finish();
        }
    }
}
