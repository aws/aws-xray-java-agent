package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.handlers.XRayHandler;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.sql.SqlSubsegments;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.disco.agent.concurrent.TransactionContext;
import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates fully populated subsegments to represent downstream SQL queries.
 */
public class SqlHandler extends XRayHandler {
    private static final Log log = LogFactory.getLog(SqlHandler.class);

    // Visible for testing
    static final String SQL_SUBSEGMENT_COUNT_KEY = "XRaySQLSubsegmentCount";

    /**
     * Uses the JDBC Statement from the Disco event to retrieve metadata about this query. Begins a subsegment
     * using that metadata.
     *
     * @param event The request event dispatched from the dispatcher.
     */
    @Override
    public void handleRequest(Event event) {
        // If a parent SQL transaction is already in progress, we return to avoid an infinite loop. This is because
        // in order to populate a SQL subsegment, we make several calls to the JDBC Driver's DatabaseMetaData object.
        // For example, if a driver's implementation of DatabaseMetaData.getUserName() uses executeQuery("SELECT USER")
        // to get the DB user, executeQuery would be intercepted by the Disco JDBC plugin, trigger this handler to
        // create subegment, and we'd call getUserName to populate that subsegment and so on.
        if (incrementSqlTransactionCount() > 1) {
            return;
        }

        ServiceDownstreamRequestEvent requestEvent = (ServiceDownstreamRequestEvent) event;
        Statement statement = (Statement) requestEvent.getRequest();
        String queryString = requestEvent.getOperation();
        boolean recordSql = XRaySDKConfiguration.getInstance().shouldCollectSqlQueries();
        final Connection connection;

        try {
            connection = statement.getConnection();
        } catch (SQLException e) {
            log.debug("Encountered exception when creating subsegment for query of "
                    + requestEvent.getService() + ", starting blank subsegment", e);
            AWSXRay.beginSubsegment(SqlSubsegments.DEFAULT_DATABASE_NAME);
            return;
        }

        // If the query string wasn't provided by current DiSCo event, check the preparedMap cache
        if (queryString == null && statement instanceof PreparedStatement) {
            queryString = XRayTransactionState.getPreparedQuery((PreparedStatement) statement);
        }

        // If user opted-in to record their Queries, include them in the subsegment
        SqlSubsegments.forQuery(connection, recordSql ? queryString : null);
    }

    /**
     * Closes the subsegment representing this SQL query, including the exception if one was raised.
     *
     * @param event The response event dispatched from the dispatcher.
     */
    @Override
    public void handleResponse(Event event) {
        // If this SQL request is being ignored, we should also ignore the response
        if (decrementSqlTransactionCount() > 0) {
            return;
        }

        Subsegment subsegment = getSubsegment();
        ServiceDownstreamResponseEvent responseEvent = (ServiceDownstreamResponseEvent) event;
        Throwable thrown = responseEvent.getThrown();

        if (thrown != null) {
            subsegment.addException(thrown);
        }
        endSubsegment();
    }

    // Visible for testing
    synchronized int getSqlTransactionCount() {
        Integer count = (Integer) TransactionContext.getMetadata(SQL_SUBSEGMENT_COUNT_KEY);
        if (count == null) {
            count = 0;
            setSqlTransactionCount(count);
        }

        return count;
    }

    private synchronized int incrementSqlTransactionCount() {
        int count = getSqlTransactionCount() + 1;
        TransactionContext.putMetadata(SQL_SUBSEGMENT_COUNT_KEY, count);
        return count;
    }

    private synchronized int decrementSqlTransactionCount() {
        int count = getSqlTransactionCount() - 1;
        TransactionContext.putMetadata(SQL_SUBSEGMENT_COUNT_KEY, count);
        return count;
    }

    // Visible for testing
    synchronized void setSqlTransactionCount(int val) {
        TransactionContext.putMetadata(SQL_SUBSEGMENT_COUNT_KEY, val);
    }
}
