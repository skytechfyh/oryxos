package io.oryxos.core.agent;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为 system prompt 提供 Bootstrap 文件与 SKILL.md 的文本。
 *
 * <p>两条铁律:每次 {@link #load} 都重新读文件、类内不缓存任何内容,用户改完立即生效;Profile 显式引用的 Skill 缺失要报错,Bootstrap 缺失至少
 * WARN,静默跳过会造成"人格悄悄丢了"这种最难查的软故障。{@code SKILL.md} 是指令模板而不是 Tool,所以由这里加载、不进 ToolRegistry。
 */
public class ContextLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ContextLoader.class);

  private final Path workspace;

  /**
   * 构造加载器。
   *
   * @param workspace 工作区根目录,即 {@code .oryxos/} 所在路径
   */
  public ContextLoader(Path workspace) {
    this.workspace = workspace.toAbsolutePath().normalize();
  }

  /**
   * 按 Profile 的 bootstrap 与 skills 字段读取文件并拼接。
   *
   * @param profile 当前 Agent 的 Profile
   * @return 拼接后的上下文文本,没有任何内容时为空串
   * @throws BizException Profile 显式引用的 Skill 文件不存在或路径越界时抛出
   */
  public String load(Profile profile) {
    List<String> parts = new ArrayList<>();
    for (String name : profile.bootstrap()) {
      Path file = resolve(workspace, name);
      if (Files.isRegularFile(file)) {
        parts.add(read(file));
      } else {
        LOG.warn("Bootstrap 文件缺失,已跳过: profile={}, file={}", profile.name(), file);
      }
    }
    Path skillRoot = workspace.resolve("skills");
    for (String skill : profile.skills()) {
      Path file = resolve(skillRoot, skill + "/SKILL.md");
      if (!Files.isRegularFile(file)) {
        throw new BizException(ErrorCode.NOT_FOUND, "Profile 引用的 Skill 文件不存在: " + file);
      }
      parts.add(read(file));
    }
    return String.join("\n\n", parts);
  }

  /**
   * 在根目录下解析相对路径,并拒绝逃出根目录的路径,避免 Profile 里写 {@code ../} 读到工作区之外的文件。
   *
   * @param root 允许读取的根目录
   * @param relative Profile 里写的相对路径
   * @return 规范化后的绝对路径
   */
  private static Path resolve(Path root, String relative) {
    Path file = root.resolve(relative).normalize();
    if (!file.startsWith(root)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "文件路径越界: " + relative);
    }
    return file;
  }

  /**
   * 读取文件全文。读失败不吞,包装后上抛,让调用方看见真实原因。
   *
   * @param file 文件路径
   * @return 文件内容
   */
  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "读取上下文文件失败: " + file, e);
    }
  }
}
