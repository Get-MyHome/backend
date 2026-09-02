package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lambda 크롤러 응답 DTO.
 *
 * <p>Lambda가 반환하는 JSON 구조 (snake_case):
 * <pre>
 * {
 *   "status_code": 200,
 *   "body": {
 *     "complex_id": "2026000372",
 *     "pdf_url": "https://...s3.amazonaws.com/pdfs/2026000372.pdf?...",
 *     "error": null
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawlerResponse(
        int statusCode,
        Body body
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String complexId,
            String pdfUrl,
            String error
    ) {
    }

    public boolean isSuccess() {
        return statusCode == 200 && body != null && body.pdfUrl() != null;
    }
}