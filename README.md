# OrangeHRM Senior QA Assessment Solution

This folder is a dedicated solution workspace for the OrangeHRM technical assessment. It is intentionally separate from the existing API-only project so the submission can be versioned, reviewed, and executed as an independent deliverable.

## What this assessment is asking for

This is not an API-only assignment. It is a combined UI, API, CI/CD, reliability, and reporting exercise built around the OrangeHRM employee lifecycle on <https://opensource-demo.orangehrmlive.com/>.

The required solution areas, as stated in the assessment document, are:

- **Part 1 - Advanced end-to-end automation:** authentication, employee creation, role-based validation, employee update, API-level verification, employee deletion.
- **Part 2 - Framework design:** Page Object Model, clean and scalable folder structure, environment-based configuration, reusable utilities.
- **Part 3 - CI/CD pipeline:** install dependencies, execute tests, generate reports, publish artifacts, parallelization.
- **Part 4 - Test stability:** retry logic, smart waiting mechanisms, screenshot capture on failures, plus a written flaky-test detection and mitigation strategy.
- **Part 5 - Performance testing (bonus):** K6 login and employee-creation tests. **Deliberately descoped for this submission** - see [Descoped bonus](#descoped-bonus).
- **Part 6 - Reporting and observability:** HTML test reports, screenshots and videos on failure, tagging strategy, environment-based test execution.

## Recommended stack

- Java 17 for enterprise-friendly execution and consistency with the workspace.
- Selenium WebDriver 4 for UI automation, with driver binaries resolved at runtime by Selenium Manager so none are committed or downloaded in CI.
- REST Assured for API verification.
- TestNG for suites, groups, retries, and parallel execution.
- Allure for HTML reporting and attachments.
- JCodec for encoding failure videos in pure Java - see [Recording video with Selenium](#recording-video-with-selenium).
- GitHub Actions for CI and artifact publishing.

## Requirement coverage

| Assignment requirement | Implemented in this project |
|---|---|
| Authentication | `EmployeeLifecycleE2ETest.shouldAuthenticateAdmin` via `LoginPage` + `DashboardPage.isLoaded` |
| Employee creation | `EmployeeLifecycleE2ETest.shouldCreateEmployee` + `PimPage.createEmployee` |
| Role-based validation | `RoleBasedAccessTest.restrictedUserShouldNotSeeAdminOnlyModules` |
| Employee update | `EmployeeLifecycleE2ETest.shouldUpdateEmployee` + `PimPage.updateDriversLicenseNumber`, verified by `PimPage.readPersistedDriversLicenseNumber` after a reload |
| API-level verification | `EmployeeLifecycleE2ETest.shouldVerifyEmployeeThroughApi` + `OrangeHrmApiClient.getEmployee` |
| Employee deletion | `EmployeeLifecycleE2ETest.shouldDeleteEmployee` + `OrangeHrmApiClient.deleteEmployee`, with a post-delete negative API check |
| POM | `pages/` package |
| Clean structure | split by `pages`, `api`, `config`, `listeners`, `tests`, `model` |
| Environment-based configuration | `FrameworkConfig` + `application-qa.properties` + `application-uat.properties` |
| Reusable utilities | `WaitUtils` interaction/waiting layer, `SessionRecorder`, config loader, retry analyzer, failure artifacts, unique employee data factory |
| CI/CD pipeline | `.github/workflows/ci.yml` |
| Install dependencies | GitHub Actions workflow |
| Execute automated tests | GitHub Actions workflow + `run-assessment.ps1` |
| Generate reports | Allure via Maven + CI artifact upload |
| Publish artifacts | Allure results, HTML report, screenshots, videos, Surefire results |
| Parallelization | class-level parallelism (`parallel="classes"`, `thread-count="2"`) in `regression.xml`, plus CI matrix execution across the smoke, role-based and regression suites |
| Retry logic | `RetryAnalyzer` |
| Smart waiting mechanisms | `WaitUtils` - condition-based waits for hydration, clickability, value persistence and row rendering; no fixed sleeps |
| Screenshot capture on failures | `FailureArtifactsListener` + `BaseUiApiTest.captureFailureArtifacts` |
| Flaky test detection strategy | [Flaky tests](#flaky-tests) below, plus `docs/reporting-and-reliability.md` |
| Flaky test mitigation strategy | [Flaky tests](#flaky-tests) below, plus `docs/reporting-and-reliability.md` |
| HTML test reports | Allure HTML report |
| Screenshots and videos on failure | artifact capture in test base and CI |
| Tagging strategy | TestNG groups + dedicated suite XML files |
| Environment-based test execution | `TEST_ENV`, secret-driven URLs and credentials, suite selection |
| Test reports and build artifacts in the repo | snapshotted into `report/` by `run-assessment.ps1`; `report/` is deliberately excluded from `.gitignore` |
| Performance testing (bonus) | descoped - see [Descoped bonus](#descoped-bonus) |
| README with setup, execution, design decisions | this README |

## Project structure

```text
orangehrm-qa-assessment/
  .github/workflows/ci.yml
  docs/
    requirement-analysis.md
    reporting-and-reliability.md
    submission-checklist.md
  src/test/java/com/orangehrm/assessment/
    api/
    base/
    config/
    listeners/
    model/
    pages/
    tests/
  src/test/resources/
    config/
    testng/
  run-assessment.ps1
```

## Test design

The framework is intentionally simple and readable.

- `EmployeeLifecycleE2ETest` covers the main business journey as five ordered stages - authenticate, create, update, verify through the API, delete. Each stage is its own `@Test` so the report shows per-stage pass/fail instead of one opaque result. Stages are chained with `dependsOnMethods`, so an upstream failure skips the rest rather than producing cascading false failures, and the class is `@Test(singleThreaded = true)` because the stages share the record they create.
- `RoleBasedAccessTest` covers restricted-role validation when a non-admin account is available.
- `LoginPage`, `PimPage`, `DashboardPage`, and `AdminUserPage` keep selectors out of tests.
- `WaitUtils` holds every waiting and interaction rule, so the page objects stay readable and a waiting rule is fixed in one place.
- `OrangeHrmApiClient` is the single API gateway used by the tests.

The key test design rule is simple: UI performs the business action, API confirms the backend state.

## Tagging strategy

The project uses TestNG groups instead of hard-coded environment-specific test lists.

- `smoke`: quick confidence checks.
- `regression`: broader lifecycle coverage.
- `role-based`: authorization checks.
- `auth`: authentication-only coverage.
- `ui`: browser-based coverage.
- `api`: API validation coverage.

Suite files:

- `src/test/resources/testng/smoke.xml`
- `src/test/resources/testng/role-based.xml`
- `src/test/resources/testng/regression.xml`

## Reporting and observability

Included outputs:

- Allure HTML report.
- Allure raw results.
- Full-page screenshots on failure.
- Browser videos saved and attached on failure when `VIDEO_ENABLED=true`.
- Surefire XML output.

See `docs/reporting-and-reliability.md` for the flaky-test and evidence strategy.

## Recording video with Selenium

Selenium has no recording capability of its own, and the requirement asks for video on failure. The usual workarounds are poor fits: ffmpeg and the Monte screen recorder capture the physical desktop, which does not exist on a headless CI agent, and the docker-selenium video sidecar would force the whole suite to run through a container grid. Both make a graded requirement depend on the execution environment.

`SessionRecorder` instead captures frames through the WebDriver session itself and encodes them to MP4 with JCodec, in pure Java:

- Because the image comes from the browser rather than the desktop, it works identically headless and headed, locally and in CI.
- Frames are captured by a `WebDriverListener` after each interaction, on the test thread. WebDriver sessions are not thread-safe, so a background capture thread would risk corrupting the session under test. The result is an action-by-action replay, which is usually more useful for diagnosis than a wall-clock one.
- Frames are encoded straight to a temporary file, so a long suite cannot exhaust the heap.
- Capture never throws. A recording problem must not change the outcome of the test being recorded.

Recording starts for every test, because a failure cannot be predicted. Only the failing run is kept; the rest are deleted so the artifacts folder contains only evidence worth looking at.

## Flaky tests

### Detection

- **Retry outcomes are the primary signal.** `RetryAnalyzer` retries only once, so a test that passes on the second attempt is recorded as flaky rather than quietly green. Allure shows these as retried, which makes them visible in the report instead of hidden in a pass count.
- **CI history across the matrix.** The same suites run on every push; a test that fails intermittently without a corresponding code change is flaky, not broken.
- **Evidence on every failure.** Screenshot, video, page HTML and URL are attached at the moment of failure, so an intermittent failure can be diagnosed from the run that produced it rather than by trying to reproduce it.

### Mitigation

The strategy is to remove causes rather than to retry until green.

- **Waiting is condition-based, never time-based.** There are no fixed sleeps. `WaitUtils` waits for the state the test depends on.
- **Readiness is distinguished from visibility.** This application renders forms before the data arrives, so a field can be visible and still empty. Writing during that window is silently discarded and saves an empty value. `untilFieldPopulated` waits for hydration before any read or write.
- **Writes are verified, not assumed.** `WaitUtils.type` confirms the value the field actually ends up holding, because some fields here are repopulated by the framework after being cleared.
- **Saves wait for the application's own confirmation** before the page is reloaded, so an in-flight request cannot be cancelled by the next step.
- **Rows are matched by content, never by position.** Acting on "the first row" races the re-render after a search - which on a shared tenant risks acting on someone else's record, not merely failing.
- **Test data is unique per run.** `EmployeeData.unique()` generates its own Employee Id, so concurrent users of the shared demo tenant cannot collide.
- **Retries are narrow.** Assertion failures are never retried: an assertion failure is a verdict about the application, and retrying it converts a reproducible bug into an intermittent one. Only timing and infrastructure failures are retried, once.
- **Cleanup never changes a verdict.** Housekeeping runs best-effort and reports problems without overwriting the test result.

## CI/CD design

The GitHub Actions pipeline demonstrates the required behavior:

- dependency installation,
- Chrome provisioning (the matching driver is resolved at runtime by Selenium Manager),
- parallel suite execution,
- Allure report generation,
- artifact publication.

## Environment-based execution

The framework reads configuration in this order:

1. JVM system properties.
2. Environment variables.
3. `src/test/resources/config/application-<env>.properties`.

Every value already has a working default in `application-qa.properties`, so the suite runs on a
fresh clone with no environment setup at all. These override it when pointing at another tenant:

| Variable | Needed? |
|---|---|
| `UI_BASE_URL` | Optional - defaults to the public demo |
| `API_BASE_URL` | Optional - defaults to the public demo |
| `ADMIN_USERNAME` | Optional - defaults to the demo's published `Admin` |
| `ADMIN_PASSWORD` | Optional - defaults to the demo's published password |
| `ESS_USERNAME` | Optional - if blank, the role-based test provisions its own restricted user |
| `ESS_PASSWORD` | Optional - as above |

`ESS_USERNAME` / `ESS_PASSWORD` are genuinely optional rather than nominally so: when they are
absent the role-based test creates the restricted account it needs and removes it afterwards, so
the requirement is still executed rather than skipped.

There is deliberately no separate `API_USERNAME` / `API_PASSWORD` pair. The API layer replays
the cookies of the browser session that is already signed in, which is exactly how the
application authenticates its own requests; a second credential pair would only be a copy that
could silently drift out of sync with the UI one.

Feature flags:

- `TEST_ENV`
- `EXECUTE_LIVE`
- `VIDEO_ENABLED`
- `HEADLESS`
- `BROWSER`
- `SSL_TRUSTSTORE_TYPE`

## Verified live execution

All three suites have been executed live against <https://opensource-demo.orangehrmlive.com/>
on Chrome 153 with Java 17, not just compiled:

| Suite | Result |
|---|---|
| `smoke` | 5 passed, 0 failed |
| `role-based` | 1 passed, 0 failed |
| `regression` | 6 passed, 0 failed |

Two environment-dependent caveats remain, and they are inputs rather than framework gaps:

- Employee delete behaviour and the exact selectors can differ between OrangeHRM versions, so a
  different tenant version may need locator review.
- The demo tenant is shared and publicly writable, so records created by other people can be
  present. The suite never asserts on data it did not create itself.

## HTTPS handling

The API verification uses Java HTTPS, so certificate trust is handled separately from the browser.

- On Windows, the framework defaults to `Windows-ROOT` for Java TLS trust.
- On other operating systems, leave the trust store type unset unless your environment requires a specific value.
- This keeps certificate validation enabled and avoids insecure relaxed-SSL workarounds.

## Local execution

### Using the project Maven wrapper

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\mvnw.cmd clean test -Dsuite=src/test/resources/testng/smoke.xml
.\mvnw.cmd allure:report
```

### Using Maven directly

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
mvn clean test -Dsuite=src/test/resources/testng/regression.xml
```

### Using the helper script

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\run-assessment.ps1 -Suite regression -GenerateReport -OpenReport
```

### Open the generated reports correctly

Do not open the Allure `index.html` file directly from the filesystem. The Allure UI loads its data with browser-side requests, so it should be served over HTTP.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\open-report.ps1
```

This starts a local server for the Allure report at `http://127.0.0.1:8080` and opens it in the browser.
The helper uses the stable `report/allure` snapshot when available, so later `clean` runs do not break the already-generated viewer.

For the static TestNG summary report:

```powershell
.\open-report.ps1 -Report testng
```

The TestNG summary is also copied into `report/testng/index.html` after a test run.

### Browser and driver

There is no driver installation step. Selenium Manager, which ships inside Selenium 4, detects
the locally installed browser and downloads the matching driver on first use, so no driver binary
is committed to the repository and no version pin can go stale against a self-updating browser.

Only the browser itself has to be present. Set `BROWSER` to `chrome` (default), `firefox` or
`edge`.

## Submission contents expected by the assessor

- Source code.
- CI configuration.
- Test reports.
- Build artifacts.
- README with setup instructions, execution steps, and key design decisions.

This project ships all of those items. The `report/` folder is tracked in Git so the evidence
travels with the repository rather than having to be regenerated:

| Path | What it is |
|---|---|
| `report/allure/index.html` | Allure report from the passing live `regression` run (6/6). Serve it with `.\open-report.ps1 -Report allure`; opening the file directly shows an empty report because browsers block its local data fetches. |
| `report/testng/index.html` | TestNG summary from the same run. Opens directly. |
| `report/artifacts/` | Failure evidence - see below. |

### About `report/artifacts/`

Screenshots and videos only exist when something fails, so a fully green submission would ship an
empty folder and leave Part 4 and Part 6 unproven. The evidence there was therefore captured from
a **deliberate negative run**: the suite was executed once with a wrong `ADMIN_PASSWORD` and
nothing else changed - no test code, no assertions, no listener behaviour.

- `screenshots/shouldAuthenticateAdmin.png` - the login page with OrangeHRM's own
  "Invalid credentials" banner, captured automatically at the moment of failure.
- `videos/shouldAuthenticateAdmin.mp4` - the full recording of that failing test.
- `failing-run-testng-report.html` - the report for that run: 1 failed, 4 skipped, because the
  downstream tests depend on authentication and TestNG correctly refuses to run them.

To be explicit: **this is not a product defect and not a flaky test.** It is proof that the
capture-on-failure path works end to end. Reproduce it with:

```powershell
$env:ADMIN_PASSWORD = 'deliberately-wrong-password'
.\mvnw.cmd test "-Dsuite=src/test/resources/testng/smoke.xml"
```

## Descoped bonus

Part 5 of the assessment is an optional K6 performance bonus covering the login API and the employee-creation API with threshold definitions and performance reporting.

It has been **deliberately excluded** from this submission. No K6 scripts, thresholds, or performance CI stage are present, and none of the coverage claims above imply otherwise. The six mandatory parts are implemented in full; the bonus was traded off against depth on framework design, reliability and reporting.

## Role-based note

The public OrangeHRM demo publishes the admin credentials on its login page but does not
publish a restricted non-admin account, so there is no ready-made ESS login to assert against.

Rather than skip the requirement or invent permissions, `RoleBasedAccessTest` provisions what it
needs: signed in as admin it creates an employee, grants that employee an ESS user account, logs
out, signs in as that account, and asserts that the `Admin` and `PIM` modules are absent. The
provisioned user is then deleted so the shared tenant is left as it was found.

This means the requirement is genuinely executed rather than described. If `ESS_USERNAME` and
`ESS_PASSWORD` are supplied for a real tenant, those credentials are used directly and no
provisioning happens.

One deliberate detail: the absent-module assertion first waits for the menu to finish rendering.
Without that wait it would pass simply because the menu had not drawn yet, which would prove
nothing about privileges - the most dangerous kind of passing test.