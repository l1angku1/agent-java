package com.agent.java.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 文件系统工具类
 * <p>
 * 提供文件读取、目录列表、命令执行等文件系统操作功能。
 * </p>
 */
@Component
public class FileSystemTools {

    /** 允许执行的命令白名单 */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "ls", "pwd", "cat", "grep", "find", "head", "tail", "wc", "sort", "uniq");

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    @Tool(name = "Read", description = "读取文件内容")
    public String readFile(
            @ToolParam(name = "file_path", description = "文件路径") String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes);
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    /**
     * 列出目录内容
     *
     * @param path 目录路径
     * @return 目录内容列表
     */
    @Tool(name = "LS", description = "列出目录内容")
    public String listFiles(
            @ToolParam(name = "path", description = "目录路径") String path) {
        try {
            File directory = new File(path);
            if (!directory.exists() || !directory.isDirectory()) {
                return "目录不存在: " + path;
            }
            List<String> files = new ArrayList<>();
            listFilesRecursive(directory, "", files);
            return String.join("\n", files);
        } catch (Exception e) {
            return "列出目录失败: " + e.getMessage();
        }
    }

    /**
     * 递归列出目录内容
     *
     * @param directory 目录对象
     * @param prefix    路径前缀
     * @param files     文件列表
     */
    private void listFilesRecursive(File directory, String prefix, List<String> files) {
        File[] entries = directory.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                String entryPath = prefix + entry.getName();
                files.add(entryPath);
                if (entry.isDirectory()) {
                    listFilesRecursive(entry, entryPath + "/", files);
                }
            }
        }
    }

    /**
     * 执行命令
     *
     * @param command 要执行的命令
     * @return 命令输出结果
     */
    @Tool(name = "RunCommand", description = "执行命令")
    public String runCommand(
            @ToolParam(name = "command", description = "要执行的命令") String command) {
        String trimmedCommand = command.trim();
        String baseCommand = trimmedCommand.split("\\s+")[0];

        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            return "禁止执行该命令: " + baseCommand + "。仅允许执行以下命令: " + ALLOWED_COMMANDS;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append("错误: " + line).append("\n");
                    }
                }
            }

            return output.toString();
        } catch (IOException | InterruptedException e) {
            return "执行命令失败: " + e.getMessage();
        }
    }
}
