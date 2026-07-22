# Yudao ERP SEO 模块设计

日期：2026-07-15
最近修订：2026-07-22
状态：总体方案已确认；关键词关联度分析与展示规格已确认，待实施
目标分支：`codex/agent-rag`

## 1. 背景与目标

当前 Yudao 电商后台包含商品、分类、文章、商城装修、文件存储和 AI 能力，但没有独立 SEO 模块。商品已有 `name`、`keyword`、`introduction`、`description` 等基础字段；`furniture web` 仅在浏览器运行后设置部分页面标题和描述，尚未形成可管理、可追溯、可批量检测的 SEO 闭环。

本项目新增独立 SEO 模块，使运营人员可以：

1. 上传 Word、PDF、Excel 商品资料并查看结构化解析结果。
2. 为商品、分类、文章和装修页面维护 SEO 元数据。
3. 使用达到 Yoast SEO 免费版适用检测项水平的规则分析内容。
4. 使用中文关键词匹配、BM25 和语义相似度评估商品与目标关键词的关联性。
5. 对焦点关键词和每一个关联关键词分别输出 0～100% 关联度、五个分项百分比、证据、原因和可执行修改建议。
6. 获得可解释的 0～100 SEO 优化评分、可信度、逐项问题和优化建议。
7. 经人工确认后将解析内容或优化建议写入 ERP；任何分析不得自动覆盖正式商品数据。
8. 在公开网站输出 Title、Meta Description、Canonical、Robots、Open Graph 和 Schema.org 商品结构化数据。
9. 管理 Sitemap、robots.txt 和 301/302 重定向。

评分名称统一为“SEO 优化评分”，不得称为“Google 排名分数”。评分用于发现内容与技术问题，不承诺搜索排名。

## 2. 范围

### 2.1 第一阶段

- 独立 SEO 后端模块、后台菜单、权限和租户隔离。
- 商品、分类、文章、装修页面 SEO 元数据管理。
- Word `.doc/.docx`、Excel `.xls/.xlsx`、文本型 PDF 上传与解析。
- 解析预览、字段确认和显式应用操作。
- 目标关键词手工输入与自动推荐。
- Yoast 免费版适用于商品/内容页的检测规则。
- 中文/英文规则配置、BM25 字面相关性和 BGE-M3 语义相关性。
- 逐关键词独立百分比分析：焦点关键词与每个关联关键词都必须单独评分和解释。
- 基于确定性规则和证据的修改建议；建议必须指明位置、问题原因和具体改法。
- 商品资料完整度、重复内容和结构化数据完整度检查。
- SEO 控制台、内容优化列表、文档分析详情、设置、Sitemap、robots 和重定向页面。
- 公开 SEO 元数据 API、Sitemap、robots.txt 和重定向解析接口。
- `furniture web` 消费 SEO 数据并输出页面 Head 与 JSON-LD。
- 单元、集成、安全、降级和 Yoast 对照测试。

### 2.2 第二阶段

- 图片型/扫描 PDF OCR。
- 全站爬虫、死链扫描和站内重复页面检测。
- Google Search Console 数据同步。
- 关键词排名、竞争对手和外链分析。
- 大规模批量重新分析和定时健康检查。
- AI 标题、Meta Description 和正文润色，以人工确认的草稿方式提供。

第二阶段不阻塞第一阶段发布。

## 3. 非目标

- 不复制或嵌入 Yoast GPL 源码、文案、界面或品牌资产。
- 不复刻 WordPress 数据模型、Hook、Gutenberg 或插件运行时。
- 不把 `meta keywords` 输出到公开页面；Google 不使用该标签。目标关键词只用于内部分析。
- 不让 AI 自动修改正式商品、分类、文章或页面数据。
- 本轮关键词分析不包含生成式 AI 润色；语义向量只用于关联度计算，不生成或改写商品文案。
- 不把 SEO 优化评分解释为搜索引擎真实排名概率。
- 第一阶段不承诺解析图片型 PDF；检测到无文本 PDF 时返回明确的 `NEEDS_OCR` 状态。

