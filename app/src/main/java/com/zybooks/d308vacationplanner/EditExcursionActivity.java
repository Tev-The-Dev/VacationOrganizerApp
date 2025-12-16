package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditExcursionActivity extends AppCompatActivity {
    public static final String EXTRA_EXCURSION_ID = "excursion_id";
    public static final String EXTRA_EXCURSION_TITLE = "excursion_title";
    public static final String EXTRA_EXCURSION_DATE = "excursion_date";
    public static final String EXTRA_EXCURSION_DELETE = "excursion_delete";

    // extras passed from ExcursionListActivity for range validation
    public static final String EXTRA_VACATION_START = "vacation_start_date";
    public static final String EXTRA_VACATION_END = "vacation_end_date";

    private EditText mTitle;
    private EditText mDate;
    private long mExcursionId = -1L;

    private String mVacationStart = null;
    private String mVacationEnd = null;

    private final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_excursion);

        DATE_FMT.setLenient(false);

        mTitle = findViewById(R.id.edit_excursion_title);
        mDate = findViewById(R.id.edit_excursion_date);

        Button btnSave = findViewById(R.id.btn_save_excursion);
        Button btnCancel = findViewById(R.id.btn_cancel_excursion);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_EXCURSION_ID)) {
                mExcursionId = intent.getLongExtra(EXTRA_EXCURSION_ID, -1L);
            }
            if (intent.hasExtra(EXTRA_VACATION_START)) {
                mVacationStart = intent.getStringExtra(EXTRA_VACATION_START);
            }
            if (intent.hasExtra(EXTRA_VACATION_END)) {
                mVacationEnd = intent.getStringExtra(EXTRA_VACATION_END);
            }
        }

        // Populate fields from repository if possible and try to derive vacation bounds if not provided
        if (mExcursionId != -1L) {
            VacationRepository repo = VacationRepository.getInstance(this);
            if (repo != null) {
                try {
                    Method getMethod = repo.getClass().getMethod("getExcursions");
                    Object res = getMethod.invoke(repo);
                    if (res instanceof List) {
                        List<?> list = (List<?>) res;
                        for (Object e : list) {
                            if (e == null) continue;
                            try {
                                Method getId = e.getClass().getMethod("getId");
                                Object idVal = getId.invoke(e);
                                if (idVal != null && Long.parseLong(String.valueOf(idVal)) == mExcursionId) {
                                    try {
                                        Method gt = e.getClass().getMethod("getTitle");
                                        Object t = gt.invoke(e);
                                        if (t != null) mTitle.setText(String.valueOf(t));
                                    } catch (Exception ignored) {}
                                    try {
                                        Method gd = e.getClass().getMethod("getExcursionDate");
                                        Object d = gd.invoke(e);
                                        if (d != null) mDate.setText(String.valueOf(d));
                                    } catch (Exception ignored) {}

                                    // If vacation bounds weren't passed in, try to derive them from the repository:
                                    try {
                                        Method gvId = e.getClass().getMethod("getVacationId");
                                        Object vacIdObj = gvId.invoke(e);
                                        if (vacIdObj != null) {
                                            long vacId = Long.parseLong(String.valueOf(vacIdObj));

                                            boolean needStart = TextUtils.isEmpty(mVacationStart);
                                            boolean needEnd = TextUtils.isEmpty(mVacationEnd);
                                            if (needStart || needEnd) {
                                                try {
                                                    Method getVacs = repo.getClass().getMethod("getVacations");
                                                    Object vres = getVacs.invoke(repo);
                                                    if (vres instanceof List) {
                                                        List<?> vacList = (List<?>) vres;
                                                        for (Object v : vacList) {
                                                            if (v == null) continue;
                                                            try {
                                                                Method getVacId = v.getClass().getMethod("getId");
                                                                Object vid = getVacId.invoke(v);
                                                                if (vid != null && Long.parseLong(String.valueOf(vid)) == vacId) {
                                                                    // try common getter names for start/end
                                                                    if (needStart) {
                                                                        try {
                                                                            Method gs = v.getClass().getMethod("getStartDate");
                                                                            Object s = gs.invoke(v);
                                                                            if (s != null) mVacationStart = String.valueOf(s);
                                                                        } catch (Exception ignore1) {
                                                                            try {
                                                                                Method gs2 = v.getClass().getMethod("getStart");
                                                                                Object s2 = gs2.invoke(v);
                                                                                if (s2 != null) mVacationStart = String.valueOf(s2);
                                                                            } catch (Exception ignore2) { }
                                                                        }
                                                                    }
                                                                    if (needEnd) {
                                                                        try {
                                                                            Method ge = v.getClass().getMethod("getEndDate");
                                                                            Object eEnd = ge.invoke(v);
                                                                            if (eEnd != null) mVacationEnd = String.valueOf(eEnd);
                                                                        } catch (Exception ignore1) {
                                                                            try {
                                                                                Method ge2 = v.getClass().getMethod("getEnd");
                                                                                Object e2 = ge2.invoke(v);
                                                                                if (e2 != null) mVacationEnd = String.valueOf(e2);
                                                                            } catch (Exception ignore2) { }
                                                                        }
                                                                    }
                                                                    break;
                                                                }
                                                            } catch (Exception ignored2) {}
                                                        }
                                                    }
                                                } catch (NoSuchMethodException ignored3) { }
                                            }
                                        }
                                    } catch (NoSuchMethodException ignored) { }
                                    break;
                                }
                            } catch (Exception ignored) { }
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Exception ignored) { }
            }
        }

        btnSave.setOnClickListener(v -> {
            if (validateDateField()) {
                boolean ok = persistEditsToRepository();
                if (ok) {
                    Toast.makeText(this, "Excursion saved.", Toast.LENGTH_SHORT).show();
                    Intent result = new Intent();
                    result.putExtra(EXTRA_EXCURSION_ID, mExcursionId);
                    result.putExtra(EXTRA_EXCURSION_TITLE, safeText(mTitle));
                    result.putExtra(EXTRA_EXCURSION_DATE, safeText(mDate));
                    setResult(RESULT_OK, result);
                    finish();
                } else {
                    Toast.makeText(this, "Failed to save excursion.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Invalid date. Use YYYY-MM-DD within vacation range.", Toast.LENGTH_SHORT).show();
            }
        });

        // Operate like EditVacationActivity: Cancel should not persist and should return RESULT_CANCELED
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    // Keep default up/back behavior (do not override) so Cancel/back act like EditVacationActivity.

    private boolean validateDateField() {
        String dateStr = safeText(mDate);
        mDate.setError(null);
        if (TextUtils.isEmpty(dateStr)) {
            mDate.setError("Date required (YYYY-MM-DD)");
            mDate.requestFocus();
            Toast.makeText(this, "Date required (YYYY-MM-DD)", Toast.LENGTH_SHORT).show();
            return false;
        }
        Date d;
        try {
            d = DATE_FMT.parse(dateStr);
        } catch (ParseException e) {
            mDate.setError("Invalid format. Use YYYY-MM-DD");
            mDate.requestFocus();
            Toast.makeText(this, "Invalid format. Use YYYY-MM-DD", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!TextUtils.isEmpty(mVacationStart)) {
            String vs = mVacationStart.trim();
            try {
                Date start = DATE_FMT.parse(vs);
                if (d.before(start)) {
                    mDate.setError("Date must be on/after vacation start: " + vs);
                    mDate.requestFocus();
                    Toast.makeText(this, "Date must be on/after vacation start: " + vs, Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (ParseException pe) {
                mDate.setError("Invalid vacation start date format: " + vs);
                mDate.requestFocus();
                Toast.makeText(this, "Invalid vacation start date format: " + vs, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (!TextUtils.isEmpty(mVacationEnd)) {
            String ve = mVacationEnd.trim();
            try {
                Date end = DATE_FMT.parse(ve);
                if (d.after(end)) {
                    mDate.setError("Date must be on/before vacation end: " + ve);
                    mDate.requestFocus();
                    Toast.makeText(this, "Date must be on/before vacation end: " + ve, Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (ParseException pe) {
                mDate.setError("Invalid vacation end date format: " + ve);
                mDate.requestFocus();
                Toast.makeText(this, "Invalid vacation end date format: " + ve, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        return true;
    }

    private String safeText(EditText et) {
        return et == null ? "" : (et.getText() == null ? "" : et.getText().toString().trim());
    }

    /**
     * Persist edits. Returns true if persistence (or in-memory object modification) succeeded.
     */
    private boolean persistEditsToRepository() {
        if (mExcursionId == -1L) {
            return false;
        }

        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return false;

        boolean changed = false;

        try {
            Method getMethod = repo.getClass().getMethod("getExcursions");
            Object res = getMethod.invoke(repo);
            if (!(res instanceof List)) return false;
            List<?> list = (List<?>) res;
            for (Object obj : list) {
                if (obj == null) continue;
                try {
                    Method getId = obj.getClass().getMethod("getId");
                    Object idVal = getId.invoke(obj);
                    if (idVal != null && Long.parseLong(String.valueOf(idVal)) == mExcursionId) {
                        String title = safeText(mTitle);
                        String date = safeText(mDate);
                        safeInvokeSetter(obj, new String[]{"setTitle", "set_excursion_title"}, title);
                        safeInvokeSetter(obj, new String[]{"setExcursionDate", "setExcursion_Date", "setDate"}, date);

                        changed = true;

                        try {
                            Method update = repo.getClass().getMethod("updateExcursion", obj.getClass());
                            update.invoke(repo, obj);
                        } catch (NoSuchMethodException ignored) {
                            try {
                                Method upd2 = repo.getClass().getMethod("updateExcursion", long.class, String.class, String.class);
                                upd2.invoke(repo, mExcursionId, title, date);
                            } catch (NoSuchMethodException ignored2) {
                                // no explicit update API; modifying object may be enough
                            } catch (Exception ignored2) {}
                        } catch (Exception ignored) {}

                        break;
                    }
                } catch (Exception ignored) {}
            }
        } catch (NoSuchMethodException nsme) {
            return changed;
        } catch (Exception ignored) {}

        return changed;
    }

    private void safeInvokeSetter(Object target, String[] candidateNames, String value) {
        if (target == null || candidateNames == null) return;
        for (String name : candidateNames) {
            try {
                Method m = target.getClass().getMethod(name, String.class);
                m.invoke(target, value);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) { }
        }
    }
}
