package io.oryxos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void bizExceptionMapsToItsHttpStatus() throws Exception {
    mvc.perform(get("/biz"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("ORYX-503"));
  }

  @Test
  void unexpectedExceptionMapsTo500() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("ORYX-500"));
  }

  @Test
  void unknownPathMapsTo404() throws Exception {
    mvc.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ORYX-404"));
  }

  @Test
  void unsupportedMethodKeepsItsStatus() throws Exception {
    mvc.perform(post("/biz")).andExpect(status().isMethodNotAllowed());
  }

  @RestController
  static class ThrowingController {
    @GetMapping("/biz")
    String biz() {
      throw new BizException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @GetMapping("/boom")
    String boom() {
      throw new IllegalStateException("boom");
    }
  }
}
