package cn.iocoder.yudao.module.product.service.furniture.search;

import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FurnitureProductMatcher {

    private static final BigDecimal FEN_PER_YUAN = BigDecimal.valueOf(100);

    private static final List<String> CONSTRAINT_ORDER = Collections.unmodifiableList(Arrays.asList(
            "category", "budgetMin", "budgetMax", "styles", "colors", "materials", "excludedMaterials",
            "roomTypes", "maxWidthMm", "maxDepthMm", "maxHeightMm", "seatCount", "hasChildren",
            "hasPets", "easyClean", "scratchResistant", "movable", "rentalFriendly", "preferredFeatures"));
    private static final Set<String> CONTEXT_ONLY_FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("roomSize", "roomWidthMm", "roomDepthMm")));

    public List<FurnitureCandidateMatch> match(FurnitureAssistantRequirements request,
                                               List<FurnitureProductCandidate> candidates,
                                               int limit) {
        if (request == null || candidates == null || candidates.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        List<FurnitureCandidateMatch> exact = new ArrayList<>();
        List<FurnitureCandidateMatch> partial = new ArrayList<>();
        for (FurnitureProductCandidate candidate : candidates) {
            if (!isSellable(candidate)) {
                continue;
            }
            EvaluatedCandidate evaluated = evaluate(request, candidate);
            if (evaluated.unmet.isEmpty()) {
                exact.add(evaluated.toMatch(FurnitureMatchType.EXACT));
            } else if (eligibleForPartial(request, evaluated.unmet)) {
                partial.add(evaluated.toMatch(FurnitureMatchType.PARTIAL));
            }
        }

        List<FurnitureCandidateMatch> selected = exact.isEmpty() ? partial : exact;
        selected.sort(matchComparator());
        if (selected.size() <= limit) {
            return selected;
        }
        return new ArrayList<>(selected.subList(0, limit));
    }

    private EvaluatedCandidate evaluate(FurnitureAssistantRequirements request,
                                        FurnitureProductCandidate candidate) {
        Set<String> requestedHard = new LinkedHashSet<>();
        if (request.getCategory() != null) {
            requestedHard.add("category");
        }
        if (request.getHardConstraints() != null) {
            requestedHard.addAll(request.getHardConstraints());
        }
        if (request.getNonRelaxableConstraints() != null) {
            requestedHard.addAll(request.getNonRelaxableConstraints());
        }
        if (hasValues(request.getExcludedMaterials())) {
            requestedHard.add("excludedMaterials");
        }
        requestedHard.removeAll(CONTEXT_ONLY_FIELDS);

        List<String> orderedHard = orderConstraints(requestedHard);
        List<String> matched = new ArrayList<>();
        List<String> unmet = new ArrayList<>();
        for (String name : orderedHard) {
            if (matches(name, request, candidate)) {
                matched.add(name);
            } else {
                unmet.add(name);
            }
        }

        int preferenceCoverage = preferenceCoverage(request, candidate, requestedHard);
        return new EvaluatedCandidate(candidate, matched, unmet, matched.size() + preferenceCoverage);
    }

    private boolean matches(String name, FurnitureAssistantRequirements request,
                            FurnitureProductCandidate candidate) {
        FurnitureSkuSearchDO projection = candidate.getProjection();
        switch (name) {
            case "category":
                return equalCode(request.getCategory(), projection.getCategoryCode());
            case "budgetMin":
                return minimumPrice(request.getBudgetMin(), candidate.getSku().getPrice());
            case "budgetMax":
                return maximumPrice(request.getBudgetMax(), candidate.getSku().getPrice());
            case "styles":
                return overlaps(request.getStyles(), projection.getStyleCodes());
            case "colors":
                return containsCode(request.getColors(), projection.getColorCode());
            case "materials":
                return containsAll(request.getMaterials(), projection.getMaterialCodes());
            case "excludedMaterials":
                return hasValues(projection.getMaterialCodes())
                        && !overlaps(request.getExcludedMaterials(), projection.getMaterialCodes());
            case "roomTypes":
                return overlaps(request.getRoomTypes(), projection.getRoomTypeCodes());
            case "maxWidthMm":
                return maximum(request.getMaxWidthMm(), projection.getWidthMm());
            case "maxDepthMm":
                return maximum(request.getMaxDepthMm(), projection.getDepthMm());
            case "maxHeightMm":
                return maximum(request.getMaxHeightMm(), projection.getHeightMm());
            case "seatCount":
                return equalValue(request.getSeatCount(), projection.getSeatCount());
            case "hasChildren":
                return equalValue(request.getHasChildren(), projection.getChildFriendly());
            case "hasPets":
                return equalValue(request.getHasPets(), projection.getPetFriendly());
            case "easyClean":
                return equalValue(request.getEasyClean(), projection.getEasyClean());
            case "scratchResistant":
                return equalValue(request.getScratchResistant(), projection.getScratchResistant());
            case "movable":
                return equalValue(request.getMovable(), projection.getMovable());
            case "rentalFriendly":
                return equalValue(request.getRentalFriendly(), projection.getRentalFriendly());
            case "preferredFeatures":
                return containsAll(request.getPreferredFeatures(), projection.getFeatureCodes());
            default:
                return false;
        }
    }

    private int preferenceCoverage(FurnitureAssistantRequirements request, FurnitureProductCandidate candidate,
                                   Set<String> hardConstraints) {
        int score = 0;
        for (String name : CONSTRAINT_ORDER) {
            if (!hardConstraints.contains(name) && isRequested(name, request)
                    && matches(name, request, candidate)) {
                score++;
            }
        }
        return score;
    }

    private boolean isRequested(String name, FurnitureAssistantRequirements request) {
        switch (name) {
            case "category": return request.getCategory() != null;
            case "budgetMin": return request.getBudgetMin() != null;
            case "budgetMax": return request.getBudgetMax() != null;
            case "styles": return hasValues(request.getStyles());
            case "colors": return hasValues(request.getColors());
            case "materials": return hasValues(request.getMaterials());
            case "excludedMaterials": return hasValues(request.getExcludedMaterials());
            case "roomTypes": return hasValues(request.getRoomTypes());
            case "maxWidthMm": return request.getMaxWidthMm() != null;
            case "maxDepthMm": return request.getMaxDepthMm() != null;
            case "maxHeightMm": return request.getMaxHeightMm() != null;
            case "seatCount": return request.getSeatCount() != null;
            case "hasChildren": return request.getHasChildren() != null;
            case "hasPets": return request.getHasPets() != null;
            case "easyClean": return request.getEasyClean() != null;
            case "scratchResistant": return request.getScratchResistant() != null;
            case "movable": return request.getMovable() != null;
            case "rentalFriendly": return request.getRentalFriendly() != null;
            case "preferredFeatures": return hasValues(request.getPreferredFeatures());
            default: return false;
        }
    }

    private boolean eligibleForPartial(FurnitureAssistantRequirements request, List<String> unmet) {
        Set<String> nonRelaxable = request.getNonRelaxableConstraints() == null
                ? Collections.emptySet() : request.getNonRelaxableConstraints();
        for (String name : unmet) {
            if ("category".equals(name) || "excludedMaterials".equals(name) || nonRelaxable.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSellable(FurnitureProductCandidate candidate) {
        if (candidate == null || !candidate.isErpMapped() || candidate.getProjection() == null
                || candidate.getSku() == null || candidate.getSpu() == null
                || candidate.getSellableStock() == null
                || candidate.getSellableStock().compareTo(BigDecimal.ZERO) <= 0
                || candidate.getSku().getPrice() == null || candidate.getSku().getPrice() < 0) {
            return false;
        }
        Long projectionSkuId = candidate.getProjection().getSkuId();
        Long projectionSpuId = candidate.getProjection().getSpuId();
        return projectionSkuId != null && projectionSpuId != null
                && projectionSkuId.equals(candidate.getSku().getId())
                && projectionSpuId.equals(candidate.getSku().getSpuId())
                && projectionSpuId.equals(candidate.getSpu().getId());
    }

    private Comparator<FurnitureCandidateMatch> matchComparator() {
        return Comparator.comparingInt(FurnitureCandidateMatch::getCoverageScore).reversed()
                .thenComparing((FurnitureCandidateMatch value) -> value.getCandidate().getSellableStock(),
                        Comparator.reverseOrder())
                .thenComparing(value -> value.getCandidate().getSku().getPrice())
                .thenComparing(value -> value.getCandidate().getSku().getId());
    }

    private List<String> orderConstraints(Set<String> constraints) {
        List<String> ordered = new ArrayList<>();
        for (String known : CONSTRAINT_ORDER) {
            if (constraints.contains(known)) {
                ordered.add(known);
            }
        }
        List<String> unknown = new ArrayList<>();
        for (String constraint : constraints) {
            if (constraint != null && !CONSTRAINT_ORDER.contains(constraint)) {
                unknown.add(constraint);
            }
        }
        Collections.sort(unknown);
        ordered.addAll(unknown);
        return ordered;
    }

    private boolean minimumPrice(BigDecimal requestedYuan, Integer actualFen) {
        return requestedYuan != null && actualFen != null
                && BigDecimal.valueOf(actualFen).compareTo(requestedYuan.multiply(FEN_PER_YUAN)) >= 0;
    }

    private boolean maximumPrice(BigDecimal requestedYuan, Integer actualFen) {
        return requestedYuan != null && actualFen != null
                && BigDecimal.valueOf(actualFen).compareTo(requestedYuan.multiply(FEN_PER_YUAN)) <= 0;
    }

    private boolean maximum(Integer requested, Integer actual) {
        return requested != null && actual != null && actual <= requested;
    }

    private boolean containsCode(List<String> requested, String actual) {
        if (!hasValues(requested) || actual == null) {
            return false;
        }
        for (String value : requested) {
            if (equalCode(value, actual)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(List<String> requested, List<String> actual) {
        if (!hasValues(requested) || !hasValues(actual)) {
            return false;
        }
        for (String expected : requested) {
            if (containsCode(actual, expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAll(List<String> requested, List<String> actual) {
        if (!hasValues(requested) || !hasValues(actual)) {
            return false;
        }
        for (String expected : requested) {
            if (!containsCode(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalCode(String requested, String actual) {
        return requested != null && actual != null && requested.equalsIgnoreCase(actual);
    }

    private boolean equalValue(Object requested, Object actual) {
        return requested != null && requested.equals(actual);
    }

    private boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static final class EvaluatedCandidate {
        private final FurnitureProductCandidate candidate;
        private final List<String> matched;
        private final List<String> unmet;
        private final int coverage;

        private EvaluatedCandidate(FurnitureProductCandidate candidate, List<String> matched,
                                   List<String> unmet, int coverage) {
            this.candidate = candidate;
            this.matched = matched;
            this.unmet = unmet;
            this.coverage = coverage;
        }

        private FurnitureCandidateMatch toMatch(FurnitureMatchType type) {
            return new FurnitureCandidateMatch(candidate, type, matched, unmet, coverage);
        }
    }
}
