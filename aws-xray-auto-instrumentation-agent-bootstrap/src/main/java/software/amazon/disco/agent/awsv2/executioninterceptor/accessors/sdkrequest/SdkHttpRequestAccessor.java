package software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.List;
import java.util.Map;

/**
 * Accessor class used to retrieve the Http specific information
 */
public class SdkHttpRequestAccessor {
    /**
     * The classpath for the underlying Sdk Http Request
     */
    private static final String SDK_HTTP_FULL_REQUEST_CLASSPATH = "software.amazon.awssdk.http.SdkHttpFullRequest";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    /**
     * The classpath for the underlying request builder object. Used for generating a new SdkHttpRequest from the existing one.
     */
    private static final String SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH = "software.amazon.awssdk.http.SdkHttpFullRequest$Builder";
    private static Class builderClass = getClassOrNull(SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH, classLoader);

    /**
     * Retrieve an immutable header map from the http request.
     */
    private static MethodHandleWrapper getHeadersMap = new MethodHandleWrapper(SDK_HTTP_FULL_REQUEST_CLASSPATH, classLoader, "headers", Map.class);

    /**
     * Convert the current request to a builder. Used so we can mutate the header map and then rebuild the object.
     */
    private static MethodHandleWrapper builderMethod = new MethodHandleWrapper(SDK_HTTP_FULL_REQUEST_CLASSPATH, classLoader, "toBuilder", builderClass);

    /**
     * Underlying Sdk Http request object that will be invoked during the calls.
     */
    private Object sdkHttpRequest;

    /**
     * Construct the SdkHttpRequestAccessor from the sdkHttpRequest object
     * @param sdkHttpRequest The Aws SdkHttpRequest that will be reflectively accessed.
     */
    public SdkHttpRequestAccessor(Object sdkHttpRequest) {
        this.sdkHttpRequest = sdkHttpRequest;
    }

    /**
     * Retrieve the underlying Http header map
     * @return the header map. Null otherwise
     */
    public Map<String, List<String>> getImmutableHeadersMap() {
        return (Map<String, List<String>>) getHeadersMap.invoke(sdkHttpRequest);
    }

    /**
     * Replaces the given header if one exists, otherwise adds it. If this call fails, we log
     * @param key The header key to replace
     * @param value The header value to replace the key with.
     * @return the updated header map if it succeeded. Otherwise returns null on failure.
     */
    public Map<String, List<String>> replaceHeader(String key, String value) {
        Object builderObject = builderMethod.invoke(sdkHttpRequest);
        if (builderObject == null) {
            // If we fail to rebuild the current http request, we return null as an indicator.
            return null;
        }
        SdkHttpRequestBuilderAccessor builder = new SdkHttpRequestBuilderAccessor(builderObject);

        builder.appendHeader(key, value);
        this.sdkHttpRequest = builder.build();
        return (Map<String, List<String>>) getHeadersMap.invoke(sdkHttpRequest);
    }

    /**
     * Retrieve the underlying Http Request Object
     * @return the unerlying Sdk Http Request
     */
    public Object getSdkHttpRequestObject() {
        return this.sdkHttpRequest;
    }

    /**
     * Helper method to retrieve a class given the class path and classLoader. returns null if it fails.
     * @param classPath The class path string to retrieve.
     * @param classLoader The classloader to use
     * @return The class object if it can be found. Null otherwise.
     */
    private static Class getClassOrNull(String classPath, ClassLoader classLoader) {
        try {
            return Class.forName(classPath, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
