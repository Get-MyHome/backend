package aichallenge.getmyhome.verdict.controller;

import aichallenge.getmyhome.verdict.dto.res.FinancingRouteDetailResponse;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResultResponse;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import aichallenge.getmyhome.verdict.service.FinancingRouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancingRouteController.class)
@WithMockUser
class FinancingRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancingRouteService financingRouteService;

    @MockitoBean
    private RuleProperties ruleProperties;

    @Test
    @DisplayName("POST /financing-routes - 정상 요청")
    void success() throws Exception {
        RuleVersion rule = new RuleVersion();
        when(ruleProperties.resolve(null)).thenReturn(rule);

        FinancingRouteResultResponse response = new FinancingRouteResultResponse(
            "CT-test1234",
            List.of(FinancingRouteDetailResponse.ok("BANK_MORTGAGE", "시중은행 주택담보대출", 15000, 25000, "DSR"))
        );
        when(financingRouteService.evaluateAndCache(any(), any())).thenReturn(response);

        String body = """
            {
              "annual_income": 4000,
              "cash": 5000,
              "birth_date": "1995-03-15",
              "marital": "SINGLE",
              "homeless": true
            }
            """;

        mockMvc.perform(post("/financing-routes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.condition_token").value("CT-test1234"))
            .andExpect(jsonPath("$.data.routes").isArray())
            .andExpect(jsonPath("$.data.routes[0].product_code").value("BANK_MORTGAGE"));
    }

    @Test
    @DisplayName("POST /financing-routes - 필수값 누락 시 400")
    void validationError() throws Exception {
        String body = """
            {
              "cash": 5000,
              "birth_date": "1995-03-15",
              "marital": "SINGLE",
              "homeless": true
            }
            """;

        mockMvc.perform(post("/financing-routes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").exists());
    }

    @Test
    @DisplayName("POST /financing-routes - 잘못된 JSON 형식 시 400")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/financing-routes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /financing-routes - marital 값 잘못된 경우 400")
    void invalidEnum() throws Exception {
        String body = """
            {
              "annual_income": 4000,
              "cash": 5000,
              "birth_date": "1995-03-15",
              "marital": "INVALID_STATUS",
              "homeless": true
            }
            """;

        mockMvc.perform(post("/financing-routes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /financing-routes - 2단계 필드 포함 정상 요청")
    void step2Fields() throws Exception {
        RuleVersion rule = new RuleVersion();
        when(ruleProperties.resolve(null)).thenReturn(rule);

        FinancingRouteResultResponse response = new FinancingRouteResultResponse(
            "CT-test5678",
            List.of(
                FinancingRouteDetailResponse.ok("DIDIMDOL_GENERAL", "디딤돌 대출 - 일반", null, 20000, "DTI"),
                FinancingRouteDetailResponse.block("YOUTH_DREAM_SINGLE", "청년주택드림 대출 - 미혼", "기혼자는 미혼 청년 대출 대상이 아닙니다"),
                FinancingRouteDetailResponse.ok("BANK_MORTGAGE", "시중은행 주택담보대출", 15000, 25000, "DSR")
            )
        );
        when(financingRouteService.evaluateAndCache(any(), any())).thenReturn(response);

        String body = """
            {
              "annual_income": 4000,
              "cash": 5000,
              "birth_date": "1990-01-01",
              "marital": "MARRIED",
              "homeless": true,
              "spouse_income": 3000,
              "household_type": "HEAD",
              "all_members_homeless": true,
              "net_asset": 30000,
              "first_time_buyer": true,
              "subscription_account": {
                "type": "YOUTH_DREAM",
                "opened_at": "2023-01-10",
                "deposit_count": 24,
                "deposit_amount": 600
              }
            }
            """;

        mockMvc.perform(post("/financing-routes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.routes").isArray())
            .andExpect(jsonPath("$.data.routes.length()").value(3));
    }
}
