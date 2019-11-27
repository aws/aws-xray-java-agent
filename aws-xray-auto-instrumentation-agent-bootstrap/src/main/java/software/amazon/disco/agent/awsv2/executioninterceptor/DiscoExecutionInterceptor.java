package software.amazon.disco.agent.awsv2.executioninterceptor;

import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.ExecutionAttributesAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.FailedExecutionContextAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.InterceptorContextAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest.SdkHttpRequestAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest.SdkRequestAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkResponseAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkHttpResponseAccessor;
import software.amazon.disco.agent.event.AwsServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.AwsServiceDownstreamRequestEventImpl;
import software.amazon.disco.agent.event.AwsServiceDownstreamResponseEvent;
import software.amazon.disco.agent.event.AwsServiceDownstreamResponseEventImpl;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.reflect.concurrent.TransactionContext;
import software.amazon.disco.agent.reflect.event.EventBus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This is directly based on the Execution Interceptor interface in the AWS SDK V2
 * https://github.com/aws/aws-sdk-java-v2/blob/master/core/sdk-core/src/main/java/software/amazon/awssdk/core/interceptor/ExecutionInterceptor.java
 *
 * In order for the method to be invoked, the following criteria need to align:
 *  - The return types need to align if they both return void.
 *  - The Arguments in this interceptor should at least be an Object (although accessors are better) and correspond
 *  to the interface objects by count.
 *
 *  On a successful call the ordering of method calls is:
 *  beforeExecution to modifyHttpRequest to beforeTransmission to afterExecution
 *
 * On an unsuccessful execution, the ordering method calls is:
 * beforeExecution - ... -- onExecutionFailure
 * Meaning that it could fail on any portion of the call chain.
 *
 */
public class DiscoExecutionInterceptor {
    /**
     * Common header keys to retrieve the request Id
     */
    private static final List<String> REQUEST_ID_KEYS = Arrays.asList("x-amz-request-id", "x-amzn-requestid");

    static final String AWS_SDK_V2_CLIENT_ORIGIN = "AWSv2";

    /**
     * Transaction Context keys to retrieve the request event and retry counts since these events could happen
     * between different threads on the same transaction.
     */
    static final String TX_REQUEST_EVENT_KEY = "AWSv2RequestEvent";
    static final String TX_RETRY_COUNT_KEY = "AWSv2RetryCount";

    private final static Logger log = LogManager.getLogger(DiscoExecutionInterceptor.class);

    /**
     * The first API to be called by the execution interceptor chain.
     * This is called before it is modified by other interceptors
     * @param context The Context object passed in by the execution interceptor.
     *                This changes as we progress through different method calls.
     * @param executionAttributes The execution attributes which contain information such as region, service name, etc.
     */
    public void beforeExecution(Object context, Object executionAttributes) {
        InterceptorContextAccessor interceptorContextAccessor = new InterceptorContextAccessor(context);
        ExecutionAttributesAccessor executionAttributesAccessor = new ExecutionAttributesAccessor(executionAttributes);

        // getAttribute returns an arbitrary object. For the service name and operation name, they are returned as Strings.
        // For the region, it's an implemented object, hence why we need to call toString().
        String serviceName = (String) executionAttributesAccessor.getAttribute(ExecutionAttributesAccessor.SERVICE_NAME_ATTRIBUTE_NAME);
        String operationName = (String) executionAttributesAccessor.getAttribute(ExecutionAttributesAccessor.OPERATION_NAME_ATTRIBUTE_NAME);
        String region = executionAttributesAccessor.getAttribute(ExecutionAttributesAccessor.AWS_REGION_ATTRIBUTE_NAME).toString();

        SdkRequestAccessor sdkRequestAccessor = new SdkRequestAccessor(interceptorContextAccessor.getRequest());
        AwsServiceDownstreamRequestEvent awsEvent = new AwsServiceDownstreamRequestEventImpl(AWS_SDK_V2_CLIENT_ORIGIN, serviceName, operationName)
                .withSdkRequestAccessor(sdkRequestAccessor)
                .withRegion(region);
        TransactionContext.putMetadata(TX_REQUEST_EVENT_KEY, awsEvent);
    }

    /**
     * This modifies the Http request object before it is transmitted. The modified Http request must be returned
     * @param context The Context object passed in by the execution interceptor.
     *                This changes as we progress through different method calls.
     * @param executionAttributes The execution attributes which contain information such as region, service name, etc.
     * @return the modified Http request
     */
    public Object modifyHttpRequest(Object context, Object executionAttributes) {
        AwsServiceDownstreamRequestEventImpl requestEvent = (AwsServiceDownstreamRequestEventImpl) TransactionContext.getMetadata(TX_REQUEST_EVENT_KEY);
        InterceptorContextAccessor interceptorContextAccessor = new InterceptorContextAccessor(context);
        ExecutionAttributesAccessor executionAttributesAccessor = new ExecutionAttributesAccessor(executionAttributes);
        SdkHttpRequestAccessor sdkHttpRequestAccessor = new SdkHttpRequestAccessor(interceptorContextAccessor.getHttpRequest());

        requestEvent.withSdkHttpRequestAccessor(sdkHttpRequestAccessor)
            .withHeaderMap(sdkHttpRequestAccessor.getImmutableHeadersMap());

        EventBus.publish(requestEvent);

        return sdkHttpRequestAccessor.getSdkHttpRequestObject();
    }

