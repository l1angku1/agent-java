package com.agent.java.controller;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import com.agent.java.model.KnowledgeRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final ReActAgent knowledgeRetrievalAgent;

    public KnowledgeController(ReActAgent knowledgeRetrievalAgent) {
        this.knowledgeRetrievalAgent = knowledgeRetrievalAgent;
    }

    @GetMapping("/health")
    public String health() {
        return "Knowledge Retrieval Agent is running!";
    }

    @PostMapping("/query")
    public Map<String, String> query(@RequestBody KnowledgeRequest knowledgeRequest) {
        try {
            Msg request = Msg.builder().textContent(knowledgeRequest.getQuery()).build();
            Msg response = knowledgeRetrievalAgent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam(value = "query", defaultValue = "什么是 AgentScope？") String query) {
        try {
            Msg request = Msg.builder().textContent(query).build();
            Msg response = knowledgeRetrievalAgent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }
}
