package com.amazonaws.xray.agent.benchmark;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.agent.utils.BenchmarkUtils;
import com.amazonaws.xray.agent.utils.ClientProvider;
import com.amazonaws.xray.agent.utils.SimpleJettyServer;
import org.eclipse.jetty.server.Server;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * For the AWS SDK benchmarks, we use the same trick as in {@link HttpDownstreamBenchmark} to stop the Agent from
 * attempting to create segments for our jetty server and mess up the timing.
 */
@Fork(jvmArgsAppend = "-Dcom.amazonaws.xray.configFile=/com/amazonaws/xray/agent/agent-config.json")
public class AwsSdkBenchmark {
    private static final int PORT = 20808;
    private static final String PATH = "/";  // path for ListTables API

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        AmazonDynamoDB v1Client;
        DynamoDbClient v2Client;
        AWSXRayRecorder recorder;
        Server jettyServer;

        @Setup(Level.Trial)
        public void setup() {
            recorder = BenchmarkUtils.configureXRayRecorder();

            if (System.getProperty("com.amazonaws.xray.sdk") != null) {
                v1Client = ClientProvider.instrumentedV1Client(recorder);
                v2Client = ClientProvider.instrumentedV2Client();
            } else {
                v1Client = ClientProvider.normalV1Client();
                v2Client = ClientProvider.normalV2Client();
            }

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

    @Benchmark
    public void awsV1Request(BenchmarkState state) {
        AWSXRay.beginSegment("Benchmark");
        state.v1Client.listTables();
        AWSXRay.endSegment();
    }

    @Benchmark
    public void awsV2Request(BenchmarkState state) {
        AWSXRay.beginSegment("Benchmark");
        state.v2Client.listTables();
        AWSXRay.endSegment();
    }
}
