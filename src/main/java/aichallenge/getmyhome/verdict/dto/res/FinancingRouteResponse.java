package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "FinancingRouteResponse", description = "자금 경로(상품 단위) 판정 결과")
public record FinancingRouteResponse(
  @Schema(description = "상품 코드. "
      + "DIDIMDOL_GENERAL(디딤돌-일반), DIDIMDOL_FIRST(디딤돌-생애최초), DIDIMDOL_NEWLYWED(디딤돌-신혼부부), "
      + "YOUTH_DREAM_SINGLE(청년주택드림-미혼), YOUTH_DREAM_NEWLYWED(청년주택드림-신혼부부), BANK_MORTGAGE(시중은행 주담대)",
      example = "DIDIMDOL_GENERAL")
  String productCode,
  @Schema(description = "상품 한글명", example = "디딤돌 대출 - 일반")
  String productName,
  @Schema(description = "판정 상태")
  VerdictStatus status,
  @Schema(description = "대출 한도 하한 (만원). 은행 주담대에서만 사용, 그 외 null", example = "15000")
  Integer limitMin,
  @Schema(description = "대출 한도 상한 (만원)", example = "40000")
  Integer limitMax,
  @Schema(description = "한도 결정 요인. DTI(총부채상환비율) / LTV(담보인정비율) / DSR(총부채원리금상환비율) / null", example = "LTV")
  String bindingFactor,
  @Schema(description = "HOLD 사유 코드. status가 OK이면 null. HoldReasonCode 참조", example = "NEED_SPOUSE_INCOME")
  String reasonCode,
  @Schema(description = "근거 자료 ID 목록")
  List<String> evidenceIds
) {
}