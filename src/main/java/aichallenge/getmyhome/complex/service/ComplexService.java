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
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteDetailResponse;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import aichallenge.getmyhome.verdict.service.FinancingRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 청약 공고 통합 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexService {

    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplyhomeApiClient applyhomeApiClient;
    private final ComplexCacheService complexCacheService;
    private final CacheManager cacheManager;
    private final FinancingRouteService financingRouteService;
    private final RuleProperties ruleProperties;

    // ── 캐시 초기화 ──

    public void evictAllComplexCaches() {
        Cache complexList = cacheManager.getCache("complexList");
        if (complexList != null) complexList.clear();
        Cache complexDetail = cacheManager.getCache("complexDetail");
        if (complexDetail != null) complexDetail.clear();
    }

    // ── 공고 목록/상세 ──

    public ComplexListResponse getComplexes(String region, HouseCategory houseCategory,
                                            int page, int size) {
        List<AptDetailData> allData = getCachedComplexData();

        // 메모리에서 필터링
        List<AptDetailData> filtered = filterComplexData(allData, region, houseCategory);

        // 메모리에서 페이지네이션
        int total = filtered.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<AptDetailData> pageData = filtered.subList(fromIndex, toIndex);

        // 캐시에 없는 공고만 MDL 조회
        List<AptDetailData> uncached = pageData.stream()
                .filter(data -> complexCacheService.getCachedSalePrice(data.houseManageNo()) == null)
                .toList();

        Map<String, List<AptDetailMdlData>> mdlMap = uncached.isEmpty()
                ? Map.of() : complexCacheService.fetchMdlData(uncached);
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        List<ComplexSummary> pageItems = pageData.stream()
                .map(data -> {
                    // 1. 상세 캐시에서 분양가 조회
                    Integer salePrice = complexCacheService.getCachedSalePrice(data.houseManageNo());
                    if (salePrice != null) {
                        return toSummary(data, salePrice);
                    }
                    // 2. 캐시 miss → MDL 결과에서 분양가 추출
                    List<AptDetailMdlData> mdlList = mdlMap.getOrDefault(data.houseManageNo(), List.of());
                    salePrice = mdlList.isEmpty() ? null : complexCacheService.parseSalePrice(mdlList.get(0).lttotTopAmount());
                    complexCacheService.preCacheDetail(data, mdlList, updatedAt);
                    return toSummary(data, salePrice);
                })
                .toList();

        return new ComplexListResponse(pageItems, total, page, size, updatedAt);
    }

    public List<AptDetailData> getCachedComplexData() {
        return complexCacheService.getCachedComplexData();
    }

    @Cacheable(value = "complexDetail", key = "#complexId")
    public ComplexDetailResponse getComplexDetail(String complexId) {
        // 1. 청약홈 API — 기본 공고 정보
        ApplyhomeApiResponse<AptDetailData> detailResponse =
                applyhomeApiClient.getAptDetail(1, 1, complexId, null, null, null, null, null, null,
                        null, null, null, null);

        List<AptDetailData> detailData = detailResponse != null && detailResponse.data() != null
                ? detailResponse.data() : List.of();
        if (detailData.isEmpty()) {
            throw BaseException.of(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR, "해당 공고를 찾을 수 없습니다.");
        }

        AptDetailData detail = detailData.get(0);

        // 2. 청약홈 API — 주택형(평형) 정보
        ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                applyhomeApiClient.getAptDetailMdl(1, 100, complexId, detail.pblancNo());

        List<AptDetailMdlData> mdlList = mdlResponse != null && mdlResponse.data() != null
                ? mdlResponse.data() : List.of();

        return complexCacheService.buildDetailResponse(detail, mdlList);
    }

    // ── 대출 매칭 공고 조회 ──

    public ComplexListResponse getMatchedComplexes(String conditionToken,
                                                    UserConditionRequest userDirect,
                                                    String region, HouseCategory houseCategory,
                                                    int page, int size) {
        // 토큰 우선, 없으면 직접 전달된 user 사용
        UserConditionRequest user = null;
        if (conditionToken != null && !conditionToken.isBlank()) {
            user = financingRouteService.getCondition(conditionToken);
            if (user == null) {
                throw BaseException.of(VerdictErrorCode.CONDITION_TOKEN_EXPIRED);
            }
        } else {
            user = userDirect;
        }
        if (user == null) {
            throw BaseException.of(GlobalErrorCode.BAD_REQUEST,
                "conditionToken 또는 user 중 하나는 필수입니다.");
        }

        RuleVersion rule = ruleProperties.resolve(null);

        // 1단계: 캐시된 전체 공고에서 필터링 (API 호출 없음)
        List<AptDetailData> allData = getCachedComplexData();
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        List<AptDetailData> filtered = filterComplexData(allData, region, houseCategory);

        // 2단계: 대출 매칭 필터링 (salePrice 없이 1회 판정)
        List<FinancingRouteDetailResponse> routes =
            financingRouteService.evaluateWithReasons(user, null, rule);

        List<String> matchedNames = routes.stream()
            .filter(r -> r.status() == VerdictStatus.OK || r.status() == VerdictStatus.HOLD)
            .map(FinancingRouteDetailResponse::productName)
            .toList();

        if (matchedNames.isEmpty()) {
            return new ComplexListResponse(List.of(), 0, page, size, updatedAt);
        }

        List<ComplexSummary> allMatched = filtered.stream()
            .map(data -> toSummary(data, null, matchedNames))
            .toList();

        // 3단계: 직접 페이지네이션
        int matchedTotal = allMatched.size();
        int fromIndex = Math.min((page - 1) * size, matchedTotal);
        int toIndex = Math.min(fromIndex + size, matchedTotal);
        List<ComplexSummary> pageItems = allMatched.subList(fromIndex, toIndex);

        // 4단계: 페이지 항목에 분양가 채우기 (캐시 miss 시 MDL 조회)
        List<String> uncachedIds = pageItems.stream()
                .map(ComplexSummary::complexId)
                .filter(id -> complexCacheService.getCachedSalePrice(id) == null)
                .toList();

        if (!uncachedIds.isEmpty()) {
            List<AptDetailData> uncachedData = allData.stream()
                    .filter(d -> uncachedIds.contains(d.houseManageNo()))
                    .toList();
            Map<String, List<AptDetailMdlData>> mdlMap = complexCacheService.fetchMdlData(uncachedData);
            for (AptDetailData d : uncachedData) {
                complexCacheService.preCacheDetail(d, mdlMap.getOrDefault(d.houseManageNo(), List.of()), updatedAt);
            }
        }

        List<ComplexSummary> itemsWithPrice = pageItems.stream()
                .map(item -> {
                    Integer salePrice = complexCacheService.getCachedSalePrice(item.complexId());
                    return new ComplexSummary(
                        item.complexId(), item.name(), item.houseType(), item.region(),
                        item.address(), item.announcementDate(), item.applicationEndDate(),
                        item.expectedMoveIn(), salePrice, item.isJudgeable(),
                        item.matchedProductNames());
                })
                .toList();

        return new ComplexListResponse(itemsWithPrice, matchedTotal, page, size, updatedAt);
    }

    // ── 내부 유틸 ──

    private List<AptDetailData> filterComplexData(List<AptDetailData> allData,
                                                    String region, HouseCategory houseCategory) {
        String normalizedRegion = normalizeRegion(region);
        return allData.stream()
                .filter(data -> normalizedRegion == null
                        || normalizedRegion.equals(data.subscrptAreaCodeNm()))
                .filter(data -> houseCategory == null
                        || houseCategory.getHouseDtlSecd().equals(data.houseDtlSecd()))
                .toList();
    }

    /** 프론트에서 '경기도'로 전달 → API 데이터의 '경기'와 매칭 */
    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) return null;
        if ("경기도".equals(region)) return "경기";
        return region;
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice) {
        return toSummary(data, salePrice, null);
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice, List<String> matchedProductNames) {
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
                true,
                matchedProductNames
        );
    }

}