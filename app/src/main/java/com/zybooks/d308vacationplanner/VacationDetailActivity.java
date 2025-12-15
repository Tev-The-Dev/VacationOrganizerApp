package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class VacationDetailActivity extends AppCompatActivity {
    private static final int REQUEST_ADD_EXCURSION = 1001;
    private static final String KEY_VACATION_ID = "key_vacation_id";
    private static final String KEY_VACATION_TITLE = "key_vacation_title";
    private static final String KEY_VACATION_ACCOM = "key_vacation_accom";
    private static final String KEY_VACATION_START = "key_vacation_start";
    private static final String KEY_VACATION_END = "key_vacation_end";

    private long mId = -1L;
    private String mTitle = null;
    private String mAccommodation = null;
    private String mStartDate = null;
    private String mEndDate = null;
    private Vacations mFound = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vacation_detail);

        // restore saved state first, otherwise read from intent
        if (savedInstanceState != null) {
            mId = savedInstanceState.getLong(KEY_VACATION_ID, -1L);
            mTitle = savedInstanceState.getString(KEY_VACATION_TITLE);
            mAccommodation = savedInstanceState.getString(KEY_VACATION_ACCOM);
            mStartDate = savedInstanceState.getString(KEY_VACATION_START);
            mEndDate = savedInstanceState.getString(KEY_VACATION_END);
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                mId = intent.getLongExtra("vacation_id", -1L);
                mTitle = intent.getStringExtra("vacation_title");
                // read any passed visible fields so we can repopulate if parent is recreated
                mAccommodation = intent.getStringExtra("vacation_accommodation");
                mStartDate = intent.getStringExtra("vacation_start_date");
                mEndDate = intent.getStringExtra("vacation_end_date");
            }
        }

        // populate UI and wire buttons
        reloadVacationDetails();

        TextView titleView = findViewById(R.id.detail_title);

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

        View addVacationButton = findViewById(R.id.btn_add_excursion);
        addVacationButton.setOnClickListener(v -> {
            Intent intent = new Intent(VacationDetailActivity.this, AddExcursionActivity.class);
            // pass current visible fields so parent can be recreated and still show the same content
            intent.putExtra("vacation_title", titleView.getText().toString());

            TextView accomView = findViewById(R.id.detail_accommodation);
            TextView startView = findViewById(R.id.detail_start_date);
            TextView endView = findViewById(R.id.detail_end_date);
            intent.putExtra("vacation_accommodation", accomView != null ? accomView.getText().toString() : "");
            intent.putExtra("vacation_start_date", startView != null ? startView.getText().toString() : "");
            intent.putExtra("vacation_end_date", endView != null ? endView.getText().toString() : "");

            long vacationIdToSend = -1L;
            if (mFound != null && mFound.getId() != null) {
                vacationIdToSend = mFound.getId();
            } else if (mId != -1L) {
                vacationIdToSend = mId;
            }
            intent.putExtra("vacation_id", vacationIdToSend);

            startActivityForResult(intent, REQUEST_ADD_EXCURSION);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle Up navigation by finishing this activity to preserve the parent instance/state
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_EXCURSION && resultCode == RESULT_OK) {
            // An excursion was added; reload the vacation details and UI
            reloadVacationDetails();
        } else if (requestCode == REQUEST_ADD_EXCURSION) {
            // If child returned CANCEL, still reload from repository (or keep existing values).
            // reloadVacationDetails() will prefer repo data and fall back to saved/title fields.
            reloadVacationDetails();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_VACATION_ID, mId);
        outState.putString(KEY_VACATION_TITLE, mTitle);
        outState.putString(KEY_VACATION_ACCOM, mAccommodation);
        outState.putString(KEY_VACATION_START, mStartDate);
        outState.putString(KEY_VACATION_END, mEndDate);
    }

    // Reload mFound from the repository using mId and update UI fields
    private void reloadVacationDetails() {
        mFound = null;
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

        TextView titleView = findViewById(R.id.detail_title);
        TextView accomView = findViewById(R.id.detail_accommodation);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView endView = findViewById(R.id.detail_end_date);

        if (mFound != null) {
            titleView.setText(safeString(mFound, "getTitle"));
            accomView.setText(safeString(mFound, "getAccommodation", "getAccomodation"));
            startView.setText(safeString(mFound, "getStartDate", "getStart"));
            endView.setText(safeString(mFound, "getEndDate", "getEnd"));
            // update cached fields
            mTitle = mFound.getTitle();
            mAccommodation = safeString(mFound, "getAccommodation", "getAccomodation");
            mStartDate = safeString(mFound, "getStartDate", "getStart");
            mEndDate = safeString(mFound, "getEndDate", "getEnd");
        } else {
            // no repository object found; use any saved/intent fields
            if (mTitle != null) {
                titleView.setText(mTitle);
            } else {
                titleView.setText("");
            }
            accomView.setText(mAccommodation != null ? mAccommodation : "");
            startView.setText(mStartDate != null ? mStartDate : "");
            endView.setText(mEndDate != null ? mEndDate : "");
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
