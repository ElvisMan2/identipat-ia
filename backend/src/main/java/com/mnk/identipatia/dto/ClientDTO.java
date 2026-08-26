package com.mnk.identipatia.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {
    private Long clientId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String paternalLastName;

    @NotBlank
    private String maternalLastName;

    @NotBlank
    private String currencyOfIncome;

    @NotNull
    @Positive
    private Double monthlyIncome;
}

