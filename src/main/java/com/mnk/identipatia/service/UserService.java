package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.exception.UserNotFoundException;
import com.mnk.identipatia.mapper.UserMapper;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDTO create(UserDTO userDTO) {
        User user = userMapper.toEntity(userDTO);
        user.setUserId(null);
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setCreationDate(LocalDateTime.now());
        return userMapper.toDto(userRepository.save(user));
    }

    public List<UserDTO> findAll() {
        return userRepository.findAll().stream()
                .map(userMapper::toDto)
                .toList();
    }

    public UserDTO findById(Long userId) {
        return userMapper.toDto(getUser(userId));
    }

    public UserDTO update(Long userId, UserDTO userDTO) {
        User user = getUser(userId);
        user.setFirstName(userDTO.getFirstName());
        user.setPaternalLastName(userDTO.getPaternalLastName());
        user.setMaternalLastName(userDTO.getMaternalLastName());
        user.setDoi(userDTO.getDoi());
        user.setDoiType(userDTO.getDoiType());
        user.setBirthDate(userDTO.getBirthDate());
        user.setGender(userDTO.getGender());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setMobilePhone(userDTO.getMobilePhone());
        user.setUserType(userDTO.getUserType());
        user.setProfession(userDTO.getProfession());
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        return userMapper.toDto(userRepository.save(user));
    }

    public void delete(Long userId) {
        User user = getUser(userId);
        userRepository.delete(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
