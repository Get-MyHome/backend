package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.AiServerClient;
import aichallenge.getmyhome.verdict.client.CrawlerLambdaClient;
import aichallenge.getmyhome.verdict.client.dto.FundingStressRequest;
import aichallenge.getmyhome.verdict.client.dto.FundingStressResponse;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.req.VerdictRequest;
import aichallenge.getmyhome.verdict.dto.res.*;
import aichallenge.getmyhome.verdict.dto.res.VerdictResponse.VerdictMeta;
import aichallenge.getmyhome.verdict.enums.EvidenceRegistry;
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;

import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 판정 오케스트레이터 서비스
 *
 * 1단계: 자금 경로 판정 → 2단계: 청약 자격 판정 → 3단계: 구간 판정
 * 순서로 수행한 뒤 최종 VerdictResponse를 조립한다.
 *
 * 동일한 사용자 입력 + 동일한 rule_version이면 항상 동일한 결과가 나와야 한다 (P-006).
 */
@Slf4j
@Service
public class VerdictService {

  private final FinancingRouteService financingRouteService;
  private final SubscriptionEligibilityService subscriptionEligibilityService;
  private final StageCalculationService stageCalculationService;
  private final ComplexService complexService;
  private final CrawlerLambdaClient crawlerLambdaClient;
  private final AiServerClient aiServerClient;
  private final RuleProperties ruleProperties;
  private final VerdictEmailService verdictEmailService;

  /** 판정 결과 임시 캐시 — 이메일 발송 시 verdictId로 조회용 (30분 TTL, 최대 200건) */
  private final Cache<String, VerdictResponse> verdictCache = Caffeine.newBuilder()
          .expireAfterWrite(30, TimeUnit.MINUTES)
          .maximumSize(200)
          .build();

  public VerdictService(FinancingRouteService financingRouteService,
                        SubscriptionEligibilityService subscriptionEligibilityService,
                        StageCalculationService stageCalculationService,
                        ComplexService complexService,
                        CrawlerLambdaClient crawlerLambdaClient,
                        AiServerClient aiServerClient,
                        RuleProperties ruleProperties,
                        VerdictEmailService verdictEmailService) {
    this.financingRouteService = financingRouteService;
    this.subscriptionEligibilityService = subscriptionEligibilityService;
    this.stageCalculationService = stageCalculationService;
    this.complexService = complexService;
    this.crawlerLambdaClient = crawlerLambdaClient;
    this.aiServerClient = aiServerClient;
    this.ruleProperties = ruleProperties;
    this.verdictEmailService = verdictEmailService;
  }

