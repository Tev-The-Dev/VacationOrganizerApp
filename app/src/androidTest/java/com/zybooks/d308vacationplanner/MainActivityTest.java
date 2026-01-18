package com.zybooks.d308vacationplanner;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        BadLoginTest.class,
        DatabasePopulateWithBadDataTest.class
})
public class MainActivityTest {
    // JUnit test suite - no implementation required
}
