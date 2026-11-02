package com.dmu.dshorts.domain.post.dto;

/** Viewer state of a post. Guests get all false. */
public record UserState(boolean authenticated, boolean liked, boolean saved, boolean followingPersona) {

    public static final UserState GUEST = new UserState(false, false, false, false);
}