    /**
     * This is called before every API attempt. We use this to keep track of how many API attempts are made
     * @param context The Context object passed in by the execution interceptor.
     *                This changes as we progress through different method calls.
     * @param executionAttributes The execution attributes which contain information such as region, service name, etc.
     */
    public void beforeTransmission(Object context, Object executionAttributes) {
        Object retryCountObj = TransactionContext.getMetadata(TX_RETRY_COUNT_KEY);
        int retryCount;
        if (retryCountObj == null) {
            retryCount = 0;
        } else {
            retryCount = (int) retryCountObj + 1;
        }

        TransactionContext.putMetadata(TX_RETRY_COUNT_KEY, retryCount);
    }

    /**
     * This is called after it has been potentially modified by other request interceptors before it is sent to the service.
     * @param context The Context object passed in by the execution interceptor.
     *                This changes as we progress through different method calls.
     * @param executionAttributes The execution attributes which contain information such as region, service name, etc.
     */
    public void afterExecution(Object context, Object executionAttributes) {
        InterceptorContextAccessor interceptorContextAccessor = new InterceptorContextAccessor(context);
        ExecutionAttributesAccessor executionAttributesAccessor = new ExecutionAttributesAccessor(executionAttributes);
        AwsServiceDownstreamRequestEvent requestEvent = (AwsServiceDownstreamRequestEvent) TransactionContext.getMetadata(TX_REQUEST_EVENT_KEY);
        software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkHttpResponseAccessor sdkHttpResponseAccessor = new software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkHttpResponseAccessor(interceptorContextAccessor.getHttpResponse());

        Object txRetryCount = TransactionContext.getMetadata(TX_RETRY_COUNT_KEY);
        int retryCount = txRetryCount == null ? 0 : (int) txRetryCount;

        SdkResponseAccessor responseAccessor = new SdkResponseAccessor(interceptorContextAccessor.getResponse());
        AwsServiceDownstreamResponseEvent awsEvent = new AwsServiceDownstreamResponseEventImpl(requestEvent)
                .withSdkResponseAccessor(responseAccessor)
                .withSdkHttpResponseAccessor(sdkHttpResponseAccessor)
                .withHeaderMap(sdkHttpResponseAccessor.getImmutableHeadersMap())
                .withRequestId(extractRequestId(sdkHttpResponseAccessor))
                .withRetryCount(retryCount);
        EventBus.publish(awsEvent);
    }

    /**
     * This is called on failure during any point of the lifecycle of the request.
     * @param context The Context object passed in by the execution interceptor.
     *                This changes as we progress through different method calls.
     *                At this point, it's a failed execution context object.
     * @param executionAttributes The execution attributes which contain information such as region, service name, etc.
     */
    public void onExecutionFailure(Object context, Object executionAttributes) {
        FailedExecutionContextAccessor failedContextAccessor = new FailedExecutionContextAccessor(context);
        ExecutionAttributesAccessor executionAttributesAccessor = new ExecutionAttributesAccessor(executionAttributes);
        AwsServiceDownstreamRequestEvent requestEvent = (AwsServiceDownstreamRequestEvent) TransactionContext.getMetadata(TX_REQUEST_EVENT_KEY);
        software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkHttpResponseAccessor sdkHttpResponseAccessor = new software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse.SdkHttpResponseAccessor(failedContextAccessor.getHttpResponse());

        Object txRetryCount = TransactionContext.getMetadata(TX_RETRY_COUNT_KEY);
        int retryCount = txRetryCount == null ? 0 : (int) txRetryCount;

        AwsServiceDownstreamResponseEvent awsEvent = new AwsServiceDownstreamResponseEventImpl(requestEvent)
                .withSdkHttpResponseAccessor(sdkHttpResponseAccessor)
                .withHeaderMap(sdkHttpResponseAccessor.getImmutableHeadersMap())
                .withRequestId(extractRequestId(sdkHttpResponseAccessor))
                .withRetryCount(retryCount);
        awsEvent.withThrown(failedContextAccessor.getException());
        EventBus.publish(awsEvent);
    }

    /**
     * Default Methods
     */

    public void beforeMarshalling(Object context, Object executionAttributes) { }
    public void afterMarshalling(Object context, Object executionAttributes) { }
    public void afterTransmission(Object context, Object executionAttributes) { }
    public void beforeUnmarshalling(Object context, Object executionAttributes) { }
    public void afterUnmarshalling(Object context, Object executionAttributes) { }

    public Object modifyRequest(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getRequest();
    }

    public Object modifyHttpContent(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getRequestBody();
    }

    public Object modifyAsyncHttpContent(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getAsyncRequestBody();
    }

    public Object modifyHttpResponse(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getHttpResponse();
    }

    public Object modifyAsyncHttpResponseContent(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getResponsePublisherMethod();
    }

    public Object modifyHttpResponseContent(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getResponseBody();
    }

    public Object modifyResponse(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getResponse();
    }

    public Object modifyException(Object context, Object executionAttributes) {
        return new InterceptorContextAccessor(context).getException();
    }

    /**
     * Helper method for extracting the request Id from the Http Response.
     * @param httpResponseAccessor The accessor which is used to retrieve the request Id
     * @return The request Id. Null if it had failed to find it.
     */
    private String extractRequestId(SdkHttpResponseAccessor httpResponseAccessor) {
        Map<String, List<String>> headerMap = httpResponseAccessor.getImmutableHeadersMap();
        if (headerMap == null) return null;

        for(String request_id_key : REQUEST_ID_KEYS) {
            List<String> requestIdList = headerMap.get(request_id_key);
            if (requestIdList != null && requestIdList.size() > 0) {
                return requestIdList.get(0); // Arbitrarily get the first one since headers are many to one.
            }
        }
        return null;
    }
}
