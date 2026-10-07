package io.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** {@link Session} 的持久化接口,主键即会话标识。 */
public interface SessionRepository extends JpaRepository<Session, String> {}
