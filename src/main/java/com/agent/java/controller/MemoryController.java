package com.agent.java.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.memory.ApiResponse;
import com.agent.java.model.memory.FeedbackRequest;
import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.PreferenceUpdateRequest;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.service.MemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆管理接口
 * 提供记忆的创建、执行、查询等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

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
    public ResponseEntity<ApiResponse> updateUserPreference(@RequestBody PreferenceUpdateRequest request) {
        log.info("Updating preference for user: {}", request.getUserId());
        memoryService.updateUserPreference(request.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status("success")
                .message("Preference updated successfully")
                .build());
    }

    /**
     * 记录用户反馈
     */
    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse> recordFeedback(@RequestBody FeedbackRequest request) {
        log.info("Recording feedback for user: {}, document: {}", request.getUserId(), request.getDocumentId());
        memoryService.recordFeedback(request.getUserId(), request.getDocumentId(),
                request.getScore() != null ? request.getScore() : 0);
        return ResponseEntity.ok(ApiResponse.builder()
                .status("success")
                .message("Feedback recorded successfully")
                .build());
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
    public ResponseEntity<ApiResponse> clearUserMemory(@PathVariable String userId) {
        log.info("Clearing memory for user: {}", userId);
        memoryService.clearUserMemory(userId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status("success")
                .message("Memory cleared successfully")
                .build());
    }
}
