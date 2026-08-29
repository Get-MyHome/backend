package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자금 경로 상품 코드")
public enum ProductCode {
  @Schema(description = "디딤돌 대출 - 일반")
  DIDIMDOL_GENERAL,
  @Schema(description = "디딤돌 대출 - 생애최초")
  DIDIMDOL_FIRST,
  @Schema(description = "디딤돌 대출 - 신혼부부")
  DIDIMDOL_NEWLYWED,
  @Schema(description = "청년주택드림 대출 - 미혼")
  YOUTH_DREAM_SINGLE,
  @Schema(description = "청년주택드림 대출 - 신혼부부")
  YOUTH_DREAM_NEWLYWED,
  @Schema(description = "시중은행 주택담보대출")
  BANK_MORTGAGE
}