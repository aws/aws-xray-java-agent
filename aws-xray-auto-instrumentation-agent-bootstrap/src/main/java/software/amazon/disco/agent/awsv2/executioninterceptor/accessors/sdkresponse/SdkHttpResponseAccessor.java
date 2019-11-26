package software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.List;
import java.util.Map;

/**
 * Accessor for Sdk Http Response objects
 */
public class SdkHttpResponseAccessor {
    private static final String SDK_HTTP_FULL_RESPONSE_CLASSPATH = "software.amazon.awssdk.http.SdkHttpFullResponse";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static MethodHandleWrapper getHeadersMap = new MethodHandleWrapper(SDK_HTTP_FULL_RESPONSE_CLASSPATH, classLoader, "headers", Map.class);
    private static MethodHandleWrapper statusCode = new MethodHandleWrapper(SDK_HTTP_FULL_RESPONSE_CLASSPATH, classLoader, "statusCode", int.class);

    private Object sdkHttpResponse;

    public SdkHttpResponseAccessor(Object sdkHttpResponse) {
        this.sdkHttpResponse = sdkHttpResponse;
    }

    /**
     * Retrieve the underlying Http header map
     * @return the header map. Null otherwise
     */
    public Map<String, List<String>> getImmutableHeadersMap() {
        return (Map<String, List<String>>) getHeadersMap.invoke(sdkHttpResponse);
    }

    /**
     * Retrieve the status code of this http response object
     * @return The status code
     */
    public int getStatusCode() {
        return (int) statusCode.invoke(this.sdkHttpResponse);
    }
}
