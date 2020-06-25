package com.amazonaws.xray.agent.handlers.upstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.agent.handlers.upstream.source.SimpleHttpServlet;
import com.amazonaws.xray.emitters.UDPEmitter;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServletHandlerIT {
    private final static String TRACE_HEADER_KEY = TraceHeader.HEADER_KEY;
    private final static String PREDEFINED_TRACE_HEADER_SAMPLED = "Root=1-5dc20232-c5f804c16e231025e5cf0d74;Parent=1d62b25534e7d360;Sampled=1";
    private final static String PREDEFINED_TRACE_HEADER_NOT_SAMPLED = "Root=1-5dc203d8-8d0b0d30aab25f0cfcfaf63a;Parent=38e0df8c9363d068;Sampled=0";
    private final static String SERVICE_NAME = "IntegTest";  // This is hardcoded in the Pom.xml file
    private HttpServlet theServlet;

    private HttpServletRequest request;
    private HttpServletResponse response;

    private AWSXRayRecorder recorder;
    private UDPEmitter mockEmitter;

    @Before
    public void setup() throws Exception {
        theServlet = new SimpleHttpServlet();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        mockEmitter = mock(UDPEmitter.class);

        recorder = AWSXRay.getGlobalRecorder();
        recorder.setSamplingStrategy(new AllSamplingStrategy());
        recorder.setEmitter(mockEmitter); // Even though we're sampling them all, none are sent. This is to test.

        mockHttpObjects();
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testReceiveGetRequest() throws Exception {
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
    }

    @Test
    public void testReceivePostRequest() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
    }

    @Test
    public void testReceive200() throws Exception {
        when(response.getStatus()).thenReturn(200);
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
    }

    @Test
    public void testReceiveThrottle() throws Exception {
        when(response.getStatus()).thenReturn(429);
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
        assertTrue(currentSegment.isError());
        assertTrue(currentSegment.isThrottle());
        assertFalse(currentSegment.isFault());
    }

    @Test
    public void testReceiveFault() throws Exception {
        when(response.getStatus()).thenReturn(500);
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
        assertFalse(currentSegment.isError());
        assertFalse(currentSegment.isThrottle());
        assertTrue(currentSegment.isFault());
    }

    @Test
    public void testReceiveError() throws Exception {
        when(response.getStatus()).thenReturn(400);
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();

        // Check Subsegment properties
        verifyHttpSegment(currentSegment);
        assertTrue(currentSegment.isError());
        assertFalse(currentSegment.isThrottle());
        assertFalse(currentSegment.isFault());
    }

    @Test
    public void testReceiveTraceHeaderSampled() throws Exception {
        // Add trace header to X-Ray through the request object.
        when(request.getHeader(TRACE_HEADER_KEY)).thenReturn(PREDEFINED_TRACE_HEADER_SAMPLED);
        mockHttpObjects(); // We need to remock now that we added a new header
        theServlet.service(request, response);

        Segment currentSegment = interceptSegment();
        verifyHttpSegment(currentSegment);
        TraceHeader origTH = TraceHeader.fromString(PREDEFINED_TRACE_HEADER_SAMPLED);
        TraceHeader.SampleDecision segmentSampleDecision = TraceHeader.SampleDecision.SAMPLED;
        assertEquals(origTH.toString(),
                new TraceHeader(currentSegment.getTraceId(), currentSegment.getParentId(), segmentSampleDecision).toString());
    }

    @Test
    public void testReceiveTraceHeaderUnsampled() throws Exception {
        // Add trace header to X-Ray through the request object.
        when(request.getHeader(TRACE_HEADER_KEY)).thenReturn(PREDEFINED_TRACE_HEADER_NOT_SAMPLED);
        mockHttpObjects();
        theServlet.service(request, response);

        verify(mockEmitter, times(0)).sendSegment(any());
    }

    private void mockHttpObjects() {
        // Mock HttpServletRequest object.
        when(request.getMethod()).thenReturn("GET");
        when(request.getProtocol()).thenReturn("http");

        // Add common http header
        Map<String, String> reqHeaderMap = new HashMap<>();
        reqHeaderMap.put("host", "amazon.com");
        reqHeaderMap.put("referer", "http://amazon.com/explore/something");
        reqHeaderMap.put("user-agent", "Mozilla/5.0 (X11; Linux x86_64; rv:12.0) Gecko/20100101 Firefox/12.0");
        if (request.getHeader(TRACE_HEADER_KEY) != null) {
            // Add trace header through mockito
            reqHeaderMap.put(TRACE_HEADER_KEY, request.getHeader(TRACE_HEADER_KEY));
        }
        for (Map.Entry<String, String> entry : reqHeaderMap.entrySet()) {
            when(request.getHeader(entry.getKey())).thenReturn(entry.getValue());
        }
        // Adding the header names is necessary for
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(reqHeaderMap.keySet()));

        // Used for request event population.
        when(request.getLocalAddr()).thenReturn("0.0.0.0");
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://request.amazon.com"));

        // Mock HttpServletResponse object
        when(response.getStatus()).thenReturn(200);
    }

    private void verifyHttpSegment(Segment segment) {
        assertEquals(SERVICE_NAME, segment.getName());

        Map<String, String> httpRequestMap = (Map<String, String>) segment.getHttp().get("request");
        assertEquals(httpRequestMap.get("method"), request.getMethod());
        assertEquals(httpRequestMap.get("client_ip"), request.getLocalAddr());
        assertEquals(httpRequestMap.get("url"), request.getRequestURL().toString());

        Map<String, String> httpResponseMap = (Map<String, String>) segment.getHttp().get("response");
        assertEquals(response.getStatus(), httpResponseMap.get("status"));
    }

    // Return the segment generated by the call to the servlet.
    // Returns null if none had been intercepted.
    private Segment interceptSegment() {
        ArgumentCaptor<Segment> segmentArgumentCaptor = ArgumentCaptor.forClass(Segment.class);
        verify(mockEmitter).sendSegment(segmentArgumentCaptor.capture());

        List<Segment> capturedSegments = segmentArgumentCaptor.getAllValues();
        assertEquals(1, capturedSegments.size());  // This should only be called after a single call to sendSegment has been made.

        try {
            return capturedSegments.get(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}
