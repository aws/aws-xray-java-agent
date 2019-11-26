package com.amazonaws.xray.agent;

import software.amazon.disco.agent.event.EventBus;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.listeners.XRayListener;
import com.amazonaws.xray.agent.models.XRayTransactionState;
import com.amazonaws.xray.contexts.SegmentContextResolverChain;
import com.amazonaws.xray.strategy.DefaultContextMissingStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({XRayListener.class, XRaySDKConfiguration.class})
@PowerMockIgnore("javax.net.ssl.*")
public class AgentRuntimeLoaderTest {
    private final String serviceName = "TestService";

    @Mock
    private XRayListener xrayListener;

    private AgentRuntimeLoader agentRuntimeLoader;

    @Before
    public void setup() {
        agentRuntimeLoader = new AgentRuntimeLoader();
    }

    @After
    public void cleanup() {
        EventBus.removeAllListeners();
        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.defaultRecorder());  // Refresh this.
    }

    @Test
    public void testInit() {
        Assert.assertFalse(EventBus.isListenerPresent(xrayListener));
        AgentRuntimeLoader agentSpy = spy(agentRuntimeLoader);

        AWSXRayRecorder preRecorder = AWSXRay.getGlobalRecorder();
        when(agentSpy.xrayListenerGenerator()).thenReturn(xrayListener);

        agentSpy.init(serviceName);

        Assert.assertTrue(EventBus.isListenerPresent(xrayListener));
        // Configuration should be done in the runtime loader init, so we expect the global recorder to change--to reflect
        // what the agent has configured.
        Assert.assertNotEquals(preRecorder, AWSXRay.getGlobalRecorder());
        Assert.assertEquals(XRayTransactionState.getServiceName(), serviceName);
    }

    @Test
    public void testConfiguration() throws Throwable {
        XRaySDKConfiguration xRaySDKConfiguration = mock(XRaySDKConfiguration.class);
        PowerMockito.stub(PowerMockito.method(XRaySDKConfiguration.class, "getInstance")).toReturn(xRaySDKConfiguration);

        Assert.assertFalse(EventBus.isListenerPresent(xrayListener));

        agentRuntimeLoader.init(serviceName);

        verify(xRaySDKConfiguration).setContextMissingStrategy(isA(DefaultContextMissingStrategy.class));
        verify(xRaySDKConfiguration).setSegmentContextResolverChain(isA(SegmentContextResolverChain.class));
        verify(xRaySDKConfiguration).setSamplingStrategy(isA(CentralizedSamplingStrategy.class));
        verify(xRaySDKConfiguration).init();
    }
}
