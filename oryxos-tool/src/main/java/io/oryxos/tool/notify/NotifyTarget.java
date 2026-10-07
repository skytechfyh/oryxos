package io.oryxos.tool.notify;

import java.util.Map;

/**
 * 一次出站通知的去向:渠道类型 + 一份渠道配置。
 *
 * <p>接口层不解释配置含义——由具体实现自行读取它需要的键(地址、认证信息等)。这样新增渠道只需新增实现类,不必改这个类型,也不必改调用方。
 *
 * @param channelType 渠道类型标识
 * @param config 渠道配置,键值含义由具体实现解释
 */
public record NotifyTarget(String channelType, Map<String, String> config) {

  /**
   * 紧凑构造器:对配置做不可变拷贝,避免调用方事后改动配置而影响已构造的目标。
   *
   * <p>只做拷贝、不做校验——为 null 的配置原样保留,由具体实现按自己的需要报错。
   */
  public NotifyTarget {
    config = config == null ? null : Map.copyOf(config);
  }
}
