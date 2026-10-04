package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.DefaultUsage;

class LlmCallAuditorTest {

  private final LlmCallRepository repository = mock(LlmCallRepository.class);
  private final LlmCallAuditor auditor = new LlmCallAuditor(repository);

  @Test
  @DisplayName("usage为null_token列为空且不报错")
  void nullUsageLeavesTokenColumnsEmpty() {
    auditor.record("s-1", "deepseek", "deepseek-chat", null, false, "timeout", 7L);

    var captor = ArgumentCaptor.forClass(LlmCall.class);
    verify(repository).save(captor.capture());
    LlmCall saved = captor.getValue();
    assertThat(saved.getPromptTokens()).isNull();
    assertThat(saved.getTotalTokens()).isNull();
    assertThat(saved.isSuccess()).isFalse();
    assertThat(saved.getErrorMessage()).isEqualTo("timeout");
    assertThat(saved.getDurationMs()).isEqualTo(7L);
  }

  @Test
  @DisplayName("成功记录_带上token与会话")
  void successRecordCarriesTokensAndSession() {
    auditor.record("s-1", "kimi", "moonshot", new DefaultUsage(1, 2, 3), true, null, 9L);

    var captor = ArgumentCaptor.forClass(LlmCall.class);
    verify(repository).save(captor.capture());
    LlmCall saved = captor.getValue();
    assertThat(saved.getSessionId()).isEqualTo("s-1");
    assertThat(saved.getProvider()).isEqualTo("kimi");
    assertThat(saved.getPromptTokens()).isEqualTo(1);
    assertThat(saved.getCompletionTokens()).isEqualTo(2);
    assertThat(saved.getTotalTokens()).isEqualTo(3);
    assertThat(saved.isSuccess()).isTrue();
    assertThat(saved.getCreatedAt()).isNotBlank();
  }

  @Test
  @DisplayName("repository抛异常_record不向外抛")
  void repositoryFailureIsSwallowed() {
    when(repository.save(any(LlmCall.class))).thenThrow(new IllegalStateException("db down"));

    assertThatCode(() -> auditor.record("s-1", "kimi", "m", null, true, null, 1L))
        .doesNotThrowAnyException();
  }
}
