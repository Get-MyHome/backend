package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "ShortfallPreparationResponse", description = "부족액 준비 시나리오 요약. "
    + "최초 부족 구간 기준으로 예상 부족액, 남은 기간, 월 필요 준비금을 계산합니다")
public record ShortfallPreparationResponse(
  @Schema(description = "전체 예상 부족액 (만원). 계산 불가 시 null", example = "3000")
  Integer totalShortfall,
  @Schema(description = "최초 부족 구간. CONTRACT/INTERIM/BALANCE", example = "INTERIM")
  String shortfallStage,
  @Schema(description = "남은 준비 기간 (개월). 기한 미확정 시 null", example = "18")
  Integer monthsRemaining,
  @Schema(description = "월 필요 준비금 (만원). 기한 미확정 시 null", example = "167")
  Integer monthlyRequired,
  @Schema(description = "계산 가능 여부. false이면 위 값 모두 null이고 holdReason에 사유 표시")
  boolean calculable,
  @Schema(description = "계산 불가 시 사유", example = "납부일 미확정")
  String holdReason
) {
}
