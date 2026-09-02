package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "InterimCriticalLineResponse", description = "중도금 임계선·조건부 안전마진. "
    + "중도금 구간 통과에 필요한 최소 대출비율과 공고문상 알선 범위를 비교합니다")
public record InterimCriticalLineResponse(
  @Schema(description = "중도금 통과에 필요한 최소 대출비율 (분양가 대비, 0~1)", example = "0.52")
  Double criticalLoanRatio,
  @Schema(description = "최소 필요 대출액 (만원)", example = "15600")
  Integer criticalLoanAmount,
  @Schema(description = "공고문상 사업주체 알선 비율 (분양가 대비, 0~1). 미공시면 null", example = "0.4")
  Double arrangedRatio,
  @Schema(description = "공고문상 알선 금액 (만원). 미공시면 null", example = "12000")
  Integer arrangedAmount,
  @Schema(description = "대출 알선 상태. CONFIRMED(확정), PLANNED(예정), NOT_AVAILABLE(불가), NOT_STATED(미기재)",
      example = "PLANNED")
  String arrangementStatus,
  @Schema(description = "조건부 안전마진 (%p 단위). 양수면 여유, 음수면 부족. "
      + "예: -12.0 → 알선 범위가 필요 비율보다 12%p 부족", example = "-12.0")
  Double safetyMarginPp,
  @Schema(description = "안전마진 상태. SAFE(마진 양수), WARNING(마진 음수), UNKNOWN(정보 부족)")
  String safetyStatus,
  @Schema(description = "고정 안내문", example = "공고문상 대출 알선 범위는 사업주체의 알선 계획이며, 실제 개인 대출 승인을 의미하지 않습니다. 대출 실행 여부는 금융기관의 개인심사에 따라 달라질 수 있습니다.")
  String disclaimer
) {
  public static final String DISCLAIMER_TEXT =
      "공고문상 대출 알선 범위는 사업주체의 알선 계획이며, "
      + "실제 개인 대출 승인을 의미하지 않습니다. "
      + "대출 실행 여부는 금융기관의 개인심사에 따라 달라질 수 있습니다.";
}
