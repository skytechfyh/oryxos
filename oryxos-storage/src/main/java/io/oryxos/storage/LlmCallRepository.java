package io.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** {@link LlmCall} 的持久化接口。 */
public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {}
