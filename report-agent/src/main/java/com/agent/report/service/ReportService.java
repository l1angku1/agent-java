package com.agent.report.service;

import org.springframework.stereotype.Service;

@Service
public class ReportService {

    public String generateReport(String content) {
        return "Report generated: " + content;
    }
}