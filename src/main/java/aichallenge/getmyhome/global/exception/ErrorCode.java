package aichallenge.getmyhome.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  HttpStatus status();

  String errorCode();

  String message();

  default boolean retryable() {
    return false;
  }
}