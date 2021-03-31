package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.agent.runtime.handlers.XRayHandler;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;

import java.sql.PreparedStatement;

/**
 * This handler processes DiSCo events with origin "SqlPrepare." Such events are emitted when preparing
 * a statement or call to a remote SQL server using the {@code connection.prepareStatement()} or
 * {@code connection.prepareCall()} JDBC methods. We do NOT generate X-Ray entities here, we just store metadata
 * for later use.
 */
public class SqlPrepareHandler extends XRayHandler {
    /**
     * No-op because we can only meaningfully record the metadata when we have a reference to the returned statement.
     *
     * @param event The request event dispatched from the dispatcher.
     */
    @Override
    public void handleRequest(Event event) {
    }

    /**
     * Stores the captured query string in a static map for later use by the SQL execution handler.
     *
     * @param event The response event dispatched from the dispatcher.
     */
    @Override
    public void handleResponse(Event event) {
        ServiceDownstreamResponseEvent responseEvent = (ServiceDownstreamResponseEvent) event;
        if (responseEvent != null &&
            responseEvent.getOperation() != null &&
            responseEvent.getResponse() instanceof PreparedStatement)
        {
            XRayTransactionState
                    .putPreparedQuery((PreparedStatement) responseEvent.getResponse(), responseEvent.getOperation());
        }
    }
}
