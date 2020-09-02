package com.amazonaws.xray.agent.utils;

import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;

public class NoOpEmitter extends Emitter {
    @Override
    public boolean sendSegment(Segment segment) {
        return true;
    }

    @Override
    public boolean sendSubsegment(Subsegment subsegment) {
        return true;
    }
}
