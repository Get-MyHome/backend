package aichallenge.getmyhome.complex.exception;

import aichallenge.getmyhome.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ApplyhomeErrorCode implements ErrorCode {

    APPLYHOME_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "APPLYHOME_001", "청약홈 API 호출에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    APPLYHOME_API_RESPONSE_ERROR(HttpStatus.BAD_GATEWAY, "APPLYHOME_002", "청약홈 API 응답을 처리할 수 없습니다."),
    APPLYHOME_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "APPLYHOME_003", "청약홈 API 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;

    @Override
    public HttpStatus status() { return status; }

    @Override
    public String errorCode() { return errorCode; }

    @Override
    public String message() { return message; }

    @Override
    public boolean retryable() { return true; }
}