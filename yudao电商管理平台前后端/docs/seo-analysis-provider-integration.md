# SEO 分析提供者与文档接入说明

## 当前能力

SEO 逐关键词分析现在有三类可替换的接入点：

1. Lucene BM25：把同租户、同站点、同语言的内容放入隔离索引，计算当前内容相对站内语料的字面相关度。
2. BGE-M3：通过 OpenAI 兼容的 `/v1/embeddings` 接口计算关键词与内容的向量相似度。
3. 文档解析：接收 DOCX、文本型 PDF 和 XLSX，抽取统一文本快照后复用现有逐关键词评分引擎。

BM25 和 BGE-M3 默认关闭。未启用、语料不足或服务故障时，分析不会失败，也不会把缺失能力伪装成 `0%`；结果标记为 `PARTIAL`，并使用仍然可用的确定性分项归一化评分。

文档解析默认可用，不依赖独立模型服务。

## 配置

配置位于 `yudao-module-seo-server/src/main/resources/application.yaml`，推荐在部署环境中使用环境变量覆盖。

### Lucene BM25

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SEO_BM25_ENABLED` | `false` | 是否启用 Lucene BM25 |
| `SEO_BM25_INDEX_PATH` | `${user.home}/.oakved/seo-bm25` | 持久化索引目录 |
| `SEO_BM25_MIN_CORPUS_SIZE` | `20` | 同一索引分区参与评分的最低内容数量 |
| `SEO_BM25_MAX_HITS` | `1000` | 单次用于相对排名的最大命中数 |

索引分区包含 `tenantId/siteId/locale`，文档键包含 `entityType/entityId`。同一实体再次分析会更新原索引文档，不会重复追加。

生产环境应把 `SEO_BM25_INDEX_PATH` 指向持久化、可写并且只供当前 SEO 服务实例使用的目录。多实例共享写入需要后续改为集中式索引服务，不能让多个 Lucene 进程直接写同一个目录。

### BGE-M3

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SEO_BGE_M3_ENABLED` | `false` | 是否启用语义评分 |
| `SEO_BGE_M3_BASE_URL` | `http://127.0.0.1:8000` | OpenAI 兼容模型服务地址 |
| `SEO_BGE_M3_API_KEY` | 空 | 可选 Bearer Token |
| `SEO_BGE_M3_MODEL` | `BAAI/bge-m3` | 请求中的模型名，同时写入分析版本 |
| `SEO_BGE_M3_ENDPOINT_PATH` | `/v1/embeddings` | Embeddings 路径 |
| `SEO_BGE_M3_CONNECT_TIMEOUT` | `2s` | 连接超时 |
| `SEO_BGE_M3_READ_TIMEOUT` | `8s` | 读取超时 |
| `SEO_BGE_M3_MIN_SIMILARITY` | `0.25` | 映射为 0% 的余弦相似度 |
| `SEO_BGE_M3_MAX_SIMILARITY` | `0.85` | 映射为 100% 的余弦相似度 |
| `SEO_BGE_M3_MAX_CONTENT_CHARS` | `12000` | 单次送入模型的最大内容字符数 |

模型服务需要接受以下 OpenAI 兼容请求，并按 `index` 返回两个浮点向量：

```json
{
  "model": "BAAI/bge-m3",
  "input": ["目标关键词", "待分析内容"],
  "encoding_format": "float"
}
```

`MIN_SIMILARITY` 和 `MAX_SIMILARITY` 是可调校准参数，不代表搜索引擎排名概率。正式上线前应使用已人工标注的中英文商品样本校准。

### 文档上传

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SEO_DOCUMENT_MAX_FILE_SIZE` | `16MB` | 单文件上限，同时用于 Spring Multipart 和解析器校验 |
| `SEO_DOCUMENT_MAX_REQUEST_SIZE` | `32MB` | Multipart 请求总上限 |
| `SEO_DOCUMENT_MAX_EXTRACTED_CHARS` | `200000` | 抽取文本最大字符数，超过时截断并返回标记 |

当前允许扩展名在配置项 `yudao.seo.analysis.document.allowed-extensions` 中维护，默认是 `docx`、`pdf`、`xlsx`。

接口：

- `POST /admin-api/seo/analysis/document/parse`：仅解析并返回预览。
- `POST /admin-api/seo/analysis/document/run`：解析后创建 `DOCUMENT` 来源的 SEO 分析记录。

两个接口都需要 `seo:analysis:run` 权限，表单文件字段名为 `file`。

当前基础版本已完成文件大小、扩展名、空文件和空解析结果校验。文件签名/MIME 双重校验、压缩炸弹限制、加密文件识别、PDF OCR、Excel 商品行结构化识别和文件哈希去重属于后续安全与解析增强，不应在完成这些增强前把当前解析器当作面向公网的无隔离文件处理服务。

## 启用示例

PowerShell 临时启用本地 BM25 和 BGE-M3：

```powershell
$env:SEO_BM25_ENABLED = "true"
$env:SEO_BM25_INDEX_PATH = "D:\oakved-data\seo-bm25"
$env:SEO_BGE_M3_ENABLED = "true"
$env:SEO_BGE_M3_BASE_URL = "http://127.0.0.1:8000"
$env:SEO_BGE_M3_MODEL = "BAAI/bge-m3"
```

环境变量只对当前进程及其子进程生效。通过分支启动器启动时，需要确保这些变量传递给 SEO 服务进程；未配置时系统会安全回退到已有的规则和行业词典分析。

## 数据库影响

本次提供者和文档解析基础接入不新增数据库表，也不需要新的 Flyway 迁移。文档内容仍保存为已有的不可变分析快照，重新分析使用已保存快照，不依赖原始上传文件。
