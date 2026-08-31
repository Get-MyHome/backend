package aichallenge.getmyhome.verdict.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "청약 판정 요청")
public record VerdictRequest(
  @Schema(description = "사용자 조건")
  @Valid
  @NotNull(message = "사용자 조건을 입력해 주세요.")
  UserConditionRequest user,

  @Schema(description = "단지 ID. null이면 추정 모드(단지 미선택)", example = "A2024-0001")
  String complexId,

  @Schema(description = "평형 식별자. 같은 단지라도 평형별로 분양가가 다름", example = "84A")
  String unitTypeId,

  @Schema(description = "규칙 버전. null이면 최신 버전 적용", example = "v2026-08")
  String ruleVersion
) {
}