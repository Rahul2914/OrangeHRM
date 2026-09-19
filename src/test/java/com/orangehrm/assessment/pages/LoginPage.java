package com.orangehrm.assessment.pages;

import com.orangehrm.assessment.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage {
    private static final By USERNAME = By.name("username");
    private static final By PASSWORD = By.name("password");
    private static final By SUBMIT = By.cssSelector("button[type='submit']");
    private static final By ERROR_BANNER = By.cssSelector(".oxd-alert-content-text");

    private final WebDriver driver;

    public LoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public LoginPage open(String baseUrl) {
        driver.get(baseUrl);
        WaitUtils.visible(driver, USERNAME);
        return this;
    }

    public void loginAs(String username, String password) {
        WaitUtils.type(driver, USERNAME, username);
        WaitUtils.type(driver, PASSWORD, password);
        WaitUtils.click(driver, SUBMIT);
        WaitUtils.untilUrlContains(driver, "/dashboard");
    }

    public boolean isLoginFormVisible() {
        return WaitUtils.isPresent(driver, USERNAME);
    }

    public boolean isErrorBannerVisible() {
        return WaitUtils.isPresent(driver, ERROR_BANNER);
    }
}
