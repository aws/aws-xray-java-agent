package com.amazonaws.xray.agent.runtime.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionContext;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.contexts.ThreadLocalSegmentContext;
import com.amazonaws.xray.emitters.UDPEmitter;
import com.amazonaws.xray.log4j.Log4JSegmentListener;
import com.amazonaws.xray.strategy.DefaultStreamingStrategy;
import com.amazonaws.xray.strategy.DefaultThrowableSerializationStrategy;
import com.amazonaws.xray.strategy.IgnoreErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.SegmentNamingStrategy;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class XRaySDKConfigurationTest {
    private static final String JVM_NAME = "jvm_name";
    private static final String ENV_NAME = "env_name";
    private static final String SYS_NAME = "sys_name";
    private static final String CONFIG_NAME = "config_name";

    XRaySDKConfiguration config;
    Map<String, String> configMap;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setup() {
        AWSXRay.setGlobalRecorder(new AWSXRayRecorder());
        configMap = new HashMap<>();
        configMap.put("pluginsEnabled", "false");  // Checking for EC2 endpoint on each test takes a long time
        config = new XRaySDKConfiguration();
    }

    @After
    public void cleanup() {
        XRayTransactionState.setServiceName(null);
        System.clearProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY);
        System.clearProperty(XRaySDKConfiguration.ENABLED_SYSTEM_PROPERTY_KEY);
        environmentVariables.set(SegmentNamingStrategy.NAME_OVERRIDE_ENVIRONMENT_VARIABLE_KEY, null);
        environmentVariables.set(XRaySDKConfiguration.ENABLED_ENVIRONMENT_VARIABLE_KEY, null);
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInitWithNonexistentFile() throws MalformedURLException {
        config.init(new File("/some/nonexistent/file").toURI().toURL());
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInitWithMalformedFile() {
        config.init(XRaySDKConfigurationTest.class.getResource("/com/amazonaws/xray/agent/malformedAgentConfig.json"));
    }

    @Test
    public void testInitWithValidFile() {
        configMap.put("serviceName", "myServiceName");
        configMap.put("contextMissingStrategy", "myTestContext");
        configMap.put("daemonAddress", "myTestAddress");
        configMap.put("samplingStrategy", "myTestSampling");
        configMap.put("samplingRulesManifest", "myTestManifest");
        configMap.put("traceIdInjection", "Log4J");
        configMap.put("traceIdInjectionPrefix", "prefix");
        configMap.put("maxStackTraceLength", "20");
        configMap.put("streamingThreshold", "10");
        configMap.put("awsSdkVersion", "1");
        configMap.put("awsServiceHandlerManifest", "myTestHandler");
        configMap.put("pluginsEnabled", "false");
        configMap.put("tracingEnabled", "false");
        configMap.put("collectSqlQueries", "true");
        configMap.put("contextPropagation", "false");
        configMap.put("traceIncomingRequests", "false");
        AgentConfiguration agentConfig = new AgentConfiguration(configMap);
        config.init(XRaySDKConfigurationTest.class.getResource("/com/amazonaws/xray/agent/validAgentConfig.json"));

        Assert.assertEquals(agentConfig, config.getAgentConfiguration());
    }

    @Test
    public void testEnvTracingDisabled() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        environmentVariables.set(XRaySDKConfiguration.ENABLED_ENVIRONMENT_VARIABLE_KEY, "false");
        System.setProperty(XRaySDKConfiguration.ENABLED_SYSTEM_PROPERTY_KEY, "true");

        config.init(builderMock);

        Assert.assertNull(XRayTransactionState.getServiceName());
        verify(builderMock, never()).build();
    }

    @Test
    public void testSysTracingDisabled() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        System.setProperty(XRaySDKConfiguration.ENABLED_SYSTEM_PROPERTY_KEY, "false");

        config.init(builderMock);

        Assert.assertNull(XRayTransactionState.getServiceName());
        verify(builderMock, never()).build();
    }

    @Test
    public void testFileTracingDisabled() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("tracingEnabled", "false");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        Assert.assertNull(XRayTransactionState.getServiceName());
        verify(builderMock, never()).build();
    }

    @Test
    public void testJVMServiceName() {
        XRayTransactionState.setServiceName(JVM_NAME);
        environmentVariables.set(SegmentNamingStrategy.NAME_OVERRIDE_ENVIRONMENT_VARIABLE_KEY, ENV_NAME);
        System.setProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY, SYS_NAME);
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertEquals(JVM_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testEnvServiceName() {
        environmentVariables.set(SegmentNamingStrategy.NAME_OVERRIDE_ENVIRONMENT_VARIABLE_KEY, ENV_NAME);
        System.setProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY, SYS_NAME);
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertEquals(ENV_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testSysServiceName() {
        System.setProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY, SYS_NAME);
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertEquals(SYS_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testConfigServiceName() {
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertEquals(CONFIG_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testDefaultServiceName() {
        config.init();

        Assert.assertEquals(AgentConfiguration.DEFAULT_SERVICE_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testPluginsEnabledByDefault() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);

        config.init(builderMock);

        verify(builderMock).withDefaultPlugins();
    }

    @Test
    public void testPluginsDisabled() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("pluginsEnabled", "false");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        verify(builderMock, never()).withDefaultPlugins();
    }

    @Test
    public void testDefaultContextMissingStrategy() {
        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getContextMissingStrategy() instanceof LogErrorContextMissingStrategy);
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInvalidContextMissingStrategy() {
        configMap.put("contextMissingStrategy", "FAKE_STRATEGY");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());
    }

    @Test
    public void testIgnoreErrorContextMissingStrategy() {
        configMap.put("contextMissingStrategy", "IGNORE_ERROR");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getContextMissingStrategy() instanceof IgnoreErrorContextMissingStrategy);
    }

    @Test
    public void testContextMissingStrategyIsCaseInsensitive() {
        configMap.put("contextMissingStrategy", "igNoRe_ErrOR");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getContextMissingStrategy() instanceof IgnoreErrorContextMissingStrategy);
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInvalidDaemonAddress() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("daemonAddress", "invalidAddress");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);
    }

    @Test
    public void testValidDaemonAddress() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("daemonAddress", "123.4.5.6:1234");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        // TODO: Make these tests simpler & better by comparing the actual value we set to the recorder's daemon
        //       address once it's exposed. See https://github.com/aws/aws-xray-sdk-java/issues/148
        ArgumentCaptor<UDPEmitter> captor = ArgumentCaptor.forClass(UDPEmitter.class);
        verify(builderMock).withEmitter(captor.capture());
        Assert.assertNotNull(captor.getValue());
    }

    @Test
    public void testInvalidSamplingRuleManifest() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("samplingRulesManifest", "notAFile");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        ArgumentCaptor<CentralizedSamplingStrategy> captor = ArgumentCaptor.forClass(CentralizedSamplingStrategy.class);
        verify(builderMock).withSamplingStrategy(captor.capture());
        Assert.assertNotNull(captor.getValue());
    }

    @Test
    public void testValidSamplingRuleManifest() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("samplingRulesManifest", "/path/to/file");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        // TODO: Verify the correct URL is used and add more Manifest tests once this PR is released:
        //  https://github.com/aws/aws-xray-sdk-java/pull/149
        ArgumentCaptor<CentralizedSamplingStrategy> captor = ArgumentCaptor.forClass(CentralizedSamplingStrategy.class);
        verify(builderMock).withSamplingStrategy(captor.capture());
        Assert.assertNotNull(captor.getValue());
    }

    @Test
    public void testDefaultSamplingStrategy() {
        config.init();
        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof CentralizedSamplingStrategy);
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInvalidSamplingStrategy() {
        configMap.put("samplingStrategy", "FakeStrategy");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());
    }

    @Test
    public void testLocalSamplingStrategy() {
        configMap.put("samplingStrategy", "LOCAL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof LocalizedSamplingStrategy);
    }

    @Test
    public void testNoSamplingStrategy() {
        configMap.put("samplingStrategy", "NONE");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof NoSamplingStrategy);
    }

    @Test
    public void testAllSamplingStrategy() {
        configMap.put("samplingStrategy", "ALL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof AllSamplingStrategy);
    }

    @Test
    public void testSamplingStrategyIsCaseInsensitive() {
        configMap.put("samplingStrategy", "LoCaL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof LocalizedSamplingStrategy);
    }

    @Test
    public void testTraceIdInjection() {
        config.init(AWSXRayRecorderBuilder.standard());

        // A little fragile, depends on the order they're added in XRaySDKConfiguration
        Log4JSegmentListener listener = (Log4JSegmentListener) AWSXRay.getGlobalRecorder().getSegmentListeners().get(0);
        Assert.assertEquals(2, AWSXRay.getGlobalRecorder().getSegmentListeners().size());
        Assert.assertEquals("", listener.getPrefix());
    }

    @Test
    public void testDisableTraceIdInjection() {
        configMap.put("traceIdInjection", "false");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        // A little fragile, depends on the order they're added in XRaySDKConfiguration
        Assert.assertEquals(0, AWSXRay.getGlobalRecorder().getSegmentListeners().size());
    }

    @Test
    public void testTraceIdInjectionPrefix() {
        configMap.put("traceIdInjectionPrefix", "my-prefix");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Log4JSegmentListener listener = (Log4JSegmentListener) AWSXRay.getGlobalRecorder().getSegmentListeners().get(0);
        Assert.assertEquals(2, AWSXRay.getGlobalRecorder().getSegmentListeners().size());
        Assert.assertEquals("my-prefix", listener.getPrefix());
    }

    @Test
    public void testMaxStackTraceLength() {
        configMap.put("maxStackTraceLength", "42");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        DefaultThrowableSerializationStrategy strategy =
                (DefaultThrowableSerializationStrategy) AWSXRay.getGlobalRecorder().getThrowableSerializationStrategy();

        Assert.assertEquals(42, strategy.getMaxStackTraceLength());
    }

    @Test
    public void testStreamingThreshold() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("streamingThreshold", "42");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        // TODO: Verify the correct threshold is used once this PR is released:
        //  https://github.com/aws/aws-xray-sdk-java/pull/149
        ArgumentCaptor<DefaultStreamingStrategy> captor = ArgumentCaptor.forClass(DefaultStreamingStrategy.class);
        verify(builderMock).withStreamingStrategy(captor.capture());
        Assert.assertNotNull(captor.getValue());
    }

    @Test(expected = InvalidAgentConfigException.class)
    public void testInvalidVersionNumber() {
        configMap.put("awsSdkVersion", "11");
        configMap.put("awsServiceHandlerManifest", "/path/to/manifest");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());
    }

    @Test
    public void testValidServiceManifest() throws MalformedURLException {
        configMap.put("awsServiceHandlerManifest", "/path/to/manifest");
        config.setAgentConfiguration(new AgentConfiguration(configMap));
        URL location = new File("/path/to/manifest").toURI().toURL();

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertEquals(location.getPath(), config.getAwsServiceHandlerManifest().getPath());
    }

    @Test
    public void testCollectSqlQueries() {
        configMap.put("collectSqlQueries", "true");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(config.shouldCollectSqlQueries());
    }

    @Test
    public void testDefaultContextPropagation() {
        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSegmentContextResolverChain().resolve() instanceof XRayTransactionContext);
    }

    @Test
    public void testContextPropagation() {
        configMap.put("contextPropagation", "false");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(AWSXRayRecorderBuilder.standard());

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSegmentContextResolverChain().resolve() instanceof ThreadLocalSegmentContext);
    }

    @Test
    public void testLazyLoadTraceIdInjection() {
        AWSXRayRecorder recorder = AWSXRayRecorderBuilder.defaultRecorder();
        config.setAgentConfiguration(new AgentConfiguration());
        config.lazyLoadTraceIdInjection(recorder);

        Assert.assertEquals(2, recorder.getSegmentListeners().size());
    }
}
