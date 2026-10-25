# SysDesign Lab

把常见的后端系统设计题，从“背思路”变成“能启动、能请求、能观察、能验证”的最小案例。

每个 Demo 只保留题目里最关键的机制，不伪装成生产系统。它应该能回答四个问题：

1. 这个方案到底在解决什么问题？
2. 核心流程和数据结构是什么？
3. 请求进来后会发生什么，如何亲手触发？
4. 真实系统还需要补上哪些生产级能力？

## 当前状态

| 模块 | 场景 | 状态 |
| --- | --- | --- |
| `demos/rate-limit` | 接口限流：逐客户端滑动窗口 | 可运行 |

完整题库、优先级和 Demo 边界见 [场景索引](docs/scenarios.md)。

## 快速开始

环境要求：

- JDK 21+
- Maven 3.9+

运行全部测试：

```powershell
mvn test
```

启动限流 Demo：

```powershell
mvn -pl demos/rate-limit spring-boot:run
```

默认配置为 10 秒内最多 3 次请求。连续调用同一个接口：

```powershell
1..4 | ForEach-Object {
  curl.exe -i -s -H "X-Client-Id: demo-user" http://localhost:8081/api/reports
  ""
}
```

前三次返回 `200`，第四次返回 `429`，并带有 `Retry-After` 响应头。

## 仓库结构

```text
.
├── demos/                  # 可独立运行的最小案例
├── docs/
│   ├── scenarios.md        # 场景题库、优先级与拆解方式
│   └── demo-standard.md    # 每个 Demo 的交付标准
├── .github/workflows/      # CI
└── pom.xml                 # Maven 聚合工程
```

## 设计原则

- **一个 Demo 讲透一个机制**：不是把多个中间件堆起来就算完成。
- **默认零外部依赖**：优先用内存、虚拟时钟和测试替身表达核心逻辑。
- **需要基础设施时显式说明**：Redis、数据库、消息队列只在该机制无法被简化时引入。
- **先有可验证行为，再有架构图**：每个关键结论都应有测试或可重复请求。
- **主动标注简化点**：单机、内存、无持久化不是缺陷，但没有写清楚才是。

## 添加新场景

1. 在 [场景索引](docs/scenarios.md) 中确认题目、考点和 Demo 边界。
2. 按 [Demo 规范](docs/demo-standard.md) 创建 `demos/<scenario>`。
3. 补一条可复制运行的请求示例和一个自动化测试。
4. 在根 README 的“当前状态”中登记。

