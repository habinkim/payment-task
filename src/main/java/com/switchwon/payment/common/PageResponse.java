package com.switchwon.payment.common;

import com.switchwon.payment.common.page.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PageResponse", description = "페이지 단위 조회 결과")
public record PageResponse<T>(

        @Schema(description = "조회된 항목")
        List<T> content,

        @Schema(description = "현재 페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "조건에 맞는 전체 건수", example = "137")
        long totalElements,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {

    public static <E, T> PageResponse<T> of(PageResult<E> result, Function<E, T> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.hasNext());
    }
}
