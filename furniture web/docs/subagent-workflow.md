# Subagent Workflow

> 当前阶段不强制使用 subagent。开始全站批量抽取后，可以用 subagent 做并行采集，但主会话负责统一结构和代码。

## 1. When To Use Subagents

适合使用 subagent 的情况：

- 页面类型彼此独立。
- 任务只需要采集、分析、输出报告。
- 不会同时修改同一批代码文件。
- 每个任务有明确 URL、viewport 和输出格式。

示例：

```text
Agent A：抽取首页和全局 Header/Footer
Agent B：抽取 Sale 页和图片规格
Agent C：抽取商品列表页模板
Agent D：抽取商品详情页模板
Agent E：整理移动端菜单和响应式差异
```

不适合使用 subagent 的情况：

- 需要统一组件架构。
- 多个任务会改同一个 Vue 文件。
- 还没明确页面范围。
- 需要判断整体视觉风格和复刻策略。

## 2. Recommended Pattern

subagent 只做采集和报告：

```text
输入：URL、viewport、页面类型、抽取字段
输出：结构摘要、图片规格、组件建议、问题清单
禁止：直接改主项目代码
```

主会话负责：

```text
统一页面清单
统一 Vue3 组件边界
合并抽取数据
生成最终代码
运行本地页面
做源站 vs 本地截图对比
```

## 3. Agent Output Contract

每个 subagent 必须返回：

```text
页面 ID：
URL：
Viewport：
页面结构：
主要模块：
图片区域：
交互状态：
可复用组件：
无法确认的问题：
生成文件：
```

## 4. Current Recommendation

当前 Batch 001 不需要 subagent，因为我们正在建立项目基线。

从 Batch 002 开始，如果要同时抽取首页、PLP、PDP 和移动端，可以使用 subagent 并行采集。
