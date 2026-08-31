package aichallenge.getmyhome.global.success;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse<T>(
  @Schema(description = "성공 코드", example = "SUCCESS")
  String successCode,
  @Schema(description = "응답 메시지", example = "요청에 성공했습니다.")
  String message,
  @Schema(description = "응답 데이터")
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