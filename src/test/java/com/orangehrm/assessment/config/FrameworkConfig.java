package com.orangehrm.assessment.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class FrameworkConfig {
    private final Properties properties;
    private final String environment;

    private FrameworkConfig(Properties properties, String environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public static FrameworkConfig load() {
        String env = System.getProperty("test.env", System.getenv().getOrDefault("TEST_ENV", "qa"));
        Properties properties = new Properties();
        String resourceName = String.format("config/application-%s.properties", env);

        try (InputStream inputStream = FrameworkConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load config resource: " + resourceName, exception);
        }

        FrameworkConfig config = new FrameworkConfig(properties, env);
        config.applyJvmSslSettings();
        return config;
    }

    /** The resolved environment name, such as {@code qa} or {@code uat}. */
    public String environment() {
        return environment;
    }

    public String uiBaseUrl() {
        return required("ui.base.url", "UI_BASE_URL");
    }

    public String apiBaseUrl() {
        return required("api.base.url", "API_BASE_URL");
    }

    public String adminUsername() {
        return required("admin.username", "ADMIN_USERNAME");
    }

    public String adminPassword() {
        return required("admin.password", "ADMIN_PASSWORD");
    }

    public String essUsername() {
        return required("ess.username", "ESS_USERNAME");
    }

    public String essPassword() {
        return required("ess.password", "ESS_PASSWORD");
    }

    /**
     * The public OrangeHRM demo does not expose a restricted (ESS) account, so the
     * role-based scenario can only run against a tenant that provides one. Callers use
     * this to skip honestly instead of failing on missing configuration.
     */
    public boolean hasEssCredentials() {
        String username = value("ess.username", "ESS_USERNAME", null);
        String password = value("ess.password", "ESS_PASSWORD", null);
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    /**
     * No separate API credentials exist. The API layer replays the cookies of the browser
     * session that is already authenticated, which is what the application itself does, so
     * a second credential pair would only be a copy that could silently drift out of sync.
     */
    public String browser() {
        return value("browser", "BROWSER", "chrome");
    }

    public boolean headless() {
        return Boolean.parseBoolean(value("headless", "HEADLESS", "true"));
    }

    public boolean executeLive() {
        return Boolean.parseBoolean(value("execute.live", "EXECUTE_LIVE", "false"));
    }

    public boolean videoEnabled() {
        return Boolean.parseBoolean(value("video.enabled", "VIDEO_ENABLED", "false"));
    }

    public String artifactsDirectory() {
        return value("artifacts.dir", "ARTIFACTS_DIR", "artifacts");
    }

    public String authPath() {
        return value("api.auth.path", "API_AUTH_PATH", "/auth/login");
    }

    public String employeePath() {
        return value("api.employee.path", "API_EMPLOYEE_PATH", "/api/v2/pim/employees");
    }

    public String sslTrustStoreType() {
        String defaultValue = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "Windows-ROOT"
                : "";
        return value("ssl.truststore.type", "SSL_TRUSTSTORE_TYPE", defaultValue);
    }

    private String required(String propertyKey, String environmentKey) {
        String value = value(propertyKey, environmentKey, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + propertyKey + " / " + environmentKey);
        }
        return value;
    }

    private String value(String propertyKey, String environmentKey, String defaultValue) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        return defaultValue;
    }

    private void applyJvmSslSettings() {
        String trustStoreType = sslTrustStoreType();
        if (trustStoreType != null && !trustStoreType.isBlank()) {
            System.setProperty("javax.net.ssl.trustStoreType", trustStoreType);
        }
    }

    @Override
    public String toString() {
        return "FrameworkConfig{" +
                "uiBaseUrl='" + Objects.toString(properties.getProperty("ui.base.url"), "<env>") + '\'' +
                ", apiBaseUrl='" + Objects.toString(properties.getProperty("api.base.url"), "<env>") + '\'' +
                '}';
    }
}