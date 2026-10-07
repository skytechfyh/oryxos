package io.oryxos.tool.notify;

/**
 * 出站通知适配器,与入站 Channel 对称:入站管"消息怎么进来",这里管"结果怎么主动送出去"。
 *
 * <p>签名只表达"把一段内容送到某个通知目标"这一意图,不出现任何某一渠道特有的概念。契约:成功即正常返回;失败抛运行时异常,不吞, 调用方据此知道没送达。新增渠道只新增实现类。
 */
public interface NotifyChannelAdapter {

  /**
   * 把内容送到通知目标。
   *
   * @param target 通知目标
   * @param content 要送出的内容
   */
  void send(NotifyTarget target, String content);
}
