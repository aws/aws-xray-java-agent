package com.amazonaws.xray.agent.runtime;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.listeners.ListenerFactory;
import com.amazonaws.xray.agent.runtime.listeners.XRayListener;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.disco.agent.event.EventBus;

import java.io.File;
import java.net.URL;

import static org.mockito.Mockito.when;

public class AgentRuntimeLoaderTest {
    private final String serviceName = "TestService";
    private static final String CONFIG_FILE_SYS_PROPERTY = "com.amazonaws.xray.configFile";
    private static final String CONFIG_FILE_DEFAULT_NAME = "xray-agent.json";

    @Mock
    private XRayListener listenerMock;

    @Mock
    private ListenerFactory factoryMock;

    private XRaySDKConfiguration config;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(factoryMock.generateListener()).thenReturn(listenerMock);
        AgentRuntimeLoader.setListenerFactory(factoryMock);

        System.clearProperty(CONFIG_FILE_SYS_PROPERTY);
        config = XRaySDKConfiguration.getInstance();
        config.init(null);  // resets any static config properties
    }

    @After
    public void cleanup() {
        EventBus.removeAllListeners();
        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.defaultRecorder());  // Refresh this.
    }

    @Test
    public void testCommandLineNameSet() {
        AgentRuntimeLoader.init(serviceName);

        Assert.assertEquals(XRayTransactionState.getServiceName(), serviceName);
    }

    @Test
    public void testListenerAddedToEventBus() {
        AgentRuntimeLoader.init(null);

        Assert.assertTrue(EventBus.isListenerPresent(listenerMock));
    }

    @Test
    public void testGetDefaultConfigFile() {
        URL configFile = AgentRuntimeLoader.getConfigFile();

        Assert.assertEquals(CONFIG_FILE_DEFAULT_NAME, FilenameUtils.getName(configFile.getPath()));
    }

    @Test
    public void testGetCustomConfigFileFromFileSystem() {
        String fileName = "emptyAgentConfig.json";
        System.setProperty(CONFIG_FILE_SYS_PROPERTY,
                AgentRuntimeLoaderTest.class.getResource("/com/amazonaws/xray/agent/" + fileName).getPath());

        URL configFile = AgentRuntimeLoader.getConfigFile();

        Assert.assertEquals(fileName, FilenameUtils.getName(configFile.getPath()));
    }

    @Test
    public void testGetCustomConfigFileFromClassPath() {
        String fileName = "emptyAgentConfig.json";
        System.setProperty(CONFIG_FILE_SYS_PROPERTY, "/com/amazonaws/xray/agent/" + fileName);

        URL configFile = AgentRuntimeLoader.getConfigFile();

        // Make sure it's not using the file system
        Assert.assertFalse(new File("/com/amazonaws/xray/agent/" + fileName).exists());
        Assert.assertEquals(fileName, FilenameUtils.getName(configFile.getPath()));
    }

    @Test
    public void testNonExistentCustomConfigFile() {
        System.setProperty(CONFIG_FILE_SYS_PROPERTY, "/some/totally/fake/file");

        URL configFile = AgentRuntimeLoader.getConfigFile();

        Assert.assertNull(configFile);
    }
}
