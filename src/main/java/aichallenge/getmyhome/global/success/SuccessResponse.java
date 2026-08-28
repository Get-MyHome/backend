package aichallenge.getmyhome.global.success;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse<T>(
  String successCode,
  String message,
  T data
) {

  public static <T> ResponseEntity<SuccessResponse<T>> of(SuccessCode code, T data) {
    return ResponseEntity
      .status(code.status())
      .body(new SuccessResponse<>(code.successCode(), code.message(), data));
  }

  public static ResponseEntity<SuccessResponse<Void>> of(SuccessCode code) {
    return ResponseEntity
      .status(code.status())
      .body(new SuccessResponse<>(code.successCode(), code.message(), null));
  }
}