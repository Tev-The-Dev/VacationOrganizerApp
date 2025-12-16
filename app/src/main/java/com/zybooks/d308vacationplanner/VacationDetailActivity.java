package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class VacationDetailActivity extends AppCompatActivity {
    private static final int REQUEST_ADD_EXCURSION = 1001;
    private static final int REQUEST_EDIT_VACATION = 2001;
    private static final int REQUEST_DELETE_VACATION = 3001;

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
        if (viewExcursionsBtn != null) {
            viewExcursionsBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, ExcursionListActivity.class);
                String titleText = (titleView != null && titleView.getText() != null) ? titleView.getText().toString() : "";
                intent.putExtra("vacation_title", titleText);

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

        Button editVacationBtn = findViewById(R.id.edit_vacation_button);
        if (editVacationBtn != null) {
            editVacationBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, EditVacationActivity.class);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ID, mId);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_TITLE, mTitle != null ? mTitle : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ACCOM, mAccommodation != null ? mAccommodation : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_START, mStartDate != null ? mStartDate : "");
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_END, mEndDate != null ? mEndDate : "");
                startActivityForResult(intent, REQUEST_EDIT_VACATION);
            });
        }

        // Launch DeleteVacationActivity for delete flow (moved logic to separate activity)
        Button deleteVacationBtn = findViewById(R.id.delete_vacation_button);
        if (deleteVacationBtn != null) {
            deleteVacationBtn.setOnClickListener(view -> {
                Intent intent = new Intent(VacationDetailActivity.this, DeleteVacationActivity.class);
                intent.putExtra(EditVacationActivity.EXTRA_VACATION_ID, mId);
                startActivityForResult(intent, REQUEST_DELETE_VACATION);
            });
        }
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
            reloadVacationDetails();
        } else if (requestCode == REQUEST_EDIT_VACATION) {
            if (resultCode == RESULT_OK && data != null) {
                mId = data.getLongExtra(EditVacationActivity.EXTRA_VACATION_ID, mId);
                mTitle = data.getStringExtra(EditVacationActivity.EXTRA_VACATION_TITLE);
                mAccommodation = data.getStringExtra(EditVacationActivity.EXTRA_VACATION_ACCOM);
                mStartDate = data.getStringExtra(EditVacationActivity.EXTRA_VACATION_START);
                mEndDate = data.getStringExtra(EditVacationActivity.EXTRA_VACATION_END);
                // propagate EDIT success to parent so VacationActivity can refresh
                setResult(RESULT_OK, data);
                reloadVacationDetails();
            } else if (resultCode == RESULT_FIRST_USER && data != null && data.getBooleanExtra(EditVacationActivity.EXTRA_VACATION_DELETE, false)) {
                // edited screen signaled delete -> finish and notify parent
                Intent result = new Intent();
                result.putExtra(EditVacationActivity.EXTRA_VACATION_DELETE, true);
                setResult(RESULT_OK, result);
                finish();
            } else {
                reloadVacationDetails();
            }
        } else if (requestCode == REQUEST_DELETE_VACATION) {
            // Accept RESULT_OK (DeleteVacationActivity uses RESULT_OK on success).
            if ((resultCode == RESULT_OK || resultCode == RESULT_FIRST_USER) && data != null && data.getBooleanExtra(EditVacationActivity.EXTRA_VACATION_DELETE, false)) {
                // Deleted successfully - propagate RESULT_OK to parent VacationActivity and finish this detail screen
                Intent result = new Intent();
                result.putExtra(EditVacationActivity.EXTRA_VACATION_DELETE, true);
                setResult(RESULT_OK, result);
                finish();
            } else {
                // not deleted or cancelled -> refresh UI
                reloadVacationDetails();
            }
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
            if (titleView != null) titleView.setText(safeString(mFound, "getTitle"));
            if (accomView != null) accomView.setText(safeString(mFound, "getAccommodation", "getAccomodation"));
            if (startView != null) startView.setText(safeString(mFound, "getStartDate", "getStart"));
            if (endView != null) endView.setText(safeString(mFound, "getEndDate", "getEnd"));
            // update cached fields
            mTitle = mFound.getTitle();
            mAccommodation = safeString(mFound, "getAccommodation", "getAccomodation");
            mStartDate = safeString(mFound, "getStartDate", "getStart");
            mEndDate = safeString(mFound, "getEndDate", "getEnd");
        } else {
            // no repository object found; use any saved/intent fields
            TextView tv = titleView;
            if (tv != null) tv.setText(mTitle != null ? mTitle : "");
            if (accomView != null) accomView.setText(mAccommodation != null ? mAccommodation : "");
            if (startView != null) startView.setText(mStartDate != null ? mStartDate : "");
            if (endView != null) endView.setText(mEndDate != null ? mEndDate : "");
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
