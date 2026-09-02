package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 요청 DTO.
 *
 * <p>backend가 이미 산출한 대출 경로별 한도와 현금 스냅샷을 전달하여
 * advisory 임계비율·자금 스트레스를 계산 요청한다.</p>
 *
 * <p>AI 서버(FastAPI+Pydantic)는 snake_case JSON을 수신하므로
 * multi-word 필드에 {@code @JsonProperty}로 명시적 직렬화한다.</p>
 */
public record FundingStressRequest(
    @JsonProperty("analysis_request") AnalysisRequest analysisRequest,
    @JsonProperty("cash_manwon") Integer cashManwon,
    @JsonProperty("cash_snapshot_timing") String cashSnapshotTiming,
    @JsonProperty("monthly_saving_manwon") Integer monthlySavingManwon,
    @JsonProperty("as_of_date") String asOfDate,
    @JsonProperty("loan_routes") List<LoanRoute> loanRoutes,
    @JsonProperty("interim_ratio_grid_bps") List<Integer> interimRatioGridBps
) {

    public record AnalysisRequest(
        @JsonProperty("complex_id") String complexId,
        @JsonProperty("pdf_url") String pdfUrl,
        @JsonProperty("unit_type_id") String unitTypeId,
        @JsonProperty("unit_type_name") String unitTypeName,
        @JsonProperty("sale_price_manwon") Integer salePriceManwon
    ) {}

    public record LoanRoute(
        @JsonProperty("route_id") String routeId,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("product_name") String productName,
        String status,
        @JsonProperty("limit_min_manwon") Integer limitMinManwon,
        @JsonProperty("limit_max_manwon") Integer limitMaxManwon,
        @JsonProperty("rule_version") String ruleVersion,
        @JsonProperty("assumption_set_id") String assumptionSetId
    ) {}
}
