package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult.*;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResponse;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.dto.res.InterimCriticalLineResponse;
import aichallenge.getmyhome.verdict.dto.res.InterimFinancingDetailResponse;
import aichallenge.getmyhome.verdict.dto.res.InterimFinancingDetailResponse.ConfirmedInfo;
import aichallenge.getmyhome.verdict.dto.res.InterimFinancingDetailResponse.UnconfirmedInfo;
import aichallenge.getmyhome.verdict.dto.res.RiskClauseResponse;
import aichallenge.getmyhome.verdict.dto.res.RouteBalanceComparison;
import aichallenge.getmyhome.verdict.dto.res.StageVerdictResponse;
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.enums.ProductCode;
import aichallenge.getmyhome.verdict.enums.Stage;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 구간 판정 서비스
 *
 * 분양 아파트의 납부 스케줄(계약금 → 중도금 → 잔금)에 따라
 * 사용자의 자금이 각 구간을 감당할 수 있는지를 판정한다.
 *
 * 판정 결과: OK(충분) / GAP(부족하나 저축으로 해소 가능) / BLOCK(해소 불가) / HOLD(보류)
 * 현금은 계약금 → 중도금 → 잔금 순으로 차감되며, 대출 한도는 잔금에서만 적용된다.
 *
 * 관련 정책: P-002, P-003, P-004
 */
@Service
public class StageCalculationService {

  public List<StageVerdictResponse> calculate(UserConditionRequest user, Integer salePrice,
                                            PdfAnalysisResult analysisResult,
                                            List<FinancingRouteResponse> financingRoutes,
                                            List<HoldResponse> holds) {
    if (analysisResult == null || analysisResult.paymentSchedule() == null) {
      holds.add(HoldReasonCode.COMPLEX_NOT_ANALYZED.toHoldResponse());
      return List.of();
    }

    // AI 분석 상태가 HOLD이고 blocking hold가 있으면 구간 판정 보류
    if ("HOLD".equals(analysisResult.analysisStatus()) && hasBlockingHold(analysisResult)) {
      holds.add(HoldReasonCode.COMPLEX_NOT_ANALYZED.toHoldResponse());
      return List.of();
    }

    // OK 상태인 상품 중 최대 대출 한도 — 잔금 단계에서 사용
    int maxFinancingLimit = financingRoutes.stream()
      .filter(r -> r.status() == VerdictStatus.OK && r.limitMax() != null)
      .mapToInt(FinancingRouteResponse::limitMax)
      .max()
      .orElse(0);

    PaymentSchedule schedule = analysisResult.paymentSchedule();
    int price = salePrice != null ? salePrice : 0;

    // v0.3: StagePayment에서 비율 또는 정액 추출
    int contractRequired = resolveStageAmount(schedule.downPayment(), price);
    int interimRequired = resolveStageAmount(schedule.interimPayment(), price);
    int balanceRequired = resolveStageAmount(schedule.balancePayment(), price);

    // 잔금 비율이 없으면 1 - 계약금 - 중도금으로 산출
    if (balanceRequired == 0 && price > 0) {
      balanceRequired = price - contractRequired - interimRequired;
      if (balanceRequired < 0) balanceRequired = 0;
    }

    // 중도금 대출 — v0.3 InterimLoan 구조 사용
    int interimLoanAvailable = resolveInterimLoan(analysisResult.interimLoan(), price, interimRequired);
    int interimSelfRequired = Math.max(interimRequired - interimLoanAvailable, 0);

    // 추가 비용 — required=true이고 includedInSalePrice가 아닌 항목만 합산
    int additionalCosts = resolveAdditionalCosts(analysisResult.additionalCosts());
    balanceRequired += additionalCosts;

    // 잔금 기한까지 남은 개월 수
    int monthsUntilBalance = resolveMonthsUntilBalance(schedule.balancePayment(), holds);

    // 각 구간별 납부 기한 추출
    String contractDueDate = resolveDueDate(schedule.downPayment());
    String interimDueDate = resolveDueDate(schedule.interimPayment());
    String balanceDueDate = resolveDueDate(schedule.balancePayment());

    int cash = user.cash() != null ? user.cash() : 0;
    Integer monthlySaving = user.monthlySaving();

    List<StageVerdictResponse> results = new ArrayList<>();

    // 계약금 → 남은 현금으로 중도금 → 남은 현금 + 대출로 잔금
    results.add(judgeStage(Stage.CONTRACT, contractRequired, cash, 0, monthlySaving, 0, holds, contractDueDate));
    int cashAfterContract = Math.max(cash - contractRequired, 0);

    results.add(judgeStage(Stage.INTERIM, interimSelfRequired, cashAfterContract, 0, monthlySaving, 0, holds, interimDueDate));
    int cashAfterInterim = Math.max(cashAfterContract - interimSelfRequired, 0);

    results.add(judgeStage(Stage.BALANCE, balanceRequired, cashAfterInterim,
      maxFinancingLimit, monthlySaving, monthsUntilBalance, holds, balanceDueDate));

    return results;
  }

