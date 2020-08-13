package com.amazonaws.xray.agent.runtime.listeners;

import software.amazon.disco.agent.event.Listener;

/**
 * Factory interface that produces a listener that can be used by Disco Agents to intercept relevant
 * events on the Disco event bus.
 */
public interface ListenerFactory {

    /**
     * Creates a Disco Event Bus listener but does not attach it to the event bus.
     *
     * @return the created Disco listener
     */
    Listener generateListener();
}
