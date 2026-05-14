# agent-java

基于 AgentScope 的智能代理应用，整合计划管理、知识检索、AI 搜索和用户记忆能力。

## 功能特性

### 1. 计划管理
- **LLM 自动生成**：输入自然语言需求，LLM 自动拆解为可执行步骤（3-10个步骤）
- **依赖分析**：智能分析步骤间依赖关系，自动识别可并行执行的步骤组
- **并行执行**：支持步骤组内并行执行，提升执行效率
- **步骤间上下文传递**：支持通过 `{outputKey}` 引用前序步骤结果
- **失败处理**：支持重试机制（0-3次）、超时控制（默认120秒）和失败策略（abort/skip/fallback）
- **双模式执行**：同步执行（等待完成）/ 异步执行（立即返回）
- **完整工作流**：预览（生成计划待确认）→ 确认 → 执行 → 完成
- **生命周期管理**：支持计划查询、步骤查看、取消和删除操作

### 2. 知识检索
- **ReActAgent**：基于 ReAct 框架的智能问答代理
- **多格式支持**：支持查询本地 Markdown 知识库文件（如产品说明书）
- **技能系统**：自动加载 skills 目录下的检索技能
- **工具集成**：集成文件系统工具，支持知识库文件读取

### 3. AI 搜索（RAG 流程）
- **查询解析**：大模型意图识别 + 关键词/实体提取 + 查询重写 + 过滤表达式生成
- **实体召回**：基于名词实体进行向量召回，过滤动词和条件
- **动态阈值**：基于统计（均值 - k×标准差）自动计算低质量结果判定阈值
- **过滤引擎**：支持结构化过滤表达式（price < 1000 && stock > 0）
- **模型重排**：大模型对召回结果进行排序评分
- **质量评估**：大模型评估检索结果质量（Quality Level + F1 Score）
- **响应生成**：基于参考文档生成最终回答
- **语义缓存**：基于向量相似度（余弦相似度）的智能缓存，相似查询复用结果
- **个性化搜索**：融合用户偏好（品牌、类目、价格范围、关键词权重）进行智能解析
- **多轮对话**：支持会话上下文，自动解析代词（如"它"、"这个"）指向的具体实体

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
| 框架 | Spring Boot 4.0.1 |
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
│   │   ├── cache/                      # 缓存相关模型
│   │   │   └── SemanticCacheEntry.java
│   │   └── knowledge/                  # 知识相关模型
│   │       └── KnowledgeRequest.java
│   ├── service/                        # 业务服务
│   │   ├── AIService.java              # AI 服务封装
│   │   ├── QueryParserService.java     # 查询解析
│   │   ├── VectorRecallService.java    # 向量召回
│   │   ├── ModelRerankService.java     # 模型重排
│   │   ├── ModelEvaluationService.java # 质量评估
│   │   ├── ResponseGeneratorService.java # 响应生成
│   │   ├── MemoryService.java           # 记忆管理
│   │   ├── ConversationSummaryService.java # 对话总结
│   │   └── SemanticCacheService.java   # 语义缓存服务
│   ├── plan/                           # 计划执行逻辑
│   │   ├── PipelinePlanExecutor.java
│   │   └── PlanGenerator.java
│   ├── config/                         # 配置类
│   │   ├── ModelConfig.java            # AI 模型配置
│   │   ├── OllamaConfig.java           # Ollama 配置
│   │   ├── MemoryConfig.java           # 记忆系统配置
│   │   └── SemanticCacheConfig.java    # 语义缓存配置
│   ├── tool/                           # 工具类
│   │   └── FileSystemTools.java
│   ├── util/                           # 通用工具
│   │   └── JsonUtils.java
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
| `enabled` | 是否启用记忆功能 |
| `default-decay-factor` | 默认衰减因子（0-1） |
| `max-memories-per-user` | 单用户最大记忆数量 |
| `memory-expire-days` | 记忆过期天数 |
| `recent-searches-limit` | 近期搜索关键词保留数量 |

---

## License

MIT License