  /**
   * 대출 상품별 잔금 비교 판정.
   * OK 상태인 각 상품의 한도로 잔금을 감당할 수 있는지 개별 판정한다.
   */
  public List<RouteBalanceComparison> calculateRouteComparisons(
      UserConditionRequest user, Integer salePrice,
      PdfAnalysisResult analysisResult,
      List<FinancingRouteResponse> financingRoutes) {

    if (analysisResult == null || analysisResult.paymentSchedule() == null) {
      return List.of();
    }

    PaymentSchedule schedule = analysisResult.paymentSchedule();
    int price = salePrice != null ? salePrice : 0;

    int contractRequired = resolveStageAmount(schedule.downPayment(), price);
    int interimRequired = resolveStageAmount(schedule.interimPayment(), price);
    int balanceRequired = resolveStageAmount(schedule.balancePayment(), price);
    if (balanceRequired == 0 && price > 0) {
      balanceRequired = price - contractRequired - interimRequired;
      if (balanceRequired < 0) balanceRequired = 0;
    }

    int interimLoanAvailable = resolveInterimLoan(analysisResult.interimLoan(), price, interimRequired);
    int interimSelfRequired = Math.max(interimRequired - interimLoanAvailable, 0);

    int additionalCosts = resolveAdditionalCosts(analysisResult.additionalCosts());
    balanceRequired += additionalCosts;

    int cash = user.cash() != null ? user.cash() : 0;
    int cashAfterPrior = Math.max(cash - contractRequired - interimSelfRequired, 0);

    Integer monthlySaving = user.monthlySaving();

    int monthsUntilBalance = resolveMonthsUntilBalance(schedule.balancePayment(), new ArrayList<>());

    List<RouteBalanceComparison> comparisons = new ArrayList<>();

    for (FinancingRouteResponse route : financingRoutes) {
      if (route.status() != VerdictStatus.OK || route.limitMax() == null) continue;

      int loanLimit = route.limitMax();
      int available = cashAfterPrior + loanLimit;
      String productName = ProductCode.valueOf(route.productCode()).getDisplayName();

      if (available >= balanceRequired) {
        comparisons.add(new RouteBalanceComparison(
            route.productCode(), productName, VerdictStatus.OK,
            loanLimit, balanceRequired, available,
            null, null, null, null));
        continue;
      }

      int gap = balanceRequired - available;

      if (monthlySaving != null && monthlySaving > 0) {
        int monthsNeeded = (int) Math.ceil((double) gap / monthlySaving);
        Integer monthsAvail = monthsUntilBalance > 0 ? monthsUntilBalance : null;

        VerdictStatus status;
        if (monthsAvail != null && monthsNeeded > monthsAvail) {
          status = VerdictStatus.BLOCK;
        } else {
          status = VerdictStatus.GAP;
        }

        String scenario = "월 " + monthlySaving + "만 원 저축 시 " + monthsNeeded + "개월 필요";
        if (monthsAvail != null) {
          scenario += " (잔금일까지 " + monthsAvail + "개월)";
        }

        comparisons.add(new RouteBalanceComparison(
            route.productCode(), productName, status,
            loanLimit, balanceRequired, available,
            gap, monthsAvail, monthsNeeded, scenario));
      } else {
        comparisons.add(new RouteBalanceComparison(
            route.productCode(), productName, VerdictStatus.BLOCK,
            loanLimit, balanceRequired, available,
            gap, monthsUntilBalance > 0 ? monthsUntilBalance : null,
            null, null));
      }
    }

    return comparisons;
  }

