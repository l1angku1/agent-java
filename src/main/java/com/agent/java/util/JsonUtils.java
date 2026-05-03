package com.agent.java.util;

public class JsonUtils {

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