package net.dark.threecore.dungeons.engine;

import java.util.ArrayList;
import java.util.List;

public record DungeonValidationResult(boolean valid, List<String> failures) {
    public static DungeonValidationResult ok() {
        return new DungeonValidationResult(true, List.of());
    }

    public static DungeonValidationResult fail(List<String> failures) {
        return new DungeonValidationResult(false, List.copyOf(failures));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> failures = new ArrayList<>();

        public void fail(String failure) {
            if (failure != null && !failure.isBlank()) failures.add(failure);
        }

        public DungeonValidationResult build() {
            return failures.isEmpty() ? ok() : DungeonValidationResult.fail(failures);
        }
    }
}
