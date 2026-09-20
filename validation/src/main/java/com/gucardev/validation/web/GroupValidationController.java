package com.gucardev.validation.web;

import com.gucardev.validation.constraint.group.ValidationGroups.OnCreate;
import com.gucardev.validation.constraint.group.ValidationGroups.OnUpdate;
import com.gucardev.validation.user.dto.GroupedUserRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 4 - same DTO validated with a different group per endpoint via
 * {@code @Validated}, since {@code @Valid} takes no group argument.
 */
@RestController
@RequestMapping("/api/groups/users")
public class GroupValidationController {

    @PostMapping
    public GroupedUserRequest create(@Validated(OnCreate.class) @RequestBody GroupedUserRequest request) {
        return request;
    }

    @PutMapping
    public GroupedUserRequest update(@Validated(OnUpdate.class) @RequestBody GroupedUserRequest request) {
        return request;
    }
}
