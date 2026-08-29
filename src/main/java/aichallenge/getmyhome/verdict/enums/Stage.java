package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "납부 구간")
public enum Stage {
  @Schema(description = "계약금")
  CONTRACT,
  @Schema(description = "중도금")
  INTERIM,
  @Schema(description = "잔금")
  BALANCE
}
