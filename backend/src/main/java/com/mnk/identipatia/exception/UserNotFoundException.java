package com.mnk.identipatia.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User with ID " + id + " not found");
    }

    public UserNotFoundException(String doi) {
        super("User with DOI " + doi + " not found");
    }
}
