package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.EvidenceResponse;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResponse;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.enums.EvidenceRegistry;
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.enums.HouseholdType;
import aichallenge.getmyhome.verdict.enums.MaritalStatus;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.rule.ProductRuleParams;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * 자금 경로 판정 서비스
 *
 * 6가지 대출 상품별 자격 확인 + 한도 계산을 수행한다.
 * 한도는 DTI·LTV·상품 절대한도(loanCap) 중 최소값으로 결정되며,
 * bindingFactor로 어떤 제약이 한도를 결정했는지 알려준다.
 *
 * 모든 금액은 만 원 단위. 정책 수치는 RuleVersion(yml)에서 로드된다.
 */
@Service
public class FinancingRouteService {

  private static final EvidenceRegistry EVIDENCE_DIDIMDOL = EvidenceRegistry.EV_RULE_002;
  private static final EvidenceRegistry EVIDENCE_YOUTH_DREAM = EvidenceRegistry.EV_RULE_003;
  private static final EvidenceRegistry EVIDENCE_BANK = EvidenceRegistry.EV_RULE_004;

  /**
   * @param salePrice 분양가(만 원). null이면 LTV 및 주택가격 상한 미적용 (P-001)
   */
  public List<FinancingRouteResponse> evaluate(UserConditionRequest user, Integer salePrice,
                                             RuleVersion rule,
                                             List<HoldResponse> holds, List<EvidenceResponse> evidence) {
    List<FinancingRouteResponse> routes = new ArrayList<>();

    evaluateDidimdolGeneral(user, salePrice, rule, routes, holds);
    evaluateDidimdolFirst(user, salePrice, rule, routes, holds);
    evaluateDidimdolNewlywed(user, salePrice, rule, routes, holds);
    evaluateYouthDreamSingle(user, salePrice, rule, routes, holds);
    evaluateYouthDreamNewlywed(user, salePrice, rule, routes, holds);
    evaluateBankMortgage(user, salePrice, rule, routes, holds);

    if (!routes.isEmpty()) {
      EvidenceRegistry.addIfAbsent(evidence, EvidenceRegistry.EV_RULE_001);
      EvidenceRegistry.addIfAbsent(evidence, EVIDENCE_DIDIMDOL);
      EvidenceRegistry.addIfAbsent(evidence, EVIDENCE_YOUTH_DREAM);
      EvidenceRegistry.addIfAbsent(evidence, EVIDENCE_BANK);
    }

    return routes;
  }

  // ═══ 디딤돌 대출 (일반) ═══

