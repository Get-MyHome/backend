package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResponse;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.dto.res.RouteBalanceComparison;
import aichallenge.getmyhome.verdict.dto.res.StageVerdictResponse;
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

class StageCalculationServiceTest {

    private StageCalculationService service;

    @BeforeEach
    void setUp() {
        service = new StageCalculationService();
    }

    // ── 헬퍼 ──

    private UserConditionRequest user(int cash, Integer monthlySaving) {
        return new UserConditionRequest(
            4000, cash, LocalDate.parse("1995-01-01"), MaritalStatus.SINGLE, true, false,
            null, monthlySaving, null, null, null, null, null, null, null, null, null, null
        );
    }

    private PdfAnalysisResult analysis(double down, double interim, double interimLoan, String balanceDate) {
        return new PdfAnalysisResult(
            "TEST-001",
            new PdfAnalysisResult.PaymentSchedule(down, interim, interimLoan, null, balanceDate),
            null
        );
    }

    private PdfAnalysisResult analysisWithCosts(double down, double interim, double interimLoan,
                                                  String balanceDate, int additionalCost) {
        return new PdfAnalysisResult(
            "TEST-001",
            new PdfAnalysisResult.PaymentSchedule(down, interim, interimLoan, null, balanceDate),
            List.of(new PdfAnalysisResult.AdditionalCost("BALCONY_EXTENSION", additionalCost, "BALANCE", true))
        );
    }

    private List<FinancingRouteResponse> routesWithLoan(int maxLoan) {
        return List.of(new FinancingRouteResponse(
            "DIDIMDOL_GENERAL", VerdictStatus.OK, null, maxLoan, null, null,
            List.of("EV-RULE-002")
        ));
    }

    // ═══════════════════════════════════════
    // calculate 기본 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("calculate - 구간 판정")
    class Calculate {

        @Test
        @DisplayName("analysisResult null → HOLD + 빈 리스트 반환")
        void nullAnalysis() {
            UserConditionRequest u = user(50000, null);
            List<HoldResponse> holds = new ArrayList<>();

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, null, routesWithLoan(20000), holds);

