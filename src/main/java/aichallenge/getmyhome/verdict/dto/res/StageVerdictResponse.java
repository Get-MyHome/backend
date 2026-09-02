package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "StageVerdictResponse", description = "구간(계약금/중도금/잔금) 판정 결과")
public record StageVerdictResponse(
  @Schema(description = "구간. CONTRACT(계약금), INTERIM(중도금), BALANCE(잔금)", example = "CONTRACT")
  String stage,
  @Schema(description = "판정 상태")
  VerdictStatus status,
  @Schema(description = "필요 금액 (만원)", example = "12000")
  Integer required,
  @Schema(description = "가용 금액 (만원, 현금 + 대출)", example = "15000")
  Integer available,
  @Schema(description = "부족 금액 (만원). 충분하면 null", example = "3000")
  Integer gap,
  @Schema(description = "납부 기한까지 남은 개월 수. 해당 없으면 null", example = "24")
  Integer monthsAvailable,
  @Schema(description = "저축으로 부족분 해소에 필요한 개월 수. 해당 없으면 null", example = "6")
  Integer monthsNeeded,
  @Schema(description = "저축 시나리오 메시지 목록")
  List<String> scenarios,
  @Schema(description = "근거 자료 ID 목록")
  List<String> evidenceIds,
  @Schema(description = "상태 이유 한 줄 요약 (화면 표시용)",
      example = "현금 5,000만 원으로 계약금 3,000만 원 충당 가능")
  String reasonSummary,
  @Schema(description = "해당 구간 납부 기한 (YYYY-MM-DD). 미확정이면 null", example = "2028-06-30")
  String dueDate
) {
}