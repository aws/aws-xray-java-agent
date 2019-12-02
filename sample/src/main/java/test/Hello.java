package test;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

// Import the Agent installer (Lambda Only)
import com.amazonaws.xray.agent.XRayAgentInstaller;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import java.io.File;

// The SDK here is optional
// import com.amazonaws.xray.AWSXRay;

public class Hello implements RequestHandler<Object, String> {

    // Here we define the segment name the Agent will use to publish.
    // It is very important that this is executed as soon as possible.
    static {
        System.out.println("Starting AWS clients tests...");
        XRayAgentInstaller.installInLambda("servicename=" + System.getenv("AWS_LAMBDA_FUNCTION_NAME"));
    }

    static final String DYNAMO_TABLE_NAME = "XRayJavaAgentSampleTable";
    static final String LAMBDA2_FCN_NAME = "XRayJavaAgentLambda2";
    static final String SQS_QUEUE_NAME = "XRayJavaAgentSampleQueue";
    static final String S3_BUCKET_KEY = "SOURCE_BUCKET";
    static final String S3_BUCKET_NAME = System.getenv(S3_BUCKET_KEY);

    private void testAWSClientInstrumentation() {
        // We're going to use a variety of clients to show the auto-instrumentation
        // feature of the Agent
        AmazonDynamoDB dynamo_client = AmazonDynamoDBClientBuilder.defaultClient();
        AmazonSQS sqs_client = AmazonSQSClientBuilder.defaultClient();
        AmazonS3 s3_client = AmazonS3ClientBuilder.defaultClient();
        AWSLambda lambda_client = AWSLambdaClientBuilder.defaultClient();

        System.out.println("Starting Dynamo client test...");

        // DynamoDB Test
        ScanRequest scanRequest = new ScanRequest().withTableName(DYNAMO_TABLE_NAME);
        ScanResult ddbResult = dynamo_client.scan(scanRequest);
        System.out.println(ddbResult);

        System.out.println("Starting SQS client test...");

        // SQS Test
        String queue_url = sqs_client.getQueueUrl(SQS_QUEUE_NAME).getQueueUrl();
        SendMessageRequest send_msg_request = new SendMessageRequest()
            .withQueueUrl(queue_url)
            .withMessageBody("Test here")
            .withDelaySeconds(5);
        SendMessageResult sqsResult = sqs_client.sendMessage(send_msg_request);
        System.out.println(sqsResult);

        System.out.println("Starting S3 client test...");

        // S3 Test
        try {
            File file = File.createTempFile("test", ".txt");
            file.deleteOnExit();
            Writer writer = new OutputStreamWriter(new FileOutputStream(file));
            writer.write("test file");
            writer.close();

            PutObjectRequest putRequest = new PutObjectRequest(S3_BUCKET_NAME, "agent/test.txt", file);
            PutObjectResult s3Result = s3_client.putObject(putRequest);
            System.out.println(s3Result);

        } catch(IOException e) {
            System.out.println("Unable to test S3" + e.toString());
        }

        System.out.println("Starting Lambda client test...");

        if (!System.getenv("AWS_LAMBDA_FUNCTION_NAME").equals(LAMBDA2_FCN_NAME)) {
            // Lambda Test
            InvokeRequest invokeRequest = new InvokeRequest();
            invokeRequest.setFunctionName(LAMBDA2_FCN_NAME);
            InvokeResult lambda_result = lambda_client.invoke(invokeRequest);
            String data = new String(lambda_result.getPayload().array(), StandardCharsets.UTF_8);
            System.out.println("lambda - sendPollMessage() - " + data);
        }
    }

    public String handleRequest(Object input, Context context) {
        System.out.println("Lambda Request Handler...");
        testAWSClientInstrumentation();
        return "Done!";
    }
}