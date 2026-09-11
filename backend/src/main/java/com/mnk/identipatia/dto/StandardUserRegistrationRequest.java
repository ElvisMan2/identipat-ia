package com.mnk.identipatia.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Datos permitidos para el registro público de un usuario STANDARD. No acepta rol, estado, contraseña ni identificador administrativo.")
public class StandardUserRegistrationRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String paternalLastName;

    @NotBlank
    private String maternalLastName;

    @NotBlank
    private String doi;

    @NotBlank
    private String doiType;

    @NotNull
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate birthDate;

    @NotBlank
    private String gender;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    @NotBlank
    private String mobilePhone;

    @NotBlank
    private String profession;
}
