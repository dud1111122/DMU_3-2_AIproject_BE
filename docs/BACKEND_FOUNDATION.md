# Backend Foundation Handbook

Shared contracts for everyone building features on this backend. Specs that win over this file:
`기획/기획.md`, `기획/BE_Endpoint.md`, `기획/DB_설계.md`, `기획/schema.sql`
(copied unchanged to `src/main/resources/db/schema.sql`).

## 0. Ground rules

- **Edit only your own package** (see the map below). If you need a change in `global/**`, in another
  package, in an entity mapping, `build.gradle`, `application.properties` or `schema.sql`, do not make it:
  list it under "needed shared changes" in your report.
- The schema is fixed. Hibernate runs with `ddl-auto=validate`; never add columns or tables.
- Java style: normal formatting, constructor injection, records for DTOs, IDs as `String` in JSON.
- Never call `AiClient` inside an open DB transaction.

## 1. Package ownership map

Base package `com.dmu.dshorts`. Each feature package uses `controller/ dto/ service/ entity/ repository/`.

| Package | Owns endpoints (`/api/v1` prefix) | Notes |
| --- | --- | --- |
| `global/**` | — | Foundation only (errors, ids, cursors, security, AI client, visibility). |
| `domain/user` | `POST /auth/login`, `POST /auth/logout`, `GET /auth/csrf`, `GET/PATCH /users/me`, `AccountSeeder` | Foundation. Done. |
| `domain/health` | `GET /health` | Foundation. Done. |
| `domain/topic`, `domain/persona`, `domain/post` | `GET /topics`, `/personas`, `/personas/recommendations`, `/personas/{id}`, `/personas/{id}/posts`, `/feed`, `/search`, `/posts/{id}` | Public read side. |
| `domain/activity` | `/users/me/interests/**`, `/users/me/follows/**`, `/users/me/posts/{id}/like`, `/users/me/saved-posts/**` | |
| `domain/learning` | `/posts/{id}/quiz`, `/posts/{id}/quiz/attempts`, `/users/me/quiz-attempts/**`, `/users/me/review-items/**` | |
| `domain/study` | `/posts/{id}/study-conversations`, `/study-conversations/**`, `/guest/posts/{id}/chat` | |
| `domain/debate` | `/debates/**`, `/users/me/debates`, `/guest/debates` | |
| `domain/report` | `POST /posts/{id}/reports`, `/admin/reports/**` | |
| `domain/generation` | admin preparation command, `/admin/generation-jobs`, `generation/seed/DevSeedLoader` + `resources/seed/*.json` | Owner may extend the dev seed (e.g. to 30 posts). |
| `domain/admin` (new) | `/admin/topics/**`, `/admin/personas/**`, `/admin/posts/**`, `/admin/images` | Put admin controllers here so they don't touch read-side packages. |

Entities and repositories for all 25 tables already exist in their domain packages. You may **add query
methods** to repositories of your own package. For queries on another package's tables, create a
repository in **your** package, e.g.
`interface QuizPostQueries extends org.springframework.data.repository.Repository<Post, Long> { ... }`.
Don't change mappings of any entity.

## 2. Entities at a glance

- Single-column IDs are `Long` with `IDENTITY`. Link tables use `@IdClass` (`FollowId`, `PostLikeId`,
  `SavedPostId`, `UserInterestId`, `AnswerOptionId`, `QuizAnswerId`, `LearningAssessmentId`, `DebateTurnId`).
- Foreign keys are plain `Long xxxId` fields, except `Post.persona` (`@ManyToOne LAZY`) and the
  `@ManyToMany Set<Topic> topics` on `Persona` (`persona_topics`) and `Post` (`post_topics`).
- Enums (`EnumType.STRING`, values = DB CHECK): `UserRole`, `PostStatus`, `GroundingMode`, `CitationType`,
  `GenerationJobStatus`, `MessageRole`, `AssessmentLevel`, `ReviewReason`, `ReviewStatus`, `DebateStage`,
  `ReportReason`, `ReportStatus`.
- `created_at/updated_at/published_at/submitted_at/completed_at` etc. are filled by `@PrePersist` when null;
  `updated_at` is refreshed by `@PreUpdate`. Times are `Instant` (JSON: ISO-8601 UTC, e.g. `2026-11-17T05:57:59.94Z`).
