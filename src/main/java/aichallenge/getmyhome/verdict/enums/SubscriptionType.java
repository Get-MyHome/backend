package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "청약 공급 유형")
public enum SubscriptionType {
  @Schema(description = "신혼부부 특별공급")
  SUB_NEWLYWED,
  @Schema(description = "생애최초 특별공급")
  SUB_FIRST,
  @Schema(description = "일반공급")
  SUB_GENERAL
}