## 4. 总体架构

### 4.1 后端模块

在 `yudao-cloud` 新增：

```text
yudao-module-seo
├─ yudao-module-seo-api
└─ yudao-module-seo-server
```

- `yudao-module-seo-api`：公开 DTO、枚举和跨模块接口。
- `yudao-module-seo-server`：Controller、Service、规则引擎、文档解析器、持久化、公开端接口和定时任务。
- 根 `pom.xml` 注册新模块。
- `yudao-server` 引入 `yudao-module-seo-server`，保持当前单体启动方式。

SEO 模块通过明确接口访问其他模块：

- `yudao-module-infra`：文件保存与读取。
- `yudao-module-mall`：商品、分类、文章、装修页只读快照及经授权的写入动作。
- `yudao-module-ai`：可选的生成式优化建议。

SEO 规则引擎不得直接操作商城模块的数据表。

### 4.2 后台前端

在 `yudao-ui-admin-vue3/src/views/seo` 新增页面，在 `src/api/seo` 新增 API 封装，并通过 Yudao 动态菜单和权限体系注册。

菜单结构：

```text
SEO 管理
├─ SEO 控制台
├─ 内容优化
│  ├─ 商品 SEO
│  ├─ 分类 SEO
│  ├─ 文章 SEO
│  └─ 页面 SEO
├─ 文档分析
├─ Sitemap
├─ robots.txt
├─ 重定向管理
└─ SEO 设置
```

### 4.3 公开网站

`furniture web` 通过公开 SEO API 获取生效元数据。所有可索引页面必须输出：

- `<title>`
- `<meta name="description">`
- `<link rel="canonical">`
- `<meta name="robots">`
- Open Graph/Twitter 元数据
- 与页面可见内容一致的 JSON-LD

现有网站是客户端 Vue SPA。仅在浏览器执行后修改 `document.title` 不作为最终验收。第一阶段增加一个 Head 渲染适配层，使上述内容存在于索引页面的初始 HTML 响应中；浏览器端继续使用相同 SEO API 更新路由切换后的 Head。Head 渲染适配层不得向搜索引擎和普通用户返回不同业务内容。

部署时将站点域名下的 `/sitemap.xml`、`/robots.txt` 和重定向解析路由转发到 SEO 模块公开接口。

## 5. 组件边界

### 5.1 `SeoMetadataService`

负责 SEO 元数据的 CRUD、版本校验、实体存在性校验、唯一性和发布状态。它不负责评分和 AI 生成。

### 5.2 `SeoDocumentService`

负责上传记录、文件安全校验、解析任务状态、结构化解析结果和人工确认。它通过 `SeoDocumentParser` 接口选择 Word、Excel 或 PDF 解析器。

### 5.3 `SeoAnalysisService`

负责创建分析快照、调用规则引擎、字面相关性和语义相关性提供者、汇总得分与可信度并持久化结果。分析必须基于不可变输入快照，保证结果可追溯。

### 5.4 `SeoRuleEngine`

加载指定版本的规则配置，逐条产生 `SeoAnalysisItem`。每条规则必须有稳定的 `ruleCode`、证据、状态、得分、用户提示和建议。

### 5.5 `SeoLexicalRelevanceProvider`

负责中文/英文归一化、关键词匹配、词频、关键词分布、重复/堆砌检测和 BM25。使用 Apache Lucene 及版本化商品行业词典、同义词表。

### 5.6 `SeoSemanticSimilarityProvider`

负责关键词、商品摘要和段落之间的语义相似度。初始实现使用 MIT 许可证的 BGE-M3 模型；模型通过 ONNX Runtime Java 或内网模型服务运行。模型文件不打包进应用 JAR，通过配置路径或服务地址加载。

### 5.7 `SeoRuleSuggestionService`

