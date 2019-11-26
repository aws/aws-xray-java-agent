package com.amazonaws.xray.agent.models;

import com.amazonaws.xray.contexts.SegmentContext;
import com.amazonaws.xray.contexts.SegmentContextResolver;

public class XRayTransactionContextResolver implements SegmentContextResolver {

    @Override
    public SegmentContext resolve() {
        return new XRayTransactionContext();
    }
}