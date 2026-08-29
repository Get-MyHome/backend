package aichallenge.getmyhome.verdict.client;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
     * @return 분석 결과. AI 서버 장애 시 null 대신 예외를 전파하므로 호출부에서 처리 필요
     */
    @Cacheable(value = "pdfAnalysis", key = "#complexId")
    public PdfAnalysisResult analyze(String complexId, String pdfUrl) {
        log.info("AI 서버 호출: complexId={}", complexId);

        try {
            return aiServerRestClient.post()
                    .body(Map.of(
                            "complex_id", complexId,
                            "pdf_url", pdfUrl
                    ))
                    .retrieve()
                    .body(PdfAnalysisResult.class);
        } catch (RestClientException e) {
            log.warn("AI 서버 호출 실패: complexId={}, error={}", complexId, e.getMessage());
            throw e;
        }
    }
}