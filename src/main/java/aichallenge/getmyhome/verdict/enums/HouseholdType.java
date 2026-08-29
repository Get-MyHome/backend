package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세대 구성")
public enum HouseholdType {
  @Schema(description = "세대주")
  HEAD,
  @Schema(description = "단독세대주")
  SINGLE_HEAD,
  @Schema(description = "세대원")
  MEMBER
}