  public VerdictResponse calculate(VerdictRequest request) {
    // 토큰 우선, 없으면 직접 전달된 user 사용
    UserConditionRequest user = null;
    if (request.conditionToken() != null && !request.conditionToken().isBlank()) {
      user = financingRouteService.getCondition(request.conditionToken());
      if (user == null) {
        throw BaseException.of(VerdictErrorCode.CONDITION_TOKEN_EXPIRED);
      }
    } else {
      user = request.user();
    }
    if (user == null) {
      throw BaseException.of(VerdictErrorCode.USER_CONDITION_REQUIRED);
    }
    String complexId = request.complexId();

    RuleVersion rule = ruleProperties.resolve(request.ruleVersion());
    String ruleVersion = request.ruleVersion() != null
            ? request.ruleVersion() : ruleProperties.getDefaultVersion();

    List<HoldResponse> holds = new ArrayList<>();
    // 단지 정보 조회 — complexId가 없으면 분양가 없이 판정 진행 (P-001 추정 모드)
    ComplexDetailResponse complex = null;
    Integer salePrice = null;
    if (complexId != null) {
      try {
        complex = complexService.getComplexDetail(complexId);
        salePrice = complex.salePrice();
      } catch (Exception e) {
        log.warn("단지 조회 실패: complexId={}, error={}", complexId, e.getMessage());
        holds.add(HoldReasonCode.COMPLEX_FETCH_FAILED.toHoldResponse());
      }
    }

    // (1) 자금 경로 판정
    List<FinancingRouteResponse> financingRoutes =
      financingRouteService.evaluate(user, salePrice, rule, holds);

    // (2) 청약 자격 판정
    List<SubscriptionEligibilityResponse> subscriptionEligibilities =
      subscriptionEligibilityService.evaluate(user, holds);

    // (3) 구간 판정 + 상품별 잔금 비교 — 단지 선택 시에만 수행
    List<StageVerdictResponse> verdicts = List.of();
    List<RouteBalanceComparison> routeComparisons = List.of();
    PdfAnalysisResult analysisResult = null;
    PdfAnalysisResult trustedResult = null;
    Integer unitSalePrice = salePrice;
    String unitTypeId = request.unitTypeId();
    String unitTypeName = null;
    String lastPdfUrl = null;
    if (complex != null) {

      // 선택 주택형 정보 조회 — unitTypeId가 있으면 해당 주택형의 분양가·타입명 사용
      if (unitTypeId != null && complex.unitTypes() != null) {
        for (var ut : complex.unitTypes()) {
          if (unitTypeId.equals(ut.unitTypeId())) {
            unitTypeName = ut.type();
            if (ut.salePrice() != null) {
              unitSalePrice = ut.salePrice();
            }
            break;
          }
        }
      }

      if (complex.sourceUrl() != null) {
        try {
          // (3-a) 크롤러 Lambda 호출 — PDF를 S3에 업로드하고 pre-signed URL 획득
          lastPdfUrl = crawlerLambdaClient.crawl(complexId, complex.sourceUrl(), complex.name());
          // (3-b) AI 서버 호출 — S3 PDF URL + 주택형 정보로 분석 요청
          analysisResult = aiServerClient.analyze(
              complexId, lastPdfUrl, unitTypeId, unitTypeName, unitSalePrice);
        } catch (AiServerClient.AiServerRetryableException e) {
          // 502 retryable — PDF URL 만료 가능성, 새 URL로 1회 재시도
          log.info("AI 서버 502 retryable → 새 크롤러 URL로 재시도: complexId={}", complexId);
          try {
            lastPdfUrl = crawlerLambdaClient.crawl(complexId, complex.sourceUrl(), complex.name());
            analysisResult = aiServerClient.analyze(
                complexId, lastPdfUrl, unitTypeId, unitTypeName, unitSalePrice);
          } catch (Exception retryEx) {
            log.warn("AI 서버 재시도 실패: complexId={}, error={}", complexId, retryEx.getMessage());
            holds.add(HoldReasonCode.AI_SERVER_FAILED.toHoldResponse());
          }
        } catch (CrawlerLambdaClient.CrawlerException e) {
          log.warn("크롤러 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.CRAWLER_FAILED.toHoldResponse());
        } catch (Exception e) {
          log.warn("AI 서버 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.AI_SERVER_FAILED.toHoldResponse());
        }
      }

      // AI holds → backend holds에 합류 (blocking 구분 유지)
      // AI 서버가 reasonCode·nextAction을 생략하는 경우 fallback 처리
      if (analysisResult != null && analysisResult.holds() != null) {
        for (var aiHold : analysisResult.holds()) {
          String reasonCode = aiHold.reasonCode() != null ? aiHold.reasonCode() : aiHold.kind();
          String nextAction = aiHold.nextAction() != null ? aiHold.nextAction() : aiHold.message();
          holds.add(new HoldResponse(
              reasonCode, aiHold.message(), nextAction,
              aiHold.kind(), aiHold.blocking(), null
          ));
        }
      }

      // 구간 판정에 사용할 AI 결과 — REVIEWED + validation.passed만 허용
      // AUTO_EXTRACTED / NEEDS_REVIEW는 HOLD 처리하여 구간 계산에 사용하지 않음
      if (analysisResult != null) {
        // AI 추출 가격과 ComplexService 가격 불일치 경고
        if (analysisResult.targetUnit() != null
            && analysisResult.targetUnit().salePriceManwon() != null
            && unitSalePrice != null
            && !unitSalePrice.equals(analysisResult.targetUnit().salePriceManwon())) {
          log.warn("분양가 불일치: ComplexService={}만원, AI 추출={}만원 → ComplexService 가격(주택형 최고가) 기준 보수적 판정",
              unitSalePrice, analysisResult.targetUnit().salePriceManwon());
        }

        // AI 원응답 메타 로깅 — PDF SHA·페이지수·검수 상태 확인용
        if (analysisResult.meta() != null) {
          log.info("AI 분석 메타: source_sha256={}, source_page_count={}, review_status={}",
              analysisResult.meta().sourceSha256(),
              analysisResult.meta().sourcePageCount(),
              analysisResult.reviewStatus());
        }

        boolean reviewed = "REVIEWED".equals(analysisResult.reviewStatus());
        boolean validated = analysisResult.validation() != null && analysisResult.validation().passed();
        if (reviewed && validated) {
          trustedResult = analysisResult;
        } else {
          log.info("AI 분석 결과 미신뢰: reviewStatus={}, validation.passed={} → HOLD 처리",
              analysisResult.reviewStatus(),
              analysisResult.validation() != null ? analysisResult.validation().passed() : "null");
          holds.add(HoldReasonCode.AI_REVIEW_PENDING.toHoldResponse());
        }
      }

      // 구간 판정 시 주택형 분양가 우선 사용 — 신뢰된 결과만 전달
      // 미검수(AI_REVIEW_PENDING)인 경우 COMPLEX_NOT_ANALYZED 중복 방지를 위해 호출 생략
      if (trustedResult != null) {
        verdicts = stageCalculationService.calculate(
            user, unitSalePrice, trustedResult, financingRoutes, holds);
        routeComparisons = stageCalculationService.calculateRouteComparisons(
            user, unitSalePrice, trustedResult, financingRoutes, holds);
      }
    }

    // (4) 결과에서 참조된 evidence만 수집
    List<EvidenceResponse> evidence = collectReferencedEvidence(financingRoutes, subscriptionEligibilities, verdicts);

    // (5) 정밀도 — 2단계 필드를 하나라도 입력했으면 "step2"
    String precision = determinePrecision(user);

    // (6) AI 분석 결과에서 프론트 전달용 데이터 추출
    String analysisSummary = null;
    List<RiskClauseResponse> riskClauses = List.of();
    String analysisReviewStatus = null;

    if (analysisResult != null) {
      analysisSummary = analysisResult.analysisSummary();
      analysisReviewStatus = analysisResult.reviewStatus();

      if (analysisResult.riskClauses() != null) {
        riskClauses = analysisResult.riskClauses().stream()
            .map(rc -> new RiskClauseResponse(
                rc.code(), rc.impactStage(), rc.message(), rc.nextAction(),
                rc.evidence() != null
                    ? rc.evidence().stream()
                        .map(e -> new RiskClauseResponse.PdfEvidence(e.page(), e.rawText()))
                        .toList()
                    : List.of()
            ))
            .toList();
      }
    }

    // (7) 중도금 임계선 계산 — 단지 선택 + 신뢰된 결과 있을 때만
    InterimCriticalLineResponse interimCriticalLine = null;
    if (trustedResult != null && unitSalePrice != null) {
      interimCriticalLine = stageCalculationService.calculateCriticalLine(
          user, unitSalePrice, trustedResult);
    }

    // (7-b) 중도금 금융조달 확정도 — AI 분석 결과 있을 때만
    InterimFinancingDetailResponse interimFinancingDetail = null;
    if (analysisResult != null) {
      interimFinancingDetail = stageCalculationService.buildInterimFinancingDetail(
          analysisResult, holds, riskClauses);
    }

    // (7-c) AI 서버 funding-stress advisory — 신뢰된 결과 + 크롤러 URL 확보 가능할 때만
    // S3 pre-signed URL은 10분 유효 — analyze 이후 만료될 수 있으므로 fresh URL 재발급
    FundingStressResponse fundingStress = null;
    if (trustedResult != null && complex != null && complex.sourceUrl() != null) {
      try {
        String freshPdfUrl = crawlerLambdaClient.crawl(complexId, complex.sourceUrl(), complex.name());
        fundingStress = callFundingStress(
            complexId, freshPdfUrl, unitTypeId, unitTypeName, unitSalePrice,
            user, financingRoutes, ruleVersion, rule.getAssumptionSetId(),
            interimCriticalLine, trustedResult);
      } catch (Exception e) {
        log.warn("funding-stress 호출 실패 (advisory 생략): complexId={}, error={}",
            complexId, e.getMessage());
      }
    }

    // (7-d) funding-stress holds → 메인 holds에 병합
    // AI 서버 StressHold는 code 필드 사용 (reason_code 아님), kind 없음
    if (fundingStress != null && fundingStress.holds() != null) {
      for (var fsHold : fundingStress.holds()) {
        String holdCode = fsHold.code() != null ? fsHold.code() : "FUNDING_STRESS_HOLD";
        String nextAction = fsHold.nextAction() != null ? fsHold.nextAction() : fsHold.message();
        boolean exists = holds.stream().anyMatch(h -> holdCode.equals(h.reasonCode()));
        if (!exists) {
          holds.add(new HoldResponse(
              holdCode, fsHold.message(), nextAction,
              "DOCUMENT_UNCERTAINTY", fsHold.blocking(), null
          ));
        }
      }
    }

    // (8) 전체 요약 — 구간 판정이 있을 때만
    VerdictStatus overallFundStatus = null;
    String overallInfoConfidence = null;
    String firstShortfallStage = null;
    Integer firstShortfallGap = null;

    if (!verdicts.isEmpty()) {
      overallFundStatus = deriveOverallFundStatus(verdicts);
      overallInfoConfidence = deriveInfoConfidence(analysisReviewStatus, holds);

      // 최초 부족 구간 탐색
      for (StageVerdictResponse sv : verdicts) {
        if (sv.status() != VerdictStatus.OK && sv.gap() != null) {
          firstShortfallStage = sv.stage();
          firstShortfallGap = sv.gap();
          break;
        }
      }
    }

    // (9) 최종 응답 조립
    String verdictId = generateVerdictId();
    Integer sourcePageCount = (analysisResult != null && analysisResult.meta() != null)
        ? analysisResult.meta().sourcePageCount() : null;
    VerdictMeta meta = new VerdictMeta(
      ruleVersion,
      rule.getAssumptionSetId(),
      LocalDate.now().toString(),
      precision,
      analysisReviewStatus,
      complex != null ? complex.name() : null,
      complexId,
      unitTypeName,
      unitSalePrice,
      user.cash(),
      user.monthlySaving(),
      sourcePageCount
    );

    VerdictResponse response = new VerdictResponse(
      verdictId, meta,
      overallFundStatus,
      overallInfoConfidence,
      firstShortfallStage,
      firstShortfallGap,
      financingRoutes,
      subscriptionEligibilities,
      verdicts,
      routeComparisons,
      interimCriticalLine,
      interimFinancingDetail,
      evidence,
      analysisSummary,
      riskClauses
    );

    verdictCache.put(verdictId, response);
    return response;
  }

  /**
   * 판정 결과를 이메일로 발송한다.
   * 캐시 TTL(30분) 이후에는 VERDICT_NOT_FOUND 예외 발생.
   */
  public VerdictEmailResponse sendResultEmail(String verdictId, String email) {
    log.info("판정 결과 이메일 발송 요청: verdictId={}, email={}", verdictId, email);

    VerdictResponse result = verdictCache.getIfPresent(verdictId);
    if (result == null) {
      throw BaseException.of(VerdictErrorCode.VERDICT_NOT_FOUND);
    }

    try {
      verdictEmailService.send(email, result);
    } catch (Exception e) {
      log.error("이메일 발송 실패: verdictId={}, email={}, error={}", verdictId, email, e.getMessage());
      throw BaseException.of(VerdictErrorCode.EMAIL_SEND_FAILED);
    }

    return new VerdictEmailResponse(
        "SENT", email,
        java.time.Instant.now().toString()
    );
  }

  private List<EvidenceResponse> collectReferencedEvidence(
      List<FinancingRouteResponse> routes,
      List<SubscriptionEligibilityResponse> eligibilities,
      List<StageVerdictResponse> verdicts) {

    Set<String> referencedIds = new LinkedHashSet<>();
    routes.forEach(r -> { if (r.evidenceIds() != null) referencedIds.addAll(r.evidenceIds()); });
    eligibilities.forEach(e -> { if (e.evidenceIds() != null) referencedIds.addAll(e.evidenceIds()); });
    verdicts.forEach(v -> { if (v.evidenceIds() != null) referencedIds.addAll(v.evidenceIds()); });

    return Arrays.stream(EvidenceRegistry.values())
        .filter(e -> referencedIds.contains(e.getEvidenceId()))
        .map(EvidenceRegistry::toEvidenceResponse)
        .toList();
  }

  private String determinePrecision(UserConditionRequest user) {
    boolean hasStep2 = user.incomeType() != null
      || user.monthlySaving() != null
      || user.spouseIncome() != null
      || user.marriagePlannedDate() != null
      || user.existingLoanMonthlyPayment() != null
      || user.existingLoanBalance() != null
      || user.householdType() != null
      || user.allMembersHomeless() != null
      || user.netAsset() != null
      || user.hasSubscriptionRight() != null
      || user.firstTimeBuyer() != null
      || user.subscriptionAccount() != null;

    return hasStep2 ? "step2" : "step1";
  }

  /** 구간 판정 중 가장 나쁜 상태를 전체 자금 상태로 사용. 우선순위: BLOCK > HOLD > GAP > OK */
  private VerdictStatus deriveOverallFundStatus(List<StageVerdictResponse> verdicts) {
    VerdictStatus worst = VerdictStatus.OK;
    for (StageVerdictResponse v : verdicts) {
      if (v.status() == VerdictStatus.BLOCK) return VerdictStatus.BLOCK;
      if (v.status() == VerdictStatus.HOLD) worst = VerdictStatus.HOLD;
      else if (v.status() == VerdictStatus.GAP && worst == VerdictStatus.OK) worst = VerdictStatus.GAP;
    }
    return worst;
  }

  /** 정보 확정도: 검수 완료 + HOLD 없음 → CONFIRMED, AI HOLD 있으면 HOLD, 부분 확인이면 PARTIAL */
  private String deriveInfoConfidence(String analysisReviewStatus, List<HoldResponse> holds) {
    boolean reviewed = "REVIEWED".equals(analysisReviewStatus);
    boolean hasDocUncertainty = holds.stream()
        .anyMatch(h -> "DOCUMENT_UNCERTAINTY".equals(h.kind()));
    boolean hasAiReviewPending = holds.stream()
        .anyMatch(h -> "AI_REVIEW_PENDING".equals(h.reasonCode()));

    if (hasAiReviewPending || !reviewed) return "HOLD";
    if (hasDocUncertainty) return "PARTIAL";
    return "CONFIRMED";
  }

  /**
   * AI 서버 funding-stress advisory 요청을 조립하여 호출한다.
   * 409(검수본 없음)이면 null을 반환, 기타 예외는 상위로 전파.
   */
  private FundingStressResponse callFundingStress(
      String complexId, String pdfUrl, String unitTypeId, String unitTypeName,
      Integer salePriceManwon, UserConditionRequest user,
      List<FinancingRouteResponse> financingRoutes,
      String ruleVersion, String assumptionSetId,
      InterimCriticalLineResponse criticalLine,
      PdfAnalysisResult trustedResult) {

    // analysis_request
    var analysisReq = new FundingStressRequest.AnalysisRequest(
        complexId, pdfUrl, unitTypeId, unitTypeName, salePriceManwon);

    // loan_routes — HOLD/BLOCK 경로는 한도를 null로 전송 (AI 서버가 422 거부)
    List<FundingStressRequest.LoanRoute> loanRoutes = financingRoutes.stream()
        .map(r -> {
          boolean hasLimits = r.status() == VerdictStatus.OK;
          return new FundingStressRequest.LoanRoute(
              r.productCode().toLowerCase().replace('_', '-'),
              r.productCode(),
              r.productName(),
              r.status().name(),
              hasLimits ? r.limitMin() : null,
              hasLimits ? r.limitMax() : null,
              ruleVersion,
              assumptionSetId
          );
        })
        .toList();

    // interim_ratio_grid_bps: [0, 공고문 알선비율, 임계비율, 중도금 총비율]
    List<Integer> ratioGrid = buildRatioGrid(criticalLine, trustedResult);

    var request = new FundingStressRequest(
        analysisReq,
        user.cash(),
        "PRE_CONTRACT",
        user.monthlySaving(),
        LocalDate.now().toString(),
        loanRoutes,
        ratioGrid
    );

    return aiServerClient.fundingStress(request);
  }

  /** 스트레스 시나리오 비율 그리드 조립 (bps 단위, 중복 제거·정렬) */
  private List<Integer> buildRatioGrid(
      InterimCriticalLineResponse criticalLine, PdfAnalysisResult trustedResult) {

    var gridSet = new java.util.TreeSet<Integer>();
    gridSet.add(0);

    // 공고문 알선비율
    if (criticalLine != null && criticalLine.arrangedRatio() != null) {
      gridSet.add((int) Math.round(criticalLine.arrangedRatio() * 10000));
    }

    // 임계비율
    if (criticalLine != null && criticalLine.criticalLoanRatio() != null) {
      gridSet.add((int) Math.round(criticalLine.criticalLoanRatio() * 10000));
    }

    // 중도금 총비율
    if (trustedResult.paymentSchedule() != null
        && trustedResult.paymentSchedule().interimPayment() != null
        && trustedResult.paymentSchedule().interimPayment().totalRatio() != null) {
      gridSet.add((int) Math.round(
          trustedResult.paymentSchedule().interimPayment().totalRatio() * 10000));
    }

    return List.copyOf(gridSet);
  }

  private String generateVerdictId() {
    return "V-" + UUID.randomUUID().toString().substring(0, 8);
  }
}