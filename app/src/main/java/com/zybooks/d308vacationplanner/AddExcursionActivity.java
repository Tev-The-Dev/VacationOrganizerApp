package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

public class AddExcursionActivity extends AppCompatActivity {

    private static final String TAG = "AddExcursionActivity";

    private EditText mTitleInput;
    private EditText mDateInput;
    private Button mSaveBtn;
    private Button mCancelBtn;

    private long mVacationId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_excursion);

        mTitleInput = findViewById(R.id.edit_excursion_title);
        mDateInput = findViewById(R.id.edit_excursion_date);
        mSaveBtn = findViewById(R.id.btn_save_excursion);
        mCancelBtn = findViewById(R.id.btn_cancel_excursion);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("vacation_id")) {
            mVacationId = intent.getLongExtra("vacation_id", -1L);
        }
        Log.d(TAG, "onCreate: received vacation_id=" + mVacationId);

        // Back gesture / hardware back handling using OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        mSaveBtn.setOnClickListener(v -> {
            String title = mTitleInput.getText().toString().trim();
            String date = mDateInput.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mVacationId == -1L) {
                Toast.makeText(this, "No vacation selected", Toast.LENGTH_SHORT).show();
                return;
            }

            Excursions excursion = new Excursions(title, date);
            excursion.setVacationId(mVacationId);

            try {
                VacationRepository repo = VacationRepository.getInstance(this);
                repo.addExcursion(excursion);
                Toast.makeText(this, "Excursion saved", Toast.LENGTH_SHORT).show();

                // Return success and include vacation_id so parent can reliably reload
                Intent data = new Intent();
                data.putExtra("vacation_id", mVacationId);
                setResult(RESULT_OK, data);
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to save excursion: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Intent data = new Intent();
                data.putExtra("excursion_title", title);
                data.putExtra("excursion_date", date);
                data.putExtra("vacation_id", mVacationId);
                setResult(RESULT_CANCELED, data);
                finish();
            }
        });

        mCancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Ensure Up returns to the existing parent instance instead of recreating it.
        setResult(RESULT_CANCELED);
        finish();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
