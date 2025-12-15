package com.zybooks.d308vacationplanner;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

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

            // Use Vacations constructor and setters as needed
            Vacations vacation = new Vacations(title, accom, start, end);

            // Save via repository (adjust method name if your repo uses different API)
            VacationRepository repo = VacationRepository.getInstance(this);
            repo.addVacation(vacation);

            Toast.makeText(this, "Vacation saved", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }
}
