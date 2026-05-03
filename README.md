# agent-java

基于 AgentScope 的智能代理应用，整合计划管理、知识检索、AI 搜索和用户记忆能力。

## 功能特性

### 1. 计划管理
- 支持计划的创建、执行、查询、取消和删除
- 通过 LLM 自动拆解用户需求并生成执行计划
- 支持同步和异步两种执行模式
- 提供预览-确认-执行的完整工作流

### 2. 知识检索
- 基于 AgentScope ReActAgent 的智能问答
- 支持知识库查询和分析

### 3. AI 搜索（RAG 流程）
- **查询解析**：大模型意图识别和关键词提取
- **向量召回**：基于 Ollama 向量化模型进行相似度匹配
- **模型重排**：大模型对召回结果进行排序评分
- **质量评估**：大模型评估检索结果质量
- **响应生成**：基于参考文档生成最终回答

### 4. 用户记忆系统
- **用户偏好管理**：品牌偏好、类别偏好、价格范围、关键词权重
- **搜索记忆**：记录用户搜索历史，自动学习关键词偏好
- **对话历史**：支持多轮对话上下文，自动定期总结
- **记忆衰减机制**：基于时间指数衰减，访问增强重要性
- **会话管理**：会话上下文维护，自动清理过期会话

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5.3 |
| 智能代理 | AgentScope 1.0.11 |
| AI 模型 | 智谱AI GLM-5 |
| 向量化 | Ollama (qwen3-embedding:0.6b) |
| 构建工具 | Maven |

## 环境要求

- JDK 21+
- Maven 3.8+
- Ollama 服务（本地运行，向量化模型）

## 快速开始

### 1. 启动 Ollama 服务

```bash
# 安装 Ollama
brew install ollama

# 启动 Ollama 服务
ollama serve

# 拉取向量化模型（新终端执行）
ollama pull qwen3-embedding:0.6b
```

### 2. 构建项目

```bash
cd agent-java
mvn clean package
```

### 3. 运行项目

```bash
# 方式一：使用 Maven
mvn spring-boot:run

# 方式二：运行 Jar 包
java -jar target/agent-java-1.0.0.jar
```

服务启动后访问: http://localhost:8081

## 项目结构

```
agent-java/
├── knowledge/                          # 知识库文件
│   ├── 智能护眼台灯-产品说明书.md
│   └── 智能温控咖啡杯-产品说明书.md
├── src/main/java/com/agent/java/
│   ├── controller/                     # REST API 控制器
│   │   ├── PlanController.java         # 计划管理接口
│   │   ├── KnowledgeController.java    # 知识检索接口
│   │   ├── AISearchController.java     # AI 搜索接口
│   │   └── MemoryController.java       # 记忆管理接口
│   ├── model/                          # 数据模型
│   │   ├── plan/                       # 计划相关模型
│   │   │   ├── Plan.java
│   │   │   ├── PlanRequest.java
│   │   │   └── PlanStatus.java
│   │   ├── search/                     # 搜索相关模型
│   │   │   ├── SearchRequest.java
│   │   │   ├── SearchResult.java
│   │   │   ├── SearchDocument.java
│   │   │   ├── QueryAnalysis.java
│   │   │   └── EvaluationResult.java
│   │   ├── memory/                     # 记忆相关模型
│   │   │   ├── MemoryItem.java
│   │   │   ├── MemoryType.java
│   │   │   ├── UserPreference.java
│   │   │   ├── SessionContext.java
│   │   │   └── PreferenceUpdateRequest.java
│   │   └── KnowledgeRequest.java
│   ├── service/                        # 业务服务
│   │   ├── AIService.java              # AI 服务封装
│   │   ├── QueryParserService.java     # 查询解析
│   │   ├── VectorRecallService.java    # 向量召回
│   │   ├── ModelRerankService.java     # 模型重排
│   │   ├── ModelEvaluationService.java # 质量评估
│   │   ├── ResponseGeneratorService.java # 响应生成
│   │   ├── MemoryService.java           # 记忆管理
│   │   └── ConversationSummaryService.java # 对话总结
│   ├── plan/                           # 计划执行逻辑
│   │   ├── PipelinePlanExecutor.java
│   │   └── PlanGenerator.java
│   ├── config/                         # 配置类
│   │   ├── ModelConfig.java            # AI 模型配置
│   │   ├── OllamaConfig.java           # Ollama 配置
│   │   └── MemoryConfig.java           # 记忆系统配置
│   ├── tool/                           # 工具类
│   │   └── FileSystemTools.java
│   └── Application.java                # 启动类
├── src/main/resources/
│   ├── skills/                         # AgentScope 技能
│   │   ├── retrieval-skill/
│   │   └── skill-creator/
│   ├── goods.csv                       # 商品数据
│   └── application.yml                 # 配置文件
└── pom.xml
```

---

## 配置说明

在 `application.yml` 中配置：

### AI 模型配置
```yaml
ai:
  openai:
    api-key: your-api-key
    base-url: https://api-inference.modelscope.cn
    model: ZhipuAI/GLM-5
    temperature: 0.7
```

### Ollama 向量化配置
```yaml
ollama:
  embedding:
    base-url: http://localhost:11434
    model-name: qwen3-embedding:0.6b
    dimensions: 1024
```

### 记忆系统配置
```yaml
ai:
  memory:
    enabled: true
    default-decay-factor: 0.9
    max-memories-per-user: 100
    memory-expire-days: 30
    recent-searches-limit: 10
```

| 配置项 | 说明 |
|--------|------|
| `api-key` | ModelScope API 密钥 |
| `base-url` | API 服务地址 |
| `model` | 使用的 AI 模型 |
| `temperature` | 生成随机性参数（0-1） |
| `ollama.base-url` | Ollama 服务地址 |
| `ollama.model-name` | 向量化模型名称 |
| `ollama.dimensions` | 向量维度 |
| `memory.enabled` | 是否启用记忆功能 |
| `memory.default-decay-factor` | 默认衰减因子（0-1） |
| `memory.max-memories-per-user` | 单用户最大记忆数量 |
| `memory.memory-expire-days` | 记忆过期天数 |
| `memory.recent-searches-limit` | 近期搜索关键词保留数量 |

---

## License

MIT License