package com.amazonaws.xray.agent.runtime.listeners;

import com.amazonaws.xray.agent.runtime.dispatcher.EventDispatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.event.HttpServletNetworkRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkResponseEvent;
import software.amazon.disco.agent.event.ServiceActivityRequestEvent;
import software.amazon.disco.agent.event.ServiceActivityResponseEvent;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;
import software.amazon.disco.agent.event.TransactionBeginEvent;
import software.amazon.disco.agent.event.TransactionEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
public class XRayListenerTest {
    private final String ORIGIN = "TestOrigin";
    private final String SERVICE = "TestService";
    private final String OPERATION = "TestOperation";

    @Mock
    private EventDispatcher upstreamDispatcher;

    @Mock
    private EventDispatcher downstreamDispatcher;

    private XRayListener xRayListener;

    @Before
    public void setup() {
        xRayListener = new XRayListener(upstreamDispatcher, downstreamDispatcher);
    }

    @Test
    public void testGetPriority() {
        Assert.assertEquals(0, xRayListener.getPriority());
    }

    @Test
    public void testListenUpstream() {
        ServiceActivityRequestEvent upstreamRequestEvent = new ServiceActivityRequestEvent(ORIGIN, SERVICE, OPERATION);
        ServiceActivityResponseEvent upstreamResponseEvent = new ServiceActivityResponseEvent(ORIGIN, SERVICE, OPERATION, upstreamRequestEvent);

        xRayListener.listen(upstreamRequestEvent);
        verify(upstreamDispatcher, times(1)).dispatchRequestEvent(upstreamRequestEvent);
        verify(upstreamDispatcher, times(0)).dispatchResponseEvent(upstreamResponseEvent);

        xRayListener.listen(upstreamResponseEvent);
        verify(upstreamDispatcher, times(1)).dispatchRequestEvent(upstreamRequestEvent);
        verify(upstreamDispatcher, times(1)).dispatchResponseEvent(upstreamResponseEvent);

        verify(downstreamDispatcher, times(0)).dispatchResponseEvent(upstreamRequestEvent);
        verify(downstreamDispatcher, times(0)).dispatchResponseEvent(upstreamResponseEvent);
    }

    @Test
    public void testListenDownstream() {
        ServiceDownstreamRequestEvent downstreamRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        ServiceDownstreamResponseEvent downstreamResponseEvent = new ServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, downstreamRequestEvent);

        xRayListener.listen(downstreamRequestEvent);
        verify(downstreamDispatcher, times(1)).dispatchRequestEvent(downstreamRequestEvent);
        verify(downstreamDispatcher, times(0)).dispatchResponseEvent(downstreamResponseEvent);

        xRayListener.listen(downstreamResponseEvent);
        verify(downstreamDispatcher, times(1)).dispatchRequestEvent(downstreamRequestEvent);
        verify(downstreamDispatcher, times(1)).dispatchResponseEvent(downstreamResponseEvent);

        verify(upstreamDispatcher, times(0)).dispatchResponseEvent(downstreamRequestEvent);
        verify(upstreamDispatcher, times(0)).dispatchResponseEvent(downstreamResponseEvent);
    }

    @Test
    public void testValidEvents() {
        ServiceActivityRequestEvent serviceRequestEvent = new ServiceActivityRequestEvent(ORIGIN, SERVICE, OPERATION);
        xRayListener.listen(serviceRequestEvent);
        verify(upstreamDispatcher, times(1)).dispatchRequestEvent(serviceRequestEvent);

        ServiceActivityResponseEvent serviceResponseEvent = new ServiceActivityResponseEvent(ORIGIN, SERVICE, OPERATION, serviceRequestEvent);
        xRayListener.listen(serviceResponseEvent);
        verify(upstreamDispatcher, times(1)).dispatchResponseEvent(serviceResponseEvent);

        HttpServletNetworkRequestEvent httpRequestEvent = new HttpServletNetworkRequestEvent(ORIGIN, 0, 0 ,"", "");
        xRayListener.listen(httpRequestEvent);
        verify(upstreamDispatcher, times(1)).dispatchRequestEvent(httpRequestEvent);

        HttpServletNetworkResponseEvent httpResponseEvent = new HttpServletNetworkResponseEvent(ORIGIN, httpRequestEvent);
        xRayListener.listen(httpResponseEvent);
        verify(upstreamDispatcher, times(1)).dispatchResponseEvent(httpResponseEvent);

        ServiceDownstreamRequestEvent downstreamRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);
        xRayListener.listen(downstreamRequestEvent);
        verify(downstreamDispatcher, times(1)).dispatchRequestEvent(downstreamRequestEvent);

        ServiceDownstreamResponseEvent downstreamResponseEvent = new ServiceDownstreamResponseEvent(ORIGIN, SERVICE, OPERATION, downstreamRequestEvent);
        xRayListener.listen(downstreamResponseEvent);
        verify(downstreamDispatcher, times(1)).dispatchResponseEvent(downstreamResponseEvent);

    }

    @Test
    public void testInvalidEvent() {
        TransactionEvent invalidEvent = new TransactionBeginEvent(ORIGIN);

        xRayListener.listen(invalidEvent);

        verify(downstreamDispatcher, times(0)).dispatchResponseEvent(any());
        verify(downstreamDispatcher, times(0)).dispatchResponseEvent(any());
        verify(upstreamDispatcher, times(0)).dispatchResponseEvent(any());
        verify(upstreamDispatcher, times(0)).dispatchResponseEvent(any());
    }

    @Test
    public void testDispatcherException() {
        // We expect the listener to be a catch-all for any exception,
        // so ultimately, it shouldn't throw exceptions and mess up application code.
        doThrow(new RuntimeException("Test Exception")).when(downstreamDispatcher).dispatchRequestEvent(any());
        ServiceDownstreamRequestEvent downstreamRequestEvent = new ServiceDownstreamRequestEvent(ORIGIN, SERVICE, OPERATION);

        verify(downstreamDispatcher, times(0)).dispatchRequestEvent(any());
        xRayListener.listen(downstreamRequestEvent);
        verify(downstreamDispatcher, times(1)).dispatchRequestEvent(any());
    }
}
