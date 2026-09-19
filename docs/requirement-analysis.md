# Requirement Analysis

## Objective breakdown

This assessment is testing more than tool knowledge. It is checking whether the solution behaves like something a senior engineer would actually hand over to a team. That means the submission has to show architecture, execution discipline, diagnostics, and tradeoff awareness.

## Part 1: Advanced end-to-end automation

### Authentication

Expected solution:

- Externalize credentials by environment.
- Support at least admin and restricted-role users.
- Fail fast on invalid credentials with clear logging.
- Avoid storing secrets in the repository.

Senior-level interpretation:

- Authentication is not just a login page action. It is the entry gate for both UI and API channels.
- If API auth differs from UI auth, the framework should model both explicitly instead of overloading one helper.

### Employee creation

Expected solution:

- Generate unique employee data every run.
- Capture a durable business identifier after creation.
- Assert success in UI immediately after save.
- Verify persistence through API.

Risk to avoid:

- Creating a record and then asserting only on a toast message. That proves almost nothing.

### Role-based validation

Expected solution:

- Use an employee self-service or restricted account.
- Verify either absence of access to PIM features or proper server-side denial.
- Validate both UI restriction and backend restriction when possible.

Senior-level interpretation:

- Role-based validation is a security boundary check. Treat it as an authorization scenario, not just a visual assertion.

### Employee update

Expected solution:

- Update a meaningful field such as nickname, last name, or job detail.
- Confirm that the new value is visible in UI.
- Re-check the same field through API.

### Employee deletion

Expected solution:

- Delete the created employee within the same test flow or cleanup hook.
- Verify the record no longer appears in UI search.
- Verify API returns not found or equivalent expected response.

### API-level verification

Expected solution:

- Use the API as an independent oracle after UI mutations.
- Validate status code, payload shape, and critical business fields.
- Attach request/response details into the report.

Why this matters:

- UI-only validation misses backend persistence defects.
- API-only validation misses real user workflow failures.
- Combined validation is the strongest evidence for this assessment.

## Part 2: Framework design and engineering

### Page Object Model

The page object layer should expose business actions, not low-level selector scripts spread across tests.

Good:

- `loginPage.loginAs(username, password)`
- `pimPage.createEmployee(employeeData)`

Weak:

- Test classes clicking individual selectors directly.

### Clean folder structure

A clean structure should support future growth. The project should be ready for:

- More roles.
- More OrangeHRM modules.
- Contract validation.
- Parallel suites.
- CI sharding.

### Environment-based configuration

Configuration should support at least:

- `qa`, `uat`, and optionally `prod-like` profiles.
- Base UI URL.
- Base API URL.
- Credentials via secrets.
- Execution switches such as browser, headless, video, and retries.

### Reusable utilities

The right utilities are:

- Unique data factory.
- Environment loader.
- Retry analyzer.
- Failure artifact hook.
- API request builder.

The wrong utilities are generic dumping grounds like `CommonUtils`.

## Part 3: CI/CD pipeline integration

The pipeline must prove the framework can run unattended.

Minimum pipeline design:

1. Checkout code.
2. Set up Java.
3. Cache Maven dependencies.
4. Provision the browser (Selenium Manager resolves the matching driver at runtime).
5. Execute the suite.
6. Generate reports.
7. Publish artifacts.

Recommended improvement:

- Split smoke and regression.
- Gate report generation to run even when tests fail.

## Part 4: Test stability and reliability

### Retry logic

Correct use:

- Retry only clear transient failures.
- Keep max retry low, usually `1`.
- Mark retried tests visibly in the report.

Incorrect use:

- Retrying assertions on product bugs.

### Smart waits

Correct use:

- Wait for element state, URL, request completion, or persisted record visibility.

Incorrect use:

- `Thread.sleep` after every action.

### Screenshot capture on failure

Expected solution:

- Screenshot in the failure listener.
- Prefer full-page screenshots.
- Attach them automatically to Allure.

### Flaky test detection and mitigation

Detection model:

- Build retry dashboards from CI history.
- Label flaky-once-passed-on-retry tests separately.
- Correlate with environment incidents.

Mitigation model:

- Fix locator quality.
- Isolate test data.
- Make cleanup resilient.
- Reduce cross-test coupling.

## Part 5: Performance testing (bonus)

This part is marked optional in the assessment. It is **deliberately not implemented**, and it is
recorded here so the omission reads as a decision rather than an oversight.

The reasoning: a credible performance submission means defensible thresholds, a load profile that
matches an actual usage pattern, and results interpreted against them. A handful of scripts with
round-number thresholds invented to pass would be worse than nothing, because it would claim
rigour that was not applied. The effort went into the six mandatory parts instead.

The target would also not have supported it: `opensource-demo.orangehrmlive.com` is a shared
public demo, so generating load against it degrades the service for other users and returns
numbers shaped by their traffic as much as by the test.

## Part 6: Reporting and observability

The report should make failures diagnosable without rerunning immediately.

Evidence set:

- HTML summary.
- Raw test result files.
- Screenshots.
- Videos.
- API payload evidence.
- Tags and environment metadata.

## Submission strategy

The repository should be easy to assess quickly. A reviewer should understand within a few minutes:

- what the framework covers,
- how to run it,
- what design choices were made,
- how evidence is generated,
- what assumptions still need environment confirmation.

That is why the README, CI workflow, and structure matter almost as much as the test code itself.

## Role-based execution constraint

The public demo publishes admin credentials but no restricted account, so there is no ready-made
non-admin login to assert against.

The resolution is provisioning rather than assumption. The test signs in as admin, creates an
employee, grants that employee an ESS user account, signs in as it, asserts the admin-only modules
are absent, and then deletes the account it created. The requirement is therefore executed live,
not merely coded and deferred.

Two details matter for honesty of the result:

- The assertion waits for the side menu to finish rendering first. Asserting on absence against a
  menu that has not drawn yet would pass for the wrong reason and prove nothing about privileges.
- If real restricted credentials are supplied through `ESS_USERNAME` and `ESS_PASSWORD`, they are
  used directly and no provisioning occurs.