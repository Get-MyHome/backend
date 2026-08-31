package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판정 결과 이메일 발송 응답")
public record VerdictEmailResponse(
  @Schema(description = "발송 상태", example = "SENT")
  String status,
  @Schema(description = "수신 이메일 주소", example = "user@example.com")
  String email,
  @Schema(description = "발송 시각 (ISO 8601)", example = "2026-08-29T12:00:00Z")
  String sentAt
) {
}