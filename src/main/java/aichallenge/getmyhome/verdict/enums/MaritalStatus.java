package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "혼인 상태")
public enum MaritalStatus {
  @Schema(description = "미혼")
  SINGLE,
  @Schema(description = "기혼")
  MARRIED,
  @Schema(description = "결혼 예정")
  ENGAGED
}
