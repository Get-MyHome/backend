package aichallenge.getmyhome.verdict.client.dto;

import java.util.List;

/**
 * AI 서버 POST /api/funding-stress 요청 DTO.
 *
 * <p>backend가 이미 산출한 대출 경로별 한도와 현금 스냅샷을 전달하여
 * advisory 임계비율·자금 스트레스를 계산 요청한다.</p>
 *
 * <p>Jackson 전역 SNAKE_CASE 설정 적용 — camelCase 필드명이 자동으로 snake_case로 직렬화됨.</p>
 */
public record FundingStressRequest(
    AnalysisRequest analysisRequest,
    Integer cashManwon,
    String cashSnapshotTiming,
    Integer monthlySavingManwon,
    String asOfDate,
    List<LoanRoute> loanRoutes,
    List<Integer> interimRatioGridBps
) {

    public record AnalysisRequest(
        String complexId,
        String pdfUrl,
        String unitTypeId,
        String unitTypeName,
        Integer salePriceManwon
    ) {}

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
