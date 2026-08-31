package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "RouteBalanceComparison", description = "대출 상품별 잔금 판정 비교 결과")
public record RouteBalanceComparison(
  @Schema(description = "상품 코드. "
      + "DIDIMDOL_GENERAL(디딤돌-일반), DIDIMDOL_FIRST(디딤돌-생애최초), DIDIMDOL_NEWLYWED(디딤돌-신혼부부), "
      + "YOUTH_DREAM_SINGLE(청년주택드림-미혼), YOUTH_DREAM_NEWLYWED(청년주택드림-신혼부부), BANK_MORTGAGE(시중은행 주담대)",
      example = "DIDIMDOL_FIRST")
  String productCode,
  @Schema(description = "상품 한글명", example = "디딤돌 대출 - 생애최초")
  String productName,
  @Schema(description = "판정 상태")
  VerdictStatus status,
  @Schema(description = "해당 상품의 대출 한도 (만원)", example = "24000")
  Integer loanLimit,
  @Schema(description = "잔금 필요 금액 (만원)", example = "37800")
  Integer balanceRequired,
  @Schema(description = "가용 금액 (잔여 현금 + 대출 한도, 만원)", example = "31800")
  Integer available,
  @Schema(description = "부족 금액 (만원). 충분하면 null", example = "6000")
  Integer gap,
  @Schema(description = "잔금일까지 남은 개월 수", example = "33")
  Integer monthsAvailable,
  @Schema(description = "저축으로 부족분 해소에 필요한 개월 수", example = "53")
  Integer monthsNeeded,
  @Schema(description = "시나리오 메시지", example = "월 95만 원 저축 시 53개월 필요 (잔금일까지 33개월)")
  String scenario
) {
}
