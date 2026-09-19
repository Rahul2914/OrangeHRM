package com.orangehrm.assessment.listeners;

import com.orangehrm.assessment.base.BaseUiApiTest;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class FailureArtifactsListener implements ITestListener {
    @Override
    public void onTestFailure(ITestResult result) {
        Object instance = result.getInstance();
        if (instance instanceof BaseUiApiTest testBase) {
            testBase.captureFailureArtifacts(result.getMethod().getMethodName());
        }
    }

    @Override
    public void onStart(ITestContext context) {
    }

    @Override
    public void onFinish(ITestContext context) {
    }
}