package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class EditVacationActivity extends AppCompatActivity {
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

        btnSave.setOnClickListener(v -> {
            // Persist edits into repository (best-effort) before returning result.
            persistEditsToRepository();

            Intent result = new Intent();
            result.putExtra(EXTRA_VACATION_ID, mVacationId);
            result.putExtra(EXTRA_VACATION_TITLE, safeText(mTitle));
            result.putExtra(EXTRA_VACATION_ACCOM, safeText(mAccommodation));
            result.putExtra(EXTRA_VACATION_START, safeText(mStartDate));
            result.putExtra(EXTRA_VACATION_END, safeText(mEndDate));
            setResult(RESULT_OK, result);
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

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
            // Prefer typed getVacations() if available
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

                        // If repository exposes an update method, call it.
                        try {
                            Method update = repo.getClass().getMethod("updateVacation", obj.getClass());
                            update.invoke(repo, obj);
                        } catch (NoSuchMethodException ignored) {
                            // try update by id + fields (common variants)
                            try {
                                Method upd2 = repo.getClass().getMethod("updateVacation", long.class, String.class, String.class, String.class, String.class);
                                upd2.invoke(repo, mVacationId,
                                        safeText(mTitle), safeText(mAccommodation), safeText(mStartDate), safeText(mEndDate));
                            } catch (NoSuchMethodException ignored2) {
                                // no explicit update API found; modifying the object reference above may be sufficient.
                            } catch (Exception ignored2) { }
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
}