  /**
   * 중도금 임계선·조건부 안전마진 계산.
   *
   * 중도금 구간 통과에 필요한 최소 대출비율(분양가 대비)을 역산하고,
   * 공고문상 알선 비율과의 차이(안전마진)를 산출한다.
   */
  public InterimCriticalLineResponse calculateCriticalLine(
      UserConditionRequest user, Integer salePrice, PdfAnalysisResult analysisResult) {

    if (analysisResult == null || analysisResult.paymentSchedule() == null || salePrice == null || salePrice == 0) {
      return null;
    }

    PaymentSchedule schedule = analysisResult.paymentSchedule();
    int price = salePrice;

    int contractRequired = resolveStageAmount(schedule.downPayment(), price);
    int interimRequired = resolveStageAmount(schedule.interimPayment(), price);

    int cash = user.cash() != null ? user.cash() : 0;
    int cashAfterContract = Math.max(cash - contractRequired, 0);

    // 중도금 구간에서 대출 없이 자비로 감당할 수 없는 금액
    int criticalLoanAmount = Math.max(interimRequired - cashAfterContract, 0);
    double criticalLoanRatio = (double) criticalLoanAmount / price;

    // 공고문상 알선 정보
    InterimLoan loan = analysisResult.interimLoan();
    String arrangementStatus = loan != null ? loan.arrangementStatus() : null;

    Double arrangedRatio = null;
    Integer arrangedAmount = null;
    if (loan != null && !"NOT_AVAILABLE".equals(arrangementStatus)) {
      arrangedRatio = loan.arrangedRatio();
      if (loan.arrangedAmountManwon() != null) {
        arrangedAmount = loan.arrangedAmountManwon();
        if (arrangedRatio == null && price > 0) {
          arrangedRatio = (double) arrangedAmount / price;
        }
      } else if (arrangedRatio != null) {
        arrangedAmount = (int) (price * arrangedRatio);
      }
    }

    // 안전마진 계산
    Double safetyMarginPp = null;
    String safetyStatus;
    if (arrangedRatio != null) {
      safetyMarginPp = Math.round((arrangedRatio - criticalLoanRatio) * 100 * 10.0) / 10.0;
      safetyStatus = safetyMarginPp >= 0 ? "SAFE" : "WARNING";
    } else {
      safetyStatus = "UNKNOWN";
    }

    // 필요비율 소수점 2자리 반올림
    double roundedCriticalRatio = Math.round(criticalLoanRatio * 1000.0) / 1000.0;

    return new InterimCriticalLineResponse(
        roundedCriticalRatio,
        criticalLoanAmount,
        arrangedRatio,
        arrangedAmount,
        arrangementStatus,
        safetyMarginPp,
        safetyStatus,
        InterimCriticalLineResponse.DISCLAIMER_TEXT
    );
  }

  /**
   * 중도금 금융조달 확정도 조립.
   *
   * AI 분석 결과에서 확인/미확정 정보를 구분하고,
   * HOLD와 RiskClause의 nextAction을 수집하여 확인 질문 목록을 생성한다.
   */
  public InterimFinancingDetailResponse buildInterimFinancingDetail(
      PdfAnalysisResult analysisResult, List<HoldResponse> holds,
      List<RiskClauseResponse> riskClauses) {

    if (analysisResult == null) return null;

    PaymentSchedule schedule = analysisResult.paymentSchedule();
    InterimLoan loan = analysisResult.interimLoan();

    // ── confirmed ──
    Double interimTotalRatio = schedule != null && schedule.interimPayment() != null
        ? schedule.interimPayment().totalRatio() : null;
    Integer installmentCount = null;
    if (schedule != null && schedule.interimPayment() != null
        && schedule.interimPayment().installments() != null) {
      installmentCount = schedule.interimPayment().installments().size();
    }

    String arrangementStatus = loan != null ? loan.arrangementStatus() : null;
    Double arrangedRatio = loan != null ? loan.arrangedRatio() : null;

    Boolean selfFundingRequired = null;
    if (loan != null) {
      if (loan.selfFundingRatio() != null && loan.selfFundingRatio() > 0) {
        selfFundingRequired = true;
      } else if ("NOT_AVAILABLE".equals(arrangementStatus)) {
        selfFundingRequired = true;
      } else if (arrangedRatio != null && interimTotalRatio != null
          && arrangedRatio < interimTotalRatio) {
        selfFundingRequired = true;
      } else if (arrangedRatio != null) {
        selfFundingRequired = false;
      }
    }

    String interestType = loan != null ? loan.interestType() : null;

    ConfirmedInfo confirmed = new ConfirmedInfo(
        interimTotalRatio, installmentCount, arrangementStatus,
        arrangedRatio, selfFundingRequired, interestType
    );

    // ── unconfirmed ──
    List<String> bankNames = loan != null && loan.bankNames() != null
        ? loan.bankNames() : List.of();
    String guaranteeProvider = loan != null ? loan.guaranteeProvider() : null;
    String settlementRequirement = loan != null ? loan.settlementRequirement() : null;
    Boolean extensionDisclosed = loan != null ? loan.extensionContingencyDisclosed() : null;

    UnconfirmedInfo unconfirmed = new UnconfirmedInfo(
        bankNames, guaranteeProvider, null, extensionDisclosed, settlementRequirement
    );

    // ── 확인 질문 목록 수집 ──
    LinkedHashSet<String> questions = new LinkedHashSet<>();

    // HOLD(DOCUMENT_UNCERTAINTY)의 nextAction 수집
    if (holds != null) {
      for (HoldResponse h : holds) {
        if ("DOCUMENT_UNCERTAINTY".equals(h.kind()) && h.nextAction() != null) {
          questions.add(h.nextAction());
        }
      }
    }

    // RiskClause의 nextAction 수집
    if (riskClauses != null) {
      for (RiskClauseResponse rc : riskClauses) {
        if (rc.nextAction() != null) {
          questions.add(rc.nextAction());
        }
      }
    }

    return new InterimFinancingDetailResponse(
        confirmed, unconfirmed, List.copyOf(questions)
    );
  }

