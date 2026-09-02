package aichallenge.getmyhome.verdict.client;
import aichallenge.getmyhome.verdict.client.dto.FundingStressRequest;
import aichallenge.getmyhome.verdict.client.dto.FundingStressResponse;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 서버 HTTP 클라이언트 — 공고문 PDF 분석 결과를 요청
 *
 * - 503 ANALYSIS_SERVER_BUSY: 짧은 지수 백오프로 최대 3회 재시도
 * - 502 retryable: PDF 다운로드 실패 → AiServerRetryableException으로 전파하여
 *   호출부에서 새 크롤러 URL로 재시도할 수 있도록 함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;

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
    @Cacheable(value = "pdfAnalysis",
               key = "#complexId + ':' + (#unitTypeId != null ? #unitTypeId : 'common')",
               unless = "#result == null || !'REVIEWED'.equals(#result.reviewStatus())")
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

        long delayMs = INITIAL_DELAY_MS;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return aiServerRestClient.post()
                        .uri("/api/analyze")
                        .body(body)
                        .retrieve()
                        .body(PdfAnalysisResult.class);
            } catch (HttpServerErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 503 && attempt < MAX_RETRIES) {
                    log.warn("AI 서버 503 (BUSY), {}ms 후 재시도 ({}/{}): complexId={}",
                            delayMs, attempt + 1, MAX_RETRIES, complexId);
                    sleep(delayMs);
                    delayMs *= 2;
                    continue;
                }

                if (status == 502) {
                    log.warn("AI 서버 502 (retryable): complexId={}, error={}", complexId, e.getMessage());
                    throw new AiServerRetryableException("AI 서버 PDF 다운로드 실패 (502)", e);
                }

                log.warn("AI 서버 호출 실패: complexId={}, status={}, error={}",
                        complexId, status, e.getMessage());
                throw e;
            } catch (RestClientException e) {
                log.warn("AI 서버 호출 실패: complexId={}, unitTypeId={}, error={}",
                        complexId, unitTypeId, e.getMessage());
                throw e;
            }
        }
        // 여기 도달 시 503 재시도 모두 소진
        throw new RestClientException("AI 서버 503 재시도 횟수 초과: complexId=" + complexId);
    }

    /**
     * 중도금 임계비율·자금 스트레스 advisory 계산 요청.
     *
     * <p>REVIEWED 검수본이 없으면 AI 서버가 409 FUNDING_STRESS_UNAVAILABLE을 반환한다.
     * 이 경우 null을 반환하여 호출부에서 advisory를 생략할 수 있도록 한다.</p>
     */
    public FundingStressResponse fundingStress(FundingStressRequest request) {
        log.info("AI 서버 funding-stress 호출: complexId={}, unitTypeId={}",
                request.analysisRequest().complexId(),
                request.analysisRequest().unitTypeId());

        long delayMs = INITIAL_DELAY_MS;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return aiServerRestClient.post()
                        .uri("/api/funding-stress")
                        .body(request)
                        .retrieve()
                        .body(FundingStressResponse.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 409) {
                    log.info("AI 서버 409 (FUNDING_STRESS_UNAVAILABLE): 검수본 없음 → advisory 생략");
                    return null;
                }
                log.warn("AI 서버 funding-stress 클라이언트 오류: status={}, error={}",
                        e.getStatusCode().value(), e.getMessage());
                throw e;
            } catch (HttpServerErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 503 && attempt < MAX_RETRIES) {
                    log.warn("AI 서버 funding-stress 503, {}ms 후 재시도 ({}/{})",
                            delayMs, attempt + 1, MAX_RETRIES);
                    sleep(delayMs);
                    delayMs *= 2;
                    continue;
                }
                log.warn("AI 서버 funding-stress 호출 실패: status={}, error={}",
                        status, e.getMessage());
                throw e;
            } catch (RestClientException e) {
                log.warn("AI 서버 funding-stress 호출 실패: error={}", e.getMessage());
                throw e;
            }
        }
        throw new RestClientException("AI 서버 funding-stress 503 재시도 횟수 초과");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RestClientException("AI 서버 재시도 대기 중 인터럽트", ie);
        }
    }

    /** 502 retryable — 호출부에서 새 크롤러 URL로 재시도 가능 */
    public static class AiServerRetryableException extends RuntimeException {
        public AiServerRetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
