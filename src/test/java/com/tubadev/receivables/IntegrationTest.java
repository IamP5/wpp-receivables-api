package com.tubadev.receivables;

import com.tubadev.receivables.infrastructure.Main;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ActiveProfiles("test-integration")
@SpringBootTest(classes = Main.class)
@AutoConfigureMockMvc
@Tag("integrationTest")
public @interface IntegrationTest {
}
