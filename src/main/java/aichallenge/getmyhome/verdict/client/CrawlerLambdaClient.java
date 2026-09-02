package aichallenge.getmyhome.verdict.client;

import aichallenge.getmyhome.global.config.CrawlerLambdaProperties;
import aichallenge.getmyhome.verdict.client.dto.CrawlerResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Map;

/**
 * Lambda 크롤러 클라이언트 — 청약홈 공고 페이지에서 PDF를 수집하여 S3에 업로드한 뒤
 * Pre-signed URL을 반환받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerLambdaClient {

    private final LambdaClient lambdaClient;
    private final CrawlerLambdaProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Lambda 크롤러를 호출하여 PDF의 S3 pre-signed URL을 반환한다.
     *
     * @param complexId 공고 관리 번호
     * @param sourceUrl 청약홈 공고 페이지 URL
     * @return S3 pre-signed URL
     * @throws CrawlerException 크롤러 호출 실패 또는 PDF 수집 실패 시
     */
    @Cacheable(value = "crawlerPdfUrl", key = "#complexId")
    public String crawl(String complexId, String sourceUrl) {
        log.info("Lambda 크롤러 호출: complexId={}", complexId);

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("sourceUrl", sourceUrl, "complexId", complexId));

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(properties.getFunctionName())
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);

            if (response.functionError() != null) {
                log.warn("Lambda 함수 오류: complexId={}, error={}", complexId, response.functionError());
                throw new CrawlerException("Lambda 함수 실행 오류: " + response.functionError());
            }

            String responsePayload = response.payload().asUtf8String();
            log.info("Lambda 응답 원문: complexId={}, payload={}", complexId, responsePayload);
            CrawlerResponse crawlerResponse = objectMapper.readValue(responsePayload, CrawlerResponse.class);

            if (!crawlerResponse.isSuccess()) {
                String errorMsg = crawlerResponse.body() != null ? crawlerResponse.body().error() : "알 수 없는 오류";
                log.warn("크롤러 실패: complexId={}, statusCode={}, error={}",
                        complexId, crawlerResponse.statusCode(), errorMsg);
                throw new CrawlerException("PDF 수집 실패: " + errorMsg);
            }

            log.info("크롤러 성공: complexId={}, pdfUrl 수신 완료", complexId);
            return crawlerResponse.body().pdfUrl();

        } catch (JsonProcessingException e) {
            log.error("크롤러 응답 파싱 실패: complexId={}", complexId, e);
            throw new CrawlerException("크롤러 응답 파싱 실패", e);
        }
    }

    public static class CrawlerException extends RuntimeException {
        public CrawlerException(String message) {
            super(message);
        }

        public CrawlerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}