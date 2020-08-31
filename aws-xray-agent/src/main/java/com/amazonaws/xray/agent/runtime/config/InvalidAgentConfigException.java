package com.amazonaws.xray.agent.runtime.config;

public class InvalidAgentConfigException extends RuntimeException {
    public InvalidAgentConfigException(String msg) {
        super(msg);
    }

    public InvalidAgentConfigException(String msg, Throwable err) {
        super(msg, err);
    }
}
