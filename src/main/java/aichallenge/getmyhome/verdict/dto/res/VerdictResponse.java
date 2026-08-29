package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "청약 판정 결과 응답")
public record VerdictResponse(
  @Schema(description = "판정 고유 ID", example = "V-a1b2c3d4")
  String verdictId,
  @Schema(description = "판정 메타 정보")
  VerdictMeta meta,
  @Schema(description = "자금 경로별 판정 결과 목록")
  List<FinancingRouteResponse> financingRoutes,
  @Schema(description = "청약 자격 판정 결과 목록")
  List<SubscriptionEligibilityResponse> subscriptionEligibilities,
  @Schema(description = "구간별(계약금/중도금/잔금) 판정 결과 목록")
  List<StageVerdictResponse> verdicts,
  @Schema(description = "HOLD 사유 목록. 추가 정보 입력이 필요한 항목")
  List<HoldResponse> holds,
  @Schema(description = "판정에 사용된 근거 자료 목록")
  List<EvidenceResponse> evidence
) {

  @Schema(description = "판정 메타 정보")
  public record VerdictMeta(
    @Schema(description = "적용된 규칙 버전", example = "v2026-08")
    String ruleVersion,
    @Schema(description = "가정 세트 ID", example = "default")
    String assumptionSetId,
    @Schema(description = "판정 수행 일시 (YYYY-MM-DD)", example = "2026-08-29")
    String calculatedAt,
    @Schema(description = "정밀도. step1: 필수 항목만 입력, step2: 상세 항목 입력", example = "step1")
    String precision
  ) {
  }
}