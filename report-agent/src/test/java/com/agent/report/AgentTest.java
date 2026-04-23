package com.agent.report;

import io.agentscope.core.ReActAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class AgentTest {

    @Autowired
    private ReActAgent weatherAgent;

    @Test
    public void contextLoads() {
        assertNotNull(weatherAgent);
    }
}
