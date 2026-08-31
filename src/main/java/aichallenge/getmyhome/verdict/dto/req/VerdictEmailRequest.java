package aichallenge.getmyhome.verdict.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "판정 결과 이메일 발송 요청")
public record VerdictEmailRequest(
  @Schema(description = "수신 이메일 주소", example = "user@example.com")
  @NotBlank(message = "이메일을 입력해 주세요.")
  @Email(message = "올바른 이메일 형식이 아닙니다.")
  String email
) {
}