根据每个关键词的规则失败项生成确定性建议。建议必须包含稳定的 `ruleCode`、严重程度、原文位置、证据摘要、扣分原因、具体改法和可恢复分值。建议不依赖生成式 AI，不得为提高词频而强制堆砌关键词。

### 5.8 `SeoAiPolishService`（后续阶段）

通过现有 AI 模块生成标题、描述和段落润色草稿。该服务不在本轮关键词分析实施范围内，未来引入时也不参与确定性分数，且所有输出只能作为待人工确认的草稿。

### 5.9 `SeoPublicService`

负责公开元数据解析、默认值回退、Sitemap、robots.txt、Schema 和重定向规则查询。不得暴露内部评分证据、上传原文或管理字段。

## 6. 数据模型

所有表继承 Yudao 通用审计字段并包含 `tenant_id`。需要站点区分的表包含 `site_id`。

### 6.1 `seo_site_config`

- `id`
- `site_id`
- `site_name`
- `domain`
- `default_locale`
- `title_template`
- `default_description`
- `default_og_image`
- `default_robots_index`
- `default_robots_follow`
- `robots_content`
- `sitemap_enabled`
- `organization_schema` JSON
- `active_rule_profile`
- `status`

唯一键：`tenant_id + site_id`。

### 6.2 `seo_metadata`

- `id`
- `site_id`
- `entity_type`：`PRODUCT/CATEGORY/ARTICLE/PAGE`
- `entity_id`
- `locale`
- `focus_keyphrase`
- `related_keyphrases` JSON
- `seo_title`
- `meta_description`
- `slug`
- `canonical_url`
- `robots_index`
- `robots_follow`
- `og_title`
- `og_description`
- `og_image`
- `schema_type`
- `schema_overrides` JSON
- `publish_status`：`DRAFT/PUBLISHED`
- `latest_analysis_id`
- `version`

唯一键：`tenant_id + site_id + entity_type + entity_id + locale`。

### 6.3 `seo_document`

- `id`
- `file_id`
- `file_name`
- `media_type`
- `file_size`
- `sha256`
- `document_type`：`WORD/EXCEL/PDF`
- `parse_status`：`PENDING/RUNNING/SUCCEEDED/FAILED/NEEDS_OCR`
- `parser_version`
- `detected_locale`
- `extraction_confidence`
- `extracted_text`
- `structured_content` JSON
- `failure_code`
- `failure_message`

`extracted_text` 与 `structured_content` 只对具有 SEO 管理权限的用户开放。

### 6.4 `seo_analysis`

- `id`
- `site_id`
- `source_type`：`DOCUMENT/ENTITY/MANUAL`
- `source_id`
- `entity_type`
- `entity_id`
- `locale`
- `focus_keyphrase`
- `input_snapshot` JSON
- `lexical_score`
- `semantic_score`
- `metadata_score`
- `completeness_score`
- `readability_score`
- `technical_score`
- `total_score`
- `confidence_score`
- `score_grade`：`EXCELLENT/GOOD/NEEDS_WORK/POOR`
- `engine_version`
- `rule_profile_version`
- `semantic_model_version`
- `analysis_status`：`PENDING/RUNNING/SUCCEEDED/PARTIAL/FAILED`
- `content_hash`：规范化分析文本的 SHA-256，用于幂等与前后对比
- `overall_relevance_percent`：焦点与关联关键词汇总值，仅作摘要
- `failure_code`
- `failure_message`

### 6.5 `seo_analysis_item`

- `id`
- `analysis_id`
- `rule_code`
- `category`
- `status`：`ERROR/WARNING/GOOD/NOT_APPLICABLE`
- `score`
- `max_score`
- `evidence` JSON
- `message`
- `recommendation`
- `sort`

该表用于不属于某一具体关键词的元数据、完整度、可读性和技术 SEO 规则。关键词级的结果使用下述两张表，不得只把多个关键词压成一条总分记录。

