package aichallenge.getmyhome.global.success;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum GlobalSuccessCode implements SuccessCode {

  SUCCESS(HttpStatus.OK, "SUCCESS", "요청에 성공했습니다."),
  CREATED(HttpStatus.CREATED, "CREATED", "요청에 성공했으며 리소스가 정상적으로 생성되었습니다.")
  ;

  private final HttpStatus status;
  private final String successCode;
  private final String message;

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String successCode() {
    return successCode;
  }

  @Override
  public String message() {
    return message;
  }
}