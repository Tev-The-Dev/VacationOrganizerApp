package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.List;

public class VacationActivity extends AppCompatActivity {

    private static final int REQ_ADD = 100;
    private static final int REQ_DETAIL = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_list);

        // Wire Add button to start AddVacationActivity for result
        View addVacationsButton = findViewById(R.id.btn_add_vacations);
        if (addVacationsButton != null) {
            addVacationsButton.setVisibility(View.VISIBLE);
            addVacationsButton.setOnClickListener(v ->
                    startActivityForResult(new Intent(VacationActivity.this, AddVacationActivity.class), REQ_ADD));
        }

        // Initial load
        loadVacations();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // If a child activity signaled success (add/edit/delete), refresh the list
        if ((requestCode == REQ_ADD || requestCode == REQ_DETAIL) && resultCode == RESULT_OK) {
            loadVacations();
        }
    }

    // Rebuilds the vacation list UI from the repository
    private void loadVacations() {
        LinearLayout container = findViewById(R.id.vacation_container);
        if (container == null) return;

        container.removeAllViews();

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return;

        List<Vacations> vacations = repo.getVacations();
        if (vacations == null || vacations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No vacations");
            empty.setTextSize(18f);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Vacations v : vacations) {
            View item = inflater.inflate(R.layout.vacation_item, container, false);

            Button titleBtn = item.findViewById(R.id.item_title);
            titleBtn.setText(v.getTitle());

            titleBtn.setOnClickListener(view -> {
                long id = (v.getId() != null) ? v.getId() : -1L;
                Intent intent = new Intent(VacationActivity.this, VacationDetailActivity.class);
                intent.putExtra("vacation_id", id);
                intent.putExtra("vacation_title", v.getTitle());
                // Start detail for result so edits/deletes can trigger refresh
                startActivityForResult(intent, REQ_DETAIL);
            });

            container.addView(item);
        }
    }
}
