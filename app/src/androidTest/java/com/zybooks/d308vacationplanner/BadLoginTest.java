package com.zybooks.d308vacationplanner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import androidx.test.espresso.intent.Intents;

@RunWith(AndroidJUnit4.class)
public class BadLoginTest {

    private static final String TAG = "BadLoginTest";
    private ActivityScenario<?> scenario;

    private static final String PREFS_AUTH = "auth_prefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PWD_HASH = "pwd_hash";
    private static final String KEY_SALT = "pwd_salt";
    private static final String KEY_ITER = "pwd_iter";
    private static final String KEY_NEEDS_CHANGE = "needs_change";

    @Before
    public void setUp() {
        Intents.init();
        scenario = null;
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
        Intents.release();
    }

    // Helper to write deterministic credentials into SharedPreferences using PasswordUtil
    private void writeCredentials(String user, String plainPassword, boolean needsChange) {
        Context ctx = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);

        String saltBase64 = PasswordUtil.generateSaltBase64();
        byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
        int iter = PasswordUtil.getDefaultIterations();
        char[] pwdChars = plainPassword.toCharArray();
        String hashBase64 = PasswordUtil.hashPasswordBase64(pwdChars, salt, iter);
        // clear char array
        for (int i = 0; i < pwdChars.length; i++) pwdChars[i] = '\0';

        prefs.edit()
                .putString(KEY_USERNAME, user)
                .putString(KEY_SALT, saltBase64)
                .putString(KEY_PWD_HASH, hashBase64)
                .putInt(KEY_ITER, iter)
                .putBoolean(KEY_NEEDS_CHANGE, needsChange)
                .apply();
    }

    @Test
    public void badCredentials_doNotStartVacationActivity() throws InterruptedException {
        writeCredentials("Admin", "AdminPassword", false);
        scenario = ActivityScenario.launch(MainActivity.class);

        onView(withHint("Username")).perform(typeText("BadUser"), closeSoftKeyboard());
        onView(withHint("Password")).perform(typeText("BadPassword"), closeSoftKeyboard());
        // click the dialog positive button explicitly to avoid matching the dialog title
        onView(allOf(withId(android.R.id.button1), isDisplayed())).perform(click());

        Thread.sleep(500);

        List<Intent> captured = Intents.getIntents();
        boolean vacationLaunched = false;
        for (Intent it : captured) {
            if (it != null && it.getComponent() != null &&
                    it.getComponent().getClassName().equals(VacationActivity.class.getName())) {
                vacationLaunched = true;
                break;
            }
        }

        // Clear, single-line output for pass/fail
        if (vacationLaunched) {
            Log.i(TAG, "Login SUCCEEDED: VacationActivity started (unexpected for bad credentials)");
            System.out.println("TEST_RESULT: Login SUCCEEDED (unexpected for bad credentials)");
        } else {
            Log.i(TAG, "Login FAILED as expected: VacationActivity NOT started");
            System.out.println("TEST_RESULT: Login FAILED as expected");
        }

        assertFalse("VacationActivity should not be launched with bad credentials", vacationLaunched);
    }

    @Test
    public void goodCredentials_startVacationActivity_and_testDatabase() throws InterruptedException {
        writeCredentials("Admin", "AdminPassword", false);
        scenario = ActivityScenario.launch(MainActivity.class);

        // enter the stored username ("Admin")
        onView(withHint("Username")).perform(typeText(""), closeSoftKeyboard());
        onView(withHint("Password")).perform(typeText("AdminPassword"), closeSoftKeyboard());
        // click the dialog positive button explicitly
        onView(allOf(withId(android.R.id.button1), isDisplayed())).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.btn_view_vacations)).perform(click());

        Thread.sleep(300);

        List<Intent> captured = Intents.getIntents();
        boolean vacationLaunched = false;
        for (Intent it : captured) {
            if (it != null && it.getComponent() != null &&
                    it.getComponent().getClassName().equals(VacationActivity.class.getName())) {
                vacationLaunched = true;
                break;
            }
        }

        // Clear, single-line output for pass/fail
        if (vacationLaunched) {
            Log.i(TAG, "Login SUCCEEDED: VacationActivity started");
            System.out.println("TEST_RESULT: Login SUCCEEDED");
        } else {
            Log.i(TAG, "Login FAILED: VacationActivity NOT started (unexpected for valid credentials)");
            System.out.println("TEST_RESULT: Login FAILED (unexpected for valid credentials)");
        }

        assertTrue("VacationActivity should be launched with valid credentials", vacationLaunched);
    }
}
