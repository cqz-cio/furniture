package cn.iocoder.yudao.module.seo.service.analysis.engine;

import org.springframework.stereotype.Component;

@Component
public class SeoRuleSuggestionService {

    public String missingPositionReason(String location, String keyword) {
        return locationLabel(location) + "未命中关键词“" + keyword + "”的完整词组或已知变体";
    }

    public String missingPositionRecommendation(String location, String keyword) {
        return switch (location) {
            case "SEO_TITLE" -> "在不改变商品事实的前提下，在 SEO 标题中自然保留一次“" + keyword + "”";
            case "H1" -> "让页面主标题明确表达“" + keyword + "”代表的商品主题，不要重复堆叠";
            case "INTRODUCTION" -> "在简介前 120 个字内补充一句包含“" + keyword + "”的事实性描述";
            case "META_DESCRIPTION" -> "在 Meta Description 中自然说明“" + keyword + "”与商品的关系";
            case "HEADING" -> "仅当正文确实讨论该主题时，在一个 H2/H3 小标题中表达“" + keyword + "”";
            case "SLUG" -> "对新页面使用与“" + keyword + "”一致的简短 Slug；已上线 URL 需先规划 301 重定向";
            case "IMAGE_ALT" -> "仅对真实展示该商品特征的图片，在 ALT 中准确描述“" + keyword + "”";
            default -> "在" + locationLabel(location) + "中补充与“" + keyword + "”相关的真实内容";
        };
    }

    public String unrelatedRecommendation(String keyword) {
        return "当前内容与“" + keyword + "”缺少可验证的主题联系；如果商品并不具备该属性，请删除该关键词或转到更合适的页面，不要强行插入";
    }

    public String locationLabel(String location) {
        return switch (location) {
            case "SEO_TITLE" -> "SEO 标题";
            case "H1" -> "H1/商品主标题";
            case "INTRODUCTION" -> "简介开头";
            case "META_DESCRIPTION" -> "Meta Description";
            case "HEADING" -> "H2/H3 小标题";
            case "SLUG" -> "Slug";
            case "IMAGE_ALT" -> "图片 ALT";
            case "BODY" -> "正文";
            case "ATTRIBUTE" -> "商品属性";
            default -> location;
        };
    }

}
