package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 응답 DTO.
 *
 * <p>advisory 계산 결과 — 중도금 임계비율, 공고문 알선 상한 비교,
 * 경로별 자금 스트레스 시나리오를 포함한다.</p>
 *
 * <p>AI 서버(FastAPI+Pydantic)는 snake_case JSON을 반환하므로
 * multi-word 필드에 {@code @JsonProperty}로 명시적 매핑한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundingStressResponse(
    Boolean advisory,
    @JsonProperty("calculator_version") String calculatorVersion,
    @JsonProperty("calculation_scope") String calculationScope,
    @JsonProperty("input_digest") String inputDigest,
    @JsonProperty("as_of_date") String asOfDate,
    @JsonProperty("savings_policy") String savingsPolicy,
    @JsonProperty("monthly_saving_manwon") Integer monthlySavingManwon,
    @JsonProperty("maximum_interim_ratio_bps") Integer maximumInterimRatioBps,
    @JsonProperty("interim_continuity_threshold") InterimContinuityThreshold interimContinuityThreshold,
    @JsonProperty("document_cap_comparison") DocumentCapComparison documentCapComparison,
    @JsonProperty("route_cases") List<RouteStressCase> routeCases,
    List<FundingStressHold> holds,
    List<String> assumptions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimContinuityThreshold(
        String status,
        @JsonProperty("minimum_ratio_bps") Integer minimumRatioBps,
        @JsonProperty("minimum_loan_amount_manwon") Integer minimumLoanAmountManwon,
        @JsonProperty("resolution_bps") Integer resolutionBps,
        @JsonProperty("limiting_shortfall") FirstShortfall limitingShortfall
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentCapComparison(
        @JsonProperty("arrangement_status") String arrangementStatus,
        @JsonProperty("document_cap_ratio_bps") Integer documentCapRatioBps,
        @JsonProperty("personal_approval_confirmed") Boolean personalApprovalConfirmed,
        @JsonProperty("interim_continuity") InterimContinuity interimContinuity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimContinuity(
        String status,
        @JsonProperty("required_ratio_bps") Integer requiredRatioBps,
        @JsonProperty("document_cap_ratio_bps") Integer documentCapRatioBps,
        @JsonProperty("margin_bps") Integer marginBps,
        String certainty,
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FirstShortfall(
        String stage,
        @JsonProperty("installment_number") Integer installmentNumber,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("due_month") String dueMonth,
        @JsonProperty("due_text") String dueText,
        @JsonProperty("shortfall_manwon") Integer shortfallManwon,
        String certainty
    ) {}

    /** 대출 경로×한도 시나리오별 완주 판정 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteStressCase(
        @JsonProperty("route_id") String routeId,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("product_name") String productName,
        @JsonProperty("rule_version") String ruleVersion,
        @JsonProperty("assumption_set_id") String assumptionSetId,
        @JsonProperty("route_status") String routeStatus,
        @JsonProperty("limit_case") String limitCase,
        @JsonProperty("balance_financing_manwon") Integer balanceFinancingManwon,
        @JsonProperty("full_completion_threshold") FullCompletionThreshold fullCompletionThreshold,
        List<FundingScenario> scenarios,
        List<FundingStressHold> holds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FullCompletionThreshold(
        String status,
        @JsonProperty("minimum_ratio_bps") Integer minimumRatioBps,
        @JsonProperty("minimum_loan_amount_manwon") Integer minimumLoanAmountManwon,
        @JsonProperty("resolution_bps") Integer resolutionBps,
        @JsonProperty("limiting_shortfall") FirstShortfall limitingShortfall
    ) {}

    /** 비율별 스트레스 시나리오 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundingScenario(
        @JsonProperty("interim_ratio_bps") Integer interimRatioBps,
        @JsonProperty("interim_loan_amount_manwon") Integer interimLoanAmountManwon,
        String status,
        @JsonProperty("first_shortfall") FirstShortfall firstShortfall,
        @JsonProperty("stage_margins") List<StageMargin> stageMargins,
        @JsonProperty("worst_margin_manwon") Integer worstMarginManwon,
        @JsonProperty("balance_margin_manwon") Integer balanceMarginManwon,
        @JsonProperty("recovery_months_at_first_shortfall") Integer recoveryMonthsAtFirstShortfall
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageMargin(
        String stage,
        @JsonProperty("required_manwon") Integer requiredManwon,
        @JsonProperty("dedicated_funding_manwon") Integer dedicatedFundingManwon,
        @JsonProperty("available_manwon") Integer availableManwon,
        @JsonProperty("cash_margin_manwon") Integer cashMarginManwon,
        @JsonProperty("shortfall_manwon") Integer shortfallManwon,
        @JsonProperty("cash_carried_forward_manwon") Integer cashCarriedForwardManwon,
        String certainty
    ) {}

    /** AI 서버 StressHold — code 필드 사용 (reason_code 아님), kind 없음 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundingStressHold(
        String code,
        Boolean blocking,
        String message,
        @JsonProperty("next_action") String nextAction
    ) {}
}
