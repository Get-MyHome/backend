package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 청약 공고 목록·상세 API
 */
@RestController
@RequestMapping("/complexes")
@RequiredArgsConstructor
public class ComplexController {

    private final ComplexService complexService;

    @GetMapping
    public ResponseEntity<SuccessResponse<ComplexListResponse>> getComplexes(
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                complexService.getComplexes(region, page, size));
    }

    @GetMapping("/{complexId}")
    public ResponseEntity<SuccessResponse<ComplexDetailResponse>> getComplexDetail(
            @PathVariable String complexId) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                complexService.getComplexDetail(complexId));
    }
}