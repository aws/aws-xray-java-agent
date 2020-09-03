package com.amazonaws.xray.agent.benchmark;

import com.amazonaws.xray.agent.utils.BenchmarkUtils;
import com.amazonaws.xray.agent.utils.ClientProvider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.Mockito.when;

public class ServletBenchmark {
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        HttpServlet servlet;

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

        @Setup(Level.Trial)
        public void setup() {
            MockitoAnnotations.initMocks(this);
            BenchmarkUtils.configureXRayRecorder();

            when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://example.com"));
            when(servletRequest.getMethod()).thenReturn("GET");
            when(servletResponse.getStatus()).thenReturn(200);

            if (System.getProperty("com.amazonaws.xray.sdk") != null) {
                servlet = ClientProvider.instrumentedHttpServlet();
            } else {
                servlet = ClientProvider.normalHttpServlet();
            }
        }
    }

    @Benchmark
    public void serviceRequest(BenchmarkState state) throws IOException, ServletException {
        state.servlet.service(state.servletRequest, state.servletResponse);
    }
}