### 6.6 `seo_keyword_analysis`

每次分析中，焦点关键词和每个关联关键词各保存一条记录。

- `id`
- `analysis_id`
- `keyword_type`：`FOCUS/RELATED`
- `keyword`
- `normalized_keyword`
- `sort`
- `key_position_percent`
- `lexical_match_percent`
- `semantic_percent`；语义未完成时为 `NULL`，不得伪造为 `0`
- `distribution_percent`
- `intent_coverage_percent`
- `relevance_percent`
- `confidence_percent`
- `grade`：`HIGH/MEDIUM/WEAK/LOW`
- `analysis_status`：`SUCCEEDED/PARTIAL/FAILED`
- `exact_match_count`
- `variant_match_count`
- `matched_locations` JSON
- `dictionary_version`
- `semantic_model_version`

唯一键：`analysis_id + keyword_type + sort`。同一次分析内对归一化后重复的关键词拒绝创建，并向用户标明重复项。

### 6.7 `seo_keyword_analysis_item`

记录某一关键词的分项证据、问题和修改建议。

- `id`
- `keyword_analysis_id`
- `rule_code`
- `dimension`：`KEY_POSITION/LEXICAL/SEMANTIC/DISTRIBUTION/INTENT`
- `severity`：`HIGH/MEDIUM/LOW/INFO`
- `status`：`ISSUE/GOOD/NOT_APPLICABLE/NOT_COMPLETED`
- `score`
- `max_score`
- `content_location`：如 `SEO_TITLE/INTRODUCTION/BODY/H2/IMAGE_ALT`
- `evidence` JSON：命中词、变体、字符位置和上下文摘要
- `reason`
- `recommendation`
- `recoverable_score`
- `sort`

同一个 `rule_code` 可对不同关键词产生不同的证据和建议。前端必须按关键词展示，不能将不同关键词的原因混在一个问题列表中。

### 6.8 `seo_redirect`

- `id`
- `site_id`
- `source_path`
- `target_url`
- `redirect_type`：`301/302`
- `enabled`
- `hit_count`
- `last_hit_time`
- `remark`

唯一键：`tenant_id + site_id + source_path`。保存时拒绝自重定向和可检测的重定向环。

## 7. 文档上传与解析

### 7.1 安全边界

- 默认文件上限 20 MB，可通过配置调整。
- 同时校验扩展名、MIME、文件签名和压缩包展开限制。
- 拒绝加密文件、宏执行、外部实体加载和压缩炸弹。
- 文件名不得用于磁盘路径拼接。
- 解析使用受限临时目录并在任务结束后清理。
- 保存 SHA-256，避免同租户重复解析相同文件。

### 7.2 解析器

- Word：Apache Tika/POI，提取段落、标题、表格、图片说明和文档元数据。
- Excel：Apache POI，保留工作表、表头、行和单元格关系；支持“一行一个商品”和“键值表”两种结构识别。
- PDF：Apache Tika/PDFBox，支持包含文本层的 PDF。无有效文本时设置 `NEEDS_OCR`，不得基于空文本评分。

### 7.3 统一结构

所有解析器输出统一 `ProductDocumentSnapshot`：

```json
{
  "name": "",
  "brand": "",
  "category": "",
  "introduction": "",
  "description": "",
  "attributes": {},
  "headings": [],
  "paragraphs": [],
  "tables": [],
  "images": [],
  "sourceEvidence": {}
}
```

界面必须同时显示提取值和来源证据。用户确认后才能创建分析或应用到商品。

## 8. 分析与评分

### 8.1 总体 SEO 优化评分

```text
SEO 优化评分 =
  关键词关联度       35%
+ 元数据质量         20%
+ 商品资料完整度     20%
+ 内容结构与可读性   15%
+ 技术 SEO 准备度    10%
```

总体 SEO 优化评分和“单个关键词关联度”是两个层次。前者用于页面综合摘要，后者是内容优化的主要依据，不得用一个总分取代逐关键词结果。

