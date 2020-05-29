package com.amazonaws.xray.agent;

import com.amazonaws.xray.agent.handlers.downstream.AWSHandler;
import com.amazonaws.xray.agent.handlers.downstream.AWSV2Handler;
import com.amazonaws.xray.agent.listeners.XRayListener;
import org.apache.commons.io.FilenameUtils;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import software.amazon.disco.agent.event.EventBus;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.models.XRayTransactionState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.net.URL;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AgentRuntimeLoader.class, EventBus.class, AWSHandler.class, AWSV2Handler.class})
@PowerMockIgnore("javax.net.ssl.*")
public class AgentRuntimeLoaderTest {
    private final String serviceName = "TestService";
    private static final String CONFIG_FILE_SYS_PROPERTY="com.amazonaws.xray.configFile";
    private static final String CONFIG_FILE_DEFAULT_NAME="xray-agent.json";

    @Mock
    private XRayListener listenerMock;

    @Mock
    private AWSHandler v1Handler;

    @Mock
    private AWSV2Handler v2Handler;

    private AgentRuntimeLoader agentRuntimeLoader;
    private URL awsManifest;
    private XRaySDKConfiguration config;

    @Before
    public void setup() throws Exception {
        agentRuntimeLoader = new AgentRuntimeLoader();
        awsManifest = new File("/path/to/manifest").toURI().toURL();
        config = XRaySDKConfiguration.getInstance();
        System.clearProperty(CONFIG_FILE_SYS_PROPERTY);

        PowerMockito.whenNew(AWSHandler.class)
                .withAnyArguments()
                .thenReturn(v1Handler);

        PowerMockito.whenNew(AWSV2Handler.class)
                .withAnyArguments()
                .thenReturn(v2Handler);
    }

    @After
    public void cleanup() {
        EventBus.removeAllListeners();
        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.defaultRecorder());  // Refresh this.
    }

    @Test
    public void testCommandLineNameSet() {
        agentRuntimeLoader.init(serviceName);

        Assert.assertEquals(XRayTransactionState.getServiceName(), serviceName);
    }

    @Test
    public void testListenerAddedToEventBus() {
        AgentRuntimeLoader loaderSpy = Mockito.spy(agentRuntimeLoader);
        Mockito.doReturn(listenerMock).when(loaderSpy).generateXRayListener();
        loaderSpy.init(null);

        Assert.assertTrue(EventBus.isListenerPresent(listenerMock));
    }

    @Test
    public void testGetDefaultConfigFile() {
        URL configFile = AgentRuntimeLoader.getConfigFile();

        Assert.assertEquals(CONFIG_FILE_DEFAULT_NAME, FilenameUtils.getName(configFile.getPath()));
    }

    @Test
    public void testGetCustomConfigFile() {
        String fileName = "emptyAgentConfig.json";
        System.setProperty(CONFIG_FILE_SYS_PROPERTY,
                AgentRuntimeLoaderTest.class.getResource("/com/amazonaws/xray/agent/" + fileName).getPath());

        URL configFile = AgentRuntimeLoader.getConfigFile();

        Assert.assertEquals(fileName, FilenameUtils.getName(configFile.getPath()));
    }

    @Test
    public void testDefaultAWSManifests() throws Exception {
        config.init();

        agentRuntimeLoader.generateXRayListener();

        PowerMockito.verifyNew(AWSHandler.class).withNoArguments();
        PowerMockito.verifyNew(AWSV2Handler.class).withNoArguments();
    }

    @Test
    public void testCustomAWSV1Manifest() throws Exception {
        URL agentConfig = AgentRuntimeLoaderTest.class.getResource("/com/amazonaws/xray/agent/awsV1AgentConfig.json");
        config.init(agentConfig);

        agentRuntimeLoader.generateXRayListener();

        PowerMockito.verifyNew(AWSHandler.class).withArguments(awsManifest);
        PowerMockito.verifyNew(AWSV2Handler.class).withNoArguments();
    }

    @Test
    public void testCustomAWSV2Manifest() throws Exception {
        URL agentConfig = AgentRuntimeLoaderTest.class.getResource("/com/amazonaws/xray/agent/awsV2AgentConfig.json");
        config.init(agentConfig);

        agentRuntimeLoader.generateXRayListener();

        PowerMockito.verifyNew(AWSHandler.class).withNoArguments();
        PowerMockito.verifyNew(AWSV2Handler.class).withArguments(awsManifest);
    }
}
