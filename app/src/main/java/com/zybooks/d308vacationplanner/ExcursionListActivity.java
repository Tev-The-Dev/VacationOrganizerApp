package com.zybooks.d308vacationplanner;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.List;

public class ExcursionListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_list);

        TextView titleView = findViewById(R.id.excursion_title);
        LinearLayout container = findViewById(R.id.vacation_container);
        long vacationId = getIntent().getLongExtra("vacation_id", -1L);
        String vacationTitle = getIntent().getStringExtra("vacation_title");

        if (titleView != null) {
            if (vacationTitle != null && !vacationTitle.isEmpty()) {
                titleView.setText(vacationTitle + " - Excursions");
            } else {
                titleView.setText(getString(R.string.excursion_list));
            }
        }

        if (container == null) return;

        VacationRepository repo = VacationRepository.getInstance(this);
        List<Excursions> all = repo.getExcursions();

        if (all == null || all.isEmpty()) {
            addMessage(container, "No excursions");
            return;
        }

        boolean any = false;
        for (Excursions e : all) {
            if (e == null) continue;
            Long vidObj = e.getVacationId();
            if (vidObj == null) continue;
            if (vidObj.longValue() != vacationId) continue;

            any = true;
            String title = e.getTitle() != null ? e.getTitle() : "";
            String date = e.getExcursionDate() != null ? e.getExcursionDate() : "";

            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setTextSize(16f);
            tv.setText(title + " — " + date);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad / 2, pad, pad / 2, pad);
            container.addView(tv);
        }

        if (!any) {
            addMessage(container, "No excursions for this vacation");
        }
    }

    private void addMessage(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(16f);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        container.addView(tv);
    }
}
