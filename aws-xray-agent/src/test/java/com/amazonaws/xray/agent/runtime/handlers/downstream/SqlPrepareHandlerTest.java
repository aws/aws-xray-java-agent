package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;

import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlPrepareHandlerTest {
    private SqlPrepareHandler handler;

    @Mock
    private PreparedStatement preparedStatementMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        handler = new SqlPrepareHandler();
    }

    @Test
    public void testPreparedStatementMapInsertion() {
        String sql = "SELECT * FROM my_table";

        ServiceDownstreamRequestEvent requestEvent = new ServiceDownstreamRequestEvent("origin", "service", sql);
        ServiceDownstreamResponseEvent responseEvent = new ServiceDownstreamResponseEvent("origin", "service", sql, requestEvent);
        responseEvent.withResponse(preparedStatementMock);

        handler.handleResponse(responseEvent);
        assertThat(XRayTransactionState.getPreparedQuery(preparedStatementMock)).isEqualTo(sql);
    }
}
