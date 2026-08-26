package com.hiveapp.shared.money;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Serializes an exact decimal as a JSON string so JavaScript clients never receive it through
 * IEEE-754 number coercion. Jackson continues to deserialize both string and numeric inputs into
 * the declared {@code BigDecimal} component for backward-compatible request handling.
 */
@JacksonAnnotationsInside
@JsonSerialize(using = ExactDecimalSerializer.class)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExactDecimal {
}
