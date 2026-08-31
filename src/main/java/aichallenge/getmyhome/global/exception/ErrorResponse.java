package aichallenge.getmyhome.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 명세서 공통 오류 응답 포맷 (0절)
 */
@Schema(title = "ErrorResponse", description = "공통 에러 응답")
public record ErrorResponse(
  @Schema(description = "에러 코드", example = "VERDICT_001")
  String errorCode,
  @Schema(description = "에러 메시지", example = "지원하지 않는 규칙 버전입니다.")
  String message,
  @Schema(description = "재시도 가능 여부", example = "false")
  boolean retryable
) {

  public static ErrorResponse of(ErrorCode code) {
    return new ErrorResponse(code.errorCode(), code.message(), code.retryable());
  }

  public static ErrorResponse of(ErrorCode code, String message) {
    return new ErrorResponse(code.errorCode(), message, code.retryable());
  }
}