// java
package com.zybooks.d308vacationplanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.zybooks.d308vacationplanner.repo.VacationRepository;

import java.lang.reflect.Method;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_ADD = 100;
    private static final int REQ_DELETE = 101;
    private static final int REQ_NOTIF = 4001;

    // Guard so we only check/ask for notifications once per process launch
    private static boolean sNotificationsChecked = false;

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
            // update your adapter here, e.g. mAdapter.setItems(items); mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            // reflection fallback if repo method signature differs
            try {
                Method m = repo.getClass().getMethod("getVacations");
                Object res = m.invoke(repo);
                if (res instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> items = (List<?>) res;
                    // update your adapter here
                }
            } catch (Exception ignored) {}
        }
    }
}
