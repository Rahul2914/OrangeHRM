package com.orangehrm.assessment.base;

import com.orangehrm.assessment.config.FrameworkConfig;
import com.orangehrm.assessment.utils.SessionRecorder;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Lifecycle for every UI + API test: browser start-up, failure evidence, and clean shutdown.
 *
 * <p>Driver binaries are resolved by Selenium Manager, which ships with Selenium 4, so no
 * driver is committed to the repository and no driver download step is needed in CI.
 */
public abstract class BaseUiApiTest {
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(45);

    /**
     * Allure reads environment metadata once per results directory, so writing it on every test
     * would be redundant IO and, under parallel classes, a write race on the same file.
     */
    private static final AtomicBoolean ENVIRONMENT_WRITTEN = new AtomicBoolean();

    protected FrameworkConfig config;

    /** The driver used by the tests; decorated so interactions are recorded. */
    protected WebDriver driver;

    /**
     * The undecorated driver. Screenshots are taken through this reference so that capturing
     * evidence does not itself fire listener events and recurse.
     */
    private WebDriver rawDriver;

    private SessionRecorder recorder;
    private String failedTestName;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // TestNG reuses one instance for every method in the class, so this marker has to be
        // cleared per test. Left set, the next test (or a passing retry) would be treated as a
        // failure and its recording would overwrite the real failure video.
        failedTestName = null;

        config = FrameworkConfig.load();
        createArtifactDirectories();
        writeAllureEnvironment();

        rawDriver = createDriver(config.browser());
        rawDriver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
        // A fixed window keeps frame sizes consistent for the encoder and holds the responsive
        // layout in its desktop breakpoint, where the menu selectors apply.
        rawDriver.manage().window().setSize(new Dimension(1600, 900));

        if (config.videoEnabled()) {
            recorder = SessionRecorder.start(rawDriver, Paths.get(config.artifactsDirectory(), "videos"));
            driver = new EventFiringDecorator<>(recorder).decorate(rawDriver);
        } else {
            driver = rawDriver;
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        persistFailureVideo();

        if (rawDriver != null) {
            rawDriver.quit();
            rawDriver = null;
            driver = null;
        }
    }

    /**
     * Called by the failure listener while the browser is still open, so the evidence
     * reflects the state the test actually failed in.
     */
    public void captureFailureArtifacts(String testName) {
        if (rawDriver == null) {
            return;
        }

        failedTestName = testName;

        try {
            if (recorder != null) {
                recorder.captureFrame();
            }

            byte[] screenshot = ((TakesScreenshot) rawDriver).getScreenshotAs(OutputType.BYTES);
            Path screenshotPath =
                    Paths.get(config.artifactsDirectory(), "screenshots", sanitizeFileName(testName) + ".png");
            Files.createDirectories(screenshotPath.getParent());
            Files.write(screenshotPath, screenshot);

            Allure.addAttachment(testName + " screenshot", "image/png", new ByteArrayInputStream(screenshot), ".png");
            Allure.addAttachment(testName + " current-url", rawDriver.getCurrentUrl());
            Allure.addAttachment(testName + " title", rawDriver.getTitle());
            Allure.addAttachment(testName + " html", "text/html", rawDriver.getPageSource(), ".html");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write failure screenshot", exception);
        } catch (RuntimeException browserGone) {
            // The browser can be in an unusable state precisely because the test failed;
            // losing some evidence is preferable to masking the real failure.
        }
    }

    /**
     * Returns the browser session cookies so API calls run as the signed-in user.
     *
     * <p>Sharing the UI session with the API layer is what makes the API stage a genuine
     * verification of what the UI did, rather than a separate, independently authenticated
     * conversation with the backend.
     */
    protected Set<Cookie> sessionCookies() {
        return driver.manage().getCookies();
    }

    /**
     * Keeps the recording of a failed test and discards the rest.
     *
     * <p>A failure cannot be predicted, so recording has to start with every test. Only the
     * failing run is required as evidence, so the other recordings are deleted rather than
     * left behind as unidentifiable files next to the real artefacts.
     */
    private void persistFailureVideo() {
        if (recorder == null) {
            return;
        }

        SessionRecorder finishedRecorder = recorder;
        recorder = null;

        if (failedTestName == null) {
            finishedRecorder.discard();
            return;
        }

        try {
            Path targetPath = Paths.get(config.artifactsDirectory(), "videos",
                    sanitizeFileName(failedTestName) + ".mp4");
            Path savedVideo = finishedRecorder.saveAs(targetPath);
            if (savedVideo == null) {
                return;
            }
            try (InputStream inputStream = Files.newInputStream(savedVideo)) {
                Allure.addAttachment(failedTestName + " video", "video/mp4", inputStream, ".mp4");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist failure video", exception);
        }
    }

    /**
     * Records which environment produced the run so the Allure report is self-describing.
     *
     * <p>Without this the report shows results with no indication of which tenant, browser or
     * environment they came from, which makes an archived report impossible to interpret later.
     * Failure to write it is deliberately not fatal - missing metadata must never fail a run.
     */
    private void writeAllureEnvironment() {
        if (!ENVIRONMENT_WRITTEN.compareAndSet(false, true)) {
            return;
        }

        Properties environment = new Properties();
        environment.setProperty("Environment", config.environment());
        environment.setProperty("UI.Base.URL", config.uiBaseUrl());
        environment.setProperty("API.Base.URL", config.apiBaseUrl());
        environment.setProperty("Browser", config.browser());
        environment.setProperty("Headless", String.valueOf(config.headless()));
        environment.setProperty("Video.Enabled", String.valueOf(config.videoEnabled()));
        environment.setProperty("Java.Version", System.getProperty("java.version", "unknown"));
        environment.setProperty("OS", System.getProperty("os.name", "unknown"));

        Path resultsDirectory =
                Paths.get(System.getProperty("allure.results.directory", "target/allure-results"));

        try {
            Files.createDirectories(resultsDirectory);
            try (var output = Files.newOutputStream(resultsDirectory.resolve("environment.properties"))) {
                environment.store(output, "Captured automatically at test start-up");
            }
        } catch (IOException metadataFailure) {
            Reporter.log("Could not write Allure environment metadata: " + metadataFailure, true);
        }
    }

    private void createArtifactDirectories() {        try {
            Files.createDirectories(Paths.get(config.artifactsDirectory(), "screenshots"));
            Files.createDirectories(Paths.get(config.artifactsDirectory(), "videos"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create artifact directories", exception);
        }
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    /**
     * Builds a browser for the configured target.
     *
     * <p>Visible to subclasses so teardown can open a short-lived session of its own. The
     * per-test driver is quit in {@code @AfterMethod}, so any class-level cleanup that still
     * needs a signed-in session has to create one rather than reuse a closed handle.
     */
    protected WebDriver createDriver(String browserName) {        return switch (browserName.toLowerCase()) {
            case "firefox" -> {
                FirefoxOptions options = new FirefoxOptions();
                if (config.headless()) {
                    options.addArguments("-headless");
                }
                yield new FirefoxDriver(options);
            }
            case "edge" -> {
                EdgeOptions options = new EdgeOptions();
                if (config.headless()) {
                    options.addArguments("--headless=new");
                }
                options.addArguments("--window-size=1600,900");
                yield new EdgeDriver(options);
            }
            default -> {
                ChromeOptions options = new ChromeOptions();
                if (config.headless()) {
                    options.addArguments("--headless=new");
                }
                // Required for the unprivileged containers used by CI runners.
                options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1600,900");
                yield new ChromeDriver(options);
            }
        };
    }
}
