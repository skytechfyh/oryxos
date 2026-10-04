package io.oryxos.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link ToolInvocation} 的持久化接口。 */
public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {

  /**
   * 按会话标识查询该会话的全部工具调用。
   *
   * @param sessionId 会话标识
   * @return 工具调用审计记录
   */
  List<ToolInvocation> findBySessionId(String sessionId);
}
