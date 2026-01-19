package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.util.List;

public class DeleteExcursionActivity extends AppCompatActivity {

    public static final String EXTRA_EXCURSION_ID = "excursion_id";
    public static final String EXTRA_EXCURSION_TITLE = "excursion_title";

    public static final String ACTION_EXCURSION_DELETED =
            "com.zybooks.d308vacationplanner.ACTION_EXCURSION_DELETED";

    private long mExcursionId = -1L;
    private String mExcursionTitle = null;
    private static final String TAG = "DeleteExcursionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        if (in != null) {
            mExcursionId = in.getLongExtra(EXTRA_EXCURSION_ID, -1L);
            if (mExcursionId == -1L) {
                mExcursionId = in.getLongExtra("excursionId", -1L);
            }
            mExcursionTitle = in.getStringExtra(EXTRA_EXCURSION_TITLE);
            if (mExcursionTitle == null) mExcursionTitle = in.getStringExtra("excursion_title");
        }

        Log.i(TAG, "onCreate: incoming id=" + mExcursionId + " title=\"" + mExcursionTitle + "\"");

        String message = "Delete this excursion?";
        if (mExcursionTitle != null && !mExcursionTitle.isEmpty()) {
            message = "Delete \"" + mExcursionTitle + "\"?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete excursion")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        boolean deleted = performDelete();
                        runOnUiThread(() -> {
                            if (deleted) {
                                // Broadcast deletion so any listeners (ExcursionListActivity) can reload
                                Intent bc = new Intent(ACTION_EXCURSION_DELETED);
                                bc.putExtra(EXTRA_EXCURSION_ID, mExcursionId);
                                sendBroadcast(bc);

                                // Signal success to any caller
                                setResult(RESULT_OK);

                                // Bring ExcursionListActivity to the front (clears intermediate activities)
                                Intent listIntent = new Intent(DeleteExcursionActivity.this, ExcursionListActivity.class);
                                listIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(listIntent);

                                finish();
                            } else {
                                Toast.makeText(DeleteExcursionActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_CANCELED);
                                finish();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
    }

    // Use the repository's deleteExcursion(Excursions) method and verify removal.
    private boolean performDelete() {
        Log.i(TAG, "performDelete: start id=" + mExcursionId);
        if (mExcursionId == -1L) {
            Log.w(TAG, "performDelete: invalid id");
            return false;
        }

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) {
            Log.w(TAG, "performDelete: repo is null");
            return false;
        }

        try {
            // get entity via repo API
            Excursions target = null;
            try {
                target = repo.getExcursion(mExcursionId);
            } catch (Exception e) {
                Log.w(TAG, "performDelete: getExcursion threw", e);
            }

            // fallback: search list
            if (target == null) {
                try {
                    List<Excursions> all = repo.getExcursions();
                    if (all != null) {
                        for (Excursions ex : all) {
                            if (ex != null && ex.getId() != null && ex.getId().longValue() == mExcursionId) {
                                target = ex;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "performDelete: fallback search threw", e);
                }
            }

            Log.i(TAG, "performDelete: target " + (target == null ? "NOT found" : "found id=" + target.getId()));

            if (target == null) {
                Log.w(TAG, "performDelete: excursion not found id=" + mExcursionId);
                return false;
            }

            // attempt delete using repository method
            try {
                repo.deleteExcursion(target);
            } catch (Exception e) {
                Log.w(TAG, "performDelete: repo.deleteExcursion threw", e);
            }

            // verification: try to fetch the entity again
            Excursions after = null;
            try {
                after = repo.getExcursion(mExcursionId);
            } catch (Exception e) {
                Log.w(TAG, "performDelete: getExcursion after delete threw", e);
            }

            if (after == null) {
                Log.i(TAG, "performDelete: verified deleted id=" + mExcursionId);
                return true;
            }

            // final fallback: check list for presence
            try {
                List<Excursions> all = repo.getExcursions();
                boolean stillPresent = false;
                if (all != null) {
                    for (Excursions ex : all) {
                        if (ex != null && ex.getId() != null && ex.getId().longValue() == mExcursionId) {
                            stillPresent = true;
                            break;
                        }
                    }
                }
                Log.i(TAG, "performDelete: stillPresent=" + stillPresent + " after delete attempt");
                return !stillPresent;
            } catch (Exception e) {
                Log.w(TAG, "performDelete: exception checking list after delete", e);
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "performDelete caught unexpected exception", e);
            return false;
        }
    }
}
