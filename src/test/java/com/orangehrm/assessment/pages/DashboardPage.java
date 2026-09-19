package com.orangehrm.assessment.pages;

import com.orangehrm.assessment.utils.WaitUtils;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class DashboardPage {
    private static final By MENU_ITEM_NAME = By.cssSelector(".oxd-main-menu-item--name");
    private static final By USER_DROPDOWN = By.cssSelector(".oxd-userdropdown-tab");
    private static final By LOGOUT_LINK = By.xpath("//a[normalize-space()='Logout']");

    private final WebDriver driver;

    public DashboardPage(WebDriver driver) {
        this.driver = driver;
    }

    public boolean isLoaded() {
        WaitUtils.untilUrlContains(driver, "/dashboard");
        return driver.getCurrentUrl().contains("/dashboard");
    }

    /**
     * Reports whether a top-level module is available to the signed-in user.
     *
     * <p>The side menu is rendered after the dashboard route resolves, so this waits for the
     * menu to exist before answering. Without that wait a "module is absent" assertion would
     * pass simply because the menu had not been drawn yet - the test would look green while
     * proving nothing about the user's privileges.
     */
    public boolean hasMenuItem(String menuName) {
        awaitMenuRendered();
        return !driver.findElements(
                By.xpath("//span[contains(@class,'oxd-main-menu-item--name')][normalize-space()='" + menuName + "']"))
                .isEmpty();
    }

    private void awaitMenuRendered() {
        WaitUtils.untilTrue(driver, d -> !d.findElements(MENU_ITEM_NAME).isEmpty(), Duration.ofSeconds(20));
    }

    /**
     * Signs the current user out so another role can be exercised in the same browser session.
     */
    public void logout() {
        WaitUtils.click(driver, USER_DROPDOWN);
        WaitUtils.click(driver, LOGOUT_LINK);
        WaitUtils.untilUrlContains(driver, "/auth/login");
    }
}
