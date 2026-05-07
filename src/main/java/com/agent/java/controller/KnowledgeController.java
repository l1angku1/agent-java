package com.agent.java.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.knowledge.KnowledgeRequest;
import com.agent.java.tool.FileSystemTools;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识检索控制器
 * 提供知识库问答功能
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final OpenAIChatModel chatModel;
    private final FileSystemTools fileSystemTools;

    /**
     * 健康检查
     * 
     * @return 状态信息
     */
    @GetMapping("/health")
    public String health() {
        return "Knowledge Retrieval Agent is running!";
    }

    /**
     * POST方式查询知识库
     * 
     * @param knowledgeRequest 查询请求
     * @return 查询结果
     */
    @PostMapping("/query")
    public Map<String, String> query(@RequestBody KnowledgeRequest knowledgeRequest) {
        ReActAgent agent = createAgent();
        try {
            Msg request = Msg.builder().textContent(knowledgeRequest.getQuery()).build();
            Msg response = agent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }

    /**
     * GET方式查询知识库
     * 
     * @param query 查询语句
     * @return 查询结果
     */
    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam(value = "query", defaultValue = "什么是 AgentScope？") String query) {
        ReActAgent agent = createAgent();
        try {
            Msg request = Msg.builder().textContent(query).build();
            Msg response = agent.call(request).block(Duration.ofSeconds(30));
            return Map.of("answer", response != null ? response.getTextContent() : "无回答");
        } catch (Exception e) {
            return Map.of("answer", "出错了: " + e.getMessage());
        }
    }

    /**
     * 创建Agent
     * 
     * @return ReActAgent实例
     */
    private ReActAgent createAgent() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(fileSystemTools);

        SkillBox skillBox = new SkillBox(toolkit);
        List<AgentSkill> skills;
        try (ClasspathSkillRepository repository = new ClasspathSkillRepository("skills")) {
            skills = repository.getAllSkills();
            log.info("加载到的技能数量: {}", skills.size());
            for (AgentSkill skill : skills) {
                log.info("加载到的技能: {}", skill.getName());
                skillBox.registration().skill(skill).apply();
            }
        } catch (IOException e) {
            log.error("技能加载失败", e);
            skills = Collections.emptyList();
        }

        return ReActAgent.builder()
                .name("KnowledgeRetrievalAgent")
                .sysPrompt(
                        "你是一个智能助手。当用户询问任何问题时，你必须使用 retrieval-skill 来检索本地知识库中的信息，然后基于检索到的内容回答问题。如果本地知识库中没有相关内容，再基于你的知识回答。")
                .model(chatModel)
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }
}