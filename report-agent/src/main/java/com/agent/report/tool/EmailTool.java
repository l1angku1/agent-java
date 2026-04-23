package com.agent.report.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailTool {

    private final JavaMailSender mailSender;

    public EmailTool(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Tool(name = "send_email", description = "发送日报邮件给领导")
    public String sendEmail(
            @ToolParam(name = "to", description = "收件人邮箱地址") String to,
            @ToolParam(name = "subject", description = "邮件主题") String subject,
            @ToolParam(name = "content", description = "邮件内容") String content) {
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            return "邮件发送成功！";
        } catch (Exception e) {
            return "发送邮件时发生错误：" + e.getMessage();
        }
    }
}