  private void evaluateDidimdolGeneral(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                       List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "DIDIMDOL_GENERAL";
    ProductRuleParams p = rule.getProduct(productCode);

    if (salePrice != null && salePrice > p.getHousingPriceLimit()) return;
    if (user.annualIncome() > p.getIncomeLimit()) return;

    if (hasSpouse(user) && user.spouseIncome() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_SPOUSE_INCOME));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_SPOUSE_INCOME);
      return;
    }
    if (getCombinedIncome(user) > p.getIncomeLimit()) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;

    // 세대 구성, 세대원 전원 무주택, 순자산 — 하나라도 미입력이면 HOLD
    if (user.householdType() == null || user.allMembersHomeless() == null || user.netAsset() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_HOUSEHOLD_INFO));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_HOUSEHOLD_INFO);
      return;
    }

    if (!Boolean.TRUE.equals(user.allMembersHomeless())) return;
    if (user.netAsset() > rule.getNetAssetLimit()) return;
    if (user.householdType() == HouseholdType.MEMBER) return;

    // 미혼 단독세대주: 나이 제한 + 별도 한도
    int loanCap = p.getLoanCap();
    if (isSingleHead(user)) {
      if (getAge(user.birthDate(), LocalDate.now()) < rule.getSingleHeadMinAge()) return;
      loanCap = rule.getSingleHeadLoanCap();
    }

    int limitMax = calculateLimitMax(user, salePrice, p, loanCap, rule);
    String bindingFactor = detectBindingFactor(user, salePrice, p, loanCap, rule);

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, null, limitMax, bindingFactor,
      null, List.of(EVIDENCE_DIDIMDOL.getEvidenceId())
    ));
  }

  // ═══ 디딤돌 대출 (생애최초) ═══

  private void evaluateDidimdolFirst(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                     List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "DIDIMDOL_FIRST";
    ProductRuleParams p = rule.getProduct(productCode);

    if (salePrice != null && salePrice > p.getHousingPriceLimit()) return;
    if (user.annualIncome() > p.getIncomeLimit()) return;
    if (hasSpouse(user) && user.spouseIncome() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_SPOUSE_INCOME));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_SPOUSE_INCOME);
      return;
    }
    if (getCombinedIncome(user) > p.getIncomeLimit()) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;

    if (user.firstTimeBuyer() == null || user.householdType() == null
        || user.allMembersHomeless() == null || user.netAsset() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_FIRST_TIME_INFO));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_FIRST_TIME_INFO);
      return;
    }

    if (!Boolean.TRUE.equals(user.firstTimeBuyer())) return;
    if (!Boolean.TRUE.equals(user.allMembersHomeless())) return;
    if (user.netAsset() > rule.getNetAssetLimit()) return;
    if (user.householdType() == HouseholdType.MEMBER) return;

    // 미혼 단독세대주: 생애최초 전용 한도
    int loanCap = p.getLoanCap();
    if (isSingleHead(user)) {
      if (getAge(user.birthDate(), LocalDate.now()) < rule.getSingleHeadMinAge()) return;
      loanCap = rule.getSingleHeadFirstLoanCap();
    }

    int limitMax = calculateLimitMax(user, salePrice, p, loanCap, rule);
    String bindingFactor = detectBindingFactor(user, salePrice, p, loanCap, rule);

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, null, limitMax, bindingFactor,
      null, List.of(EVIDENCE_DIDIMDOL.getEvidenceId())
    ));
  }

  // ═══ 디딤돌 대출 (신혼부부) ═══

  private void evaluateDidimdolNewlywed(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                        List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "DIDIMDOL_NEWLYWED";
    ProductRuleParams p = rule.getProduct(productCode);

    if (salePrice != null && salePrice > p.getHousingPriceLimit()) return;
    if (user.marital() == MaritalStatus.SINGLE) return;

    if (user.spouseIncome() == null || user.householdType() == null
        || user.allMembersHomeless() == null || user.netAsset() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_NEWLYWED_INFO));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_NEWLYWED_INFO);
      return;
    }

    if (getCombinedIncome(user) > p.getIncomeLimit()) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;
    if (!Boolean.TRUE.equals(user.allMembersHomeless())) return;
    if (user.netAsset() > rule.getNetAssetLimit()) return;

    int limitMax = calculateLimitMax(user, salePrice, p, p.getLoanCap(), rule);
    String bindingFactor = detectBindingFactor(user, salePrice, p, p.getLoanCap(), rule);

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, null, limitMax, bindingFactor,
      null, List.of(EVIDENCE_DIDIMDOL.getEvidenceId())
    ));
  }

  // ═══ 청년주택드림 (미혼) ═══

  private void evaluateYouthDreamSingle(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                        List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "YOUTH_DREAM_SINGLE";
    ProductRuleParams p = rule.getProduct(productCode);

    if (salePrice != null && salePrice > p.getHousingPriceLimit()) return;
    if (user.marital() != MaritalStatus.SINGLE) return;
    if (user.annualIncome() > p.getIncomeLimit()) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;
    if (getAge(user.birthDate(), LocalDate.now()) > 39) return;

    if (user.subscriptionAccount() == null || user.subscriptionAccount().openedAt() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_SUBSCRIPTION_INFO));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_SUBSCRIPTION_INFO);
      return;
    }

    // 청약통장 가입 12개월 미만이면 자격 미달
    long subscriptionMonths = java.time.temporal.ChronoUnit.MONTHS.between(
      user.subscriptionAccount().openedAt(), LocalDate.now());
    if (subscriptionMonths < 12) return;

    int limitMax = calculateLimitMax(user, salePrice, p, p.getLoanCap(), rule);
    String bindingFactor = detectBindingFactor(user, salePrice, p, p.getLoanCap(), rule);

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, null, limitMax, bindingFactor,
      null, List.of(EVIDENCE_YOUTH_DREAM.getEvidenceId())
    ));
  }

  // ═══ 청년주택드림 (신혼부부) ═══

  private void evaluateYouthDreamNewlywed(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                          List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "YOUTH_DREAM_NEWLYWED";
    ProductRuleParams p = rule.getProduct(productCode);

    if (salePrice != null && salePrice > p.getHousingPriceLimit()) return;
    if (user.marital() == MaritalStatus.SINGLE) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;

    if (user.spouseIncome() == null
        || user.subscriptionAccount() == null || user.subscriptionAccount().openedAt() == null) {
      routes.add(buildHoldRoute(productCode, HoldReasonCode.NEED_YOUTH_NEWLYWED_INFO));
      addHoldIfAbsent(holds, HoldReasonCode.NEED_YOUTH_NEWLYWED_INFO);
      return;
    }

    if (getCombinedIncome(user) > p.getIncomeLimit()) return;

    long subMonths = java.time.temporal.ChronoUnit.MONTHS.between(
      user.subscriptionAccount().openedAt(), LocalDate.now());
    if (subMonths < 12) return;

    int limitMax = calculateLimitMax(user, salePrice, p, p.getLoanCap(), rule);
    String bindingFactor = detectBindingFactor(user, salePrice, p, p.getLoanCap(), rule);

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, null, limitMax, bindingFactor,
      null, List.of(EVIDENCE_YOUTH_DREAM.getEvidenceId())
    ));
  }

  // ═══ 시중은행 주택담보대출 ═══
  // 정책대출과 달리 자격 필터 없이 DSR 규제로 한도 결정.
  // limitMin = min(보수적 금리, 스트레스 DSR) / limitMax = 최저 금리 기준

  private void evaluateBankMortgage(UserConditionRequest user, Integer salePrice, RuleVersion rule,
                                    List<FinancingRouteResponse> routes, List<HoldResponse> holds) {
    String productCode = "BANK_MORTGAGE";
    ProductRuleParams p = rule.getProduct(productCode);

    int existingAnnual = getExistingAnnualRepayment(user);

    int dsrMaxHigh = calculateDsrMaxLoan(user.annualIncome(), rule.getBankDsrPercent(),
      existingAnnual, rule.getBankRateMin() / 100.0, rule.getLoanTermYears());
    int dsrMaxLow = calculateDsrMaxLoan(user.annualIncome(), rule.getBankDsrPercent(),
      existingAnnual, rule.getBankRateMax() / 100.0, rule.getLoanTermYears());
    int stressDsrMax = calculateDsrMaxLoan(user.annualIncome(), rule.getBankDsrPercent(),
      existingAnnual, rule.getStressDsrRate() / 100.0, rule.getLoanTermYears());

    int limitMin = Math.max(Math.min(dsrMaxLow, stressDsrMax), 0);
    int limitMax = Math.max(dsrMaxHigh, 0);
    String bindingFactor = "DSR";

    if (salePrice != null) {
      int ltvMax = salePrice * p.getBaseLtvPercent() / 100;
      if (ltvMax < limitMax) {
        limitMax = ltvMax;
        bindingFactor = "LTV";
      }
      limitMin = Math.min(limitMin, ltvMax);
    }

    if (limitMin > limitMax) limitMin = limitMax;

    routes.add(new FinancingRouteResponse(
      productCode, VerdictStatus.OK, limitMin, limitMax, bindingFactor,
      null, List.of(EVIDENCE_BANK.getEvidenceId())
    ));
  }

  // ═══ 공통 한도 계산 ═══

  /** DTI·LTV·loanCap 중 최소값으로 한도 계산 */
  private int calculateLimitMax(UserConditionRequest user, Integer salePrice,
                                ProductRuleParams p, int loanCap, RuleVersion rule) {
    int dtiMax = calculateDtiMaxLoan(user.annualIncome(), p.getDtiPercent(),
      getExistingAnnualRepayment(user), p.getPolicyRate(), rule.getLoanTermYears());

    int limitMax = Math.min(dtiMax, loanCap);

    if (salePrice != null) {
      int ltvMax = salePrice * p.getBaseLtvPercent() / 100;
      limitMax = Math.min(limitMax, ltvMax);
    }

    return Math.max(limitMax, 0);
  }

  /** 한도를 결정한 제약 요인: "DTI" / "LTV" / null(loanCap) */
  private String detectBindingFactor(UserConditionRequest user, Integer salePrice,
                                     ProductRuleParams p, int loanCap, RuleVersion rule) {
    int dtiMax = calculateDtiMaxLoan(user.annualIncome(), p.getDtiPercent(),
      getExistingAnnualRepayment(user), p.getPolicyRate(), rule.getLoanTermYears());
    int limitMax = Math.min(dtiMax, loanCap);

    String factor = dtiMax < loanCap ? "DTI" : null;

    if (salePrice != null) {
      int ltvMax = salePrice * p.getBaseLtvPercent() / 100;
      if (ltvMax < limitMax) factor = "LTV";
    }
    return factor;
  }

  // ═══ 유틸리티 ═══

  private int getCombinedIncome(UserConditionRequest user) {
    int income = user.annualIncome();
    if (user.spouseIncome() != null) income += user.spouseIncome();
    return income;
  }

  private int getExistingAnnualRepayment(UserConditionRequest user) {
    return user.existingLoanMonthlyPayment() != null ? user.existingLoanMonthlyPayment() * 12 : 0;
  }

  private int getAge(LocalDate birthDate, LocalDate referenceDate) {
    return Period.between(birthDate, referenceDate).getYears();
  }

  private boolean isSingleHead(UserConditionRequest user) {
    return user.householdType() == HouseholdType.SINGLE_HEAD
      && user.marital() == MaritalStatus.SINGLE;
  }

  private boolean hasSpouse(UserConditionRequest user) {
    return user.marital() == MaritalStatus.MARRIED
      || user.marital() == MaritalStatus.ENGAGED;
  }

  private void addHoldIfAbsent(List<HoldResponse> holds, HoldReasonCode reason) {
    boolean exists = holds.stream().anyMatch(h -> reason.name().equals(h.reasonCode()));
    if (!exists) holds.add(reason.toHoldResponse());
  }

  /**
   * DTI 기반 최대 대출 가능 금액 역산.
   * 연간 상환 여력 = 연소득 × DTI% - 기존 상환액 → 원리금균등상환 역산
   */
  private int calculateDtiMaxLoan(int annualIncome, int dtiPercent,
                                  int existingAnnualRepayment, double annualRatePercent, int loanTermYears) {
    int maxAnnualRepayment = annualIncome * dtiPercent / 100 - existingAnnualRepayment;
    if (maxAnnualRepayment <= 0) return 0;
    return annualRepaymentToLoanAmount(maxAnnualRepayment, annualRatePercent / 100.0, loanTermYears);
  }

  /** DSR 기반 최대 대출 가능 금액 역산. annualRate는 소수 형태 (0.035 = 3.5%). */
  private int calculateDsrMaxLoan(int annualIncome, int dsrPercent,
                                  int existingAnnualRepayment, double annualRate, int loanTermYears) {
    int maxAnnualRepayment = annualIncome * dsrPercent / 100 - existingAnnualRepayment;
    if (maxAnnualRepayment <= 0) return 0;
    return annualRepaymentToLoanAmount(maxAnnualRepayment, annualRate, loanTermYears);
  }

  /**
   * 연간 상환 가능액 → 대출 원금 역산 (원리금균등상환 기준).
   * 원금 = 월상환액 × ((1 - (1+r)^(-n)) / r)
   */
  private int annualRepaymentToLoanAmount(int annualRepayment, double annualRate, int loanTermYears) {
    double monthlyRate = annualRate / 12.0;
    int months = loanTermYears * 12;
    double monthlyPayment = (double) annualRepayment / 12.0;

    if (monthlyRate == 0) return (int) (monthlyPayment * months);
    return (int) (monthlyPayment * ((1 - Math.pow(1 + monthlyRate, -months)) / monthlyRate));
  }

  private FinancingRouteResponse buildHoldRoute(String productCode, HoldReasonCode reason) {
    return new FinancingRouteResponse(
      productCode, VerdictStatus.HOLD, null, null, null,
      reason.name(), List.of(EvidenceRegistry.EV_RULE_001.getEvidenceId())
    );
  }
}
