package com.amazonaws.xray.agent.runtime.handlers;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.strategy.sampling.SamplingResponse;
import com.amazonaws.xray.strategy.sampling.SamplingStrategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.disco.agent.event.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XRayHandlerTest {
    private FakeHandler fakeHandler;

    @Mock
    private SamplingStrategy mockSamplingStrategy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockSamplingStrategy.shouldTrace(any())).thenReturn(new SamplingResponse());
        fakeHandler = new FakeHandler();

        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.standard()
            .withSamplingStrategy(mockSamplingStrategy)
            .build()
        );

        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testRespectUpstreamSamplingDecision() {
        XRayTransactionState state = new XRayTransactionState();
        TraceHeader header = new TraceHeader(null, null, TraceHeader.SampleDecision.SAMPLED);
        state.withTraceheaderString(header.toString());
        boolean decision = fakeHandler.getSamplingDecision(state);

        assertThat(decision).isTrue();
        verify(mockSamplingStrategy, never()).shouldTrace(any());
    }

    @Test
    public void testComputeSamplingDecisionForUnknown() {
        XRayTransactionState state = new XRayTransactionState();
        fakeHandler.getSamplingDecision(state);

        assertThat(state.getTraceHeader()).isNull();
        verify(mockSamplingStrategy, times((1))).shouldTrace(any());
    }

    private static class FakeHandler extends XRayHandler {

        @Override
        public void handleRequest(Event event) {
        }

        @Override
        public void handleResponse(Event event) {
        }
    }
}


