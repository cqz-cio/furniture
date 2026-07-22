# Yudao SEO 逐关键词关联度分析实施计划

日期：2026-07-22
状态：已确认，待实施
目标分支：`codex/agent-rag`
依赖设计：`docs/superpowers/specs/2026-07-15-yudao-seo-module-design.md`

## 1. 交付目标

在现有 SEO 元数据和站点设置基础上，实现一个可解释、可复现、可版本化的关键词分析闭环：

1. 对焦点关键词和每个关联关键词分别输出 0～100% 关联度。
2. 每个关键词都有五个分项百分比、命中证据、扣分原因和具体修改建议。
3. 用户修改内容后可重新分析，对比同一关键词的上次与本次得分、已解决问题和新增问题。
4. 分析历史保留输入快照、规则版本、词典版本和语义模型版本。
5. 后台使用已确认的统一主色进度条方案，以百分比和文字等级表达差异。

本轮不实施生成式 AI 润色。语义向量是分析算法的一部分，不产生或改写文案。

## 2. 当前基线与差距

当前 SEO 基础模块已有站点设置、元数据 CRUD、发布状态、焦点关键词和关联关键词存储。当前字段只能录入和查询，不等于已存在分析算法。

本计划需要补齐：

- 不可变内容快照和幂等分析任务。
- 逐关键词分析和逐规则证据数据模型。
- 中英文归一化、词形/变体、家具行业同义词和属性覆盖。
- 确定性规则引擎、BM25 字面相关度和可降级的语义相似度。
- 逐关键词结果 API、历史对比 API 和管理端 UI。

## 3. 范围边界

### 3.1 本轮包含

- `PRODUCT/CATEGORY/ARTICLE/PAGE` 四类实体的手工文本或 ERP 实体快照分析。
- 焦点关键词 + 多个关联关键词的独立分析。
- 五项分值、汇总百分比、文字等级、可信度和降级状态。
- 确定性修改建议和预计可恢复分值。
- 历史分析和两次结果对比。
- 统一主色逐关键词 UI。
- 规则、词典、模型和计分参数版本化。

### 3.2 本轮不包含

- 生成式 AI 改写、一键润色或自动覆盖正式数据。
- Google 搜索排名概率、关键词搜索量或竞争度预测。
- Search Console、外链、竞争对手和全站爬虫。
- 扫描型 PDF OCR。
- 文件解析器的具体实现；DOCX/PDF/XLSX 解析完成后必须复用本计划的快照和分析入口，不再建第二套算法。

## 4. 统一计分合同

### 4.1 单个关键词

```text
关联度 =
  关键位置覆盖       25%
+ 精确词/变体匹配   20%
+ 语义相关性         25%
+ 分布与自然度       15%
+ 搜索意图/主题覆盖 15%
```

所有分项和最终值均为 0～100 整数百分比。内部计算保留 4 位小数，最后一次四舍五入，避免分项逐次取整导致偏差。

### 4.2 分项实现

**关键位置覆盖（100 分）**

- SEO 标题：25
- H1/商品主标题：20
- 简介前 120 个字符：15
- Meta Description：15
- H2/H3：10
- Slug：10
- 图片 ALT：5

对当前实体不适用的位置不记 0 分，而是从分母中排除并记录 `NOT_APPLICABLE`。

**精确词/变体匹配（100 分）**

- 完整词组或紧邻词组命中：40
- 归一化词、词形、同义词和家具行业变体覆盖：25
- 字段加权 BM25：35

BM25 索引不可用时保留前两项，按可用权重归一化，分析标记 `PARTIAL`。

**语义相关性（100 分）**

- 分别计算关键词与标题、简介、属性摘要和正文段落的向量相似度。
- 字段权重通过版本化评测集校准，不直接把原始余弦相似度当成百分比。
- 中文、英文和实体类型使用独立校准参数。
- 语义模型不可用时，该分项为 `NULL/NOT_COMPLETED`，不是 0 分。

**分布与自然度（100 分）**

- 正文前、中、后部和不同结构区域的自然分布。
- 按语言和内容长度版本化定义密度区间。
- 单段过度集中、连续重复、异常标点隔开或隐藏堆砌进行扣分。
- 短文本不强制套用长文密度阈值。

**搜索意图/主题覆盖（100 分）**

