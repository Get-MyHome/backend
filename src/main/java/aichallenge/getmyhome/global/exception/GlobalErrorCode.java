package aichallenge.getmyhome.global.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum GlobalErrorCode implements ErrorCode {

  /**
   * 400: 요청 실패 - 클라이언트 오류
   */
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "REQUEST_001", "요청 데이터가 유효하지 않습니다. 입력값을 확인해 주세요."),
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "REQUEST_002", "잘못된 요청입니다. 요청 형식 또는 파라미터를 확인해 주세요."),
  NOT_SUPPORTED_URI_ERROR(HttpStatus.NOT_FOUND, "REQUEST_003", "요청한 리소스를 찾을 수 없습니다. URI 경로를 확인해 주세요."),
  NOT_SUPPORTED_METHOD_ERROR(HttpStatus.METHOD_NOT_ALLOWED, "REQUEST_004", "허용되지 않는 HTTP 메서드입니다. 요청 메서드를 확인해 주세요."),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "REQUEST_006", "필수 요청 파라미터가 누락되었습니다."),
  INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "REQUEST_007", "요청 바디가 올바르지 않습니다. JSON 형식을 확인해 주세요."),
  NOT_SUPPORTED_MEDIA_TYPE_ERROR(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQUEST_005", "지원하지 않는 미디어 타입입니다. Content-Type을 확인해 주세요."),

  /**
   * 500: 응답 실패 - 서버 오류
   */
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "RESPONSE_001", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String errorCode;
  private final String message;

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String errorCode() {
    return errorCode;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public boolean retryable() {
    return this == INTERNAL_SERVER_ERROR;
  }
}
