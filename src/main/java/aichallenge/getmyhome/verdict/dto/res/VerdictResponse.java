package aichallenge.getmyhome.verdict.dto.res;

import aichallenge.getmyhome.verdict.client.dto.FundingStressResponse;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "VerdictResponse", description = "청약 판정 결과 응답")
public record VerdictResponse(
  @Schema(description = "판정 고유 ID", example = "V-a1b2c3d4")
  String verdictId,
  @Schema(description = "판정 메타 정보")
  VerdictMeta meta,
  @Schema(description = "전체 자금 상태. 구간 판정 중 가장 나쁜 상태. 단지 미선택 시 null")
  VerdictStatus overallFundStatus,
  @Schema(description = "정보 확정도. CONFIRMED(검수 완료·HOLD 없음), HOLD(미확정 정보 있음), PARTIAL(일부만 확인). 단지 미선택 시 null",
      example = "HOLD")
  String overallInfoConfidence,
  @Schema(description = "최초 자금 부족 구간. CONTRACT/INTERIM/BALANCE. 부족 없으면 null", example = "INTERIM")
  String firstShortfallStage,
  @Schema(description = "최초 부족 구간의 예상 부족액 (만원). 부족 없으면 null", example = "3000")
  Integer firstShortfallGap,
  @Schema(description = "자금 경로별 판정 결과 목록")
  List<FinancingRouteResponse> financingRoutes,
  @Schema(description = "청약 자격 판정 결과 목록")
  List<SubscriptionEligibilityResponse> subscriptionEligibilities,
  @Schema(description = "구간별(계약금/중도금/잔금) 판정 결과 목록. 단지 선택 시에만 반환")
  List<StageVerdictResponse> verdicts,
  @Schema(description = "대출 상품별 잔금 판정 비교. 각 상품으로 진행 시 잔금 부족/충족 여부를 보여줍니다. 단지 선택 시에만 반환")
  List<RouteBalanceComparison> routeComparisons,
  @Schema(description = "중도금 임계선·조건부 안전마진. 단지 선택 시에만 반환")
  InterimCriticalLineResponse interimCriticalLine,
  @Schema(description = "중도금 금융조달 확정도. 확인/미확정 정보 구분 및 확인 질문 목록. 단지 선택 시에만 반환")
  InterimFinancingDetailResponse interimFinancingDetail,
  @Schema(description = "부족액 준비 시나리오 요약. 부족 구간이 있을 때만 반환")
  ShortfallPreparationResponse shortfallPreparation,
  @Schema(description = "HOLD 사유 목록. 백엔드 판정 HOLD(추가 입력 필요)와 AI 분석 HOLD(공고문 불확실/개인심사)가 함께 포함됩니다. kind 필드로 구분")
  List<HoldResponse> holds,
  @Schema(description = "판정에 사용된 규칙 근거 자료 목록 (정책 규정, 계산 공식 등)")
  List<EvidenceResponse> evidence,
  @Schema(description = "공고문 사실 요약. AI 분석 결과에서 추출한 납부 일정·대출 조건 등 핵심 정보. 단지 미선택 또는 분석 실패 시 null",
      example = "계약금은 분양가의 10%입니다. 중도금은 분양가의 60%입니다. 잔금은 분양가의 30%입니다. 공고문상 분양가의 40% 범위에서 중도금 대출을 알선할 예정입니다.")
  String analysisSummary,
  @Schema(description = "공고문 위험조항 목록. 대출 알선 비보장, 개인심사 필요, 자납 필요 등 사용자 자금에 영향을 줄 수 있는 조항과 PDF 근거")
  List<RiskClauseResponse> riskClauses,
  @Schema(description = "AI 서버 advisory 자금 스트레스 계산 결과. "
      + "중도금 임계비율, 공고문 알선 상한 비교, 비율별 스트레스 시나리오 포함. "
      + "REVIEWED 검수본이 없거나 호출 실패 시 null")
  FundingStressResponse fundingStress
) {

  @Schema(title = "VerdictMeta", description = "판정 메타 정보")
  public record VerdictMeta(
    @Schema(description = "적용된 규칙 버전", example = "v1")
    String ruleVersion,
    @Schema(description = "가정 세트 ID", example = "default")
    String assumptionSetId,
    @Schema(description = "판정 수행 일시 (YYYY-MM-DD)", example = "2026-09-02")
    String calculatedAt,
    @Schema(description = "정밀도. step1: 필수 항목만 입력, step2: 상세 항목 입력", example = "step1")
    String precision,
    @Schema(description = "AI 공고문 분석 검수 상태. "
        + "AUTO_EXTRACTED(AI 추출 · 검수 대기 — 확정값 아님), "
        + "REVIEWED(사람 검수 완료), "
        + "null(AI 분석 없음 — 단지 미선택 또는 분석 실패)",
        example = "REVIEWED")
    String analysisReviewStatus,

    // ── 이메일/PDF 커버 페이지용 메타 ──
    @Schema(description = "단지명", example = "래미안 원베일리")
    String complexName,
    @Schema(description = "공고 관리 번호", example = "2025000001")
    String complexId,
    @Schema(description = "선택 주택형", example = "084.9900A")
    String unitTypeName,
    @Schema(description = "분양가 (만원)", example = "120000")
    Integer salePriceManwon,
    @Schema(description = "보유 현금 (만원)", example = "5000")
    Integer cashManwon,
    @Schema(description = "월 저축 가능액 (만원). 미입력이면 null", example = "100")
    Integer monthlySavingManwon,
    @Schema(description = "공고문 PDF 페이지 수. AI 분석 없으면 null", example = "42")
    Integer sourcePageCount
  ) {
  }
}
