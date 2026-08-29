package aichallenge.getmyhome.complex.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * APT 분양 모델(주택형) 정보 — 공고 내 주택형별 세부 데이터
 *
 * @param modelNo         모델번호
 * @param houseTy         주택형 (예: "084.9800")
 * @param suplyAr         공급면적 (㎡)
 * @param suplyHshldco    공급세대수
 * @param spsplyHshldco   특별공급 세대수
 * @param mnychHshldco    다자녀 세대수
 * @param nwwdsHshldco    신혼부부 세대수
 * @param lfeFrstHshldco  생애최초 세대수
 * @param oldParntsSuportHshldco 노부모부양 세대수
 * @param insttRecomendHshldco   기관추천 세대수
 * @param etcHshldco      기타 세대수
 * @param transrInsttEnfsnHshldco 이전기관 세대수
 * @param ygmnHshldco     청년 세대수
 * @param nwbbHshldco     신생아 세대수
 * @param lttotTopAmount  분양최고금액 (만원)
 */
public record AptDetailMdlData(
        @JsonProperty("HOUSE_MANAGE_NO") String houseManageNo,
        @JsonProperty("PBLANC_NO") String pblancNo,
        @JsonProperty("MODEL_NO") String modelNo,
        @JsonProperty("HOUSE_TY") String houseTy,
        @JsonProperty("SUPLY_AR") String suplyAr,
        @JsonProperty("SUPLY_HSHLDCO") Integer suplyHshldco,
        @JsonProperty("SPSPLY_HSHLDCO") Integer spsplyHshldco,
        @JsonProperty("MNYCH_HSHLDCO") Integer mnychHshldco,
        @JsonProperty("NWWDS_HSHLDCO") Integer nwwdsHshldco,
        @JsonProperty("LFE_FRST_HSHLDCO") Integer lfeFrstHshldco,
        @JsonProperty("OLD_PARNTS_SUPORT_HSHLDCO") Integer oldParntsSuportHshldco,
        @JsonProperty("INSTT_RECOMEND_HSHLDCO") Integer insttRecomendHshldco,
        @JsonProperty("ETC_HSHLDCO") Integer etcHshldco,
        @JsonProperty("TRANSR_INSTT_ENFSN_HSHLDCO") Integer transrInsttEnfsnHshldco,
        @JsonProperty("YGMN_HSHLDCO") Integer ygmnHshldco,
        @JsonProperty("NWBB_HSHLDCO") Integer nwbbHshldco,
        @JsonProperty("LTTOT_TOP_AMOUNT") String lttotTopAmount
) {
}