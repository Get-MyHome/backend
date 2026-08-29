package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.dto.PdfAnalysisResult;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.EvidenceResponse;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResponse;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.dto.res.StageVerdictResponse;
import aichallenge.getmyhome.verdict.enums.EvidenceRegistry;
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.enums.Stage;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
                                            List<HoldResponse> holds,
                                            List<EvidenceResponse> evidence) {
    if (analysisResult == null || analysisResult.paymentSchedule() == null) {
      holds.add(HoldReasonCode.COMPLEX_NOT_ANALYZED.toHoldResponse());
      return List.of();
    }

    // OK 상태인 상품 중 최대 대출 한도 — 잔금 단계에서 사용
    int maxFinancingLimit = financingRoutes.stream()
      .filter(r -> r.status() == VerdictStatus.OK && r.limitMax() != null)
      .mapToInt(FinancingRouteResponse::limitMax)
      .max()
      .orElse(0);

    EvidenceRegistry.addIfAbsent(evidence, EvidenceRegistry.EV_SRC_001);
    EvidenceRegistry.addIfAbsent(evidence, EvidenceRegistry.EV_CALC_001);

    PdfAnalysisResult.PaymentSchedule schedule = analysisResult.paymentSchedule();

    // 잔금 기한까지 남은 개월 수 — GAP 판정 시 저축 해소 가능 여부 판단에 사용
    int monthsUntilBalance = 0;
    if (schedule.balanceDueDate() != null) {
      try {
        LocalDate balanceDate = LocalDate.parse(schedule.balanceDueDate());
        monthsUntilBalance = (int) ChronoUnit.MONTHS.between(LocalDate.now(), balanceDate);
        monthsUntilBalance = Math.max(monthsUntilBalance, 0);
      } catch (Exception e) {
        addHoldIfAbsent(holds, HoldReasonCode.BALANCE_DATE_PARSE_FAILED);
      }
    }

    int price = salePrice != null ? salePrice : 0;
    int contractRatioPercent = toPercent(schedule.downPaymentRatio());
    int interimRatioPercent = toPercent(schedule.interimPaymentRatio());
    int interimLoanRatioPercent = toPercent(schedule.interimLoanRatio());

    int additionalCosts = analysisResult.additionalCosts() != null
      ? analysisResult.additionalCosts().stream()
          .mapToInt(c -> c.amount() != null ? c.amount() : 0)
          .sum()
      : 0;

    return calculateWithComplexData(
      user, price, additionalCosts,
      contractRatioPercent, interimRatioPercent, interimLoanRatioPercent,
      monthsUntilBalance, maxFinancingLimit, holds
    );
  }

  /**
   * 계약금 → 중도금 → 잔금 순서대로 판정을 수행한다.
   */
  List<StageVerdictResponse> calculateWithComplexData(
    UserConditionRequest user,
    int salePrice, int additionalCosts,
    int contractRatioPercent, int interimRatioPercent,
    int interimLoanRatioPercent, int monthsUntilBalance,
    int maxFinancingLimit, List<HoldResponse> holds) {

    List<StageVerdictResponse> results = new ArrayList<>();

    int balanceRatioPercent = 100 - contractRatioPercent - interimRatioPercent;

    int contractRequired = salePrice * contractRatioPercent / 100;
    int interimRequired = salePrice * interimRatioPercent / 100;
    int interimLoanAvailable = interimRequired * interimLoanRatioPercent / 100;
    int interimSelfRequired = interimRequired - interimLoanAvailable;
    int balanceRequired = salePrice * balanceRatioPercent / 100 + additionalCosts;

    int cash = user.cash() != null ? user.cash() : 0;
    Integer monthlySaving = user.monthlySaving();

    // 계약금 → 남은 현금으로 중도금 → 남은 현금 + 대출로 잔금
    results.add(judgeStage(Stage.CONTRACT, contractRequired, cash, 0, monthlySaving, 0, holds));
    int cashAfterContract = Math.max(cash - contractRequired, 0);

    results.add(judgeStage(Stage.INTERIM, interimSelfRequired, cashAfterContract, 0, monthlySaving, 0, holds));
    int cashAfterInterim = Math.max(cashAfterContract - interimSelfRequired, 0);

    results.add(judgeStage(Stage.BALANCE, balanceRequired, cashAfterInterim,
      maxFinancingLimit, monthlySaving, monthsUntilBalance, holds));

    return results;
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
                                        int monthsUntilDeadline, List<HoldResponse> holds) {
    int totalAvailable = cashAvailable + loanAvailable;

    if (totalAvailable >= required) {
      return new StageVerdictResponse(
        stage.name(), VerdictStatus.OK,
        required, totalAvailable, null,
        null, null, List.of(), List.of()
      );
    }

    int gap = required - totalAvailable;

    if (monthlySaving == null) {
      addHoldIfAbsent(holds, HoldReasonCode.NEED_MONTHLY_SAVING);
      return new StageVerdictResponse(
        stage.name(), VerdictStatus.HOLD,
        required, totalAvailable, gap,
        monthsUntilDeadline > 0 ? monthsUntilDeadline : null,
        null, List.of(), List.of()
      );
    }

    if (monthlySaving > 0) {
      int monthsNeeded = (int) Math.ceil((double) gap / monthlySaving);

      if (monthsUntilDeadline > 0 && monthsNeeded <= monthsUntilDeadline) {
        List<String> scenarios = List.of(
          "월 " + monthlySaving + "만 원 저축 시 " + monthsNeeded + "개월"
        );
        return new StageVerdictResponse(
          stage.name(), VerdictStatus.GAP,
          required, totalAvailable, gap,
          monthsUntilDeadline, monthsNeeded, scenarios, List.of()
        );
      } else if (monthsUntilDeadline == 0) {
        // 기한 정보 없음 → 기한 제약 없이 GAP 처리
        List<String> scenarios = List.of(
          "월 " + monthlySaving + "만 원 저축 시 " + monthsNeeded + "개월"
        );
        return new StageVerdictResponse(
          stage.name(), VerdictStatus.GAP,
          required, totalAvailable, gap,
          null, monthsNeeded, scenarios, List.of()
        );
      }
    }

    return new StageVerdictResponse(
      stage.name(), VerdictStatus.BLOCK,
      required, totalAvailable, gap,
      monthsUntilDeadline > 0 ? monthsUntilDeadline : null, null,
      List.of(), List.of()
    );
  }

  private void addHoldIfAbsent(List<HoldResponse> holds, HoldReasonCode reason) {
    boolean exists = holds.stream().anyMatch(h -> reason.name().equals(h.reasonCode()));
    if (!exists) {
      holds.add(reason.toHoldResponse());
    }
  }

  private int toPercent(Double ratio) {
    if (ratio == null) return 0;
    return (int) (ratio * 100);
  }
}