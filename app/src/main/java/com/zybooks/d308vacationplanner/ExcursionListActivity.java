package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.ArrayList;
import java.util.List;

public class ExcursionListActivity extends AppCompatActivity {
    private static final String TAG = "ExcursionListActivity";
    private static final int REQUEST_ADD_EXCURSION = 1001;

    private long mVacationId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_list);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("vacation_id")) {
            mVacationId = intent.getLongExtra("vacation_id", -1L);
        }

        // Load all excursions from repository and filter by vacation id
        VacationRepository repo = VacationRepository.getInstance(this);
        List<Excursions> all = repo.getExcursions();
        List<String> display = new ArrayList<>();
        if (all != null) {
            for (Excursions e : all) {
                if (e == null) continue;
                Long vid = e.getVacationId();
                if (vid != null && vid.longValue() == mVacationId) {
                    String title = e.getTitle() != null ? e.getTitle() : "";
                    String date = e.getExcursionDate() != null ? e.getExcursionDate() : "";
                    display.add(title + (date.isEmpty() ? "" : " — " + date));
                }
            }
        }

        // Populate the LinearLayout container defined in excursion_list.xml
        LinearLayout container = findViewById(R.id.vacation_container);
        if (container != null) {
            container.removeAllViews();
            if (display.isEmpty()) {
                TextView empty = createRowTextView("No excursions");
                container.addView(empty);
            } else {
                for (String row : display) {
                    TextView tv = createRowTextView(row);
                    container.addView(tv);
                }
            }
        } else {
            Log.w(TAG, "LinearLayout with id R.id.vacation_container not found; cannot display excursions.");
        }

        // Find title view (excursion list title) safely
        TextView titleView = findViewById(R.id.excursion_title);

        View addVacationButton = findViewById(R.id.add_excursion);
        if (addVacationButton != null) {
            addVacationButton.setOnClickListener(v -> {
                Intent addIntent = new Intent(ExcursionListActivity.this, AddExcursionActivity.class);

                // pass current visible fields so parent can be recreated and still show the same content
                String titleText = titleView != null ? titleView.getText().toString() : "";
                addIntent.putExtra("vacation_title", titleText);

                TextView accomView = findViewById(R.id.detail_accommodation);
                TextView startView = findViewById(R.id.detail_start_date);
                TextView endView = findViewById(R.id.detail_end_date);
                addIntent.putExtra("vacation_accommodation", accomView != null ? accomView.getText().toString() : "");
                addIntent.putExtra("vacation_start_date", startView != null ? startView.getText().toString() : "");
                addIntent.putExtra("vacation_end_date", endView != null ? endView.getText().toString() : "");

                long vacationIdToSend = mVacationId;
                addIntent.putExtra("vacation_id", vacationIdToSend);

                startActivityForResult(addIntent, REQUEST_ADD_EXCURSION);
            });
        } else {
            Log.w(TAG, "Add excursion button (R.id.add_excursion) not found.");
        }
    }

    private TextView createRowTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int pad = dpToPx(8);
        tv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
