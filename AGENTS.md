# AGENTS.md

## 通用规则
- 始终使用中文回复。
- 类、属性、方法必须添加注释，注释简洁明了，避免冗余和长篇大论。

## 项目结构
- 配置类统一放在 `config` 目录下，类名以 `Config` 结尾，使用 `@Configuration` 或 `@Component` 注解，配置变量使用 `@Value` 注解注入，支持通过 `:` 指定默认值。
- Controller 类放在 `controller` 目录下，使用 `@RestController` + `@RequestMapping` 注解。
- Service 类放在 `service` 目录下，使用 `@Service` 注解。
- 数据模型类放在 `model` 目录下，按业务模块划分子包（如 `memory`、`search`、`plan`）。
- 工具类放在 `util` 目录下，提供静态方法。
- 计划执行相关类放在 `plan` 目录下，使用 `@Component` 注解。

## 依赖注入
- 统一使用构造器注入，禁止使用 `@Autowired` 字段注入。
- 依赖字段声明为 `private final`。
- 使用 Lombok 的 `@RequiredArgsConstructor` 注解自动生成构造器。

## Lombok 使用
- 模型类使用 `@Data` 注解。
- 需要日志的类使用 `@Slf4j` 注解。
- 需要 Builder 模式的类使用 `@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`。

## 日志规范
- Controller 层使用 `log.info` 记录请求入口和关键参数。
- Service 层使用 `log.debug` 记录中间过程，`log.warn` 记录异常但可恢复的情况，`log.error` 记录错误。
- 日志占位符使用 `{}`，不使用字符串拼接。

## 技术栈
- Java 21，Spring Boot 3.5.3，Maven 构建。
- 使用 AgentScope 框架进行 AI Agent 开发。
- 使用 Caffeine 作为本地缓存。
- 使用 Jackson 进行 JSON 序列化/反序列化。
- 使用 Reactor（Mono/Flux）进行响应式编程。
- 使用 Ollama 进行文本向量化。

## 配置文件
- 配置文件统一使用 `application.yml`，配置项按功能模块分组（如 `ai`、`ollama`、`logging`）。
- 敏感信息（如 API Key）不硬编码，通过配置文件注入。

## 依赖版本管理
- 在 `pom.xml` 的 `<properties>` 中定义全局版本变量，如 `${java.version}`、`${jackson.version}`。
- 使用 `<dependencyManagement>` 进行依赖版本统一管理。
- 优先使用 BOM（Bill of Materials）进行依赖版本管理，减少版本冲突。
