package com.agent.java.util;

/**
 * JSON工具类
 * <p>
 * 提供JSON相关的静态工具方法。
 * </p>
 */
public class JsonUtils {

    /**
     * 去除Markdown代码块标记
     * <p>
     * 用于处理LLM返回的JSON字符串，去除```json或```等Markdown代码块标记。
     * </p>
     *
     * @param text 原始文本
     * @return 去除代码块标记后的文本
     */
    public static String stripMarkdownCodeBlock(String text) {
        if (text == null) {
            return null;
        }
        String result = text.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }
}