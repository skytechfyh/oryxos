package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.tool.OryxTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolSchemaAdapterTest {

  private static final String SCHEMA =
      "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}";

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  private static OryxTool tool(String name, String description, String schema) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public String getInputSchema() {
        return schema;
      }
    };
  }

  @Test
  @DisplayName("翻译后字段一一对齐")
  void translatedFieldsAlignOneToOne() {
    List<ToolCallback> result =
        adapter.toSpringAiTools(
            List.of(tool("http_get", "发起 GET 请求", SCHEMA), tool("read_file", "读文件", "{}")));

    assertThat(result).hasSize(2);
    ToolDefinition first = result.get(0).getToolDefinition();
    assertThat(first.name()).isEqualTo("http_get");
    assertThat(first.description()).isEqualTo("发起 GET 请求");
    assertThat(first.inputSchema()).isEqualTo(SCHEMA);
    assertThat(result.get(1).getToolDefinition().name()).isEqualTo("read_file");
  }

  @Test
  @DisplayName("只翻译不执行_产物调用即拒绝")
  void translatedToolRefusesExecution() {
    ToolCallback callback =
        adapter.toSpringAiTools(List.of(tool("http_get", "发起 GET 请求", SCHEMA))).get(0);

    assertThatThrownBy(() -> callback.call("{\"url\":\"http://x\"}"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("空列表_返回空列表")
  void emptyListYieldsEmptyList() {
    assertThat(adapter.toSpringAiTools(List.of())).isEmpty();
  }
}
