package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판정 상태")
public enum VerdictStatus {
  @Schema(description = "충족 - 자금이 충분하거나 자격 요건을 만족")
  OK,
  @Schema(description = "부족하지만 저축으로 해소 가능")
  GAP,
  @Schema(description = "현재 조건으로는 해소 불가")
  BLOCK,
  @Schema(description = "추가 정보 입력 필요 - 판정 보류")
  HOLD
}