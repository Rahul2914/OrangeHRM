package com.orangehrm.assessment.pages;

import com.orangehrm.assessment.utils.WaitUtils;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Admin &gt; User Management screens.
 *
 * <p>This page object exists so role-based validation can be executed honestly: the public
 * OrangeHRM demo does not publish a restricted (ESS) account, but an admin is allowed to
 * create one. The role-based test therefore provisions its own ESS user, verifies the
 * restrictions that role implies, and deletes the user afterwards.
 */
public class AdminUserPage {
    private static final By USERNAME_INPUT = By.xpath(
            "//label[normalize-space()='Username']/ancestor::div[contains(@class,'oxd-input-group')]//input");
    private static final By PASSWORD_INPUT = By.xpath(
            "//label[normalize-space()='Password']/ancestor::div[contains(@class,'oxd-input-group')]//input");
    private static final By CONFIRM_PASSWORD_INPUT = By.xpath(
            "//label[normalize-space()='Confirm Password']/ancestor::div[contains(@class,'oxd-input-group')]//input");
    private static final By EMPLOYEE_AUTOCOMPLETE = By.cssSelector("input[placeholder='Type for hints...']");
    private static final By AUTOCOMPLETE_OPTION = By.cssSelector(".oxd-autocomplete-option");
    private static final By DROPDOWN = By.cssSelector(".oxd-select-text");
    private static final By SAVE_BUTTON = By.xpath("//button[normalize-space()='Save']");
    private static final By SEARCH_BUTTON = By.xpath("//button[normalize-space()='Search']");
    private static final By TABLE_CARD = By.cssSelector(".oxd-table-card");

    private final WebDriver driver;

    public AdminUserPage(WebDriver driver) {
        this.driver = driver;
    }

    public void openAddUser(String siteRootUrl) {
        driver.get(rootOf(siteRootUrl) + "/web/index.php/admin/saveSystemUser");
        WaitUtils.visible(driver, USERNAME_INPUT);
    }

    public void openUserList(String siteRootUrl) {
        driver.get(rootOf(siteRootUrl) + "/web/index.php/admin/viewSystemUsers");
        WaitUtils.visible(driver, USERNAME_INPUT);
    }

    /**
     * Creates an enabled ESS user bound to an existing employee.
     */
    public void createEssUser(String employeeFullName, String username, String password) {
        selectDropdownOption(0, "ESS");
        fillEmployeeAutocomplete(employeeFullName);
        selectDropdownOption(1, "Enabled");

        WaitUtils.type(driver, USERNAME_INPUT, username);
        WaitUtils.type(driver, PASSWORD_INPUT, password);
        WaitUtils.type(driver, CONFIRM_PASSWORD_INPUT, password);

        WaitUtils.click(driver, SAVE_BUTTON);
        WaitUtils.untilUrlContains(driver, "/viewSystemUsers");
    }

    /**
     * Deletes the named user so the shared demo tenant is left clean.
     *
     * <p>The row is located by username rather than by position. Clicking Search only starts
     * the request, so the previous, unfiltered result set stays on screen for a moment; acting
     * on the first row would race that re-render and could delete an unrelated user on a shared
     * tenant. Waiting for a row that contains this username proves the filtered results have
     * been rendered before anything is clicked.
     */
    public void deleteUser(String siteRootUrl, String username) {
        openUserList(siteRootUrl);
        WaitUtils.type(driver, USERNAME_INPUT, username);
        WaitUtils.click(driver, SEARCH_BUTTON);

        WebElement row = WaitUtils.until(driver, d -> {
            List<WebElement> cards = d.findElements(TABLE_CARD);
            return cards.stream()
                    .filter(card -> card.getText().contains(username))
                    .findFirst()
                    .orElse(null);
        }, Duration.ofSeconds(20));

        // Row actions are [delete, edit]; select the delete icon explicitly rather than by index.
        WebElement deleteButton = row.findElement(By.xpath(".//button[.//i[contains(@class,'bi-trash')]]"));
        WaitUtils.click(driver, deleteButton);

        // Matched by its own label rather than by a wrapping dialog class. "Yes, Delete" only
        // exists on the confirmation modal, so it identifies the button without depending on
        // the modal's container class.
        WaitUtils.click(driver, By.xpath("//button[contains(normalize-space(),'Yes, Delete')]"));

        // The row going stale is the observable proof the delete was applied.
        WaitUtils.untilGone(driver, row);
    }

    /**
     * Selects an employee through the "Type for hints..." autocomplete.
     *
     * <p>Two behaviours of this widget have to be respected. Its search is debounced and only
     * reacts to real key events, so the value is typed rather than assigned. Its dropdown also
     * renders the "No Records Found" placeholder using the same option class as genuine hints,
     * so the hint is matched by name instead of simply taking the first option - otherwise the
     * placeholder gets clicked, the field stays in its "Invalid" state, and the eventual
     * failure surfaces as an unrelated timeout on Save.
     */
    private void fillEmployeeAutocomplete(String employeeFullName) {
        String searchTerm = employeeFullName.split("\\s+")[0];

        WaitUtils.type(driver, EMPLOYEE_AUTOCOMPLETE, searchTerm);

        WebElement hint = WaitUtils.until(driver, d -> d.findElements(AUTOCOMPLETE_OPTION).stream()
                .filter(option -> option.getText().contains(searchTerm))
                .findFirst()
                .orElse(null), Duration.ofSeconds(20));
        WaitUtils.click(driver, hint);

        WebElement employeeInput = driver.findElement(EMPLOYEE_AUTOCOMPLETE);
        if (!WaitUtils.valueOf(employeeInput).contains(searchTerm)) {
            throw new IllegalStateException(
                    "Employee autocomplete did not accept a hint for '" + employeeFullName + "'");
        }
    }

    private void selectDropdownOption(int dropdownIndex, String optionText) {
        List<WebElement> dropdowns = driver.findElements(DROPDOWN);
        WaitUtils.click(driver, dropdowns.get(dropdownIndex));
        WaitUtils.click(driver,
                By.xpath("//div[@role='option']//span[normalize-space()='" + optionText + "']"));
    }

    private String rootOf(String siteRootUrl) {
        return siteRootUrl.endsWith("/") ? siteRootUrl.substring(0, siteRootUrl.length() - 1) : siteRootUrl;
    }
}
