package com.amazonaws.xray.agent.config;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class AgentConfigurationTest {
    @Test
    public void testEmptyMapUsesDefaults() {
        AgentConfiguration defaultConfig = new AgentConfiguration();
        AgentConfiguration mapConfig = new AgentConfiguration(new HashMap<>());

        Assert.assertEquals(defaultConfig, mapConfig);
    }
}
