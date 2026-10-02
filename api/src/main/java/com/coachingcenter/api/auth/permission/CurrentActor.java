package com.coachingcenter.api.auth.permission;

import java.util.UUID;

import com.coachingcenter.api.auth.Role;

public record CurrentActor(UUID userId, Role role) {
}
