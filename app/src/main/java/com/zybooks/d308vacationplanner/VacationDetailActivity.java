package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class VacationDetailActivity extends AppCompatActivity {

    private static final String KEY_VACATION_ID = "key_vacation_id";
    private static final String KEY_VACATION_TITLE = "key_vacation_title";

    private long mId = -1L;
    private String mTitle = null;
    private Vacations mFound = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_detail);

        TextView titleView = findViewById(R.id.detail_title);
        TextView accomView = findViewById(R.id.detail_accommodation);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView endView = findViewById(R.id.detail_end_date);

        // restore saved state first, otherwise read from intent
        if (savedInstanceState != null) {
            mId = savedInstanceState.getLong(KEY_VACATION_ID, -1L);
            mTitle = savedInstanceState.getString(KEY_VACATION_TITLE);
        } else {
            mId = getIntent().getLongExtra("vacation_id", -1L);
            mTitle = getIntent().getStringExtra("vacation_title");
        }

        // find the Vacations object when id is available
        if (mId != -1L) {
            VacationRepository repo = VacationRepository.getInstance(this);
            List<Vacations> list = repo.getVacations();
            if (list != null) {
                for (Vacations v : list) {
                    if (v != null && v.getId() != null && v.getId().longValue() == mId) {
                        mFound = v;
                        break;
                    }
                }
            }
        }

        // populate UI from the found object or from mTitle (persisted)
        if (mFound != null) {
            titleView.setText(safeString(mFound, "getTitle"));
            accomView.setText(safeString(mFound, "getAccommodation", "getAccomodation"));
            startView.setText(safeString(mFound, "getStartDate", "getStart"));
            endView.setText(safeString(mFound, "getEndDate", "getEnd"));
            // ensure mTitle matches the found object's title
            mTitle = mFound.getTitle();
        } else if (mTitle != null) {
            titleView.setText(mTitle);
            accomView.setText("");
            startView.setText("");
            endView.setText("");
        } else {
            titleView.setText("");
            accomView.setText("");
            startView.setText("");
            endView.setText("");
        }

        Button viewExcursionsBtn = findViewById(R.id.btn_view_excursions);
        viewExcursionsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(VacationDetailActivity.this, ExcursionListActivity.class);
            intent.putExtra("vacation_title", titleView.getText().toString());

            long vacationIdToSend = -1L;
            if (mFound != null && mFound.getId() != null) {
                vacationIdToSend = mFound.getId();
            } else if (mId != -1L) {
                vacationIdToSend = mId;
            }
            intent.putExtra("vacation_id", vacationIdToSend);

            startActivity(intent);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_VACATION_ID, mId);
        outState.putString(KEY_VACATION_TITLE, mTitle);
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
