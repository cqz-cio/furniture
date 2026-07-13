package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FurnitureRequirementNormalizer {

    private static final String NUMBER = "([0-9]+(?:\\.[0-9]+)?)";
    private static final String UNIT = "(mm|millimeters?|cm|centimeters?|毫米|厘米|米|m)";

    private static final Pattern MAX_BUDGET_CN = Pattern.compile(
            NUMBER + "\\s*(?:元|块)?\\s*(?:以内|以下|之内|不超过|封顶)");
    private static final Pattern MAX_BUDGET_EN = Pattern.compile(
            "(?:under|below|up to|no more than|max(?:imum)?(?: budget)?(?: of)?)\\s*(?:cny|rmb|\\u00a5)?\\s*"
                    + NUMBER + "(?!(?:[0-9.]|\\s*(?:mm|millimeters?|cm|centimeters?|m\\b)))");
    private static final Pattern MAX_WIDTH_CN = Pattern.compile(
            "(?:宽度|总宽|宽)[^0-9]{0,16}" + NUMBER + "\\s*" + UNIT);
    private static final Pattern MAX_DEPTH_CN = Pattern.compile(
            "(?:深度|总深|深)[^0-9]{0,16}" + NUMBER + "\\s*" + UNIT);
    private static final Pattern MAX_HEIGHT_CN = Pattern.compile(
            "(?:高度|总高|高)[^0-9]{0,16}" + NUMBER + "\\s*" + UNIT);
    private static final Pattern MAX_WIDTH_EN = Pattern.compile(
            "(?:(?:under|below|up to|no more than|max(?:imum)?)\\s*)?" + NUMBER + "\\s*" + UNIT + "\\s*(?:wide|width)");
    private static final Pattern MAX_DEPTH_EN = Pattern.compile(
            "(?:(?:under|below|up to|no more than|max(?:imum)?)\\s*)?" + NUMBER + "\\s*" + UNIT + "\\s*(?:deep|depth)");
    private static final Pattern MAX_HEIGHT_EN = Pattern.compile(
            "(?:(?:under|below|up to|no more than|max(?:imum)?)\\s*)?" + NUMBER + "\\s*" + UNIT + "\\s*(?:high|height)");
    private static final Pattern ROOM_DIMENSIONS = Pattern.compile(
            NUMBER + "\\s*" + UNIT + "\\s*(?:乘|x|×|by)\\s*" + NUMBER + "\\s*" + UNIT);
    private static final Pattern SEAT_COUNT_CN = Pattern.compile("([0-9一二两三四五六七八九十]+)(?:人位|人座|人|口之家)");
    private static final Pattern SEAT_COUNT_EN = Pattern.compile("(?:for\\s+)?([0-9]+)[ -]?(?:seat(?:er)?|people|person)");

    private static final Map<String, String> CATEGORY_ALIASES = aliasMap(new String[][]{
            {"卧室储物", "bedroom-storage"}, {"bedroom storage", "bedroom-storage"},
            {"单椅", "single-chair"}, {"armchair", "single-chair"}, {"single chair", "single-chair"},
            {"沙发", "sofa"}, {"sofa", "sofa"},
            {"餐桌", "dining-table"}, {"dining table", "dining-table"},
            {"茶几", "coffee-table"}, {"coffee table", "coffee-table"},
            {"书桌", "desk"}, {"desk", "desk"},
            {"衣柜", "wardrobe"}, {"wardrobe", "wardrobe"},
            {"床", "bed"}, {"bed", "bed"},
            {"边几", "side-table"}, {"side table", "side-table"},
            {"地毯", "rug"}, {"rug", "rug"},
            {"灯", "lighting"}, {"lighting", "lighting"},
            {"媒体柜", "media-storage"}, {"media storage", "media-storage"}
    });
    private static final Map<String, String> MATERIAL_ALIASES = aliasMap(new String[][]{
            {"布艺", "fabric"}, {"布料", "fabric"}, {"fabric", "fabric"}, {"linen", "fabric"},
            {"实木", "solid-wood"}, {"solid wood", "solid-wood"},
            {"真皮", "leather"}, {"leather", "leather"},
            {"大理石纹", "marble-look"}, {"岩板", "marble-look"}, {"marble look", "marble-look"},
            {"金属", "metal"}, {"metal", "metal"}, {"玻璃", "glass"}, {"glass", "glass"},
            {"羊毛", "wool"}, {"wool", "wool"}, {"人造板", "engineered-wood"}
    });
    private static final Map<String, String> COLOR_ALIASES = aliasMap(new String[][]{
            {"米白", "cream"}, {"奶油色", "cream"}, {"cream", "cream"}, {"ivory", "cream"},
            {"浅灰", "light-gray"}, {"light gray", "light-gray"}, {"light grey", "light-gray"},
            {"灰色", "gray"}, {"gray", "gray"}, {"grey", "gray"},
            {"深棕", "deep-brown"}, {"deep brown", "deep-brown"},
            {"深色", "dark"}, {"dark", "dark"}, {"黑色", "black"}, {"black", "black"},
            {"原木色", "natural"}, {"白色", "white"}, {"white", "white"}
    });
    private static final Map<String, String> STYLE_ALIASES = aliasMap(new String[][]{
            {"奶油风", "cream-style"}, {"cream style", "cream-style"},
            {"现代简约", "modern"}, {"现代", "modern"}, {"modern", "modern"},
            {"原木风", "natural"}, {"natural", "natural"},
            {"轻奢", "light-luxury"}, {"light luxury", "light-luxury"}
    });
    private static final Map<String, String> ROOM_ALIASES = aliasMap(new String[][]{
            {"小客厅", "living-room"}, {"客厅", "living-room"}, {"living room", "living-room"},
            {"餐厅", "dining-room"}, {"dining room", "dining-room"},
            {"卧室", "bedroom"}, {"bedroom", "bedroom"},
            {"儿童房", "children-room"}, {"children's room", "children-room"}, {"child room", "children-room"},
            {"书房", "home-office"}, {"home office", "home-office"},
            {"租房", "rental-apartment"}, {"出租屋", "rental-apartment"}, {"rental apartment", "rental-apartment"}
    });
    private static final Map<String, String> FEATURE_ALIASES = aliasMap(new String[][]{
            {"圆角", "rounded-edges"}, {"rounded edge", "rounded-edges"}, {"rounded edges", "rounded-edges"},
            {"浅进深", "shallow-depth"}, {"shallow depth", "shallow-depth"},
            {"紧凑", "compact"}, {"compact", "compact"},
            {"模块化", "modular"}, {"modular", "modular"},
            {"储物", "storage"}, {"storage", "storage"}
    });

    public FurnitureRequirementPatch normalize(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        FurnitureRequirementPatch patch = new FurnitureRequirementPatch();

        extractSingle(text, CATEGORY_ALIASES, patch, "category");
        extractBudget(text, patch);
        extractDimensions(text, patch);
        extractSeatCount(text, patch);
        extractLists(text, patch);
        extractHousehold(text, patch);
        extractCapabilities(text, patch);
        classifyConstraints(text, patch);
        return patch;
    }

    private void extractSingle(String text, Map<String, String> aliases, FurnitureRequirementPatch patch, String field) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (containsAlias(text, entry.getKey())) {
                patch.setCategory(entry.getValue());
                patch.mention(field);
                return;
            }
        }
    }

    private void extractBudget(String text, FurnitureRequirementPatch patch) {
        if (containsAny(text, "预算不限", "不限预算", "no budget limit")) {
            patch.mention("budgetMax");
            return;
        }
        BigDecimal value = firstDecimal(MAX_BUDGET_CN, text);
        if (value == null) value = firstDecimal(MAX_BUDGET_EN, text);
        if (value != null) {
            patch.setBudgetMax(value);
            hard(patch, "budgetMax");
        }
    }

    private void extractDimensions(String text, FurnitureRequirementPatch patch) {
        Matcher room = ROOM_DIMENSIONS.matcher(text);
        if (room.find() && containsAny(text, "空间", "房间", "room", "space")) {
            patch.setRoomWidthMm(toMillimeters(room.group(1), room.group(2)));
            patch.setRoomDepthMm(toMillimeters(room.group(3), room.group(4)));
            patch.mention("roomWidthMm");
            patch.mention("roomDepthMm");
        }
        extractMaximum(text, MAX_WIDTH_CN, MAX_WIDTH_EN, "maxWidthMm", patch);
        extractMaximum(text, MAX_DEPTH_CN, MAX_DEPTH_EN, "maxDepthMm", patch);
        extractMaximum(text, MAX_HEIGHT_CN, MAX_HEIGHT_EN, "maxHeightMm", patch);
    }

    private void extractMaximum(String text, Pattern chinese, Pattern english, String field,
                                FurnitureRequirementPatch patch) {
        Matcher matcher = chinese.matcher(text);
        if (!matcher.find()) {
            matcher = english.matcher(text);
            if (!matcher.find()) return;
        }
        Integer value = toMillimeters(matcher.group(1), matcher.group(2));
        if ("maxWidthMm".equals(field)) patch.setMaxWidthMm(value);
        if ("maxDepthMm".equals(field)) patch.setMaxDepthMm(value);
        if ("maxHeightMm".equals(field)) patch.setMaxHeightMm(value);
        hard(patch, field);
    }

    private void extractSeatCount(String text, FurnitureRequirementPatch patch) {
        Matcher matcher = SEAT_COUNT_CN.matcher(text);
        Integer seats = matcher.find() ? chineseNumber(matcher.group(1)) : null;
        if (seats == null) {
            matcher = SEAT_COUNT_EN.matcher(text);
            if (matcher.find()) seats = Integer.valueOf(matcher.group(1));
        }
        if (seats != null) {
            patch.setSeatCount(seats);
            hard(patch, "seatCount");
        }
    }

    private void extractLists(String text, FurnitureRequirementPatch patch) {
        boolean mandatory = containsAny(text, "必须", "只要", "只能", "一定要", "must", "only");
        if (containsAny(text, "颜色不限", "不限颜色", "any color")) {
            patch.mention("colors");
        } else {
            extractAliases(text, COLOR_ALIASES, patch.getColors(), null);
            if (!patch.getColors().isEmpty()) {
                patch.mention("colors");
                if (mandatory) nonRelaxable(patch, "colors");
            }
        }
        if (containsAny(text, "风格不限", "不限风格", "any style")) {
            patch.mention("styles");
        } else {
            extractAliases(text, STYLE_ALIASES, patch.getStyles(), null);
            if (!patch.getStyles().isEmpty()) {
                patch.mention("styles");
                if (mandatory) nonRelaxable(patch, "styles");
            }
        }

        extractExcludedMaterialRetractions(text, patch);
        extractAliases(text, MATERIAL_ALIASES, patch.getExcludedMaterials(), Boolean.TRUE);
        if (!patch.getExcludedMaterials().isEmpty()) {
            patch.mention("excludedMaterials");
            hard(patch, "excludedMaterials");
            nonRelaxable(patch, "excludedMaterials");
        }
        if (containsAny(text, "材质不限", "材料不限", "any material")) {
            patch.mention("materials");
        } else {
            extractAliases(text, MATERIAL_ALIASES, patch.getMaterials(), Boolean.FALSE);
            patch.getMaterials().removeAll(patch.getRemovedExcludedMaterials());
            if (!patch.getMaterials().isEmpty()) {
                hard(patch, "materials");
                if (mandatory) nonRelaxable(patch, "materials");
            }
        }

        extractAliases(text, ROOM_ALIASES, patch.getRoomTypes(), null);
        if (!patch.getRoomTypes().isEmpty()) patch.mention("roomTypes");
        extractAliases(text, FEATURE_ALIASES, patch.getPreferredFeatures(), null);
        if (!patch.getPreferredFeatures().isEmpty()) hard(patch, "preferredFeatures");
    }

    private void extractExcludedMaterialRetractions(String text, FurnitureRequirementPatch patch) {
        for (Map.Entry<String, String> entry : MATERIAL_ALIASES.entrySet()) {
            int index = text.indexOf(entry.getKey());
            if (index < 0) continue;
            int end = index + entry.getKey().length();
            String prefix = text.substring(Math.max(0, index - 12), index);
            String suffix = text.substring(end, Math.min(text.length(), end + 16));
            boolean retracted = suffix.matches("^\\s*(?:is\\s+)?(?:okay|ok|acceptable|allowed).*")
                    || suffix.matches("^\\s*(?:可以|能接受|不用排除).*")
                    || prefix.matches(".*(?:allow|accept|可以接受|不再排除)\\s*$");
            if (retracted && !patch.getRemovedExcludedMaterials().contains(entry.getValue())) {
                patch.getRemovedExcludedMaterials().add(entry.getValue());
                patch.mention("excludedMaterials");
            }
        }
    }

    private void extractAliases(String text, Map<String, String> aliases, List<String> target, Boolean excluded) {
        boolean[] occupied = new boolean[text.length()];
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            int fromIndex = 0;
            while (fromIndex < text.length()) {
                int index = text.indexOf(entry.getKey(), fromIndex);
                if (index < 0) break;
                int end = index + entry.getKey().length();
                fromIndex = end;
                if (overlaps(occupied, index, end)) continue;
                boolean negated = isNegated(text, index);
                if (excluded != null && excluded.booleanValue() != negated) continue;
                for (int i = index; i < end; i++) occupied[i] = true;
                if (!target.contains(entry.getValue())) target.add(entry.getValue());
            }
        }
    }

    private boolean overlaps(boolean[] occupied, int start, int end) {
        for (int i = start; i < end; i++) if (occupied[i]) return true;
        return false;
    }

    private boolean isNegated(String text, int aliasIndex) {
        String prefix = text.substring(Math.max(0, aliasIndex - 10), aliasIndex);
        return prefix.matches(".*(?:非|不要|不选|排除|避免|without|no)\\s*$");
    }

    private void extractHousehold(String text, FurnitureRequirementPatch patch) {
        if (containsAny(text, "有小孩", "有孩子", "儿童", "小朋友", "child", "kid")) {
            patch.setHasChildren(Boolean.TRUE);
            patch.mention("hasChildren");
        }
        if (containsAny(text, "有猫", "有狗", "宠物", "只猫", "只狗", "pet")) {
            patch.setHasPets(Boolean.TRUE);
            patch.mention("hasPets");
        }
    }

    private void extractCapabilities(String text, FurnitureRequirementPatch patch) {
        capability(text, patch, "easyClean", new String[]{"容易清洁", "易清洁", "好打理", "easy clean", "easy-clean"});
        capability(text, patch, "scratchResistant", new String[]{"防刮", "耐刮", "scratch resistant", "scratch-resistant"});
        capability(text, patch, "movable", new String[]{"可移动", "易移动", "movable", "easy to move"});
        capability(text, patch, "rentalFriendly", new String[]{"适合租房", "租房友好", "rental apartment", "rental friendly", "rental-friendly"});
    }

    private void classifyConstraints(String text, FurnitureRequirementPatch patch) {
        if (patch.mentions("category")) patch.getHardConstraints().add("category");
        if (!containsMandatoryLanguage(text)) return;
        for (String field : patch.getMentionedFields()) {
            patch.getHardConstraints().add(field);
            patch.getNonRelaxableConstraints().add(field);
        }
    }

    private boolean containsMandatoryLanguage(String text) {
        return containsAny(text, "必须", "只要", "只能", "一定要", "只推荐")
                || Pattern.compile("\\b(?:must|only)\\b").matcher(text).find();
    }

    private void capability(String text, FurnitureRequirementPatch patch, String field, String[] aliases) {
        String matched = null;
        for (String alias : aliases) if (text.contains(alias)) { matched = alias; break; }
        if (matched == null) return;
        boolean clear = containsAny(text, "不需要" + matched, "不要求" + matched, "不用" + matched,
                "don't need " + matched, "do not need " + matched);
        Boolean value = clear ? null : Boolean.TRUE;
        if ("easyClean".equals(field)) patch.setEasyClean(value);
        if ("scratchResistant".equals(field)) patch.setScratchResistant(value);
        if ("movable".equals(field)) patch.setMovable(value);
        if ("rentalFriendly".equals(field)) patch.setRentalFriendly(value);
        patch.mention(field);
        if (!clear) hard(patch, field);
    }

    private void hard(FurnitureRequirementPatch patch, String field) {
        patch.mention(field);
        patch.getHardConstraints().add(field);
    }

    private void nonRelaxable(FurnitureRequirementPatch patch, String field) {
        patch.getNonRelaxableConstraints().add(field);
    }

    private BigDecimal firstDecimal(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private Integer toMillimeters(String amount, String unit) {
        BigDecimal multiplier;
        if (unit.startsWith("mm") || unit.startsWith("millimeter") || "毫米".equals(unit)) multiplier = BigDecimal.ONE;
        else if (unit.startsWith("cm") || unit.startsWith("centimeter") || "厘米".equals(unit)) multiplier = BigDecimal.TEN;
        else multiplier = BigDecimal.valueOf(1000);
        return new BigDecimal(amount).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Integer chineseNumber(String value) {
        if (value.matches("[0-9]+")) return Integer.valueOf(value);
        if ("一".equals(value)) return 1;
        if ("二".equals(value) || "两".equals(value)) return 2;
        if ("三".equals(value)) return 3;
        if ("四".equals(value)) return 4;
        if ("五".equals(value)) return 5;
        if ("六".equals(value)) return 6;
        if ("七".equals(value)) return 7;
        if ("八".equals(value)) return 8;
        if ("九".equals(value)) return 9;
        if ("十".equals(value)) return 10;
        return null;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private boolean containsAlias(String text, String alias) {
        if (!alias.matches(".*[a-z0-9].*")) return text.contains(alias);
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])")
                .matcher(text).find();
    }

    private static Map<String, String> aliasMap(String[][] entries) {
        Map<String, String> value = new LinkedHashMap<>();
        for (String[] entry : entries) value.put(entry[0], entry[1]);
        return Collections.unmodifiableMap(value);
    }
}
