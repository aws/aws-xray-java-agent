package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class AWSHandlerTest {
    private final String ORIGIN = "AWSv1";
    private final String SERVICE = "AmazonDynamoDBv2";
    private final String OPERATION = "ScanRequest";

    // DynamoDB Specific fake request and response object properties
    private final String TABLE_NAME = "TestTableName";
    private final String AWS_REQUEST_ID = "SOMERANDOMID";
    private final int COUNT = 25;
    private final int SCAN_COUNT = 10;

    private AWSHandler awsHandler;

    @Mock
    private Request awsRequest;

    private Response awsResponse;

    private ScanRequest scanRequest;
    private ScanResult scanResult;
    private Segment parentSegment;

    private ScanResult generateFakeScanResult() {
        ScanResult theScanResult = new ScanResult();
        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("AWS_REQUEST_ID", AWS_REQUEST_ID);
        ResponseMetadata sdkResponseMetadata = new ResponseMetadata(metadataMap);
        theScanResult.setSdkResponseMetadata(sdkResponseMetadata);

        Map<String, String> headerMap = new HashMap<>();
        HttpResponse fakeHttpResponse = mock(HttpResponse.class);
        when(fakeHttpResponse.getHeaders()).thenReturn(headerMap);
        when(fakeHttpResponse.getStatusCode()).thenReturn(200);
        SdkHttpMetadata sdkHttpMetadata = SdkHttpMetadata.from(fakeHttpResponse);
        theScanResult.setSdkHttpMetadata(sdkHttpMetadata);

        return theScanResult;
    }

    @Before
    public void setup() {
        parentSegment = AWSXRay.beginSegment("TestSegment");
        awsHandler = new AWSHandler();

        // We create an imitation DynamoDB request/response for this test.
        // Mock the request object
        scanRequest = new ScanRequest("TestName");
        when(awsRequest.getOriginalRequest()).thenReturn(scanRequest);
        when(awsRequest.getServiceName()).thenReturn(SERVICE);
        scanRequest.setTableName(TABLE_NAME);

        // Generate a "fake" response now; populate it with metadata X-Ray would gather.
        scanResult = generateFakeScanResult();
        scanResult.setCount(COUNT);
        scanResult.setScannedCount(SCAN_COUNT);

        HttpResponse fakeHttpResponse = mock(HttpResponse.class);
        when(fakeHttpResponse.getHeaders()).thenReturn(scanResult.getSdkHttpMetadata().getHttpHeaders());
        when(fakeHttpResponse.getStatusCode()).thenReturn(scanResult.getSdkHttpMetadata().getHttpStatusCode());
        awsResponse = new Response(scanResult, fakeHttpResponse);
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testHandleAWSRequest() {
        ServiceDownstreamRequestEvent awsRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        awsRequestEvent.withRequest(awsRequest);
        awsHandler.handleRequest(awsRequestEvent);

        Subsegment awsSubsegment = AWSXRay.getCurrentSubsegment();
        Assert.assertEquals("aws", awsSubsegment.getNamespace());
        Assert.assertEquals(SERVICE, awsSubsegment.getName());
        Assert.assertTrue(awsSubsegment.isInProgress());

        Map<String, Object> awsMap = awsSubsegment.getAws();
        Assert.assertEquals("Scan", awsMap.get("operation"));
        Assert.assertEquals(TABLE_NAME, awsMap.get("table_name"));

        Assert.assertEquals(parentSegment, awsSubsegment.getParentSegment());
        Assert.assertEquals(1, parentSegment.getSubsegments().size());
    }

    @Test
    public void testPopulateTraceHeaderToHttpHeader() {
        // Ensure that we propagate the trace header to the request's http header.
        ServiceDownstreamRequestEvent awsRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        awsRequestEvent.withRequest(awsRequest);
        awsHandler.handleRequest(awsRequestEvent);

        TraceID traceID = parentSegment.getTraceId();
        String parentID = AWSXRay.getCurrentSubsegment().getId();
        TraceHeader.SampleDecision sampleDecision = parentSegment.isSampled() ? TraceHeader.SampleDecision.SAMPLED : TraceHeader.SampleDecision.NOT_SAMPLED;
        TraceHeader theTraceHeader = new TraceHeader(traceID, parentID, sampleDecision);

        verify(awsRequest).addHeader(TraceHeader.HEADER_KEY, theTraceHeader.toString());
    }

    @Test
    public void testHandleAWSResponse() {
        ServiceDownstreamRequestEvent awsRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        awsRequestEvent.withRequest(awsRequest);
        ServiceDownstreamResponseEvent awsResponseEvent = new ServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, awsRequestEvent);
        awsResponseEvent.withResponse(awsResponse);

        Subsegment awsSubsegment = AWSXRay.beginSubsegment(SERVICE);
        awsSubsegment.setNamespace("aws"); // Mimic aws namespace so that the subsegment would be processed.
        awsHandler.handleResponse(awsResponseEvent);

        Map<String, Object> awsMap = awsSubsegment.getAws();
        Assert.assertEquals(AWS_REQUEST_ID, awsMap.get("request_id"));
        Assert.assertEquals(SCAN_COUNT, awsMap.get("scanned_count"));
        Assert.assertEquals(COUNT, awsMap.get("count"));

        Map<String, Object> httpMap = awsSubsegment.getHttp();
        Assert.assertEquals(200, ((Map<String, Integer>) httpMap.get("response")).get("status").intValue());
    }

    @Test
    public void testHandleEventFault() {
        ServiceDownstreamRequestEvent awsRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        awsRequestEvent.withRequest(awsRequest);
        ServiceDownstreamResponseEvent awsResponseEvent = new ServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, awsRequestEvent);
        awsResponseEvent.withResponse(null);
        awsResponseEvent.withThrown(new AmazonDynamoDBException("Some error"));

        Subsegment awsSubsegment = AWSXRay.beginSubsegment(SERVICE);
        awsSubsegment.setNamespace("aws"); // Mimic aws namespace so that the subsegment would be processed.

        Assert.assertEquals(0, awsSubsegment.getCause().getExceptions().size());

        awsHandler.handleResponse(awsResponseEvent);

        // Ensure that our subsegment has the error in it.
        Assert.assertEquals(1, awsSubsegment.getCause().getExceptions().size());
        Assert.assertTrue(awsSubsegment.isFault());
    }
}
