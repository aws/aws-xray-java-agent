package software.amazon.disco.agent.awsv2;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import software.amazon.disco.agent.awsv2.executioninterceptor.DiscoInvokeHandler;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

public class AWSClientBuilderInterceptor extends AWSClientInterceptor {
    private final static Logger log = LogManager.getLogger(AWSClientBuilderInterceptor.class);

    private final static String EXECUTION_INTERCEPTOR_CLASSPATH = "software.amazon.awssdk.core.interceptor.ExecutionInterceptor";

    private static Object executionInterceptorProxy;

    /**
     * The HttpServlet#service method is intercepted, and redirected here, where the
     * original request and response objects are sifted through to retrieve useful
     * header information that is stored in the HttpNetworkProtocol(Request/Response)Events
     * and published to the event bus.
     *
     * @param args    the original arguments passed to the invoke call
     * @param invoker the original 'this' of the invoker, in case useful or for debugging
     * @param origin  identifier of the intercepted method, for debugging/logging
     * @param zuper   a callable to call the original method
     * @throws Exception - catch-all for whatever exceptions might be throwable in the original call
     * @return The object returned by the http client call
     */
    @SuppressWarnings("unused")
    @RuntimeType
    public static Object build(@AllArguments Object[] args,
                               @This Object invoker,
                               @Origin String origin,
                               @SuperCall Callable<Object> zuper) throws Throwable {
        Throwable throwable = null;

        if (LogManager.isDebugEnabled()) {
            log.debug("DiSCo(AWSV2) interception of " + origin);
        }

        if (makeInterceptorProxyReady()) {
            // Attempt to add the custom handler to the
            AWSClientBuilderAccessor clientBuilderAccessor = new AWSClientBuilderAccessor(invoker);

            Object overrideResult = clientBuilderAccessor.overrideConfigurationWithExecutionInterceptor(executionInterceptorProxy);
            if (overrideResult == null) {
                // Experienced failure with overriding if it returns null. All this means is that they're not instrumented.
                // No application faults as a result.
                log.error("DiSCo(AWSV2) Unable to override the client builder with the DiSCo execution interceptor.");
            }
        } else {
            log.error("DiSCo(AWSV2) Unable to pass in the execution proxy into the client " + origin +
                    ". It wasn't generated because the execution interceptor interface could not be located.");
        }

        // call the original
        // We intercept the Builder.build() method
        return zuper.call();
    }

    /**
     * Get the interceptor proxy.
     * @return False if it was unable to. True if it succeeded.
     */
    private static boolean makeInterceptorProxyReady() {
        if (executionInterceptorProxy != null) {
            return true;
        }

        // The classloader we should use here should be the same classloader that contains the execution interface
        // Since that's likely where the entire AWS SDK v2 core is.
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class executionInterceptorInterface;
        try {
            executionInterceptorInterface = Class.forName(EXECUTION_INTERCEPTOR_CLASSPATH, false, classLoader);
        } catch (ClassNotFoundException e) {
            log.error("DiSCo(AWSV2) Unable to locate the execution interceptor.");
            return false;
        }
        InvocationHandler invokeHandler = new DiscoInvokeHandler(executionInterceptorInterface);
        executionInterceptorProxy = Proxy.newProxyInstance(classLoader, new Class[] {executionInterceptorInterface}, invokeHandler);
        return true;
    }
}
