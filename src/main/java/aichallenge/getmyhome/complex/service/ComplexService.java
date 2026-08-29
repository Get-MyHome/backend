package aichallenge.getmyhome.complex.service;

import aichallenge.getmyhome.complex.client.ApplyhomeApiClient;
import aichallenge.getmyhome.complex.client.dto.ApplyhomeApiResponse;
import aichallenge.getmyhome.complex.client.dto.AptDetailData;
import aichallenge.getmyhome.complex.client.dto.AptDetailMdlData;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse.*;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse.ComplexSummary;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 청약 공고 통합 서비스
 */
@Service
@RequiredArgsConstructor
public class ComplexService {

    private final ApplyhomeApiClient applyhomeApiClient;

    // ── 공고 목록/상세 ──

    @Cacheable(value = "complexList",
            key = "T(String).valueOf(#region) + ':' + #page + ':' + #size")
    public ComplexListResponse getComplexes(String region, int page, int size) {
        ApplyhomeApiResponse<AptDetailData> apiResponse =
                applyhomeApiClient.getAptDetail(page, size, null, null, null, null, region, null, null);

        List<ComplexSummary> items = safeData(apiResponse).stream()
                .map(this::toSummary)
                .toList();

        return new ComplexListResponse(items, apiResponse.totalCount(), apiResponse.page(), size);
    }

    @Cacheable(value = "complexDetail", key = "#complexId")
    public ComplexDetailResponse getComplexDetail(String complexId) {
        // 1. 청약홈 API — 기본 공고 정보
        ApplyhomeApiResponse<AptDetailData> detailResponse =
                applyhomeApiClient.getAptDetail(1, 1, complexId, null, null, null, null, null, null);

        List<AptDetailData> detailData = safeData(detailResponse);
        if (detailData.isEmpty()) {
            throw BaseException.of(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR, "해당 공고를 찾을 수 없습니다.");
        }

        AptDetailData detail = detailData.get(0);

        // 2. 청약홈 API — 주택형(평형) 정보
        ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                applyhomeApiClient.getAptDetailMdl(1, 100, complexId, detail.pblancNo());

        List<UnitType> unitTypes = safeData(mdlResponse).stream()
                .map(mdl -> new UnitType(
                        mdl.modelNo(),
                        mdl.houseTy(),
                        parseSalePrice(mdl.lttotTopAmount()),
                        mdl.suplyAr()
                ))
                .toList();

        Integer representativeSalePrice = unitTypes.isEmpty() ? null : unitTypes.get(0).salePrice();

        return new ComplexDetailResponse(
                detail.houseManageNo(),
                detail.houseNm(),
                detail.houseDtlSecdNm(),
                detail.subscrptAreaCodeNm(),
                detail.hssplyAdres(),
                detail.rcritPblancDe(),
                detail.rceptEndde(),
                representativeSalePrice,
                unitTypes,
                mapRegulationZone(detail),
                buildSourceUrl(detail)
        );
    }

    // ── 내부 유틸 ──

    private ComplexSummary toSummary(AptDetailData data) {
        return new ComplexSummary(
                data.houseManageNo(),
                data.houseNm(),
                data.houseDtlSecdNm(),
                data.subscrptAreaCodeNm(),
                data.hssplyAdres(),
                data.rcritPblancDe(),
                data.rceptEndde(),
                data.mvnPrearngeYm(),
                true
        );
    }

    private Integer parseSalePrice(String lttotTopAmount) {
        if (lttotTopAmount == null || lttotTopAmount.isBlank()) return null;
        try {
            return Integer.parseInt(lttotTopAmount.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildSourceUrl(AptDetailData data) {
        String url = data.pblancUrl();
        if (url != null && !url.isBlank() && !url.equals("https://www.applyhome.co.kr")) {
            return url;
        }
        return "https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancDetail.do"
                + "?houseManageNo=" + data.houseManageNo()
                + "&pblancNo=" + data.pblancNo();
    }

    private <T> List<T> safeData(ApplyhomeApiResponse<T> response) {
        return response.data() != null ? response.data() : List.of();
    }

    private String mapRegulationZone(AptDetailData data) {
        if ("Y".equals(data.specltRdnEarthAt())) return "투기과열지구";
        if ("Y".equals(data.parcprcUlsAt())) return "분양가상한제";
        return null;
    }
}