package com.agent.java.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.knowledge.KnowledgeRequest;
import com.agent.java.model.knowledge.KnowledgeResponse;
import com.agent.java.tool.FileSystemTools;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    /**
     * POST方式查询知识库
     * 
     * @param knowledgeRequest 查询请求
     * @return 查询结果
     */
    @PostMapping("/query")
    public KnowledgeResponse query(@RequestBody KnowledgeRequest knowledgeRequest) {
        log.info("知识查询请求: {}", knowledgeRequest.getQuery());
        ReActAgent agent = createAgent();
        try {
            Msg request = Msg.builder().textContent(knowledgeRequest.getQuery()).build();
            Msg response = agent.call(request).block(Duration.ofSeconds(30));
            String responseText = response != null ? response.getTextContent() : "无回答";
            return parseJsonResponse(responseText);
        } catch (Exception e) {
            log.error("知识查询异常: {}", e.getMessage(), e);
            return KnowledgeResponse.builder().answer("出错了: " + e.getMessage()).source(null).build();
        }
    }

    /**
     * 解析LLM返回的JSON格式响应
     * 
     * @param responseText LLM响应文本
     * @return 解析后的KnowledgeResponse对象
     */
    private KnowledgeResponse parseJsonResponse(String responseText) {
        try {
            KnowledgeResponse response = objectMapper.readValue(responseText, KnowledgeResponse.class);
            return response;
        } catch (Exception e) {
            log.warn("JSON解析失败, 返回原始文本: {}", e.getMessage());
            return KnowledgeResponse.builder().answer(responseText).source(null).build();
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
                        "你是一个智能助手。当用户询问任何问题时，你必须使用 retrieval-skill 来检索本地知识库中的信息，然后基于检索到的内容回答问题。\n" +
                                "如果本地知识库中没有相关内容，再基于你的知识回答。\n" +
                                "\n" +
                                "请严格按照以下JSON格式返回结果, 不要包含任何额外的文本或解释:\n" +
                                "{\n" +
                                "  \"answer\": \"你的回答内容\",\n" +
                                "  \"source\": \"来源文件名, 如: 智能护眼台灯-产品说明书.md, 若未使用知识库则为null\"\n" +
                                "}\n" +
                                "\n" +
                                "注意: source字段必须准确填写你检索时使用的知识库文件名, 若没有使用知识库则填写null.")
                .model(chatModel)
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }
}