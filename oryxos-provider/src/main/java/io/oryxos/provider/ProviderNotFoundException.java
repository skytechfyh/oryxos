package io.oryxos.provider;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;

/** Profile 引用了全局层没有接入的 provider;直接报错,不悄悄换一家或留空跑过去。 */
public class ProviderNotFoundException extends BizException {

  private static final long serialVersionUID = 1L;

  public ProviderNotFoundException(String providerName) {
    super(ErrorCode.NOT_FOUND, "provider 未接入或不可用: " + providerName);
  }
}
