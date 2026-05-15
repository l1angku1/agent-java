package com.agent.java.service;

import com.agent.java.model.search.SearchDocument;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 过滤表达式解析器
 * 用于解析LLM生成的过滤条件表达式并应用到商品过滤
 */
@Slf4j
public class FilterExpressionParser {
    
    /**
     * 条件匹配正则表达式
     * 支持: 字段名 运算符 数值
     * 示例: price>=100, stock>0, salesVolume>=1000
     */
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
        "(\\w+)\\s*(==|!=|<|<=|>|>=)\\s*([\\d.]+)"
    );

    /**
     * 检查商品是否满足过滤条件
     * 
     * @param doc 商品文档
     * @param expression 过滤条件表达式
     * @return 是否满足条件
     */
    public static boolean matches(SearchDocument doc, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        
        try {
            // 先按 or 分割（或条件）
            String[] orConditions = expression.split("\\bor\\b");
            
            for (String orCondition : orConditions) {
                orCondition = orCondition.trim();
                // 再按 and 分割（与条件）
                String[] andConditions = orCondition.split("\\band\\b");
                
                boolean allAndMatch = true;
                for (String condition : andConditions) {
                    condition = condition.trim();
                    if (!matchesSingleCondition(doc, condition)) {
                        allAndMatch = false;
                        break;
                    }
                }
                
                // 如果任意一个或条件满足，则整体满足
                if (allAndMatch) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Failed to parse filter expression: {}, error: {}", expression, e.getMessage());
            return true; // 解析失败时视为满足条件
        }
    }
    
    /**
     * 检查单个条件是否满足
     */
    private static boolean matchesSingleCondition(SearchDocument doc, String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }
        
        Matcher matcher = CONDITION_PATTERN.matcher(condition);
        if (!matcher.find()) {
            log.debug("Cannot parse condition: {}", condition);
            return true; // 无法解析的条件视为满足
        }
        
        String field = matcher.group(1);
        String operator = matcher.group(2);
        double value = Double.parseDouble(matcher.group(3));
        
        return evaluateCondition(doc, field, operator, value);
    }
    
    /**
     * 评估单个条件
     */
    private static boolean evaluateCondition(SearchDocument doc, String field, String operator, double value) {
        Double fieldValue = getFieldValue(doc, field);
        if (fieldValue == null) {
            log.debug("Field {} not found in document {}", field, doc.getId());
            return true; // 字段不存在视为满足条件
        }
        
        return switch (operator) {
            case "==" -> Math.abs(fieldValue - value) < 0.0001;
            case "!=" -> Math.abs(fieldValue - value) >= 0.0001;
            case "<" -> fieldValue < value;
            case "<=" -> fieldValue <= value;
            case ">" -> fieldValue > value;
            case ">=" -> fieldValue >= value;
            default -> {
                log.warn("Unknown operator: {}", operator);
                yield true;
            }
        };
    }
    
    /**
     * 获取商品字段值
     */
    private static Double getFieldValue(SearchDocument doc, String field) {
        if (doc == null || field == null) {
            return null;
        }
        
        return switch (field.toLowerCase()) {
            case "price" -> doc.getPrice();
            case "stock" -> doc.getStock() != null ? doc.getStock().doubleValue() : null;
            case "salesvolume", "sales_volume", "sales" -> 
                doc.getSalesVolume() != null ? doc.getSalesVolume().doubleValue() : null;
            case "sharecount", "share_count", "share" -> 
                doc.getShareCount() != null ? doc.getShareCount().doubleValue() : null;
            default -> {
                log.debug("Unsupported field: {}", field);
                yield null;
            }
        };
    }
}