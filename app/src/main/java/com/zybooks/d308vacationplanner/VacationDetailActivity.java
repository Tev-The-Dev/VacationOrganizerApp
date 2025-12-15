package com.zybooks.d308vacationplanner;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class VacationDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_detail);

        TextView titleView = findViewById(R.id.detail_title);
        TextView accomView = findViewById(R.id.detail_accommodation);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView endView = findViewById(R.id.detail_end_date);

        long id = getIntent().getLongExtra("vacation_id", -1L);
        String titleExtra = getIntent().getStringExtra("vacation_title");

        Vacations found = null;
        if (id != -1L) {
            VacationRepository repo = VacationRepository.getInstance(this);
            List<Vacations> list = repo.getVacations();
            if (list != null) {
                for (Vacations v : list) {
                    if (v.getId() != null && v.getId() == id) {
                        found = v;
                        break;
                    }
                }
            }
        }

        if (found != null) {
            titleView.setText(safeString(found, "getTitle"));
            accomView.setText(safeString(found, "getAccommodation", "getAccomodation"));
            startView.setText(safeString(found, "getStartDate", "getStart"));
            endView.setText(safeString(found, "getEndDate", "getEnd"));
        } else if (titleExtra != null) {
            titleView.setText(titleExtra);
            accomView.setText("");
            startView.setText("");
            endView.setText("");
        } else {
            titleView.setText("Vacation");
            accomView.setText("");
            startView.setText("");
            endView.setText("");
        }
    }

    private String safeString(Object obj, String... methodNames) {
        if (obj == null) return "";
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object val = m.invoke(obj);
                if (val != null) return String.valueOf(val);
            } catch (Exception ignored) { }
        }
        return "";
    }
}
