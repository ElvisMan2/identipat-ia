package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Respuesta paginada estable, independiente de la representación interna de Spring Data.")
public record PagedResponse<T>(
        @Schema(description = "Elementos de la página actual") List<T> content,
        @Schema(description = "Índice de página basado en cero", example = "0") int page,
        @Schema(description = "Tamaño solicitado de la página", example = "20") int size,
        @Schema(description = "Cantidad total de elementos", example = "42") long totalElements,
        @Schema(description = "Cantidad total de páginas", example = "3") int totalPages,
        @Schema(description = "Indica si es la primera página") boolean first,
        @Schema(description = "Indica si es la última página") boolean last) {

    public PagedResponse {
        content = List.copyOf(content);
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
