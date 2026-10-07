package io.oryxos.cli;

/**
 * 最小 Profile 模板,{@code init} 与 {@code profile create} 共用。
 *
 * <p>只使用 Profile 已有字段,不引入新字段;provider 默认指向全局层的 deepseek,凭证不在这里出现。
 */
final class ProfileTemplates {

  /** 工具类,禁止实例化。 */
  private ProfileTemplates() {}

  /**
   * 生成最小 Profile YAML。
   *
   * @param name Profile 名(调用方已校验合法)
   * @param description 描述
   * @return YAML 文本
   */
  static String profile(String name, String description) {
    return "name: "
        + name
        + "\n"
        + "description: "
        + description
        + "\n"
        + "identity:\n"
        + "  agent_name: "
        + name
        + "\n"
        + "  prompt: 你是一名乐于助人的助手,回答要简洁准确。\n"
        + "provider:\n"
        + "  name: deepseek # 必须能在 application.yaml 的 oryxos.providers 里找到同名项\n"
        + "  model: deepseek-chat\n"
        + "  temperature: 0.7\n"
        + "settings:\n"
        + "  max_iterations: 10\n"
        + "  max_history_turns: 20\n";
  }
}
