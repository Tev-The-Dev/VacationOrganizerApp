// java
package com.zybooks.d308vacationplanner;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;

public class DeleteExcursionActivity extends AppCompatActivity {

    public static final String EXTRA_EXCURSION_ID = "excursion_id";
    public static final String EXTRA_EXCURSION_TITLE = "excursion_title";

    private long mExcursionId = -1L;
    private String mExcursionTitle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        if (in != null) {
            mExcursionId = in.getLongExtra(EXTRA_EXCURSION_ID, -1L);
            if (mExcursionId == -1L) {
                // try common alternative key
                mExcursionId = in.getLongExtra("excursionId", -1L);
            }
            mExcursionTitle = in.getStringExtra(EXTRA_EXCURSION_TITLE);
            if (mExcursionTitle == null) mExcursionTitle = in.getStringExtra("excursion_title");
        }

        String message = "Delete this excursion?";
        if (mExcursionTitle != null && !mExcursionTitle.isEmpty()) {
            message = "Delete \"" + mExcursionTitle + "\"?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete excursion")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = performDelete();
                    if (deleted) {
                        Toast.makeText(DeleteExcursionActivity.this, "Excursion deleted", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                    } else {
                        Toast.makeText(DeleteExcursionActivity.this, "Unable to delete excursion", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED);
                    }
                    finish();
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

    private boolean performDelete() {
        if (mExcursionId == -1L) return false;

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return false;

        try {
            // Try common delete signatures via reflection to be robust across implementations.

            // 1) deleteExcursion(long id)
            try {
                Method m = repo.getClass().getMethod("deleteExcursion", long.class);
                m.invoke(repo, mExcursionId);
                return true;
            } catch (NoSuchMethodException ignored) { }

            // 2) deleteExcursion(java.lang.Long id)
            try {
                Method m = repo.getClass().getMethod("deleteExcursion", Long.class);
                m.invoke(repo, Long.valueOf(mExcursionId));
                return true;
            } catch (NoSuchMethodException ignored) { }

            // 3) deleteExcursion(Excursions excursion)
            try {
                Method m = repo.getClass().getMethod("deleteExcursion", Excursions.class);
                Excursions ex = getExcursionFromRepo(repo, mExcursionId);
                if (ex != null) {
                    m.invoke(repo, ex);
                    return true;
                }
            } catch (NoSuchMethodException ignored) { }

            // 4) deleteById / removeExcursion / delete (other common names)
            String[] altNames = new String[] {"deleteById", "removeExcursion", "delete", "remove"};
            for (String name : altNames) {
                try {
                    Method m = repo.getClass().getMethod(name, long.class);
                    m.invoke(repo, mExcursionId);
                    return true;
                } catch (NoSuchMethodException ignored) { }
                try {
                    Method m = repo.getClass().getMethod(name, Long.class);
                    m.invoke(repo, Long.valueOf(mExcursionId));
                    return true;
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Exception ignored) { }

        return false;
    }

    private Excursions getExcursionFromRepo(VacationRepository repo, long id) {
        try {
            // attempt repo.getExcursion(long)
            try {
                Method m = repo.getClass().getMethod("getExcursion", long.class);
                Object res = m.invoke(repo, id);
                if (res instanceof Excursions) return (Excursions) res;
            } catch (NoSuchMethodException ignored) { }

            try {
                Method m = repo.getClass().getMethod("getExcursion", Long.class);
                Object res = m.invoke(repo, Long.valueOf(id));
                if (res instanceof Excursions) return (Excursions) res;
            } catch (NoSuchMethodException ignored) { }

            // fallback to getExcursions() and search
            try {
                Method m = repo.getClass().getMethod("getExcursions");
                Object res = m.invoke(repo);
                if (res instanceof java.util.List) {
                    for (Object o : (java.util.List) res) {
                        if (o instanceof Excursions) {
                            Excursions e = (Excursions) o;
                            try {
                                Method getId = e.getClass().getMethod("getId");
                                Object idObj = getId.invoke(e);
                                if (idObj instanceof Number && ((Number) idObj).longValue() == id) return e;
                                if (idObj instanceof String) {
                                    try {
                                        if (Long.parseLong((String) idObj) == id) return e;
                                    } catch (Exception ignored) { }
                                }
                            } catch (Exception ignored) { }
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) { }

        } catch (Exception ignored) { }
        return null;
    }
}
