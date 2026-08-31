package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.dto.res.SubscriptionEligibilityResponse;
import aichallenge.getmyhome.verdict.enums.MaritalStatus;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionEligibilityServiceTest {

    private SubscriptionEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionEligibilityService();
    }

    // ── 헬퍼 ──

    private UserConditionRequest user(MaritalStatus marital, boolean homeless,
                                       Integer spouseIncome, Boolean firstTimeBuyer,
                                       UserConditionRequest.SubscriptionAccount sub) {
        return new UserConditionRequest(
            4000, 5000, LocalDate.parse("1995-01-01"), marital, homeless, false,
            null, null, spouseIncome, null, null, null, null, null, null, null, firstTimeBuyer, sub
        );
    }

    // ═══════════════════════════════════════
    // 신혼부부 특별공급
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("SUB_NEWLYWED - 신혼부부 특별공급")
    class SubNewlywed {

        @Test
        @DisplayName("기혼 + 무주택 + 배우자 소득 입력 → OK")
        void eligible() {
            UserConditionRequest u = user(MaritalStatus.MARRIED, true, 3000, null, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            SubscriptionEligibilityResponse nw = findType(results, "SUB_NEWLYWED");
            assertThat(nw).isNotNull();
            assertThat(nw.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("미혼 → 결과에 미포함")
        void singleExcluded() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, null, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_NEWLYWED")).isNull();
        }

        @Test
        @DisplayName("유주택 → 결과에 미포함")
        void notHomeless() {
            UserConditionRequest u = user(MaritalStatus.MARRIED, false, 3000, null, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_NEWLYWED")).isNull();
        }

        @Test
        @DisplayName("배우자 소득 미입력 → HOLD")
        void spouseIncomeNull() {
            UserConditionRequest u = user(MaritalStatus.MARRIED, true, null, null, null);
            List<HoldResponse> holds = new ArrayList<>();
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, holds);

            SubscriptionEligibilityResponse nw = findType(results, "SUB_NEWLYWED");
            assertThat(nw).isNotNull();
            assertThat(nw.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(nw.reasonCode()).isEqualTo("NEED_SPOUSE_INCOME");
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("NEED_SPOUSE_INCOME"));
        }

        @Test
        @DisplayName("ENGAGED 상태도 신혼부부 대상")
        void engagedIncluded() {
            UserConditionRequest u = user(MaritalStatus.ENGAGED, true, 3000, null, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_NEWLYWED")).isNotNull();
        }
    }

    // ═══════════════════════════════════════
    // 생애최초 특별공급
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("SUB_FIRST - 생애최초 특별공급")
    class SubFirst {

        @Test
        @DisplayName("무주택 + 생애최초 → OK")
        void eligible() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, true, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            SubscriptionEligibilityResponse first = findType(results, "SUB_FIRST");
            assertThat(first).isNotNull();
            assertThat(first.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("생애최초 아닌 경우 → 결과에 미포함")
        void notFirstTime() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, false, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_FIRST")).isNull();
        }

        @Test
        @DisplayName("유주택 → 결과에 미포함")
        void notHomeless() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, false, null, true, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_FIRST")).isNull();
        }

        @Test
        @DisplayName("생애최초 여부 미입력 → HOLD")
        void firstTimeBuyerNull() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, null, null);
            List<HoldResponse> holds = new ArrayList<>();
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, holds);

            SubscriptionEligibilityResponse first = findType(results, "SUB_FIRST");
            assertThat(first).isNotNull();
            assertThat(first.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(first.reasonCode()).isEqualTo("NEED_FIRST_TIME_BUYER_INFO");
        }
    }

    // ═══════════════════════════════════════
    // 일반공급
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("SUB_GENERAL - 일반공급")
    class SubGeneral {

        @Test
        @DisplayName("무주택 + 청약통장 있음 → OK")
        void eligible() {
            UserConditionRequest.SubscriptionAccount sub =
                new UserConditionRequest.SubscriptionAccount("YOUTH_DREAM", LocalDate.parse("2023-01-01"), 24, 600);
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, null, sub);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            SubscriptionEligibilityResponse general = findType(results, "SUB_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("유주택 → 결과에 미포함")
        void notHomeless() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, false, null, null, null);
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

            assertThat(findType(results, "SUB_GENERAL")).isNull();
        }

        @Test
        @DisplayName("청약통장 미입력 → HOLD")
        void subscriptionNull() {
            UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, null, null);
            List<HoldResponse> holds = new ArrayList<>();
            List<SubscriptionEligibilityResponse> results = service.evaluate(u, holds);

            SubscriptionEligibilityResponse general = findType(results, "SUB_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(general.reasonCode()).isEqualTo("NEED_SUBSCRIPTION_INFO");
        }
    }

    // ═══════════════════════════════════════
    // 복합 시나리오
    // ═══════════════════════════════════════

    @Test
    @DisplayName("기혼 + 무주택 + 전체 입력 → 3가지 유형 모두 결과 포함")
    void allTypesIncluded() {
        UserConditionRequest.SubscriptionAccount sub =
            new UserConditionRequest.SubscriptionAccount("YOUTH_DREAM", LocalDate.parse("2023-01-01"), 24, 600);
        UserConditionRequest u = user(MaritalStatus.MARRIED, true, 3000, true, sub);

        List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

        assertThat(findType(results, "SUB_NEWLYWED")).isNotNull();
        assertThat(findType(results, "SUB_FIRST")).isNotNull();
        assertThat(findType(results, "SUB_GENERAL")).isNotNull();
    }

    @Test
    @DisplayName("미혼 + 무주택 + 생애최초 + 통장 → SUB_FIRST, SUB_GENERAL만 포함")
    void singleWithFirstAndGeneral() {
        UserConditionRequest.SubscriptionAccount sub =
            new UserConditionRequest.SubscriptionAccount("YOUTH_DREAM", LocalDate.parse("2023-01-01"), 24, 600);
        UserConditionRequest u = user(MaritalStatus.SINGLE, true, null, true, sub);

        List<SubscriptionEligibilityResponse> results = service.evaluate(u, new ArrayList<>());

        assertThat(findType(results, "SUB_NEWLYWED")).isNull();
        assertThat(findType(results, "SUB_FIRST")).isNotNull();
        assertThat(findType(results, "SUB_GENERAL")).isNotNull();
    }

    // ── 유틸 ──

    private SubscriptionEligibilityResponse findType(List<SubscriptionEligibilityResponse> list, String type) {
        return list.stream().filter(r -> type.equals(r.type())).findFirst().orElse(null);
    }
}