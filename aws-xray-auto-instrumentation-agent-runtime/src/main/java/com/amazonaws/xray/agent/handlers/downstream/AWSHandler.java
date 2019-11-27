package com.amazonaws.xray.agent.handlers.downstream;

import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.ServiceRequestEvent;
import software.amazon.disco.agent.event.ServiceResponseEvent;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.xray.agent.handlers.XRayHandler;
import com.amazonaws.xray.handlers.TracingHandler;

/**
 * The AWS handler generates a subsegment from a given AWS downstream service event.
 * Due to a limitation in the AWS SDK V1 interceptor, S3 is currently not supported.
 */
public class AWSHandler extends XRayHandler {
    private TracingHandler tracingHandler;

    public AWSHandler() {
        // We internally re-use our tracing handler from our AWS SDK V1 instrumentor to do all the X-Ray handling.
        // The tracing handler's internal call to beforeExecution doesn't need to be done because this agent
        // uses its own context for propagating segments using the TransactionContext.
        tracingHandler = new TracingHandler();
    }

    @Override
    public void handleRequest(Event event) {
        ServiceRequestEvent requestEvent = (ServiceRequestEvent) event;

        Request awsRequest = (Request) requestEvent.getRequest();
        tracingHandler.beforeRequest(awsRequest);
    }

    @Override
    public void handleResponse(Event event) {
        ServiceResponseEvent responseEvent = (ServiceResponseEvent) event;
        Request awsReq = (Request) responseEvent.getRequest().getRequest();
        Response awsResp = (Response) responseEvent.getResponse();

        if (responseEvent.getThrown() == null) {
            tracingHandler.afterResponse(awsReq, awsResp);
        } else {
            Throwable exception = responseEvent.getThrown();
            tracingHandler.afterError(awsReq, awsResp, (Exception) exception);
        }
    }
}
