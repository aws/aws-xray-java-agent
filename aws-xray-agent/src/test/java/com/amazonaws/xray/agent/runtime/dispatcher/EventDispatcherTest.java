package com.amazonaws.xray.agent.runtime.dispatcher;

import com.amazonaws.xray.agent.runtime.handlers.XRayHandlerInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.event.ServiceActivityRequestEvent;
import software.amazon.disco.agent.event.ServiceActivityResponseEvent;
import software.amazon.disco.agent.event.ServiceRequestEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
public class EventDispatcherTest {
    private final String ORIGIN = "testOrigin";
    private final String SERVICE = "testService";
    private final String OPERATION = "testOperation";

    @Mock
    private XRayHandlerInterface mockHandler;

    private EventDispatcher eventDispatcher;

    @Before
    public void setup() {
        eventDispatcher = new EventDispatcher();
        eventDispatcher.addHandler(ORIGIN, mockHandler);
    }

    @Test
    public void testAddHandler() {
        String testOrigin = "SOMEORIGIN";
        ServiceRequestEvent serviceRequestEvent = new ServiceActivityRequestEvent(testOrigin, SERVICE, OPERATION);

        eventDispatcher.dispatchRequestEvent(serviceRequestEvent);
        verify(mockHandler, times(0)).handleRequest(serviceRequestEvent);

        eventDispatcher.addHandler(testOrigin, mockHandler);

        eventDispatcher.dispatchRequestEvent(serviceRequestEvent);
        verify(mockHandler, times(1)).handleRequest(serviceRequestEvent);
    }

    @Test
    public void testDispatchRequest() {
        ServiceActivityRequestEvent serviceRequestEvent = new ServiceActivityRequestEvent(ORIGIN, SERVICE, OPERATION);
        eventDispatcher.dispatchRequestEvent(serviceRequestEvent);
        verify(mockHandler, times(1)).handleRequest(serviceRequestEvent);
    }

    @Test
    public void testDispatchResponse() {
        ServiceActivityRequestEvent serviceRequestEvent = mock(ServiceActivityRequestEvent.class);
        ServiceActivityResponseEvent serviceResponseEvent = new ServiceActivityResponseEvent(ORIGIN, SERVICE, OPERATION, serviceRequestEvent);
        eventDispatcher.dispatchResponseEvent(serviceResponseEvent);
        verify(mockHandler, times(1)).handleResponse(serviceResponseEvent);
    }

    @Test
    public void testDispatchNoHandler() {
        ServiceActivityRequestEvent serviceRequestEvent = new ServiceActivityRequestEvent("NotUsedOrigin", null, null);
        eventDispatcher.dispatchRequestEvent(serviceRequestEvent);
        verify(mockHandler, times(0)).handleRequest(serviceRequestEvent);
    }

}
