package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteDetailResponse;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResponse;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResultResponse;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.enums.HouseholdType;
import aichallenge.getmyhome.verdict.enums.MaritalStatus;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.rule.ProductRuleParams;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinancingRouteServiceTest {

    private FinancingRouteService service;
    private RuleVersion rule;

    @BeforeEach
    void setUp() {
        service = new FinancingRouteService();
        rule = buildDefaultRule();
    }

    // ── 테스트 헬퍼 ──

    private RuleVersion buildDefaultRule() {
        RuleVersion r = new RuleVersion();
        r.setAssumptionSetId("A-2026-08");
        r.setNetAssetLimit(51100);
        r.setSingleHeadMinAge(30);
        r.setSingleHeadLoanCap(15000);
        r.setSingleHeadFirstLoanCap(20000);
        r.setBankRateMin(4.0);
        r.setBankRateMax(5.0);
        r.setStressDsrRate(5.0);
        r.setBankDsrPercent(40);
        r.setLoanTermYears(30);
        r.setYouthDreamMaxAge(39);
        r.setSubscriptionMinMonths(12);

        r.setProducts(Map.of(
            "DIDIMDOL_GENERAL", buildProduct(6000, 50000, 20000, 70, 60, 3.0),
            "DIDIMDOL_FIRST", buildProduct(7000, 50000, 24000, 80, 60, 2.7),
            "DIDIMDOL_NEWLYWED", buildProduct(8500, 60000, 32000, 70, 60, 2.7),
            "YOUTH_DREAM_SINGLE", buildProduct(7000, 60000, 30000, 70, 60, 2.2),
            "YOUTH_DREAM_NEWLYWED", buildProduct(10000, 60000, 40000, 70, 60, 2.2),
            "BANK_MORTGAGE", buildProduct(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 70, 0, 4.5)
        ));
        return r;
    }

    private ProductRuleParams buildProduct(int income, int housing, int cap, int ltv, int dti, double rate) {
        ProductRuleParams p = new ProductRuleParams();
        p.setIncomeLimit(income);
        p.setHousingPriceLimit(housing);
        p.setLoanCap(cap);
        p.setBaseLtvPercent(ltv);
        p.setDtiPercent(dti);
        p.setPolicyRate(rate);
        return p;
    }

    /** 1단계 필수만 입력한 미혼 사용자 */
    private UserConditionRequest singleStep1(int income, int cash, String birth) {
        return new UserConditionRequest(
            income, cash, LocalDate.parse(birth), MaritalStatus.SINGLE, true, false,
            null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    /** 2단계까지 입력한 미혼 사용자 (세대주, 무주택, 순자산, 생애최초) */
    private UserConditionRequest singleStep2Full(int income, int cash, String birth,
                                                   HouseholdType household, int netAsset,
                                                   boolean firstTime, LocalDate subOpened) {
        return new UserConditionRequest(
            income, cash, LocalDate.parse(birth), MaritalStatus.SINGLE, true, false,
            null, 100, null, null, null, null,
            household, true, netAsset, null, firstTime,
            subOpened != null ? new UserConditionRequest.SubscriptionAccount("YOUTH_DREAM", subOpened, 24, 600) : null
        );
    }

    /** 기혼 사용자 */
    private UserConditionRequest married(int income, int cash, int spouseIncome, HouseholdType household, int netAsset) {
        return new UserConditionRequest(
            income, cash, LocalDate.parse("1990-01-01"), MaritalStatus.MARRIED, true, false,
            null, 100, spouseIncome, null, null, null,
            household, true, netAsset, null, true,
            new UserConditionRequest.SubscriptionAccount("YOUTH_DREAM", LocalDate.parse("2023-01-01"), 24, 600)
        );
    }

    // ═══════════════════════════════════════
    // evaluateAndCache 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("evaluateAndCache - 토큰 발급 및 캐시")
    class EvaluateAndCache {

        @Test
        @DisplayName("토큰 발급 후 캐시에서 조건 조회 가능")
        void tokenIssuedAndCached() {
            UserConditionRequest user = singleStep1(4000, 5000, "1995-03-15");
            FinancingRouteResultResponse result = service.evaluateAndCache(user, rule);

            assertThat(result.conditionToken()).startsWith("CT-");
            assertThat(result.routes()).hasSize(6);

            UserConditionRequest cached = service.getCondition(result.conditionToken());
            assertThat(cached).isEqualTo(user);
        }

        @Test
        @DisplayName("만료/존재하지 않는 토큰 조회 시 null 반환")
        void expiredTokenReturnsNull() {
            assertThat(service.getCondition("CT-nonexistent")).isNull();
        }
    }

    // ═══════════════════════════════════════
    // 디딤돌 대출 (일반) 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("디딤돌 대출 - 일반")
    class DidimdolGeneral {

        @Test
        @DisplayName("자격 충족 - 세대주, 무주택, 소득·자산 기준 이내")
        void eligible() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, false, null);

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, holds);

            FinancingRouteResponse general = findRoute(routes, "DIDIMDOL_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.OK);
            assertThat(general.limitMax()).isGreaterThan(0);
        }

        @Test
        @DisplayName("소득 초과 시 목록에서 제외")
        void incomeExceeded() {
            UserConditionRequest user = singleStep2Full(7000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("분양가 초과 시 목록에서 제외")
        void salePriceExceeded() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 55000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("기혼인데 배우자 소득 미입력 시 HOLD")
        void marriedWithoutSpouseIncome() {
            UserConditionRequest user = new UserConditionRequest(
                4000, 5000, LocalDate.parse("1990-01-01"), MaritalStatus.MARRIED, true, false,
                null, null, null, null, null, null, null, null, null, null, null, null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, holds);

            FinancingRouteResponse general = findRoute(routes, "DIDIMDOL_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(general.reasonCode()).isEqualTo("NEED_SPOUSE_INCOME");
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("NEED_SPOUSE_INCOME"));
        }

        @Test
        @DisplayName("세대 구성 미입력 시 HOLD")
        void missingHouseholdInfo() {
            UserConditionRequest user = singleStep1(4000, 5000, "1990-01-01");

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, holds);

            FinancingRouteResponse general = findRoute(routes, "DIDIMDOL_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(general.reasonCode()).isEqualTo("NEED_HOUSEHOLD_INFO");
        }

        @Test
        @DisplayName("유주택자 제외")
        void notHomeless() {
            UserConditionRequest user = new UserConditionRequest(
                4000, 5000, LocalDate.parse("1990-01-01"), MaritalStatus.SINGLE, false, false,
                null, null, null, null, null, null,
                HouseholdType.HEAD, true, 30000, null, false, null
            );

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("순자산 초과 시 제외")
        void netAssetExceeded() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 60000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("세대원은 제외")
        void memberExcluded() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.MEMBER, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("미혼 단독세대주 30세 미만 제외")
        void singleHeadUnder30() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "2000-01-01",
                HouseholdType.SINGLE_HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_GENERAL")).isNull();
        }

        @Test
        @DisplayName("미혼 단독세대주 30세 이상 - 한도 15000 적용")
        void singleHeadOver30() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.SINGLE_HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            FinancingRouteResponse general = findRoute(routes, "DIDIMDOL_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.limitMax()).isLessThanOrEqualTo(15000);
        }

        @Test
        @DisplayName("salePrice null인 경우 LTV 미적용 (추정 모드)")
        void nullSalePrice() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, null, rule, new ArrayList<>());
            FinancingRouteResponse general = findRoute(routes, "DIDIMDOL_GENERAL");
            assertThat(general).isNotNull();
            assertThat(general.status()).isEqualTo(VerdictStatus.OK);
        }
    }

    // ═══════════════════════════════════════
    // 디딤돌 대출 (생애최초) 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("디딤돌 대출 - 생애최초")
    class DidimdolFirst {

        @Test
        @DisplayName("자격 충족")
        void eligible() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, true, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            FinancingRouteResponse first = findRoute(routes, "DIDIMDOL_FIRST");
            assertThat(first).isNotNull();
            assertThat(first.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("생애최초 아닌 경우 제외")
        void notFirstTime() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, false, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_FIRST")).isNull();
        }

        @Test
        @DisplayName("생애최초 여부 미입력 시 HOLD")
        void firstTimeBuyerNull() {
            UserConditionRequest user = new UserConditionRequest(
                5000, 5000, LocalDate.parse("1990-01-01"), MaritalStatus.SINGLE, true, false,
                null, null, null, null, null, null,
                HouseholdType.HEAD, true, 30000, null, null, null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, holds);
            FinancingRouteResponse first = findRoute(routes, "DIDIMDOL_FIRST");
            assertThat(first).isNotNull();
            assertThat(first.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(first.reasonCode()).isEqualTo("NEED_FIRST_TIME_INFO");
        }
    }

    // ═══════════════════════════════════════
    // 디딤돌 대출 (신혼부부) 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("디딤돌 대출 - 신혼부부")
    class DidimdolNewlywed {

        @Test
        @DisplayName("자격 충족")
        void eligible() {
            UserConditionRequest user = married(4000, 5000, 3000, HouseholdType.HEAD, 30000);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            FinancingRouteResponse nw = findRoute(routes, "DIDIMDOL_NEWLYWED");
            assertThat(nw).isNotNull();
            assertThat(nw.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("미혼자 제외")
        void singleExcluded() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, true, null);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_NEWLYWED")).isNull();
        }

        @Test
        @DisplayName("배우자 소득 미입력 시 HOLD")
        void spouseIncomeNull() {
            UserConditionRequest user = new UserConditionRequest(
                4000, 5000, LocalDate.parse("1990-01-01"), MaritalStatus.MARRIED, true, false,
                null, null, null, null, null, null, null, null, null, null, null, null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, holds);
            FinancingRouteResponse nw = findRoute(routes, "DIDIMDOL_NEWLYWED");
            assertThat(nw).isNotNull();
            assertThat(nw.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(nw.reasonCode()).isEqualTo("NEED_NEWLYWED_INFO");
        }

        @Test
        @DisplayName("부부합산 소득 초과 시 제외")
        void combinedIncomeExceeded() {
            UserConditionRequest user = married(5000, 5000, 5000, HouseholdType.HEAD, 30000);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "DIDIMDOL_NEWLYWED")).isNull();
        }
    }

    // ═══════════════════════════════════════
    // 청년주택드림 (미혼) 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("청년주택드림 - 미혼")
    class YouthDreamSingle {

        @Test
        @DisplayName("자격 충족 - 39세 이하, 12개월 이상 가입")
        void eligible() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1995-01-01",
                HouseholdType.HEAD, 30000, true, LocalDate.now().minusMonths(24));

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            FinancingRouteResponse yd = findRoute(routes, "YOUTH_DREAM_SINGLE");
            assertThat(yd).isNotNull();
            assertThat(yd.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("40세 이상 제외")
        void ageExceeded() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1980-01-01",
                HouseholdType.HEAD, 30000, true, LocalDate.now().minusMonths(24));

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "YOUTH_DREAM_SINGLE")).isNull();
        }

        @Test
        @DisplayName("기혼자 제외")
        void marriedExcluded() {
            UserConditionRequest user = married(5000, 5000, 3000, HouseholdType.HEAD, 30000);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "YOUTH_DREAM_SINGLE")).isNull();
        }

        @Test
        @DisplayName("청약통장 미입력 시 HOLD")
        void subscriptionNull() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1995-01-01",
                HouseholdType.HEAD, 30000, true, null);

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, holds);
            FinancingRouteResponse yd = findRoute(routes, "YOUTH_DREAM_SINGLE");
            assertThat(yd).isNotNull();
            assertThat(yd.status()).isEqualTo(VerdictStatus.HOLD);
        }

        @Test
        @DisplayName("청약통장 가입 기간 12개월 미만 제외")
        void subscriptionTooShort() {
            UserConditionRequest user = singleStep2Full(5000, 5000, "1995-01-01",
                HouseholdType.HEAD, 30000, true, LocalDate.now().minusMonths(6));

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "YOUTH_DREAM_SINGLE")).isNull();
        }
    }

    // ═══════════════════════════════════════
    // 청년주택드림 (신혼부부) 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("청년주택드림 - 신혼부부")
    class YouthDreamNewlywed {

        @Test
        @DisplayName("자격 충족")
        void eligible() {
            UserConditionRequest user = married(4000, 5000, 3000, HouseholdType.HEAD, 30000);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            FinancingRouteResponse yd = findRoute(routes, "YOUTH_DREAM_NEWLYWED");
            assertThat(yd).isNotNull();
            assertThat(yd.status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("미혼자 제외")
        void singleExcluded() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1995-01-01",
                HouseholdType.HEAD, 30000, true, LocalDate.now().minusMonths(24));

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "YOUTH_DREAM_NEWLYWED")).isNull();
        }

        @Test
        @DisplayName("배우자 소득 또는 청약통장 미입력 시 HOLD")
        void missingInfo() {
            UserConditionRequest user = new UserConditionRequest(
                4000, 5000, LocalDate.parse("1990-01-01"), MaritalStatus.MARRIED, true, false,
                null, null, null, null, null, null, null, null, null, null, null, null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, holds);
            FinancingRouteResponse yd = findRoute(routes, "YOUTH_DREAM_NEWLYWED");
            assertThat(yd).isNotNull();
            assertThat(yd.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(yd.reasonCode()).isEqualTo("NEED_YOUTH_NEWLYWED_INFO");
        }

        @Test
        @DisplayName("부부합산 소득 초과 시 제외")
        void combinedIncomeExceeded() {
            UserConditionRequest user = married(6000, 5000, 5000, HouseholdType.HEAD, 30000);

            List<FinancingRouteResponse> routes = service.evaluate(user, 50000, rule, new ArrayList<>());
            assertThat(findRoute(routes, "YOUTH_DREAM_NEWLYWED")).isNull();
        }
    }

    // ═══════════════════════════════════════
    // 시중은행 주택담보대출 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("시중은행 주택담보대출")
    class BankMortgage {

        @Test
        @DisplayName("항상 OK 반환 (자격 필터 없음)")
        void alwaysOk() {
            UserConditionRequest user = singleStep1(4000, 5000, "1995-01-01");

            List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
            FinancingRouteResponse bank = findRoute(routes, "BANK_MORTGAGE");
            assertThat(bank).isNotNull();
            assertThat(bank.status()).isEqualTo(VerdictStatus.OK);
            assertThat(bank.limitMin()).isNotNull();
            assertThat(bank.limitMax()).isNotNull();
            assertThat(bank.limitMin()).isLessThanOrEqualTo(bank.limitMax());
        }

        @Test
        @DisplayName("DSR 기반 한도 계산 - 기존 대출 있으면 한도 감소")
        void existingLoanReducesLimit() {
            UserConditionRequest noLoan = singleStep1(4000, 5000, "1995-01-01");
            UserConditionRequest withLoan = new UserConditionRequest(
                4000, 5000, LocalDate.parse("1995-01-01"), MaritalStatus.SINGLE, true, false,
                null, null, null, null, 50, null, null, null, null, null, null, null
            );

            List<FinancingRouteResponse> noLoanRoutes = service.evaluate(noLoan, 40000, rule, new ArrayList<>());
            List<FinancingRouteResponse> withLoanRoutes = service.evaluate(withLoan, 40000, rule, new ArrayList<>());

            int noLoanMax = findRoute(noLoanRoutes, "BANK_MORTGAGE").limitMax();
            int withLoanMax = findRoute(withLoanRoutes, "BANK_MORTGAGE").limitMax();

            assertThat(withLoanMax).isLessThan(noLoanMax);
        }

        @Test
        @DisplayName("salePrice null이면 LTV 미적용, bindingFactor=DSR")
        void nullSalePriceDsr() {
            UserConditionRequest user = singleStep1(4000, 5000, "1995-01-01");

            List<FinancingRouteResponse> routes = service.evaluate(user, null, rule, new ArrayList<>());
            FinancingRouteResponse bank = findRoute(routes, "BANK_MORTGAGE");
            assertThat(bank.bindingFactor()).isEqualTo("DSR");
        }

        @Test
        @DisplayName("salePrice가 작으면 LTV가 binding factor")
        void lowSalePriceLtv() {
            UserConditionRequest user = singleStep1(10000, 5000, "1995-01-01");

            List<FinancingRouteResponse> routes = service.evaluate(user, 10000, rule, new ArrayList<>());
            FinancingRouteResponse bank = findRoute(routes, "BANK_MORTGAGE");
            // LTV = 10000 * 70% = 7000, DSR 기반은 훨씬 클 것이므로 LTV가 binding
            assertThat(bank.bindingFactor()).isEqualTo("LTV");
            assertThat(bank.limitMax()).isEqualTo(7000);
        }
    }

    // ═══════════════════════════════════════
    // evaluateWithReasons 상세 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("evaluateWithReasons - 상세 사유 포함")
    class EvaluateWithReasons {

        @Test
        @DisplayName("6개 상품 모두 반환")
        void allSixProducts() {
            UserConditionRequest user = singleStep1(4000, 5000, "1995-01-01");
            List<FinancingRouteDetailResponse> results = service.evaluateWithReasons(user, 40000, rule);
            assertThat(results).hasSize(6);
        }

        @Test
        @DisplayName("소득 초과 시 BLOCK + 탈락 사유 포함")
        void incomeExceededBlock() {
            UserConditionRequest user = singleStep1(8000, 5000, "1995-01-01");
            List<FinancingRouteDetailResponse> results = service.evaluateWithReasons(user, 40000, rule);

            FinancingRouteDetailResponse general = results.stream()
                .filter(r -> r.productCode().equals("DIDIMDOL_GENERAL")).findFirst().orElseThrow();
            assertThat(general.status()).isEqualTo(VerdictStatus.BLOCK);
            assertThat(general.eligible()).isFalse();
            assertThat(general.ineligibleReason()).contains("소득");
        }

        @Test
        @DisplayName("OK 상품은 eligible=true, limitMax > 0")
        void okProductEligible() {
            UserConditionRequest user = singleStep2Full(4000, 5000, "1990-01-01",
                HouseholdType.HEAD, 30000, true, LocalDate.now().minusMonths(24));

            List<FinancingRouteDetailResponse> results = service.evaluateWithReasons(user, 40000, rule);

            FinancingRouteDetailResponse general = results.stream()
                .filter(r -> r.productCode().equals("DIDIMDOL_GENERAL")).findFirst().orElseThrow();
            assertThat(general.status()).isEqualTo(VerdictStatus.OK);
            assertThat(general.eligible()).isTrue();
            assertThat(general.limitMax()).isGreaterThan(0);
        }

        @Test
        @DisplayName("HOLD 상품은 holdReasonCode + holdMessage 포함")
        void holdProductHasReason() {
            UserConditionRequest user = singleStep1(4000, 5000, "1995-01-01");
            List<FinancingRouteDetailResponse> results = service.evaluateWithReasons(user, 40000, rule);

            FinancingRouteDetailResponse general = results.stream()
                .filter(r -> r.productCode().equals("DIDIMDOL_GENERAL")).findFirst().orElseThrow();
            assertThat(general.status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(general.holdReasonCode()).isNotNull();
            assertThat(general.holdMessage()).isNotNull();
        }
    }

    // ═══════════════════════════════════════
    // 복합 시나리오
    // ═══════════════════════════════════════

    @Test
    @DisplayName("1단계만 입력 - 시중은행은 OK, 정책대출은 HOLD 또는 BLOCK")
    void step1OnlyMixedResults() {
        UserConditionRequest user = singleStep1(4000, 5000, "1995-01-01");

        List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
        FinancingRouteResponse bank = findRoute(routes, "BANK_MORTGAGE");
        assertThat(bank.status()).isEqualTo(VerdictStatus.OK);

        // 정책대출은 추가 정보가 필요하므로 HOLD
        routes.stream()
            .filter(r -> !r.productCode().equals("BANK_MORTGAGE"))
            .forEach(r -> assertThat(r.status()).isIn(VerdictStatus.HOLD, VerdictStatus.OK));
    }

    @Test
    @DisplayName("기혼 + 2단계 전부 입력 - 다수 상품 자격 충족")
    void marriedFullStep2MultipleEligible() {
        UserConditionRequest user = married(4000, 5000, 3000, HouseholdType.HEAD, 30000);

        List<FinancingRouteResponse> routes = service.evaluate(user, 40000, rule, new ArrayList<>());
        long okCount = routes.stream().filter(r -> r.status() == VerdictStatus.OK).count();
        assertThat(okCount).isGreaterThanOrEqualTo(3); // 최소 일반, 신혼, 청년주택드림 신혼, 은행
    }

    // ── 유틸리티 ──

    private FinancingRouteResponse findRoute(List<FinancingRouteResponse> routes, String productCode) {
        return routes.stream()
            .filter(r -> productCode.equals(r.productCode()))
            .findFirst()
            .orElse(null);
    }
}