            assertThat(results).isEmpty();
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("COMPLEX_NOT_ANALYZED"));
        }

        @Test
        @DisplayName("paymentSchedule null → HOLD + 빈 리스트 반환")
        void nullPaymentSchedule() {
            PdfAnalysisResult analysis = new PdfAnalysisResult("TEST-001", null, null);
            List<HoldResponse> holds = new ArrayList<>();

            List<StageVerdictResponse> results = service.calculate(
                user(50000, null), 100000, analysis, routesWithLoan(20000), holds);

            assertThat(results).isEmpty();
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("COMPLEX_NOT_ANALYZED"));
        }

        @Test
        @DisplayName("모든 구간 OK - 충분한 현금 + 대출")
        void allStagesOk() {
            // 분양가 100000, 계약금 10%, 중도금 60%(전액 대출), 잔금 30%
            // 계약금 = 10000, 중도금 자비 = 0, 잔금 = 30000
            // 현금 50000이면: 계약금 OK(남은 40000), 중도금 OK(남은 40000), 잔금(30000 < 40000+20000) OK
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            assertThat(results).hasSize(3);
            assertThat(results.get(0).stage()).isEqualTo("CONTRACT");
            assertThat(results.get(0).status()).isEqualTo(VerdictStatus.OK);
            assertThat(results.get(1).stage()).isEqualTo("INTERIM");
            assertThat(results.get(1).status()).isEqualTo(VerdictStatus.OK);
            assertThat(results.get(2).stage()).isEqualTo("BALANCE");
            assertThat(results.get(2).status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("계약금 부족 + 저축 미입력 → HOLD")
        void contractShortNoSaving() {
            // 분양가 100000, 계약금 10% = 10000, 현금 5000
            UserConditionRequest u = user(5000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<HoldResponse> holds = new ArrayList<>();
            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), holds);

            assertThat(results.get(0).stage()).isEqualTo("CONTRACT");
            assertThat(results.get(0).status()).isEqualTo(VerdictStatus.HOLD);
            assertThat(results.get(0).gap()).isEqualTo(5000);
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("NEED_MONTHLY_SAVING"));
        }

        @Test
        @DisplayName("계약금 부족 + 저축 가능 + 기한 없음 → GAP")
        void contractGapNoDeadline() {
            UserConditionRequest u = user(5000, 100);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            assertThat(results.get(0).status()).isEqualTo(VerdictStatus.GAP);
            assertThat(results.get(0).gap()).isEqualTo(5000);
            assertThat(results.get(0).monthsNeeded()).isEqualTo(50); // 5000/100 = 50개월
            assertThat(results.get(0).scenarios()).isNotEmpty();
        }

        @Test
        @DisplayName("잔금 부족 + 기한 내 저축 해소 불가 → BLOCK")
        void balanceBlockDeadlineTooShort() {
            // 분양가 100000, 계약금 10%, 중도금 60%(전액대출), 잔금 30% = 30000
            // 현금 10000, 대출 10000 → 가용 20000, 부족 10000
            // 월저축 100 → 100개월 필요, 잔금일까지 6개월 → BLOCK
            String balanceDate = LocalDate.now().plusMonths(6).toString();
            UserConditionRequest u = user(10000, 100);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, balanceDate);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(10000), new ArrayList<>());

            assertThat(results.get(2).stage()).isEqualTo("BALANCE");
            assertThat(results.get(2).status()).isEqualTo(VerdictStatus.BLOCK);
        }

        @Test
        @DisplayName("잔금 부족 + 기한 내 저축 해소 가능 → GAP")
        void balanceGapWithinDeadline() {
            // 잔금 = 30000, 현금 after prior = 0, 대출 28000, 부족 2000
            // 월저축 200 → 10개월, 잔금일까지 24개월 → GAP
            String balanceDate = LocalDate.now().plusMonths(24).toString();
            UserConditionRequest u = user(10000, 200);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, balanceDate);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(28000), new ArrayList<>());

            assertThat(results.get(2).stage()).isEqualTo("BALANCE");
            assertThat(results.get(2).status()).isEqualTo(VerdictStatus.GAP);
            assertThat(results.get(2).monthsNeeded()).isLessThanOrEqualTo(24);
        }

        @Test
        @DisplayName("추가 비용(옵션) 잔금에 합산")
        void additionalCostsAddedToBalance() {
            // 잔금 = 30000 + 추가비용 5000 = 35000
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysisWithCosts(0.1, 0.6, 1.0, null, 5000);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            // 잔금 필요액 = 30000 + 5000 = 35000
            assertThat(results.get(2).required()).isEqualTo(35000);
        }

        @Test
        @DisplayName("중도금 자비 부담 계산 (중도금 대출 비율 50%)")
        void interimPartialLoan() {
            // 분양가 100000, 중도금 60% = 60000, 중도금 대출 50% = 30000, 자비 30000
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 0.5, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            // 계약금 10000, 남은현금 40000
            // 중도금 자비 30000, 남은현금 10000
            assertThat(results.get(1).required()).isEqualTo(30000);
            assertThat(results.get(1).status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("대출 없는 경우 잔금 판정")
        void noFinancingRoutes() {
            // 대출 0, 현금으로만 잔금 감당
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, List.of(), new ArrayList<>());

            // 잔금 30000, 남은 현금 40000, 대출 0 → OK
            assertThat(results.get(2).status()).isEqualTo(VerdictStatus.OK);
        }
    }

    // ═══════════════════════════════════════
    // calculateRouteComparisons 테스트
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("calculateRouteComparisons - 상품별 잔금 비교")
    class RouteComparisons {

        @Test
        @DisplayName("analysisResult null → 빈 리스트")
        void nullAnalysis() {
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(50000, null), 100000, null, routesWithLoan(20000));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("OK인 상품만 비교 대상에 포함")
        void onlyOkRoutes() {
            List<FinancingRouteResponse> routes = List.of(
                new FinancingRouteResponse("DIDIMDOL_GENERAL", VerdictStatus.OK, null, 20000, null, null, List.of()),
                new FinancingRouteResponse("DIDIMDOL_FIRST", VerdictStatus.HOLD, null, null, null, "NEED_FIRST_TIME_INFO", List.of()),
                new FinancingRouteResponse("BANK_MORTGAGE", VerdictStatus.OK, 15000, 25000, "DSR", null, List.of())
            );

            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(50000, null), 100000, a, routes);

            assertThat(result).hasSize(2);
            assertThat(result.stream().map(RouteBalanceComparison::productCode))
                .containsExactlyInAnyOrder("DIDIMDOL_GENERAL", "BANK_MORTGAGE");
        }

        @Test
        @DisplayName("잔금 충분 → OK")
        void sufficientBalance() {
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(50000, null), 100000, a, routesWithLoan(30000));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(VerdictStatus.OK);
            assertThat(result.get(0).gap()).isNull();
        }

        @Test
        @DisplayName("잔금 부족 + 저축 가능 → GAP with scenario")
        void balanceGap() {
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);
            // 잔금 30000, 현금 after prior 0, 대출 20000 → 부족 10000
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(10000, 200), 100000, a, routesWithLoan(20000));

            assertThat(result.get(0).status()).isEqualTo(VerdictStatus.GAP);
            assertThat(result.get(0).gap()).isGreaterThan(0);
            assertThat(result.get(0).scenario()).contains("저축");
        }

        @Test
        @DisplayName("잔금 부족 + 저축 불가 → BLOCK")
        void balanceBlock() {
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(10000, null), 100000, a, routesWithLoan(10000));

            assertThat(result.get(0).status()).isEqualTo(VerdictStatus.BLOCK);
        }

        @Test
        @DisplayName("잔금 부족 + 저축 가능하나 기한 초과 → BLOCK")
        void balanceBlockDeadline() {
            String balanceDate = LocalDate.now().plusMonths(3).toString();
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, balanceDate);

            // 잔금 30000, 가용 20000, 부족 10000
            // 월저축 100 → 100개월 필요, 기한 3개월 → BLOCK
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(10000, 100), 100000, a, routesWithLoan(10000));

            assertThat(result.get(0).status()).isEqualTo(VerdictStatus.BLOCK);
            assertThat(result.get(0).monthsNeeded()).isGreaterThan(result.get(0).monthsAvailable());
        }

        @Test
        @DisplayName("productName이 올바르게 매핑됨")
        void productNameMapping() {
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);
            List<RouteBalanceComparison> result = service.calculateRouteComparisons(
                user(50000, null), 100000, a, routesWithLoan(30000));

            assertThat(result.get(0).productName()).isEqualTo("디딤돌 대출 - 일반");
        }
    }

    // ═══════════════════════════════════════
    // 엣지 케이스
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("salePrice 0 → 모든 구간 required = 0, 전부 OK")
        void zeroSalePrice() {
            UserConditionRequest u = user(0, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 0, a, routesWithLoan(0), new ArrayList<>());

            assertThat(results).allMatch(r -> r.status() == VerdictStatus.OK);
        }

        @Test
        @DisplayName("현금 0 + 대출 0 + 분양가 있음 → BLOCK 또는 HOLD")
        void zeroCashZeroLoan() {
            UserConditionRequest u = user(0, 0);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, List.of(), new ArrayList<>());

            assertThat(results.get(0).status()).isEqualTo(VerdictStatus.BLOCK);
        }

        @Test
        @DisplayName("잘못된 balanceDueDate → HOLD 추가, 판정은 계속")
        void invalidBalanceDate() {
            PdfAnalysisResult a = new PdfAnalysisResult(
                "TEST-001",
                new PdfAnalysisResult.PaymentSchedule(0.1, 0.6, 1.0, null, "invalid-date"),
                null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<StageVerdictResponse> results = service.calculate(
                user(50000, null), 100000, a, routesWithLoan(20000), holds);

            assertThat(results).hasSize(3);
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("BALANCE_DATE_PARSE_FAILED"));
        }
    }
}
