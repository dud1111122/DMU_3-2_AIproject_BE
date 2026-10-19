package com.dmu.dshorts.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests: full Spring context + MockMvc (no HTTP port) against a throw-away
 * PostgreSQL 16 container. The container is started once per JVM (i.e. per {@code ./gradlew test} run) and
 * gets {@code db/schema.sql} applied by the postgres entrypoint, so each worktree/agent has its own DB.
 *
 * <p>On context start the normal startup seeders run: accounts {@link #ADMIN_USERNAME}/{@link #DEMO_USERNAME}
 * and the dev seed (8 topics, 10 personas, 3 fixture posts). AI is forced to "not configured".
 *
 * <p>The DB is shared by all test classes in the run: do not assume empty tables or exact global counts.
 * Prefer creating your own rows ({@link #createUser}) or annotate a test with {@code @Transactional}
 * (MockMvc runs in the test thread, so the transaction rolls back). Avoid {@code @DirtiesContext}.
 *
 * <pre>
 * MockHttpSession demo = loginAsDemo();
 * mockMvc.perform(put("/api/v1/users/me/posts/1/like").with(withSession(demo))).andExpect(status().isNoContent());
 * mockMvc.perform(get("/api/v1/users/me").session(demo)).andExpect(status().isOk());   // GET needs no CSRF
 * mockMvc.perform(post("/api/v1/guest/debates").with(csrf()).contentType(JSON).content(json(body)));  // guest
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    public static final String ADMIN_USERNAME = "test.admin";
    public static final String ADMIN_PASSWORD = "test-admin-password";
    public static final String DEMO_USERNAME = "test.demo";
    public static final String DEMO_PASSWORD = "test-demo-password";

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger();

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("dshorts_test")
                    .withUsername("dshorts")
                    .withPassword("dshorts")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("db/schema.sql"),
                            "/docker-entrypoint-initdb.d/01-schema.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.seed.admin-username", () -> ADMIN_USERNAME);
        registry.add("app.seed.admin-password", () -> ADMIN_PASSWORD);
        registry.add("app.seed.demo-username", () -> DEMO_USERNAME);
        registry.add("app.seed.demo-password", () -> DEMO_PASSWORD);
        registry.add("app.seed.dev-data", () -> "true");
        registry.add("app.ai.provider", () -> "");
        registry.add("app.ai.api-key", () -> "");
        registry.add("app.ai.model", () -> "");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Logs in as the seeded USER account and returns its session. */
    protected MockHttpSession loginAsDemo() throws Exception {
        return login(DEMO_USERNAME, DEMO_PASSWORD);
    }

    /** Logs in as the seeded ADMIN account and returns its session. */
    protected MockHttpSession loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    /** POST /api/v1/auth/login (with CSRF); fails the test unless it returns 200. */
    protected MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Uses the logged-in session and adds a valid CSRF token: {@code .with(withSession(session))}. */
    protected static RequestPostProcessor withSession(MockHttpSession session) {
        RequestPostProcessor csrf = csrf();
        return request -> {
            request.setSession(session);
            return csrf.postProcessRequest(request);
        };
    }

    /**
     * Inserts a fresh account with a unique username (prefix + counter) so tests do not share state.
     * Log in with {@code login(user.getUsername(), password)}.
     */
    protected User createUser(String usernamePrefix, String password, UserRole role) {
        String username = usernamePrefix + "." + System.nanoTime() % 1_000_000 + USER_SEQUENCE.incrementAndGet();
        if (username.length() > 30) {
            username = username.substring(username.length() - 30);
        }
        return userRepository.save(new User(username, passwordEncoder.encode(password), "테스트 사용자", role));
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
