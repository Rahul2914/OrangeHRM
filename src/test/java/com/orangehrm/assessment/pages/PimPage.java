package com.orangehrm.assessment.pages;

import com.orangehrm.assessment.model.EmployeeData;
import com.orangehrm.assessment.utils.WaitUtils;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class PimPage {
    private static final By PIM_MENU =
            By.xpath("//span[contains(@class,'oxd-main-menu-item--name')][normalize-space()='PIM']");
    private static final By ADD_BUTTON = By.xpath("//button[normalize-space()='Add']");
    private static final By SAVE_BUTTON = By.xpath("//button[normalize-space()='Save']");
    private static final By SEARCH_BUTTON = By.xpath("//button[normalize-space()='Search']");
    private static final By FIRST_NAME = By.name("firstName");
    private static final By LAST_NAME = By.name("lastName");
    private static final By SUCCESS_TOAST = By.cssSelector(".oxd-toast");

    private static final By DRIVERS_LICENCE_INPUT = By.xpath(
            "//label[normalize-space()=\"Driver's License Number\"]/ancestor::div[contains(@class,'oxd-input-group')]//input");
    private static final By EMPLOYEE_ID_INPUT = By.xpath(
            "//label[normalize-space()='Employee Id']/ancestor::div[contains(@class,'oxd-input-group')]//input");

    private final WebDriver driver;

    public PimPage(WebDriver driver) {
        this.driver = driver;
    }

    public void open() {
        WaitUtils.click(driver, PIM_MENU);
        WaitUtils.untilUrlContains(driver, "/pim/");
    }

    /**
     * Navigates straight to an existing employee's personal details screen so a stage
     * that did not create the record can still act on it.
     *
     * @param siteRootUrl the site root (for example {@code https://host}), not the login URL
     */
    public void openEmployeeRecord(String siteRootUrl, String employeeId) {
        driver.get(rootOf(siteRootUrl) + "/web/index.php/pim/viewPersonalDetails/empNumber/" + employeeId);
        WaitUtils.untilUrlContains(driver, "/viewPersonalDetails/");
        waitForPersonalDetailsToLoad();
    }

    /**
     * Waits until the Personal Details form holds the employee's data.
     *
     * <p>The form renders its inputs before the employee is fetched, so the fields exist and
     * are visible while still empty. Anything typed during that window is discarded when the
     * response arrives and the form re-populates - which silently turns an update into a save
     * of an empty value. A populated first name is used as the signal because every employee
     * created by these tests has one.
     */
    private void waitForPersonalDetailsToLoad() {
        WaitUtils.untilFieldPopulated(driver, FIRST_NAME, Duration.ofSeconds(20));
    }

    public String createEmployee(EmployeeData employeeData) {
        open();
        WaitUtils.click(driver, ADD_BUTTON);
        WaitUtils.type(driver, FIRST_NAME, employeeData.firstName());
        WaitUtils.type(driver, LAST_NAME, employeeData.lastName());

        // Replace the pre-filled sequential Employee Id, which collides on the shared demo
        // tenant and causes the save to be rejected.
        WaitUtils.type(driver, EMPLOYEE_ID_INPUT, employeeData.employeeNumber());

        clickPrimarySave();
        WaitUtils.untilUrlContains(driver, "/viewPersonalDetails/");
        return extractEmpNumberFromUrl();
    }

    /**
     * Updates a standard Personal Details field. Driver's License Number is used because it
     * is a plain text field that ships with every OrangeHRM tenant, so the update stage does
     * not depend on a tenant-specific custom field being configured.
     */
    public void updateDriversLicenseNumber(String fieldValue) {
        WaitUtils.type(driver, DRIVERS_LICENCE_INPUT, fieldValue);
        clickPrimarySave();

        // Clicking Save only starts the update. Returning immediately would let the caller
        // reload while the request is still in flight, cancelling it and losing the change.
        // The success toast is the application's own confirmation that the write completed,
        // so it is awaited as proof before anyone reloads the page.
        awaitSaveConfirmation();
    }

    private void awaitSaveConfirmation() {
        WebElement toast = WaitUtils.visible(driver, SUCCESS_TOAST);
        // Letting the toast clear keeps it from covering controls used by the next step.
        try {
            WaitUtils.untilGone(driver, toast);
        } catch (TimeoutException toastLingered) {
            // The confirmation was seen, which is what mattered.
        }
    }

    /**
     * Clicks the first Save button on the form.
     *
     * <p>The Personal Details screen renders more than one Save button (one per section), so
     * the button is selected explicitly rather than assuming the page has only one.
     */
    private void clickPrimarySave() {
        WaitUtils.until(driver, d -> {
            List<WebElement> saveButtons = d.findElements(SAVE_BUTTON);
            if (saveButtons.isEmpty() || !saveButtons.get(0).isDisplayed()) {
                return null;
            }
            saveButtons.get(0).click();
            return true;
        });
    }

    public String readEmployeeId() {
        return WaitUtils.valueOf(WaitUtils.visible(driver, EMPLOYEE_ID_INPUT));
    }

    /**
     * Re-reads the field from a freshly loaded page so the assertion proves the value was
     * persisted by the backend rather than being what the previous entry left in the DOM.
     *
     * <p>Whatever value is finally present is returned - including a wrong one - so that a
     * mismatch reports what was really persisted instead of failing with a bare timeout.
     */
    public String readPersistedDriversLicenseNumber(String expectedValue) {
        driver.navigate().refresh();
        waitForPersonalDetailsToLoad();

        WebElement licenceInput = WaitUtils.visible(driver, DRIVERS_LICENCE_INPUT);
        try {
            WaitUtils.untilTrue(driver, d -> expectedValue.equals(WaitUtils.valueOf(licenceInput)),
                    Duration.ofSeconds(15));
        } catch (TimeoutException mismatch) {
            // Fall through and report the value that is actually there.
        }
        return WaitUtils.valueOf(licenceInput);
    }

    public void searchEmployeeById(String employeeId) {
        open();
        WaitUtils.type(driver, EMPLOYEE_ID_INPUT, employeeId);
        WaitUtils.click(driver, SEARCH_BUTTON);
    }

    public boolean isNoRecordsMessageVisible() {
        return WaitUtils.isPresent(driver, By.xpath("//span[contains(normalize-space(),'No Records Found')]"));
    }

    private String extractEmpNumberFromUrl() {
        String currentUrl = driver.getCurrentUrl();
        String[] parts = currentUrl.split("/empNumber/");
        if (parts.length < 2) {
            throw new IllegalStateException("Unable to extract empNumber from URL: " + currentUrl);
        }

        String trailingPart = parts[1];
        int nextSlash = trailingPart.indexOf('/');
        return nextSlash >= 0 ? trailingPart.substring(0, nextSlash) : trailingPart;
    }

    private String rootOf(String siteRootUrl) {
        return siteRootUrl.endsWith("/") ? siteRootUrl.substring(0, siteRootUrl.length() - 1) : siteRootUrl;
    }
}
