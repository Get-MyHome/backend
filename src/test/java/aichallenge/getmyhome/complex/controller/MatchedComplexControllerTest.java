package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse.ComplexSummary;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.global.exception.GlobalErrorCode;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchedComplexController.class)
@WithMockUser
class MatchedComplexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplexService complexService;

    @Test
    @DisplayName("POST /complexes/matched - conditionToken으로 조회")
    void withToken() throws Exception {
        ComplexListResponse response = new ComplexListResponse(
            List.of(new ComplexSummary(
                "2025000001", "래미안", "민영", "서울", "서초구",
                "2026-08-01", "2026-08-15", "2028년06월",
                120000, true,
                List.of("디딤돌 대출 - 일반", "시중은행 주택담보대출")
            )),
            1, 1, 20, "2026-09-01 14:30:00"
        );
        when(complexService.getMatchedComplexes(eq("CT-abc12345"), isNull(), isNull(), isNull(), eq(1), eq(20)))
            .thenReturn(response);

        String body = """
            {
              "condition_token": "CT-abc12345"
            }
            """;

        mockMvc.perform(post("/complexes/matched")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].matched_product_names").isArray())
            .andExpect(jsonPath("$.data.items[0].matched_product_names[0]").value("디딤돌 대출 - 일반"));
    }

    @Test
    @DisplayName("POST /complexes/matched - user 조건 직접 전달")
    void withUser() throws Exception {
        ComplexListResponse response = new ComplexListResponse(List.of(), 0, 1, 20, "2026-09-01 14:30:00");
        when(complexService.getMatchedComplexes(isNull(), any(), isNull(), isNull(), eq(1), eq(20)))
            .thenReturn(response);

        String body = """
            {
              "user": {
                "annual_income": 4000,
                "cash": 5000,
                "birth_date": "1995-03-15",
                "marital": "SINGLE",
                "homeless": true
              }
            }
            """;

        mockMvc.perform(post("/complexes/matched")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /complexes/matched - 만료된 토큰 → 400")
    void expiredToken() throws Exception {
        when(complexService.getMatchedComplexes(eq("CT-expired"), isNull(), isNull(), isNull(), eq(1), eq(20)))
            .thenThrow(BaseException.of(VerdictErrorCode.CONDITION_TOKEN_EXPIRED));

        String body = """
            {
              "condition_token": "CT-expired"
            }
            """;

        mockMvc.perform(post("/complexes/matched")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("VERDICT_005"));
    }

    @Test
    @DisplayName("POST /complexes/matched - token과 user 모두 없음 → 400")
    void noTokenNoUser() throws Exception {
        when(complexService.getMatchedComplexes(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
            .thenThrow(BaseException.of(GlobalErrorCode.BAD_REQUEST,
                "conditionToken 또는 user 중 하나는 필수입니다."));

        String body = "{}";

        mockMvc.perform(post("/complexes/matched")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /complexes/matched - 지역 및 주택구분 필터 포함")
    void withFilters() throws Exception {
        ComplexListResponse response = new ComplexListResponse(List.of(), 0, 1, 20, "2026-09-01 14:30:00");
        when(complexService.getMatchedComplexes(eq("CT-abc12345"), isNull(), eq("서울"), any(), eq(1), eq(20)))
            .thenReturn(response);

        String body = """
            {
              "condition_token": "CT-abc12345",
              "region": "서울",
              "house_category": "PRIVATE",
              "page": 1,
              "size": 20
            }
            """;

        mockMvc.perform(post("/complexes/matched")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }
}
