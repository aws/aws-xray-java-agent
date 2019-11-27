package software.amazon.disco.agent.awsv2.executioninterceptor.accessors;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.Optional;

import static software.amazon.disco.agent.awsv2.executioninterceptor.accessors.AccessorUtils.getClassOrNull;

/**
 * Accessor used to access the failed exeuction context. Typically passed in the onExecutionFailure() call of the execution
 * interceptor.
 */
public class FailedExecutionContextAccessor implements ContextAccessor {
    private static final String INTERCEPTOR_CONTEXT_CLASSPATH = "software.amazon.awssdk.core.interceptor.Context$FailedExecution";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static Class SDK_REQUEST_CLASS = getClassOrNull("software.amazon.awssdk.core.SdkRequest", classLoader);

    private static MethodHandleWrapper requestMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "request", SDK_REQUEST_CLASS);
    private static MethodHandleWrapper responseMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "response", Optional.class);
    private static MethodHandleWrapper httpRequestMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "httpRequest", Optional.class);
    private static MethodHandleWrapper httpResponseMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "httpResponse", Optional.class);
    private static MethodHandleWrapper exceptionMethod = new MethodHandleWrapper(INTERCEPTOR_CONTEXT_CLASSPATH, classLoader, "exception", Throwable.class);

    private Object interceptorContext;

    public FailedExecutionContextAccessor(Object interceptorContext) {
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
        Optional<Object> httpRequestOptional = (Optional) httpRequestMethod.invoke(interceptorContext);
        return httpRequestOptional.orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getResponse() {
        Optional<Object> responseOptional = (Optional) responseMethod.invoke(interceptorContext);
        return responseOptional.orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getHttpResponse() {
        Optional<Object> httpResponseOptional = (Optional) httpResponseMethod.invoke(interceptorContext);
        return httpResponseOptional.orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Throwable getException() {
        return (Throwable) exceptionMethod.invoke(interceptorContext);
    }

}
