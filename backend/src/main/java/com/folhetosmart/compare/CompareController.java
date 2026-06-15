package com.folhetosmart.compare;

import com.folhetosmart.compare.dto.CompareRequest;
import com.folhetosmart.compare.dto.CompareResultDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compare")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping
    public List<CompareResultDto> compare(@Valid @RequestBody CompareRequest request) {
        return compareService.compare(request.productIds());
    }
}
