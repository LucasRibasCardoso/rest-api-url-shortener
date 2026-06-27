package com.app.url_shortener.url.presentation.validator.imp;

import com.app.url_shortener.url.presentation.validator.ValidRankingSize;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RankingSizeValidator implements ConstraintValidator<ValidRankingSize, Integer> {

  @Override
  public boolean isValid(Integer value, ConstraintValidatorContext context) {
    return value == null || value == 3 || value == 10;
  }
}
