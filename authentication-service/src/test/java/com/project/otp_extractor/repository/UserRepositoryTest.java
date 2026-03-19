package com.project.otp_extractor.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.project.otp_extractor.user.User;
import com.project.otp_extractor.user.UserRepository;
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

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

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
    }

    @Test
    void shouldContainMoreThanOneUser() {
        User user = User.builder().email("test1@gmail.com").build();

        User user2 = User.builder().email("test2@gmail.com").build();

        userRepository.save(user);
        userRepository.save(user2);

        List<User> users = userRepository.findAll();

        assertEquals(2, users.size());
    }

    @Test
    void shouldFindUserByEmail() {
        User user = User.builder().email("test@example.com").build();

        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("notfound@example.com");

        assertTrue(found.isEmpty());
    }

    @Test
    void shouldReturnAllUsers_whenMultipleUsersExist() {
        userRepository.save(User.builder().email("test1@gmail.com").build());
        userRepository.save(User.builder().email("test2@gmail.com").build());

        List<User> users = userRepository.findAll();

        assertEquals(2, users.size());
    }

    @Test
    void shouldReturnEmptyList_whenNoUsersExist() {
        List<User> users = userRepository.findAll();

        assertTrue(users.isEmpty());
    }

    @Test
    void shouldDeleteUserByEmail() {
        User user = User.builder().email("delete@test.com").build();

        userRepository.save(user);

        long deletedCount = userRepository.deleteByEmail("delete@test.com");

        assertEquals(1, deletedCount);
        assertTrue(userRepository.findByEmail("delete@test.com").isEmpty());
    }

    @Test
    void shouldReturnZero_whenDeletingNonExistingUser() {
        long deletedCount = userRepository.deleteByEmail("ghost@test.com");

        assertEquals(0, deletedCount);
    }

    @Test
    void shouldGenerateIdOnSave() {
        User user = User.builder().email("id@test.com").build();

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
    }

    @Test
    void shouldUpdateUserEmail() {
        User user = User.builder().email("old@test.com").build();

        user = userRepository.save(user);

        user.setEmail("new@test.com");
        userRepository.save(user);

        Optional<User> updated = userRepository.findByEmail("new@test.com");

        assertTrue(updated.isPresent());
    }

    @Test
    void shouldThrowException_whenSavingDuplicateEmail() {
        User user1 = User.builder().email("duplicate@test.com").build();

        User user2 = User.builder().email("duplicate@test.com").build();

        userRepository.saveAndFlush(user1);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    userRepository.saveAndFlush(user2);
                });
    }
}
