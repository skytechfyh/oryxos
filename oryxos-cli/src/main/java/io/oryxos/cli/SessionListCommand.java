package io.oryxos.cli;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos session list}:列出已有会话。
 *
 * <p>轻命令:用 JDBC 只读查询 {@code sessions} 表,不启动 Spring,也不给 {@code SessionManager} 增加课件之外的公共方法。 SQL
 * 是常量,无任何拼接。
 */
@Command(name = "list", mixinStandardHelpOptions = true, description = "列出已有会话")
public class SessionListCommand implements Callable<Integer> {

  private static final String QUERY =
      "select session_id, profile_name, channel, user_id, status, last_active_at"
          + " from sessions order by last_active_at desc";

  /** SQLite 在表不存在时报错信息里的固定片段。 */
  private static final String NO_SUCH_TABLE = "no such table";

  @Spec private CommandSpec spec;

  /**
   * 查询并打印会话。
   *
   * @return 成功 0
   * @throws SQLException 非"表不存在"的数据库错误,上抛由统一处理器报错
   */
  @Override
  public Integer call() throws SQLException {
    PrintWriter out = spec.commandLine().getOut();
    Path db = WorkspacePaths.dbPath();
    if (!Files.exists(db)) {
      out.println("暂无会话(数据库尚未创建)");
      return 0;
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(QUERY)) {
      int count = 0;
      while (rows.next()) {
        count++;
        out.println(
            String.join(
                "\t",
                rows.getString("session_id"),
                rows.getString("profile_name"),
                rows.getString("channel"),
                rows.getString("user_id"),
                rows.getString("status"),
                rows.getString("last_active_at")));
      }
      if (count == 0) {
        out.println("暂无会话");
      }
    } catch (SQLException e) {
      // 库文件存在但还没建过表:按"暂无会话"处理;其它数据库错误必须上抛,不吞
      if (e.getMessage() != null && e.getMessage().contains(NO_SUCH_TABLE)) {
        out.println("暂无会话(sessions 表尚未创建)");
        return 0;
      }
      throw e;
    }
    return 0;
  }
}
