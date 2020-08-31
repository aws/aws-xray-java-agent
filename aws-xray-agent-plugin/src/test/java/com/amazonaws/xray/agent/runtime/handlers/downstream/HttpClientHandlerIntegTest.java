package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceHeader.SampleDecision;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpClientHandlerIntegTest {
    private final static String TRACE_HEADER_KEY = TraceHeader.HEADER_KEY;
    private Segment currentSegment;

    @Before
    public void setup() {
        // Generate the segment that would be normally made by the upstream instrumentor
        currentSegment = AWSXRay.beginSegment("parentSegment");
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
        currentSegment = null;
    }

    @Test
    public void testBasicGetCall() throws Exception {
        URI uri = new URI("https://www.amazon.com");
        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(uri);

        assertEquals(0, request.getAllHeaders().length);
        HttpResponse httpResponse = httpClient.execute(request);

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Check Subsegment properties
        assertEquals(uri.getHost(), currentSubsegment.getName());
        assertEquals(Namespace.REMOTE.toString(), currentSubsegment.getNamespace());
        assertFalse(currentSubsegment.isInProgress());

        // Check for http-specific request info
        Map<String, String> requestMap = (Map<String, String>) currentSubsegment.getHttp().get("request");
        assertEquals(2, requestMap.size());
        assertEquals("GET", request.getMethod());
        assertEquals(request.getMethod(), requestMap.get("method"));
        assertEquals(uri.toString(), requestMap.get("url"));

        // Check for http-specific response info
        Map<String, String> responseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(1, responseMap.size());
        assertEquals(httpResponse.getStatusLine().getStatusCode(), responseMap.get("status"));

        // Obviously can't pass if above is asserted, but for completeness in case this test fails.
        // the request doesn't return content_length because this is something that's populated in headers.
        // And amazon.com just doesn't publish it.
        assertFalse(responseMap.containsKey("content_length"));

        // Check for Trace propagation
        Header httpTraceHeader = request.getAllHeaders()[0];
        assertEquals(TRACE_HEADER_KEY, httpTraceHeader.getName());
        TraceHeader injectedTH = TraceHeader.fromString(httpTraceHeader.getValue());
        SampleDecision sampleDecision = currentSegment.isSampled() ? SampleDecision.SAMPLED : SampleDecision.NOT_SAMPLED;
        assertEquals(new TraceHeader(currentSegment.getTraceId(), currentSubsegment.getId(), sampleDecision).toString(),
                injectedTH.toString()); // Trace header should be added
    }

    @Test
    public void testBasicPostCall() throws Exception {
        URI uri = new URI("https://www.amazon.com");
        HttpClient httpClient = HttpClients.createMinimal();
        HttpPost request = new HttpPost(uri);
        HttpResponse httpResponse = httpClient.execute(request);

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Check Subsegment properties
        assertEquals(uri.getHost(), currentSubsegment.getName());
        assertEquals(Namespace.REMOTE.toString(), currentSubsegment.getNamespace());
        assertFalse(currentSubsegment.isInProgress());

        // Check for http-specific request info
        Map<String, String> requestMap = (Map<String, String>) currentSubsegment.getHttp().get("request");
        assertEquals(2, requestMap.size());
        assertEquals("POST", request.getMethod());
        assertEquals(request.getMethod(), requestMap.get("method"));
        assertEquals(uri.toString(), requestMap.get("url"));

        // Check for http-specific response info
        Map<String, String> responseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(2, responseMap.size());
        assertEquals(httpResponse.getStatusLine().getStatusCode(), responseMap.get("status"));
        assertEquals(httpResponse.getEntity().getContentLength(), responseMap.get("content_length"));
    }

    @Test
    public void testChainedCalls() throws Exception {
        URI uri = new URI("https://www.amazon.com");
        HttpClient httpClient = HttpClients.createMinimal();
        HttpPost request = new HttpPost(uri);

        assertEquals(0, currentSegment.getSubsegments().size());
        httpClient.execute(request);
        assertEquals(1, currentSegment.getSubsegments().size());
        httpClient.execute(request);
        assertEquals(2, currentSegment.getSubsegments().size());
    }

    @Test
    public void testInvalidTargetHost() throws Exception {
        URI uri = new URI("sdfkljdfs");
        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(uri);
        Throwable theExceptionThrown = null;
        try {
            httpClient.execute(request);
            assertTrue(false); // The test should not hit this case.
        } catch (IllegalArgumentException e) {
            theExceptionThrown = e;
        }

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals(uri.toString(), currentSubsegment.getName());
        assertEquals(1, currentSubsegment.getCause().getExceptions().size());
        assertEquals(theExceptionThrown, currentSubsegment.getCause().getExceptions().get(0).getThrowable());
        assertEquals(Namespace.REMOTE.toString(), currentSubsegment.getNamespace());
        assertTrue(currentSubsegment.isFault());
        assertFalse(currentSubsegment.isError());
        assertFalse(currentSubsegment.isInProgress());

        // Even in failures, we should atleast see the requested information.
        Map<String, String> requestMap = (Map<String, String>) currentSubsegment.getHttp().get("request");
        assertEquals(2, requestMap.size());
        assertEquals("GET", request.getMethod());
        assertEquals(request.getMethod(), requestMap.get("method"));
        assertEquals(uri.toString(), requestMap.get("url"));

        // No response because we passed in an invalid request.
        assertNull(currentSubsegment.getHttp().get("response"));
    }

    @Test
    public void testIgnoreSamplingCalls() throws Exception {
        URI targetsUri = new URI("http://127.0.0.1:2000/SamplingTargets");
        URI rulesUri = new URI("http://127.0.0.1:2000/GetSamplingRules");

        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(targetsUri);
        httpClient.execute(request);

        assertEquals(0, currentSegment.getSubsegments().size());

        request = new HttpGet(rulesUri);
        httpClient.execute(request);

        assertEquals(0, currentSegment.getSubsegments().size());
    }
}
