package software.amazon.disco.agent.awsv1;

import software.amazon.disco.agent.event.Event;
import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.event.Listener;
import software.amazon.disco.agent.event.ServiceDownstreamRequestEvent;
import software.amazon.disco.agent.event.ServiceDownstreamResponseEvent;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Response;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Matchers.any;

public class AWSClientInvokeInterceptorTests {
    private TestListener testListener;

    @Before
    public void before() {
        EventBus.removeAllListeners();
        EventBus.addListener(testListener = new TestListener());
    }

    @After
    public void after() {
        EventBus.removeAllListeners();
    }

    @Test
    public void testMethodMatcherSucceeds() throws Exception {
        Assert.assertTrue(methodMatches("doInvoke", AmazonSQSClient.class));
    }

    @Test(expected = NoSuchMethodException.class)
    public void testMethodMatcherFailsOnMethod() throws Exception {
        methodMatches("notAMethod", AmazonDynamoDBClient.class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void testMethodMatcherFailsOnClass() throws Exception {
        Assert.assertFalse(methodMatches("doInvoke", String.class));
    }

    @Test
    public void testClassMatcherSucceeds() {
        Assert.assertTrue(classMatches(AmazonSNSClient.class));
    }

    @Test
    public void testClassMatcherFails() {
        Assert.assertFalse(classMatches(String.class));
    }

    @Test
    public void testClassMatcherFailsOnAbstractType() {
        Assert.assertFalse(classMatches(FakeAWSClass.class));
    }

    @Test
    public void testDoInvoke() throws Throwable {
        Response response = new Response<>("Output", null);
        Callable<Object> zuper = ()->response;

        DefaultRequest request = new DefaultRequest(
                new UpdateTableRequest("tableName", new ProvisionedThroughput(1L, 1L)), "AmazonDynamoDBv2");
        AWSClientInvokeInterceptor.doInvoke(request,
                "FakeMethodName",
                zuper);

        Assert.assertNotNull(testListener.request);
        Assert.assertNotNull(testListener.response);
        Assert.assertEquals(request, testListener.request.getRequest());
        Assert.assertEquals(testListener.request, testListener.response.getRequest());
        Assert.assertEquals(response, testListener.response.getResponse());
        Assert.assertEquals("Output", ((Response) testListener.response.getResponse()).getAwsResponse());
        Assert.assertNull(testListener.response.getThrown());
    }

    @Test
    public void testDoInvokeExceptionHandling() {
        Callable<Object> zuper = ()-> {
            throw new ClassCastException();
        };

        DefaultRequest request = new DefaultRequest(
                new UpdateTableRequest("tableName", new ProvisionedThroughput(1L, 1L)), "AmazonDynamoDBv2");

        try {
            AWSClientInvokeInterceptor.doInvoke(request,
                    "FakeMethodName",
                    zuper);
            //should throw exception and not run this line
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertThat(t, instanceOf(ClassCastException.class));

            Assert.assertNotNull(testListener.request);
            Assert.assertNotNull(testListener.response);
            Assert.assertEquals(request, testListener.request.getRequest());
            Assert.assertEquals(testListener.request, testListener.response.getRequest());
            Assert.assertNull(testListener.response.getResponse());
            Assert.assertEquals(ClassCastException.class, testListener.response.getThrown().getClass());
        }
    }

    @Test
    public void testDoInvokeIntrusiveInterceptor() throws Throwable {
        Response response = new Response<>("Output", null);
        Callable<Object> zuper = ()->response;


        DefaultRequest request = new DefaultRequest(
                new UpdateTableRequest("tableName", new ProvisionedThroughput(1L, 1L)), "AmazonDynamoDBv2");
        AWSClientInvokeInterceptor.doInvoke(request,
                "FakeMethodName",
                zuper);

        Assert.assertNotNull(testListener.request);
        Assert.assertNotNull(testListener.response);
        Assert.assertEquals(request, testListener.request.getRequest());
        Assert.assertEquals(testListener.request, testListener.response.getRequest());
        Assert.assertEquals(testListener.response.getResponse().getClass(), response.getClass());
        Assert.assertEquals("Output", ((Response) testListener.response.getResponse()).getAwsResponse());
        Assert.assertNull(testListener.response.getThrown());
    }

    @Test
    public void testInstallation() {
        AgentBuilder agentBuilder = Mockito.mock(AgentBuilder.class);
        AgentBuilder.Identified.Extendable extendable = Mockito.mock(AgentBuilder.Identified.Extendable.class);
        AgentBuilder.Identified.Narrowable narrowable = Mockito.mock(AgentBuilder.Identified.Narrowable.class);
        AWSClientInvokeInterceptor interceptor = new AWSClientInvokeInterceptor();
        Mockito.when(agentBuilder.type(Mockito.any(ElementMatcher.class))).thenReturn(narrowable);
        Mockito.when(narrowable.transform(any(AgentBuilder.Transformer.class))).thenReturn(extendable);
        AgentBuilder result = interceptor.install(agentBuilder);
        Assert.assertSame(extendable, result);
    }

    /**
     * Helper function to test the class matcher matching
     * @param clazz Class type we are validating
     * @return true if matches else false
     */
    private boolean classMatches(Class clazz) {
        AWSClientInvokeInterceptor interceptor = new AWSClientInvokeInterceptor(){};
        return interceptor.buildClassMatcher().matches(new TypeDescription.ForLoadedType(clazz));
    }

    /**
     * Helper function to test the method matcher against an input class
     * @param methodName name of method
     * @param paramType class we are verifying contains the method
     * @return true if matches, else false
     * @throws NoSuchMethodException
     */
    private boolean methodMatches(String methodName, Class paramType) throws NoSuchMethodException {
        Method method = null;
        for (Method m: paramType.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                Assert.assertNull(method);
                method = m;
            }
        }

        if (method == null) {
            throw new NoSuchMethodException();
        }

        AWSClientInvokeInterceptor interceptor = new AWSClientInvokeInterceptor();
        return interceptor.buildMethodMatcher()
                .matches(new MethodDescription.ForLoadedMethod(method));
    }

    private static class TestListener implements Listener {
        ServiceDownstreamRequestEvent request;
        ServiceDownstreamResponseEvent response;
        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void listen(Event e) {
            if (e instanceof ServiceDownstreamRequestEvent) {
                request = (ServiceDownstreamRequestEvent)e;
            } else if (e instanceof ServiceDownstreamResponseEvent) {
                response = (ServiceDownstreamResponseEvent)e;
            } else  {
                Assert.fail("Unexpected event");
            }
        }
    }

    /**
     * Fake AWS class to test that only non-abstract classes are instrumented
     */
    public abstract class FakeAWSClass extends AmazonWebServiceClient {
        public FakeAWSClass(ClientConfiguration clientConfiguration) {
            super(clientConfiguration);
        }
    }

}
