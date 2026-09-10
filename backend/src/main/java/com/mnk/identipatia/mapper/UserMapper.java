package com.mnk.identipatia.mapper;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(UserDTO dto);

    UserDTO toDto(User entity);
}
