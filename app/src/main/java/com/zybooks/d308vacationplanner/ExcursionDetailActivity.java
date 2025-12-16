package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

public class ExcursionDetailActivity extends AppCompatActivity {

    private static final String TAG = "ExcursionDetailActivity";

    private long mVacationId = -1L;
    private Vacations mVacation = null;
    private String mVacationTitle = null;
    private String mVacationStart = null;
    private String mVacationEnd = null;

    private long mExcursionId = -1L;
    private String mExcursionTitle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.excursion_detail);
        // show Up button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            mVacationId = savedInstanceState.getLong("vacation_id", -1L);
            mVacationTitle = savedInstanceState.getString("vacation_title");
            mVacationStart = savedInstanceState.getString("vacation_start_date");
            mVacationEnd = savedInstanceState.getString("vacation_end_date");
            mExcursionId = savedInstanceState.getLong("excursion_id", -1L);
            mExcursionTitle = savedInstanceState.getString("excursion_title");
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                mVacationId = intent.getLongExtra("vacation_id", -1L);
                mVacationTitle = intent.getStringExtra("vacation_title");
                mVacationStart = intent.getStringExtra("vacation_start_date");
                mVacationEnd = intent.getStringExtra("vacation_end_date");

                mExcursionId = intent.getLongExtra("excursion_id", -1L);
                mExcursionTitle = intent.getStringExtra("excursion_title");

                // derive vacation id and excursion title from the excursion record in DB if not provided
                if ((mVacationId == -1L || mExcursionTitle == null || mExcursionTitle.isEmpty()) && mExcursionId != -1L) {
                    Excursions ex = findExcursionById(mExcursionId);
                    if (ex != null) {
                        if (ex.getVacationId() != null && mVacationId == -1L) {
                            mVacationId = ex.getVacationId();
                        }
                        if (mExcursionTitle == null || mExcursionTitle.isEmpty()) {
                            mExcursionTitle = ex.getTitle();
                        }
                    }
                }
            }
        }

        reloadVacationInfo();
    }

    @Override
    public void onBackPressed() {
        // Preserve filter on Back
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle ActionBar Up/Home to preserve the vacation filter
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = new Intent(this, ExcursionListActivity.class);
            if (mVacationId != -1L) {
                upIntent.putExtra("vacation_id", mVacationId);
                if (mVacationTitle != null) upIntent.putExtra("vacation_title", mVacationTitle);
            }
            setResult(RESULT_OK);
            NavUtils.navigateUpTo(this, upIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // also handle support navigate up
        Intent upIntent = new Intent(this, ExcursionListActivity.class);
        if (mVacationId != -1L) {
            upIntent.putExtra("vacation_id", mVacationId);
            if (mVacationTitle != null) upIntent.putExtra("vacation_title", mVacationTitle);
        }
        setResult(RESULT_OK);
        NavUtils.navigateUpTo(this, upIntent);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("vacation_id", mVacationId);
        outState.putString("vacation_title", mVacationTitle);
        outState.putString("vacation_start_date", mVacationStart);
        outState.putString("vacation_end_date", mVacationEnd);
        outState.putLong("excursion_id", mExcursionId);
        outState.putString("excursion_title", mExcursionTitle);
    }

    private void reloadVacationInfo() {
        mVacation = null;
        if (mVacationId != -1L) {
            VacationRepository repo = VacationRepository.getInstance(this);
            if (repo != null) {
                mVacation = repo.getVacation(mVacationId);
            }
        }

        TextView titleView = findViewById(R.id.detail_title);
        TextView startView = findViewById(R.id.detail_start_date);
        TextView endView = findViewById(R.id.detail_end_date); // layout may not have this; safe to keep null-checks

        if (mVacation != null) {
            String title = mVacation.getTitle() != null ? mVacation.getTitle() : "";
            String start = mVacation.getStartDate() != null ? mVacation.getStartDate() : "";
            String end = mVacation.getEndDate() != null ? mVacation.getEndDate() : "";

            if (!title.isEmpty()) setTitle(title);
            if (titleView != null) titleView.setText(title);
            if (startView != null) startView.setText(start);
            if (endView != null) endView.setText(end);

            mVacationTitle = title;
            mVacationStart = start;
            mVacationEnd = end;
        } else {
            if (mVacationTitle != null) setTitle(mVacationTitle);
            if (titleView != null) titleView.setText(mVacationTitle != null ? mVacationTitle : "");
            if (startView != null) startView.setText(mVacationStart != null ? mVacationStart : "");
            if (endView != null) endView.setText(mVacationEnd != null ? mVacationEnd : "");
            if (mVacationId == -1L) Log.w(TAG, "No vacation id available to load parent vacation");
        }

        // Ensure excursion title is shown in the layout's excursion TextView(s)
        TextView excursionBodyView = findViewById(R.id.detail_accommodation); // matches layout
        TextView pageTitleView = findViewById(R.id.excursion_detail_title);    // optional top title

        // If we still don't have the excursion title, try loading excursion from DB
        if ((mExcursionTitle == null || mExcursionTitle.isEmpty()) && mExcursionId != -1L) {
            Excursions ex = findExcursionById(mExcursionId);
            if (ex != null) mExcursionTitle = ex.getTitle();
        }

        if (excursionBodyView != null) {
            excursionBodyView.setText(mExcursionTitle != null ? mExcursionTitle : "");
        }
        if (pageTitleView != null && mExcursionTitle != null && !mExcursionTitle.isEmpty()) {
            pageTitleView.setText(mExcursionTitle);
        }
    }

    private Excursions findExcursionById(long id) {
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return null;
        return repo.getExcursion(id);
    }
}
