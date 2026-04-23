package com.agent.report.model;

public class ReportRequest {
    private String message;
    private String date;
    private String manualContent;
    private String leaderEmail;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getManualContent() {
        return manualContent;
    }

    public void setManualContent(String manualContent) {
        this.manualContent = manualContent;
    }

    public String getLeaderEmail() {
        return leaderEmail;
    }

    public void setLeaderEmail(String leaderEmail) {
        this.leaderEmail = leaderEmail;
    }
}