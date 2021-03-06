/**
 * Mule Anypoint Template
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.context.notification.NotificationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.templates.utils.Employee;

import com.mulesoft.module.batch.BatchTestHelper;

/**
 * The objective of this class is to validate the correct behavior of the flows
 * for this Anypoint Template that make calls to external systems.
 */
public class BusinessLogicIT extends AbstractTemplateTestCase {

	private static final long TIMEOUT_MILLIS = 300000;
	private static final long DELAY_MILLIS = 500;
	private static final String PATH_TO_TEST_PROPERTIES = "./src/test/resources/mule.test.properties";
	private static final String TEST_NAME = "wday";
	private static final Logger log = LogManager.getLogger(BusinessLogicIT.class);
	private BatchTestHelper helper;
	private Employee user;
	private Map<String, String> emailUser;
	private static String WORKDAY_ID;	
    private static String EMAIL = "@broadcast.com"; 
    
    @BeforeClass
    public static void beforeTestClass() {
        System.setProperty("poll.startDelayMillis", "8000");
        System.setProperty("poll.frequencyMillis", "30000");
        Date initialDate = new Date(System.currentTimeMillis() - 1000 * 60 * 3);
        Calendar cal = Calendar.getInstance();
        cal.setTime(initialDate);
        System.setProperty(
        		"watermark.defaultExpression", 
        		"#[groovy: new GregorianCalendar("
        				+ cal.get(Calendar.YEAR) + ","
        				+ cal.get(Calendar.MONTH) + ","
        				+ cal.get(Calendar.DAY_OF_MONTH) + ","
        				+ cal.get(Calendar.HOUR_OF_DAY) + ","
        				+ cal.get(Calendar.MINUTE) + ","
        				+ cal.get(Calendar.SECOND) + ") ]");
    }

    @Before
    public void setUp() throws Exception {
		final Properties props = new Properties();
    	try {
    		props.load(new FileInputStream(PATH_TO_TEST_PROPERTIES));
    	} catch (Exception e) {
    	   log.error("Error occured while reading mule.test.properties", e);
    	} 
    	WORKDAY_ID = props.getProperty("wday.testuser.id");
    	helper = new BatchTestHelper(muleContext);
		stopFlowSchedulers(POLL_FLOW_NAME);
		registerListeners();
				
    }

    private Map<String, String> generateEmail() {
    	emailUser = new HashMap<String, String>();
    	emailUser.put("Email", System.currentTimeMillis() + EMAIL);
    	emailUser.put("Id", WORKDAY_ID);    	
    	return emailUser;
	}

    private void registerListeners() throws NotificationException {
		muleContext.registerListener(pipelineListener);
	}
    
    private void updateNameTestDataInSandBox(Employee user) throws MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("updateWorkdayEmployeeName");
		flow.initialise();
		log.info("updating a workday employee...");
		try {
			flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
    private void updateEmailTestDataInSandBox(Map<String, String> user) throws MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("updateWorkdayEmployeeEmail");
		flow.initialise();
		log.info("updating a workday employee...");
		try {
			flow.process(getTestEvent(user, MessageExchangePattern.REQUEST_RESPONSE));						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
    private Employee generateEmployee(){    	
    	user = new Employee(TEST_NAME + System.currentTimeMillis(), TEST_NAME + System.currentTimeMillis(), "", WORKDAY_ID);  
		return user;
	}
        
	@SuppressWarnings("unchecked")
	@Test
    public void testCreateFlow() throws Exception {
		updateEmailTestDataInSandBox(generateEmail());
		// this is testing the insert branch
		basicTest();
		
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("getSAPEmployeeByEmail");
		flow.initialise();
		MuleEvent response = flow.process(getTestEvent(emailUser.get("Email"), MessageExchangePattern.REQUEST_RESPONSE));
		
		Map<String, String> sapEmployee = (Map<String, String>) response.getMessage().getPayload();		
		log.info("sap employee after create: " + sapEmployee);
		assertNotNull("SAP Employee should have been synced", sapEmployee);
		
		// remove test data from SAP, moved here as @After would cause the redundant and invalid remove call
		deleteTestDataFromSandBox(sapEmployee.get("id"));
				
	}	
	
	@Test
    public void testUpdateFlow() throws Exception {
		updateNameTestDataInSandBox(generateEmployee());
		// this is testing the update branch		
		basicTest();
		
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("getSAPEmployee");
		flow.initialise();
		Map<String, String> map = new HashMap<String, String>();
		map.put("firstName", user.getGivenName());
		map.put("lastName", user.getFamilyName());
		MuleEvent response = flow.process(getTestEvent(map, MessageExchangePattern.REQUEST_RESPONSE));
		Map<String, String> sapEmployee = (Map<String, String>) response.getMessage().getPayload();
		log.info("sap employee: " + sapEmployee);
		assertNotNull("SAP Employee should have been synced", sapEmployee.get("id"));
		
    }
	
	private void basicTest() throws InterruptedException, Exception,
			InitialisationException, MuleException {
		Thread.sleep(20000);
		runSchedulersOnce(POLL_FLOW_NAME);
		waitForPollToRun();
		helper.awaitJobTermination(TIMEOUT_MILLIS, DELAY_MILLIS);
		helper.assertJobWasSuccessful();							
	}    
    
    private void deleteTestDataFromSandBox(String id) throws MuleException, Exception {
    	log.info("deleting test employee: " + id);
    	SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("terminateSAPEmployee");
		flow.initialise();
		Map<String, String> map = new HashMap<String, String>();
		map.put("id", id);
		flow.process(getTestEvent(map));		
	}       
	
}
