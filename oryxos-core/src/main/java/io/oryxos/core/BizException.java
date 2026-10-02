package io.oryxos.core;

/** 业务异常,携带统一错误码。 */
public class BizException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  public BizException(ErrorCode errorCode) {
    super(errorCode.defaultMessage());
    this.errorCode = errorCode;
  }

  public BizException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public BizException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
