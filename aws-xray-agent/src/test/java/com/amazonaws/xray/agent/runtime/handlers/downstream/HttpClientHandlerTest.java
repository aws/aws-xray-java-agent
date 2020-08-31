package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.event.HttpServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.HttpServiceDownstreamResponseEvent;

import java.net.URI;
import java.util.Map;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class HttpClientHandlerTest {
    private final String ORIGIN = "ApacheHttpClient";
    private final String SERVICE = "https://amazon.com";
    private final String OPERATION = "GET";
    private final int STATUS_CODE = 200;
    private final int CONTENT_LENGTH = 48343;

    private HttpClientHandler httpClientHandler;
    private Segment parentSegment;
    private HttpServiceDownstreamRequestEvent httpClientRequestEvent;
    private HttpServiceDownstreamResponseEvent httpClientResponseEvent;

    @Before
    public void setup() {
        parentSegment = AWSXRay.beginSegment("HttpClientTestSegment");
        httpClientHandler = new HttpClientHandler();

        httpClientRequestEvent = new HttpServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        httpClientRequestEvent.withMethod(OPERATION);
        httpClientRequestEvent.withUri(SERVICE);

        httpClientResponseEvent = new HttpServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, httpClientRequestEvent);
        httpClientResponseEvent.withStatusCode(STATUS_CODE);
        httpClientResponseEvent.withContentLength(CONTENT_LENGTH);

    }

    @After
    public void clean() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testHandleGetRequest() throws Exception {
        HttpServiceDownstreamRequestEvent requestEventSpy = spy(httpClientRequestEvent);
        httpClientHandler.handleRequest(requestEventSpy);

        Subsegment httpClientSubsegment = AWSXRay.getCurrentSubsegment();
        Assert.assertEquals(Namespace.REMOTE.toString(), httpClientSubsegment.getNamespace());
        Assert.assertEquals(new URI(SERVICE).getHost(), httpClientSubsegment.getName());
        Assert.assertTrue(httpClientSubsegment.isInProgress());

        Map<String, String> requestMap = (Map<String, String>) httpClientSubsegment.getHttp().get("request");
        Assert.assertEquals(OPERATION, requestMap.get("method"));

        // Trace header check
        TraceID traceID = httpClientSubsegment.getParentSegment().getTraceId();
        String parentID = httpClientSubsegment.getId();
        TraceHeader.SampleDecision sampleDecision = parentSegment.isSampled() ? TraceHeader.SampleDecision.SAMPLED : TraceHeader.SampleDecision.NOT_SAMPLED;
        TraceHeader theTraceHeader = new TraceHeader(traceID, parentID, sampleDecision);
        verify(requestEventSpy).replaceHeader(TraceHeader.HEADER_KEY, theTraceHeader.toString());

        Assert.assertEquals(parentSegment, httpClientSubsegment.getParentSegment());
        Assert.assertEquals(1, parentSegment.getSubsegments().size());
    }

    @Test
    public void testHandleGetResponse() {
        Subsegment httpClientSubsegment = AWSXRay.beginSubsegment("responseSubsegment");
        httpClientHandler.handleResponse(httpClientResponseEvent);

        Map<String, String> responseMap = (Map<String, String>) httpClientSubsegment.getHttp().get("response");
        Assert.assertFalse(httpClientSubsegment.isInProgress());
        Assert.assertEquals(200,responseMap.get("status"));
        Assert.assertEquals(1, parentSegment.getSubsegments().size());
    }

    @Test
    public void testHandleInvalidRequest() {
        HttpServiceDownstreamResponseEvent failedResponseEvent = new HttpServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, httpClientRequestEvent);
        failedResponseEvent.withStatusCode(500);
        failedResponseEvent.withContentLength(1000);
        Throwable ourThrowable = new IllegalArgumentException("Some illegal exception");
        failedResponseEvent.withThrown(ourThrowable);
        Subsegment httpClientSubsegment = AWSXRay.beginSubsegment("failedResponseSubsegment");

        httpClientHandler.handleResponse(failedResponseEvent);
        Assert.assertTrue(httpClientSubsegment.isFault());
        Assert.assertEquals(1, httpClientSubsegment.getCause().getExceptions().size());
        Assert.assertEquals(ourThrowable, httpClientSubsegment.getCause().getExceptions().get(0).getThrowable());
    }

    @Test
    public void testHandle4xxStatusCode() {
        httpClientResponseEvent.withStatusCode(400);
        Subsegment httpClientSubsegment = AWSXRay.beginSubsegment("failedResponseSubsegment");

        httpClientHandler.handleResponse(httpClientResponseEvent);
        Assert.assertTrue(httpClientSubsegment.isError());
        Assert.assertFalse(httpClientSubsegment.isFault());
        Assert.assertFalse(httpClientSubsegment.isThrottle());

        Map<String, Integer> httpResponseMap = (Map<String, Integer>) httpClientSubsegment.getHttp().get("response");
        Assert.assertEquals(400, (int) httpResponseMap.get("status"));
    }

    @Test
    public void testHandleThrottlingStatusCode() {
        httpClientResponseEvent.withStatusCode(429);
        Subsegment httpClientSubsegment = AWSXRay.beginSubsegment("failedResponseSubsegment");

        httpClientHandler.handleResponse(httpClientResponseEvent);
        Assert.assertTrue(httpClientSubsegment.isError());
        Assert.assertFalse(httpClientSubsegment.isFault());
        Assert.assertTrue(httpClientSubsegment.isThrottle());

        Map<String, Integer> httpResponseMap = (Map<String, Integer>) httpClientSubsegment.getHttp().get("response");
        Assert.assertEquals(429, (int) httpResponseMap.get("status"));
    }

    @Test
    public void testHandle5xxStatusCode() {
        httpClientResponseEvent.withStatusCode(500);
        Subsegment httpClientSubsegment = AWSXRay.beginSubsegment("failedResponseSubsegment");

        httpClientHandler.handleResponse(httpClientResponseEvent);
        Assert.assertTrue(httpClientSubsegment.isFault());
        Assert.assertFalse(httpClientSubsegment.isError());
        Assert.assertFalse(httpClientSubsegment.isThrottle());

        Map<String, Integer> httpResponseMap = (Map<String, Integer>) httpClientSubsegment.getHttp().get("response");
        Assert.assertEquals(500, (int) httpResponseMap.get("status"));
    }
}
