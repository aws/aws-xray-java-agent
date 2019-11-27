package software.amazon.disco.agent.awsv2.executioninterceptor.accessors;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.Optional;

import static software.amazon.disco.agent.awsv2.executioninterceptor.accessors.AccessorUtils.getClassOrNull;

/**
 * Accessor used to access the interceptor context. Typically passed in the beforeExecution() call of the execution
 * interceptor.
 */
public class InterceptorContextAccessor implements ContextAccessor {
    private static final String INTERCEPTOR_CONTEXT_CLASSPATH = "software.amazon.awssdk.core.interceptor.InterceptorContext";
    private static String FAILED_INTERCEPTOR_CONTEXT_CLASSPATH = "software.amazon.awssdk.core.interceptor.Context$FailedExecution";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static Class SDK_REQUEST_CLASS = getClassOrNull("software.amazon.awssdk.core.SdkRequest", classLoader);
    private static Class SDK_RESPONSE_CLASS = getClassOrNull("software.amazon.awssdk.core.SdkResponse", classLoader);
    private static Class SDK_HTTP_REQUEST_CLASS = getClassOrNull("software.amazon.awssdk.http.SdkHttpRequest", classLoader);
    private static Class SDK_HTTP_RESPONSE_CLASS = getClassOrNull("software.amazon.awssdk.http.SdkHttpResponse", classLoader);

    private static MethodHandleWrapper requestMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "request", SDK_REQUEST_CLASS);
    private static MethodHandleWrapper responseMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "response", SDK_RESPONSE_CLASS);
    private static MethodHandleWrapper httpRequestMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "httpRequest", SDK_HTTP_REQUEST_CLASS);
    private static MethodHandleWrapper requestBodyMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "requestBody", Optional.class);
    private static MethodHandleWrapper asyncRequestBodyMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "asyncRequestBody", Optional.class);
    private static MethodHandleWrapper httpResponseMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "httpResponse", SDK_HTTP_RESPONSE_CLASS);
    private static MethodHandleWrapper responseBodyMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "responseBody", Optional.class);
    private static MethodHandleWrapper exceptionMethod = new MethodHandleWrapper(FAILED_INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "exception", Throwable.class);
    private static MethodHandleWrapper responsePublisherMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "responsePublisher", Optional.class);

    private Object interceptorContext;

    public InterceptorContextAccessor(Object interceptorContext) {
        this.interceptorContext = interceptorContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getRequest() {
        return requestMethod.invoke(interceptorContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getHttpRequest() {
        return httpRequestMethod.invoke(interceptorContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getResponse() {
        return responseMethod.invoke(interceptorContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getHttpResponse() {
        return httpResponseMethod.invoke(interceptorContext);
    }

    public Object getRequestBody() {
        return requestBodyMethod.invoke(interceptorContext);
    }

    public Object getAsyncRequestBody() {
        return asyncRequestBodyMethod.invoke(interceptorContext);
    }

    public Object getResponseBody() {
        return responseBodyMethod.invoke(interceptorContext);
    }

    public Object getResponsePublisherMethod() {
        return responsePublisherMethod.invoke(interceptorContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Throwable getException() {
        return (Throwable) exceptionMethod.invoke(interceptorContext);
    }

}
