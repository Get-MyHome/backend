package aichallenge.getmyhome.verdict.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "청약 판정 요청. conditionToken 또는 user 중 하나는 필수")
public record VerdictRequest(
  @Schema(description = "대출 자격 조회 시 발급받은 조건 토큰 (30분 유효). user와 동시에 전달하면 토큰이 우선", example = "CT-a1b2c3d4")
  String conditionToken,

  @Schema(description = "사용자 조건 직접 전달 (토큰 없이 독립 호출 시 사용)")
  @Valid
  UserConditionRequest user,

  @Schema(description = "단지 ID. null이면 추정 모드(단지 미선택)", example = "A2024-0001")
  String complexId,

  @Schema(description = "평형 식별자. 같은 단지라도 평형별로 분양가가 다름", example = "84A")
  String unitTypeId,

  @Schema(description = "규칙 버전. null이면 최신 버전 적용", example = "v2026-08")
  String ruleVersion
) {
}