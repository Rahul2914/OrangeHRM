package com.orangehrm.assessment.tests;

import com.orangehrm.assessment.api.OrangeHrmApiClient;
import com.orangehrm.assessment.base.BaseUiApiTest;
import com.orangehrm.assessment.listeners.FailureArtifactsListener;
import com.orangehrm.assessment.listeners.RetryAnalyzer;
import com.orangehrm.assessment.model.EmployeeData;
import com.orangehrm.assessment.pages.DashboardPage;
import com.orangehrm.assessment.pages.LoginPage;
import com.orangehrm.assessment.pages.PimPage;
import io.restassured.response.Response;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Covers the employee lifecycle required by Part 1 of the assessment as discrete,
 * individually reportable stages: authentication, creation, update, API verification
 * and deletion.
 *
 * <p>Stages are ordered with {@code dependsOnMethods} so an upstream failure skips the
 * remaining stages instead of producing cascading false failures. The class runs single
 * threaded because the stages share the employee record created by the first stage.
 */
@Listeners(FailureArtifactsListener.class)
@Test(singleThreaded = true)
public class EmployeeLifecycleE2ETest extends BaseUiApiTest {

    private static final EmployeeData EMPLOYEE = EmployeeData.unique();

    private static String employeeId;

    @Test(description = "Part 1 - Authentication: admin credentials reach the dashboard",
            groups = {"smoke", "regression", "ui", "auth"},
            retryAnalyzer = RetryAnalyzer.class)
    public void shouldAuthenticateAdmin() {
        requireLiveExecution();
        loginAsAdmin();
        Assert.assertTrue(new DashboardPage(driver).isLoaded(), "Dashboard must load after a successful admin login");
    }

    @Test(description = "Part 1 - Employee creation: a new PIM record is persisted",
            groups = {"smoke", "regression", "ui"},
            dependsOnMethods = "shouldAuthenticateAdmin",
            retryAnalyzer = RetryAnalyzer.class)
    public void shouldCreateEmployee() {
        requireLiveExecution();
        loginAsAdmin();

        employeeId = new PimPage(driver).createEmployee(EMPLOYEE);
        Assert.assertNotNull(employeeId, "Created employee must expose an empNumber");
        Assert.assertFalse(employeeId.isBlank(), "Created employee empNumber must not be blank");
    }

    @Test(description = "Part 1 - Employee update: a Personal Details change survives a save and reload",
            groups = {"smoke", "regression", "ui"},
            dependsOnMethods = "shouldCreateEmployee",
            retryAnalyzer = RetryAnalyzer.class)
    public void shouldUpdateEmployee() {
        requireLiveExecution();
        loginAsAdmin();

        PimPage pimPage = new PimPage(driver);
        pimPage.openEmployeeRecord(config.apiBaseUrl(), employeeId);
        pimPage.updateDriversLicenseNumber(EMPLOYEE.customFieldValue());
        Assert.assertEquals(pimPage.readPersistedDriversLicenseNumber(EMPLOYEE.customFieldValue()),
                EMPLOYEE.customFieldValue(),
                "Updated Personal Details value must persist after a reload");
    }

    @Test(description = "Part 1 - API verification: the API reflects the UI-created employee",
            groups = {"smoke", "regression", "api"},
            dependsOnMethods = "shouldUpdateEmployee",
            retryAnalyzer = RetryAnalyzer.class)
    public void shouldVerifyEmployeeThroughApi() {
        requireLiveExecution();
        loginAsAdmin();

        Response response = new OrangeHrmApiClient(config).getEmployee(employeeId, sessionCookies());
        Assert.assertTrue(response.statusCode() < 300, "Employee fetch must succeed after creation");
        Assert.assertEquals(response.jsonPath().getString("data.firstName"), EMPLOYEE.firstName(),
                "API should reflect the UI-created employee");
    }

    @Test(description = "Part 1 - Employee deletion: the record is removed and no longer retrievable",
            groups = {"smoke", "regression", "api"},
            dependsOnMethods = "shouldVerifyEmployeeThroughApi",
            retryAnalyzer = RetryAnalyzer.class)
    public void shouldDeleteEmployee() {
        requireLiveExecution();
        loginAsAdmin();

        OrangeHrmApiClient apiClient = new OrangeHrmApiClient(config);
        Response deleteResponse = apiClient.deleteEmployee(employeeId, sessionCookies());
        Assert.assertTrue(deleteResponse.statusCode() < 300, "Employee delete must succeed");

        Response deletedEmployeeResponse = apiClient.getEmployee(employeeId, sessionCookies());
        Assert.assertTrue(deletedEmployeeResponse.statusCode() >= 400,
                "Deleted employee should no longer be retrievable through the API");

        // Tells teardown the record is already gone, so the safety net below stays out of the way.
        employeeId = null;
    }

    /**
     * Removes the employee when the deletion stage never ran.
     *
     * <p>The deletion stage is chained behind the earlier stages, so any upstream failure skips
     * it and would leave the record on a publicly shared tenant permanently. Owning that case in
     * teardown is what makes the cleanup resilient rather than merely present.
     *
     * <p>It opens its own short-lived session because the per-test driver is already quit by the
     * time class teardown runs, and it never throws: cleanup must not convert a reported test
     * failure into a different, misleading one.
     */
    @AfterClass(alwaysRun = true)
    public void removeEmployeeIfDeletionStageWasSkipped() {
        if (employeeId == null || config == null || !config.executeLive()) {
            return;
        }

        WebDriver cleanupDriver = null;
        try {
            cleanupDriver = createDriver(config.browser());
            new LoginPage(cleanupDriver)
                    .open(config.uiBaseUrl())
                    .loginAs(config.adminUsername(), config.adminPassword());

            new OrangeHrmApiClient(config).deleteEmployee(employeeId, cleanupDriver.manage().getCookies());
            Reporter.log("Teardown removed employee " + employeeId
                    + " that the deletion stage never reached.", true);
        } catch (RuntimeException cleanupFailure) {
            Reporter.log("Could not remove employee " + employeeId
                    + "; it may need manual removal. Cause: " + cleanupFailure, true);
        } finally {
            if (cleanupDriver != null) {
                cleanupDriver.quit();
            }
            employeeId = null;
        }
    }

    private void requireLiveExecution() {
        if (!config.executeLive()) {
            throw new SkipException("Set EXECUTE_LIVE=true after confirming tenant URL, selectors, credentials, and API paths.");
        }
    }

    private void loginAsAdmin() {
        new LoginPage(driver)
                .open(config.uiBaseUrl())
                .loginAs(config.adminUsername(), config.adminPassword());
    }
}