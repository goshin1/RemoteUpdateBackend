package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

/** 변경 이력. before/after 는 JSON 객체로 내려가므로 프런트에서 필드별로 비교할 수 있다 */
public record UpdateHistoryResponse(
        Long id,
        HistoryAction action,
        Long changedById,
        String changedByName,
        JsonNode before,
        JsonNode after,
        LocalDateTime changedAt
) {
}
