package aichallenge.getmyhome.verdict.controller;

import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.verdict.dto.res.*;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;
import aichallenge.getmyhome.verdict.service.VerdictService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@WebMvcTest(VerdictController.class)
@WithMockUser
class VerdictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerdictService verdictService;

    // ═══════════════════════════════════════
    // POST /verdicts - 판정 실행
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("POST /verdicts - 판정 실행")
    class Calculate {

        @Test
        @DisplayName("정상 요청 - 추정 모드 (complexId 없음)")
        void estimationMode() throws Exception {
            VerdictResponse response = new VerdictResponse(
                "V-test1234",
                new VerdictResponse.VerdictMeta("v1", "A-2026-08", "2026-09-01", "step1"),
                List.of(new FinancingRouteResponse("BANK_MORTGAGE", VerdictStatus.OK, 15000, 25000, "DSR", null, List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );
            when(verdictService.calculate(any())).thenReturn(response);

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

            mockMvc.perform(post("/verdicts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict_id").value("V-test1234"))
                .andExpect(jsonPath("$.data.meta.rule_version").value("v1"))
                .andExpect(jsonPath("$.data.meta.precision").value("step1"))
                .andExpect(jsonPath("$.data.financing_routes").isArray());
        }

        @Test
        @DisplayName("정상 요청 - 단지 선택 모드")
        void withComplex() throws Exception {
            VerdictResponse response = new VerdictResponse(
                "V-complex01",
                new VerdictResponse.VerdictMeta("v1", "A-2026-08", "2026-09-01", "step2"),
                List.of(new FinancingRouteResponse("DIDIMDOL_GENERAL", VerdictStatus.OK, null, 20000, "DTI", null, List.of())),
                List.of(new SubscriptionEligibilityResponse("SUB_GENERAL", VerdictStatus.OK, null, List.of())),
                List.of(new StageVerdictResponse("CONTRACT", VerdictStatus.OK, 10000, 50000, null, null, null, List.of(), List.of())),
                List.of(),
                List.of(),
                List.of()
            );
            when(verdictService.calculate(any())).thenReturn(response);

            String body = """
                {
                  "user": {
                    "annual_income": 4000,
                    "cash": 50000,
                    "birth_date": "1990-01-01",
                    "marital": "MARRIED",
                    "homeless": true,
                    "spouse_income": 3000,
                    "household_type": "HEAD",
                    "all_members_homeless": true,
                    "net_asset": 30000
                  },
                  "complex_id": "2025000001",
                  "unit_type_id": "84A"
                }
                """;

            mockMvc.perform(post("/verdicts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict_id").value("V-complex01"))
                .andExpect(jsonPath("$.data.meta.precision").value("step2"))
                .andExpect(jsonPath("$.data.subscription_eligibilities").isArray())
                .andExpect(jsonPath("$.data.verdicts").isArray());
        }

        @Test
        @DisplayName("user와 conditionToken 모두 누락 시 400")
        void missingUserAndToken() throws Exception {
            String body = """
                {
                  "complex_id": "2025000001"
                }
                """;

            when(verdictService.calculate(any()))
                .thenThrow(BaseException.of(VerdictErrorCode.USER_CONDITION_REQUIRED));

            mockMvc.perform(post("/verdicts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("user 내 필수값 누락 시 400")
        void missingUserFields() throws Exception {
            String body = """
                {
                  "user": {
                    "annual_income": 4000,
                    "cash": 5000
                  }
                }
                """;

            mockMvc.perform(post("/verdicts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 규칙 버전 시 400")
        void invalidRuleVersion() throws Exception {
            when(verdictService.calculate(any()))
                .thenThrow(BaseException.of(VerdictErrorCode.INVALID_RULE_VERSION));

            String body = """
                {
                  "user": {
                    "annual_income": 4000,
                    "cash": 5000,
                    "birth_date": "1995-03-15",
                    "marital": "SINGLE",
                    "homeless": true
                  },
                  "rule_version": "v999"
                }
                """;

            mockMvc.perform(post("/verdicts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VERDICT_001"));
        }
    }

    // ═══════════════════════════════════════
    // POST /verdicts/{verdictId}/email - 이메일 발송
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("POST /verdicts/{verdictId}/email - 이메일 발송")
    class SendEmail {

        @Test
        @DisplayName("정상 요청")
        void success() throws Exception {
            VerdictEmailResponse response = new VerdictEmailResponse("SENT", "user@example.com", "2026-09-01T12:00:00Z");
            when(verdictService.sendResultEmail(eq("V-test1234"), eq("user@example.com")))
                .thenReturn(response);

            String body = """
                {
                  "email": "user@example.com"
                }
                """;

            mockMvc.perform(post("/verdicts/V-test1234/email")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
        }

        @Test
        @DisplayName("만료된 verdictId → 404")
        void verdictNotFound() throws Exception {
            when(verdictService.sendResultEmail(eq("V-expired"), anyString()))
                .thenThrow(BaseException.of(VerdictErrorCode.VERDICT_NOT_FOUND));

            String body = """
                {
                  "email": "user@example.com"
                }
                """;

            mockMvc.perform(post("/verdicts/V-expired/email")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("VERDICT_003"));
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        void invalidEmail() throws Exception {
            String body = """
                {
                  "email": "not-an-email"
                }
                """;

            mockMvc.perform(post("/verdicts/V-test1234/email")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일 빈 값 → 400")
        void emptyEmail() throws Exception {
            String body = """
                {
                  "email": ""
                }
                """;

            mockMvc.perform(post("/verdicts/V-test1234/email")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일 발송 실패 → 500")
        void emailSendFailed() throws Exception {
            when(verdictService.sendResultEmail(anyString(), anyString()))
                .thenThrow(BaseException.of(VerdictErrorCode.EMAIL_SEND_FAILED));

            String body = """
                {
                  "email": "user@example.com"
                }
                """;

            mockMvc.perform(post("/verdicts/V-test1234/email")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isInternalServerError());
        }
    }

    // ═══════════════════════════════════════
    // HTTP 메서드 테스트
    // ═══════════════════════════════════════

    @Test
    @DisplayName("GET /verdicts → 405 Method Not Allowed")
    void getNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/verdicts"))
            .andExpect(status().isMethodNotAllowed());
    }
}
