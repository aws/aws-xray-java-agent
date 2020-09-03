package com.amazonaws.xray.agent.utils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.handlers.TracingHandler;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.proxies.apache.http.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;

/**
 * Helper class to create various types of instrumented and non-instrumented clients for use in benchmark tests,
 * so that we can ensure the tests are exactly the same and the only variable is the type (or lack) of instrumentation
 * used.
 */
public final class ClientProvider {
    private static final String ENDPOINT = "http://localhost:20808";

    public static CloseableHttpClient normalApacheHttpClient() {
        return HttpClients.createDefault();
    }

    public static CloseableHttpClient instrumentedApacheHttpClient() {
        return HttpClientBuilder.create().build();
    }

    public static HttpServlet normalHttpServlet() {
        return new NormalServlet();
    }

    public static HttpServlet instrumentedHttpServlet() {
        return new InstrumentedServlet();
    }

    public static AmazonDynamoDB normalV1Client() {
        return (AmazonDynamoDB) getTestableV1Client(AmazonDynamoDBClientBuilder.standard()).build();
    }

    public static AmazonDynamoDB instrumentedV1Client(AWSXRayRecorder recorder) {
        return (AmazonDynamoDB) getTestableV1Client(AmazonDynamoDBClientBuilder.standard())
                .withRequestHandlers(new TracingHandler(recorder))
                .build();
    }

    public static DynamoDbClient normalV2Client() {
        SdkClientBuilder builder = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
                .region(Region.US_WEST_2);

        return (DynamoDbClient) getTestableV2Client(builder, false).build();
    }

    public static DynamoDbClient instrumentedV2Client() {
        SdkClientBuilder builder = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
                .region(Region.US_WEST_2);

        return (DynamoDbClient) getTestableV2Client(builder, true).build();
    }

    private static AwsClientBuilder getTestableV1Client(AwsClientBuilder builder) {
        AWSCredentialsProvider fakeCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("fake", "fake"));
        AwsClientBuilder.EndpointConfiguration mockEndpoint = new AwsClientBuilder.EndpointConfiguration(ENDPOINT, "us-west-2");
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxErrorRetry(0);
        clientConfiguration.setRequestTimeout(1000);

        return builder
                .withEndpointConfiguration(mockEndpoint)
                .withCredentials(fakeCredentials)
                .withClientConfiguration(clientConfiguration);
    }

    private static SdkClientBuilder getTestableV2Client(SdkClientBuilder builder, boolean instrumented) {
        ClientOverrideConfiguration.Builder configBuilder = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(1));

        if (instrumented) {
            configBuilder.addExecutionInterceptor(new TracingInterceptor());
        }

        return builder
                .overrideConfiguration(configBuilder.build())
                .endpointOverride(URI.create(ENDPOINT));
    }

    /**
     * Simple servlet class that just waits 2 milliseconds to service a request then responds. Will be invoked by the "service"
     * method, which is instrumented by the Agent. No point in manipulating response since it's a mock.
     */
    private static class NormalServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            try {
                Thread.sleep(2);
            } catch (Exception e) {
            }
        }
    }

    /**
     * "Instrumented" servlet class that simulates the X-Ray SDK's filter by calling the pre-filter and post-filter
     * methods around the activity of the servlet.
     */
    private static class InstrumentedServlet extends HttpServlet {
        private AWSXRayServletFilter filter = new AWSXRayServletFilter("Benchmark");

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            filter.preFilter(request, response);

            try {
                Thread.sleep(2);
            } catch (Exception e) {
            }

            filter.postFilter(request, response);
        }
    }
}
