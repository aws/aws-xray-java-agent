package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.handlers.downstream.source.sql.MyConnectionImpl;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.sql.SqlSubsegments;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


public class SqlPrepareHandlerIntegTest {
    private static final String SQL = "SELECT * FROM my_table";
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private CallableStatement callableStatement;

    @Mock
    private DatabaseMetaData mockMetadata;

    @Before
    public void setup() throws SQLException {
        MockitoAnnotations.initMocks(this);
        connection = new MyConnectionImpl(preparedStatement, callableStatement, mockMetadata);
        when(preparedStatement.getConnection()).thenReturn(connection);
        when(preparedStatement.toString()).thenReturn(null);
        when(callableStatement.getConnection()).thenReturn(connection);
        when(callableStatement.toString()).thenReturn(null);
        when(mockMetadata.getURL()).thenReturn("http://example.com");
        when(mockMetadata.getUserName()).thenReturn("user");
        when(mockMetadata.getDriverVersion()).thenReturn("1.0");
        when(mockMetadata.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMetadata.getDatabaseProductVersion()).thenReturn("2.0");

        AWSXRay.beginSegment("test");
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testPreparedStatementQueryCaptured() throws SQLException {
        connection.prepareStatement(SQL);  // insert into map
        preparedStatement.execute();

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        assertThat(sub.getSql()).containsEntry(SqlSubsegments.SANITIZED_QUERY, SQL);
    }

    @Test
    public void testCallableStatementQueryCaptured() throws SQLException {
        connection.prepareCall(SQL);  // insert into map
        callableStatement.execute();

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        assertThat(sub.getSql()).containsEntry(SqlSubsegments.SANITIZED_QUERY, SQL);
    }
}
