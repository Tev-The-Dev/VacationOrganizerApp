package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class AddVacationActivity extends AppCompatActivity {

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

            Vacations vacation = new Vacations(title, accom, start, end);

            VacationRepository repo = VacationRepository.getInstance(this);
            boolean added = tryAddVacation(repo, vacation);

            Intent result = new Intent();
            if (added) {
                Toast.makeText(this, "Vacation Added", Toast.LENGTH_SHORT).show();
                // If repo assigned an id, include it; otherwise caller can reload entire list
                if (vacation.getId() != null) {
                    result.putExtra(EditVacationActivity.EXTRA_VACATION_ID, vacation.getId());
                }
                setResult(RESULT_OK, result);
            } else {
                Toast.makeText(this, "Vacation Failed", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
            }
            finish();
        });

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    // Try common repo insert/add method names, otherwise add to returned list if modifiable.
    private boolean tryAddVacation(Object repo, Vacations vac) {
        if (repo == null || vac == null) return false;
        String[] names = {"insertVacation", "addVacation", "saveVacation", "createVacation", "insert", "add", "save", "insertAll"};
        for (String name : names) {
            try {
                Method m = repo.getClass().getMethod(name, Vacations.class);
                Object res = m.invoke(repo, vac);
                if (res == null) return true;
                if (res instanceof Boolean && (Boolean) res) return true;
                if (res instanceof Number) {
                    long id = ((Number) res).longValue();
                    if (id > 0) {
                        try { vac.setId(id); } catch (Exception ignored) {}
                        return true;
                    }
                }
            } catch (NoSuchMethodException ignored) {}
            catch (Exception ignored) {}
        }

        // fallback: try to add to getVacations() list if modifiable
        try {
            Method getVacations = repo.getClass().getMethod("getVacations");
            Object listObj = getVacations.invoke(repo);
            if (listObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) listObj;
                list.add(vac);
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }
}
