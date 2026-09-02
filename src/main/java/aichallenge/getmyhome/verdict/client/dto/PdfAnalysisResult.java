package aichallenge.getmyhome.verdict.client.dto;

import aichallenge.getmyhome.verdict.enums.AnalysisStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버(POST /api/analyze) v0.3 응답 DTO.
 *
 * <p>내부 통신용 — 프론트엔드에 직접 노출되지 않으며,
 * StageCalculationService에서 구간별 필요자금을 산출할 때 사용된다.</p>
 *
 * <p>모든 비율은 총 분양가 대비 0~1. 금액은 만 원 정수.
 * 미확인 값은 null, 0은 공고가 실제 0을 명시한 경우에만 사용.</p>
 *
 * <p>AI 서버(FastAPI+Pydantic)는 snake_case JSON을 반환하므로
 * multi-word 필드에 {@code @JsonProperty}로 명시적 매핑한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfAnalysisResult(
    @JsonProperty("complex_id") String complexId,
    @JsonProperty("analysis_status") AnalysisStatus analysisStatus,
    @JsonProperty("review_status") String reviewStatus,
    String reviewer,
    @JsonProperty("reviewed_at") String reviewedAt,
    @JsonProperty("target_unit") TargetUnit targetUnit,
    @JsonProperty("payment_schedule") PaymentSchedule paymentSchedule,
    @JsonProperty("interim_loan") InterimLoan interimLoan,
    @JsonProperty("additional_costs") List<AdditionalCost> additionalCosts,
    @JsonProperty("risk_clauses") List<RiskClause> riskClauses,
    @JsonProperty("analysis_summary") String analysisSummary,
    List<AiHold> holds,
    @JsonProperty("exception_flags") List<String> exceptionFlags,
    List<Evidence> evidence,
    Validation validation,
    Meta meta
) {

    // ── 대상 주택형 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetUnit(
        @JsonProperty("unit_type_id") String unitTypeId,
        @JsonProperty("unit_type_name") String unitTypeName,
        @JsonProperty("sale_price_manwon") Integer salePriceManwon
    ) {}

    // ── 납부 일정 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentSchedule(
        @JsonProperty("down_payment") StagePayment downPayment,
        @JsonProperty("interim_payment") StagePayment interimPayment,
        @JsonProperty("balance_payment") StagePayment balancePayment
    ) {}

    /** 계약금·중도금·잔금 공통 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StagePayment(
        @JsonProperty("total_ratio") Double totalRatio,
        @JsonProperty("total_amount_manwon") Integer totalAmountManwon,
        String basis,
        List<Installment> installments,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("due_month") String dueMonth,
        @JsonProperty("due_text") String dueText
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installment(
        Integer number,
        Double ratio,
        @JsonProperty("amount_manwon") Integer amountManwon,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("due_text") String dueText
    ) {}

    // ── 중도금 대출 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterimLoan(
        @JsonProperty("arrangement_status") String arrangementStatus,
        @JsonProperty("arranged_ratio") Double arrangedRatio,
        @JsonProperty("arranged_amount_manwon") Integer arrangedAmountManwon,
        @JsonProperty("self_funding_ratio") Double selfFundingRatio,
        @JsonProperty("self_funding_amount_manwon") Integer selfFundingAmountManwon,
        @JsonProperty("self_funding_origin") String selfFundingOrigin,
        @JsonProperty("bank_names") List<String> bankNames,
        @JsonProperty("guarantee_provider") String guaranteeProvider,
        @JsonProperty("interest_type") String interestType,
        @JsonProperty("interest_note") String interestNote,
        @JsonProperty("prepay_requirement_ratio") Double prepayRequirementRatio,
        @JsonProperty("settlement_requirement") String settlementRequirement,
        @JsonProperty("settlement_deadline_text") String settlementDeadlineText,
        @JsonProperty("extension_contingency_disclosed") Boolean extensionContingencyDisclosed
    ) {}

    // ── 추가 비용 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdditionalCost(
        String type,
        String name,
        @JsonProperty("total_amount_manwon") Integer totalAmountManwon,
        Boolean required,
        @JsonProperty("included_in_sale_price") Boolean includedInSalePrice,
        @JsonProperty("applicable_unit_type") String applicableUnitType,
        List<CostPayment> payments,
        String note
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostPayment(
        Integer number,
        String stage,
        @JsonProperty("amount_manwon") Integer amountManwon,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("due_text") String dueText
    ) {}

    // ── 위험조항 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskClause(
        String code,
        @JsonProperty("impact_stage") String impactStage,
        String origin,
        String message,
        @JsonProperty("next_action") String nextAction,
        List<Evidence> evidence
    ) {}

    // ── AI 발 HOLD (backend HoldResponse와 별도) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiHold(
        @JsonProperty("reason_code") String reasonCode,
        String kind,
        boolean blocking,
        String message,
        @JsonProperty("next_action") String nextAction
    ) {}

    // ── 근거 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(
        String field,
        Integer page,
        @JsonProperty("raw_text") String rawText
    ) {}

    // ── 검증 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(
        boolean passed,
        List<ValidationIssue> issues,
        @JsonProperty("derived_fields") List<String> derivedFields
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidationIssue(
        String severity,
        String code,
        String field,
        String message
    ) {}

    // ── 메타 ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("extractor_version") String extractorVersion,
        @JsonProperty("prompt_version") String promptVersion,
        String provider,
        String model,
        @JsonProperty("source_sha256") String sourceSha256,
        @JsonProperty("source_page_count") Integer sourcePageCount,
        @JsonProperty("candidate_pages") List<Integer> candidatePages,
        @JsonProperty("analyzed_at") String analyzedAt
    ) {}
}