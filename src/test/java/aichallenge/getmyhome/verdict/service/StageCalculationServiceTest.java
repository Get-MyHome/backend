package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult.*;
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

    /** v0.3 구조의 PdfAnalysisResult 생성 헬퍼 */
    private PdfAnalysisResult analysis(double down, double interim, double interimLoanRatio, String balanceDate) {
        StagePayment downPayment = new StagePayment(down, null, "RATIO", null, null, null, null);
        StagePayment interimPayment = new StagePayment(interim, null, "RATIO", null, null, null, null);
        double balanceRatio = 1.0 - down - interim;
        StagePayment balancePayment = new StagePayment(balanceRatio, null, "RATIO", null, balanceDate, null, null);

        PaymentSchedule schedule = new PaymentSchedule(downPayment, interimPayment, balancePayment);

        // interimLoanRatio는 중도금 대비 비율이므로 분양가 대비로 변환
        double arrangedRatio = interim * interimLoanRatio;
        InterimLoan loan = new InterimLoan(
            "PLANNED", arrangedRatio > 0 ? arrangedRatio : null, null,
            null, null, null,
            List.of(), null, null, null, null, null, null, null
        );

        return new PdfAnalysisResult(
            "TEST-001", "READY", "REVIEWED", null, null,
            null, schedule, loan, null, null, null, null, null, null, null, null
        );
    }

    private PdfAnalysisResult analysisWithCosts(double down, double interim, double interimLoanRatio,
                                                  String balanceDate, int additionalCost) {
        PdfAnalysisResult base = analysis(down, interim, interimLoanRatio, balanceDate);

        List<AdditionalCost> costs = List.of(new AdditionalCost(
            "BALCONY_EXTENSION", "발코니 확장", additionalCost, true, false, null, null, null
        ));

        return new PdfAnalysisResult(
            base.complexId(), base.analysisStatus(), base.reviewStatus(),
            base.reviewer(), base.reviewedAt(), base.targetUnit(),
            base.paymentSchedule(), base.interimLoan(), costs,
            base.riskClauses(), base.analysisSummary(), base.holds(),
            base.exceptionFlags(), base.evidence(), base.validation(), base.meta()
        );
    }

    private List<FinancingRouteResponse> routesWithLoan(int maxLoan) {
        return List.of(new FinancingRouteResponse(
            "DIDIMDOL_GENERAL", "디딤돌 대출 - 일반", VerdictStatus.OK, null, maxLoan, null, null,
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
            PdfAnalysisResult analysis = new PdfAnalysisResult(
                "TEST-001", "HOLD", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
            );
            List<HoldResponse> holds = new ArrayList<>();

            List<StageVerdictResponse> results = service.calculate(
                user(50000, null), 100000, analysis, routesWithLoan(20000), holds);

            assertThat(results).isEmpty();
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("COMPLEX_NOT_ANALYZED"));
        }

        @Test
        @DisplayName("모든 구간 OK - 충분한 현금 + 대출")
        void allStagesOk() {
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
            assertThat(results.get(0).monthsNeeded()).isEqualTo(50);
            assertThat(results.get(0).scenarios()).isNotEmpty();
        }

        @Test
        @DisplayName("잔금 부족 + 기한 내 저축 해소 불가 → BLOCK")
        void balanceBlockDeadlineTooShort() {
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
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysisWithCosts(0.1, 0.6, 1.0, null, 5000);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            assertThat(results.get(2).required()).isEqualTo(35000);
        }

        @Test
        @DisplayName("중도금 자비 부담 계산 (중도금 대출 비율 50%)")
        void interimPartialLoan() {
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 0.5, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, routesWithLoan(20000), new ArrayList<>());

            assertThat(results.get(1).required()).isEqualTo(30000);
            assertThat(results.get(1).status()).isEqualTo(VerdictStatus.OK);
        }

        @Test
        @DisplayName("대출 없는 경우 잔금 판정")
        void noFinancingRoutes() {
            UserConditionRequest u = user(50000, null);
            PdfAnalysisResult a = analysis(0.1, 0.6, 1.0, null);

            List<StageVerdictResponse> results = service.calculate(
                u, 100000, a, List.of(), new ArrayList<>());

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
                new FinancingRouteResponse("DIDIMDOL_GENERAL", "디딤돌 대출 - 일반", VerdictStatus.OK, null, 20000, null, null, List.of()),
                new FinancingRouteResponse("DIDIMDOL_FIRST", "디딤돌 대출 - 생애최초", VerdictStatus.HOLD, null, null, null, "NEED_FIRST_TIME_INFO", List.of()),
                new FinancingRouteResponse("BANK_MORTGAGE", "시중은행 주택담보대출", VerdictStatus.OK, 15000, 25000, "DSR", null, List.of())
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
            StagePayment downPayment = new StagePayment(0.1, null, "RATIO", null, null, null, null);
            StagePayment interimPayment = new StagePayment(0.6, null, "RATIO", null, null, null, null);
            StagePayment balancePayment = new StagePayment(0.3, null, "RATIO", null, "invalid-date", null, null);
            PaymentSchedule schedule = new PaymentSchedule(downPayment, interimPayment, balancePayment);

            InterimLoan loan = new InterimLoan(
                "PLANNED", 0.6, null, null, null, null,
                List.of(), null, null, null, null, null, null, null
            );

            PdfAnalysisResult a = new PdfAnalysisResult(
                "TEST-001", "READY", "REVIEWED", null, null,
                null, schedule, loan, null, null, null, null, null, null, null, null
            );

            List<HoldResponse> holds = new ArrayList<>();
            List<StageVerdictResponse> results = service.calculate(
                user(50000, null), 100000, a, routesWithLoan(20000), holds);

            assertThat(results).hasSize(3);
            assertThat(holds).anyMatch(h -> h.reasonCode().equals("BALANCE_DATE_PARSE_FAILED"));
        }

        @Test
        @DisplayName("정액 중도금 — FIXED_AMOUNT basis")
        void fixedAmountInterim() {
            StagePayment downPayment = new StagePayment(0.1, null, "RATIO", null, null, null, null);
            StagePayment interimPayment = new StagePayment(null, 1000, "FIXED_AMOUNT", null, null, null, null);
            StagePayment balancePayment = new StagePayment(null, null, "RATIO", null, null, null, null);
            PaymentSchedule schedule = new PaymentSchedule(downPayment, interimPayment, balancePayment);

            PdfAnalysisResult a = new PdfAnalysisResult(
                "TEST-001", "READY", "REVIEWED", null, null,
                null, schedule, null, null, null, null, null, null, null, null, null
            );

            List<StageVerdictResponse> results = service.calculate(
                user(50000, null), 100000, a, routesWithLoan(20000), new ArrayList<>());

            assertThat(results).hasSize(3);
            assertThat(results.get(1).required()).isEqualTo(1000);
            assertThat(results.get(2).required()).isEqualTo(89000);
        }
    }
}
