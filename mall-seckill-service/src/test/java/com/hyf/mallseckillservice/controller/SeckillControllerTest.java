package com.hyf.mallseckillservice.controller;

import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.mallcommon.security.jwt.LoginUser;
import com.hyf.mallseckillservice.dto.ExecuteReqDTO;
import com.hyf.mallseckillservice.dto.ExecuteResultDTO;
import com.hyf.mallseckillservice.dto.SeckillResultDTO;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeckillControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void executeUsesSecurityContextUserId() {
        SeckillApplicationService service = mock(SeckillApplicationService.class);
        when(service.execute(any(), eq(20L), any())).thenReturn(new ExecuteResultDTO("queued", "msg-1"));
        SeckillController controller = new SeckillController(service);
        SecurityContextHolder.set(LoginUser.builder().userId(10L).build());

        controller.execute(20L, new ExecuteReqDTO());

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(service).execute(userIdCaptor.capture(), eq(20L), any());
        assertThat(userIdCaptor.getValue()).isEqualTo(10L);
    }

    @Test
    void resultUsesSecurityContextUserId() {
        SeckillApplicationService service = mock(SeckillApplicationService.class);
        when(service.result(any(), eq(20L), eq(30L))).thenReturn(new SeckillResultDTO());
        SeckillController controller = new SeckillController(service);
        SecurityContextHolder.set(LoginUser.builder().userId(10L).build());

        controller.result(20L, 30L);

        verify(service).result(10L, 20L, 30L);
    }

    @Test
    void executeIgnoresSpoofedUserIdHeader() throws Exception {
        SeckillApplicationService service = mock(SeckillApplicationService.class);
        when(service.execute(any(), eq(20L), any())).thenReturn(new ExecuteResultDTO("queued", "msg-1"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SeckillController(service)).build();
        SecurityContextHolder.set(LoginUser.builder().userId(10L).build());

        mockMvc.perform(post("/seckill/20/execute")
                        .header("X-User-Id", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seckillItemId": 30,
                                  "quantity": 1,
                                  "addressId": 40
                                }
                                """))
                .andExpect(status().isOk());

        verify(service).execute(eq(10L), eq(20L), any());
    }
}
