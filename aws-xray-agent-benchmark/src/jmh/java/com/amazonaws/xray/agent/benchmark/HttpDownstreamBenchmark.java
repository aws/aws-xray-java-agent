package com.amazonaws.xray.agent.benchmark;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.agent.utils.ClientProvider;
import com.amazonaws.xray.agent.utils.NoOpEmitter;
import com.amazonaws.xray.agent.utils.SimpleJettyServer;
import com.amazonaws.xray.entities.TraceHeader;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.server.Server;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
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
            // This initializes the static recorder if the agent is not present, and ensures we don't actually emit
            // segments regardless of whether or not we're using the agent
            AWSXRay.getGlobalRecorder().setEmitter(new NoOpEmitter());
            recorder = AWSXRay.getGlobalRecorder();

            if (System.getProperty("com.amazonaws.xray.sdk") != null) {
                System.out.println("SDK Client is used");
                httpClient = ClientProvider.instrumentedApacheHttpClient();
            } else {
                System.out.println("Uninstrumented Client is used");
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
     */
    @Benchmark
    public void makeHttpRequest(BenchmarkState state) throws IOException {
        AWSXRay.beginSegment("Benchmark");
        state.httpClient.execute(state.httpGet);
        state.httpGet.releaseConnection();                    // Prevents overwhelming max connections
        state.httpGet.removeHeaders(TraceHeader.HEADER_KEY);  // Prevents Trace ID headers stacking up
        AWSXRay.endSegment();
    }
}
