package aichallenge.getmyhome.complex.service;

import aichallenge.getmyhome.complex.client.ApplyhomeApiClient;
import aichallenge.getmyhome.complex.client.dto.ApplyhomeApiResponse;
import aichallenge.getmyhome.complex.client.dto.AptDetailData;
import aichallenge.getmyhome.complex.client.dto.AptDetailMdlData;
import aichallenge.getmyhome.complex.enums.HouseCategory;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse.*;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse.ComplexSummary;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 청약 공고 통합 서비스
 */
@Service
@RequiredArgsConstructor
public class ComplexService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplyhomeApiClient applyhomeApiClient;

    // ── 공고 목록/상세 ──

    @Cacheable(value = "complexList",
            key = "(#region ?: 'ALL') + ':' + (#houseCategory ?: 'ALL') + ':' + #page + ':' + #size")
    public ComplexListResponse getComplexes(String region, HouseCategory houseCategory,
                                            int page, int size) {
        String normalizedRegion = (region != null && !region.isBlank()) ? region : null;
        String houseDtlSecd = houseCategory != null ? houseCategory.getHouseDtlSecd() : null;

        ApplyhomeApiResponse<AptDetailData> apiResponse =
                applyhomeApiClient.getAptDetail(page, size, null, null, null, houseDtlSecd,
                        normalizedRegion, null, null,
                        null, null, null, null);

        List<AptDetailData> dataList = safeData(apiResponse);

        // 각 공고의 분양가를 병렬로 조회
        Map<String, Integer> salePriceMap = fetchSalePrices(dataList);

        List<ComplexSummary> items = dataList.stream()
                .map(data -> toSummary(data, salePriceMap.get(data.houseManageNo())))
                .toList();

        String updatedAt = LocalDateTime.now(KST).format(KST_FORMATTER);

        return new ComplexListResponse(items, apiResponse.matchCount(), apiResponse.page(), size, updatedAt);
    }

    @Cacheable(value = "complexDetail", key = "#complexId")
    public ComplexDetailResponse getComplexDetail(String complexId) {
        // 1. 청약홈 API — 기본 공고 정보
        ApplyhomeApiResponse<AptDetailData> detailResponse =
                applyhomeApiClient.getAptDetail(1, 1, complexId, null, null, null, null, null, null,
                        null, null, null, null);

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
        String updatedAt = LocalDateTime.now(KST).format(KST_FORMATTER);

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
                buildSourceUrl(detail),
                updatedAt
        );
    }

    // ── 내부 유틸 ──

    private Map<String, Integer> fetchSalePrices(List<AptDetailData> dataList) {
        Map<String, CompletableFuture<Integer>> futures = new java.util.HashMap<>();

        for (AptDetailData data : dataList) {
            futures.put(data.houseManageNo(), CompletableFuture.supplyAsync(() -> {
                try {
                    ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                            applyhomeApiClient.getAptDetailMdl(1, 1, data.houseManageNo(), data.pblancNo());
                    List<AptDetailMdlData> mdlData = safeData(mdlResponse);
                    return mdlData.isEmpty() ? null : parseSalePrice(mdlData.get(0).lttotTopAmount());
                } catch (Exception e) {
                    return null;
                }
            }));
        }

        Map<String, Integer> result = new java.util.HashMap<>();
        futures.forEach((key, future) -> result.put(key, future.join()));
        return result;
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice) {
        return new ComplexSummary(
                data.houseManageNo(),
                data.houseNm(),
                data.houseDtlSecdNm(),
                data.subscrptAreaCodeNm(),
                data.hssplyAdres(),
                data.rcritPblancDe(),
                data.rceptEndde(),
                data.mvnPrearngeYm(),
                salePrice,
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