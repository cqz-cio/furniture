package cn.iocoder.yudao.module.seo.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * SEO module error-code contracts in the reserved 1-070 segment.
 */
public interface ErrorCodeConstants {

    ErrorCode LOCALE_INVALID = new ErrorCode(1_070_000_000, "Locale 必须是有效的 BCP 47 语言标签");

    ErrorCode SITE_CONFIG_NOT_EXISTS = new ErrorCode(1_070_001_000, "SEO 站点配置不存在");
    ErrorCode SITE_CONFIG_URL_INVALID = new ErrorCode(1_070_001_001, "SEO 站点地址必须是有效的 HTTP(S) 绝对地址");

    ErrorCode METADATA_NOT_EXISTS = new ErrorCode(1_070_002_000, "SEO 元数据不存在");
    ErrorCode METADATA_DUPLICATE = new ErrorCode(1_070_002_001, "该实体和语言的 SEO 元数据已存在");
    ErrorCode ENTITY_TYPE_INVALID = new ErrorCode(1_070_002_002, "SEO 实体类型不支持");
    ErrorCode METADATA_VERSION_CONFLICT = new ErrorCode(1_070_002_003, "SEO 元数据已被其他用户修改，请刷新后重试");
    ErrorCode METADATA_IDENTITY_IMMUTABLE = new ErrorCode(1_070_002_004, "SEO 元数据的站点、实体和语言不可修改");
    ErrorCode METADATA_CANONICAL_URL_INVALID = new ErrorCode(1_070_002_005,
            "Canonical URL 必须是有效的 HTTP(S) 绝对地址");

    ErrorCode ANALYSIS_NOT_EXISTS = new ErrorCode(1_070_003_000, "SEO 分析记录不存在");
    ErrorCode ANALYSIS_SOURCE_NOT_SUPPORTED = new ErrorCode(1_070_003_001, "当前 SEO 分析来源尚不支持");
    ErrorCode ANALYSIS_CONTENT_REQUIRED = new ErrorCode(1_070_003_002, "SEO 分析内容不能为空");
    ErrorCode ANALYSIS_KEYWORD_DUPLICATE = new ErrorCode(1_070_003_003,
            "焦点关键词和关联关键词归一化后不能重复");
    ErrorCode ANALYSIS_IDEMPOTENCY_CONFLICT = new ErrorCode(1_070_003_004,
            "幂等键已用于不同的 SEO 分析内容");
    ErrorCode KEYWORD_ANALYSIS_NOT_EXISTS = new ErrorCode(1_070_003_005, "SEO 关键词分析记录不存在");
    ErrorCode ANALYSIS_KEYWORD_INVALID = new ErrorCode(1_070_003_006, "SEO 关键词归一化后不能为空");
    ErrorCode ANALYSIS_COMPARISON_MISMATCH = new ErrorCode(1_070_003_007,
            "只能对比同一站点、实体和语言的 SEO 分析");

    ErrorCode DOCUMENT_FILE_EMPTY = new ErrorCode(1_070_004_000, "SEO 分析文件不能为空");
    ErrorCode DOCUMENT_TYPE_UNSUPPORTED = new ErrorCode(1_070_004_001,
            "SEO 分析文件仅支持 DOCX、PDF 和 XLSX");
    ErrorCode DOCUMENT_FILE_TOO_LARGE = new ErrorCode(1_070_004_002, "SEO 分析文件超过允许大小");
    ErrorCode DOCUMENT_PARSE_FAILED = new ErrorCode(1_070_004_003, "SEO 分析文件内容解析失败");

}
