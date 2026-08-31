package com.hiveapp.shared.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedMutation {

    String action();

    String resourceType();

    boolean recordSuccess() default true;

    boolean recordFailure() default true;
}
