package com.amazonaws.xray.agent.handlers.downstream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.http.AmazonHttpClient;
import com.amazonaws.http.apache.client.impl.ConnectionManagerAwareHttpClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.xray.AWSXRayClientBuilder;
import com.amazonaws.services.xray.model.GetSamplingRulesRequest;
import com.amazonaws.services.xray.model.GetSamplingTargetsRequest;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.io.EmptyInputStream;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.reflect.Whitebox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AWSHandlerIT {
    private final static String TRACE_HEADER_KEY = TraceHeader.HEADER_KEY;
    private Segment currentSegment;

    // Adopted from the AWS X-Ray SDK for Java - AWS package unit test.
    // https://github.com/aws/aws-xray-sdk-java/blob/master/aws-xray-recorder-sdk-aws-sdk/src/test/java/com/amazonaws/xray/handlers/TracingHandlerTest.java#L61
    private void mockHttpClient(Object client, String responseContent) {
        AmazonHttpClient amazonHttpClient = new AmazonHttpClient(new ClientConfiguration());
        ConnectionManagerAwareHttpClient apacheHttpClient = mock(ConnectionManagerAwareHttpClient.class);
        HttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        BasicHttpEntity responseBody = new BasicHttpEntity();
        InputStream in = EmptyInputStream.INSTANCE;
        if(null != responseContent && !responseContent.isEmpty()) {
            in = new ByteArrayInputStream(responseContent.getBytes(StandardCharsets.UTF_8));
        }
        responseBody.setContent(in);
        httpResponse.setEntity(responseBody);

        try {
            doReturn(httpResponse).when(apacheHttpClient).execute(any(HttpUriRequest.class), any(HttpContext.class));
        } catch (IOException e) {
            System.err.println("Exception during mock: " + e);
        }

        Whitebox.setInternalState(amazonHttpClient, "httpClient", apacheHttpClient);
        Whitebox.setInternalState(client, "client", amazonHttpClient);
    }


    @Before
    public void setup() {
//        System.setProperty("com.amazonaws.sdk.disableCertChecking", "true"); // TODO remove

        // Generate the segment that would be normally made by the upstream instrumentor
        currentSegment = AWSXRay.beginSegment("awsSegment");
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
        currentSegment = null;
    }

    @Test
    public void testDynamoDBListTable() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        // Acquired by intercepting the HTTP Request and copying the raw JSON.
        String result = "{\"TableNames\":[\"ATestTable\",\"dynamodb-user\",\"some_random_table\",\"scorekeep-game\",\"scorekeep-move\",\"scorekeep-session\",\"scorekeep-state\",\"scorekeep-user\"]}";
        mockHttpClient(client, result);
        client.listTables();

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("AmazonDynamoDBv2", currentSubsegment.getName());
        assertEquals(Namespace.AWS.toString(), currentSubsegment.getNamespace());

        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("ListTables", awsMap.get("operation"));
        assertEquals(8, awsMap.get("table_count"));

        Map<String, String> httpResponseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(200, httpResponseMap.get("status"));
    }

    @Test
    public void testSQSSendMessage() {
        AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
        // XML acquired by intercepting a valid AWS SQS response.
        String result = "<SendMessageResponse xmlns=\"http://queue.amazonaws.com/doc/2012-11-05/\">\n" +
                "\t<SendMessageResult>\n" +
                "\t\t<MessageId>de9e2f1b-aa00-43a6-84b8-b5379085c0f2</MessageId>\n" +
                "\t\t<MD5OfMessageBody>c1ddd94da830e09533d058f67d4ef56a</MD5OfMessageBody>\n" +
                "\t</SendMessageResult>\n" +
                "\t<ResponseMetadata>\n" +
                "\t\t<RequestId>41edd773-d43f-5493-b43b-814e965a23f1</RequestId>\n" +
                "\t</ResponseMetadata>\n" +
                "</SendMessageResponse>";
        mockHttpClient(sqs, result);
        sqs.sendMessage("https://sqs.us-west-2.amazonaws.com/123611858231/xray-queue", "Koo lai ahh");

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("AmazonSQS", currentSubsegment.getName());
        assertEquals(Namespace.AWS.toString(), currentSubsegment.getNamespace());

        // Validate AWS Response
        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("SendMessage", awsMap.get("operation"));
        assertEquals("https://sqs.us-west-2.amazonaws.com/123611858231/xray-queue", awsMap.get("queue_url"));
        assertEquals("41edd773-d43f-5493-b43b-814e965a23f1", awsMap.get("request_id"));
        assertEquals("de9e2f1b-aa00-43a6-84b8-b5379085c0f2", awsMap.get("message_id"));
        assertEquals("[]", awsMap.get("message_attribute_names").toString());

        Map<String, String> httpResponseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(200, httpResponseMap.get("status"));
    }

    @Test
    public void testSNSCreateTopic() {
        AmazonSNS sns = AmazonSNSClientBuilder.defaultClient();
        String result =
                "<CreateTopicResponse xmlns=\"http://sns.amazonaws.com/doc/2010-03-31/\">\n" +
                "  <CreateTopicResult>\n" +
                "    <TopicArn>arn:aws:sns:us-west-2:223528386801:testTopic</TopicArn>\n" +
                "  </CreateTopicResult>\n" +
                "  <ResponseMetadata>\n" +
                "    <RequestId>03e28ac9-f5a1-599b-8b57-dcf4b3ef028d</RequestId>\n" +
                "  </ResponseMetadata>\n" +
                "</CreateTopicResponse>";
        mockHttpClient(sns, result);
        sns.createTopic("testTopic");

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("AmazonSNS", currentSubsegment.getName());
        assertEquals(Namespace.AWS.toString(), currentSubsegment.getNamespace());

        // Validate AWS Response
        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("CreateTopic", awsMap.get("operation"));
        assertEquals("03e28ac9-f5a1-599b-8b57-dcf4b3ef028d", awsMap.get("request_id"));

        Map<String, String> httpResponseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(200, httpResponseMap.get("status"));
    }

    @Test
    public void testS3CreatesHttpClientSubsegment() {
        // We know that S3 isn't supported. But when we do, this test should fail.
        // This is a reminder to add Integ tests to S3 clients.
        // Because the HttpClient handler is enabled, this will generate an httpClient subsegment.
        // Though it's not as informative as an AWS subsegment, this still may provide some insight.
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion("us-west-2").build();
        String data = "testData";
        mockHttpClient(s3, data);
        InputStream stream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(data.getBytes().length);

        PutObjectRequest putRequest = new PutObjectRequest("fake-testing-bucket-jkwjfkdsf", "test.txt", stream, objectMetadata);

        s3.putObject(putRequest);

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("fake-testing-bucket-jkwjfkdsf.s3.us-west-2.amazonaws.com", currentSubsegment.getName());

        Map<String, String> requestMap = (Map<String, String>) currentSubsegment.getHttp().get("request");
        assertEquals("PUT", requestMap.get("method"));
        assertEquals("https://fake-testing-bucket-jkwjfkdsf.s3.us-west-2.amazonaws.com/test.txt", requestMap.get("url"));

        Map<String, String> responseMap = (Map<String, String>) currentSubsegment.getHttp().get("response");
        assertEquals(200, responseMap.get("status"));
    }

    @Test
    public void testLambda() {
        // Setup test
        AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();
        mockHttpClient(lambda, "null"); // Lambda returns "null" on successful fn. with no return value

        InvokeRequest request = new InvokeRequest();
        request.setFunctionName("testFunctionName");
        InvokeResult r = lambda.invoke(request);

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("Invoke", awsMap.get("operation"));
        assertEquals("testFunctionName", awsMap.get("function_name"));
    }

    @Test
    public void testShouldNotTraceXRaySamplingOperations() {
        com.amazonaws.services.xray.AWSXRay xray = AWSXRayClientBuilder.defaultClient();
        mockHttpClient(xray, null);

        xray.getSamplingRules(new GetSamplingRulesRequest());
        assertEquals(0, currentSegment.getSubsegments().size());

        xray.getSamplingTargets(new GetSamplingTargetsRequest());
        assertEquals(0, currentSegment.getSubsegments().size());
    }

    @Test
    public void testTraceHeaderPropagation() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        String result = "{\"TableNames\":[\"ATestTable\",\"dynamodb-user\",\"some_random_table\",\"scorekeep-game\",\"scorekeep-move\",\"scorekeep-session\",\"scorekeep-state\",\"scorekeep-user\"]}";
        mockHttpClient(client, result);
        AmazonHttpClient amazonHttpClient = Whitebox.getInternalState(client, "client");
        HttpClient apacheHttpClient = Whitebox.getInternalState(amazonHttpClient, "httpClient");

        client.listTables();

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Capture the argument passed into the internal http request object
        ArgumentCaptor<HttpPost> requestAccessor = ArgumentCaptor.forClass(HttpPost.class);
        verify(apacheHttpClient).execute(requestAccessor.capture(), any(HttpContext.class));

        // Find the trace header if it was injected.
        HttpRequest request = requestAccessor.getValue();
        String traceHeader = null;
        for (Header h : request.getAllHeaders()) {
            if (h.getName().equals(TRACE_HEADER_KEY)) {
                traceHeader = h.getValue();
                break;
            }
        }

        assertNotNull(traceHeader);

        // Validate the trace header.
        TraceHeader injectedTH = TraceHeader.fromString(traceHeader);
        TraceHeader.SampleDecision ourSampleDecision = currentSegment.isSampled() ?
                TraceHeader.SampleDecision.SAMPLED : TraceHeader.SampleDecision.NOT_SAMPLED;
        TraceHeader ourTraceHeader = new TraceHeader(currentSegment.getTraceId(), currentSubsegment.getId(), ourSampleDecision);

        // The trace header is as expected!
        assertEquals(ourTraceHeader.toString(), injectedTH.toString());
    }

    @Test
    public void testAWSCallsDontGenerateHttpSubsegments() {
        // Ensure that the underlying HttpClient does not get instrumented
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        String result = "{\"TableNames\":[\"ATestTable\",\"dynamodb-user\",\"some_random_table\",\"scorekeep-game\",\"scorekeep-move\",\"scorekeep-session\",\"scorekeep-state\",\"scorekeep-user\"]}";
        mockHttpClient(client, result);
        client.listTables();

        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals(0, currentSubsegment.getSubsegments().size());
    }

    @Test
    public void testAwsClientFailure() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        String result = null;
        mockHttpClient(client, result);
        AmazonHttpClient amazonHttpClient = mock(AmazonHttpClient.class);
        when(amazonHttpClient.execute(any(), any(), any(), any())).thenThrow(new SdkClientException("Fake timeout exception"));
        Whitebox.setInternalState(client, "client", amazonHttpClient);

        try {
            client.listTables();
            assertFalse(true);  // Fail if we get here
        } catch(SdkClientException e) {
            ; // We expect to catch this.
        }

        // Make sure the subsegment has the exception stored.
        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("AmazonDynamoDBv2", currentSubsegment.getName());
        assertEquals(Namespace.AWS.toString(), currentSubsegment.getNamespace());

        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("ListTables", awsMap.get("operation"));

        assertTrue(currentSubsegment.isFault());
        assertEquals(SdkClientException.class, currentSubsegment.getCause().getExceptions().get(0).getThrowable().getClass());
        assertEquals("Fake timeout exception", currentSubsegment.getCause().getExceptions().get(0).getThrowable().getMessage());
    }

    @Test
    public void testAwsServiceFailure() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        String result = null;
        mockHttpClient(client, result);
        AmazonHttpClient amazonHttpClient = mock(AmazonHttpClient.class);
        AmazonDynamoDBException myException = new AmazonDynamoDBException("Failed to get response from DynamoDB");
        myException.setServiceName("AmazonDynamoDBv2");
        myException.setErrorCode("FakeErrorCode");
        myException.setRequestId("FakeRequestId");
        when(amazonHttpClient.execute(any(), any(), any(), any())).thenThrow(myException);
        Whitebox.setInternalState(client, "client", amazonHttpClient);

        try {
            client.listTables();
            assertFalse(true);  // Fail if we get here
        } catch(AmazonServiceException e) {
            ; // We expect to catch this.
        }

        // Make sure the subsegment has the exception stored.
        assertEquals(1, currentSegment.getSubsegments().size());
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertEquals("AmazonDynamoDBv2", currentSubsegment.getName());
        assertEquals(Namespace.AWS.toString(), currentSubsegment.getNamespace());

        Map<String, Object> awsMap = currentSubsegment.getAws();
        assertEquals("ListTables", awsMap.get("operation"));

        assertTrue(currentSubsegment.isFault());
        assertEquals(AmazonDynamoDBException.class, currentSubsegment.getCause().getExceptions().get(0).getThrowable().getClass());
        assertEquals("Failed to get response from DynamoDB (Service: AmazonDynamoDBv2; Status Code: 0; Error Code: FakeErrorCode; Request ID: FakeRequestId)", currentSubsegment.getCause().getExceptions().get(0).getThrowable().getMessage());
    }

}
