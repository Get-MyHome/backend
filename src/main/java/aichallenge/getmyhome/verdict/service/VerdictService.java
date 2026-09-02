package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.AiServerClient;
import aichallenge.getmyhome.verdict.client.CrawlerLambdaClient;
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
    if (complex != null) {

      // 선택 주택형 정보 조회 — unitTypeId가 있으면 해당 주택형의 분양가·타입명 사용
      String unitTypeId = request.unitTypeId();
      String unitTypeName = null;
      Integer unitSalePrice = salePrice;
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
          String pdfUrl = crawlerLambdaClient.crawl(complexId, complex.sourceUrl());
          // (3-b) AI 서버 호출 — S3 PDF URL + 주택형 정보로 분석 요청
          analysisResult = aiServerClient.analyze(
              complexId, pdfUrl, unitTypeId, unitTypeName, unitSalePrice);
        } catch (CrawlerLambdaClient.CrawlerException e) {
          log.warn("크롤러 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.CRAWLER_FAILED.toHoldResponse());
        } catch (Exception e) {
          log.warn("AI 서버 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.AI_SERVER_FAILED.toHoldResponse());
        }
      }

      // AI holds → backend holds에 합류 (blocking 구분 유지)
      if (analysisResult != null && analysisResult.holds() != null) {
        for (var aiHold : analysisResult.holds()) {
          holds.add(new HoldResponse(
              aiHold.reasonCode(), aiHold.message(), aiHold.nextAction(),
              aiHold.kind(), aiHold.blocking()
          ));
        }
      }

      // 구간 판정에 사용할 AI 결과 — REVIEWED + validation.passed만 허용
      // AUTO_EXTRACTED / NEEDS_REVIEW는 HOLD 처리하여 구간 계산에 사용하지 않음
      PdfAnalysisResult trustedResult = null;
      if (analysisResult != null) {
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
      verdicts = stageCalculationService.calculate(
        user, unitSalePrice, trustedResult, financingRoutes, holds);
      routeComparisons = stageCalculationService.calculateRouteComparisons(
        user, unitSalePrice, trustedResult, financingRoutes);
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

    // (7) 최종 응답 조립
    String verdictId = generateVerdictId();
    VerdictMeta meta = new VerdictMeta(
      ruleVersion,
      rule.getAssumptionSetId(),
      LocalDate.now().toString(),
      precision,
      analysisReviewStatus
    );

    VerdictResponse response = new VerdictResponse(
      verdictId, meta,
      financingRoutes,
      subscriptionEligibilities,
      verdicts,
      routeComparisons,
      holds,
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

  private String generateVerdictId() {
    return "V-" + UUID.randomUUID().toString().substring(0, 8);
  }
}