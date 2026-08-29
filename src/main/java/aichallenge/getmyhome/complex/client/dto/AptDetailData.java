package aichallenge.getmyhome.complex.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * APT 분양 공고 상세 데이터 (분양정보 조회 서비스 — API 승인 대기 중)
 *
 * 주요 필드:
 * - houseNm: 주택명 (단지명)
 * - houseSecd/houseDtlSecd: 주택구분/상세구분 코드·명 (APT, 민영, 국민 등)
 * - subscrptAreaCodeNm: 공급지역명 (서울, 경기 등)
 * - hssplyAdres: 공급위치 주소
 * - totSuplyHshldco: 총 공급세대수
 * - rcritPblancDe: 모집공고일
 * - rceptBgnde/rceptEndde: 청약접수 시작일/종료일
 * - spsplyRceptBgnde/spsplyRceptEndde: 특별공급 접수 시작일/종료일
 * - gnrlRnk1.../gnrlRnk2...: 1순위/2순위 해당지역·기타경기·기타지역 접수일
 * - przwnerPresnatnDe: 당첨자 발표일
 * - cntrctCnclsBgnde/cntrctCnclsEndde: 계약 시작일/종료일
 * - specltRdnEarthAt: 투기과열지구 여부
 * - pblancUrl: 공고문 URL
 */
public record AptDetailData(
        @JsonProperty("HOUSE_MANAGE_NO") String houseManageNo,
        @JsonProperty("PBLANC_NO") String pblancNo,
        @JsonProperty("HOUSE_NM") String houseNm,
        @JsonProperty("HOUSE_SECD") String houseSecd,
        @JsonProperty("HOUSE_SECD_NM") String houseSecdNm,
        @JsonProperty("HOUSE_DTL_SECD") String houseDtlSecd,
        @JsonProperty("HOUSE_DTL_SECD_NM") String houseDtlSecdNm,
        @JsonProperty("RENT_SECD") String rentSecd,
        @JsonProperty("RENT_SECD_NM") String rentSecdNm,
        @JsonProperty("SUBSCRPT_AREA_CODE") String subscrptAreaCode,
        @JsonProperty("SUBSCRPT_AREA_CODE_NM") String subscrptAreaCodeNm,
        @JsonProperty("HSSPLY_ZIP") String hssplyZip,
        @JsonProperty("HSSPLY_ADRES") String hssplyAdres,
        @JsonProperty("TOT_SUPLY_HSHLDCO") Integer totSuplyHshldco,
        @JsonProperty("RCRIT_PBLANC_DE") String rcritPblancDe,
        @JsonProperty("NSPRC_NM") String nsprcNm,
        @JsonProperty("RCEPT_BGNDE") String rceptBgnde,
        @JsonProperty("RCEPT_ENDDE") String rceptEndde,
        @JsonProperty("SPSPLY_RCEPT_BGNDE") String spsplyRceptBgnde,
        @JsonProperty("SPSPLY_RCEPT_ENDDE") String spsplyRceptEndde,
        @JsonProperty("GNRL_RNK1_CRSPAREA_RCPTDE") String gnrlRnk1CrspareaRcptde,
        @JsonProperty("GNRL_RNK1_CRSPAREA_ENDDE") String gnrlRnk1CrspareaEndde,
        @JsonProperty("GNRL_RNK1_ETC_GG_RCPTDE") String gnrlRnk1EtcGgRcptde,
        @JsonProperty("GNRL_RNK1_ETC_GG_ENDDE") String gnrlRnk1EtcGgEndde,
        @JsonProperty("GNRL_RNK1_ETC_AREA_RCPTDE") String gnrlRnk1EtcAreaRcptde,
        @JsonProperty("GNRL_RNK1_ETC_AREA_ENDDE") String gnrlRnk1EtcAreaEndde,
        @JsonProperty("GNRL_RNK2_CRSPAREA_RCPTDE") String gnrlRnk2CrspareaRcptde,
        @JsonProperty("GNRL_RNK2_CRSPAREA_ENDDE") String gnrlRnk2CrspareaEndde,
        @JsonProperty("GNRL_RNK2_ETC_GG_RCPTDE") String gnrlRnk2EtcGgRcptde,
        @JsonProperty("GNRL_RNK2_ETC_GG_ENDDE") String gnrlRnk2EtcGgEndde,
        @JsonProperty("GNRL_RNK2_ETC_AREA_RCPTDE") String gnrlRnk2EtcAreaRcptde,
        @JsonProperty("GNRL_RNK2_ETC_AREA_ENDDE") String gnrlRnk2EtcAreaEndde,
        @JsonProperty("PRZWNER_PRESNATN_DE") String przwnerPresnatnDe,
        @JsonProperty("CNTRCT_CNCLS_BGNDE") String cntrctCnclsBgnde,
        @JsonProperty("CNTRCT_CNCLS_ENDDE") String cntrctCnclsEndde,
        @JsonProperty("HMPG_ADRES") String hmpgAdres,
        @JsonProperty("CNSTRCT_ENTRPS_NM") String cnstrctEntrpsNm,
        @JsonProperty("MDHS_TELNO") String mdhsTelno,
        @JsonProperty("BSNS_MBY_NM") String bsnsMbyNm,
        @JsonProperty("MVN_PREARNGE_YM") String mvnPrearngeYm,
        @JsonProperty("SPECLT_RDN_EARTH_AT") String specltRdnEarthAt,
        @JsonProperty("MDAT_TRGET_AREA_SECD") String mdatTrgetAreaSecd,
        @JsonProperty("PARCPRC_ULS_AT") String parcprcUlsAt,
        @JsonProperty("IMPRMN_BSNS_AT") String imprmnBsnsAt,
        @JsonProperty("PUBLIC_HOUSE_EARTH_AT") String publicHouseEarthAt,
        @JsonProperty("LRSCL_BLDLND_AT") String lrsclBldlndAt,
        @JsonProperty("NPLN_PRVOPR_PUBLIC_HOUSE_AT") String nplnPrvoprPublicHouseAt,
        @JsonProperty("PUBLIC_HOUSE_SPCLM_APPLC_APT") String publicHouseSpclmApplcApt,
        @JsonProperty("PBLANC_URL") String pblancUrl
) {
}