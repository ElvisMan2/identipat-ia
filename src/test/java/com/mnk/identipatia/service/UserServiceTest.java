package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.exception.InvalidUserDataException;
import com.mnk.identipatia.exception.UserNotFoundException;
import com.mnk.identipatia.mapper.UserMapper;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createIgnoresUserIdAndSetsCreationDate() {
        UserDTO request = userDTO(99L, "Ana", "Torres", "Salas", "12345678", "DNI");
        User user = user();
        User savedUser = user();
        savedUser.setUserId(1L);
        UserDTO expected = userDTO(1L, "Ana", "Torres", "Salas", "12345678", "DNI");

        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(savedUser);
        when(userMapper.toDto(savedUser)).thenReturn(expected);

        UserDTO result = userService.create(request);

        assertEquals(expected, result);
        assertEquals(null, user.getUserId());
        assertNotNull(user.getCreationDate());
        verify(userRepository).save(user);
        verify(userMapper).toDto(savedUser);
    }

    @Test
    void createAdminWithoutPasswordThrows() {
        UserDTO request = userDTO(null, "Ana", "Torres", "Salas", "12345678", "DNI");
        request.setUserType("ADMIN");
        User user = user();

        when(userMapper.toEntity(request)).thenReturn(user);

        assertThrows(InvalidUserDataException.class, () -> userService.create(request));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createAdminWithPasswordEncodesPassword() {
        UserDTO request = userDTO(null, "Ana", "Torres", "Salas", "12345678", "DNI");
        request.setUserType("ADMIN");
        request.setPassword("secret");
        User user = user();

        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(request);

        userService.create(request);

        assertEquals("encoded-secret", user.getPassword());
    }

    @Test
    void createStandardUserWithoutPasswordSucceeds() {
        UserDTO request = userDTO(null, "Ana", "Torres", "Salas", "12345678", "DNI");
        User user = user();

        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(request);

        userService.create(request);

        assertNull(user.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createStandardUserWithPasswordThrows() {
        UserDTO request = userDTO(null, "Ana", "Torres", "Salas", "12345678", "DNI");
        request.setPassword("secret");
        User user = user();

        when(userMapper.toEntity(request)).thenReturn(user);

        assertThrows(InvalidUserDataException.class, () -> userService.create(request));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findAllMapsEveryUser() {
        User first = user();
        User second = user();
        UserDTO firstDTO = userDTO(1L, "Ana", "Torres", "Salas", "12345678", "DNI");
        UserDTO secondDTO = userDTO(2L, "Luis", "Ramos", "Diaz", "87654321", "CE");

        when(userRepository.findAll()).thenReturn(List.of(first, second));
        when(userMapper.toDto(first)).thenReturn(firstDTO);
        when(userMapper.toDto(second)).thenReturn(secondDTO);

        assertEquals(List.of(firstDTO, secondDTO), userService.findAll());
        verify(userMapper).toDto(first);
        verify(userMapper).toDto(second);
    }

    @Test
    void findByIdReturnsMappedUser() {
        User user = user();
        UserDTO expected = userDTO(7L, "Ana", "Torres", "Salas", "12345678", "DNI");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(expected);

        assertEquals(expected, userService.findById(7L));
        verify(userRepository).findById(7L);
    }

    @Test
    void updateChangesBusinessFieldsAndPreservesIdAndCreationDate() {
        User existing = user();
        existing.setUserId(7L);
        LocalDateTime creationDate = LocalDateTime.of(2025, 1, 1, 10, 0);
        existing.setCreationDate(creationDate);
        UserDTO request = userDTO(null, "Luis", "Ramos", "Diaz", "87654321", "CE");
        UserDTO expected = userDTO(7L, "Luis", "Ramos", "Diaz", "87654321", "CE");

        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toDto(existing)).thenReturn(expected);

        assertEquals(expected, userService.update(7L, request));
        assertEquals(7L, existing.getUserId());
        assertEquals(creationDate, existing.getCreationDate());
        assertEquals("Luis", existing.getFirstName());
        assertEquals("Ramos", existing.getPaternalLastName());
        assertEquals("Diaz", existing.getMaternalLastName());
        assertEquals("87654321", existing.getDoi());
        assertEquals("CE", existing.getDoiType());
        assertEquals(LocalDate.of(1990, 1, 1), existing.getBirthDate());
        assertEquals("FEMALE", existing.getGender());
        assertEquals("ana.torres@example.com", existing.getEmail());
        assertEquals("016543210", existing.getPhone());
        assertEquals("912345678", existing.getMobilePhone());
        assertEquals("STANDARD", existing.getUserType());
        assertEquals("Abogada", existing.getProfession());
        verify(userRepository).save(existing);
    }

    @Test
    void deleteRemovesExistingUser() {
        User existing = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

        userService.delete(7L);

        verify(userRepository).delete(existing);
    }

    @Test
    void operationsThrowWhenUserDoesNotExist() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.findById(7L));
        assertThrows(UserNotFoundException.class, () -> userService.update(7L, userDTO(null, "Luis", "Ramos", "Diaz", "87654321", "CE")));
        assertThrows(UserNotFoundException.class, () -> userService.delete(7L));
        verifyNoInteractions(userMapper);
    }

    private static User user() {
        return new User();
    }

    private static UserDTO userDTO(Long id, String firstName, String paternalLastName,
                                       String maternalLastName, String doi, String doiType) {
        return UserDTO.builder()
                .userId(id)
                .firstName(firstName)
                .paternalLastName(paternalLastName)
                .maternalLastName(maternalLastName)
                .doi(doi)
                .doiType(doiType)
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender("FEMALE")
                .email("ana.torres@example.com")
                .phone("016543210")
                .mobilePhone("912345678")
                .userType("STANDARD")
                .profession("Abogada")
                .build();
    }
}
