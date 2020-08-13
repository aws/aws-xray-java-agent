package com.amazonaws.xray.agent.runtime.handlers.upstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceID;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.event.HttpServletNetworkRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkResponseEvent;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class ServletHandlerTest {
    public static final String HEADER_KEY = "X-Amzn-Trace-Id";

    private final String ORIGIN = "httpServlet";
    private final String SERVICE_NAME = "SomeServletHostedService";
    private final String SDK = "X-Ray for Java";

    // Transaction State configurations
    private final String HOST = "localhost:8080";
    private final String METHOD = "POST";
    private final String URL = "http://localhost:8080/";
    private final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36";
    private final String DST_IP = "192.52.32.10";
    private final String SRC_IP = "152.152.152.2";

    private ServletHandler servletHandler;

    @Before
    public void setup() {
        AWSXRay.getGlobalRecorder().setSamplingStrategy(new NoSamplingStrategy());
        servletHandler = new ServletHandler();
        XRayTransactionState.setServiceName(SERVICE_NAME);
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testHandleRequest() {
        HttpServletNetworkRequestEvent requestEvent = new HttpServletNetworkRequestEvent(ORIGIN, 54, 32, SRC_IP, DST_IP);
        requestEvent.withHost(HOST)
                .withMethod(METHOD)
                .withURL(URL)
                .withUserAgent(USER_AGENT);

        Assert.assertFalse(AWSXRay.getCurrentSegmentOptional().isPresent());
        servletHandler.handleRequest(requestEvent);
        Assert.assertTrue(AWSXRay.getCurrentSegmentOptional().isPresent());

        Segment servletSegment = AWSXRay.getCurrentSegment();

        Assert.assertEquals(SERVICE_NAME, servletSegment.getName());

        Map<String, String> httpMap = (Map<String, String>) servletSegment.getHttp().get("request");
        Assert.assertEquals(METHOD, httpMap.get("method"));
        Assert.assertEquals(DST_IP, httpMap.get("client_ip"));
        Assert.assertEquals(URL, httpMap.get("url"));
        Assert.assertEquals(USER_AGENT, httpMap.get("user_agent"));

        Map<String, Object> xrayMap = (Map<String, Object>) servletSegment.getAws().get("xray");
        Assert.assertEquals(SDK, xrayMap.get("sdk"));
        Assert.assertEquals(Boolean.TRUE, xrayMap.get("auto_instrumentation"));
    }

    @Test
    public void testHandleRequestWithTraceHeader() {
        HttpServletNetworkRequestEvent requestEvent = new HttpServletNetworkRequestEvent(ORIGIN, 54, 32, SRC_IP, DST_IP);
        TraceID traceID = new TraceID();
        String parentId = "1fb64b3cbdcd5705";

        TraceHeader thSampled = new TraceHeader(traceID, parentId, TraceHeader.SampleDecision.SAMPLED);
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(HEADER_KEY.toLowerCase(), thSampled.toString());
        requestEvent.withHeaderMap(headerMap);

        servletHandler.handleRequest(requestEvent);
        Segment servletSegment = AWSXRay.getCurrentSegment();
        Assert.assertTrue(servletSegment.isSampled());
        Assert.assertEquals(thSampled.getRootTraceId(), servletSegment.getTraceId());
        Assert.assertEquals(thSampled.getParentId(), parentId);
        Assert.assertEquals(thSampled.getSampled() == TraceHeader.SampleDecision.SAMPLED, servletSegment.isSampled());
        AWSXRay.clearTraceEntity();

        TraceHeader thUnsampled = new TraceHeader(traceID, parentId, TraceHeader.SampleDecision.NOT_SAMPLED);
        headerMap = new HashMap<>();
        headerMap.put(HEADER_KEY.toLowerCase(), thUnsampled.toString());
        requestEvent.withHeaderMap(headerMap);

        servletHandler.handleRequest(requestEvent);
        servletSegment = AWSXRay.getCurrentSegment();
        Assert.assertFalse(servletSegment.isSampled());
        Assert.assertEquals(thSampled.getRootTraceId(), servletSegment.getTraceId());
        Assert.assertEquals(thSampled.getParentId(), parentId);
        Assert.assertEquals(thSampled.getSampled() == TraceHeader.SampleDecision.NOT_SAMPLED, servletSegment.isSampled());
    }

    @Test
    public void testHandleResponse() {
        HttpServletNetworkRequestEvent requestEvent = mock(HttpServletNetworkRequestEvent.class);
        HttpServletNetworkResponseEvent responseEvent = new HttpServletNetworkResponseEvent(ORIGIN, requestEvent);
        responseEvent.withStatusCode(200);
        Segment servletSegment = AWSXRay.beginSegment(SERVICE_NAME);

        servletHandler.handleResponse(responseEvent);

        Assert.assertEquals(200, ((Map<String, Integer>) servletSegment.getHttp().get("response")).get("status").intValue());
    }

    @Test
    public void testHandleResponseErrors() {
        HttpServletNetworkRequestEvent requestEvent = mock(HttpServletNetworkRequestEvent.class);
        HttpServletNetworkResponseEvent responseEvent = new HttpServletNetworkResponseEvent(ORIGIN, requestEvent);

        Segment servletSegment = AWSXRay.beginSegment(SERVICE_NAME);
        responseEvent.withStatusCode(400);
        servletHandler.handleResponse(responseEvent);
        Assert.assertEquals(400, ((Map<String, Integer>) servletSegment.getHttp().get("response")).get("status").intValue());
        Assert.assertTrue(servletSegment.isError());
        Assert.assertFalse(servletSegment.isFault());
        AWSXRay.clearTraceEntity();

        servletSegment = AWSXRay.beginSegment(SERVICE_NAME);
        responseEvent.withStatusCode(500);
        servletHandler.handleResponse(responseEvent);
        Assert.assertEquals(500, ((Map<String, Integer>) servletSegment.getHttp().get("response")).get("status").intValue());
        Assert.assertFalse(servletSegment.isError());
        Assert.assertTrue(servletSegment.isFault());
    }
}
