package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "EvidenceResponse", description = "근거 자료 항목")
public record EvidenceResponse(
  @Schema(description = "근거 자료 고유 ID", example = "EV-RULE-001")
  String evidenceId,
  @Schema(description = "출처 유형. 가능한 값: 규정, 계산", example = "규정")
  String sourceType,
  @Schema(description = "출처 설명", example = "주택도시기금 수치 기준표")
  String ref,
  @Schema(description = "기준 시점 (YYYY-MM-DD)", example = "2026-08-20")
  String asOf,
  @Schema(description = "관련 URL. 없으면 null")
  String url
) {
}