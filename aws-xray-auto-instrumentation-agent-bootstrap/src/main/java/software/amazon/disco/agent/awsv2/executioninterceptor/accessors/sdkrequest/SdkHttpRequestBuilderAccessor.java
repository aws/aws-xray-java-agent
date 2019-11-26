package software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

/**
 * Accessor used to call methods in the Sdk Http Request Builder Class
 */
public class SdkHttpRequestBuilderAccessor {
    private static final String SDK_HTTP_FULL_REQUEST_CLASSPATH = "software.amazon.awssdk.http.SdkHttpFullRequest";
    private static final String SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH = "software.amazon.awssdk.http.SdkHttpFullRequest$Builder";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static Class builderClass = getClassOrNull(SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH, classLoader);
    private static Class sdkHttpRequestClass = getClassOrNull(SDK_HTTP_FULL_REQUEST_CLASSPATH, classLoader);

    private static MethodHandleWrapper appendHeader = new MethodHandleWrapper(SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH, classLoader, "appendHeader", builderClass, String.class, String.class);
    private static MethodHandleWrapper build = new MethodHandleWrapper(SDK_HTTP_FULL_REQUEST_BUILDER_CLASSPATH, classLoader, "build", sdkHttpRequestClass);

    private Object sdkHttpRequestBuilder;

    public SdkHttpRequestBuilderAccessor(Object sdkHttpRequestBuilder) {
        this.sdkHttpRequestBuilder = sdkHttpRequestBuilder;
    }

    /**
     * Append the header to the current builder instance.
     * @param key The header key to use
     * @param value The header value to use
     * @return 'this' for method chaining.
     */
    public SdkHttpRequestBuilderAccessor appendHeader(String key, String value) {
        // From experience, this just replaces the header and doesn't append it. For consistency purposes, will
        // keep the method name as appendHeader since the underlying object has that same method name.
        sdkHttpRequestBuilder = appendHeader.invoke(sdkHttpRequestBuilder, key, value);
        return this;
    }

    /**
     * Build the builder into a SdkHttpRequest object
     * @return the built SdkHttpRequest Object
     */
    public Object build() {
        return build.invoke(sdkHttpRequestBuilder);
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
