package com.amazonaws.xray.agent.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.models.XRayTransactionState;
import com.amazonaws.xray.emitters.UDPEmitter;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

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
        configMap.put("maxStackTraceLength", "20");
        configMap.put("streamingThreshold", "10");
        configMap.put("awsSDKVersion", "1");
        configMap.put("awsServiceHandlerManifest", "myTestHandler");
        configMap.put("tracingEnabled", "false");
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

        config.init();

        Assert.assertEquals(JVM_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testEnvServiceName() {
        environmentVariables.set(SegmentNamingStrategy.NAME_OVERRIDE_ENVIRONMENT_VARIABLE_KEY, ENV_NAME);
        System.setProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY, SYS_NAME);
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertEquals(ENV_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testSysServiceName() {
        System.setProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY, SYS_NAME);
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertEquals(SYS_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testConfigServiceName() {
        configMap.put("serviceName", CONFIG_NAME);
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertEquals(CONFIG_NAME, XRayTransactionState.getServiceName());
    }

    @Test
    public void testDefaultServiceName() {
        config.init();

        Assert.assertEquals(AgentConfiguration.DEFAULT_SERVICE_NAME, XRayTransactionState.getServiceName());
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

        config.init();
    }

    @Test
    public void testIgnoreErrorContextMissingStrategy() {
        configMap.put("contextMissingStrategy", "IGNORE_ERROR");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getContextMissingStrategy() instanceof IgnoreErrorContextMissingStrategy);
    }

    @Test
    public void testContextMissingStrategyIsCaseInsensitive() {
        configMap.put("contextMissingStrategy", "igNoRe_ErrOR");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

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
        configMap.put("sampingRulesManifest", "notAFile");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init(builderMock);

        ArgumentCaptor<CentralizedSamplingStrategy> captor = ArgumentCaptor.forClass(CentralizedSamplingStrategy.class);
        verify(builderMock).withSamplingStrategy(captor.capture());
        Assert.assertNotNull(captor.getValue());
    }

    @Test
    public void testValidSamplingRuleManifest() {
        AWSXRayRecorderBuilder builderMock = mock(AWSXRayRecorderBuilder.class);
        configMap.put("sampingRulesManifest", "/path/to/file");
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

        config.init();
    }

    @Test
    public void testLocalSamplingStrategy() {
        configMap.put("samplingStrategy", "LOCAL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof LocalizedSamplingStrategy);
    }

    @Test
    public void testNoSamplingStrategy() {
        configMap.put("samplingStrategy", "NONE");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof NoSamplingStrategy);
    }

    @Test
    public void testAllSamplingStrategy() {
        configMap.put("samplingStrategy", "ALL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof AllSamplingStrategy);
    }

    @Test
    public void testSamplingStrategyIsCaseInsensitive() {
        configMap.put("samplingStrategy", "LoCaL");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

        Assert.assertTrue(AWSXRay.getGlobalRecorder().getSamplingStrategy() instanceof LocalizedSamplingStrategy);
    }

    @Test
    public void testMaxStackTraceLength() {
        configMap.put("maxStackTraceLength", "42");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();

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
        configMap.put("awsSDKVersion", "11");
        configMap.put("awsServiceHandlerManifest", "/path/to/manifest");
        config.setAgentConfiguration(new AgentConfiguration(configMap));

        config.init();
    }

    @Test
    public void testValidServiceManifest() throws MalformedURLException {
        configMap.put("awsServiceHandlerManifest", "/path/to/manifest");
        config.setAgentConfiguration(new AgentConfiguration(configMap));
        URL location = new File("/path/to/manifest").toURI().toURL();

        config.init();

        Assert.assertEquals(location.getPath(), config.getAwsServiceHandlerManifest().getPath());
    }
}
