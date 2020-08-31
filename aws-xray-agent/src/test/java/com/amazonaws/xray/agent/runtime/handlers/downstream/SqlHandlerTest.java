package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.sql.SqlSubsegments;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.disco.agent.concurrent.TransactionContext;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;

import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for the X-Ray Agent's handler of Disco SQL events.
 * See unit tests for {@link com.amazonaws.xray.sql.SqlSubsegments} for more on expected contents of a SQL subsegment.
 */
public class SqlHandlerTest {
    private static final String DB = "myDB";
    private static final String QUERY = "SQL";
    private static final String DB_URL = "http://example.com";

    private SqlHandler handler;
    private ServiceDownstreamRequestEvent requestEvent;
    private ServiceDownstreamResponseEvent responseEvent;

    @Mock
    Statement mockStatement;

    @Mock
    Connection mockConnection;

    @Mock
    DatabaseMetaData mockMetaData;

    @Before
    public void setup() throws SQLException {
        MockitoAnnotations.initMocks(this);

        when(mockStatement.getConnection()).thenReturn(mockConnection);
        when(mockConnection.getCatalog()).thenReturn(DB);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getURL()).thenReturn(DB_URL);
        when(mockMetaData.getUserName()).thenReturn("USER");
        when(mockMetaData.getDriverVersion()).thenReturn("DRIVER_VERSION");
        when(mockMetaData.getDatabaseProductName()).thenReturn("DB_TYPE");
        when(mockMetaData.getDatabaseProductVersion()).thenReturn("DB_VERSION");

        handler = new SqlHandler();
        TransactionContext.clear();

        requestEvent = (ServiceDownstreamRequestEvent) new ServiceDownstreamRequestEvent("SQL", DB, QUERY)
                .withRequest(mockStatement);
        responseEvent = (ServiceDownstreamResponseEvent) new ServiceDownstreamResponseEvent("SQL", DB, QUERY, requestEvent)
            .withThrown(new SQLException());
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testSubsegmentCreatedWithoutSql() {
        XRaySDKConfiguration.getInstance().init();
        AWSXRay.beginSegment("test");  // must be after config init

        handler.handleRequest(requestEvent);

        Subsegment sqlSub = AWSXRay.getCurrentSubsegment();
        assertThat(sqlSub.isInProgress()).isTrue();
        assertThat(sqlSub.getName()).isEqualTo(DB + "@example.com");
        assertThat(sqlSub.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(sqlSub.getSql()).doesNotContainKey(SqlSubsegments.SANITIZED_QUERY);
    }

    @Test
    public void testSubsegmentCreatedWithSql() {
        URL configFile = SqlHandlerTest.class.getResource("/com/amazonaws/xray/agent/collectSqlConfig.json");
        XRaySDKConfiguration.getInstance().init(configFile);
        AWSXRay.beginSegment("test");  // must be after config init

        handler.handleRequest(requestEvent);

        Subsegment sqlSub = AWSXRay.getCurrentSubsegment();
        assertThat(sqlSub.isInProgress()).isTrue();
        assertThat(sqlSub.getName()).isEqualTo(DB + "@example.com");
        assertThat(sqlSub.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(sqlSub.getSql()).containsEntry(SqlSubsegments.SANITIZED_QUERY, QUERY);
    }

    @Test
    public void testSubsegmentCreatedDespiteException() throws SQLException {
        when(mockStatement.getConnection()).thenThrow(new SQLException());
        XRaySDKConfiguration.getInstance().init();
        AWSXRay.beginSegment("test");  // must be after config init

        handler.handleRequest(requestEvent);

        Subsegment sqlSub = AWSXRay.getCurrentSubsegment();
        assertThat(sqlSub.isInProgress()).isTrue();
        assertThat(sqlSub.getName()).isEqualTo(SqlSubsegments.DEFAULT_DATABASE_NAME);
    }

    @Test
    public void testSubsegmentEndedWithThrowable() {
        XRaySDKConfiguration.getInstance().init();
        Segment seg = AWSXRay.beginSegment("test");  // must be after config init
        Subsegment sub = AWSXRay.beginSubsegment("FakeSqlSub");
        TransactionContext.putMetadata(SqlHandler.SQL_SUBSEGMENT_COUNT_KEY, 1);

        handler.handleResponse(responseEvent);

        assertThat(seg.getSubsegments().size()).isEqualTo(1);
        assertThat(sub.isInProgress()).isFalse();
        assertThat(sub.getCause().getExceptions().size()).isEqualTo(1);
        assertThat(sub.isFault()).isTrue();
    }
}
