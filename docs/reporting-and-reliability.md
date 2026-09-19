# Reporting And Reliability

## Reporting strategy

The assignment explicitly asks for HTML reports, screenshots and videos on failure, tagging strategy, and environment-based execution. This project addresses those requirements as follows:

- Allure generates the HTML report.
- Surefire produces raw XML results for CI diagnostics.
- Failure screenshots are captured into `artifacts/screenshots` and attached to Allure.
- A `WebDriverListener` records video when `VIDEO_ENABLED=true`. Selenium has no native recorder, so the listener screenshots after each navigation, click, submit and key entry and encodes those frames to MP4 with JCodec. Only failed tests keep their recording; passing runs discard it so the folder stays evidence, not noise. See the "Recording video with Selenium" section of the README for why ffmpeg, Monte and a docker-selenium sidecar were rejected.

## Tagging strategy

The tests are grouped with TestNG tags so the same codebase can support different execution purposes without duplicating suites.

- `smoke`: fast path confidence.
- `regression`: broader business coverage.
- `role-based`: authorization and permissions.
- `ui`: browser tests.
- `api`: backend verification.

This keeps the code simple while still allowing CI parallelization.

## Smart waiting approach

The framework avoids static waits.

- Login waits for the dashboard URL.
- PIM navigation waits for the module URL.
- Employee creation waits for the personal details screen.
- UI assertions use visible state or persisted values instead of timing guesses.

## Retry logic

The retry policy is intentionally narrow:

- only one retry,
- only for clearly transient errors,
- never for product assertions.

That design is important because aggressive retries can make a weak framework look artificially stable.

## Flaky test detection

Recommended way to detect flaky tests in CI:

- track tests that pass only on retry,
- review repeated intermittent signatures,
- correlate failures with environment instability,
- separate locator instability from real application defects.

## Flaky test mitigation

Recommended mitigation approach:

- use unique test data,
- avoid shared state,
- keep page objects small and deterministic,
- validate persisted state through API,
- quarantine only as a temporary and visible measure.

## Failure video behavior

Video recording has to be enabled before the run starts, so the framework records the browser session and retains the artifact only for failed tests.

- passed tests do not keep a named failure video artifact,
- failed tests copy the saved video into `artifacts/videos`,
- failed-test videos are also attached to Allure.