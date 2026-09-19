package com.orangehrm.assessment.api;

import com.orangehrm.assessment.config.FrameworkConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openqa.selenium.Cookie;

public class OrangeHrmApiClient {
    private final FrameworkConfig config;

    public OrangeHrmApiClient(FrameworkConfig config) {
        this.config = config;
    }

    public Response getEmployee(String employeeId, Set<Cookie> browserCookies) {
        return RestAssured.given()
                .baseUri(config.apiBaseUrl())
                .contentType(ContentType.JSON)
                .cookies(toCookieMap(browserCookies))
                .filter(new AllureRestAssured())
                .get(config.employeePath() + "/" + normalizeEmployeeId(employeeId) + "/personal-details");
    }

    public Response deleteEmployee(String employeeId, Set<Cookie> browserCookies) {
        return RestAssured.given()
                .baseUri(config.apiBaseUrl())
                .contentType(ContentType.JSON)
                .cookies(toCookieMap(browserCookies))
                .filter(new AllureRestAssured())
                .body(Map.of("ids", List.of(Integer.parseInt(normalizeEmployeeId(employeeId)))))
                .delete(config.employeePath());
    }

    private Map<String, String> toCookieMap(Set<Cookie> browserCookies) {
        Map<String, String> cookies = new HashMap<>();
        for (Cookie cookie : browserCookies) {
            cookies.put(cookie.getName(), cookie.getValue());
        }
        return cookies;
    }

    private String normalizeEmployeeId(String employeeId) {
        return employeeId.replaceFirst("^0+(?!$)", "");
    }
}