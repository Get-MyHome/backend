package aichallenge.getmyhome.complex.client;

import aichallenge.getmyhome.complex.config.ApplyhomeApiProperties;
import aichallenge.getmyhome.complex.client.dto.*;
import aichallenge.getmyhome.complex.exception.ApplyhomeErrorCode;
import aichallenge.getmyhome.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyhomeApiClient {

    private static final String DETAIL_BASE_PATH = "/ApplyhomeInfoDetailSvc/v1";

    private final RestClient applyhomeRestClient;
    private final ApplyhomeApiProperties properties;

    public ApplyhomeApiResponse<AptDetailData> getAptDetail(
            int page, int perPage,
            String houseManageNo, String pblancNo,
            String houseSecd, String houseDtlSecd,
            String subscrptAreaCodeNm,
            String rcritPblancDeGte, String rcritPblancDeLte,
            String rceptBgndeGte, String rceptBgndeLte,
            String rceptEnddeGte, String rceptEnddeLte) {
        return callApi(DETAIL_BASE_PATH + "/getAPTLttotPblancDetail", page, perPage, uriBuilder -> {
            if (houseManageNo != null) uriBuilder.queryParam("cond[HOUSE_MANAGE_NO::EQ]", houseManageNo);
            if (pblancNo != null) uriBuilder.queryParam("cond[PBLANC_NO::EQ]", pblancNo);
            if (houseSecd != null) uriBuilder.queryParam("cond[HOUSE_SECD::EQ]", houseSecd);
            if (houseDtlSecd != null) uriBuilder.queryParam("cond[HOUSE_DTL_SECD::EQ]", houseDtlSecd);
            if (subscrptAreaCodeNm != null) uriBuilder.queryParam("cond[SUBSCRPT_AREA_CODE_NM::EQ]", subscrptAreaCodeNm);
            if (rcritPblancDeGte != null) uriBuilder.queryParam("cond[RCRIT_PBLANC_DE::GTE]", rcritPblancDeGte);
            if (rcritPblancDeLte != null) uriBuilder.queryParam("cond[RCRIT_PBLANC_DE::LTE]", rcritPblancDeLte);
            if (rceptBgndeGte != null) uriBuilder.queryParam("cond[RCEPT_BGNDE::GTE]", rceptBgndeGte);
            if (rceptBgndeLte != null) uriBuilder.queryParam("cond[RCEPT_BGNDE::LTE]", rceptBgndeLte);
            if (rceptEnddeGte != null) uriBuilder.queryParam("cond[RCEPT_ENDDE::GTE]", rceptEnddeGte);
            if (rceptEnddeLte != null) uriBuilder.queryParam("cond[RCEPT_ENDDE::LTE]", rceptEnddeLte);
            return uriBuilder;
        }, new ParameterizedTypeReference<>() {});
    }

    public ApplyhomeApiResponse<AptDetailMdlData> getAptDetailMdl(
            int page, int perPage, String houseManageNo, String pblancNo) {
        return callApi(DETAIL_BASE_PATH + "/getAPTLttotPblancMdl", page, perPage, uriBuilder -> {
            if (houseManageNo != null) uriBuilder.queryParam("cond[HOUSE_MANAGE_NO::EQ]", houseManageNo);
            if (pblancNo != null) uriBuilder.queryParam("cond[PBLANC_NO::EQ]", pblancNo);
            return uriBuilder;
        }, new ParameterizedTypeReference<>() {});
    }

    private <T> ApplyhomeApiResponse<T> callApi(
            String path, int page, int perPage,
            Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder> queryCustomizer,
            ParameterizedTypeReference<ApplyhomeApiResponse<T>> typeRef) {
        try {
            ApplyhomeApiResponse<T> response = applyhomeRestClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path(path)
                                .queryParam("page", page)
                                .queryParam("perPage", perPage)
                                .queryParam("serviceKey", properties.getServiceKey())
                                .queryParam("returnType", "JSON");
                        builder = queryCustomizer.apply(builder);
                        return builder.build();
                    })
                    .retrieve()
                    .body(typeRef);

            if (response == null) {
                log.error("청약홈 API 빈 응답: path={}", path);
                throw BaseException.of(ApplyhomeErrorCode.APPLYHOME_API_RESPONSE_ERROR);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.error("청약홈 API 타임아웃: path={}", path, e);
            throw BaseException.of(ApplyhomeErrorCode.APPLYHOME_API_TIMEOUT);
        } catch (RestClientException e) {
            log.error("청약홈 API 호출 실패: path={}", path, e);
            throw BaseException.of(ApplyhomeErrorCode.APPLYHOME_API_CALL_FAILED);
        }
    }
}