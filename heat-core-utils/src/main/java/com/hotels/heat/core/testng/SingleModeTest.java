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

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import com.hotels.heat.core.checks.BasicChecks;
import com.hotels.heat.core.handlers.PlaceholderHandler;
import com.hotels.heat.core.handlers.TestCaseMapHandler;
import com.hotels.heat.core.handlers.TestSuiteHandler;
import com.hotels.heat.core.heatspecificchecks.SpecificChecks;
import com.hotels.heat.core.specificexception.HeatException;
import com.hotels.heat.core.utils.RestAssuredRequestMaker;
import com.hotels.heat.core.utils.TestCaseUtils;
import com.hotels.heat.core.utils.TestRequest;
import com.hotels.heat.core.utils.log.LoggingUtils;
import com.jayway.restassured.response.Response;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.annotations.Test;

import static com.hotels.heat.core.runner.TestBaseRunner.*;


/**
 * Created on 20/02/18.
 *
 * @author mrascioni
 */
public class SingleModeTest implements HeatTest {

    private final String suiteName;
    private final TestSuiteHandler testSuiteHandler;
    private final Map<String, Object> description;
    private final PlaceholderHandler placeholderHandler;
    private final LoggingUtils loggingUtils;

    public SingleModeTest(String suiteName, TestSuiteHandler testSuiteHandler, Map<String, Object> description, PlaceholderHandler placeholderHandler) {
        this.suiteName = suiteName;
        this.testSuiteHandler = testSuiteHandler;
        this.description = description;
        this.placeholderHandler = placeholderHandler;
        this.loggingUtils = testSuiteHandler.getLogUtils();
        System.out.println(getTestName());
    }

    @Override
    public String getTestName() {
        return String.format("%s [%s.%s]", description.get(ATTR_TESTCASE_NAME), suiteName, description.get(ATTR_TESTCASE_ID));
    }

    @Test
    public void execute(ITestContext context) {
        testSuiteHandler.getLogUtils().setTestContext(context);
        populateContextAttributes(context, description);

        final Map testCaseParamsElaborated = resolvePlaceholdersInTcParams(context, description);
        final Response apiResponse = executeRequest(testCaseParamsElaborated);

        testSuiteHandler.getTestCaseUtils().setWebappPath(getWebAppPath());

        final BasicChecks basicChecks = new BasicChecks(context);
        basicChecks.setResponse(apiResponse);
        basicChecks.commonTestValidation(testCaseParamsElaborated);

        final Map<String, Response> rspMap = new HashMap<>();
        rspMap.put(testSuiteHandler.getWebappName(), apiResponse);

        specificChecks(context, testCaseParamsElaborated, rspMap, testSuiteHandler.getEnvironmentHandler().getEnvironmentUnderTest());

    }

    /**
     * Method to set useful parameters in the context managed by testNG.
     * Parameters that will be set will be: 'testId', 'suiteDescription', 'tcDescription'
     * @param testCaseParams Map containing test case parameters coming from the json input file
     */
    public void populateContextAttributes(ITestContext testContext, Map<String, Object> testCaseParams) {
        String testCaseID = testCaseParams.get(ATTR_TESTCASE_ID).toString();
        //TODO manage suite description
//        String suiteDescription = TestSuiteHandler.getInstance().getTestCaseUtils().getSuiteDescription();
        String testCaseDesc = testCaseParams.get(ATTR_TESTCASE_NAME).toString();

        testContext.setAttribute(ATTR_TESTCASE_ID, testCaseID);
//        testContext.setAttribute(SUITE_DESCRIPTION_CTX_ATTR, suiteDescription);
        testContext.setAttribute(TC_DESCRIPTION_CTX_ATTR, testCaseDesc);
    }

    public Map resolvePlaceholdersInTcParams(ITestContext context, Map<String, Object> testCaseParams) {
//        TestSuiteHandler testSuiteHandler = TestSuiteHandler.getInstance();
        testSuiteHandler.getLogUtils().setTestCaseId(context.getAttribute(ATTR_TESTCASE_ID).toString());

        // now we start elaborating the parameters.
//        placeholderHandler = new PlaceholderHandler();
//        placeholderHandler.setPreloadedVariables(testSuiteHandler.getTestCaseUtils().getPreloadedVariables());

        final TestCaseMapHandler tcMapHandler = new TestCaseMapHandler(testCaseParams, placeholderHandler);

        final Map<String, Object> elaboratedTestCaseParams = (Map) tcMapHandler.retriveProcessedMap();
//        testSuiteHandler.getTestCaseUtils().setTcParams(elaboratedTestCaseParams); TODO is this really needed?
        return elaboratedTestCaseParams;
    }

    private Response executeRequest(Map testCaseParamsElaborated) {
        Response apiRsp;

        try {
            RestAssuredRequestMaker restAssuredRequestMaker = new RestAssuredRequestMaker();
            TestCaseUtils testCaseUtils = testSuiteHandler.getTestCaseUtils();
            testCaseUtils.setTcParams(testCaseParamsElaborated);

            restAssuredRequestMaker.setBasePath(getWebAppPath());
            TestRequest tr = restAssuredRequestMaker.buildRequestByParams(testCaseUtils.getHttpMethod(), testCaseParamsElaborated);
            apiRsp = restAssuredRequestMaker.executeTestRequest(tr);
            if (apiRsp == null) {
                throw new HeatException(testSuiteHandler.getLogUtils().getExceptionDetails() + "Exception: the service has provided a response null");
            }

        } catch (Exception oEx) {
            loggingUtils.error("Exception class '{}', cause '{}', message '{}'",
                    new Object[] {oEx.getClass(), oEx.getCause(), oEx.getLocalizedMessage()});
            throw new HeatException(loggingUtils.getExceptionDetails() + "Exception message: '" + oEx.getLocalizedMessage() + "'", oEx);
        }

        return apiRsp;
    }

    private String getWebAppPath() {
        return testSuiteHandler.getEnvironmentHandler().getEnvironmentUrl(testSuiteHandler.getWebappName());
    }

    public void specificChecks(ITestContext context, Map testCaseParams, Map<String, Response> rspRetrieved, String environment) {
        ServiceLoader.load(SpecificChecks.class).forEach((checks) -> {
            checks.process(context.getName(), testCaseParams, rspRetrieved,
                    testSuiteHandler.getLogUtils().getTestCaseDetails(),
                    testSuiteHandler.getEnvironmentHandler().getEnvironmentUnderTest());
        });
    }
}