  // ── 내부 헬퍼 ──

  /** StagePayment에서 납부 기한을 추출한다. dueDate 우선, 없으면 installments의 마지막 회차. */
  private String resolveDueDate(StagePayment stage) {
    if (stage == null) return null;
    if (stage.dueDate() != null) return stage.dueDate();
    if (stage.installments() != null && !stage.installments().isEmpty()) {
      return stage.installments().get(stage.installments().size() - 1).dueDate();
    }
    return null;
  }

  /** v0.3 StagePayment에서 해당 구간의 필요금액을 산출한다. */
  private int resolveStageAmount(StagePayment stage, int salePrice) {
    if (stage == null) return 0;

    // 정액이 명시된 경우 우선 사용
    if (stage.totalAmountManwon() != null) {
      return stage.totalAmountManwon();
    }
    // 비율로 산출
    if (stage.totalRatio() != null) {
      return (int) (salePrice * stage.totalRatio());
    }
    return 0;
  }

  /** v0.3 InterimLoan에서 중도금 대출 가용 금액을 산출한다. */
  private int resolveInterimLoan(InterimLoan loan, int salePrice, int interimRequired) {
    if (loan == null) return 0;

    String status = loan.arrangementStatus();
    if ("NOT_AVAILABLE".equals(status)) return 0;

    // 정액 우선
    if (loan.arrangedAmountManwon() != null) {
      return loan.arrangedAmountManwon();
    }
    // 비율은 분양가 대비 (중도금 대비가 아님)
    if (loan.arrangedRatio() != null) {
      return (int) (salePrice * loan.arrangedRatio());
    }
    return 0;
  }

  /** 추가 비용 합산 — required가 null이 아니고 true이며 분양가 미포함인 항목만 */
  private int resolveAdditionalCosts(List<AdditionalCost> costs) {
    if (costs == null) return 0;
    return costs.stream()
        .filter(c -> Boolean.TRUE.equals(c.required()) && !Boolean.TRUE.equals(c.includedInSalePrice()))
        .mapToInt(c -> c.totalAmountManwon() != null ? c.totalAmountManwon() : 0)
        .sum();
  }

  /** 잔금 납부일까지 남은 개월 수 */
  private int resolveMonthsUntilBalance(StagePayment balance, List<HoldResponse> holds) {
    if (balance == null) return 0;

    String dateStr = balance.dueDate();
    if (dateStr == null && balance.installments() != null && !balance.installments().isEmpty()) {
      // installments의 마지막 회차 납부일 사용
      dateStr = balance.installments().get(balance.installments().size() - 1).dueDate();
    }

    if (dateStr != null) {
      try {
        LocalDate balanceDate = LocalDate.parse(dateStr);
        return Math.max((int) ChronoUnit.MONTHS.between(LocalDate.now(), balanceDate), 0);
      } catch (Exception e) {
        addHoldIfAbsent(holds, HoldReasonCode.BALANCE_DATE_PARSE_FAILED);
      }
    }
    return 0;
  }

