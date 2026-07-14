package cn.iocoder.yudao.module.product.service.furniture.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FurnitureCandidateMatch {

    private final FurnitureProductCandidate candidate;
    private final FurnitureMatchType matchType;
    private final List<String> matchedConstraints;
    private final List<String> unmetConstraints;
    private final int coverageScore;

    public FurnitureCandidateMatch(FurnitureProductCandidate candidate, FurnitureMatchType matchType,
                                   List<String> matchedConstraints, List<String> unmetConstraints,
                                   int coverageScore) {
        this.candidate = candidate;
        this.matchType = matchType;
        this.matchedConstraints = immutableCopy(matchedConstraints);
        this.unmetConstraints = immutableCopy(unmetConstraints);
        this.coverageScore = coverageScore;
    }

    public FurnitureProductCandidate getCandidate() {
        return candidate;
    }

    public FurnitureMatchType getMatchType() {
        return matchType;
    }

    public List<String> getMatchedConstraints() {
        return matchedConstraints;
    }

    public List<String> getUnmetConstraints() {
        return unmetConstraints;
    }

    public int getCoverageScore() {
        return coverageScore;
    }

    private static List<String> immutableCopy(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
