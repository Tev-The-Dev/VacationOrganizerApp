// Java
package com.zybooks.d308vacationplanner;

import android.content.Intent;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class DatabasePopulateWithBadDataTest {
    private static final String TAG = "DatabasePopulateBadDataTest";

    @Test
    public void malformedAndOutOfRangeDates_logWarnings() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), AddExcursionActivity.class);
        // supply a vacation range to exercise range checks
        intent.putExtra("vacation_start_date", "2026-01-10");
        intent.putExtra("vacation_end_date", "2026-01-20");

        try (ActivityScenario<AddExcursionActivity> sc = ActivityScenario.launch(intent)) {
            sc.onActivity(activity -> {
                Log.i(TAG, "Launched AddExcursionActivity for bad-data checks");

                try {
                    // call private parseDateStrict(String) to force the "Failed to parse date" log path
                    Method parse = activity.getClass().getDeclaredMethod("parseDateStrict", String.class);
                    parse.setAccessible(true);
                    Object parsed = parse.invoke(activity, "2026-13-99"); // clearly invalid month/day
                    assertNull("Malformed date should parse to null", parsed);
                    Log.i(TAG, "Malformed date parse returned null as expected");

                    // call private isDateInRange(String, String, String) to check out-of-range behavior
                    Method inRange = activity.getClass().getDeclaredMethod("isDateInRange", String.class, String.class, String.class);
                    inRange.setAccessible(true);
                    boolean ok = (Boolean) inRange.invoke(activity, "2026-02-01", "2026-01-10", "2026-01-20");
                    assertFalse("Date outside vacation range should be false", ok);
                    Log.i(TAG, "Out-of-range date correctly reported as not in range");

                } catch (NoSuchMethodException nsme) {
                    Log.w(TAG, "Expected private helper not found via reflection", nsme);
                } catch (Exception e) {
                    Log.w(TAG, "Reflection call failed", e);
                }
            });
        }
        Log.i(TAG, "malformedAndOutOfRangeDates_logWarnings finished");
    }
}
