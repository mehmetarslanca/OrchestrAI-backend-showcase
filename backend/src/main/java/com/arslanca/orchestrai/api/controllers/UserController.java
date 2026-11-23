package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.UserManagementService;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.business.dtos.user.UserUpdateRequest;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }


    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        log.info("API request: Get profile for userId={}", authenticatedUser.id());
        final UserResponse userResponse = userManagementService.getCurrentUser(authenticatedUser.id());
        return ResponseEntity.ok(userResponse);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @Valid @RequestBody final UserUpdateRequest request) {
        final UserResponse response = userManagementService.updateUserProfile(authenticatedUser.id(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        userManagementService.deleteAccount(authenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
