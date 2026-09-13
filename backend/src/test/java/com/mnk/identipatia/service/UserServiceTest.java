package com.mnk.identipatia.service;

import com.mnk.identipatia.dto.DocumentRecognitionRequest;
import com.mnk.identipatia.dto.DocumentRecognitionResponse;
import com.mnk.identipatia.dto.StandardUserRegistrationRequest;
import com.mnk.identipatia.dto.StandardUserRegistrationResponse;
import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.exception.InvalidUserDataException;
import com.mnk.identipatia.exception.UserNotFoundException;
import com.mnk.identipatia.mapper.UserMapper;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Mock
    private StandardSessionRepository standardSessionRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void registerStandardForcesStandardActiveAndPasswordlessUser() {
        StandardUserRegistrationRequest request = registrationRequest("12345678", "DNI");
        User mappedUser = new User();

        when(userRepository.existsByDoi(request.getDoi())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(mappedUser);

        StandardUserRegistrationResponse response = userService.registerStandard(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("STANDARD", savedUser.getValue().getUserType());
        assertEquals("A", savedUser.getValue().getStatus());
        assertNull(savedUser.getValue().getPassword());
        assertNull(savedUser.getValue().getUserId());
        assertNotNull(savedUser.getValue().getCreationDate());
        assertEquals(true, response.isRegistered());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerStandardRejectsDuplicateDocument() {
        StandardUserRegistrationRequest request = registrationRequest("12345678", "DNI");
        when(userRepository.existsByDoi(request.getDoi())).thenReturn(true);

        assertThrows(InvalidUserDataException.class, () -> userService.registerStandard(request));

        verify(userMapper, never()).toEntity(any(StandardUserRegistrationRequest.class));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void identifyDocumentRecognizesStandardWithoutRequestingPassword() {
        DocumentRecognitionRequest request = recognitionRequest("12345678", "DNI");
        User standard = user();
        standard.setDoi("12345678");
        standard.setDoiType("DNI");
        standard.setUserType("STANDARD");
        when(userRepository.findByDoi("12345678")).thenReturn(Optional.of(standard));

        assertEquals(true, userService.identifyDocument(request).isRegistered());
        assertFalse(userService.identifyDocument(request).isPasswordRequired());
    }

    @Test
    void identifyDocumentRequestsPasswordForAdmin() {
        DocumentRecognitionRequest request = recognitionRequest("87654321", "CE");
        User admin = user();
        admin.setDoi("87654321");
        admin.setDoiType("CE");
        admin.setUserType("ADMIN");
        when(userRepository.findByDoi("87654321")).thenReturn(Optional.of(admin));

        DocumentRecognitionResponse response = userService.identifyDocument(request);

        assertTrue(response.isRegistered());
        assertTrue(response.isPasswordRequired());
    }

    @Test
    void identifyDocumentReturnsFalseForUnknownDocument() {
        DocumentRecognitionRequest request = recognitionRequest("99999999", "CE");
        when(userRepository.findByDoi("99999999")).thenReturn(Optional.empty());

        assertFalse(userService.identifyDocument(request).isRegistered());
        assertFalse(userService.identifyDocument(request).isPasswordRequired());
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
        existing.setDoi("12345678");
        existing.setDoiType("DNI");
        existing.setPassword("encoded-password");
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
        assertEquals("12345678", existing.getDoi());
        assertEquals("DNI", existing.getDoiType());
        assertEquals("encoded-password", existing.getPassword());
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
    void deleteDeactivatesUserWhenHistoricalSessionsExist() {
        User existing = user();
        existing.setStatus("A");
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(standardSessionRepository.existsByUserUserId(7L)).thenReturn(true);

        userService.delete(7L);

        assertEquals("I", existing.getStatus());
        verify(userRepository).save(existing);
        verify(userRepository, never()).delete(existing);
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

    private static DocumentRecognitionRequest recognitionRequest(String doi, String doiType) {
        DocumentRecognitionRequest request = new DocumentRecognitionRequest();
        request.setDoi(doi);
        request.setDoiType(doiType);
        return request;
    }

    private static StandardUserRegistrationRequest registrationRequest(String doi, String doiType) {
        StandardUserRegistrationRequest request = new StandardUserRegistrationRequest();
        request.setFirstName("Ana");
        request.setPaternalLastName("Torres");
        request.setMaternalLastName("Salas");
        request.setDoi(doi);
        request.setDoiType(doiType);
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setGender("FEMALE");
        request.setEmail("ana.torres@example.com");
        request.setPhone("016543210");
        request.setMobilePhone("912345678");
        request.setProfession("Abogada");
        return request;
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
