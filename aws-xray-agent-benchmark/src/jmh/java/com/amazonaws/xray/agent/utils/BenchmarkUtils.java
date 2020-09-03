package com.amazonaws.xray.agent.utils;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;

public final class BenchmarkUtils {
    private BenchmarkUtils() {
    }

    /**
     * This initializes the static recorder if the agent is not present, and ensures we don't actually emit
     * segments regardless of whether or not we're using the agent
     *
     * @return the configured X-Ray recorder, also retrievable by AWSXRay.getGlobalRecorder
     */
    public static AWSXRayRecorder configureXRayRecorder() {
        AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();
        recorder.setEmitter(new NoOpEmitter());
        recorder.setSamplingStrategy(new AllSamplingStrategy());
        return AWSXRay.getGlobalRecorder();
    }

    private static class NoOpEmitter extends Emitter {
        @Override
        public boolean sendSegment(Segment segment) {
            return true;
        }

        @Override
        public boolean sendSubsegment(Subsegment subsegment) {
            return true;
        }
    }

}
