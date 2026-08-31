package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "SubscriptionEligibilityResponse", description = "청약 자격 판정 결과")
public record SubscriptionEligibilityResponse(
  @Schema(description = "청약 유형. 가능한 값: SUB_NEWLYWED, SUB_FIRST, SUB_GENERAL", example = "SUB_GENERAL")
  String type,
  @Schema(description = "판정 상태 (OK 또는 HOLD)", example = "OK")
  VerdictStatus status,
  @Schema(description = "HOLD 사유 코드. status가 OK이면 null", example = "NEED_FIRST_TIME_BUYER_INFO")
  String reasonCode,
  @Schema(description = "근거 자료 ID 목록")
  List<String> evidenceIds
) {
}