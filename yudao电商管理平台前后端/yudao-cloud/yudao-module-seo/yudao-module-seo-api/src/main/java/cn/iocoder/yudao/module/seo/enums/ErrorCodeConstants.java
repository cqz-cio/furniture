package cn.iocoder.yudao.module.seo.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * SEO module error-code contracts in the reserved 1-070 segment.
 */
public interface ErrorCodeConstants {

    ErrorCode SITE_CONFIG_NOT_EXISTS = new ErrorCode(1_070_001_000, "SEO 站点配置不存在");
    ErrorCode SITE_CONFIG_URL_INVALID = new ErrorCode(1_070_001_001, "SEO 站点地址必须是有效的 HTTP(S) 绝对地址");

    ErrorCode METADATA_NOT_EXISTS = new ErrorCode(1_070_002_000, "SEO 元数据不存在");
    ErrorCode METADATA_DUPLICATE = new ErrorCode(1_070_002_001, "该实体和语言的 SEO 元数据已存在");
    ErrorCode ENTITY_TYPE_INVALID = new ErrorCode(1_070_002_002, "SEO 实体类型不支持");
    ErrorCode METADATA_VERSION_CONFLICT = new ErrorCode(1_070_002_003, "SEO 元数据已被其他用户修改，请刷新后重试");

}
