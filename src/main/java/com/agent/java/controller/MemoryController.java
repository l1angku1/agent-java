package com.agent.java.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.PreferenceUpdateRequest;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.service.MemoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 获取用户偏好
     */
    @GetMapping("/preference/{userId}")
    public ResponseEntity<UserPreference> getUserPreference(@PathVariable String userId) {
        log.info("Getting preference for user: {}", userId);
        UserPreference preference = memoryService.getUserPreference(userId);
        return ResponseEntity.ok(preference);
    }

    /**
     * 更新用户偏好
     */
    @PostMapping("/preference/update")
    public ResponseEntity<Map<String, String>> updateUserPreference(@RequestBody PreferenceUpdateRequest request) {
        log.info("Updating preference for user: {}", request.getUserId());
        memoryService.updateUserPreference(request.getUserId(), request);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Preference updated successfully"));
    }

    /**
     * 记录用户反馈
     */
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> recordFeedback(@RequestBody Map<String, Object> feedbackData) {
        String userId = (String) feedbackData.get("userId");
        String documentId = (String) feedbackData.get("documentId");
        Integer score = feedbackData.get("score") != null ? (Integer) feedbackData.get("score") : 0;

        log.info("Recording feedback for user: {}, document: {}", userId, documentId);
        memoryService.recordFeedback(userId, documentId, score);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Feedback recorded successfully"));
    }

    /**
     * 获取用户记忆历史
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<MemoryItem>> getUserMemories(@PathVariable String userId) {
        log.info("Getting memory history for user: {}", userId);
        List<MemoryItem> memories = memoryService.getUserMemories(userId);
        return ResponseEntity.ok(memories);
    }

    /**
     * 清空用户记忆
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> clearUserMemory(@PathVariable String userId) {
        log.info("Clearing memory for user: {}", userId);
        memoryService.clearUserMemory(userId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Memory cleared successfully"));
    }
}
