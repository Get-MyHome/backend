package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "FinancingRouteResponse", description = "자금 경로(상품 단위) 판정 결과")
public record FinancingRouteResponse(
  @Schema(description = "상품 코드. 가능한 값: DIDIMDOL_GENERAL, DIDIMDOL_FIRST, DIDIMDOL_NEWLYWED, YOUTH_DREAM_SINGLE, YOUTH_DREAM_NEWLYWED, BANK_MORTGAGE", example = "DIDIMDOL_GENERAL")
  String productCode,
  @Schema(description = "상품 한글명", example = "디딤돌 대출 - 일반")
  String productName,
  @Schema(description = "판정 상태", example = "OK")
  VerdictStatus status,
  @Schema(description = "대출 한도 하한 (만원). 은행 주담대에서만 사용, 그 외 null", example = "15000")
  Integer limitMin,
  @Schema(description = "대출 한도 상한 (만원)", example = "40000")
  Integer limitMax,
  @Schema(description = "한도 결정 요인. 가능한 값: DTI, LTV, DSR, null", example = "LTV")
  String bindingFactor,
  @Schema(description = "HOLD 사유 코드. status가 OK이면 null", example = "NEED_SPOUSE_INCOME")
  String reasonCode,
  @Schema(description = "근거 자료 ID 목록")
  List<String> evidenceIds
) {
}