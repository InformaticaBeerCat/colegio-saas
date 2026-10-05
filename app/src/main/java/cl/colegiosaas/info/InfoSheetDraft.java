package cl.colegiosaas.info;

import java.time.LocalDate;

/** Lo editable de una lista de útiles, uniforme o minuta. */
public record InfoSheetDraft(InfoSheetKind kind, String title, Long gradeLevelId, int academicYear, String content,
                             LocalDate validFrom, LocalDate validUntil) {
}
