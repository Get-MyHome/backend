package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "HOLD 사유 항목")
public record HoldResponse(
  @Schema(description = "HOLD 사유 코드", example = "NEED_SPOUSE_INCOME")
  String reasonCode,
  @Schema(description = "사용자 안내 메시지 (다음 행동 지침)", example = "배우자 연소득을 입력해 주세요")
  String nextAction
) {
}