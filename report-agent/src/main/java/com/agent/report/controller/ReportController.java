package com.agent.report.controller;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import com.agent.report.model.ReportRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReActAgent weatherAgent;
    private final ReActAgent reportAgent;

    public ReportController(ReActAgent weatherAgent, ReActAgent reportAgent) {
        this.weatherAgent = weatherAgent;
        this.reportAgent = reportAgent;
    }

    @GetMapping("/health")
    public String health() {
        return "Report Agent is running!";
    }

    @GetMapping("/weather")
    public Map<String, String> weather(@RequestParam(value = "message", defaultValue = "杭州天气怎么样？") String message) {
        try {
            Msg request = Msg.builder().textContent(message).build();
            Msg response = weatherAgent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public Map<String, String> generate(@RequestBody ReportRequest reportRequest) {
        try {
            String prompt = String.format("生成并发送日报。日期：%s，手动补充内容：%s，发送至：%s。%s",
                    reportRequest.getDate(),
                    reportRequest.getManualContent(),
                    reportRequest.getLeaderEmail(),
                    reportRequest.getMessage() != null ? reportRequest.getMessage() : "");

            Msg request = Msg.builder().textContent(prompt).build();
            Msg response = reportAgent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Report Agent!";
    }
}