package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_ADD = 100;
    private static final int REQ_DELETE = 101;
    private static final int REQ_NOTIF = 4001;

    // Guard so we only check/ask for notifications once per process launch
    private static boolean sNotificationsChecked = false;

    private ReportGenerator reportGenerator;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View vacationsButton = findViewById(R.id.btn_view_vacations);
        vacationsButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, VacationActivity.class)));

        // Initialize report generator and wire nav menu button (dropdown) for report actions
        reportGenerator = new ReportGenerator(this);
        // Immediate debug output to terminal / logcat
        if (reportGenerator != null) {
            reportGenerator.printToTerminal("ReportGenerator ready");
            // example/demo invocation
            reportGenerator.printVacationDebug(1L, "Beach Trip (demo)", "2026-06-01", "2026-06-07");
        }

        ImageButton navMenuBtn = findViewById(R.id.btn_nav_menu);
        if (navMenuBtn != null) {
            navMenuBtn.setOnClickListener(this::showNavMenu);
        }

        // Run the notification check/request only once per process lifetime
        if (!sNotificationsChecked) {
            sNotificationsChecked = true; // ensure it's only attempted once
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    NotificationScheduler.checkDatabaseAndNotifyToday(this);
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQ_NOTIF);
                }
            } else {
                // Pre-Android 13: permissions not required at runtime
                NotificationScheduler.checkDatabaseAndNotifyToday(this);
            }
        }

        // Example: if you launch Add/Delete from here, use startActivityForResult(...) with REQ_ADD/REQ_DELETE
    }

    private void showNavMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Generate report (all vacations)");
        popup.getMenu().add(0, 2, 1, "Search vacation by id");
        popup.setOnMenuItemClickListener(this::onNavMenuItemClicked);
        popup.show();
    }

    private boolean onNavMenuItemClicked(MenuItem item) {
        if (item.getItemId() == 1) {
            if (reportGenerator != null) {
                Toast.makeText(this, "Generating report...", Toast.LENGTH_SHORT).show();
                reportGenerator.printToTerminal("User requested report generation");
                // refresh/load current vacations and print each to terminal
                loadVacations();
                // indicate completion to the user
                Toast.makeText(this, "Report Generated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Report generator unavailable", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == 2) {
            // prompt the user for an id and search
            promptAndSearchVacation();
            return true;
        }
        return false;
    }


    private void promptAndSearchVacation() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Search vacation by id")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    String val = input.getText() == null ? "" : input.getText().toString().trim();
                    if (val.isEmpty()) {
                        if (reportGenerator != null) reportGenerator.printToTerminal("No id entered");
                        Toast.makeText(MainActivity.this, "Please enter a vacation id to search", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Long id = Long.parseLong(val);
                        searchAndPrintVacation(id);
                    } catch (NumberFormatException e) {
                        if (reportGenerator != null) reportGenerator.printToTerminal("Invalid id: " + val);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Find a vacation by id (try repo direct methods then fallback to scanning all vacations).
    // Print the vacation and associated excursions when found.
    private void searchAndPrintVacation(Long vacationId) {
        if (vacationId == null || reportGenerator == null) return;
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) {
            reportGenerator.printToTerminal("Repository unavailable");
            return;
        }

        Object vacationObj = null;

        // Try common repository methods that accept an id
        String[] candidateMethods = new String[] {
                "getVacation", "getVacationById", "findVacationById", "getById", "findById"
        };

        for (String name : candidateMethods) {
            try {
                Method m = repo.getClass().getMethod(name, Long.class);
                Object res = m.invoke(repo, vacationId);
                if (res != null) { vacationObj = res; break; }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                // try primitive long
                try {
                    Method m2 = repo.getClass().getMethod(name, long.class);
                    Object res2 = m2.invoke(repo, vacationId.longValue());
                    if (res2 != null) { vacationObj = res2; break; }
                } catch (Exception ignored2) {}
            }
        }

        // fallback: iterate all vacations and match id
        if (vacationObj == null) {
            try {
                List<?> all = repo.getVacations();
                if (all != null) {
                    for (Object it : all) {
                        if (it == null) continue;
                        try {
                            Method getId = it.getClass().getMethod("getId");
                            Object o = getId.invoke(it);
                            Long vid = null;
                            if (o instanceof Long) vid = (Long) o;
                            else if (o instanceof Number) vid = ((Number) o).longValue();
                            if (vid != null && vid.equals(vacationId)) {
                                vacationObj = it;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        if (vacationObj == null) {
            reportGenerator.printToTerminal("Vacation id=" + vacationId + " not found");
            Toast.makeText(MainActivity.this, "Invalid id entered. Please enter a numeric id", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract vacation fields and print
        Long id = null;
        String title = null;
        String start = null;
        String end = null;
        try {
            Method getId = vacationObj.getClass().getMethod("getId");
            Object o = getId.invoke(vacationObj);
            if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue();
        } catch (Exception ignored) {}
        try {
            Method getTitle = vacationObj.getClass().getMethod("getTitle");
            Object o = getTitle.invoke(vacationObj);
            if (o != null) title = o.toString();
        } catch (Exception ignored) {}
        try {
            Method getStart = vacationObj.getClass().getMethod("getStartDate");
            Object o = getStart.invoke(vacationObj);
            if (o != null) start = o.toString();
        } catch (Exception ignored) {}
        try {
            Method getEnd = vacationObj.getClass().getMethod("getEndDate");
            Object o = getEnd.invoke(vacationObj);
            if (o != null) end = o.toString();
        } catch (Exception ignored) {}

        reportGenerator.printToTerminal("Search result:");
        reportGenerator.printVacationDebug(id, title, start, end);

        // Print associated excursions (uses existing helper)
        try {
            printExcursionsForVacation(repo, vacationId);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                NotificationScheduler.checkDatabaseAndNotifyToday(this);
            }
            // do not reset sNotificationsChecked — we only want the prompt/check once per process
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If a child activity signaled success, refresh UI / reload data.
        if ((requestCode == REQ_ADD || requestCode == REQ_DELETE) && resultCode == RESULT_OK) {
            loadVacations();
        }
    }

    // Example reload method - adjust to your adapter / list implementation.
    private void loadVacations() {
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) return;
        try {
            // prefer direct call if available
            java.util.List<?> items = repo.getVacations();
            if (reportGenerator != null) {
                reportGenerator.printToTerminal("Loaded " + (items == null ? 0 : items.size()) + " vacation(s)");
            }
            if (items != null) {
                for (Object it : items) {
                    if (it == null) continue;
                    try {
                        Method getId = it.getClass().getMethod("getId");
                        Method getTitle = it.getClass().getMethod("getTitle");
                        Method getStart = null;
                        Method getEnd = null;
                        // common names for date fields
                        try { getStart = it.getClass().getMethod("getStartDate"); } catch (Exception ignored) {}
                        try { getEnd = it.getClass().getMethod("getEndDate"); } catch (Exception ignored) {}
                        // invoke safely
                        Long id = null;
                        String title = null;
                        String start = null;
                        String end = null;
                        try { Object o = getId.invoke(it); if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue(); } catch (Exception ignored) {}
                        try { Object o = getTitle.invoke(it); if (o != null) title = o.toString(); } catch (Exception ignored) {}
                        try { if (getStart != null) { Object o = getStart.invoke(it); if (o != null) start = o.toString(); } } catch (Exception ignored) {}
                        try { if (getEnd != null) { Object o = getEnd.invoke(it); if (o != null) end = o.toString(); } } catch (Exception ignored) {}

                        if (reportGenerator != null) {
                            reportGenerator.printVacationDebug(id, title, start, end);
                        }

                        // Try to print excursions by asking the repository for excursions for this vacation id
                        if (id != null) {
                            try {
                                printExcursionsForVacation(repo, id);
                            } catch (Exception ignored) {}
                        }

                    } catch (Exception e) {
                        // fallback: print item toString
                        if (reportGenerator != null) {
                            reportGenerator.printToTerminal("Vacation: " + it.toString());
                        }
                        // still attempt to find excursions via repo if we can extract an id
                        try {
                            Method getId = it.getClass().getMethod("getId");
                            Object o = getId.invoke(it);
                            Long id = null;
                            if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue();
                            if (id != null) printExcursionsForVacation(repo, id);
                        } catch (Exception ignored2) {}
                    }
                }
            }
            // update your adapter here, e.g. mAdapter.setItems(items); mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            // reflection fallback if repo method signature differs
            try {
                Method m = repo.getClass().getMethod("getVacations");
                Object res = m.invoke(repo);
                if (res instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> items = (List<?>) res;
                    if (reportGenerator != null) {
                        reportGenerator.printToTerminal("Loaded (reflected) " + (items == null ? 0 : items.size()) + " vacation(s)");
                    }
                    if (items != null) {
                        for (Object it : items) {
                            if (it == null) continue;
                            try {
                                Method getId = it.getClass().getMethod("getId");
                                Method getTitle = it.getClass().getMethod("getTitle");
                                Method getStart = null;
                                Method getEnd = null;
                                try { getStart = it.getClass().getMethod("getStartDate"); } catch (Exception ignored) {}
                                try { getEnd = it.getClass().getMethod("getEndDate"); } catch (Exception ignored) {}

                                Long id = null;
                                String title = null;
                                String start = null;
                                String end = null;
                                try { Object o = getId.invoke(it); if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue(); } catch (Exception ignored) {}
                                try { Object o = getTitle.invoke(it); if (o != null) title = o.toString(); } catch (Exception ignored) {}
                                try { if (getStart != null) { Object o = getStart.invoke(it); if (o != null) start = o.toString(); } } catch (Exception ignored) {}
                                try { if (getEnd != null) { Object o = getEnd.invoke(it); if (o != null) end = o.toString(); } } catch (Exception ignored) {}

                                if (reportGenerator != null) {
                                    reportGenerator.printVacationDebug(id, title, start, end);
                                }

                                if (id != null) {
                                    try { printExcursionsForVacation(repo, id); } catch (Exception ignored) {}
                                }
                            } catch (Exception ignored) {
                                if (reportGenerator != null) {
                                    reportGenerator.printToTerminal("Vacation: " + it.toString());
                                }
                            }
                        }
                    }
                    // update your adapter here
                }
            } catch (Exception ignored) {}
        }
    }

    // Try to discover common repository methods that return excursions for a vacation id,
    // invoke them reflectively and print each excursion.
    private void printExcursionsForVacation(Object repoObj, Long vacationId) {
        if (repoObj == null || vacationId == null || reportGenerator == null) return;

        String[] candidateRepoMethods = new String[] {
                "getExcursionsForVacation", "getExcursionsByVacationId", "getExcursionsForVacId",
                "getExcursionsForVacationId", "getExcursionsByVacation", "getExcursions"
        };

        Object found = null;
        Method foundMethod = null;
        for (String name : candidateRepoMethods) {
            try {
                // try Long wrapper
                Method m = repoObj.getClass().getMethod(name, Long.class);
                if (m != null) {
                    foundMethod = m;
                    found = m.invoke(repoObj, vacationId);
                    if (found != null) break;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                // try primitive long parameter
                try {
                    Method m2 = repoObj.getClass().getMethod(name, long.class);
                    if (m2 != null) {
                        foundMethod = m2;
                        found = m2.invoke(repoObj, vacationId.longValue());
                        if (found != null) break;
                    }
                } catch (Exception ignored2) {}
            }
            // try zero-arg method (some repos return all excursions and filter client-side)
            if (found == null) {
                try {
                    Method m0 = repoObj.getClass().getMethod(name);
                    if (m0 != null) {
                        foundMethod = m0;
                        found = m0.invoke(repoObj);
                        if (found != null) break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (found == null) return;

        List<?> excList = null;
        if (found instanceof List) {
            excList = (List<?>) found;
        } else if (found.getClass().isArray()) {
            Object[] arr = (Object[]) found;
            excList = new ArrayList<>(Arrays.asList(arr));
        }

        if (excList == null || excList.isEmpty()) return;

        // If the repo returned a full list, filter by vacationId where possible
        List<Object> toPrint = new ArrayList<>();
        for (Object ex : excList) {
            if (ex == null) continue;
            boolean matches = true;
            // if excursion has getVacationId, verify it matches
            try {
                Method getVacId = ex.getClass().getMethod("getVacationId");
                Object o = getVacId.invoke(ex);
                Long vid = null;
                if (o instanceof Long) vid = (Long) o; else if (o instanceof Number) vid = ((Number) o).longValue();
                if (vid != null && !vid.equals(vacationId)) matches = false;
            } catch (Exception ignored) {}
            if (matches) toPrint.add(ex);
        }

        if (toPrint.isEmpty()) return;

        reportGenerator.printToTerminal("  Found " + toPrint.size() + " excursion(s) for vacation id=" + vacationId + ":");
        for (Object ex : toPrint) {
            if (ex == null) continue;
            Long exId = null;
            String exTitle = null;
            String exDate = null;
            try {
                Method getId = ex.getClass().getMethod("getId");
                try { Object o = getId.invoke(ex); if (o instanceof Long) exId = (Long) o; else if (o instanceof Number) exId = ((Number) o).longValue(); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            try {
                Method getTitle = ex.getClass().getMethod("getTitle");
                try { Object o = getTitle.invoke(ex); if (o != null) exTitle = o.toString(); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            try {
                Method getDate = ex.getClass().getMethod("getExcursionDate");
                try { Object o = getDate.invoke(ex); if (o != null) exDate = o.toString(); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            // fallback common names
            if (exDate == null) {
                String[] dateNames = new String[] {"getDate", "getStartDate", "getStart"};
                for (String dn : dateNames) {
                    try {
                        Method m = ex.getClass().getMethod(dn);
                        try { Object o = m.invoke(ex); if (o != null) { exDate = o.toString(); break; } } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                }
            }

            reportGenerator.printToTerminal("    Excursion id=" + (exId == null ? "null" : exId)
                    + ", title=\"" + (exTitle == null ? "" : exTitle) + "\""
                    + ", date=\"" + (exDate == null ? "" : exDate) + "\"");
        }
    }
}
