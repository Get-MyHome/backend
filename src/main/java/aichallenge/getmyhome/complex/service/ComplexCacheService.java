package aichallenge.getmyhome.complex.service;

import aichallenge.getmyhome.complex.client.ApplyhomeApiClient;
import aichallenge.getmyhome.complex.client.dto.ApplyhomeApiResponse;
import aichallenge.getmyhome.complex.client.dto.AptDetailData;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplexCacheService {

    private static final int FETCH_PAGE_SIZE = 100;

    private final ApplyhomeApiClient applyhomeApiClient;

    @Cacheable(value = "complexList", key = "'ALL'")
    public List<AptDetailData> getCachedComplexData() {
        return fetchAllComplexData();
    }

    private List<AptDetailData> fetchAllComplexData() {
        ApplyhomeApiResponse<AptDetailData> firstResponse =
                applyhomeApiClient.getAptDetail(1, FETCH_PAGE_SIZE, null, null, null, null,
                        null, null, null, null, null, null, null);

        List<AptDetailData> result = new ArrayList<>(safeData(firstResponse));

        int total = firstResponse.matchCount();
        for (int pg = 2; pg * FETCH_PAGE_SIZE - FETCH_PAGE_SIZE < total; pg++) {
            ApplyhomeApiResponse<AptDetailData> nextResponse =
                    applyhomeApiClient.getAptDetail(pg, FETCH_PAGE_SIZE, null, null, null, null,
                            null, null, null, null, null, null, null);
            result.addAll(safeData(nextResponse));
        }

        return result;
    }

    private <T> List<T> safeData(ApplyhomeApiResponse<T> response) {
        return response != null && response.data() != null ? response.data() : List.of();
    }
}