总体关键词关联度摘要：

```text
有关联关键词时：
总体关键词关联度 = 焦点关键词 60% + 关联关键词平均值 40%

没有关联关键词时：
总体关键词关联度 = 焦点关键词 100%
```

分数与可信度分开计算。语义服务降级、解析证据不足或来源为低质量 OCR 时降低可信度，不用伪造满信息得分。

### 8.2 逐关键词关联度算法

焦点关键词和每一个关联关键词都执行同一套 0～100% 计算：

```text
单个关键词关联度 =
  关键位置覆盖       25%
+ 精确词/变体匹配   20%
+ 语义相关性         25%
+ 分布与自然度       15%
+ 搜索意图/主题覆盖 15%
```

五个分项自身也统一输出 0～100%，最终按上述权重汇总。

- **关键位置覆盖**：检查 SEO 标题、H1、开头简介、Meta Description、Slug、H2/H3 和图片 ALT，不同位置有不同权重。
- **精确词/变体匹配**：经过中英文归一化、分词、型号与单位保护后，统计精确匹配、同义词、近义变体和 BM25 字面相关度。
- **语义相关性**：比较关键词与标题、简介、属性摘要及正文分段的向量相似度，防止只用字面词频判断。
- **分布与自然度**：检查关键词是否集中在单一段、是否自然分布在主要区域，并扣除堆砌、重复句和隐藏词等风险。
- **搜索意图/主题覆盖**：结合家具行业词典，判断内容是否回答该关键词隐含的品类、材质、尺寸、风格、场景、保养、交付等信息需求。

关联度等级只使用百分比和文字：

- 80～100%：高度相关
- 60～79%：基本相关
- 40～59%：关联较弱
- 0～39%：关联度低

这些百分比是 ERP 内部“内容与关键词匹配度”，不是 Google 排名概率，也不是搜索引擎公开分数。

### 8.3 不完整分析的计分规则

- 语义模型可用时，按五项完整公式计算。
- 语义模型超时或不可用时，`semantic_percent` 保持为 `NULL`，不得记为 0%。界面将可用分项按剩余权重归一化后显示“暂定关联度”，同时标记 `PARTIAL` 并降低可信度。
- 内容为空、文档未解析或语言无法识别时，不显示 0% 误导用户，而是显示“无法分析”和具体缺失原因。
- 任何降级结果都不得自动应用到商品或 SEO 元数据。

### 8.4 Yoast 对标规则

第一阶段覆盖以下适用于商品、分类、文章和页面的规则：

- 关键词长度。
- 关键词位于标题、简介、Meta Description、Slug、小标题和图片 ALT。
- 关键词密度、自然分布和堆砌检测。
- 同站点/语言关键词重复使用。
- SEO 标题宽度与 Meta Description 长度。
- 内容长度与正文存在性。
- 内部链接、外部链接和竞争性链接。
- 图片数量与图片替代文本。
- 单一 H1。
- 小标题分布、段落长度、句子长度和重复句首。

英文可以增加过渡词和被动语态。中文不直接套用 Flesch、英文被动语态和英文过渡词规则，而使用：

- 中文句长和段落长度。
- 重复表达、广告词堆砌和异常标点。
- 中英文空格、尺寸、单位与型号规范。
- 家具行业同义词和属性覆盖。
- 材质、尺寸、颜色、风格、场景、保养、交付等商品信息完整度。

规则阈值保存在版本化代码配置中，按 `locale + entity_type + rule_profile_version` 选择。历史分析永远引用原始版本，不随升级自动改分。

### 8.5 语义和 BM25

- Lucene 索引按租户、站点和语言隔离。
- 商品发布或关键文本更新时增量更新索引。
- BM25 用于目标关键词与当前商品文本的相对字面相关性，也用于识别同站点近似竞争内容。
- BGE-M3 分别计算关键词与标题、简介、属性摘要、正文分段的相似度，再按字段权重汇总。
- 家具行业词典和同义词表作为受版本控制的项目资源维护。

