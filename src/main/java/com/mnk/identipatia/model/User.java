package com.mnk.identipatia.model;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String username;

    private String password;

    private String firstName;
    private String paternalLastName;
    private String maternalLastName;
    private String doi;
    private String doiType;
    private LocalDate birthDate;
    private String gender;
    private String email;
    private String phone;
    private String mobilePhone;
    private String userType;
    private String profession;
    private LocalDateTime creationDate;

}
