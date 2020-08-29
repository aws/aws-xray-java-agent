package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.runtime.handlers.downstream.source.sql.MyStatementImpl;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.sql.SqlSubsegments;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integ testing class for SQL calls. We use a stubbed out implementation of the JDBC Statement classes rather
 * than mocks because mocking them interferes with Disco interception of their methods.
 */
public class SqlHandlerIntegTest {
    private static final String QUERY = "SQL";
    private static final String DB = "MY_DB";
    private static final String DB_URL = "http://example.com";
    private static final String DOMAIN = "example.com";

    private Statement statement;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private CallableStatement callableStatement;

    @Mock
    private Connection mockConnection;

    @Mock
    private DatabaseMetaData mockMetaData;

    @Before
    public void setup() throws SQLException {
        MockitoAnnotations.initMocks(this);

        when(mockConnection.getCatalog()).thenReturn(DB);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);

        when(preparedStatement.getConnection()).thenReturn(mockConnection);
        when(callableStatement.getConnection()).thenReturn(mockConnection);

        when(mockMetaData.getURL()).thenReturn(DB_URL);
        when(mockMetaData.getUserName()).thenReturn("user");
        when(mockMetaData.getDriverVersion()).thenReturn("1.0");
        when(mockMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMetaData.getDatabaseProductVersion()).thenReturn("2.0");

        statement = new MyStatementImpl(mockConnection);

        AWSXRay.beginSegment("test");
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testStatementCaptured() throws SQLException {
        statement.executeQuery(QUERY);

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        verifySubsegment(sub);
    }

    @Test
    public void testPreparedStatementCaptured() throws SQLException {
        preparedStatement.execute();

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        verifySubsegment(sub);
    }

    @Test
    public void testCallableStatementCaptured() throws SQLException {
        callableStatement.execute();

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        verifySubsegment(sub);
    }

    @Test
    public void testSeveralQueriesOnStatement() throws SQLException {
        statement.execute(QUERY);
        statement.executeUpdate(QUERY);
        statement.executeQuery(QUERY);
        statement.executeLargeUpdate(QUERY);

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(4);

        for (Subsegment sub : AWSXRay.getCurrentSegment().getSubsegments()) {
            verifySubsegment(sub);
        }
    }

    @Test
    public void testExceptionIsRecordedAndThrown() {
        assertThatThrownBy(() -> {
            statement.executeUpdate(QUERY, 0);  // Implemented to throw exception
        }).isInstanceOf(SQLException.class);

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        verifySubsegment(sub);
        assertThat(sub.getCause().getExceptions()).hasSize(1);
    }

    @Test
    public void testInternalQueriesDontGetCaptured() throws SQLException {
        // Will be called by SqlSubsegments.forQuery
        String user = "username";
        when(mockMetaData.getUserName()).thenAnswer(new AnswerUsingQuery(statement, user));

        statement.executeQuery(QUERY);

        assertThat(AWSXRay.getCurrentSegment().getSubsegments()).hasSize(1);
        Subsegment sub = AWSXRay.getCurrentSegment().getSubsegments().get(0);
        assertThat(sub.getSql()).containsEntry(SqlSubsegments.USER, user);
        verifySubsegment(sub);
    }

    private void verifySubsegment(Subsegment sub) {
        assertThat(sub.getName()).isEqualTo(DB + "@" + DOMAIN);
        assertThat(sub.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(sub.getSql()).containsEntry(SqlSubsegments.URL, DB_URL);  // checking an arbitrary field in SQL namespace
        assertThat(sub.isInProgress()).isFalse();
    }

    /**
     * "Helper" class to simulate a getUserName() method whose internal implementation makes a query
     */
    private static class AnswerUsingQuery implements Answer<String> {
        private final Statement statement;
        private final String user;

        AnswerUsingQuery(Statement statement, String user) {
            this.statement = statement;
            this.user = user;
        }

        @Override
        public String answer(InvocationOnMock invocationOnMock) throws Throwable {
            statement.executeQuery("SELECT USER");
            return user;
        }
    }
}
