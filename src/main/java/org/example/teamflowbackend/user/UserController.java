package org.example.teamflowbackend.user;

import jakarta.validation.Valid;
import org.example.teamflowbackend.user.dto.PublicUserResponse;
import org.example.teamflowbackend.user.dto.UpdateUserRequest;
import org.example.teamflowbackend.user.dto.UserResponse;
import org.example.teamflowbackend.user.dto.UserSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final Logger log = LoggerFactory.getLogger(UserController.class);

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.getCurrentUser();
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateCurrentUser(request);
    }

    @GetMapping
    public Page<UserSearchItem> search(
            @RequestParam String name,
            @PageableDefault(size = 20, sort = "displayName", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return userService.searchActiveUsers(name, pageable);
    }

    @GetMapping("/{id}")
    public PublicUserResponse getPublicUser(
            @PathVariable UUID id
    ) {
        return userService.getPublicUser(id);
    }

}
