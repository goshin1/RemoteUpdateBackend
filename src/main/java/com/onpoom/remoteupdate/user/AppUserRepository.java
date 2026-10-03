package com.onpoom.remoteupdate.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
