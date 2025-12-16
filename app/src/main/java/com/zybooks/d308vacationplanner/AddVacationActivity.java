package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class AddVacationActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 5001;
    private boolean mPendingScheduleAfterPermission = false;
    private long mPendingVacId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_vacation);

        EditText titleInput = findViewById(R.id.edit_title);
        EditText accomInput = findViewById(R.id.edit_accommodation);
        EditText startInput = findViewById(R.id.edit_start_date);
        EditText endInput = findViewById(R.id.edit_end_date);
        Button saveBtn = findViewById(R.id.btn_save_vacation);
        Button cancelBtn = findViewById(R.id.btn_cancel_vacation);

        saveBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String accom = accomInput.getText().toString().trim();
            String start = startInput.getText().toString().trim();
            String end = endInput.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            String pattern = "\\d{4}-\\d{2}-\\d{2}";
            if (!start.matches(pattern) || !end.matches(pattern)) {
                Toast.makeText(this, "Dates must be formatted `YYYY-MM-DD`", Toast.LENGTH_SHORT).show();
                return;
            }

            LocalDate startDate;
            LocalDate endDate;
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
                startDate = LocalDate.parse(start, fmt);
                endDate = LocalDate.parse(end, fmt);
            } catch (DateTimeParseException ex) {
                Toast.makeText(this, "Invalid date values; use `YYYY-MM-DD`", Toast.LENGTH_SHORT).show();
                return;
            }

            LocalDate today = LocalDate.now();
            if (startDate.isBefore(today)) {
                Toast.makeText(this, "Start date must be today or later", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!endDate.isAfter(startDate)) {
                Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
                return;
            }

            Vacations vacation = new Vacations(title, accom, start, end);

            VacationRepository repo = VacationRepository.getInstance(this);
            boolean added = tryAddVacation(repo, vacation);

            Intent result = new Intent();
            if (added) {
                Toast.makeText(this, "Vacation Added", Toast.LENGTH_SHORT).show();
                if (vacation.getId() != null) {
                    result.putExtra(EditVacationActivity.EXTRA_VACATION_ID, vacation.getId());
                }
                setResult(RESULT_OK, result);

                if (vacation.getId() != null) {
                    long vacId = vacation.getId();
                    // Runtime notification permission (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED) {
                            NotificationScheduler.scheduleVacationNotifications(this, vacId, title, start, end);
                            finish();
                        } else {
                            // defer scheduling until permission result; don't finish now so we get callback
                            mPendingScheduleAfterPermission = true;
                            mPendingVacId = vacId;
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQ_NOTIF);
                        }
                    } else {
                        NotificationScheduler.scheduleVacationNotifications(this, vacId, title, start, end);
                        finish();
                    }
                } else {
                    // repo didn't provide id: run a DB-wide check or just finish
                    NotificationScheduler.checkDatabaseAndNotifyToday(this);
                    finish();
                }
            } else {
                Toast.makeText(this, "Unable to add vacation", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && mPendingScheduleAfterPermission && mPendingVacId != -1L) {
                // schedule now that permission granted
                // title/start/end are not stored here; schedule by vacId (repository can be queried if needed)
                VacationRepository repo = VacationRepository.getInstance(this);
                String title = "";
                String start = "";
                String end = "";
                if (repo != null) {
                    try {
                        List<Vacations> list = repo.getVacations();
                        if (list != null) {
                            for (Vacations v : list) {
                                if (v != null && v.getId() != null && v.getId() == mPendingVacId) {
                                    title = v.getTitle() != null ? v.getTitle() : "";
                                    start = v.getStartDate() != null ? v.getStartDate() : "";
                                    end = v.getEndDate() != null ? v.getEndDate() : "";
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }
                NotificationScheduler.scheduleVacationNotifications(this, mPendingVacId, title, start, end);
            }
            // clear pending state and finish activity
            mPendingScheduleAfterPermission = false;
            mPendingVacId = -1L;
            finish();
        }
    }

    // Try common repo insert/add method names, otherwise add to returned list if modifiable.
    private boolean tryAddVacation(Object repo, Vacations vac) {
        if (repo == null) return false;
        try {
            // common add method names
            try {
                Method m = repo.getClass().getMethod("addVacation", Vacations.class);
                Object res = m.invoke(repo, vac);
                if (res instanceof Boolean) return (Boolean) res;
                return res == null || Boolean.TRUE.equals(res);
            } catch (NoSuchMethodException ignored) { }

            try {
                Method m = repo.getClass().getMethod("add", Vacations.class);
                Object res = m.invoke(repo, vac);
                if (res instanceof Boolean) return (Boolean) res;
                return res == null || Boolean.TRUE.equals(res);
            } catch (NoSuchMethodException ignored) { }

            // fallback: if getVacations() returns a modifiable list, add to it
            try {
                Method getMethod = repo.getClass().getMethod("getVacations");
                Object res = getMethod.invoke(repo);
                if (res instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Vacations> list = (List<Vacations>) res;
                    return list.add(vac);
                }
            } catch (NoSuchMethodException ignored) { }

        } catch (Exception ignored) { }
        return false;
    }
}
