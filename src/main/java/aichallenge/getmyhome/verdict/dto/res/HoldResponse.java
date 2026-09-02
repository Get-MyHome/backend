package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(title = "HoldResponse", description = "HOLD 사유 항목. 백엔드 판정 HOLD와 AI 분석 HOLD가 함께 포함되며, kind 필드로 구분합니다")
public record HoldResponse(
  @Schema(description = "HOLD 사유 코드. "
      + "백엔드: NEED_SPOUSE_INCOME, NEED_MONTHLY_SAVING, COMPLEX_NOT_ANALYZED 등 / "
      + "AI: LOAN_ARRANGEMENT_ONLY, BANK_NOT_DISCLOSED, SELF_FUNDING_SCHEDULE_UNKNOWN 등",
      example = "NEED_SPOUSE_INCOME")
  String reasonCode,
  @Schema(description = "사용자 안내 메시지 (화면 표시용)", example = "배우자 연소득을 입력해 주세요.")
  String message,
  @Schema(description = "다음 행동 지침", example = "배우자 연소득을 입력해 주세요.")
  String nextAction,
  @Schema(description = "HOLD 종류. "
      + "DOCUMENT_UNCERTAINTY: 공고문에서 확인 불가 (AI 분석), "
      + "PERSONAL_REVIEW: 금융기관 개인심사 필요 (AI 분석), "
      + "null: 백엔드 판정 HOLD (추가 입력 필요)",
      example = "DOCUMENT_UNCERTAINTY")
  String kind,
  @Schema(description = "차단 여부. "
      + "true: 해당 구간 계산 보류 (blocking HOLD), "
      + "false: 참고 안내만 (non-blocking, 계산은 진행), "
      + "null: 백엔드 판정 HOLD (차단으로 간주)",
      example = "true")
  Boolean blocking,
  @Schema(description = "영향을 주는 구간. CONTRACT/INTERIM/BALANCE. "
      + "특정 구간에 한정되지 않으면 null",
      example = "INTERIM")
  String relatedStage
) {
}
