package dev.airouter.contract;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractConfigTest {

    @Test
    void normalizesTheApiRootAndRedactsTheToken() {
        String token = "token-that-must-stay-private";
        ContractConfig config = ContractConfig.builder("https://gateway.example/v1")
                .model(" model-name ")
                .bearerTokenSupplier(() -> token)
                .build();

        assertEquals("https://gateway.example/v1/", config.apiRoot().toString());
        assertEquals("https://gateway.example/v1/models", config.endpoint("models").toString());
        assertEquals("model-name", config.model());
        assertFalse(config.toString().contains(token));
    }

    @Test
    void rejectsUnsafeOrAmbiguousApiRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("/v1").build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("ftp://gateway.example/v1").build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://user:secret@gateway.example/v1").build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://gateway.example/v1?key=value").build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://gateway.example/v1#fragment").build());
    }

    @Test
    void rejectsInvalidTimeoutsAndBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://gateway.example/v1")
                        .timeout(Duration.ZERO)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://gateway.example/v1")
                        .maxResponseBytes(0)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> ContractConfig.builder("https://gateway.example/v1")
                        .maxResponseBytes(100)
                        .maxEventBytes(101)
                        .build());
    }
}
