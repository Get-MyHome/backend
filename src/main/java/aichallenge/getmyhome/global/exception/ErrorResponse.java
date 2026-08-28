package aichallenge.getmyhome.global.exception;

/**
 * API 명세서 공통 오류 응답 포맷 (0절)
 */
public record ErrorResponse(
  String errorCode,
  String message,
  boolean retryable
) {

  public static ErrorResponse of(ErrorCode code) {
    return new ErrorResponse(code.errorCode(), code.message(), code.retryable());
  }

  public static ErrorResponse of(ErrorCode code, String message) {
    return new ErrorResponse(code.errorCode(), message, code.retryable());
  }
}