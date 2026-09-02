package aichallenge.getmyhome.verdict.enums;

import aichallenge.getmyhome.verdict.dto.res.HoldResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * HOLD 사유 코드 — 추가 정보 입력이 필요한 경우 사용
 */
@Getter
@RequiredArgsConstructor
public enum HoldReasonCode {

    NEED_SPOUSE_INCOME("배우자 연소득을 입력해 주세요."),
    NEED_HOUSEHOLD_INFO("세대 구성, 세대원 전원 무주택 여부, 순자산 정보를 입력해 주세요."),
    NEED_FIRST_TIME_INFO("생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요."),
    NEED_NEWLYWED_INFO("신혼부부 관련 정보(배우자 소득, 세대 구성, 순자산 등)를 입력해 주세요."),
    NEED_SUBSCRIPTION_INFO("청약통장 정보를 입력해 주세요."),
    NEED_YOUTH_NEWLYWED_INFO("배우자 소득 및 청약통장 정보를 입력해 주세요."),
    NEED_FIRST_TIME_BUYER_INFO("생애최초 주택 구입 여부를 입력해 주세요."),
    NEED_MONTHLY_SAVING("월 저축 가능액을 입력하면 부족분 해소 시나리오를 확인할 수 있습니다."),
    COMPLEX_NOT_ANALYZED("공고문 분석이 완료되지 않았습니다. 시행사에 직접 확인해 보세요."),
    COMPLEX_FETCH_FAILED("단지 정보 조회에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    AI_SERVER_FAILED("공고문 분석 서버에 일시적 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    CRAWLER_FAILED("공고문 PDF 수집에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    BALANCE_DATE_PARSE_FAILED("잔금 납부일 형식을 인식할 수 없어 기한 기반 시나리오가 제외되었습니다.");

    private final String nextAction;

    public HoldResponse toHoldResponse() {
        return new HoldResponse(this.name(), this.nextAction, this.nextAction, null, null);
    }
}