### 8.6 修改建议与证据

每个关键词必须分别输出“为什么是这个分数”和“如何提高”。一条问题的完整输出包括：

1. 规则编号和严重程度。
2. 发生位置，例如 SEO 标题、简介第 1 段或图片 ALT。
3. 原文证据或缺失证据，包含命中次数和上下文。
4. 本项得分、满分和扣分原因。
5. 可直接执行的建议，例如“在 H1 保留一次完整词组”，不使用“请优化内容”这类空泛文案。
6. 可确定计算时显示预计可恢复分值；语义类建议不承诺固定提分。

如果关键词与商品核心主题明显无关，系统应建议删除该关键词或改用其他页面承载，不能建议在原文中强行插入。

### 8.7 后台展示规格

内容优化页的逐关键词结果按以下固定结构显示：

```text
[关键词] [焦点/关联]  [================----] 78%  基本相关
  展开后：
  - 关键位置覆盖       84%
  - 精确词/变体匹配   76%
  - 语义相关性         81%
  - 分布与自然度       68%
  - 搜索意图/主题覆盖 72%
  - 命中证据、扣分原因、修改建议和预计可恢复分值
```

- 每个关键词各有一条进度条，数值和宽度都使用 0～100% 的同一度量衡。
- 所有关键词进度条统一使用 Element Plus 主色 token `var(--el-color-primary)`，不硬编色值，不用红、黄、绿多色进度条表示高低。
- 等级差异通过百分比、文字等级和问题数量表达；状态使用文字标签和图标，不单独依赖颜色，以便色觉障碍用户识别。
- 默认展开焦点关键词，关联关键词可逐项展开。
- 重新分析后在同一关键词下显示“上次→本次”的分数变化和已解决/新增问题，不用新结果覆盖历史证据。
- 界面固定展示说明：“关联度是系统内部内容分析指标，不代表搜索引擎排名保证。”

### 8.8 可信度

可信度由以下因素组成：

- 文档解析可信度。
- 关键字段覆盖率。
- 语义模型可用性。
- 规则证据完整度。
- 语言识别可信度。

分析详情同时展示总分和可信度，不用低可信度结果自动应用建议。

## 9. AI 润色（后续可选，不在本轮）

当前开发优先完成可复现的逐关键词评分、证据和确定性建议。AI 润色不是分析成功条件，不影响关联度或 SEO 优化评分。

后续经独立效果评测后若引入 AI，必须满足：

- AI 输入只包含当前租户有权访问的分析快照、规则问题和商品事实。
- Prompt 明确禁止虚构材质、尺寸、价格、认证、库存和配送承诺。
- AI 输出使用结构化 JSON，并将原文、建议文本和依据对照展示。
- 应用建议采用字段级选择，不提供“一键无确认覆盖全部”。

## 10. 前台输出与技术 SEO

### 10.1 默认值回退

若没有已发布 SEO 元数据：

- Title 回退到商品/分类/文章/页面名称加站点模板。
- Description 回退到简介的清洗和截断结果。
- Canonical 回退到当前实体的规范公开 URL。
- Robots 回退到站点默认值。
- Open Graph 回退到 Title、Description 和主图。

### 10.2 Schema

商品页生成 `Product`/`Offer`，包含可验证的名称、图片、描述、SKU、品牌、价格、币种、库存和 URL。存在变体时生成 `ProductGroup`/变体关系。Schema 内容必须与用户可见页面一致，不生成虚假评价或不可见字段。

文章、分类和页面分别使用适用的 `Article`、`BreadcrumbList`、`WebPage` 和站点级 `Organization` 数据。

### 10.3 Sitemap

