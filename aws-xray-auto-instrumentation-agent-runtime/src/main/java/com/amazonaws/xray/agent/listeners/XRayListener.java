package com.amazonaws.xray.agent.listeners;

import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.HttpServletNetworkRequestEvent;
import software.amazon.disco.agent.event.HttpServletNetworkResponseEvent;
import software.amazon.disco.agent.event.Listener;
import software.amazon.disco.agent.event.ServiceEvent;
import software.amazon.disco.agent.event.ServiceRequestEvent;
import software.amazon.disco.agent.event.ServiceResponseEvent;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import com.amazonaws.xray.agent.dispatcher.EventDispatcher;


public class XRayListener implements Listener {
    /**
     * log4j logger for log messages.
     */
    private static final Logger LOG = LogManager.getLogger(XRayListener.class);

    private final EventDispatcher upstreamEventDispatcher;
    private final EventDispatcher downstreamEventDispatcher;

    public XRayListener(EventDispatcher upstreamEventDispatcher, EventDispatcher downstreamEventDispatcher) {
        this.upstreamEventDispatcher = upstreamEventDispatcher;
        this.downstreamEventDispatcher = downstreamEventDispatcher;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void listen(Event event) {
        try {
            EventDispatcher dispatcher = isEventDownstream(event) ? downstreamEventDispatcher : upstreamEventDispatcher;

            if (event instanceof ServiceRequestEvent || event instanceof HttpServletNetworkRequestEvent) {
                dispatcher.dispatchRequestEvent(event);
            } else if (event instanceof ServiceResponseEvent || event instanceof HttpServletNetworkResponseEvent) {
                dispatcher.dispatchResponseEvent(event);
            } else {
                // Other events we don't care about so return.
                return;
            }
        } catch (Exception e) {
            // We dont want to propagate any exceptions back to the bus nor the application code, so we
            // just log it and continue.
            LOG.error("The X-Ray Agent had encountered an unexpected exception for the following event: "
                    + event.toString(), e);
        }
    }

    /**
     * Use the downstream or upstream event dispatcher depending on the type of event.
     *
     * Upstream dispatchers are invoked as a result of an incoming request in a service. They generally run handlers
     * that generate segments, populate them with request metadata, and end them.
     *
     * Downstream dispatchers usually deal with events relating to client-sided, send requests and run handlers
     * that generate subsegments, populate them with metadata, and then end the subsegments.
     */
    private boolean isEventDownstream(Event e) {
        if (e instanceof ServiceEvent) {
            ServiceEvent serviceEvent = (ServiceEvent) e;
            return serviceEvent.getType() == ServiceEvent.Type.DOWNSTREAM;
        }
        return false;
    }
}
