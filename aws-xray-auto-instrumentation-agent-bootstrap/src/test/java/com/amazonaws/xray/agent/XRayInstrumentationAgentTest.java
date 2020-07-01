package com.amazonaws.xray.agent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.DiscoAgentTemplate;

import java.lang.instrument.Instrumentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({XRayInstrumentationAgent.class, AgentRuntimeLoaderInterface.class, DiscoAgentTemplate.class})
public class XRayInstrumentationAgentTest {
    private static final String GET_AGENT_RUNTIME_METHOD = "getAgentRuntimeLoader";
    private final String serviceName = "ControlPlane";

    @Mock
    private Instrumentation instrumentation;

    @Mock
    private DiscoAgentTemplate discoAgentTemplate;

    @Mock
    private AgentRuntimeLoaderInterface agentRuntimeLoader;

    @Before
    public void setup() throws Exception {
        whenNew(DiscoAgentTemplate.class)
                .withArguments("servicename="+serviceName)
                .thenReturn(discoAgentTemplate);
    }

    @Test
    /**
     * Verify that the agent template was initialized and that agentRuntimeLoader.init was called with the
     * correct serviceName passed in. @code{serviceName} should've been parsed from the agentArgs.
     */
    public void testAgentServiceName() throws Exception {
        PowerMockito.stub(PowerMockito.method(XRayInstrumentationAgent.class, GET_AGENT_RUNTIME_METHOD)).toReturn(agentRuntimeLoader);

        XRayInstrumentationAgent.premain("servicename=" + serviceName, instrumentation);

        verifyNew(DiscoAgentTemplate.class).withArguments("servicename=" + serviceName);
        verify(discoAgentTemplate, times(1)).install(any(), anySet());
        verify(agentRuntimeLoader, times(1)).init(serviceName);
    }

    @Test
    /**
     * Tests that failure to obtain the runtime agent (as a result of perhaps forgetting to put in the dependency)
     * should prevent the agent from installing interceptors.
     */
    public void testFailObtainRuntimeAgent(){
        PowerMockito.stub(PowerMockito.method(XRayInstrumentationAgent.class, GET_AGENT_RUNTIME_METHOD))
                .toThrow(new ClassNotFoundException("Pretending that we can't find the agent runtime loader."));

        XRayInstrumentationAgent.premain("servicename=" + serviceName, instrumentation);

        verify(agentRuntimeLoader, times(0)).init(serviceName);
    }
}
