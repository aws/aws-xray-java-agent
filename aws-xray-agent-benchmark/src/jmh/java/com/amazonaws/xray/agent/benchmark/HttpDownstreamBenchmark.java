package com.amazonaws.xray.agent.benchmark;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.agent.utils.BenchmarkUtils;
import com.amazonaws.xray.agent.utils.ClientProvider;
import com.amazonaws.xray.agent.utils.SimpleJettyServer;
import com.amazonaws.xray.entities.TraceHeader;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.server.Server;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;

public class HttpDownstreamBenchmark {
    private static final int PORT = 20808;
    private static final String PATH = "/path/to/page";

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        CloseableHttpClient httpClient;
        HttpGet httpGet;
        AWSXRayRecorder recorder;
        Server jettyServer;

        @Setup(Level.Trial)
        public void setup() {
            recorder = BenchmarkUtils.configureXRayRecorder();

            if (System.getProperty("com.amazonaws.xray.sdk") != null) {
                httpClient = ClientProvider.instrumentedApacheHttpClient();
            } else {
                httpClient = ClientProvider.normalApacheHttpClient();
            }
            httpGet = new HttpGet("http://localhost:" + PORT + PATH);

            jettyServer = SimpleJettyServer.create(PORT, PATH);
        }

        @TearDown(Level.Trial)
        public void cleanup() {
            try {
                jettyServer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                jettyServer.destroy();
            }
        }
    }

    /**
     * This benchmark tests the time it takes to make a HTTP request with an Apache client wrapped in an X-Ray Segment.
     * It can be tested with or without the agent running on the JVM. The segment is necessary because we only want to
     * test the performance change introduced by the instrumentation of the Apache client, and without the segment we
     * get CMEs, which do not accurately represent the instrumentation latency.
     *
     * As something of a hack, we add an additional JVM arg pointing to an agent config file in the resources directory
     * that disables the agent's instrumentation of servlet requests. This prevents us from attempting to start a new
     * segment when the Jetty server handles these HTTP requests, which would add latency.
     */
    @Benchmark
    @Fork(jvmArgsAppend = "-Dcom.amazonaws.xray.configFile=/com/amazonaws/xray/agent/agent-config.json")
    public void makeHttpRequest(BenchmarkState state) throws IOException {
        AWSXRay.beginSegment("Benchmark");
        state.httpClient.execute(state.httpGet);
        state.httpGet.releaseConnection();                    // Prevents overwhelming max connections
        state.httpGet.removeHeaders(TraceHeader.HEADER_KEY);  // Prevents Trace ID headers stacking up
        AWSXRay.endSegment();
    }
}