- `spring.jpa.open-in-view=false`: build DTOs inside `@Transactional` service methods. Lazy access
  (`post.getTopics()`, `persona.getTopics()`) outside a transaction throws.
- Composite-key `save(new PostLike(u, p))` does select+insert. For idempotent PUT check
  `existsById(new PostLikeId(u, p))` first; a racing duplicate becomes `DataIntegrityViolationException` → 409.
- `questions.correct_option_order` → `answer_options` is a DEFERRABLE FK: save the question and its 4
  `AnswerOption`s in the same transaction (see `DevSeedLoader.savePost`).

## 3. Current user, login, admin

Server-side `HttpSession` (cookie `DSHORTS_SESSION`, HttpOnly, SameSite=Lax). Principal = `AuthUser`.

```java
public record AuthUser(Long id, String username, UserRole role) { boolean isAdmin(); }

// Public (P) endpoint: null for guests
@GetMapping("/posts/{id}")
ApiResponse<PostDetail> get(@PathVariable Long id, @CurrentUser AuthUser user)

// Member (U) endpoint: guests get 401 UNAUTHORIZED before your code runs
@PutMapping("/users/me/posts/{postId}/like")
ResponseEntity<Void> like(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user)
```

- Inside services: `SecurityUtils.currentUser()` → `Optional<AuthUser>`, `SecurityUtils.requireUser()` (401),
  `SecurityUtils.requireUser(AuthUser maybeNull)` (401), `SecurityUtils.requireAdmin()` (401/403).
- Admin: every URL under `/api/v1/admin/**` is already ADMIN-only in `SecurityConfig` (guest 401, user 403).
  Just map your admin controller under that prefix.
- Mixed endpoints (e.g. `/feed?following=true` is member-only): call `SecurityUtils.requireUser(user)` when needed.
- Other users' rows (attempts, conversations, debates, review items) → **404**, not 403.
- `role` is only stored in the session at login; always use `user.id()` to load data.

### CSRF (for FE and tests)

All POST/PUT/PATCH/DELETE (including login) need header `X-XSRF-TOKEN` = value of cookie `XSRF-TOKEN`.
The cookie (readable by JS) is issued on any response when absent; `GET /api/v1/auth/csrf` returns
`{data:{headerName, token}}`. The token is rotated at login. Failure → 403 `CSRF_INVALID`.
CORS allows `http://localhost:*` / `http://127.0.0.1:*` with credentials (`CORS_ALLOWED_ORIGINS` to change).

## 4. Visibility (mandatory for every non-admin read)

`com.dmu.dshorts.global.visibility.VisibilityService`:

| Method | Use |
| --- | --- |
| `Post getVisiblePost(Long postId)` | Post with persona loaded, or 404. Detail, quiz, chat, report target, … |
| `void requireVisiblePost(Long postId)` / `boolean isPostVisible(Long)` | Checks only. |
| `Set<Long> visiblePostIds(Collection<Long>)` | Filter stored records (saved posts, attempts, reviews, conversations). |
| `Persona getVisiblePersona(Long)` / `boolean isPersonaVisible(Long)` / `Set<Long> visiblePersonaIds(...)` | Persona profile, follow, debates. |
| `boolean isDebateVisible(Long lowId, Long highId)` | Both participants visible. |
| `List<Topic> publicTopicsOfPost(Long)` / `publicTopicsOfPersona(Long)` | Topic lists for public DTOs (deleted topics removed, `sortOrder,id`). |
| `static List<Topic> publicTopics(Collection<Topic>)` | Same filter on already loaded topics. |

Rules: post visible = `PUBLISHED` + `deletedAt == null` + persona `active` + persona `deletedAt == null`.
Invisible looks exactly like non-existent (404). Admin endpoints skip these checks.

For list queries use the JPQL constants (aliases `p` = Post, `pe` = its persona):

```java
@Query("select p from Post p join fetch p.persona pe where " + PostRepository.VISIBLE_POST
        + " and (p.publishedAt < :time or (p.publishedAt = :time and p.id < :id))"
        + " order by p.publishedAt desc, p.id desc")
List<Post> findFeedAfter(@Param("time") Instant time, @Param("id") Long id, Pageable page);
```

