package com.orangehrm.assessment.tests;

import com.orangehrm.assessment.api.OrangeHrmApiClient;
import com.orangehrm.assessment.base.BaseUiApiTest;
import com.orangehrm.assessment.listeners.FailureArtifactsListener;
import com.orangehrm.assessment.listeners.RetryAnalyzer;
import com.orangehrm.assessment.model.EmployeeData;
import com.orangehrm.assessment.pages.AdminUserPage;
import com.orangehrm.assessment.pages.DashboardPage;
import com.orangehrm.assessment.pages.LoginPage;
import com.orangehrm.assessment.pages.PimPage;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Part 1 - Role-based validation.
 *
 * <p>The public OrangeHRM demo publishes only the admin account, so there is no ready-made
 * restricted login to assert against. Rather than skip the requirement, this test provisions
 * what it needs: as admin it creates an employee, grants that employee an ESS (restricted)
 * user account, then signs in as that account and asserts the privilege boundary. Both the
 * provisioned user and the employee record behind it are removed afterwards so the shared demo
 * tenant is left as it was found.
 *
 * <p>If {@code ESS_USERNAME}/{@code ESS_PASSWORD} are supplied for a real tenant, those
 * credentials are used directly and no provisioning takes place.
 */
@Listeners(FailureArtifactsListener.class)
public class RoleBasedAccessTest extends BaseUiApiTest {

    private static final String[] ADMIN_ONLY_MENUS = {"Admin", "PIM"};

    @Test(description = "Part 1 - Role-based validation: an ESS user cannot reach admin-only modules",
            groups = {"role-based", "regression", "ui"},
            retryAnalyzer = RetryAnalyzer.class)
    public void restrictedUserShouldNotSeeAdminOnlyModules() {
        if (!config.executeLive()) {
            throw new SkipException("Set EXECUTE_LIVE=true to run the role-based validation.");
        }

        if (config.hasEssCredentials()) {
            assertRestrictedAccess(config.essUsername(), config.essPassword());
            return;
        }

        String siteRoot = config.apiBaseUrl();
        EmployeeData employee = EmployeeData.unique();
        AdminUserPage adminUserPage = new AdminUserPage(driver);
        String provisionedEmployeeId = null;

        loginAsAdmin();

        // Provisioning is inside the try so that a failure part-way through still reaches
        // cleanup. Creating the records outside it would leave them stranded whenever the
        // user-creation step was the thing that broke.
        try {
            provisionedEmployeeId = new PimPage(driver).createEmployee(employee);

            adminUserPage.openAddUser(siteRoot);
            adminUserPage.createEssUser(employee.fullName(), employee.username(), employee.password());

            new DashboardPage(driver).logout();
            assertRestrictedAccess(employee.username(), employee.password());
        } finally {
            // Tearing down the provisioned data is housekeeping for the shared demo tenant, not
            // part of the assertion. A throw here would replace the real verdict with an
            // unrelated failure, because Java discards the original exception when finally
            // throws, so cleanup problems are reported without changing the test outcome.
            cleanUpProvisionedData(adminUserPage, siteRoot, employee.username(), provisionedEmployeeId);
        }
    }

    private void cleanUpProvisionedData(AdminUserPage adminUserPage, String siteRoot,
                                        String username, String employeeId) {
        try {
            logoutQuietly();
            loginAsAdmin();
        } catch (RuntimeException signInFailure) {
            Reporter.log("Could not sign back in as admin to clean up '" + username
                    + "'; manual removal may be needed. Cause: " + signInFailure, true);
            return;
        }

        // The user account and the employee record are separate objects, and each is removed
        // independently: deleting the user first would otherwise mean a failure there left the
        // employee behind permanently on a tenant that is shared with other people.
        removeQuietly("ESS user '" + username + "'",
                () -> adminUserPage.deleteUser(siteRoot, username));

        if (employeeId != null) {
            removeQuietly("employee " + employeeId,
                    () -> new OrangeHrmApiClient(config).deleteEmployee(employeeId, sessionCookies()));
        }
    }

    private void removeQuietly(String description, Runnable removal) {
        try {
            removal.run();
        } catch (RuntimeException cleanupFailure) {
            Reporter.log("Could not remove " + description
                    + "; it may need manual removal. Cause: " + cleanupFailure, true);
        }
    }

    private void logoutQuietly() {
        try {
            new DashboardPage(driver).logout();
        } catch (RuntimeException alreadySignedOut) {
            // The session may already be gone, which is exactly the state logout was aiming for.
        }
    }

    private void assertRestrictedAccess(String username, String password) {
        new LoginPage(driver).open(config.uiBaseUrl()).loginAs(username, password);

        DashboardPage dashboardPage = new DashboardPage(driver);
        Assert.assertTrue(dashboardPage.isLoaded(), "Restricted user must still reach the dashboard after login");

        for (String menu : ADMIN_ONLY_MENUS) {
            Assert.assertFalse(dashboardPage.hasMenuItem(menu),
                    "Restricted user must not see the admin-only " + menu + " module");
        }
    }

    private void loginAsAdmin() {
        new LoginPage(driver)
                .open(config.uiBaseUrl())
                .loginAs(config.adminUsername(), config.adminPassword());
    }
}