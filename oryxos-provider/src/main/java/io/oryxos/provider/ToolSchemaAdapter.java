package io.oryxos.provider;

import io.oryxos.core.tool.OryxTool;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * 把 {@link OryxTool} 的参数说明翻译成 Spring AI 的工具描述,只翻译、不执行。
 *
 * <p>Spring AI 的请求选项只接受 {@link ToolCallback},所以产物是"仅含 schema"的回调;它的 call 一律拒绝, 因为自动执行已关闭,真正的执行归
 * ToolExecutor。
 */
@Component
public class ToolSchemaAdapter {

  public List<ToolCallback> toSpringAiTools(List<OryxTool> tools) {
    return tools.stream().map(ToolSchemaAdapter::toSchemaOnly).toList();
  }

  private static ToolCallback toSchemaOnly(OryxTool tool) {
    ToolDefinition definition =
        ToolDefinition.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.getInputSchema())
            .build();
    return new SchemaOnlyToolCallback(definition);
  }

  /** 只携带工具描述;被调用说明自动执行被误开,必须立刻暴露而不是悄悄执行。 */
  private record SchemaOnlyToolCallback(ToolDefinition toolDefinition) implements ToolCallback {

    @Override
    public ToolDefinition getToolDefinition() {
      return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
      throw new UnsupportedOperationException(
          "Provider 只翻译工具 schema,不执行工具: " + toolDefinition.name());
    }
  }
}
