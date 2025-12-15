package com.zybooks.d308vacationplanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.R;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.List;

public class VacationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_list);

        LinearLayout container = findViewById(R.id.vacation_container);
        VacationRepository repo = VacationRepository.getInstance(this);
        List<Vacations> vacations = repo.getVacations();

        if (vacations == null || vacations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No vacations");
            empty.setTextSize(18f);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Vacations v : vacations) {
            View item = inflater.inflate(R.layout.vacation_item, container, false);

            TextView title = item.findViewById(R.id.item_title);
            title.setText(v.getTitle());
            container.addView(item);
        }
    }
}
