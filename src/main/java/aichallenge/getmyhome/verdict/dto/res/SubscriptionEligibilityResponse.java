package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "SubscriptionEligibilityResponse", description = "청약 자격 판정 결과")
public record SubscriptionEligibilityResponse(
  @Schema(description = "청약 유형. SUB_NEWLYWED(신혼부부 특별공급), SUB_FIRST(생애최초 특별공급), SUB_GENERAL(일반공급)",
      example = "SUB_GENERAL")
  String type,
  @Schema(description = "판정 상태")
  VerdictStatus status,
  @Schema(description = "HOLD 사유 코드. status가 OK이면 null. HoldReasonCode 참조", example = "NEED_FIRST_TIME_BUYER_INFO")
  String reasonCode,
  @Schema(description = "근거 자료 ID 목록")
  List<String> evidenceIds
) {
}