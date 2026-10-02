package io.oryxos.core;

/** 统一错误码,{@code httpStatus} 为建议的 HTTP 状态码。 */
public enum ErrorCode {
  /** 请求参数不合法。 */
  BAD_REQUEST(400, "ORYX-400", "请求参数不合法"),
  /** 资源不存在。 */
  NOT_FOUND(404, "ORYX-404", "资源不存在"),
  /** 服务内部错误。 */
  INTERNAL_ERROR(500, "ORYX-500", "服务内部错误"),
  /** 服务暂不可用。 */
  SERVICE_UNAVAILABLE(503, "ORYX-503", "服务暂不可用");

  private final int httpStatus;
  private final String code;
  private final String defaultMessage;

  ErrorCode(int httpStatus, String code, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
