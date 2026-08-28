package aichallenge.getmyhome.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ErrorResponse> onBaseException(BaseException e) {
    ErrorCode code = e.getCode();
    log.warn("Business error: {} | {}", code.errorCode(), code.message(), e);
    String detail = e.getCustomMessage() != null ? e.getCustomMessage() : code.message();
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, detail));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> onMethodArgumentNotValid(MethodArgumentNotValidException e) {
    String msg = extractErrorMessage(e.getBindingResult().getFieldErrors());
    log.info("Validation failed: {}", msg);
    return buildResponse(GlobalErrorCode.VALIDATION_ERROR, msg);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> onMissingParam(MissingServletRequestParameterException e) {
    log.info("Missing parameter: {}", e.getParameterName());
    String msg = "필수 파라미터 '" + e.getParameterName() + "'이(가) 누락되었습니다.";
    return buildResponse(GlobalErrorCode.MISSING_PARAMETER, msg);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> onConstraintViolation(ConstraintViolationException e) {
    log.info("Constraint violation: {}", e.getMessage());
    return buildResponse(GlobalErrorCode.VALIDATION_ERROR, e.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> onHttpMessageNotReadable(HttpMessageNotReadableException e) {
    log.info("Malformed JSON request: {}", e.getMessage());
    return buildResponse(GlobalErrorCode.INVALID_REQUEST_BODY);
  }

  @ExceptionHandler({NoHandlerFoundException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ErrorResponse> onNotFoundOrTypeMismatch(Exception e) {
    log.info("Not found or type mismatch: {}", e.getMessage());
    return buildResponse(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> onMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    log.info("Method not supported: {}", e.getMethod());
    return buildResponse(GlobalErrorCode.NOT_SUPPORTED_METHOD_ERROR);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> onMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
    log.info("Media type not supported: {}", e.getContentType());
    return buildResponse(GlobalErrorCode.NOT_SUPPORTED_MEDIA_TYPE_ERROR);
  }

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<ErrorResponse> onAnyException(Throwable e) {
    log.error("Unhandled exception", e);
    return buildResponse(GlobalErrorCode.INTERNAL_SERVER_ERROR);
  }

  private String extractErrorMessage(List<FieldError> fieldErrors) {
    return fieldErrors.stream()
      .map(FieldError::getDefaultMessage)
      .collect(Collectors.joining("; "));
  }

  private ResponseEntity<ErrorResponse> buildResponse(ErrorCode code) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
  }

  private ResponseEntity<ErrorResponse> buildResponse(ErrorCode code, String message) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message));
  }
}