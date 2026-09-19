package com.orangehrm.assessment.utils;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Smart waiting primitives shared by every page object.
 *
 * <p>Playwright auto-waits before each interaction; Selenium does not. Rather than scatter
 * ad-hoc {@code WebDriverWait} instances (or, worse, {@code Thread.sleep}) through the page
 * objects, every interaction in this framework goes through this class. That keeps the page
 * objects readable and gives one place to fix a waiting rule.
 *
 * <p>The waits here are deliberately condition-based rather than time-based. An element being
 * present, or even visible, does not mean it is ready: this application renders its forms
 * before the data arrives, so a field can be visible and still empty. Waiting for the
 * <em>state the test depends on</em> is what makes these tests stable.
 */
public final class WaitUtils {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private WaitUtils() {
    }

    private static FluentWait<WebDriver> waiter(WebDriver driver, Duration timeout) {
        return new WebDriverWait(driver, timeout, POLL_INTERVAL)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(NoSuchElementException.class);
    }

    public static <T> T until(WebDriver driver, ExpectedCondition<T> condition) {
        return until(driver, condition, DEFAULT_TIMEOUT);
    }

    public static <T> T until(WebDriver driver, ExpectedCondition<T> condition, Duration timeout) {
        return waiter(driver, timeout).until(condition);
    }

    /**
     * Waits for an arbitrary predicate. Used where readiness is expressed as application
     * state (a populated field, a settled value) rather than as element presence.
     */
    public static void untilTrue(WebDriver driver, Function<WebDriver, Boolean> predicate, Duration timeout) {
        waiter(driver, timeout).until(predicate::apply);
    }

    public static WebElement visible(WebDriver driver, By locator) {
        return until(driver, ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public static WebElement clickable(WebDriver driver, By locator) {
        return until(driver, ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Clicks an element, retrying while another element (an overlay, a toast, a re-render)
     * is intercepting the click. A single interception is a timing artefact, not a defect,
     * so it is absorbed here instead of failing the test.
     */
    public static void click(WebDriver driver, By locator) {
        until(driver, d -> {
            try {
                WebElement element = d.findElement(locator);
                if (!element.isDisplayed() || !element.isEnabled()) {
                    return null;
                }
                element.click();
                return true;
            } catch (ElementClickInterceptedException | StaleElementReferenceException retryable) {
                return null;
            }
        });
    }

    public static void click(WebDriver driver, WebElement element) {
        until(driver, d -> {
            try {
                element.click();
                return true;
            } catch (ElementClickInterceptedException retryable) {
                return null;
            }
        });
    }

    /**
     * Replaces the contents of a field with {@code value}.
     *
     * <p>{@code sendKeys} is used rather than any scripted value assignment because several
     * controls in this application are debounced and only react to genuine keyboard input.
     *
     * <p>Clearing is done with a select-all followed by the typed value, and the result is
     * verified rather than assumed. Some fields here are re-populated by the framework after
     * being cleared - the Employee Id field restores its pre-filled sequential number - so
     * neither "the field is empty" nor "the keys were sent" proves anything. Confirming the
     * value the field ends up holding is the only check that does, and it is retried because
     * a repopulation can land between the clear and the typing.
     */
    public static void type(WebDriver driver, By locator, String value) {
        until(driver, d -> {
            WebElement field = d.findElement(locator);
            if (!field.isDisplayed() || !field.isEnabled()) {
                return null;
            }
            field.sendKeys(Keys.chord(Keys.CONTROL, "a"), value);
            return value.equals(valueOf(field)) ? true : null;
        });
    }

    public static String valueOf(WebElement element) {
        String value = element.getAttribute("value");
        return value == null ? "" : value;
    }

    /**
     * Waits until a field holds a non-blank value, proving the form has been hydrated with
     * server data. Writing to a form before this point silently loses the change, because the
     * arriving response re-populates the inputs.
     */
    public static void untilFieldPopulated(WebDriver driver, By locator, Duration timeout) {
        WebElement field = visible(driver, locator);
        untilTrue(driver, d -> !valueOf(field).isBlank(), timeout);
    }

    public static void untilUrlContains(WebDriver driver, String fragment) {
        until(driver, ExpectedConditions.urlContains(fragment));
    }

    public static boolean isPresent(WebDriver driver, By locator) {
        List<WebElement> elements = driver.findElements(locator);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    public static void untilGone(WebDriver driver, WebElement element) {
        until(driver, ExpectedConditions.stalenessOf(element));
    }
}
