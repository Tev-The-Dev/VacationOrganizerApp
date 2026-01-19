package com.zybooks.d308vacationplanner;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
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
    private static final String TAG = "ExcursionListActivity";

    private BroadcastReceiver mDeleteReceiver;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.excursion_list);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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

        // create receiver instance but register in onResume
        mDeleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long deletedId = intent != null ? intent.getLongExtra(DeleteExcursionActivity.EXTRA_EXCURSION_ID, -1L) : -1L;
                Log.i(TAG, "onReceive: excursion deleted broadcast id=" + deletedId);
                loadExcursions();
                ExcursionListActivity.this.setResult(RESULT_OK);
            }
        };

        // initial load
        loadExcursions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            IntentFilter filter = new IntentFilter(DeleteExcursionActivity.ACTION_EXCURSION_DELETED);
            registerReceiver(mDeleteReceiver, filter);
            Log.i(TAG, "receiver registered");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register receiver", e);
        }
        // reload in case parent signalled change
        loadExcursions();
    }

    @Override
    protected void onPause() {
        try {
            if (mDeleteReceiver != null) unregisterReceiver(mDeleteReceiver);
            Log.i(TAG, "receiver unregistered");
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister receiver", e);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // defensive: ensure receiver gone
        try {
            if (mDeleteReceiver != null) unregisterReceiver(mDeleteReceiver);
        } catch (Exception ignored) { }
        super.onDestroy();
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
            setResult(RESULT_OK);
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
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
        Log.i(TAG, "loadExcursions start for vacationId=" + mVacationId);
        LinearLayout container = findViewById(R.id.vacation_container);
        if (container == null) {
            Log.w(TAG, "loadExcursions: container view is null. Check excursion_list.xml id `vacation_container`");
            return;
        }

        container.removeAllViews();

        VacationRepository repo;
        try {
            repo = VacationRepository.getInstance(this);
        } catch (Exception e) {
            Log.w(TAG, "loadExcursions: repo.getInstance failed", e);
            TextView error = new TextView(this);
            error.setText("Unable to load excursions");
            container.addView(error);
            return;
        }

        List<Excursions> excursions = null;
        try {
            excursions = repo.getExcursions();
        } catch (Exception e) {
            Log.w(TAG, "loadExcursions: repo.getExcursions failed", e);
        }

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
            if (titleBtn == null) {
                Log.w(TAG, "loadExcursions: item_title not found in excursion_item layout");
                continue;
            }

            String title = (e.getTitle() != null) ? e.getTitle() : "";
            titleBtn.setText(title);

            // Ensure the button can receive clicks and capture id/title into final locals
            titleBtn.setClickable(true);
            titleBtn.setFocusable(false);
            final long clickId = (e.getId() != null) ? e.getId() : -1L;
            final String clickTitle = title;

            titleBtn.setOnClickListener(view -> {
                Log.i(TAG, "item click: excursion id=" + clickId + " title=\"" + clickTitle + "\"");
                Intent intent = new Intent(ExcursionListActivity.this, ExcursionDetailActivity.class);
                intent.putExtra("excursion_id", clickId);
                intent.putExtra("excursion_title", clickTitle);
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
        Log.i(TAG, "loadExcursions finished, itemsAdded=" + added);
    }
}