- 由版本化家具词典将关键词拆解为品类、材质、尺寸、颜色、风格、空间/场景、人群、保养和交付等意图切面。
- 依据关键词实际包含的切面生成期望主题，不要求每个商品填满所有行业属性。
- 证据可来自标题、简介、规格属性和正文，但不得从不可见系统字段中虚构主题覆盖。

### 4.3 等级和降级

- 80～100%：高度相关
- 60～79%：基本相关
- 40～59%：关联较弱
- 0～39%：关联度低

若某分项因技术故障未完成，暂定分数按可用权重归一化：

```text
暂定关联度 = 已完成分项的加权和 / 已完成分项的权重和
```

前端必须显示“暂定”、`PARTIAL` 原因和降低后的可信度。内容为空或根本不可解析时显示“无法分析”，不显示 0%。

## 5. 证据和建议合同

每条关键词问题必须返回：

- `ruleCode`：稳定、可测试、可版本化的规则编号。
- `dimension`：属于五个分项之一。
- `severity`：`HIGH/MEDIUM/LOW/INFO`。
- `contentLocation`：发生在哪个字段、段落或图片。
- `evidence`：命中词、变体、次数、字符位置和最小必要上下文。
- `reason`：为什么扣分，不只显示结论。
- `recommendation`：可直接执行的改法，明确修改位置和目标。
- `recoverableScore`：只在可确定计算时返回。

建议样例：

```text
关键词：实木餐桌
问题：SEO 标题未出现完整词组
证据：当前标题“Oakved 北欧餐桌 1.8m”，只命中“餐桌”
原因：材质意图未在标题中明确表达
建议：仅在商品确实为实木时，将标题调整为“Oakved 北欧实木餐桌 1.8m”
可恢复：关键位置覆盖 +25 分，折算总关联度最多 +6.25
```

当关键词与商品核心主题明显无关时，建议应是删除关键词或转移到更合适的页面，不得鼓励堆砌。

## 6. 后台 UI 合同

### 6.1 列表层

每个关键词一行：

```text
[关键词] [焦点/关联]  [================----] 78%  基本相关  [3 条建议] [展开]
```

- 所有进度条使用 Element Plus 主色 token `var(--el-color-primary)`，不在组件中硬编色值。
- 进度条宽度、右侧数字和文字等级必须来自同一个百分比值。
- 不使用红/黄/绿进度条，不单独依赖颜色表达状态。
- 焦点关键词默认展开，关联关键词默认折叠。
- `PARTIAL`、“无法分析”和“语义未完成”使用文字标签和帮助说明。

### 6.2 展开层

- 五个分项百分比。
- 命中位置与原文证据。
- 按严重程度排序的原因和建议。
- 可恢复分值，仅在确定性规则下显示。
- 上次分数→本次分数、已解决问题和新增问题。
- 固定声明：“关联度是系统内部内容分析指标，不代表搜索引擎排名保证。”

### 6.3 动作

- `分析`：使用当前已保存的内容快照。
- `重新分析`：创建新历史记录，不覆盖旧结果。
- `查看证据`：定位到原文字段或段落。
- `去修改`：跳转到对应实体或 SEO 元数据表单，不自动写入。

## 7. 数据与 API 合同

### 7.1 数据表

新增：

- `seo_analysis`：一次分析任务和不可变输入快照。
- `seo_analysis_item`：不属于特定关键词的综合 SEO 规则。
- `seo_keyword_analysis`：每个关键词一条汇总记录。
- `seo_keyword_analysis_item`：每个关键词的逐规则证据与建议。

表字段和状态以设计文档第 6 节为准。SQL 迁移文件必须在实施时查询当前最大 Flyway 版本号后顺延，不在本文档预先硬编号。

### 7.2 管理端 API

```text
POST /admin-api/seo/analysis/run
GET  /admin-api/seo/analysis/{id}
GET  /admin-api/seo/analysis/{id}/keywords
GET  /admin-api/seo/analysis/{id}/keywords/{keywordAnalysisId}
POST /admin-api/seo/analysis/{id}/rerun
GET  /admin-api/seo/analysis/{id}/compare?previousAnalysisId={previousId}
```

`POST /analysis/run` 最少接收：

- `siteId/entityType/entityId/locale`
- `focusKeyphrase`
- `relatedKeyphrases[]`
- `sourceType`：`ENTITY/MANUAL/DOCUMENT`
- `sourceId`（可选）
- `idempotencyKey`

