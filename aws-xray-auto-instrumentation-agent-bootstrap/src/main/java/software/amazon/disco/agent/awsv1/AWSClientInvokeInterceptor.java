package software.amazon.disco.agent.awsv1;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;
import software.amazon.disco.agent.interception.Installable;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;

import java.util.concurrent.Callable;

/**
 * When making a downstream AWS call, the doInvoke method is intercepted.
 */
public class AWSClientInvokeInterceptor implements Installable {
    private static Logger log = LogManager.getLogger(AWSClientInvokeInterceptor.class);

    /**
     * This method is used to replace the doInvoke() method inside each of the AWS clients.
     *
     * @param request - The method signature of the intercepted call is:
     *                  Response (DefaultRequest, HttpResponseHandler, ExecutionContext
     *                  The first argument is the request object which is the argument we need.
     * @param origin - an identifier of the intercepted Method, for logging/debugging
     * @param zuper - ByteBuddy supplies a Callable to the intercepted method, due to the @SuperCall annotation
     * @return - The object produced by the original intercepted request
     * @throws Exception - The internal call to 'zuper.call()' may throw any Exception
     */
    @SuppressWarnings("unused")
    @RuntimeType
    public static Object doInvoke(@Argument(0) Object request,
                                  @Origin String origin,
                                  @SuperCall Callable<Object> zuper) throws Throwable {
        log.debug("DiSCo(AWS) method interception of " + origin);

        //Retrieve name of AWS service we are calling
        String serviceName = (String) request.getClass().getMethod("getServiceName").invoke(request);

        //Original request contains both the operation name and the request object
        Object originalRequest = request.getClass().getMethod("getOriginalRequest").invoke(request);
        String operationName = originalRequest.getClass().getSimpleName().replace("Request", "");

        ServiceDownstreamRequestEvent requestEvent = new ServiceDownstreamRequestEvent("AWSv1", serviceName, operationName);
        requestEvent.withRequest(request);
        EventBus.publish(requestEvent);

        //make the original call, and wrap in an exception catch, in case it throws instead of servicing the request
        //normally.
        Object output = null;
        Throwable thrown = null;
        try {
            output = zuper.call();
        } catch (Throwable t) {
            thrown = t;
        }

        log.debug("DiSCo(AWS) publishing event from "+serviceName+"."+operationName);

        ServiceDownstreamResponseEvent responseEvent = new ServiceDownstreamResponseEvent("AWSv1", serviceName, operationName, requestEvent);
        responseEvent.withResponse(output);
        responseEvent.withThrown(thrown);
        EventBus.publish(responseEvent);

        if (thrown != null) {
            throw thrown;
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AgentBuilder install(AgentBuilder agentBuilder) {
        return agentBuilder
                .type(buildClassMatcher())
                .transform((builder, typeDescription, classLoader, module) -> builder
                        .method(buildMethodMatcher())
                        .intercept(MethodDelegation.to(AWSClientInvokeInterceptor.class)));

    }

    /**
     * Builds a class matcher to discover all implemented
     * AWS clients.
     * @return an ElementMatcher suitable for passing to the type() method of a AgentBuilder
     */
    ElementMatcher<? super TypeDescription> buildClassMatcher() {
        return ElementMatchers.hasSuperType(ElementMatchers.named("com.amazonaws.AmazonWebServiceClient"))
                .and(ElementMatchers.not(ElementMatchers.isAbstract()));
    }

    /**
     * Builds a method matcher to match against all doInvoke methods in AWS clients
     * @return an ElementMatcher suitable for passing to builder.method()
     */
    ElementMatcher<? super MethodDescription> buildMethodMatcher() {
        return ElementMatchers.named("doInvoke")
                .and(ElementMatchers.not(ElementMatchers.isAbstract()));
    }
}