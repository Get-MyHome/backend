package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "자금 경로 상품 코드")
public enum ProductCode {
  @Schema(description = "디딤돌 대출 - 일반")
  DIDIMDOL_GENERAL("디딤돌 대출 - 일반"),
  @Schema(description = "디딤돌 대출 - 생애최초")
  DIDIMDOL_FIRST("디딤돌 대출 - 생애최초"),
  @Schema(description = "디딤돌 대출 - 신혼부부")
  DIDIMDOL_NEWLYWED("디딤돌 대출 - 신혼부부"),
  @Schema(description = "청년주택드림 대출 - 미혼")
  YOUTH_DREAM_SINGLE("청년주택드림 대출 - 미혼"),
  @Schema(description = "청년주택드림 대출 - 신혼부부")
  YOUTH_DREAM_NEWLYWED("청년주택드림 대출 - 신혼부부"),
  @Schema(description = "시중은행 주택담보대출")
  BANK_MORTGAGE("시중은행 주택담보대출");

  private final String displayName;
}