- 按站点和语言生成 Sitemap 索引。
- 商品、分类、文章、页面分文件输出。
- 只包含已发布、允许索引且存在规范 URL 的实体。
- `lastmod` 使用内容或 SEO 元数据的最新有效更新时间。
- 大集合按协议容量拆分并缓存；内容更新使相关缓存失效。

### 10.4 robots.txt

- 提供编辑、预览和发布。
- 保存前检测明显阻断站点或 Sitemap 的规则并强提醒，但具有权限的管理员可以二次确认。
- 自动追加当前站点 Sitemap URL，除非配置明确关闭。

### 10.5 重定向

- 支持精确路径 301/302。
- 第一阶段不支持用户输入任意正则，避免性能和安全风险。
- 保存时检测重复源、源目标相同和可发现的环。
- 命中计数异步更新，不阻塞重定向响应。

## 11. API 设计

管理端主要接口：

```text
GET/POST/PUT/DELETE /admin-api/seo/metadata
POST                /admin-api/seo/document/upload
GET                 /admin-api/seo/document/{id}
POST                /admin-api/seo/document/{id}/parse
POST                /admin-api/seo/analysis/run
GET                 /admin-api/seo/analysis/{id}
GET                 /admin-api/seo/analysis/{id}/keywords
GET                 /admin-api/seo/analysis/{id}/keywords/{keywordAnalysisId}
GET                 /admin-api/seo/analysis/{id}/compare?previousAnalysisId={previousId}
POST                /admin-api/seo/analysis/{id}/rerun
POST                /admin-api/seo/analysis/{id}/apply
GET/PUT              /admin-api/seo/site-config
GET/POST/PUT/DELETE  /admin-api/seo/redirect
POST                 /admin-api/seo/sitemap/refresh
```

公开端主要接口：

```text
GET /app-api/seo/metadata/resolve
GET /app-api/seo/schema/resolve
GET /app-api/seo/sitemap.xml
GET /app-api/seo/robots.txt
GET /app-api/seo/redirect/resolve
```

异步解析和分析接口返回任务标识，前端轮询状态；重复提交相同幂等键返回原任务。`GET /analysis/{id}` 必须返回关键词摘要列表，每个关键词包含自己的百分比、分项、等级、状态和问题计数，不只返回整体总分。

## 12. 权限

权限前缀统一为 `seo:`：

- `seo:dashboard:query`
- `seo:metadata:query/create/update/delete/publish`
- `seo:document:upload/query/delete`
- `seo:analysis:run/query/apply`
- `seo:site-config:query/update`
- `seo:sitemap:query/update`
- `seo:robots:query/update`
- `seo:redirect:query/create/update/delete`

所有管理查询执行租户过滤。跨模块实体访问同时校验实体所属租户。公开接口只返回已发布数据。

## 13. 错误处理和降级

- 文件不支持、损坏、加密或超限：拒绝解析并返回稳定错误码。
- 图片型 PDF：`NEEDS_OCR`，不评分。
- 语义模型不可用：继续规则和 BM25，分析标记 `PARTIAL` 并降低可信度。
- 确定性建议生成异常：保留已完成的分数和证据，关键词标记 `PARTIAL`，界面指明哪些建议未生成。
- Lucene 索引不可用：继续执行不依赖语料库的字面规则，记录降级原因并触发重建告警。
- 实体在分析后被修改：应用前比较实体版本或更新时间，冲突时要求重新载入或重新分析。
- SEO 配置缺失：公开端使用确定性默认值回退。
- Sitemap 生成失败：继续返回最近一次成功缓存并记录告警。
- 重定向目标非法或成环：拒绝保存。

管理界面不得只显示“系统错误”；应展示可操作的原因，同时将详细堆栈仅写入服务日志。

## 14. 可观测性

记录以下指标：

- 各格式上传、解析成功率和耗时。
- 分析任务成功、部分成功和失败数量。
- 语义、建议生成和索引降级次数。
- 各规则百分比等级分布。
- Sitemap 生成时间、URL 数和缓存命中。
- 重定向命中数和环检测拒绝数。