服务端必须重新读取和规范化有权访问的源数据，不盲信前端传入的完整快照。

## 8. 预计文件边界

### 8.1 后端

```text
yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/.../enums/
  SeoAnalysisStatusEnum.java
  SeoKeywordTypeEnum.java
  SeoKeywordGradeEnum.java

yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/.../analysis/
  controller/admin/SeoAnalysisController.java
  controller/admin/vo/SeoAnalysisRunReqVO.java
  controller/admin/vo/SeoAnalysisRespVO.java
  controller/admin/vo/SeoKeywordAnalysisRespVO.java
  service/SeoAnalysisService.java
  service/SeoAnalysisServiceImpl.java
  service/SeoContentSnapshotFactory.java
  engine/SeoKeywordAnalysisEngine.java
  engine/SeoKeywordScorer.java
  engine/SeoRuleSuggestionService.java
  lexical/SeoTextNormalizer.java
  lexical/SeoLexicalRelevanceProvider.java
  lexical/LuceneSeoLexicalRelevanceProvider.java
  semantic/SeoSemanticSimilarityProvider.java
  semantic/BgeM3SemanticSimilarityProvider.java
  semantic/DisabledSemanticSimilarityProvider.java
  dictionary/SeoIndustryDictionary.java
  dal/dataobject/*.java
  dal/mysql/*.java
```

包路径以现有 `cn.iocoder.yudao.module.seo` 规范落地，不在 `yudao-module-ai` 中建 SEO 业务表或规则引擎。

### 8.2 前端

```text
yudao-ui-admin-vue3/src/api/seo/analysis/index.ts
yudao-ui-admin-vue3/src/views/seo/analysis/index.vue
yudao-ui-admin-vue3/src/views/seo/analysis/components/KeywordRelevanceCard.vue
yudao-ui-admin-vue3/src/views/seo/analysis/components/KeywordDimensionBreakdown.vue
yudao-ui-admin-vue3/src/views/seo/analysis/components/KeywordSuggestionList.vue
yudao-ui-admin-vue3/src/views/seo/analysis/components/AnalysisComparison.vue
```

`metadata` 页只增加“分析/查看最新分析”入口，不将整套分析组件复制进元数据表单。

## 9. 实施任务

### Task 0：合并前基线确认

- [ ] 确认开发分支包含当前 `main` 的 SEO 基础模块、SQL 迁移和后台页面。
- [ ] 运行 `git status --short --branch`，记录并保留用户的无关改动。
- [ ] 查询 `sql/mysql/migrations` 当前最大版本，新迁移顺延且不重号。
- [ ] 运行现有 SEO 后端测试和前端类型检查，确认基线绿色。

### Task 1：用测试锁定数据模型和租户边界

- [ ] 先写 SQL 合同测试：四张表、索引、外键/逻辑关联、租户字段、逻辑删除字段和唯一性。
- [ ] 测试同一分析内焦点和关联关键词的排序唯一性。
- [ ] 测试归一化后重复关键词被拒绝。
- [ ] 测试跨租户、跨站点、跨实体访问失败。
- [ ] 实现 Flyway 迁移、DO、Mapper 和枚举。

### Task 2：建立不可变内容快照

- [ ] 为四种实体定义统一 `SeoContentSnapshot`：标题、简介、Meta、Slug、标题层级、正文段落、属性、图片 ALT 和来源定位。
- [ ] 规范化 JSON 并生成 `contentHash`，相同输入 + 相同引擎版本应得到相同结果。
- [ ] 保留快照中的来源字段/段落编号，使证据可跳转。
- [ ] 测试实体在分析后变更时，旧分析仍可复现。

### Task 3：实现分词、归一化和行业词典

- [ ] 测试 Unicode NFKC、大小写、全半角、标点、空格和 HTML 文本归一化。
- [ ] 保护家具型号、长宽高、单位和常见材质复合词，避免误分词。
- [ ] 新建受版本控制的中英文家具同义词、变体和意图切面词典。
- [ ] 每个归一化和词典命中都保留原词、规范词和词典版本作为证据。

### Task 4：实现五分项评分与确定性建议

