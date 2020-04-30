package com.amazonaws.xray.agent;

/**
 * Interface for the bridge between the agent runtime loader and the application class loader.
 * This should be implemented in the application classloader.
 */
public interface AgentRuntimeLoaderInterface
{
    // Maybe in the future convert serviceName into a configuration map
    void init(String serviceName);
}