日志中记录任务 ID、租户、站点、规则/模型版本，不记录完整商品文档和敏感 AI 密钥。

## 15. 测试与验收

### 15.1 自动化测试

- Word、Excel、文本 PDF 解析器单元测试和损坏文件测试。
- 文件签名、MIME、压缩限制、路径和租户安全测试。
- 每条 SEO 规则的百分比等级和边界测试。
- 中文分词、同义词、语义相关、关键词堆砌和重复内容测试。
- 多关键词隔离测试：每个关键词的分数、证据、原因和建议不串数据。
- 五个分项权重、整数取整、边界值、缺失分项归一化和不可分析状态测试。
- 前后两次分析对比测试，验证历史证据不被覆盖。
- 总分、文字等级、可信度和版本固定测试。
- 确定性建议、语义模型和 Lucene 故障降级测试。
- Metadata、Document、Analysis、Sitemap、robots、Redirect API 集成测试。
- 前台初始 HTML 的 Title、Description、Canonical、Robots、OG 和 JSON-LD 测试。
- Sitemap 内容、缓存失效、拆分和 robots 引用测试。
- 重定向状态码、环、非法目标和命中统计测试。

### 15.2 Yoast 对标

固定评测集：

- 50 个英文商品样本。
- 50 个中文商品样本。
- 20 个故意包含缺失、重复、堆砌和技术问题的样本。

英文样本在双方共有规则上的红/黄/绿结论一致率不低于 90%。中文样本不强行复刻不适用的英文可读性规则；所有差异必须由中文规则说明和固定测试证明。ERP 额外的语义、商品完整度和 Schema 检查单独验收。

### 15.3 发布验收

- 运营用户能上传三类文件并看到解析预览；图片型 PDF 有明确 OCR 提示。
- 未确认前，分析不会修改商品数据。
- 焦点关键词和每个关联关键词均有独立的 0～100% 结果、五项分值、原因、证据和修改建议。
- 进度条使用统一系统主色，百分比与文字等级一致，不依赖颜色识别高低。
- 语义服务不可用时仍能完成规则和字面分析，结果明确标记为暂定值；未完成分项不显示为 0%。
- AI 未配置时不影响本轮所有分析、证据和确定性建议。
- 每个结果可追溯到输入快照、规则版本和模型版本。
- 可索引页面的初始 HTML 包含正确 SEO Head，而非仅依赖浏览器执行后修改。
- 公开接口不泄露内部文档、评分证据或草稿数据。
- 所有新增权限和租户隔离测试通过。

## 16. 实施顺序

1. 新模块骨架、SQL、菜单、权限和基础 CRUD。
2. SEO 元数据管理和公开解析 API。
3. 逐关键词数据模型、规则引擎、五项百分比和确定性建议。
4. Yoast 对标检测、Lucene/BM25、行业词典和语义相似度。
5. 逐关键词统一主色 UI、证据展开和分析前后对比。
6. 文档上传、解析与人工确认，解析结果复用同一分析快照和引擎。
7. Sitemap、robots 和重定向。
8. `furniture web` Head/Schema 集成和初始 HTML 渲染适配。
9. 完整回归、Yoast 对标、安全和降级验收。
10. 经单独评测确认有价值后，再实施可选 AI 润色与字段级应用。

每一步都应保持基础功能可测试，不把 AI 或语义模型作为整个模块启动的必要条件。

## 17. 许可证策略

- 允许直接依赖 Apache-2.0、MIT 等适合商业使用的组件，并保留其 NOTICE/许可证要求。
- Yoast 源码仅用于理解公开检测项和行为，不复制到 ERP。
- 引入任何 NLP/Embedding 模型前同时检查代码许可证和模型权重许可证。
- BGE-M3 初始模型使用其 MIT 许可版本并固定模型校验值。
- 依赖升级必须经过许可证、评分回归和安全审查。
