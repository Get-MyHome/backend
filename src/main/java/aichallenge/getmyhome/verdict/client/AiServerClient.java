package aichallenge.getmyhome.verdict.client;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 서버 HTTP 클라이언트 — 공고문 PDF 분석 결과를 요청
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiServerRestClient;

    /**
     * 주택형별 PDF 분석 요청.
     *
     * @param complexId     공고 식별자
     * @param pdfUrl        crawler가 생성한 pre-signed S3 URL
     * @param unitTypeId    주택형 ID (null이면 문서 공통 분석)
     * @param unitTypeName  주택형명 (null이면 문서 공통 분석)
     * @param salePriceManwon 주택형 최고 분양가 (만원, null이면 문서 공통 분석)
     * @return 분석 결과. AI 서버 장애 시 예외 전파
     */
    @Cacheable(value = "pdfAnalysis", key = "#complexId + ':' + (#unitTypeId != null ? #unitTypeId : 'common')")
    public PdfAnalysisResult analyze(String complexId, String pdfUrl,
                                     String unitTypeId, String unitTypeName,
                                     Integer salePriceManwon) {
        log.info("AI 서버 호출: complexId={}, unitTypeId={}", complexId, unitTypeId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("complex_id", complexId);
        body.put("pdf_url", pdfUrl);
        if (unitTypeId != null) {
            body.put("unit_type_id", unitTypeId);
            body.put("unit_type_name", unitTypeName);
            body.put("sale_price_manwon", salePriceManwon);
        }

        try {
            return aiServerRestClient.post()
                    .uri("/api/analyze")
                    .body(body)
                    .retrieve()
                    .body(PdfAnalysisResult.class);
        } catch (RestClientException e) {
            log.warn("AI 서버 호출 실패: complexId={}, unitTypeId={}, error={}",
                    complexId, unitTypeId, e.getMessage());
            throw e;
        }
    }
}
