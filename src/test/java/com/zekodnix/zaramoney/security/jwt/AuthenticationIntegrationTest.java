package com.zekodnix.zaramoney.security.jwt;

import com.zekodnix.zaramoney.IntegrationTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Full application context: since TODO.md item 13 every access token is checked against its session in the database
 * (DeviceBindingFilter), so the security chain cannot run in a slim context without JPA anymore.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
public @interface AuthenticationIntegrationTest {
}
