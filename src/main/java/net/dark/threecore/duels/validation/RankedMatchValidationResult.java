package net.dark.threecore.duels.validation;

import java.util.List;

public record RankedMatchValidationResult(boolean ranked, boolean valid, List<String> reasons) {
    public static RankedMatchValidationResult unranked() {
        return new RankedMatchValidationResult(false, true, List.of());
    }
}
