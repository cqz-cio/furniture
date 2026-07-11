package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FurnitureProductSearchTool {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;
    private static final int SEARCH_LIMIT = 10;
    private static final List<String> PRODUCT_KEYWORDS = Arrays.asList(
            "coffee table", "side table", "dining table", "table lamp", "floor lamp",
            "media cabinet", "nightstand", "wardrobe", "rug", "dresser", "desk",
            "sofa", "bed", "chair", "lighting", "lamp", "cabinet", "table");
    private static final List<KeywordAlias> PRODUCT_KEYWORD_ALIASES = Arrays.asList(
            new KeywordAlias("coffee table", "\u8336\u51e0", "\u5ba2\u5385\u8336\u51e0", "\u539f\u6728\u8336\u51e0"),
            new KeywordAlias("side table", "\u8fb9\u51e0", "\u5c0f\u8fb9\u51e0", "\u6c99\u53d1\u8fb9\u51e0"),
            new KeywordAlias("dining table", "\u9910\u684c", "\u5ca9\u677f\u9910\u684c", "\u539f\u6728\u9910\u684c"),
            new KeywordAlias("floor lamp", "\u843d\u5730\u706f"),
            new KeywordAlias("table lamp", "\u53f0\u706f", "\u5e8a\u5934\u706f"),
            new KeywordAlias("media cabinet", "\u7535\u89c6\u67dc", "\u7535\u89c6\u5899\u67dc"),
            new KeywordAlias("desk", "\u4e66\u684c", "\u529e\u516c\u684c", "\u5b66\u4e60\u684c"),
            new KeywordAlias("dresser", "\u6597\u67dc", "\u68b3\u5986\u53f0", "\u5367\u5ba4\u6597\u67dc"),
            new KeywordAlias("nightstand", "\u5e8a\u5934\u67dc", "\u5e8a\u8fb9\u67dc"),
            new KeywordAlias("wardrobe", "\u8863\u67dc", "\u8863\u6a71", "\u6728\u8d28\u8863\u67dc"),
            new KeywordAlias("rug", "\u5730\u6bef", "\u5ba2\u5385\u5730\u6bef", "\u7c73\u8272\u5730\u6bef"),
            new KeywordAlias("sofa", "\u6c99\u53d1", "\u5e03\u827a\u6c99\u53d1", "\u76ae\u6c99\u53d1", "\u6a21\u5757\u6c99\u53d1"),
            new KeywordAlias("bed", "\u5e8a", "\u53cc\u4eba\u5e8a", "\u8f6f\u5305\u5e8a"),
            new KeywordAlias("chair", "\u6905", "\u6905\u5b50", "\u9910\u6905"),
            new KeywordAlias("lighting", "\u706f", "\u540a\u706f", "\u706f\u5177", "\u7167\u660e"),
            new KeywordAlias("cabinet", "\u67dc", "\u67dc\u5b50", "\u6536\u7eb3\u67dc"));
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "(?:under|below|less than|<=|<)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_MAX_PRICE_PATTERN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*(?:元)?\\s*(?:以内|以下|内|之内|以下|以下的|以内的|以下价位|预算内|以下预算|不超过|低于)");
    private static final Pattern CHINESE_LEADING_MAX_PRICE_PATTERN = Pattern.compile(
            "(?:不超过|低于|少于|小于)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:元)?");

    private final ProductSpuService productSpuService;

    public FurnitureProductSearchTool(ProductSpuService productSpuService) {
        this.productSpuService = productSpuService;
    }

    public FurnitureProductSearchResult searchForAssistant(String message) {
        FurnitureProductSearchRequest request = new FurnitureProductSearchRequest();
        request.setMessage(message);
        request.setKeyword(extractKeyword(message));
        request.setMaxPrice(extractMaxPrice(message).orElse(null));
        request.setLimit(DEFAULT_LIMIT);
        return searchProducts(request);
    }

    public boolean shouldSearchProducts(String message, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        String keyword = extractKeyword(message);
        return PRODUCT_KEYWORDS.contains(keyword) || extractMaxPrice(message).isPresent();
    }

    public FurnitureProductSearchResult searchProducts(FurnitureProductSearchRequest request) {
        FurnitureProductSearchRequest normalizedRequest = normalizeRequest(request);
        List<ProductSpuDO> spus = searchSpus(normalizedRequest.getKeyword());
        List<FurnitureAssistantChatRespVO.Product> products = spus.stream()
                .filter(spu -> normalizedRequest.getMaxPrice() == null
                        || toYuan(spu.getPrice()).compareTo(normalizedRequest.getMaxPrice()) <= 0)
                .limit(normalizedRequest.getLimit())
                .map(spu -> toProduct(spu, normalizedRequest.getMessage()))
                .collect(Collectors.toList());
        return FurnitureProductSearchResult.of(products);
    }

    private FurnitureProductSearchRequest normalizeRequest(FurnitureProductSearchRequest request) {
        FurnitureProductSearchRequest normalized = request == null ? new FurnitureProductSearchRequest() : request;
        String message = StrUtil.blankToDefault(normalized.getMessage(), normalized.getKeyword());
        normalized.setMessage(StrUtil.blankToDefault(message, ""));
        normalized.setKeyword(StrUtil.blankToDefault(normalized.getKeyword(), extractKeyword(normalized.getMessage())));
        normalized.setLimit(normalizeLimit(normalized.getLimit()));
        return normalized;
    }

    private Integer normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<ProductSpuDO> searchSpus(String keyword) {
        AppProductSpuPageReqVO pageReqVO = new AppProductSpuPageReqVO();
        pageReqVO.setKeyword(keyword);
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(SEARCH_LIMIT);
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageReqVO);
        return pageResult == null || pageResult.getList() == null ? Collections.emptyList() : pageResult.getList();
    }

    private String extractKeyword(String message) {
        String normalized = StrUtil.blankToDefault(message, "").toLowerCase(Locale.ROOT);
        return PRODUCT_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst()
                .orElseGet(() -> PRODUCT_KEYWORD_ALIASES.stream()
                        .filter(alias -> alias.matches(normalized))
                        .map(KeywordAlias::getKeyword)
                        .findFirst()
                        .orElse(StrUtil.blankToDefault(message, "")));
    }

    private Optional<BigDecimal> extractMaxPrice(String message) {
        String normalized = StrUtil.blankToDefault(message, "");
        Matcher matcher = MAX_PRICE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            matcher = CHINESE_MAX_PRICE_PATTERN.matcher(normalized);
            if (!matcher.find()) {
                matcher = CHINESE_LEADING_MAX_PRICE_PATTERN.matcher(normalized);
                if (!matcher.find()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new BigDecimal(matcher.group(1)));
    }

    private FurnitureAssistantChatRespVO.Product toProduct(ProductSpuDO spu, String message) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(spu.getId());
        product.setSkuId(spu.getId());
        product.setName(spu.getName());
        product.setSubtitle(StrUtil.blankToDefault(spu.getIntroduction(), ""));
        product.setPrice(toYuan(spu.getPrice()));
        product.setMarketPrice(toYuan(spu.getMarketPrice()));
        product.setStock(spu.getStock());
        product.setCover(spu.getPicUrl());
        String defaultReason = containsChinese(message) ? "家具推荐" : "furniture recommendation";
        String request = StrUtil.blankToDefault(message, defaultReason);
        product.setReason(containsChinese(message)
                ? "根据你的需求匹配：" + request + "。"
                : "Matched against your request: " + request + ".");
        product.setDetailUrl("/sofa-pdp?id=" + spu.getId());
        return product;
    }

    private boolean containsChinese(String text) {
        return StrUtil.blankToDefault(text, "").chars()
                .anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
    }

    private BigDecimal toYuan(Integer fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(fen).movePointLeft(2).stripTrailingZeros();
    }

    @lombok.Value
    private static class KeywordAlias {
        String keyword;
        List<String> aliases;

        KeywordAlias(String keyword, String... aliases) {
            this.keyword = keyword;
            this.aliases = Arrays.asList(aliases);
        }

        boolean matches(String message) {
            return aliases.stream().anyMatch(message::contains);
        }
    }

}
