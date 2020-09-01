package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.agent.runtime.handlers.XRayHandler;
import com.amazonaws.xray.entities.EntityDataKeys;
import com.amazonaws.xray.entities.EntityHeaderKeys;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.handlers.config.AWSOperationHandler;
import com.amazonaws.xray.handlers.config.AWSOperationHandlerManifest;
import com.amazonaws.xray.handlers.config.AWSServiceHandlerManifest;
import com.amazonaws.xray.utils.StringTransform;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.disco.agent.event.AwsServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.AwsServiceDownstreamResponseEvent;
import software.amazon.disco.agent.event.Event;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Much of the code is adopted from
 * https://github.com/aws/aws-xray-sdk-java/blob/master/aws-xray-recorder-sdk-aws-sdk-v2/src/main/java/com/amazonaws/xray/interceptors/TracingInterceptor.java#L281
 *
 * The long term would be to expose APIs in the SDK package so that we can take bits of it to perform the
 * instrumentation. Since this is an event-based architecture, we can't quite re-use the same beforeExecution
 * calls.
 */
public class AWSV2Handler extends XRayHandler {
    private static final Log log = LogFactory.getLog(AWSV2Handler.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    private static final URL DEFAULT_OPERATION_PARAMETER_WHITELIST = AWSV2Handler.class.getResource("/com/amazonaws/xray/interceptors/DefaultOperationParameterWhitelist.json");

    // Response Fields
    private static final String STATUS_CODE_KEY = "status";
    private static final String CONTENT_LENGTH_KEY = "content_length";
    private static final String HTTP_RESPONSE_KEY = "response";

    private static AWSServiceHandlerManifest awsServiceHandlerManifest;

    public AWSV2Handler() {
        initInterceptorManifest(DEFAULT_OPERATION_PARAMETER_WHITELIST);
    }

    public AWSV2Handler(URL serviceHandlerManifest) {
        initInterceptorManifest(serviceHandlerManifest);
    }

    @Override
    public void handleRequest(Event event) {
        AwsServiceDownstreamRequestEvent requestEvent = (AwsServiceDownstreamRequestEvent) event;
        String serviceName = requestEvent.getService();
        String operationName = requestEvent.getOperation();
        String region = requestEvent.getRegion();

        // Avoid throwing if name isn't present. HTTP interceptor will pick this up instead
        if (serviceName == null) {
            return;
        }

        // Begin subsegment
        Subsegment subsegment = beginSubsegment(serviceName);
        subsegment.setNamespace(Namespace.AWS.toString());
        subsegment.putAws(EntityDataKeys.AWS.OPERATION_KEY, operationName);
        subsegment.putAws(EntityDataKeys.AWS.REGION_KEY, region);

        // Trace propagation
        TraceHeader traceHeader = buildTraceHeader(subsegment);
        requestEvent.replaceHeader(TraceHeader.HEADER_KEY, traceHeader.toString());

        Map<String, Object> parameterMap = extractRequestParameters(requestEvent);
        subsegment.putAllAws(parameterMap);
    }

    /**
     * Extract the Request Parameters from the Aws Request Event. This is based on what's been whitelisted in
     * the operations whitelist JSON file to retrieve specific fields so that we can fetch for example
     * TableName, Count, etc from the Sdk request.
     * @param requestEvent The request event to retrieve the the field values from.
     * @return A mapping of the snake cased name of the field to the actual field value.
     */
    private HashMap<String, Object> extractRequestParameters(AwsServiceDownstreamRequestEvent requestEvent) {
        HashMap<String, Object> parameters = new HashMap<>();

        AWSOperationHandler operationHandler = getOperationHandler(requestEvent.getService(), requestEvent.getOperation());
        if (operationHandler == null) {
            return parameters;
        }

        if (operationHandler.getRequestParameters() != null) {
            operationHandler.getRequestParameters().forEach((parameterName) -> {
                Optional<Object> parameterValue = (Optional) requestEvent.getValueForField(parameterName, Object.class);
                parameterValue.ifPresent(o -> parameters.put(StringTransform.toSnakeCase(parameterName), o));
            });
        }

        if (operationHandler.getRequestDescriptors() != null) {
            operationHandler.getRequestDescriptors().forEach((key, descriptor) -> {
                if (descriptor.isMap() && descriptor.shouldGetKeys()) {
                    Optional<Map> parameterValue = (Optional<Map>) requestEvent.getValueForField(key, Map.class);
                    if (parameterValue.isPresent()) {
                        String renameTo = descriptor.getRenameTo() != null ? descriptor.getRenameTo() : key;
                        parameters.put(StringTransform.toSnakeCase(renameTo), parameterValue.get().keySet());
                    }
                } else if (descriptor.isList() && descriptor.shouldGetCount()) {
                    Optional<List> parameterValue = (Optional<List>)requestEvent.getValueForField(key, List.class);
                    if (parameterValue.isPresent()) {
                        String renameTo = descriptor.getRenameTo() != null ? descriptor.getRenameTo() : key;
                        parameters.put(StringTransform.toSnakeCase(renameTo), parameterValue.get().size());
                    }
                }
            });
        }

        return parameters;
    }

    /**
     * Extract the Response Parameters from the Aws Response Event. This is based on what's been whitelisted in
     * the operations whitelist JSON file to retrieve specific fields so that we can fetch for example
     * TableName, Count, etc from the Sdk request.
     * @param responseEvent The request event to retrieve the the field values from.
     * @return A mapping of the snake cased name of the field to the actual field value.
     */
    private HashMap<String, Object> extractResponseParameters(AwsServiceDownstreamResponseEvent responseEvent) {
        // TODO Similar to extract Request Parameters. Need to refactor common functionality
        HashMap<String, Object> parameters = new HashMap<>();

        AWSOperationHandler operationHandler = getOperationHandler(responseEvent.getService(), responseEvent.getOperation());
        if (operationHandler == null) {
            return parameters;
        }

        if (operationHandler.getResponseParameters() != null) {
            operationHandler.getResponseParameters().forEach((parameterName) -> {
                Optional<Object> parameterValue = (Optional) responseEvent.getValueForField(parameterName, Object.class);
                parameterValue.ifPresent(o -> parameters.put(StringTransform.toSnakeCase(parameterName), o));
            });
        }

        if (operationHandler.getResponseDescriptors() != null) {
            operationHandler.getResponseDescriptors().forEach((key, descriptor) -> {
                if (descriptor.isMap() && descriptor.shouldGetKeys()) {
                    Optional<Map> parameterValue = (Optional<Map>) responseEvent.getValueForField(key, Map.class);
                    if (parameterValue.isPresent()) {
                        String renameTo = descriptor.getRenameTo() != null ? descriptor.getRenameTo() : key;
                        parameters.put(StringTransform.toSnakeCase(renameTo), parameterValue.get().keySet());
                    }
                } else if (descriptor.isList() && descriptor.shouldGetCount()) {
                    Optional<List> parameterValue = (Optional<List>) responseEvent.getValueForField(key, List.class);
                    if (parameterValue.isPresent()) {
                        String renameTo = descriptor.getRenameTo() != null ? descriptor.getRenameTo() : key;
                        parameters.put(StringTransform.toSnakeCase(renameTo), parameterValue.get().size());
                    }
                }
            });
        }

        return parameters;
    }

    /**
     * Set the cause in the subegment to remote if the throwable is an exception from the Sdk Exception object.
     * @param subsegment The subsegment which contains throwables
     * @param exception The SdkException that we want to set the cause to remote in the subsegment
     */
    private void setRemoteForException(Subsegment subsegment, Throwable exception) {
        subsegment.getCause().getExceptions().forEach((e) -> {
            if (e.getThrowable() == exception) {
                e.setRemote(true);
            }
        });
    }

    @Override
    public void handleResponse(Event event) {
        AwsServiceDownstreamResponseEvent responseEvent = (AwsServiceDownstreamResponseEvent) event;
        Subsegment subsegment = getSubsegmentOptional().orElse(null);
        if (subsegment == null) {
            return;
        }

        Map<String, Object> responseInformation = new HashMap<>();

        // Retrieve the response parameters such as the table name, table size, etc.
        Map<String, Object> parameterMap = extractResponseParameters(responseEvent);
        subsegment.putAllAws(parameterMap);

        // Detect throwable for the downstream call.
        Throwable exception = responseEvent.getThrown();
        if (exception != null && exception.getMessage() != null) {
            subsegment.addException(exception);
            subsegment.getCause().setMessage(exception.getMessage());
            setRemoteForException(subsegment, exception);
        }

        // Store retry count
        subsegment.putAws(EntityDataKeys.AWS.RETRIES_KEY, responseEvent.getRetryCount());

        // Get status code an add it to the response map.
        // TODO unify this in the XRayHandler superclass.
        int responseCode = responseEvent.getStatusCode();
        switch (responseCode/100) {
            case 4:
                subsegment.setError(true);
                subsegment.setFault(false);
                if (429 == responseCode) {
                    subsegment.setThrottle(true);
                }
                break;
            case 5:
                subsegment.setError(false);
                subsegment.setFault(true);
                break;
        }
        if (responseCode >= 0) {
            responseInformation.put(STATUS_CODE_KEY, responseCode);
        }

        // Obtain the content length from the header map and store it in the subsegment
        Map<String, List<String>> headerMap = responseEvent.getHeaderMap();
        List<String> contentLengthList = headerMap.get(EntityHeaderKeys.HTTP.CONTENT_LENGTH_HEADER);
        if (contentLengthList != null && contentLengthList.size() > 0) {
            long contentLength = Long.parseLong(contentLengthList.get(0));
            responseInformation.put(CONTENT_LENGTH_KEY, contentLength);
        }

        if (responseInformation.size() > 0) {
            subsegment.putHttp(HTTP_RESPONSE_KEY, responseInformation);
        }

        // Store request ID
        String requestId = responseEvent.getRequestId();
        if (requestId != null) {
            subsegment.putAws(EntityDataKeys.AWS.REQUEST_ID_KEY, requestId);
        }

        // Store extended ID
        String extendedRequestId = extractExtendedRequestIdFromHeaderMap(responseEvent.getHeaderMap());

        if (extendedRequestId != null) {
            subsegment.putAws(EntityDataKeys.AWS.EXTENDED_REQUEST_ID_KEY, extendedRequestId);
        }

        endSubsegment();
    }

    /**
     * Extracts the extended Request Id from the given header map. This header map should generally
     * be retrieved from the response events.
     * @param headers The header map that may contain the extended request Id
     * @return The request Id if it exists, null otherwise.
     */
    private String extractExtendedRequestIdFromHeaderMap(Map<String, List<String>> headers) {
        return headers.containsKey(EntityHeaderKeys.AWS.EXTENDED_REQUEST_ID_HEADER) ?
                headers.get(EntityHeaderKeys.AWS.EXTENDED_REQUEST_ID_HEADER).get(0) : null;
    }

    /**
     * Retrieve the operation handler from internal whitelist
     * @param serviceName The service name that we are intercepting
     * @param operationName The operation name of downstream call we are making
     * @return The operation handler that will help us obtain field values to gather.
     */
    private AWSOperationHandler getOperationHandler(String serviceName, String operationName) {
        if (awsServiceHandlerManifest == null) {
            return null;
        }
        AWSOperationHandlerManifest operationManifest = awsServiceHandlerManifest.getOperationHandlerManifest(serviceName);
        if (operationManifest == null) {
            return null;
        }
        return operationManifest.getOperationHandler(operationName);
    }

    /**
     * Initialize the interceptor manifest by reading the default whitelisted JSON and constructing the handler
     * manifest from this JSON
     * @param parameterWhitelist The URL path of the parameter whitelist JSON.
     */
    private void initInterceptorManifest(URL parameterWhitelist) {
        if (parameterWhitelist != null) {
            try {
                awsServiceHandlerManifest = mapper.readValue(parameterWhitelist, AWSServiceHandlerManifest.class);
                return;
            } catch (IOException e) {
                log.error(
                        "Unable to parse operation parameter whitelist at " + parameterWhitelist.getPath() +
                                ". Falling back to default operation parameter whitelist at " + DEFAULT_OPERATION_PARAMETER_WHITELIST.getPath() + ".",
                        e
                );
            }
        }
        try {
            awsServiceHandlerManifest = mapper.readValue(DEFAULT_OPERATION_PARAMETER_WHITELIST, AWSServiceHandlerManifest.class);
        } catch (IOException e) {
            log.error(
                    "Unable to parse default operation parameter whitelist at " + DEFAULT_OPERATION_PARAMETER_WHITELIST.getPath() +
                            ". This will affect this handler's ability to capture AWS operation parameter information.",
                    e
            );
        }
    }
}
