package cl.colegiosaas.info;

import java.util.Set;

public record WorkshopDraft(String name, String description, String schedule, String instructor, Integer capacity,
                            int academicYear, boolean active, Set<Long> gradeLevelIds) {

    public WorkshopDraft {
        gradeLevelIds = gradeLevelIds == null ? Set.of() : Set.copyOf(gradeLevelIds);
    }
}
