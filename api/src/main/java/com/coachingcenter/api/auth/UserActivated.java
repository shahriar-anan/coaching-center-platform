package com.coachingcenter.api.auth;

import java.util.UUID;

/**
 * Published inside the activation's transaction, once the account is active with its password set. Listeners in other packages
 * (the offline admission, which clears the desk's code) run in the same transaction, so a failure there undoes the activation.
 */
public record UserActivated(UUID userId) {
}
