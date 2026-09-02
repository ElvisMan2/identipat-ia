package com.mnk.identipatia.mapper;

import com.mnk.identipatia.dto.ClientDTO;
import com.mnk.identipatia.model.Client;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    Client toEntity(ClientDTO dto);

    ClientDTO toDto(Client entity);
}

