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
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;

import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
                        RuleProperties ruleProperties) {
    this.financingRouteService = financingRouteService;
    this.subscriptionEligibilityService = subscriptionEligibilityService;
    this.stageCalculationService = stageCalculationService;
    this.complexService = complexService;
    this.crawlerLambdaClient = crawlerLambdaClient;
    this.aiServerClient = aiServerClient;
    this.ruleProperties = ruleProperties;
  }

  public VerdictResponse calculate(VerdictRequest request) {
    UserConditionRequest user = request.user();
    String complexId = request.complexId();

    RuleVersion rule = ruleProperties.resolve(request.ruleVersion());
    String ruleVersion = request.ruleVersion() != null
            ? request.ruleVersion() : ruleProperties.getDefaultVersion();

    List<HoldResponse> holds = new ArrayList<>();
    List<EvidenceResponse> evidence = new ArrayList<>();

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
      financingRouteService.evaluate(user, salePrice, rule, holds, evidence);

    // (2) 청약 자격 판정
    List<SubscriptionEligibilityResponse> subscriptionEligibilities =
      subscriptionEligibilityService.evaluate(user, holds, evidence);

    // (3) 구간 판정 — 단지 선택 시에만 수행
    List<StageVerdictResponse> verdicts = List.of();
    if (complex != null) {
      PdfAnalysisResult analysisResult = null;
      if (complex.sourceUrl() != null) {
        try {
          // (3-a) 크롤러 Lambda 호출 — PDF를 S3에 업로드하고 pre-signed URL 획득
          String pdfUrl = crawlerLambdaClient.crawl(complexId, complex.sourceUrl());
          // (3-b) AI 서버 호출 — S3 PDF URL로 분석 요청
          analysisResult = aiServerClient.analyze(complexId, pdfUrl);
        } catch (CrawlerLambdaClient.CrawlerException e) {
          log.warn("크롤러 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.CRAWLER_FAILED.toHoldResponse());
        } catch (Exception e) {
          log.warn("AI 서버 호출 실패: complexId={}, error={}", complexId, e.getMessage());
          holds.add(HoldReasonCode.AI_SERVER_FAILED.toHoldResponse());
        }
      }
      verdicts = stageCalculationService.calculate(
        user, salePrice, analysisResult, financingRoutes, holds, evidence);
    }

    // (4) 정밀도 — 2단계 필드를 하나라도 입력했으면 "step2"
    String precision = determinePrecision(user);

    // (5) 최종 응답 조립
    String verdictId = generateVerdictId();
    VerdictMeta meta = new VerdictMeta(
      ruleVersion,
      rule.getAssumptionSetId(),
      LocalDate.now().toString(),
      precision
    );

    VerdictResponse response = new VerdictResponse(
      verdictId, meta,
      financingRoutes,
      subscriptionEligibilities,
      verdicts,
      holds,
      evidence
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

    // TODO: PDF 렌더링 + SMTP 발송 (P-020, P-022 문구 적용 필요)
    throw BaseException.of(VerdictErrorCode.EMAIL_NOT_IMPLEMENTED);
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