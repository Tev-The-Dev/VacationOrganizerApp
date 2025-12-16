package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.List;

public class ExcursionListActivity extends AppCompatActivity {

    private static final int REQ_ADD = 100;
    private static final int REQ_DETAIL = 101;

    private long mVacationId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_list);

        // show Up button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Restore saved state first so the filter persists across recreations
        if (savedInstanceState != null) {
            mVacationId = savedInstanceState.getLong("vacation_id", -1L);
            String vacationTitle = savedInstanceState.getString("vacation_title");
            if (vacationTitle != null && !vacationTitle.isEmpty()) {
                TextView titleView = findViewById(R.id.vacation_title);
                if (titleView != null) titleView.setText(vacationTitle);
            }
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                mVacationId = intent.getLongExtra("vacation_id", -1L);
                String vacationTitle = intent.getStringExtra("vacation_title");
                if (vacationTitle != null && !vacationTitle.isEmpty()) {
                    TextView titleView = findViewById(R.id.vacation_title);
                    if (titleView != null) titleView.setText(vacationTitle);
                }
            }
        }

        View addExcursionsButton = findViewById(R.id.btn_add_vacations);
        if (addExcursionsButton != null) {
            addExcursionsButton.setVisibility(View.VISIBLE);
            addExcursionsButton.setOnClickListener(v -> {
                Intent addIntent = new Intent(ExcursionListActivity.this, AddExcursionActivity.class);
                addIntent.putExtra("vacation_id", mVacationId);
                startActivityForResult(addIntent, REQ_ADD);
            });
        }

        loadExcursions();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("vacation_id", mVacationId);
        TextView titleView = findViewById(R.id.vacation_title);
        if (titleView != null) outState.putString("vacation_title", titleView.getText().toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQ_ADD || requestCode == REQ_DETAIL) && resultCode == RESULT_OK) {
            loadExcursions();
            // Notify parent (VacationDetailActivity) that something changed so it can reload
            setResult(RESULT_OK);
        }
    }

    @Override
    public void onBackPressed() {
        // ensure parent reloads and preserve single instance
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Up pressed - finish and signal parent to reload
            setResult(RESULT_OK);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        setResult(RESULT_OK);
        finish();
        return true;
    }

    private void loadExcursions() {
        LinearLayout container = findViewById(R.id.vacation_container);
        if (container == null) return;

        container.removeAllViews();

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return;

        List<Excursions> excursions = repo.getExcursions();
        if (excursions == null || excursions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No excursions");
            empty.setTextSize(18f);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int added = 0;
        for (final Excursions e : excursions) {
            if (e == null) continue;
            Long vid = e.getVacationId();
            if (mVacationId != -1L) {
                if (vid == null || vid.longValue() != mVacationId) {
                    continue;
                }
            }

            View item = inflater.inflate(R.layout.excursion_item, container, false);

            Button titleBtn = item.findViewById(R.id.item_title);
            String title = (e.getTitle() != null) ? e.getTitle() : "";
            titleBtn.setText(title);

            titleBtn.setOnClickListener(view -> {
                long id = (e.getId() != null) ? e.getId() : -1L;
                Intent intent = new Intent(ExcursionListActivity.this, ExcursionDetailActivity.class);
                intent.putExtra("excursion_id", id);
                intent.putExtra("excursion_title", title);
                intent.putExtra("vacation_id", mVacationId);
                startActivityForResult(intent, REQ_DETAIL);
            });

            container.addView(item);
            added++;
        }

        if (added == 0) {
            TextView empty = new TextView(this);
            empty.setText("No excursions");
            empty.setTextSize(18f);
            container.addView(empty);
        }
    }
}
