package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.domain.exception.auth.DuplicateEmailDispatchEventException;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import com.app.url_shortener.iam.infrastructure.mapper.EmailDispatchPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - EmailDispatchRepositoryAdapter")
class EmailDispatchRepositoryAdapterTest {

  @Mock private EmailDispatchPersistenceMapper mapper;

  @Mock private EmailDispatchJpaRepository emailDispatchJpaRepository;

  @Mock private DataIntegrityExceptionTranslator dataIntegrityExceptionTranslator;

  @InjectMocks private EmailDispatchRepositoryAdapter adapter;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName("Deve salvar dispatch e mapear entidade salva para domínio")
    void shouldSaveEmailDispatchAndMapSavedEntityToDomain() {
      // 1. Arrange
      var dispatch = org.mockito.Mockito.mock(EmailDispatch.class);
      var entity = org.mockito.Mockito.mock(EmailDispatchEntity.class);
      var savedEntity = org.mockito.Mockito.mock(EmailDispatchEntity.class);
      var savedDispatch = org.mockito.Mockito.mock(EmailDispatch.class);

      given(mapper.toEntity(dispatch)).willReturn(entity);
      given(emailDispatchJpaRepository.saveAndFlush(entity)).willReturn(savedEntity);
      given(mapper.toDomain(savedEntity)).willReturn(savedDispatch);

      // 2. Act
      var result = adapter.save(dispatch);

      // 3. Assert
      assertThat(result).isSameAs(savedDispatch);

      verify(mapper).toEntity(dispatch);
      verify(emailDispatchJpaRepository).saveAndFlush(entity);
      verify(mapper).toDomain(savedEntity);
      verifyNoMoreInteractions(
          mapper, emailDispatchJpaRepository, dataIntegrityExceptionTranslator);
    }

    @Test
    @DisplayName("Deve traduzir violação de integridade ao salvar dispatch")
    void shouldTranslateDataIntegrityViolationWhenSavingEmailDispatch() {
      // 1. Arrange
      var dispatch = org.mockito.Mockito.mock(EmailDispatch.class);
      var entity = org.mockito.Mockito.mock(EmailDispatchEntity.class);
      var dataIntegrityViolation = new DataIntegrityViolationException("duplicate event");
      var translatedException = new DuplicateEmailDispatchEventException();

      given(mapper.toEntity(dispatch)).willReturn(entity);
      given(emailDispatchJpaRepository.saveAndFlush(entity)).willThrow(dataIntegrityViolation);
      given(dataIntegrityExceptionTranslator.translate(dataIntegrityViolation))
          .willReturn(translatedException);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.save(dispatch));

      // 3. Assert
      throwableAssert.isSameAs(translatedException);

      verify(mapper).toEntity(dispatch);
      verify(emailDispatchJpaRepository).saveAndFlush(entity);
      verify(dataIntegrityExceptionTranslator).translate(dataIntegrityViolation);
      verify(mapper, never()).toDomain(org.mockito.ArgumentMatchers.any());
      verifyNoMoreInteractions(
          mapper, emailDispatchJpaRepository, dataIntegrityExceptionTranslator);
    }
  }

  @Nested
  @DisplayName("Busca por Evento")
  class FindByEventIdTests {

    @Test
    @DisplayName("Deve buscar dispatch por eventId e mapear para domínio")
    void shouldFindEmailDispatchByEventIdAndMapToDomain() {
      // 1. Arrange
      var eventId = UUID.randomUUID();
      var entity = org.mockito.Mockito.mock(EmailDispatchEntity.class);
      var dispatch = org.mockito.Mockito.mock(EmailDispatch.class);

      given(emailDispatchJpaRepository.findByEventId(eventId)).willReturn(Optional.of(entity));
      given(mapper.toDomain(entity)).willReturn(dispatch);

      // 2. Act
      var result = adapter.findByEventId(eventId);

      // 3. Assert
      assertThat(result).contains(dispatch);

      verify(emailDispatchJpaRepository).findByEventId(eventId);
      verify(mapper).toDomain(entity);
      verifyNoMoreInteractions(
          mapper, emailDispatchJpaRepository, dataIntegrityExceptionTranslator);
    }
  }
}