  /** AI 분석 결과에 blocking hold가 있는지 확인 */
  private boolean hasBlockingHold(PdfAnalysisResult result) {
    if (result.holds() == null) return false;
    return result.holds().stream().anyMatch(AiHold::blocking);
  }

  /**
   * 단일 구간 판정.
   *
   * 1. 현금 + 대출 >= 필요액 → OK
   * 2. 부족 + 월 저축액 미입력 → HOLD
   * 3. 부족 + 저축으로 기한 내 해소 가능 → GAP
   * 4. 해소 불가 → BLOCK
   */
  private StageVerdictResponse judgeStage(Stage stage, int required, int cashAvailable,
                                        int loanAvailable, Integer monthlySaving,
                                        int monthsUntilDeadline, List<HoldResponse> holds,
                                        String dueDate) {
    int totalAvailable = cashAvailable + loanAvailable;
    String stageName = stageDisplayName(stage);

    if (totalAvailable >= required) {
      String reason = formatAmount(totalAvailable) + "으로 " + stageName + " " + formatAmount(required) + " 충당 가능";
      return new StageVerdictResponse(
        stage.name(), VerdictStatus.OK,
        required, totalAvailable, null,
        null, null, List.of(), List.of(), reason, dueDate
      );
    }

    int gap = required - totalAvailable;

    if (monthlySaving == null) {
      addHoldIfAbsent(holds, HoldReasonCode.NEED_MONTHLY_SAVING);
      String reason = stageName + " " + formatAmount(gap) + " 부족, 월 저축 가능액 입력 시 시나리오 확인 가능";
      return new StageVerdictResponse(
        stage.name(), VerdictStatus.HOLD,
        required, totalAvailable, gap,
        monthsUntilDeadline > 0 ? monthsUntilDeadline : null,
        null, List.of(), List.of(), reason, dueDate
      );
    }

    if (monthlySaving > 0) {
      int monthsNeeded = (int) Math.ceil((double) gap / monthlySaving);

      if (monthsUntilDeadline > 0 && monthsNeeded <= monthsUntilDeadline) {
        List<String> scenarios = List.of(
          "월 " + monthlySaving + "만 원 저축 시 " + monthsNeeded + "개월"
        );
        String reason = stageName + " " + formatAmount(gap) + " 부족, 월 " + formatAmount(monthlySaving) + " 저축 시 " + monthsNeeded + "개월 소요";
        return new StageVerdictResponse(
          stage.name(), VerdictStatus.GAP,
          required, totalAvailable, gap,
          monthsUntilDeadline, monthsNeeded, scenarios, List.of(), reason, dueDate
        );
      } else if (monthsUntilDeadline == 0) {
        // 기한 정보 없음 → 기한 제약 없이 GAP 처리
        List<String> scenarios = List.of(
          "월 " + monthlySaving + "만 원 저축 시 " + monthsNeeded + "개월"
        );
        String reason = stageName + " " + formatAmount(gap) + " 부족, 월 " + formatAmount(monthlySaving) + " 저축 시 " + monthsNeeded + "개월 소요";
        return new StageVerdictResponse(
          stage.name(), VerdictStatus.GAP,
          required, totalAvailable, gap,
          null, monthsNeeded, scenarios, List.of(), reason, dueDate
        );
      }
    }

    String reason = stageName + " " + formatAmount(gap) + " 부족, 현재 조건으로 기한 내 해소 불가";
    return new StageVerdictResponse(
      stage.name(), VerdictStatus.BLOCK,
      required, totalAvailable, gap,
      monthsUntilDeadline > 0 ? monthsUntilDeadline : null, null,
      List.of(), List.of(), reason, dueDate
    );
  }

  private static String stageDisplayName(Stage stage) {
    return switch (stage) {
      case CONTRACT -> "계약금";
      case INTERIM -> "중도금 자납분";
      case BALANCE -> "잔금";
    };
  }

  private static String formatAmount(int manwon) {
    if (manwon >= 10000) {
      int eok = manwon / 10000;
      int remainder = manwon % 10000;
      if (remainder == 0) return eok + "억 원";
      return eok + "억 " + String.format("%,d", remainder) + "만 원";
    }
    return String.format("%,d", manwon) + "만 원";
  }

  private void addHoldIfAbsent(List<HoldResponse> holds, HoldReasonCode reason) {
    boolean exists = holds.stream().anyMatch(h -> reason.name().equals(h.reasonCode()));
    if (!exists) {
      holds.add(reason.toHoldResponse());
    }
  }
}
