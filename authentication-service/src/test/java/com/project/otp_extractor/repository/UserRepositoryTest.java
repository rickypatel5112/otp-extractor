package com.project.otp_extractor.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create"})
public class UserRepositoryTest {

    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired private UserRepository userRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setup() {
        userRepository.deleteAll();

        User user1 = createUser("test1@gmail.com");
        User user2 = createUser("test2@gmail.com");

        userRepository.save(user1);
        userRepository.save(user2);
    }

    private User createUser(String email) {
        return User.builder()
                .firstname("John")
                .lastname("Doe")
                .email(email)
                .password("Password1!")
                .build();
    }

    @Test
    void shouldFindAllUsers() {
        List<User> users = userRepository.findAll();
        assertEquals(2, users.size());
    }

    @Test
    void shouldFindUserByEmail() {
        Optional<User> found = userRepository.findByEmail("test1@gmail.com");

        assertTrue(found.isPresent());
        assertEquals("test1@gmail.com", found.get().getEmail());
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("notfound@gmail.com");

        assertTrue(found.isEmpty());
    }

    @Test
    void shouldDeleteUserByEmail() {
        long deleted = userRepository.deleteByEmail("test1@gmail.com");

        assertEquals(1, deleted);
        assertTrue(userRepository.findByEmail("test1@gmail.com").isEmpty());
    }

    @Test
    void shouldReturnZeroWhenDeletingNonExistingUser() {
        long deleted = userRepository.deleteByEmail("ghost@gmail.com");

        assertEquals(0, deleted);
    }

    @Test
    void shouldGenerateIdOnSave() {
        User user = createUser("new@test.com");

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
    }

    @Test
    void shouldUpdateUserEmail() {
        User user = userRepository.findByEmail("test1@gmail.com").orElseThrow();

        user.setEmail("updated@test.com");
        userRepository.save(user);

        assertFalse(userRepository.findByEmail("test1@gmail.com").isPresent());
        assertTrue(userRepository.findByEmail("updated@test.com").isPresent());
    }

    @Test
    void shouldThrowExceptionWhenSavingDuplicateEmail() {
        User duplicate = createUser("test1@gmail.com");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    userRepository.saveAndFlush(duplicate);
                });
    }

    @Test
    void shouldThrowExceptionWhenEmailIsNull() {
        User invalid = createUser(null);

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenEmailIsInvalid() {
        User invalid = createUser("invalid_email");

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenEmailIsEmptyString() {
        User invalid = createUser("");
        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenOnlyEmailSuffixIsPresent() {
        User invalid = createUser("@gmail.com");

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenFirstNameIsNull() {
        User invalid =
                User.builder()
                        .firstname(null)
                        .lastname("lastname")
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenLastNameIsNull() {
        User invalid =
                User.builder()
                        .firstname("firstname")
                        .lastname(null)
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(invalid);
                });
    }

    @Test
    void shouldThrowExceptionWhenFirstNameIsEmpty() {
        User user =
                User.builder()
                        .firstname("")
                        .lastname("lastname")
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(user);
                });
    }

    @Test
    void shouldThrowExceptionWhenLastNameIsEmpty() {
        User user =
                User.builder()
                        .firstname("firstname")
                        .lastname("")
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(user);
                });
    }

    @Test
    void shouldThrowExceptionWhenFirstNameLengthIsMoreThan100Characters() {
        String firstname = "a".repeat(101);

        User user =
                User.builder()
                        .firstname(firstname)
                        .lastname("lastname")
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(user);
                });
    }

    @Test
    void shouldThrowExceptionWhenLastNameLengthIsMoreThan100Characters() {
        String lastname = "a".repeat(101);

        User user =
                User.builder()
                        .firstname("firstname")
                        .lastname(lastname)
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        assertThrows(
                ConstraintViolationException.class,
                () -> {
                    userRepository.saveAndFlush(user);
                });
    }

    @Test
    void shouldCreateUserWhenFirstNameLengthIs100Characters() {
        String firstname = "a".repeat(100);

        User user =
                User.builder()
                        .firstname(firstname)
                        .lastname("lastname")
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        userRepository.save(user);

        assertTrue(userRepository.findByEmail("valid@gmail.com").isPresent());
    }

    @Test
    void shouldCreateUserWhenLastNameLengthIs100Characters() {
        String lastname = "a".repeat(100);

        User user =
                User.builder()
                        .firstname("firstname")
                        .lastname(lastname)
                        .email("valid@gmail.com")
                        .password("ValidPassword1@")
                        .build();

        userRepository.save(user);

        assertTrue(userRepository.findByEmail("valid@gmail.com").isPresent());
    }
}
