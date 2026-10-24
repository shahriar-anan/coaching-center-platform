package com.coachingcenter.api.auth;

public record GoogleIdentity(String subject, String email, String name, boolean emailVerified) {
}
