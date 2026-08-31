package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.dto.req.MatchedComplexRequest;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "청약 공고 매칭", description = "사용자 조건 기반 대출 매칭 공고 조회 API")
@RestController
@RequestMapping("/complexes")
@RequiredArgsConstructor
public class MatchedComplexController {

    private final ComplexService complexService;

    @Operation(
        summary = "사용자 조건 기반 공고 매칭 조회",
        description = "conditionToken 또는 user 중 하나로 사용자 조건을 전달합니다. "
            + "토큰이 있으면 캐시된 조건을 사용하고, 없으면 user를 직접 사용합니다. "
            + "각 공고의 분양가 기반 대출 매칭 결과를 반환합니다."
    )
    @PostMapping("/matched")
    public ResponseEntity<SuccessResponse<ComplexListResponse>> getMatchedComplexes(
            @Valid @RequestBody MatchedComplexRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
            complexService.getMatchedComplexes(
                request.conditionToken(), request.user(),
                request.region(), request.houseCategory(),
                request.pageOrDefault(), request.sizeOrDefault()));
    }
}
