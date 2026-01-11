package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

    // SharedPrefs keys for auth
    private static final String PREFS_AUTH = "auth_prefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PWD_HASH = "pwd_hash";
    private static final String KEY_SALT = "pwd_salt";
    private static final String KEY_ITER = "pwd_iter";
    private static final String KEY_NEEDS_CHANGE = "needs_change";

    // Guards so we only check/ask once per process launch
    private static boolean sNotificationsChecked = false;
    private static boolean sAuthenticated = false; // process-lifetime auth flag

    private ReportGenerator reportGenerator;
    private View mVacationsButton;
    private View mChangeCredsButton;

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

        mVacationsButton = findViewById(R.id.btn_view_vacations);
        if (mVacationsButton != null) mVacationsButton.setVisibility(View.GONE);

        mChangeCredsButton = findViewById(R.id.btn_change_credentials);
        if (mChangeCredsButton != null) {
            mChangeCredsButton.setVisibility(View.GONE);
            mChangeCredsButton.setOnClickListener(v -> showChangeCredentialsDialog());
        }

        // Initialize report generator
        reportGenerator = new ReportGenerator(this);
        if (reportGenerator != null) {
            reportGenerator.printToTerminal("ReportGenerator ready");
            reportGenerator.printVacationDebug(1L, "Beach Trip (demo)", "2026-06-01", "2026-06-07");
        }

        ImageButton navMenuBtn = findViewById(R.id.btn_nav_menu);
        if (navMenuBtn != null) {
            navMenuBtn.setOnClickListener(this::showNavMenu);
        }

        // Ensure default credentials exist
        ensureDefaultCredentials();

        // Only prompt for login if not already authenticated in this process
        if (sAuthenticated) {
            revealVacationsButton();
        } else {
            showLoginDialog();
        }

        // Notification permission/check once per process
        if (!sNotificationsChecked) {
            sNotificationsChecked = true;
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
                NotificationScheduler.checkDatabaseAndNotifyToday(this);
            }
        }
    }

    private void ensureDefaultCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE);
        if (!prefs.contains(KEY_PWD_HASH)) {
            String defaultUser = "Admin";
            String defaultPwd = "AdminPassword";
            String saltBase64 = PasswordUtil.generateSaltBase64();
            byte[] salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP);
            int iter = PasswordUtil.getDefaultIterations();
            char[] pwdChars = defaultPwd.toCharArray();
            String hashBase64 = PasswordUtil.hashPasswordBase64(pwdChars, salt, iter);
            Arrays.fill(pwdChars, '\0');

            prefs.edit()
                    .putString(KEY_USERNAME, defaultUser)
                    .putString(KEY_SALT, saltBase64)
                    .putString(KEY_PWD_HASH, hashBase64)
                    .putInt(KEY_ITER, iter)
                    .putBoolean(KEY_NEEDS_CHANGE, true)
                    .apply();
            if (reportGenerator != null) reportGenerator.printToTerminal("Default credentials created (Admin) - change required");
        }
    }

    private void showLoginDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE);
        final String storedUser = prefs.getString(KEY_USERNAME, "Admin");
        final String storedSaltBase64 = prefs.getString(KEY_SALT, null);
        final String storedHash = prefs.getString(KEY_PWD_HASH, null);
        final int iterations = prefs.getInt(KEY_ITER, PasswordUtil.getDefaultIterations());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText userInput = new EditText(this);
        userInput.setHint("Username");
        userInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        userInput.setText(storedUser);
        layout.addView(userInput);

        final EditText pwdInput = new EditText(this);
        pwdInput.setHint("Password");
        pwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(pwdInput);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Log in")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Log in", (dialog, which) -> {
                    String user = userInput.getText() == null ? "" : userInput.getText().toString().trim();
                    char[] pwd = pwdInput.getText() == null ? new char[0] : pwdInput.getText().toString().toCharArray();

                    boolean ok = false;
                    if (user != null && user.equals(storedUser) && storedSaltBase64 != null && storedHash != null) {
                        byte[] salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP);
                        ok = PasswordUtil.verifyPassword(pwd, storedHash, salt, iterations);
                    }

                    Arrays.fill(pwd, '\0');

                    if (ok) {
                        if (reportGenerator != null) reportGenerator.printToTerminal("User logged in: " + user);
                        Toast.makeText(MainActivity.this, "Login successful", Toast.LENGTH_SHORT).show();

                        boolean needsChange = prefs.getBoolean(KEY_NEEDS_CHANGE, false);
                        if (needsChange) {
                            if (reportGenerator != null) reportGenerator.printToTerminal("Default credentials used; forcing change");
                            showChangeCredentialsDialog();
                        } else {
                            sAuthenticated = true;
                            revealVacationsButton();
                        }
                    } else {
                        if (reportGenerator != null) reportGenerator.printToTerminal("Login failed for user: " + user);
                        Toast.makeText(MainActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                        showLoginDialog();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "Login required to view vacations", Toast.LENGTH_SHORT).show();
                })
                .create();

        dlg.show();
    }

    private void showChangeCredentialsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE);
        final String currentUser = prefs.getString(KEY_USERNAME, "Admin");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText userInput = new EditText(this);
        userInput.setHint("New username");
        userInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        userInput.setText(currentUser);
        layout.addView(userInput);

        final EditText pwdInput = new EditText(this);
        pwdInput.setHint("New password");
        pwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(pwdInput);

        final EditText pwdConfirm = new EditText(this);
        pwdConfirm.setHint("Confirm password");
        pwdConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(pwdConfirm);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Change credentials (required)")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Change", (dialog, which) -> {
                    String newUser = userInput.getText() == null ? "" : userInput.getText().toString().trim();
                    char[] newPwd = pwdInput.getText() == null ? new char[0] : pwdInput.getText().toString().toCharArray();
                    char[] confirm = pwdConfirm.getText() == null ? new char[0] : pwdConfirm.getText().toString().toCharArray();

                    if (newUser.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Username cannot be empty", Toast.LENGTH_SHORT).show();
                        showChangeCredentialsDialog();
                        return;
                    }
                    if (newPwd.length < 6) {
                        Toast.makeText(MainActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        showChangeCredentialsDialog();
                        return;
                    }
                    boolean match = newPwd.length == confirm.length;
                    if (match) {
                        for (int i = 0; i < newPwd.length; i++) {
                            if (newPwd[i] != confirm[i]) { match = false; break; }
                        }
                    }
                    if (!match) {
                        Toast.makeText(MainActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                        Arrays.fill(newPwd, '\0');
                        Arrays.fill(confirm, '\0');
                        showChangeCredentialsDialog();
                        return;
                    }

                    String saltBase64 = PasswordUtil.generateSaltBase64();
                    byte[] salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP);
                    int iter = PasswordUtil.getDefaultIterations();
                    String hashBase64 = PasswordUtil.hashPasswordBase64(newPwd, salt, iter);
                    Arrays.fill(newPwd, '\0');
                    Arrays.fill(confirm, '\0');

                    prefs.edit()
                            .putString(KEY_USERNAME, newUser)
                            .putString(KEY_SALT, saltBase64)
                            .putString(KEY_PWD_HASH, hashBase64)
                            .putInt(KEY_ITER, iter)
                            .putBoolean(KEY_NEEDS_CHANGE, false)
                            .apply();

                    if (reportGenerator != null) reportGenerator.printToTerminal("Credentials changed for user: " + newUser);
                    Toast.makeText(MainActivity.this, "Credentials updated", Toast.LENGTH_SHORT).show();

                    sAuthenticated = true;
                    revealVacationsButton();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "You must change default credentials to continue", Toast.LENGTH_SHORT).show();
                    showChangeCredentialsDialog();
                })
                .create();

        dlg.show();
    }

    private void revealVacationsButton() {
        if (mVacationsButton != null) {
            mVacationsButton.setVisibility(View.VISIBLE);
            mVacationsButton.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, VacationActivity.class)));
        }
        if (mChangeCredsButton != null) {
            mChangeCredsButton.setVisibility(View.VISIBLE);
        }
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
                loadVacations();
                Toast.makeText(this, "Report Generated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Report generator unavailable", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == 2) {
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
                        Toast.makeText(MainActivity.this, "Invalid id format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Reporting / repository helper methods (reflection-friendly) ---

    private void loadVacations() {
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) {
            if (reportGenerator != null) reportGenerator.printToTerminal("Repository unavailable");
            return;
        }
        try {
            List<?> items = repo.getVacations();
            if (reportGenerator != null) reportGenerator.printToTerminal("Loaded " + (items == null ? 0 : items.size()) + " vacation(s)");
            if (items != null) {
                for (Object it : items) {
                    if (it == null) continue;
                    try {
                        Long id = null;
                        String title = null;
                        String start = null;
                        String end = null;
                        try {
                            Method getId = it.getClass().getMethod("getId");
                            Object o = getId.invoke(it);
                            if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue();
                        } catch (Exception ignored) {}
                        try {
                            Method getTitle = it.getClass().getMethod("getTitle");
                            Object o = getTitle.invoke(it);
                            if (o != null) title = o.toString();
                        } catch (Exception ignored) {}
                        try {
                            Method getStart = it.getClass().getMethod("getStartDate");
                            Object o = getStart.invoke(it);
                            if (o != null) start = o.toString();
                        } catch (Exception ignored) {}
                        try {
                            Method getEnd = it.getClass().getMethod("getEndDate");
                            Object o = getEnd.invoke(it);
                            if (o != null) end = o.toString();
                        } catch (Exception ignored) {}

                        if (reportGenerator != null) reportGenerator.printVacationDebug(id, title, start, end);
                    } catch (Exception e) {
                        if (reportGenerator != null) reportGenerator.printToTerminal("Error printing vacation: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // fallback via reflection
            try {
                Method m = repo.getClass().getMethod("getVacations");
                Object res = m.invoke(repo);
                if (res instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> items = (List<?>) res;
                    if (reportGenerator != null) reportGenerator.printToTerminal("Loaded (reflected) " + (items == null ? 0 : items.size()) + " vacation(s)");
                    if (items != null) {
                        for (Object it : items) {
                            if (it == null) continue;
                            try {
                                Long id = null;
                                String title = null;
                                String start = null;
                                String end = null;
                                try {
                                    Method getId = it.getClass().getMethod("getId");
                                    Object o = getId.invoke(it);
                                    if (o instanceof Long) id = (Long) o; else if (o instanceof Number) id = ((Number) o).longValue();
                                } catch (Exception ignored) {}
                                try {
                                    Method getTitle = it.getClass().getMethod("getTitle");
                                    Object o = getTitle.invoke(it);
                                    if (o != null) title = o.toString();
                                } catch (Exception ignored) {}
                                try {
                                    Method getStart = it.getClass().getMethod("getStartDate");
                                    Object o = getStart.invoke(it);
                                    if (o != null) start = o.toString();
                                } catch (Exception ignored) {}
                                try {
                                    Method getEnd = it.getClass().getMethod("getEndDate");
                                    Object o = getEnd.invoke(it);
                                    if (o != null) end = o.toString();
                                } catch (Exception ignored) {}
                                if (reportGenerator != null) reportGenerator.printVacationDebug(id, title, start, end);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void searchAndPrintVacation(Long vacationId) {
        if (vacationId == null || reportGenerator == null) return;
        VacationRepository repo = VacationRepository.getInstance(this);
        if (repo == null) {
            reportGenerator.printToTerminal("Repository unavailable");
            return;
        }

        Object vacationObj = null;

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
                try {
                    Method m2 = repo.getClass().getMethod(name, long.class);
                    Object res2 = m2.invoke(repo, vacationId.longValue());
                    if (res2 != null) { vacationObj = res2; break; }
                } catch (Exception ignored2) {}
            }
        }

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

        try {
            printExcursionsForVacation(repo, vacationId);
        } catch (Exception ignored) {}
    }

    private void printExcursionsForVacation(Object repoObj, Long vacationId) {
        if (repoObj == null || vacationId == null || reportGenerator == null) return;

        String[] candidateRepoMethods = new String[] {
                "getExcursionsForVacation", "getExcursionsByVacationId", "getExcursionsForVacId",
                "getExcursionsForVacationId", "getExcursionsByVacation", "getExcursions"
        };

        Object found = null;
        for (String name : candidateRepoMethods) {
            try {
                Method m = repoObj.getClass().getMethod(name, Long.class);
                found = m.invoke(repoObj, vacationId);
                if (found != null) break;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                try {
                    Method m2 = repoObj.getClass().getMethod(name, long.class);
                    found = m2.invoke(repoObj, vacationId.longValue());
                    if (found != null) break;
                } catch (Exception ignored2) {}
            }
            if (found == null) {
                try {
                    Method m0 = repoObj.getClass().getMethod(name);
                    found = m0.invoke(repoObj);
                    if (found != null) break;
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

        List<Object> toPrint = new ArrayList<>();
        for (Object ex : excList) {
            if (ex == null) continue;
            boolean matches = true;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                NotificationScheduler.checkDatabaseAndNotifyToday(this);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQ_ADD || requestCode == REQ_DELETE) && resultCode == RESULT_OK) {
            loadVacations();
        }
    }
}
