package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import aichallenge.getmyhome.verdict.dto.res.SubscriptionEligibilityResponse;
import aichallenge.getmyhome.verdict.enums.EvidenceRegistry;
import aichallenge.getmyhome.verdict.enums.HoldReasonCode;
import aichallenge.getmyhome.verdict.enums.MaritalStatus;
import aichallenge.getmyhome.verdict.enums.SubscriptionType;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 청약 자격 판정 서비스
 *
 * 3가지 유형(신혼부부/생애최초/일반)에 대해 자격 충족 여부를 판정한다.
 * 자금 금액과는 무관하며, 결과는 OK 또는 HOLD만 존재한다.
 * 자격 미달인 경우 해당 유형은 결과 리스트에 포함되지 않는다.
 */
@Service
public class SubscriptionEligibilityService {

  private static final String EVIDENCE_GENERAL = EvidenceRegistry.EV_RULE_005.getEvidenceId();
  private static final String EVIDENCE_NEWLYWED = EvidenceRegistry.EV_RULE_006.getEvidenceId();
  private static final String EVIDENCE_FIRST = EvidenceRegistry.EV_RULE_007.getEvidenceId();

  public List<SubscriptionEligibilityResponse> evaluate(UserConditionRequest user,
                                                      List<HoldResponse> holds) {
    List<SubscriptionEligibilityResponse> results = new ArrayList<>();

    evaluateSubNewlywed(user, results, holds);
    evaluateSubFirst(user, results, holds);
    evaluateSubGeneral(user, results, holds);

    return results;
  }

  // ─── 신혼부부 특별공급 ───

  private void evaluateSubNewlywed(UserConditionRequest user,
                                   List<SubscriptionEligibilityResponse> results,
                                   List<HoldResponse> holds) {
    if (user.marital() == MaritalStatus.SINGLE) return;
    if (!Boolean.TRUE.equals(user.homeless())) return;

    // 배우자 소득 미입력 → 합산 소득 판단 불가
    if (user.spouseIncome() == null) {
      HoldReasonCode reason = HoldReasonCode.NEED_SPOUSE_INCOME;
      results.add(new SubscriptionEligibilityResponse(
        SubscriptionType.SUB_NEWLYWED.name(), VerdictStatus.HOLD,
        reason.name(), List.of(EVIDENCE_NEWLYWED)
      ));
      holds.add(reason.toHoldResponse());
      return;
    }

    results.add(new SubscriptionEligibilityResponse(
      SubscriptionType.SUB_NEWLYWED.name(), VerdictStatus.OK,
      null, List.of(EVIDENCE_NEWLYWED)
    ));
  }

  // ─── 생애최초 특별공급 ───

  private void evaluateSubFirst(UserConditionRequest user,
                                List<SubscriptionEligibilityResponse> results,
                                List<HoldResponse> holds) {
    if (!Boolean.TRUE.equals(user.homeless())) return;

    if (user.firstTimeBuyer() == null) {
      HoldReasonCode reason = HoldReasonCode.NEED_FIRST_TIME_BUYER_INFO;
      results.add(new SubscriptionEligibilityResponse(
        SubscriptionType.SUB_FIRST.name(), VerdictStatus.HOLD,
        reason.name(), List.of(EVIDENCE_FIRST)
      ));
      holds.add(reason.toHoldResponse());
      return;
    }

    if (!Boolean.TRUE.equals(user.firstTimeBuyer())) return;

    results.add(new SubscriptionEligibilityResponse(
      SubscriptionType.SUB_FIRST.name(), VerdictStatus.OK,
      null, List.of(EVIDENCE_FIRST)
    ));
  }

  // ─── 일반공급 ───

  private void evaluateSubGeneral(UserConditionRequest user,
                                  List<SubscriptionEligibilityResponse> results,
                                  List<HoldResponse> holds) {
    if (!Boolean.TRUE.equals(user.homeless())) return;

    if (user.subscriptionAccount() == null) {
      HoldReasonCode reason = HoldReasonCode.NEED_SUBSCRIPTION_INFO;
      boolean alreadyHeld = holds.stream()
        .anyMatch(h -> reason.name().equals(h.reasonCode()));
      if (!alreadyHeld) {
        holds.add(reason.toHoldResponse());
      }
      results.add(new SubscriptionEligibilityResponse(
        SubscriptionType.SUB_GENERAL.name(), VerdictStatus.HOLD,
        reason.name(), List.of(EVIDENCE_GENERAL)
      ));
      return;
    }

    results.add(new SubscriptionEligibilityResponse(
      SubscriptionType.SUB_GENERAL.name(), VerdictStatus.OK,
      null, List.of(EVIDENCE_GENERAL)
    ));
  }
}
