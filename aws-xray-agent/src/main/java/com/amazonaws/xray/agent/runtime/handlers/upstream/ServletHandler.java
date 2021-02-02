package com.amazonaws.xray.agent.runtime.handlers.upstream;

import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.handlers.XRayHandler;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceHeader;
import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.HttpNetworkProtocolRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkResponseEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * This handler handles an HttpEvent usually retrieved as a result of servlet interception and generates a segment.
 * It populates this segment with HTTP metadata.
 */
public class ServletHandler extends XRayHandler {
    private static final String URL_KEY = "url";
    private static final String METHOD_KEY = "method";
    private static final String CLIENT_IP_KEY = "client_ip";
    private static final String USER_AGENT_KEY = "user_agent";
    private static final String FORWARDED_FOR_KEY_LOWER = "x-forwarded-for";
    private static final String FORWARDED_FOR_KEY_UPPER = "X-Forwarded-For";
    private static final String FORWARDED_FOR_ATTRIB = "x_forwarded_for";

    private static final String RESPONSE_KEY = "response";
    private static final String HTTP_REQUEST_KEY = "request";
    private static final String STATUS_KEY = "status";

    @Override
    public void handleRequest(Event event) {
        HttpServletNetworkRequestEvent requestEvent = (HttpServletNetworkRequestEvent) event;

        // For Spring Boot apps, the trace ID injection libraries will not be visible on classpath until after startup,
        // so we must try to lazy load them as early as possible
        XRaySDKConfiguration.getInstance().lazyLoadTraceIdInjection(getGlobalRecorder());

        // HttpEvents are seen as servlet invocations, so in every request, we mark that we are serving an Http request
        // In X-Ray's context, this means that if we receive a activity event, to start generating a segment.
        XRayTransactionState transactionState = getTransactionState();
        addRequestDataToTransactionState(requestEvent, transactionState);
        boolean ipForwarded = addClientIPToTransactionState(requestEvent, transactionState);

        // TODO Fix request event bug so that getHeaderData is lower cased. This needs to be case insensitive
        // See: https://github.com/awslabs/disco/issues/14
        String headerData = requestEvent.getHeaderData(HEADER_KEY.toLowerCase());
        if (headerData == null) {
            headerData = requestEvent.getHeaderData(HEADER_KEY);
        }
        transactionState.withTraceheaderString(headerData);

        TraceHeader traceHeader = TraceHeader.fromString(transactionState.getTraceHeader());
        Segment segment = beginSegment(XRayTransactionState.getServiceName(), traceHeader);

        // Obtain sampling decision
        boolean shouldSample = getSamplingDecision(transactionState);
        segment.setSampled(shouldSample);

        // Add HTTP Information
        Map<String, Object> requestAttributes = new HashMap<>();
        requestAttributes.put(URL_KEY, transactionState.getURL());
        requestAttributes.put(USER_AGENT_KEY, transactionState.getUserAgent());
        requestAttributes.put(METHOD_KEY, transactionState.getMethod());
        requestAttributes.put(CLIENT_IP_KEY, transactionState.getClientIP());
        if (ipForwarded) requestAttributes.put(FORWARDED_FOR_ATTRIB, true);
        segment.putHttp(HTTP_REQUEST_KEY, requestAttributes);
    }

    @Override
    public void handleResponse(Event event) {
        HttpServletNetworkResponseEvent responseEvent = (HttpServletNetworkResponseEvent) event;
        Segment currentSegment = getSegment();

        // No need to log since a Context Missing Error will already be recorded
        if (currentSegment == null) {
            return;
        }

        // Add the status code
        // Obtain the status code of the underlying http response. If it failed, it's a fault.
        Map<String, Object> responseAttributes = new HashMap<>();
        int statusCode = responseEvent.getStatusCode();

        // Check if the status code was a fault.
        switch (statusCode / 100) {
            case 2:
                // Server OK
                break;
            case 4:
                // Exception
                currentSegment.setError(true);
                if (statusCode == 429) {
                    currentSegment.setThrottle(true);
                }
                break;
            case 5:
                // Fault
                currentSegment.setFault(true);
                break;
        }
        responseAttributes.put(STATUS_KEY, statusCode);
        currentSegment.putHttp(RESPONSE_KEY, responseAttributes);

        endSegment();
    }

    /**
     * Helper method to put all the relevant Http information from the request event to our transaction state.
     *
     * @param requestEvent     The HttpNetworkProtocolRequestEvent that was captured from the event bus.
     * @param transactionState The current XRay transactional state
     */
    private void addRequestDataToTransactionState(HttpNetworkProtocolRequestEvent requestEvent, XRayTransactionState transactionState) {
        transactionState.withHost(requestEvent.getHost())
                .withMethod(requestEvent.getMethod())
                .withUrl(requestEvent.getURL())
                .withUserAgent(requestEvent.getUserAgent())
                .withTraceheaderString(requestEvent.getHeaderData(HEADER_KEY));
    }

    private boolean addClientIPToTransactionState(HttpNetworkProtocolRequestEvent requestEvent, XRayTransactionState transactionState) {
        String clientIP = requestEvent.getHeaderData(FORWARDED_FOR_KEY_UPPER);
        boolean forwarded = true;

        if (clientIP == null || clientIP.isEmpty()) {
            clientIP = requestEvent.getHeaderData(FORWARDED_FOR_KEY_LOWER);
        }
        if (clientIP == null || clientIP.isEmpty()) {
            clientIP = requestEvent.getRemoteIPAddress();
            forwarded = false;
        }

        transactionState.withClientIP(clientIP);
        return forwarded;
    }
}
