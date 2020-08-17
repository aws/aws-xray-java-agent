package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.agent.runtime.handlers.XRayHandler;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.HttpServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.HttpServiceDownstreamResponseEvent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HttpClientHandler extends XRayHandler {
    private static final Log log = LogFactory.getLog(HttpClientHandler.class);

    // Request Fields
    private static final String URL_KEY = "url";
    private static final String METHOD_KEY = "method";
    private static final String HTTP_REQUEST_KEY = "request";

    // Response Fields
    private static final String STATUS_CODE_KEY = "status";
    private static final String CONTENT_LENGTH_KEY = "content_length";
    private static final String HTTP_RESPONSE_KEY = "response";

    @Override
    public void handleRequest(Event event) {
        HttpServiceDownstreamRequestEvent requestEvent = (HttpServiceDownstreamRequestEvent) event;
        URI uri = getUriFromEvent(requestEvent);

        if (isWithinAWSCall() || isXRaySamplingCall(uri) || isXRayPluginCall(uri)) {
            return;
        }

        String hostName = uri.getHost();
        if (hostName == null) {
            // If we fail to acquire the hostname, we will use the entire URL/service name instead.
            // This is an indication that there's an issue with the request configuration so we
            // use the entire URI so we can provide a subsegment with useful information.
            hostName = requestEvent.getService();
        }
        Subsegment subsegment = beginSubsegment(hostName);

        // Adds http metadata and stores the Trace Header into the request header.
        if (subsegment != null) {
            addRequestInformation(subsegment, requestEvent, uri);
        }
    }

    /**
     * Obtains the downstream call's Uri from the event. Fallsback to obtaining it from the
     * HttpRequest object otherwise.
     * @param requestEvent - The request event to get the Uri from
     * @return The full URI of the downstream call.
     */
    private URI getUriFromEvent(HttpServiceDownstreamRequestEvent requestEvent) {
        URI uri;
        try {
            uri = new URI(requestEvent.getUri());
        } catch (URISyntaxException e) {
            log.error("HttpClientHandler: Unable to generate URI from request event service; using request object's URI: "
                    + requestEvent.getService());
            uri = null;
        }
        return uri;
    }

    private static void addRequestInformation(Subsegment subsegment, HttpServiceDownstreamRequestEvent requestEvent, URI uri) {
        subsegment.setNamespace(Namespace.REMOTE.toString());
        Segment parentSegment = subsegment.getParentSegment();
        String url = uri.toString();

        TraceHeader header = new TraceHeader(parentSegment.getTraceId(),
                parentSegment.isSampled() ? subsegment.getId() : null,
                parentSegment.isSampled() ? TraceHeader.SampleDecision.SAMPLED : TraceHeader.SampleDecision.NOT_SAMPLED);
        requestEvent.replaceHeader(TraceHeader.HEADER_KEY, header.toString());

        Map<String, Object> requestInformation = new HashMap<>();
        requestInformation.put(URL_KEY, url);
        requestInformation.put(METHOD_KEY, requestEvent.getMethod());

        subsegment.putHttp(HTTP_REQUEST_KEY, requestInformation);
    }

    @Override
    public void handleResponse(Event event) {
        HttpServiceDownstreamResponseEvent responseEvent = (HttpServiceDownstreamResponseEvent) event;

        // Check again if this is an X-Ray sampling call or within an AWS call.
        // By this time, the request handler would've executed the same logic and didn't generate a subsegment.
        HttpServiceDownstreamRequestEvent requestEvent = (HttpServiceDownstreamRequestEvent) responseEvent.getRequest();
        URI uri = getUriFromEvent(requestEvent);
        if (isWithinAWSCall() || isXRaySamplingCall(uri) || isXRayPluginCall(uri)) {
            return;
        }

        Optional<Subsegment> subsegmentOptional = getSubsegmentOptional();
        if (subsegmentOptional.isPresent()) {
            addResponseInformation(subsegmentOptional.get(), responseEvent);
            endSubsegment();
        }
    }

    private static void addResponseInformation(Subsegment subsegment, HttpServiceDownstreamResponseEvent responseEvent) {
        Map<String, Object> responseInformation = new HashMap<>();

        // Add exceptions
        if (responseEvent.getThrown() != null) {
            Throwable exception = responseEvent.getThrown();
            subsegment.addException(exception);
        }

        int responseCode = responseEvent.getStatusCode();
        switch (responseCode/100) {
            case 4:
                subsegment.setError(true);
                if (429 == responseCode) {
                    subsegment.setThrottle(true);
                }
                break;
            case 5:
                subsegment.setFault(true);
                break;
        }
        if (responseCode >= 0) {
            responseInformation.put(STATUS_CODE_KEY, responseCode);
        }

        long contentLength = responseEvent.getContentLength();
        if (contentLength >= 0) {
            // If content length is -1, then the information isn't provided in the web request.
            responseInformation.put(CONTENT_LENGTH_KEY, contentLength);
        }

        if (responseInformation.size() > 0) {
            subsegment.putHttp(HTTP_RESPONSE_KEY, responseInformation);
        }
    }

    /** Check if the current call is within an AWS SDK call.
     * We can validate this by seeing if the parent subsegment is an AWS one and is valid.
     *
     * @return true if we are currently processing an AWS SDK request
     */
    private boolean isWithinAWSCall() {
        Optional<Subsegment> subsegmentOptional = getSubsegmentOptional();
        if (!subsegmentOptional.isPresent()) {
            // If the subsegment doesn't exist, this must either be a vanilla http client call
            // or an x-ray sample call
            return false;
        }

        Subsegment currentSubsegment = subsegmentOptional.get();
        String namespace = currentSubsegment.getNamespace() == null ? "" : currentSubsegment.getNamespace();
        return namespace.equals(Namespace.AWS.toString()) && currentSubsegment.isInProgress();
    }

    /**
     * Checks if the current call is an X-Ray sampling call
     *
     * @param uri URI of intercepted call
     * @return true if this is a request made by X-Ray sampling
     */
    private boolean isXRaySamplingCall(URI uri) {
        String uriPath = uri.getPath();
        return uriPath != null && (uriPath.equals("/GetSamplingRules") || uriPath.equals("/SamplingTargets"));
    }

    /**
     * Checks if the current request is the one made by EKS plugin to Kubernetes endpoint
     *
     * @param uri URI of intercepted call
     * @return true if this is a request made by EKS plugin
     */
    private boolean isXRayPluginCall(URI uri) {
        String uriPath = uri.getPath();
        return uriPath != null && uriPath.equals("/api/v1/namespaces/amazon-cloudwatch/configmaps/cluster-info");
    }
}
