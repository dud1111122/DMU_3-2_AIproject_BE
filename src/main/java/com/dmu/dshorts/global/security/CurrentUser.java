package com.dmu.dshorts.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the logged-in user into a controller method parameter of type {@link AuthUser}.
 *
 * <pre>
 * // Public (P) endpoint: user is null for guests
 * public ApiResponse&lt;PostDetail&gt; get(@PathVariable Long id, @CurrentUser AuthUser user)
 *
 * // Member (U) endpoint: 401 UNAUTHORIZED for guests, user is never null
 * public void like(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /** When true, a guest request fails with 401 UNAUTHORIZED before the controller runs. */
    boolean required() default false;
}
