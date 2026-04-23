package com.agent.report.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Commit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GitLabTool {

    @Value("${gitlab.url}")
    private String gitlabUrl;

    @Value("${gitlab.token}")
    private String gitlabToken;

    @Value("${gitlab.project-id}")
    private String projectId;

    @Tool(name = "get_gitlab_commits", description = "获取 GitLab 指定项目的最近提交记录")
    public String getCommits(
            @ToolParam(name = "since", description = "开始日期，格式：YYYY-MM-DD") String since) {
        
        try (GitLabApi gitLabApi = new GitLabApi(gitlabUrl, gitlabToken)) {
            Date sinceDate = java.sql.Date.valueOf(since);
            List<Commit> commits = gitLabApi.getCommitsApi().getCommits(projectId, "main", sinceDate, new Date());
            
            if (commits.isEmpty()) {
                return "在指定日期范围内没有找到提交记录。";
            }

            return commits.stream()
                    .map(c -> String.format("- %s (作者: %s, 时间: %s)", 
                            c.getMessage().trim(), c.getAuthorName(), c.getAuthoredDate()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "获取 GitLab 提交记录时发生错误：" + e.getMessage();
        }
    }
}
