package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "InterimFinancingDetailResponse", description = "중도금 금융조달 확정도. "
    + "확인된 정보와 미확정 정보를 구분하여 보여줍니다")
public record InterimFinancingDetailResponse(
  @Schema(description = "확인된 중도금 정보")
  ConfirmedInfo confirmed,
  @Schema(description = "미확정 중도금 정보")
  UnconfirmedInfo unconfirmed,
  @Schema(description = "은행·시행사에 확인할 질문 목록")
  List<String> questionsForBankOrDeveloper
) {

  @Schema(title = "ConfirmedInfo", description = "공고문에서 확인된 중도금 정보")
  public record ConfirmedInfo(
    @Schema(description = "중도금 총비율 (분양가 대비, 0~1)", example = "0.6")
    Double interimTotalRatio,
    @Schema(description = "중도금 납부 회차 수", example = "6")
    Integer interimInstallmentCount,
    @Schema(description = "대출 알선 상태. CONFIRMED/PLANNED/NOT_AVAILABLE/NOT_STATED", example = "PLANNED")
    String arrangementStatus,
    @Schema(description = "알선 비율 (분양가 대비, 0~1)", example = "0.4")
    Double arrangedRatio,
    @Schema(description = "자납 필요 여부. true: 알선 외 자납 구간 있음, false: 전액 대출 알선, null: 미확인")
    Boolean selfFundingRequired,
    @Schema(description = "알선 범위 밖 별도 조달 비율 (분양가 대비, 0~1). 자납 불필요 시 null", example = "0.2")
    Double selfFundingRatio,
    @Schema(description = "이자 방식. DEFERRED_INTEREST(이자후불제) 등", example = "DEFERRED_INTEREST")
    String interestType
  ) {}

  @Schema(title = "UnconfirmedInfo", description = "공고문에서 미확정되거나 추가 확인이 필요한 정보")
  public record UnconfirmedInfo(
    @Schema(description = "취급 은행 목록. 미확정이면 빈 배열")
    List<String> bankNames,
    @Schema(description = "보증사. 미확정이면 null")
    String guaranteeProvider,
    @Schema(description = "대출 신청 일정 안내. 미확정이면 null")
    String applicationSchedule,
    @Schema(description = "기간 연장 특약 공시 여부. 미확정이면 null")
    Boolean extensionContingencyDisclosed,
    @Schema(description = "정산 요건. REPAY_OR_CONVERT_TO_MORTGAGE 등", example = "REPAY_OR_CONVERT_TO_MORTGAGE")
    String settlementRequirement
  ) {}
}
