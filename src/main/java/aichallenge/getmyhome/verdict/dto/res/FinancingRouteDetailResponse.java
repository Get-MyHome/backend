package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "대출 상품 자격 상세 조회 결과 (탈락 사유 포함)")
public record FinancingRouteDetailResponse(
  @Schema(description = "상품 코드", example = "DIDIMDOL_GENERAL")
  String productCode,
  @Schema(description = "상품 한글명", example = "디딤돌 대출 - 일반")
  String productName,
  @Schema(description = "판정 상태. OK: 자격 충족, BLOCK: 자격 미달, HOLD: 추가 정보 필요", example = "OK")
  VerdictStatus status,
  @Schema(description = "자격 충족 여부", example = "true")
  boolean eligible,
  @Schema(description = "대출 한도 하한 (만원). 시중은행 주담대에서만 사용", example = "15000")
  Integer limitMin,
  @Schema(description = "대출 한도 상한 (만원)", example = "40000")
  Integer limitMax,
  @Schema(description = "한도 결정 요인. DTI / LTV / DSR / null", example = "LTV")
  String bindingFactor,
  @Schema(description = "자격 미달 사유 (status가 BLOCK일 때)", example = "연소득이 6,000만원 상한을 초과합니다")
  String ineligibleReason,
  @Schema(description = "HOLD 사유 코드 (status가 HOLD일 때)", example = "NEED_SPOUSE_INCOME")
  String holdReasonCode,
  @Schema(description = "HOLD 안내 메시지 (status가 HOLD일 때)", example = "배우자 연소득을 입력해 주세요.")
  String holdMessage
) {

  public static FinancingRouteDetailResponse ok(String productCode, String productName,
                                                 Integer limitMin, Integer limitMax, String bindingFactor) {
    return new FinancingRouteDetailResponse(productCode, productName, VerdictStatus.OK, true,
        limitMin, limitMax, bindingFactor, null, null, null);
  }

  public static FinancingRouteDetailResponse block(String productCode, String productName, String reason) {
    return new FinancingRouteDetailResponse(productCode, productName, VerdictStatus.BLOCK, false,
        null, null, null, reason, null, null);
  }

  public static FinancingRouteDetailResponse hold(String productCode, String productName,
                                                   String holdReasonCode, String holdMessage) {
    return new FinancingRouteDetailResponse(productCode, productName, VerdictStatus.HOLD, false,
        null, null, null, null, holdReasonCode, holdMessage);
  }
}