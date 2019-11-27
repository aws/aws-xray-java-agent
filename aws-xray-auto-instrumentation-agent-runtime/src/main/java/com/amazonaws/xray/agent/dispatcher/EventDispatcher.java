package com.amazonaws.xray.agent.dispatcher;

import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import com.amazonaws.xray.agent.handlers.XRayHandlerInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * The dispatcher is the gateway between the listener and the  handlers. It acts as the multiplexor
 * that delegates events to a handler, based solely on its origin. The event dispatcher should be
 * instantiated to represent the downstream or upstream dispatcher.
 */
public class EventDispatcher {
    /**
     * log4j logger for log messages.
     */
    private static final Logger LOG = LogManager.getLogger(EventDispatcher.class);

    /**
     * Map that holds a reference between the origin and its handler
     */
    private Map<String, XRayHandlerInterface> originHandlerMap;

    public EventDispatcher() {
        originHandlerMap = new HashMap<>();
    }

    /**
     * Add a handler for a given origin. This handler is executed when an event is dispatched to it.
     * @param origin The event origin that corresponds to the handler
     * @param handler The handler that is executed for the given event origin.
     */
    public void addHandler(String origin, XRayHandlerInterface handler) {
        originHandlerMap.put(origin, handler);
    }

    /**
     * Helper method to acquire the appropriate handler given the event; the origin is extracted from
     * this event to determine which handler to execute.
     * @param event Incoming event to acquire the handler.
     * @return The handler to execute, otherwise null if no handler exists for the event.
     */
    private XRayHandlerInterface getHandler(Event event) {
        String eventOrigin = event.getOrigin();

        XRayHandlerInterface xrayHandler = originHandlerMap.get(eventOrigin);
        if (xrayHandler == null) {
            String logMessage = String.format("Unable to retrieve a handler from event [%s] and origin [%s].", event.toString(), event.getOrigin());
            LOG.error(logMessage);
        }
        return xrayHandler;

    }

    /**
     * Dispatches the request event to its corresponding handler if one exists.
     * If it doesn't, this method doesn't do anything.
     * @param event The request event to dispatch to its underlying handler if one exists
     */
    public void dispatchRequestEvent(Event event) {
        XRayHandlerInterface xrayHandler = getHandler(event);
        if (xrayHandler != null) {
            xrayHandler.handleRequest(event);
        }
    }

    /**
     * Dispatches the response event to its corresponding handler if one exists.
     * If it doesn't, this method doesn't do anything.
     * @param event The response event to dispatch to its underlying handler if one exists
     */
    public void dispatchResponseEvent(Event event) {
        XRayHandlerInterface xrayHandler = getHandler(event);
        if (xrayHandler != null) {
            xrayHandler.handleResponse(event);
        }
    }
}
