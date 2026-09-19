package com.orangehrm.assessment.listeners;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a test once, but only when the failure looks environmental.
 *
 * <p>Retrying indiscriminately would hide real defects: a failed assertion means the
 * application did the wrong thing, and running it again only converts a reproducible bug
 * into an intermittent one. Only failures that indicate a timing or infrastructure problem
 * - a lost session, a mid-render click, a slow response on the shared demo tenant - are
 * retried, and the retry is capped at one attempt so a genuinely broken test still fails.
 */
public class RetryAnalyzer implements IRetryAnalyzer {
    private static final int MAX_RETRIES = 1;
    private int retries;

    @Override
    public boolean retry(ITestResult result) {
        Throwable throwable = result.getThrowable();
        if (retries >= MAX_RETRIES || throwable == null) {
            return false;
        }

        // An assertion failure is a verdict about the application and is never retried.
        if (throwable instanceof AssertionError) {
            return false;
        }

        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        boolean transientFailure = throwable instanceof TimeoutException
                || throwable instanceof StaleElementReferenceException
                || throwable instanceof ElementClickInterceptedException
                || throwable instanceof NoSuchSessionException
                || message.contains("timeout")
                || message.contains("err_connection")
                || message.contains("chrome not reachable")
                || message.contains("session deleted")
                || message.contains("connection reset");

        if (transientFailure) {
            retries++;
            return true;
        }

        return false;
    }
}