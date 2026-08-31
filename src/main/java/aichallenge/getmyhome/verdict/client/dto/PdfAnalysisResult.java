package aichallenge.getmyhome.verdict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 서버가 반환하는 분양 공고문 PDF 분석 결과.
 *
 * <p>AI 서버(POST /api/analyze)의 응답 본문을 역직렬화하는 내부 통신용 DTO.
 * 프론트엔드에 직접 노출되지 않으며, 백엔드의 StageCalculationService에서
 * 구간별 필요자금을 산출할 때 사용된다.</p>
 *
 * <p>TODO: AI 서버 응답 스펙 확정 전 — 필드 추가·변경 시 AI 엔지니어와 협의 필요</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfAnalysisResult(
  String complexId,              // 단지 ID (공고문에서 추출)
  PaymentSchedule paymentSchedule, // 납부 일정 (계약금·중도금·잔금 비율 및 잔금 납부일)
  List<AdditionalCost> additionalCosts // 추가 비용 목록 (옵션 비용, 발코니 확장 등)
) {

  /**
   * 납부 일정 — 공고문에서 추출한 구간별 비율.
   *
   * <p>모든 비율은 0.0 ~ 1.0 범위의 소수 (예: 0.1 = 10%).
   * null이면 공고문에서 해당 정보를 추출하지 못한 것.</p>
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PaymentSchedule(
    Double downPaymentRatio,    // 계약금 비율 (예: 0.1 = 분양가의 10%)
    Double interimPaymentRatio, // 중도금 비율 (예: 0.6 = 분양가의 60%)
    Double interimLoanRatio,    // 중도금 대출 비율 — 중도금 중 대출 가능 비율 (예: 1.0 = 전액 대출)
    Double balanceRatio,        // 잔금 비율 (예: 0.3 = 분양가의 30%). null이면 1 - 계약금 - 중도금으로 산출
    String balanceDueDate       // 잔금 납부 예정일 (YYYY-MM-DD). null이면 미정
  ) {
  }

  /**
   * 추가 비용 항목 — 분양가 외 발생하는 비용.
   *
   * <p>잔금 구간의 필요자금 산출 시 합산된다.</p>
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AdditionalCost(
    String type,      // 비용 유형 (예: "BALCONY_EXTENSION", "OPTION", "SYSTEM_AC")
    Integer amount,   // 금액 (만 원). null이면 금액 미확인
    String stage,     // 납부 구간 (예: "CONTRACT", "BALANCE"). null이면 잔금으로 간주
    boolean required  // 필수 납부 여부. true면 총 필요자금에 자동 포함
  ) {
  }
}