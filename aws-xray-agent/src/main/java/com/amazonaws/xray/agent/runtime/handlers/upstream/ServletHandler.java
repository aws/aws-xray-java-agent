package com.amazonaws.xray.agent.runtime.handlers.upstream;

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
import java.util.Optional;

/**
 * This handler handles an HttpEvent usually retrieved as a result of servlet interception and generates a segment.
 * It populates this segment with HTTP metadata.
 */
public class ServletHandler extends XRayHandler {
    private static final String URL_KEY = "url";
    private static final String METHOD_KEY = "method";
    private static final String CLIENT_IP_KEY = "client_ip";
    private static final String USER_AGENT_KEY = "user_agent";

    private static final String RESPONSE_KEY = "response";
    private static final String HTTP_REQUEST_KEY = "request";
    private static final String STATUS_KEY = "status";


    @Override
    public void handleRequest(Event event) {
        HttpServletNetworkRequestEvent requestEvent = (HttpServletNetworkRequestEvent) event;

        // HttpEvents are seen as servlet invocations, so in every request, we mark that we are serving an Http request
        // In X-Ray's context, this means that if we receive a activity event, to start generating a segment.
        XRayTransactionState transactionState = getTransactionState();
        populateHeaderToTransactionState(requestEvent, transactionState);

        // TODO Fix request event bug so that getHeaderData is lower cased. This needs to be case insensitive
        String headerData = requestEvent.getHeaderData(HEADER_KEY.toLowerCase());
        if (headerData == null) {
            headerData = requestEvent.getHeaderData(HEADER_KEY);
        }
        transactionState.withTraceheaderString(headerData);

        Optional<TraceHeader> traceHeader = Optional.empty();
        String traceHeaderString = transactionState.getTraceHeader();
        // If the trace header string is null, then this is the origin call.
        if (traceHeaderString != null) {
            traceHeader = Optional.of(TraceHeader.fromString(traceHeaderString));
        }
        Segment segment = beginSegment(XRayTransactionState.getServiceName(), traceHeader);

        // Obtain sampling decision
        boolean shouldSample = getSamplingDecision(transactionState, traceHeader);
        segment.setSampled(shouldSample);

        // Add HTTP Information
        Map<String, Object> requestAttributes = new HashMap<>();
        requestAttributes.put(URL_KEY, transactionState.getURL());
        requestAttributes.put(USER_AGENT_KEY, transactionState.getUserAgent());
        requestAttributes.put(METHOD_KEY, transactionState.getMethod());
        requestAttributes.put(CLIENT_IP_KEY, transactionState.getClientIP());
        segment.putHttp(HTTP_REQUEST_KEY, requestAttributes);
    }

    @Override
    public void handleResponse(Event event) {
        HttpServletNetworkResponseEvent responseEvent = (HttpServletNetworkResponseEvent) event;
        Segment currentSegment = getSegment();

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
    private void populateHeaderToTransactionState(HttpNetworkProtocolRequestEvent requestEvent, XRayTransactionState transactionState) {
        transactionState.withHost(requestEvent.getHost())
                .withMethod(requestEvent.getMethod())
                .withUrl(requestEvent.getURL())
                .withUserAgent(requestEvent.getUserAgent())
                .withClientIP(requestEvent.getLocalIPAddress())
                .withTraceheaderString(requestEvent.getHeaderData(HEADER_KEY));
    }
}
