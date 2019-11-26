package software.amazon.disco.agent.awsv2.executioninterceptor;

import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class to an Execution Interceptor to ensure that any Sdk clients that have added this proxy to its
 * interceptor chain would call the DiSCo execution interceptor.
 *
 * This proxy works by forwarding all the method calls to the DiSCo Exercution Interceptor if the method is
 * implemented in the class. Because we cannot make direct dependencies on the Aws SDK interfaces in DiSCo,
 * this proxy reflectively forwards the methods to the DiSCo execution interceptor if and only if it has
 * implemented the appropriate methods. Otherwise, it calls the interface's default implementation.
 */
public class DiscoInvokeHandler implements InvocationHandler {
    private final static Logger log = LogManager.getLogger(DiscoInvokeHandler.class);
    private DiscoExecutionInterceptor executionInterceptor;
    private Class executionInterceptorInterface;

    // Header map used to determine if the generated execution interceptor has the methods passed in
    // from the AWS SDK interceptor invokers.
    private static Map<String, Method> methodsMap;
    private static MethodHandles.Lookup defaultMethodHandleLookup;

    public DiscoInvokeHandler(Class executionInterceptorInterface) {
        this.executionInterceptor = new DiscoExecutionInterceptor();
        this.executionInterceptorInterface = executionInterceptorInterface;
        initializeMethodsMap();
        initializeDefaultHandlesLookup();
    }

    /**
     * Invoke the proxied method if our interceptor contains the corresponding one.
     * Otherwise invoke the default method stored in the interface.
     * @param proxy The proxy object
     * @param method The method to be called in this interceptor
     * @param args The args passed into the method call
     * @return The result of the method call. Null if it failed to find the method in Disco Interceptor
     *         as well as the default
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Retrieve the DiSCo implemented method if it exists.
        String methodName = method.getName();
        Method discoInterceptorMethod = methodsMap.get(methodName);
        if (discoInterceptorMethod != null) {
            return discoInterceptorMethod.invoke(executionInterceptor, args);
        }

        // Otherwise, execute the interface's default method.
        // Solution adopted from https://stackoverflow.com/questions/26206614/java8-dynamic-proxy-and-default-methods
        // This is the case where our execution interceptor doesn't implement methods that are default methods in
        // the execution interceptor interface. Because our interceptor doesn't implement the interface directly,
        // we need to invoke these default methods through the interface itself.
        if (method.isDefault()) {
            Object result = defaultMethodHandleLookup.
                unreflectSpecial(method, executionInterceptorInterface).
                bindTo(proxy).
                invokeWithArguments(args);

            return result;
        }

        return null;
    }

    private void initializeMethodsMap() {
        if (methodsMap != null) {
             return;
        }

        methodsMap = new HashMap<>();

        for (Method m : DiscoExecutionInterceptor.class.getDeclaredMethods()) {
            methodsMap.put(m.getName(), m);
        }

        // For the methods names that do match the disco interceptor, check if the arguments align
        /// If it doesn't align, remove it from the map because it'll error if the sdk attempts to execute it.
        for (Method m : executionInterceptorInterface.getDeclaredMethods()) {
            String interfaceMethodName = m.getName();
            Method discoMethod = methodsMap.get(interfaceMethodName);
            // Verify that our interceptor implements the current method
            if (discoMethod == null) {
                continue;
            }
            // Verify the parameter count
            if (discoMethod.getParameterCount() != m.getParameterCount()) {
                methodsMap.remove(interfaceMethodName);
                continue;
            }
            // If the interface method doesn't return anything, we shouldn't either and vice versa
            if ((m.getReturnType() == Void.TYPE && discoMethod.getReturnType() != Void.TYPE) ||
                (discoMethod.getReturnType() == Void.TYPE && m.getReturnType() != Void.TYPE)) {
                methodsMap.remove(interfaceMethodName);
            }
        }
    }

    /**
     * Initialize the default handles look up variable. This is used to invoke the default
     * methods implemented in an interface.
     */
    private void initializeDefaultHandlesLookup() {
        if (defaultMethodHandleLookup != null) {
            return;
        }

        try {
            Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.
                    getDeclaredConstructor(Class.class, int.class);
            constructor.setAccessible(true);

            defaultMethodHandleLookup = constructor.
                    newInstance(executionInterceptorInterface, MethodHandles.Lookup.PRIVATE);
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
            log.error("DiSCo(AWSv2) Unable to initialize default handles look up in the Invoke handler.");
        }
    }
}
