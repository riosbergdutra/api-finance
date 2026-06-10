package com.api.finance.shared;

import com.api.finance.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResourceNotFoundException")
class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("mensagem contém nome do recurso e valor do campo")
    void mensagemCorreta() {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        ResourceNotFoundException ex = ResourceNotFoundException.of("User", id);

        assertThat(ex.getMessage()).contains("User");
        assertThat(ex.getMessage()).contains(id.toString());
        assertThat(ex.getResourceName()).isEqualTo("User");
        assertThat(ex.getFieldValue()).isEqualTo(id);
    }
}
