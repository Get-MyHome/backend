package aichallenge.getmyhome.verdict.enums;

/**
 * AI 분석 상태 — POST /api/analyze 응답의 analysis_status 필드.
 *
 * <ul>
 *   <li>READY: 분석 완료, 구간 판정에 사용 가능</li>
 *   <li>PARTIAL: 일부 추출 실패, blocking HOLD 존재 가능</li>
 *   <li>HOLD: 분석 보류, blocking HOLD 존재</li>
 * </ul>
 */
public enum AnalysisStatus {
    READY,
    PARTIAL,
    HOLD
}