- [ ] 每个分项先写 0、边界、满分和 `NOT_APPLICABLE` 测试。
- [ ] 实现关键位置、精确/变体、分布/自然度和意图/主题覆盖。
- [ ] 实现分项统一权重汇总和最后取整。
- [ ] 实现每条失败规则到证据、原因、建议和可恢复分值的确定性映射。
- [ ] 测试一个关键词的命中不得给另一个关键词加分或生成证据。
- [ ] 测试无关关键词产生“删除/换页”建议，而不是堆砌建议。

### Task 5：接入 Lucene/BM25 和可降级语义提供者

- [ ] Lucene 索引键包含 `tenantId/siteId/locale/entityType/entityId`，测试无跨租户语料泄漏。
- [ ] 用固定小语料库锁定 BM25 排序和字段权重。
- [ ] 实现 `SeoSemanticSimilarityProvider` 接口，提供正常 BGE-M3 和禁用/故障实现。
- [ ] 用固定评测集校准原始向量相似度到百分比的映射，将参数作为版本化资源。
- [ ] 故障测试验证：语义分为 `NULL`、结果为 `PARTIAL`、暂定分数正确归一化，界面/API 不显示伪 0%。

### Task 6：实现分析编排、API 和历史对比

- [ ] `run` 在事务中创建任务和快照，后续异步分析可重试且不重复生成关键词记录。
- [ ] 逐关键词分析失败互相隔离；一个关联关键词失败时，已完成的其他关键词仍可查看。
- [ ] 详情 API 一次返回关键词摘要，证据详情可按关键词懒加载。
- [ ] 对比 API 按 `keywordType + normalizedKeyword` 配对，正确处理新增、删除和改名关键词。
- [ ] 保护所有接口的 `seo:analysis:run/query/apply` 权限和租户边界。

### Task 7：实现统一主色 UI

- [ ] 先写 API 类型合同和组件渲染测试。
- [ ] 每个关键词一条进度条，焦点关键词默认展开。
- [ ] 进度条统一使用 `var(--el-color-primary)`，禁止根据分值切换红/黄/绿或在组件中硬编色值。
- [ ] 展开层显示五项百分比、证据、原因、建议和可恢复分值。
- [ ] 显示暂定/失败状态，并为进度条、按钮、展开项补齐键盘和辅助文本。
- [ ] 实现上次→本次对比，不将新结果写回旧记录。

### Task 8：评测、降级和发布验收

- [ ] 固定评测集：50 个中文商品样本、50 个英文商品样本、20 个故意缺失/堆砌/无关样本，每个样本至少包含焦点词和两个关联词。
- [ ] 两名业务审核人独立标注关联度等级和关键问题，分歧由第三人仲裁。
- [ ] 人工等级精确一致率不低于 85%，相差不超过一个等级的比例不低于 95%。
- [ ] 与 Yoast 共有的英文页面检测规则结论一致率不低于 90%；语义和家具属性是 ERP 额外项，单独验收。
- [ ] 注入语义、Lucene、数据库和异步任务故障，验证状态、可重试性和已完成数据保留。
- [ ] 验证公开 API 不暴露分析证据、原文快照和草稿数据。
- [ ] 通过后端模块测试、集成测试、SQL 迁移测试、前端类型检查、组件测试和生产构建。

## 10. 验收清单

- [ ] 焦点关键词和每个关联关键词均有独立记录和 0～100% 结果。
- [ ] 每个关键词均能展开五项分值、命中证据、扣分原因和具体建议。
- [ ] 同一篇内容的多关键词结果不串数据。
- [ ] 所有关键词进度条统一主色，并且不依赖颜色判断等级。
- [ ] 语义不可用时显示暂定值和 `PARTIAL`，语义分不伪造为 0%。
- [ ] 内容无法解析时显示原因，不用 0% 误导用户。
- [ ] 重新分析保留历史，可对比分数和问题变化。
- [ ] 没有任何建议自动改写正式商品或 SEO 元数据。
- [ ] AI 未配置时，本计划的全部分析、证据和确定性建议均可用。
- [ ] 百分比界面包含“非搜索排名保证”的明确声明。

## 11. 后续计划边界

1. DOCX/PDF/XLSX 文档解析计划：输出统一 `SeoContentSnapshot`，再调用本计划的分析引擎。
2. Sitemap、robots 和重定向技术 SEO 计划。
3. `furniture web` Head、Canonical、Open Graph 和 Schema 的初始 HTML 渲染计划。
4. AI 润色独立实验计划：先用固定样本评估事实准确性、可用性和人工接受率，确认有价值后再进入产品实施。
