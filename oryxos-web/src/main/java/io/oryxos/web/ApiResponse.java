package io.oryxos.web;

import java.time.Instant;

/** 统一响应体。 */
public record ApiResponse<T>(String code, String message, T data, Instant timestamp) {

  private static final String SUCCESS_CODE = "0";

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(SUCCESS_CODE, "OK", data, Instant.now());
  }

  public static ApiResponse<Void> ok() {
    return ok(null);
  }
}
