package io.oryxos.web;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理,将异常统一转换为标准 JSON 错误。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 5xx 状态码下限,达到该值按错误级别记录日志。 */
  private static final int SERVER_ERROR_MIN_STATUS = 500;

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ErrorResponse> handleBiz(BizException e) {
    ErrorCode code = e.getErrorCode();
    if (code.httpStatus() >= SERVER_ERROR_MIN_STATUS) {
      LOG.error("业务异常: {}", e.getMessage(), e);
    } else {
      LOG.warn("业务异常: {}", e.getMessage());
    }
    return build(code, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
    LOG.warn("请求参数错误: {}", e.getMessage());
    return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage());
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
    return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
  }

  /** 405/406/415 等框架自带的 4xx 异常,保持其原始状态码,不落入 500。 */
  @ExceptionHandler({
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotSupportedException.class,
    HttpMediaTypeNotAcceptableException.class
  })
  public ResponseEntity<ErrorResponse> handleFrameworkClientError(Exception e) {
    HttpStatusCode status =
        e instanceof org.springframework.web.ErrorResponse er
            ? er.getStatusCode()
            : HttpStatus.BAD_REQUEST;
    LOG.warn("请求不被支持: {}", e.getMessage());
    return ResponseEntity.status(status)
        .body(new ErrorResponse("ORYX-" + status.value(), e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    LOG.error("未预期异常", e);
    return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
  }

  private static ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
    return ResponseEntity.status(code.httpStatus())
        .body(new ErrorResponse(code.code(), message, Instant.now()));
  }
}
