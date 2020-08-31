package com.amazonaws.xray.agent.runtime.handlers;

import software.amazon.disco.agent.event.Event;

public interface XRayHandlerInterface {
    /**
     * Handle the incoming request event. The event should be type checked depending on the type of event
     * the handler is processing. There isn't a common interface yet that relates request/responses in DiSCo,
     * so type checking and handling should be done on a per-handler case.
     * @param event The request event dispatched from the dispatcher.
     */
    void handleRequest(Event event);

    /**
     * Handle the incoming response event. The event should be type checked depending on the type of event
     * the handler is processing. There isn't a common interface yet that relates request/responses in DiSCo,
     * so type checking and handling should be done on a per-handler case.
     *
     * Errors resulting from calls made within the interception is passed to the response event. These should be
     * taken care of in this method.
     * @param event The response event dispatched from the dispatcher.
     */
    void handleResponse(Event event);
}
