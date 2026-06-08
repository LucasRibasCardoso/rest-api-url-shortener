package com.app.url_shortener.url.presentation.validator;

import com.app.url_shortener.url.presentation.validator.imp.RankingSizeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RankingSizeValidator.class)
public @interface ValidRankingSize {
  String message() default "O tamanho do ranking deve ser 3 ou 10";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
