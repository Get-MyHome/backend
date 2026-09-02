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

    NEED_SPOUSE_INCOME("배우자 연소득이 입력되지 않아 일부 대출 한도를 산정할 수 없습니다.", "배우자 연소득을 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_HOUSEHOLD_INFO("세대 구성 정보가 입력되지 않아 자격 판정이 제한됩니다.", "세대 구성, 세대원 전원 무주택 여부, 순자산 정보를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_FIRST_TIME_INFO("생애최초 관련 정보가 입력되지 않아 자격 판정이 제한됩니다.", "생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_NEWLYWED_INFO("신혼부부 관련 정보가 입력되지 않아 자격 판정이 제한됩니다.", "신혼부부 관련 정보(배우자 소득, 세대 구성, 순자산 등)를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_SUBSCRIPTION_INFO("청약통장 정보가 입력되지 않아 청약 자격 판정이 제한됩니다.", "청약통장 정보를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_YOUTH_NEWLYWED_INFO("배우자 소득 및 청약통장 정보가 입력되지 않아 판정이 제한됩니다.", "배우자 소득 및 청약통장 정보를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_FIRST_TIME_BUYER_INFO("생애최초 주택 구입 여부가 입력되지 않아 자격 판정이 제한됩니다.", "생애최초 주택 구입 여부를 입력해 주세요.", "PERSONAL_REVIEW", false),
    NEED_MONTHLY_SAVING("월 저축 가능액이 입력되지 않아 부족분 해소 시나리오를 산출할 수 없습니다.", "월 저축 가능액을 입력하면 부족분 해소 시나리오를 확인할 수 있습니다.", "PERSONAL_REVIEW", false),
    COMPLEX_NOT_ANALYZED("공고문 분석이 완료되지 않아 구간별 자금 판정이 보류되었습니다.", "시행사에 직접 확인해 보세요.", "DOCUMENT_UNCERTAINTY", true),
    AI_REVIEW_PENDING("AI 분석 결과가 아직 검수되지 않아 구간별 자금 판정이 보류되었습니다.", "검수 완료 후 다시 판정해 주세요.", "DOCUMENT_UNCERTAINTY", true),
    COMPLEX_FETCH_FAILED("단지 정보 조회에 실패했습니다.", "잠시 후 다시 시도해 주세요.", "SYSTEM_ERROR", true),
    AI_SERVER_FAILED("공고문 분석 서버에 일시적 오류가 발생했습니다.", "잠시 후 다시 시도해 주세요.", "SYSTEM_ERROR", true),
    CRAWLER_FAILED("공고문 PDF 수집에 실패했습니다.", "잠시 후 다시 시도해 주세요.", "SYSTEM_ERROR", true),
    BALANCE_CONVERSION_UNCERTAIN("입주 시 중도금 대출 처리 방식이 공고문에 명시되지 않았습니다.", "시행사 또는 취급은행에 중도금 대출 처리 방식을 확인해 주세요.", "DOCUMENT_UNCERTAINTY", true),
    BALANCE_DATE_PARSE_FAILED("잔금 납부일 형식을 인식할 수 없어 기한 기반 시나리오가 제외되었습니다.", "잔금 납부일을 시행사에 확인해 주세요.", "DOCUMENT_UNCERTAINTY", false);

    private final String message;
    private final String nextAction;
    private final String kind;
    private final boolean blocking;

    public HoldResponse toHoldResponse() {
        return new HoldResponse(this.name(), this.message, this.nextAction, this.kind, this.blocking, null);
    }
}
