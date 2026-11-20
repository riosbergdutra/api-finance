package com.api.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
@ActiveProfiles("test")
class FinanceApplicationTests {

    @MockBean
    JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
