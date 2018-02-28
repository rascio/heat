/**
 * Copyright (C) 2015-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.heat.core.testng;

import com.hotels.heat.core.handlers.PlaceholderHandler;
import com.hotels.heat.core.handlers.TestSuiteHandler;
import com.hotels.heat.core.runner.TestBaseRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.Factory;
import org.testng.annotations.Listeners;
import org.testng.internal.BaseTestMethod;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.collect;


/**
 * Created on 20/02/18.
 *
 * @author mrascioni
 */
@Listeners(SingleModeFactory.HeatTestNameListener.class)
public class SingleModeFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeFactory.class);

    @Factory
    public SingleModeTest[] create(ITestContext context) {

        final String jsonFile = context.getCurrentXmlTest().getParameter(TestBaseRunner.INPUT_JSON_PATH);
        final String envPropFilePath = context.getCurrentXmlTest().getParameter(TestBaseRunner.ENV_PROP_FILE_PATH);

        LOGGER.debug("Creating tests for suite [{}] with json [{}] and props [{}]", context.getName(), jsonFile, envPropFilePath);

//        final TestSuiteHandler testSuiteHandler = new TestSuiteHandler();
        final TestSuiteHandler testSuiteHandler = TestSuiteHandler.getInstance();
        testSuiteHandler.setPropertyFilePath(envPropFilePath);
        testSuiteHandler.populateEnvironmentHandler();
        testSuiteHandler.populateTestCaseUtils();
        testSuiteHandler.setWebappName(context.getCurrentXmlTest().getParameter(TestBaseRunner.WEBAPP_NAME));
        testSuiteHandler.getEnvironmentHandler().setEnabledEnvironments(context.getCurrentXmlTest().getParameter(TestBaseRunner.ENABLED_ENVIRONMENTS));

        final PlaceholderHandler placeholderHandler = new PlaceholderHandler();
        placeholderHandler.setPreloadedVariables(testSuiteHandler.getTestCaseUtils().getPreloadedVariables());

        final List<Map<String, Object>> testCases = testSuiteHandler.getTestCaseUtils().jsonReader(jsonFile, context);

        return testCases.stream()
            .peek(t -> LOGGER.info("Parsing {}.{}", context.getName(), t.get(TestBaseRunner.ATTR_TESTCASE_ID)))
            .filter(testCase -> !TestBaseRunner.isTestCaseSkippable(
                    context.getName(),
                    testCase.get(TestBaseRunner.ATTR_TESTCASE_ID).toString(),
                    testSuiteHandler.getWebappName(),
                    testSuiteHandler.getEnvironmentHandler().getEnvironmentUrl(testSuiteHandler.getWebappName()),
                    jsonFile)
            )
            .map(testSuite -> new SingleModeTest(context.getName(), testSuiteHandler, testSuite, placeholderHandler))
            .peek(t -> LOGGER.info("Creating {}", t.getTestName()))
            .collect(Collectors.toList())
            .toArray(new SingleModeTest[0]);
    }

    /**
     * Used to set the test name, as the input file, in the testNg report, because by default it shows the 'method' name.
     * Created by acavarero on 12/12/16.
     */
    public static class HeatTestNameListener implements ITestListener {
        @Override
        public void onTestStart(ITestResult result) {
            setTestName(result);
        }

        @Override
        public void onTestSuccess(ITestResult result) {
        }

        @Override
        public void onTestFailure(ITestResult result) {
        }

        @Override
        public void onTestSkipped(ITestResult result) {
        }

        @Override
        public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        }

        @Override
        public void onStart(ITestContext context) {

        }

        @Override
        public void onFinish(ITestContext context) {

        }

        /**
         * Change the test name with reflection, because TestNG seems to ignore the real test name ({@link ITest#getTestName()} in its reports.
         * @param result
         */
        private void setTestName(ITestResult result) {
            if (result.getInstance() instanceof HeatTest && result.getMethod() instanceof BaseTestMethod) {
                String name = ((HeatTest) result.getInstance()).getTestName();
                try {
                    Field methodName = BaseTestMethod.class.getDeclaredField("m_methodName");
                    methodName.setAccessible(true);
                    methodName.set(result.getMethod(), name);
                    methodName.setAccessible(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
