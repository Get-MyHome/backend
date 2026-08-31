package aichallenge.getmyhome.verdict.exception;

import aichallenge.getmyhome.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VerdictErrorCode implements ErrorCode {

  INVALID_RULE_VERSION(HttpStatus.BAD_REQUEST, "VERDICT_001", "지원하지 않는 규칙 버전입니다."),
  CALCULATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VERDICT_002", "판정 계산 중 오류가 발생했습니다."),
  VERDICT_NOT_FOUND(HttpStatus.NOT_FOUND, "VERDICT_003", "판정 결과가 만료되었거나 존재하지 않습니다."),
  EMAIL_NOT_IMPLEMENTED(HttpStatus.SERVICE_UNAVAILABLE, "VERDICT_004", "이메일 발송 기능이 아직 준비되지 않았습니다."),
  CONDITION_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "VERDICT_005", "조건 토큰이 만료되었거나 존재하지 않습니다. 대출 자격 조회를 다시 수행해 주세요."),
  EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VERDICT_006", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");

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
}