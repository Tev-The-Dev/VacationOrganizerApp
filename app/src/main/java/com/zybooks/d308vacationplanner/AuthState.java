// java
package com.zybooks.d308vacationplanner;

public final class AuthState {
    private static volatile boolean sAuthenticated = false;

    private AuthState() { /* no-op */ }

    public static boolean isAuthenticated() {
        return sAuthenticated;
    }

    public static void setAuthenticated(boolean value) {
        sAuthenticated = value;
    }

    public static void clear() {
        sAuthenticated = false;
    }
}
