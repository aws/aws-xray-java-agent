package com.amazonaws.xray.agent.models;

import software.amazon.disco.agent.concurrent.TransactionContext;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.contexts.SegmentContext;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.SubsegmentImpl;
import com.amazonaws.xray.exceptions.SegmentNotFoundException;
import com.amazonaws.xray.exceptions.SubsegmentNotFoundException;

/**
 * X-Ray-friendly context that utilizes the TransactionContext object to propagate across thread boundaries. This context
 * is used by the global recorder to maintain segments and subsegments.
 */
public class XRayTransactionContext implements SegmentContext {
    private static final String XRAY_ENTITY_KEY = "DiSCoXRayEntity";
    private static final Logger logger = LogManager.getLogger(XRayTransactionContext.class);

    // Transaction Context approach.
    public Entity getTraceEntity() {
        return (Entity) TransactionContext.getMetadata(XRAY_ENTITY_KEY);
    }

    public void setTraceEntity(Entity entity) {
        TransactionContext.putMetadata(XRAY_ENTITY_KEY, entity);
    }

    public void clearTraceEntity() {
        TransactionContext.putMetadata(XRAY_ENTITY_KEY, null);
    }

    @Override
    public Subsegment beginSubsegment(AWSXRayRecorder recorder, String name) {
        Entity current = getTraceEntity();
        if (null == current) {
            recorder.getContextMissingStrategy().contextMissing("Failed to begin subsegment named '" + name + "': segment cannot be found.", SegmentNotFoundException.class);
            return null;
        }
        if (LogManager.isDebugEnabled()) {
            logger.debug("Beginning subsegment named: " + name);
        }
        Segment parentSegment = getTraceEntity().getParentSegment();
        Subsegment subsegment = new SubsegmentImpl(recorder, name, parentSegment);
        subsegment.setParent(current);
        current.addSubsegment(subsegment);
        setTraceEntity(subsegment);
        return subsegment;
    }

    @Override
    public void endSubsegment(AWSXRayRecorder recorder) {
        Entity current = getTraceEntity();
        if (current instanceof Subsegment) {
            // LogManager.isDebugEnabled()
            if (LogManager.isDebugEnabled()) {
                logger.debug("Ending subsegment named: " + current.getName());
            }
            Subsegment currentSubsegment = (Subsegment) current;
            if (currentSubsegment.end()) {
                recorder.sendSegment(currentSubsegment.getParentSegment());
            } else {
                if (recorder.getStreamingStrategy().requiresStreaming(currentSubsegment.getParentSegment())) {
                    recorder.getStreamingStrategy().streamSome(currentSubsegment.getParentSegment(), recorder.getEmitter());
                }
                setTraceEntity(current.getParent());
            }
        } else {
            recorder.getContextMissingStrategy().contextMissing("Failed to end subsegment: subsegment cannot be found.", SubsegmentNotFoundException.class);
        }
    }
}
