// java
package com.zybooks.d308vacationplanner;

import android.content.Context;
import android.util.Log;

public class ReportGenerator {
    private static final String TAG = "ReportGenerator";
    private final Context context;

    public ReportGenerator(Context context) {
        this.context = context.getApplicationContext();
        Log.d(TAG, "instantiated");
    }

    public void printToTerminal(String message) {
        Log.d(TAG, message == null ? "null" : message);
    }

    public void printVacationDebug(Long id, String title, String start, String end) {
        Log.d(TAG, "Vacation id=" + (id == null ? "null" : id)
                + ", title=\"" + (title == null ? "" : title) + "\""
                + ", start=\"" + (start == null ? "" : start) + "\""
                + ", end=\"" + (end == null ? "" : end) + "\"");
    }
}