`PersonaRepository.VISIBLE_PERSONA` works the same way (alias `pe`).
Tip: avoid `(:param is null or ...)` with `Instant` params on PostgreSQL; write a first-page query and a
next-page query instead.

## 5. Responses and errors

- Success: `ApiResponse.of(dto)` → `{"data": ...}`. Lists: `ApiResponse.of(PageResponse...)` →
  `{"data":{"items":[...],"nextCursor":...}}`. No-body success: `ResponseEntity.noContent().build()` (204).
- Errors: throw `com.dmu.dshorts.global.error.ApiException`; body `{"error":{"code","message","fields":[], "details"?}}`.

```java
throw ApiException.notFound();                                  // 404 NOT_FOUND
throw ApiException.validation("topic", "논제는 1~200자여야 합니다."); // 400 VALIDATION_FAILED + fields
throw ApiException.badRequest("문항이 이 게시물에 속하지 않습니다.");   // 400 BAD_REQUEST
throw ApiException.conflict("이미 사용 중인 이름입니다.");             // 409 CONFLICT
throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS);          // 409
throw new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED)
        .withDetails(Map.of("remainingToday", 0, "resetAt", resetAt)); // 429, extra data under error.details
throw new ApiException(HttpStatus.CONFLICT, "CUSTOM_CODE", "message");
```

`ErrorCode`: `VALIDATION_FAILED`, `BAD_REQUEST` (400) · `UNAUTHORIZED` (401) · `FORBIDDEN`, `CSRF_INVALID` (403) ·
`NOT_FOUND` (404) · `METHOD_NOT_ALLOWED` (405) · `CONFLICT`, `REQUEST_IN_PROGRESS` (409) ·
`UNSUPPORTED_MEDIA_TYPE` (415) · `DAILY_LIMIT_EXCEEDED` (429) · `INTERNAL_ERROR` (500) · `AI_FAILED` (502) ·
`AI_NOT_CONFIGURED` (503).

Automatic mappings (`GlobalExceptionHandler`): `@Valid` body / `@Validated` params → 400 VALIDATION_FAILED
with fields; missing `@RequestParam` → 400 VALIDATION_FAILED; unreadable JSON → 400 BAD_REQUEST;
type mismatch on `@PathVariable` → **404**, on other params → 400; unknown URL → 404;
`DataIntegrityViolationException` → 409 CONFLICT; anything else → logged + 500 INTERNAL_ERROR.

## 6. IDs and cursors

`com.dmu.dshorts.global.common.Ids`
- DTO ID fields are `String`: `Ids.str(Long)`, `Ids.strs(Collection<Long>)`.
- Path IDs: declare `@PathVariable Long id` (non-numeric → 404). Manual: `Ids.parsePathId(String)` → 404.
- Body/query IDs: `Ids.parseBodyId(String raw, String field)`, `Ids.parseBodyIds(List<String>, field)`,
  `Ids.parseCsvIds("1,2", "topicIds")` → invalid = 400 VALIDATION_FAILED (field named).

`com.dmu.dshorts.global.common.Cursors`
- `int Cursors.limit(Integer limitParam)`: null → 20, >50 → 50, <1 → 400.
- Time+id keys (feed `(publishedAt,id)`, saved `(createdAt,postId)`, debates `(completedAt,id)`, reviews
  `(updatedAt,id)`): `Cursors.encode(Instant, long)` / `Cursors.decodeTimeId(String)` → `TimeIdCursor(time, id)` or null.
- Id-only keys: `Cursors.encodeId(long)` / `Cursors.decodeId(String)`.
- Malformed cursor → 400 VALIDATION_FAILED (`cursor`).
- Fetch `limit + 1` rows, then `PageResponse.slice(rows, limit, row -> Cursors.encode(...), Dto::from)`.

## 7. AI client

`com.dmu.dshorts.global.ai.AiClient` (impl `OpenAiCompatibleAiClient`, env `AI_PROVIDER=openai|openai-compatible`,
`AI_API_KEY`, `AI_MODEL`, optional `AI_BASE_URL`, `AI_CONNECT_TIMEOUT`=10s, `AI_READ_TIMEOUT`=60s):

