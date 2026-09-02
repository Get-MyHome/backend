package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 서버(POST /api/analyze) v0.3 응답 DTO.
 *
 * <p>내부 통신용 — 프론트엔드에 직접 노출되지 않으며,
 * StageCalculationService에서 구간별 필요자금을 산출할 때 사용된다.</p>
 *
 * <p>모든 비율은 총 분양가 대비 0~1. 금액은 만 원 정수.
 * 미확인 값은 null, 0은 공고가 실제 0을 명시한 경우에만 사용.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfAnalysisResult(
    String complexId,
    String analysisStatus,       // READY | PARTIAL | HOLD
    String reviewStatus,         // AUTO_EXTRACTED | NEEDS_REVIEW | REVIEWED
    String reviewer,
    String reviewedAt,
    TargetUnit targetUnit,
    PaymentSchedule paymentSchedule,
    InterimLoan interimLoan,
    List<AdditionalCost> additionalCosts,
    List<RiskClause> riskClauses,
    String analysisSummary,
    List<AiHold> holds,
    List<String> exceptionFlags,
    List<Evidence> evidence,
    Validation validation,
    Meta meta
) {

    // ── 대상 주택형 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetUnit(
        String unitTypeId,
        String unitTypeName,
        Integer salePriceManwon
    ) {}

    // ── 납부 일정 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentSchedule(
        StagePayment downPayment,
        StagePayment interimPayment,
        StagePayment balancePayment
    ) {}

    /** 계약금·중도금·잔금 공통 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StagePayment(
        Double totalRatio,
        Integer totalAmountManwon,
        String basis,                  // RATIO | FIXED_AMOUNT | MIXED | UNKNOWN
        List<Installment> installments,
        String dueDate,                // YYYY-MM-DD
        String dueMonth,               // YYYY-MM
        String dueText
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installment(
        Integer number,
        Double ratio,
        Integer amountManwon,
        String dueDate,
        String dueText
    ) {}

    // ── 중도금 대출 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimLoan(
        String arrangementStatus,             // PLANNED | CONFIRMED | NOT_AVAILABLE | NOT_STATED
        Double arrangedRatio,                 // 사업장 알선 상한 비율
        Integer arrangedAmountManwon,
        Double selfFundingRatio,              // 알선 외 별도 조달 구간
        Integer selfFundingAmountManwon,
        String selfFundingOrigin,             // EXTRACTED | DERIVED
        List<String> bankNames,
        String guaranteeProvider,
        String interestType,                  // DEFERRED_INTEREST 등
        String interestNote,
        Double prepayRequirementRatio,
        String settlementRequirement,         // REPAY_OR_CONVERT_TO_MORTGAGE 등
        String settlementDeadlineText,
        Boolean extensionContingencyDisclosed
    ) {}

    // ── 추가 비용 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdditionalCost(
        String type,
        String name,
        Integer totalAmountManwon,
        Boolean required,
        Boolean includedInSalePrice,
        String applicableUnitType,
        List<CostPayment> payments,
        String note
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostPayment(
        Integer number,
        String stage,               // CONTRACT | INTERIM | BALANCE
        Integer amountManwon,
        String dueDate,
        String dueText
    ) {}

    // ── 위험조항 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskClause(
        String code,                // LOAN_MEDIATION_NOT_GUARANTEED 등
        String impactStage,         // CONTRACT | INTERIM | BALANCE
        String origin,              // EXTRACTED | DERIVED
        String message,
        String nextAction,
        List<Evidence> evidence
    ) {}

    // ── AI 발 HOLD (backend HoldResponse와 별도) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiHold(
        String reasonCode,
        String kind,                // DOCUMENT_UNCERTAINTY | PERSONAL_REVIEW
        boolean blocking,
        String message,
        String nextAction
    ) {}

    // ── 근거 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(
        String field,
        Integer page,
        String rawText
    ) {}

    // ── 검증 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(
        boolean passed,
        List<ValidationIssue> issues,
        List<String> derivedFields
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidationIssue(
        String severity,            // WARNING | ERROR
        String code,
        String field,
        String message
    ) {}

    // ── 메타 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
        String schemaVersion,
        String extractorVersion,
        String promptVersion,
        String provider,
        String model,
        String sourceSha256,
        Integer sourcePageCount,
        List<Integer> candidatePages,
        String analyzedAt
    ) {}
}