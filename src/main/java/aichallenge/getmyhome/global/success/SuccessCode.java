package aichallenge.getmyhome.global.success;

import org.springframework.http.HttpStatus;

public interface SuccessCode {

  HttpStatus status();

  String successCode();

  String message();
}