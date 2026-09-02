package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 요청 DTO.
 *
 * <p>backend가 이미 산출한 대출 경로별 한도와 현금 스냅샷을 전달하여
 * advisory 임계비율·자금 스트레스를 계산 요청한다.</p>
 *
 * <p>AI 서버(FastAPI+Pydantic)는 snake_case JSON을 수신하므로
 * {@code @JsonNaming}으로 명시적 직렬화한다.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FundingStressRequest(
    AnalysisRequest analysisRequest,
    Integer cashManwon,
    String cashSnapshotTiming,
    Integer monthlySavingManwon,
    String asOfDate,
    List<LoanRoute> loanRoutes,
    List<Integer> interimRatioGridBps
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalysisRequest(
        String complexId,
        String pdfUrl,
        String unitTypeId,
        String unitTypeName,
        Integer salePriceManwon
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoanRoute(
        String routeId,
        String productCode,
        String productName,
        String status,
        Integer limitMinManwon,
        Integer limitMaxManwon,
        String ruleVersion,
        String assumptionSetId
    ) {}
}
