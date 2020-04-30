package com.amazonaws.xray.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.disco.agent.DiscoAgentTemplate;
import software.amazon.disco.agent.interception.Installable;

import java.lang.instrument.Instrumentation;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.whenNew;


@RunWith(PowerMockRunner.class)
@PrepareForTest({XRayInstrumentationAgent.class, AgentRuntimeLoaderInterface.class, DiscoAgentTemplate.class})
public class XRayInstrumentationAgentTest {
    private static final String GET_AGENT_RUNTIME_METHOD = "getAgentRuntimeLoader";
    private final String serviceName = "ControlPlane";
    public static final String TRACE_HEADER_KEY = "X-Amzn-Trace-Id";

    @Mock
    private Instrumentation instrumentation;

    @Mock
    private DiscoAgentTemplate discoAgentTemplate;

    @Mock
    private AgentRuntimeLoaderInterface agentRuntimeLoader;

    @Before
    public void setup() throws Exception {
        // In order to avoid weird classloader issues, we partially static mock this factory method to consistently
        // return our mocked AgentRuntimeLoader; otherwise, they would exist in different classloaders, causing issues.
        PowerMockito.stub(PowerMockito.method(XRayInstrumentationAgent.class, GET_AGENT_RUNTIME_METHOD)).toReturn(agentRuntimeLoader);

        whenNew(DiscoAgentTemplate.class)
                .withArguments("servicename="+serviceName)
                .thenReturn(discoAgentTemplate);
    }

    @After
    public void cleanup() {

    }


    @Test
    public void testAgentServiceName() throws Exception {
        XRayInstrumentationAgent.premain("servicename=" + serviceName, instrumentation);

        // Verify that the agent template was initialized and that agentRuntimeLoader.init was called with the
        // correct serviceName passed in. serviceName should've been parsed from the agentArgs.
        verifyNew(DiscoAgentTemplate.class).withArguments("servicename=" + serviceName);
        verify(discoAgentTemplate, times(1)).install(any(), anySetOf(Installable.class));
        verify(agentRuntimeLoader, times(1)).init(serviceName);

        // Unit Test
        // Agent release for itneg tests shadow artifact
    }

//    // TODO: Mocking private static methods with PowerMockito 2.x isn't the same in 1.6.x
//    @Test
//    public void testFailObtainRuntimeAgent() throws Exception{
//        // Tests that failure to obtain the runtime agent (as a result of perhaps forgetting to put in the dependency)
//        // should prevent the agent from installing interceptors.
//        PowerMockito.stub(PowerMockito.method(XRayInstrumentationAgent.class, GET_AGENT_RUNTIME_METHOD))
//                .toThrow(new ClassNotFoundException("Pretending that we can't find the agent runtime loader."));
//
//        XRayInstrumentationAgent.premain("servicename=" + serviceName, instrumentation);
//
//        verify(discoAgentTemplate, times(0)).install(any(), anySetOf(Installable.class));
//        verify(agentRuntimeLoader, times(0)).init(serviceName);
//    }
}