```java
boolean configured();
String modelName();                                   // store in ai_model columns
String complete(List<AiMessage> messages, boolean jsonMode);
JsonNode completeJson(List<AiMessage> messages);      // JSON object or 502 AI_FAILED

String reply = aiClient.complete(List.of(AiMessage.system(prompt), AiMessage.user(content)), false);
```

Not configured → 503 `AI_NOT_CONFIGURED`; HTTP/network/empty/invalid JSON → 502 `AI_FAILED`.
Write prompt builders and output validation in your own package. Validation failures of the AI output
are yours to throw as `new ApiException(ErrorCode.AI_FAILED, "...")`. In tests mock it:
`@MockitoBean AiClient aiClient;` (creates a separate Spring context; that's fine).

## 8. Seed data

- Accounts: `AccountSeeder` creates ADMIN and USER from `ADMIN_USERNAME/ADMIN_PASSWORD/DEMO_USERNAME/DEMO_PASSWORD`
  when absent (local values are in `.env`; tests use `test.admin` / `test.demo`).
- Content: `domain/generation/seed/DevSeedLoader` runs when `topics` is empty (`DEV_SEED=false` disables):
  `seed/catalog.json` = 8 topics, 10 personas (same as `기획/seed_catalog.sql`), 27 persona_topics;
  `seed/posts.json` = 3 fixture posts (nietzsche 4 cards/2 points, einstein 3 cards/3 points,
  marie.curie 3 cards/2 points; 4 options per question, sources `verified=false`). Look rows up by
  `personaRepository.findByUsername("nietzsche")` etc., never by hard-coded numeric IDs.

## 9. Tests

Extend `com.dmu.dshorts.support.IntegrationTestSupport` (in `src/test/java`):
`@SpringBootTest` + MockMvc + a PostgreSQL 16 Testcontainer started once per JVM with `db/schema.sql`
applied. Each `./gradlew test` run gets its own container and random port, so parallel worktrees don't collide.
Requires Docker running.

```java
class LikeIntegrationTest extends IntegrationTestSupport {
    @Test
    void likeIsIdempotent() throws Exception {
        MockHttpSession demo = loginAsDemo();                    // or loginAsAdmin(), login(user, pw)
        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", postId).with(withSession(demo)))  // session + CSRF
               .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me/saved-posts").session(demo)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/guest/debates").with(csrf())...);  // guest POST
    }
}
```

Helpers: `loginAsDemo()`, `loginAsAdmin()`, `login(username, password)` → `MockHttpSession`;
`withSession(session)` (session + CSRF); `createUser(prefix, password, UserRole)` → fresh isolated account;
`json(Object)`, `readJson(MvcResult)`; constants `ADMIN_USERNAME/…PASSWORD`, `DEMO_USERNAME/…PASSWORD`.
The DB is shared by all test classes in a run: create your own rows or use `@Transactional` on the test
(rolls back; MockMvc runs in the same thread). Don't assert exact global counts. Avoid `@DirtiesContext`.

## 10. Running locally

```sh
docker compose up -d                       # PostgreSQL 16 container dshorts-postgres (localhost:5432)
scripts/reset-db.sh dshorts_dev            # drop + create DB and apply db/schema.sql
./gradlew bootRun                          # uses .env (DB_URL=.../dshorts_dev, seed accounts)
# parallel instance with its own DB and port:
scripts/reset-db.sh dshorts_quiz
DB_URL=jdbc:postgresql://localhost:5432/dshorts_quiz SERVER_PORT=8082 ./gradlew bootRun
```

Env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `ADMIN_*`, `DEMO_*`, `DEV_SEED`,
`AI_*`, `CORS_ALLOWED_ORIGINS`, `SESSION_COOKIE_SECURE` (true behind HTTPS), `SESSION_TIMEOUT` (default 12h).
Real environment variables override `.env`.

curl smoke test:

```sh
curl -s -c jar -b jar localhost:8080/api/v1/auth/csrf
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar)
curl -s -c jar -b jar -X POST localhost:8080/api/v1/auth/login -H "X-XSRF-TOKEN: $TOKEN" \
     -H 'Content-Type: application/json' -d '{"username":"demo","password":"<DEMO_PASSWORD>"}'
curl -s -b jar localhost:8080/api/v1/users/me
```

Swagger UI: `/swagger-ui/index.html`, OpenAPI: `/v3/api-docs`.
