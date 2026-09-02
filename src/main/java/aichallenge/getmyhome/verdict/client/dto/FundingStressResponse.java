package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 응답 DTO.
 *
 * <p>advisory 계산 결과 — 중도금 임계비율, 공고문 알선 상한 비교,
 * 비율별 자금 스트레스 시나리오를 포함한다.</p>
 *
 * <p>AI 서버 응답은 additive하게 확장될 수 있으므로 모든 레코드에
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}를 적용한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundingStressResponse(
    Boolean advisory,
    String calculatorVersion,
    Integer maximumInterimRatioBps,
    InterimContinuityThreshold interimContinuityThreshold,
    DocumentCapComparison documentCapComparison,
    List<RatioScenario> ratioScenarios,
    List<RouteScenario> routeScenarios,
    List<FundingStressHold> holds
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimContinuityThreshold(
        String status,
        Integer minimumRatioBps,
        Integer minimumLoanAmountManwon,
        Integer resolutionBps,
        String limitingShortfall
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentCapComparison(
        String arrangementStatus,
        Integer documentCapRatioBps,
        Boolean personalApprovalConfirmed,
        InterimContinuity interimContinuity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimContinuity(
        String status,
        Integer requiredRatioBps,
        Integer documentCapRatioBps,
        Integer marginBps,
        String certainty
    ) {}

    /** 비율별 스트레스 시나리오 (interim_ratio_grid_bps 각 항목에 대응) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RatioScenario(
        Integer ratioBps,
        String stage,
        Integer cashMarginManwon,
        Integer firstShortfallManwon,
        String certainty,
        Integer worstMarginManwon,
        Integer balanceMarginManwon
    ) {}

    /** 대출 경로×한도 시나리오별 완주 판정 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteScenario(
        String routeId,
        String productCode,
        String limitScenario,
        Integer limitManwon,
        FullCompletionThreshold fullCompletionThreshold
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FullCompletionThreshold(
        String status,
        Integer minimumRatioBps,
        Integer balanceShortfallManwon,
        Integer worstMarginManwon
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundingStressHold(
        String reasonCode,
        String kind,
        Boolean blocking,
        String message,
        String nextAction
    ) {}
}
