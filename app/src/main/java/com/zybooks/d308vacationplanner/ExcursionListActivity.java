package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ExcursionListActivity extends AppCompatActivity {
    private static final String TAG = "ExcursionListActivity";
    private static final int REQUEST_ADD_EXCURSION = 1001;
    private static final int REQUEST_EDIT_EXCURSION = 1002;

    private long mVacationId = -1L;
    private long mSelectedExcursionId = -1L;
    private View mSelectedRowView = null;

    private Button mEditTop;
    private Button mDeleteTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_list);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("vacation_id")) {
            mVacationId = intent.getLongExtra("vacation_id", -1L);
        }

        // Wire Add button (unchanged)
        TextView titleView = findViewById(R.id.excursion_title);
        View addVacationButton = findViewById(R.id.add_excursion);
        if (addVacationButton != null) {
            addVacationButton.setOnClickListener(v -> {
                Intent addIntent = new Intent(ExcursionListActivity.this, AddExcursionActivity.class);

                String titleText = titleView != null ? titleView.getText().toString() : "";
                addIntent.putExtra("vacation_title", titleText);

                TextView accomView = findViewById(R.id.detail_accommodation);
                TextView startView = findViewById(R.id.detail_start_date);
                TextView endView = findViewById(R.id.detail_end_date);
                addIntent.putExtra("vacation_accommodation", accomView != null ? accomView.getText().toString() : "");
                addIntent.putExtra("vacation_start_date", startView != null ? startView.getText().toString() : "");
                addIntent.putExtra("vacation_end_date", endView != null ? endView.getText().toString() : "");

                addIntent.putExtra("vacation_id", mVacationId);

                startActivityForResult(addIntent, REQUEST_ADD_EXCURSION);
            });
        } else {
            Log.w(TAG, "Add excursion button (R.id.add_excursion) not found.");
        }

        // Wire top edit/delete buttons to act on the selected row
        mEditTop = findViewById(R.id.edit_button);
        mDeleteTop = findViewById(R.id.delete_button);

        // ensure buttons start disabled (layout may already set them disabled)
        if (mEditTop != null) mEditTop.setEnabled(false);
        if (mDeleteTop != null) mDeleteTop.setEnabled(false);

        if (mEditTop != null) {
            mEditTop.setOnClickListener(v -> {
                if (mSelectedExcursionId == -1L) {
                    Log.i(TAG, "No excursion selected to edit.");
                    return;
                }
                Excursions e = findExcursionById(mSelectedExcursionId);
                Intent edit = new Intent(ExcursionListActivity.this, EditExcursionActivity.class);
                edit.putExtra(EditExcursionActivity.EXTRA_EXCURSION_ID, mSelectedExcursionId);
                if (e != null) {
                    edit.putExtra(EditExcursionActivity.EXTRA_EXCURSION_TITLE, e.getTitle() != null ? e.getTitle() : "");
                    edit.putExtra(EditExcursionActivity.EXTRA_EXCURSION_DATE, e.getExcursionDate() != null ? e.getExcursionDate() : "");
                }

                // Pass vacation start/end so EditExcursionActivity can validate range
                TextView startView = findViewById(R.id.detail_start_date);
                TextView endView = findViewById(R.id.detail_end_date);
                if (startView != null) edit.putExtra(EditExcursionActivity.EXTRA_VACATION_START, startView.getText().toString());
                if (endView != null) edit.putExtra(EditExcursionActivity.EXTRA_VACATION_END, endView.getText().toString());

                startActivityForResult(edit, REQUEST_EDIT_EXCURSION);
            });
        }

        if (mDeleteTop != null) {
            mDeleteTop.setOnClickListener(v -> {
                if (mSelectedExcursionId == -1L) {
                    Log.i(TAG, "No excursion selected to delete.");
                    return;
                }
                // Best-effort delete on repository
                VacationRepository repo = VacationRepository.getInstance(this);
                if (repo != null) {
                    try {
                        Method delLong = repo.getClass().getMethod("deleteExcursion", long.class);
                        delLong.invoke(repo, mSelectedExcursionId);
                    } catch (NoSuchMethodException ns1) {
                        try {
                            Method delObj = repo.getClass().getMethod("deleteExcursion", Excursions.class);
                            Excursions target = findExcursionById(mSelectedExcursionId);
                            if (target != null) delObj.invoke(repo, target);
                        } catch (Exception ignored) {
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to call repository delete method", e);
                    }
                }
                // reset selection and reload
                mSelectedExcursionId = -1L;
                mSelectedRowView = null;
                if (mEditTop != null) mEditTop.setEnabled(false);
                if (mDeleteTop != null) mDeleteTop.setEnabled(false);
                loadExcursions();
            });
        }

        // Initial load
        loadExcursions();
    }

    // Populate / refresh excursions list and allow selecting a row
    private void loadExcursions() {
        VacationRepository repo = VacationRepository.getInstance(this);
        List<Excursions> all = (repo != null) ? repo.getExcursions() : null;

        LinearLayout container = findViewById(R.id.vacation_container);
        if (container == null) {
            Log.w(TAG, "LinearLayout with id R.id.vacation_container not found; cannot display excursions.");
            return;
        }

        container.removeAllViews();
        // clear selection when reloading
        mSelectedExcursionId = -1L;
        mSelectedRowView = null;
        if (mEditTop != null) mEditTop.setEnabled(false);
        if (mDeleteTop != null) mDeleteTop.setEnabled(false);

        List<Excursions> filtered = new ArrayList<>();
        if (all != null) {
            for (Excursions e : all) {
                if (e == null) continue;
                Long vid = e.getVacationId();
                if (vid != null && vid.longValue() == mVacationId) {
                    filtered.add(e);
                }
            }
        }

        if (filtered.isEmpty()) {
            TextView empty = createRowTextView("No excursions");
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Excursions e : filtered) {
            String title = e.getTitle() != null ? e.getTitle() : "";
            String date = e.getExcursionDate() != null ? e.getExcursionDate() : "";
            String rowText = title + (date.isEmpty() ? "" : " — " + date);

            // Create a simple selectable row using the helper
            TextView row = createRowTextView(rowText);
            // store the excursion id as tag for retrieval
            Long id = e.getId();
            row.setTag(id);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                Long tagId = (Long) v.getTag();
                selectRow(v, tagId);
            });

            container.addView(row);
        }
    }

    private void selectRow(View row, Long excursionId) {
        if (row == null || excursionId == null) return;

        // un-highlight previous
        if (mSelectedRowView != null) {
            mSelectedRowView.setBackgroundColor(Color.TRANSPARENT);
        }

        // highlight new
        row.setBackgroundColor(Color.parseColor("#D0E8FF")); // light blue
        mSelectedRowView = row;
        mSelectedExcursionId = excursionId.longValue();

        // enable top buttons
        if (mEditTop != null) mEditTop.setEnabled(true);
        if (mDeleteTop != null) mDeleteTop.setEnabled(true);
    }

    private Excursions findExcursionById(long id) {
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return null;
        List<Excursions> all = repo.getExcursions();
        if (all == null) return null;
        for (Excursions e : all) {
            if (e == null) continue;
            Long eid = e.getId();
            if (eid != null && eid.longValue() == id) return e;
        }
        return null;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_ADD_EXCURSION || requestCode == REQUEST_EDIT_EXCURSION) && resultCode == RESULT_OK) {
            loadExcursions();
        }
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
