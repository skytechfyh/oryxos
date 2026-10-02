package io.oryxos.web;

import java.time.Instant;

/** 统一错误响应体。 */
public record ErrorResponse(String errorCode, String message, Instant timestamp) {}
