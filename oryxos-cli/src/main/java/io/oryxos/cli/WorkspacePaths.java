package io.oryxos.cli;

import java.nio.file.Path;

/**
 * 工作区路径与名称校验的小工具,供轻命令共用。
 *
 * <p>工作区约定见 TechnicalSolution §8.1:当前目录下的 {@code .oryxos/}。所有文件写入都限定在这个目录内, Profile
 * 名只允许字母、数字、下划线、连字符,拒绝路径分隔符与 {@code ..},防止路径穿越。
 */
final class WorkspacePaths {

  /** 工作区目录名。 */
  private static final String WORKSPACE = ".oryxos";

  /** 工具类,禁止实例化。 */
  private WorkspacePaths() {}

  /**
   * 工作区根目录。
   *
   * @return {@code .oryxos}
   */
  static Path root() {
    return Path.of(WORKSPACE);
  }

  /**
   * Profile 目录。
   *
   * @return {@code .oryxos/profiles}
   */
  static Path profilesDir() {
    return root().resolve("profiles");
  }

  /**
   * 数据库文件路径,取环境变量 {@code ORYXOS_DB_PATH},默认当前目录的 {@code oryxos.db}。
   *
   * @return 数据库文件路径
   */
  static Path dbPath() {
    String configured = System.getenv("ORYXOS_DB_PATH");
    return Path.of(configured == null || configured.isBlank() ? "./oryxos.db" : configured);
  }

  /**
   * 解析某个 Profile 的 YAML 文件路径,同时校验名称合法。
   *
   * <p>第 24 节落地后,此处的文件访问需接入 {@code SandboxChecker}(留调用位)。
   *
   * @param name Profile 名
   * @return {@code .oryxos/profiles/<name>.yaml}
   * @throws IllegalArgumentException 名称为空或含非法字符
   */
  static Path profileFile(String name) {
    requireValidName(name);
    return profilesDir().resolve(name + ".yaml");
  }

  /**
   * 校验名称只含字母、数字、下划线、连字符。手工逐字符判断而不用正则,避免回溯风险。
   *
   * @param name 待校验名称
   * @throws IllegalArgumentException 名称为空或含非法字符
   */
  static void requireValidName(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("名称不能为空");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '_'
              || c == '-';
      if (!ok) {
        throw new IllegalArgumentException("名称只允许字母、数字、下划线、连字符: " + name);
      }
    }
  }
}
