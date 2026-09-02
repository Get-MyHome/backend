package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 응답 DTO.
 *
 * <p>advisory 계산 결과 — 중도금 임계비율, 공고문 알선 상한 비교,
 * 경로별 자금 스트레스 시나리오를 포함한다.</p>
 *
 * <p>AI 서버(FastAPI+Pydantic)는 snake_case JSON을 반환하므로
 * {@code @JsonNaming}으로 명시적 매핑한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FundingStressResponse(
    Boolean advisory,
    String calculatorVersion,
    String calculationScope,
    String inputDigest,
    String asOfDate,
    String savingsPolicy,
    Integer monthlySavingManwon,
    Integer maximumInterimRatioBps,
    InterimContinuityThreshold interimContinuityThreshold,
    DocumentCapComparison documentCapComparison,
    List<RouteStressCase> routeCases,
    List<FundingStressHold> holds,
    List<String> assumptions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InterimContinuityThreshold(
        String status,
        Integer minimumRatioBps,
        Integer minimumLoanAmountManwon,
        Integer resolutionBps,
        FirstShortfall limitingShortfall
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DocumentCapComparison(
        String arrangementStatus,
        Integer documentCapRatioBps,
        Boolean personalApprovalConfirmed,
        InterimContinuity interimContinuity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InterimContinuity(
        String status,
        Integer requiredRatioBps,
        Integer documentCapRatioBps,
        Integer marginBps,
        String certainty,
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FirstShortfall(
        String stage,
        Integer installmentNumber,
        String dueDate,
        String dueMonth,
        String dueText,
        Integer shortfallManwon,
        String certainty
    ) {}

    /** 대출 경로×한도 시나리오별 완주 판정 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RouteStressCase(
        String routeId,
        String productCode,
        String productName,
        String ruleVersion,
        String assumptionSetId,
        String routeStatus,
        String limitCase,
        Integer balanceFinancingManwon,
        FullCompletionThreshold fullCompletionThreshold,
        List<FundingScenario> scenarios,
        List<FundingStressHold> holds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FullCompletionThreshold(
        String status,
        Integer minimumRatioBps,
        Integer minimumLoanAmountManwon,
        Integer resolutionBps,
        FirstShortfall limitingShortfall
    ) {}

    /** 비율별 스트레스 시나리오 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FundingScenario(
        Integer interimRatioBps,
        Integer interimLoanAmountManwon,
        String status,
        FirstShortfall firstShortfall,
        List<StageMargin> stageMargins,
        Integer worstMarginManwon,
        Integer balanceMarginManwon,
        Integer recoveryMonthsAtFirstShortfall
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StageMargin(
        String stage,
        Integer requiredManwon,
        Integer dedicatedFundingManwon,
        Integer availableManwon,
        Integer cashMarginManwon,
        Integer shortfallManwon,
        Integer cashCarriedForwardManwon,
        String certainty
    ) {}

    /** AI 서버 StressHold — code 필드 사용 (reason_code 아님), kind 없음 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FundingStressHold(
        String code,
        Boolean blocking,
        String message,
        String nextAction
    ) {}
}