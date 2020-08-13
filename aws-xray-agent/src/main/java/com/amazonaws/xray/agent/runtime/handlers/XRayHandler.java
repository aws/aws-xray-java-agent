package com.amazonaws.xray.agent.runtime.handlers;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceID;
import com.amazonaws.xray.strategy.sampling.SamplingRequest;
import com.amazonaws.xray.strategy.sampling.SamplingResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.disco.agent.concurrent.TransactionContext;

import java.util.Map;
import java.util.Optional;


/**
 * Base class for an X-Ray handler which contains methods that help initiate
 */
public abstract class XRayHandler implements XRayHandlerInterface {
    private static final Log log = LogFactory.getLog(XRayHandler.class);

    /**
     * DiSCo TransactionContext key for XRay State data.
     */
    private static final String XRAY_STATE = "DiSCoXRayState";

    /**
     * AWS key to get X-Ray map
     */
    private static final String XRAY_AWS_KEY = "xray";

    /**
     * Auto instrumentation flag for the xray AWS map.
     */
    private static final String AUTO_INSTRUMENTATION_KEY = "auto_instrumentation";

    /**
     * Trace Header key for X-Ray upstream propagation.
     */
    public static final String HEADER_KEY = TraceHeader.HEADER_KEY;

    /**
     * Retrieve the current transaction state from the TransactionContext if it exists. Otherwise,
     * create a new one, store it in the TransactionContext, and return it.
     * @return the current transaction's transaction state. Create one if none exists.
     */
    protected XRayTransactionState getTransactionState() {
        XRayTransactionState transactionState = (XRayTransactionState) TransactionContext.getMetadata(XRAY_STATE);
        if (transactionState == null) {
            transactionState = new XRayTransactionState();
            TransactionContext.putMetadata(XRAY_STATE, transactionState);
        }

        return transactionState;
    }

    /**
     * Creates a segment using trace header information.
     * @param segmentName - The segment name to name the segment.
     * @param traceID - The segment's trace ID.
     * @param parentID - The parent ID of this segment.
     * @return the new segment
     */
    protected Segment beginSegment(String segmentName, TraceID traceID, String parentID) {
        Segment segment;
        if (traceID == null || parentID == null) {
            segment = AWSXRay.beginSegment(segmentName);
        } else {
            segment = AWSXRay.beginSegment(segmentName, traceID, parentID);
        }

        Map<String, Object> awsMap = segment.getAws();
        if (awsMap == null) {
            // If this is null, the global recorder wasn't properly initialized. Just ignore putting in the version
            // altogether, log this, and continue along.
            log.error("Unable to retrieve AWS map to set the auto instrumentation flag.");
            return segment;
        }

        Map<String, Object> xrayMap = (Map<String, Object>) awsMap.get(XRAY_AWS_KEY);
        if (xrayMap != null) {
            xrayMap.put(AUTO_INSTRUMENTATION_KEY, true);
        } else {
            log.error("Unable to retrieve X-Ray map from AWS map of segment.");
        }

        return segment;
    }

    protected Segment beginSegment(String segmentName, Optional<TraceHeader> traceHeader) {
        TraceID traceId = traceHeader.map(TraceHeader::getRootTraceId).orElse(null);
        String parentId = traceHeader.map(TraceHeader::getParentId).orElse(null);
        return beginSegment(segmentName, traceId, parentId);
    }

    protected Segment getSegment() {
        return AWSXRay.getCurrentSegment();
    }

    protected void endSegment() {
        AWSXRay.endSegment();
    }

    protected Subsegment beginSubsegment(String subsegmentName) {
        return AWSXRay.beginSubsegment(subsegmentName);
    }

    protected Subsegment getSubsegment() {
        return AWSXRay.getCurrentSubsegment();
    }

    protected Optional<Subsegment> getSubsegmentOptional() {
        return AWSXRay.getCurrentSubsegmentOptional();
    }

    protected void endSubsegment() {
        AWSXRay.endSubsegment();
    }

    /**
     * Calculate the sampling decision from the transaction state. The transaction state should contain
     * all the URL, method, host, origin, and service name information.
     * @param transactionState The current state of the X-Ray transaction.
     * @param traceHeader An optional trace header that's passed in usually as a result of an upstream call.
     * @return True if we should sample, false otherwise.
     */
    protected boolean getSamplingDecision(XRayTransactionState transactionState, Optional<TraceHeader> traceHeader) {
        String method = transactionState.getMethod();
        String serviceName = XRayTransactionState.getServiceName();
        String host = transactionState.getHost();
        String uri = transactionState.getURL();

        String traceHeaderString = Optional.ofNullable(transactionState.getTraceHeader()).map(Object::toString).orElse(null);
        // If the trace header string is null, then this is the origin call.
        if (traceHeaderString != null) {
            traceHeader = Optional.of(TraceHeader.fromString(traceHeaderString));
        }

        TraceHeader.SampleDecision sampleDecision = traceHeader.map(TraceHeader::getSampled).orElse(null);
        if (sampleDecision != null) {
            log.debug(String.format("Received sampling decision from trace header: %s", sampleDecision.toString()));
            return sampleDecision.equals(TraceHeader.SampleDecision.SAMPLED);
        }

        // No sampling decision made on the upstream. So use the in-house rules.
        SamplingRequest samplingRequest = new SamplingRequest(serviceName, host, uri, method,
                transactionState.getServiceType());
        SamplingResponse samplingResponse = AWSXRay.getGlobalRecorder().getSamplingStrategy().shouldTrace(samplingRequest);
        return samplingResponse.isSampled();
    }

    /**
     * Builds a trace header object out of the input entity.
     * Typically used for propagating trace information downstream.
     * @param entity Either the segment or subsegment.
     *               Passing in a subsegment will get the information from the parent segment
     * @return The trace header that represents the entity.
     */
    protected TraceHeader buildTraceHeader(Entity entity) {
        boolean isSampled;
        TraceID traceID;

        // Generate the trace header based on the segment itself.
        if (entity instanceof Segment) {
            Segment segment = (Segment) entity;
            isSampled = segment.isSampled();
            traceID = segment.getTraceId();
        } else {
            // Generate the trace header based on the parent of the subsegment.
            Subsegment subsegment = (Subsegment) entity;
            isSampled = subsegment.getParentSegment().isSampled();
            traceID = subsegment.getParentSegment().getTraceId();
        }
        TraceHeader traceHeader = new TraceHeader(traceID,
                isSampled ? entity.getId() : null,
                isSampled ? TraceHeader.SampleDecision.SAMPLED : TraceHeader.SampleDecision.NOT_SAMPLED);

        return traceHeader;
    }
}
