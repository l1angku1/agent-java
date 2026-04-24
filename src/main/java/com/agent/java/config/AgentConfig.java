package com.agent.java.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agent.java.tool.FileSystemTools;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;

@Slf4j
@Configuration
public class AgentConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    @Autowired
    private FileSystemTools fileSystemTools;

    private OpenAIChatModel createModel() {
        String effectiveBaseUrl = baseUrl;
        if (effectiveBaseUrl != null && !effectiveBaseUrl.endsWith("/v1") && !effectiveBaseUrl.endsWith("/v1/")) {
            effectiveBaseUrl = effectiveBaseUrl.endsWith("/") ? effectiveBaseUrl + "v1" : effectiveBaseUrl + "/v1";
        }

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(effectiveBaseUrl)
                .modelName(modelName)
                .build();
    }

    @Bean
    public ReActAgent knowledgeRetrievalAgent() {
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
                        "你是一个智能助手。当用户询问关于智能温控咖啡杯的任何问题时，你必须使用 retrieval-skill 来检索本地知识库中的信息，然后基于检索到的内容回答问题。如果本地知识库中没有相关内容，再基于你的知识回答。")
                .model(createModel())
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }
}