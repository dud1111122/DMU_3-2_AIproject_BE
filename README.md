## De-Shorts 프로젝트 개요

> 서비스 기획의 기준 문서는 상위 프로젝트 폴더의 `기획/기획.md`이고, API 계약은 `기획/BE_Endpoint.md`이다. 이 README와 기획서가 다르면 기획서를 따른다.

### 개발 배경

쇼츠와 릴스를 보며 보내는 시간을 교양과 지식을 쌓는 시간으로 바꾸고자 한다. 기존 숏폼 이용 방식은 유지하면서, 관심 있는 내용을 짧고 쉽게 읽을 수 있는 서비스를 제공한다.

### 프로젝트 목적

인물의 사상·생애·주요 사건을 카드뉴스로 제공하여, 사용자가 짧은 휴식 시간에도 지식을 접하고 관심 분야를 넓힐 수 있도록 한다.

### 대상 사용자 및 사용 상황

쇼츠나 릴스를 보는 시간을 더 유익하게 보내고 싶은 사람을 대상으로 한다. 이동 중이나 잠깐 쉬는 시간에 콘텐츠를 읽고, 관심 있는 게시물을 저장해 다시 확인한다.

### 제안하는 해결 방법

사용자가 관심 분야를 선택하고 인물을 팔로우하면, 관련 카드뉴스를 피드에서 제공한다. 사용자는 인물 프로필을 확인하고 카드를 넘겨 읽으며, 출처를 열거나 게시물을 저장할 수 있다.

게시물을 만든 AI 페르소나와 채팅하며 배운 내용을 설명하고, 학습 포인트를 기준으로 피드백을 받는다.

AI는 관심 주제와 인물별 페르소나 설정을 바탕으로 카드뉴스의 글, 학습 포인트, 퀴즈를 생성하고, 생성된 게시물은 형식 검증만 거쳐 검토 없이 바로 게시한다. 현재는 참고 자료 없이 모델 지식으로 생성하며, 등록 자료·웹 검색 기반 생성은 이후 단계에서 추가한다. 인물별 관점과 말투를 반영하며, 실제 인용과 AI가 재구성한 표현을 구분한다.

## 기술 스택

목표 시스템 기준이다.

| 구분 | 기술 | 사용 목적 |
|---|---|---|
| Frontend | React, Vite, Tailwind CSS | 피드·탐색·프로필·저장함·관리자 화면 |
| Backend | Spring Boot, Spring Security | REST API, 인증, 회원 활동, 예약 콘텐츠 생성 |
| Database | PostgreSQL | 계정·인물·게시물·출처·사용자 활동 저장 |
| AI | AI 제공자 API — API 키를 환경 변수(`AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`)로 설정 | 카드뉴스 글·학습 포인트·퀴즈 생성, 페르소나 채팅 |
| AI | AI 이미지 생성 API (선택, `AI_IMAGE_MODEL`) | 프로필 및 카드뉴스 이미지 생성 |
| 검색 | Tavily API (이후 단계) | 자료 기반 생성에 필요한 자료 검색 |
| 배포 | Docker Compose, Nginx | 컨테이너 실행, 프런트 제공, API 전달 |
| 네트워크 | Cloudflare Tunnel | 외부에서 서버로 접속 |
| 개발 및 협업 도구 | Git, GitHub | 코드 버전 관리 및 FE·BE 저장소 관리 |

## 로컬 실행

JDK 21과 Docker가 필요하다. DB는 PostgreSQL 16만 사용하며 스키마는 `src/main/resources/db/schema.sql`(=`기획/schema.sql`)이다. 앱은 `ddl-auto=validate`로 스키마를 검증만 한다.

```bash
# .env(gitignore 대상)에 DB_*, ADMIN_*/DEMO_*, AI_* 값을 채운다
docker compose up -d              # dshorts-postgres 컨테이너
scripts/reset-db.sh dshorts_dev   # DB 재생성 + schema.sql 적용
./gradlew bootRun
```

- 최초 기동 시 `ADMIN_*`/`DEMO_*` 환경 변수로 계정 2개를, 주제 테이블이 비어 있으면 `seed/*.json`으로 주제 8개·페르소나 10명·예시 게시물 3개를 넣는다.
- 인증은 세션 쿠키 + CSRF(`X-XSRF-TOKEN` 헤더)다. 자세한 규칙은 [docs/BACKEND_FOUNDATION.md](docs/BACKEND_FOUNDATION.md)를 본다.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`, 헬스체크: `http://localhost:8080/api/v1/health`
- 테스트: `./gradlew build` (Testcontainers로 별도 PostgreSQL을 띄우므로 Docker 실행 필요)

## 구현된 API 범위

현재는 공통 기반만 있다: 로그인/로그아웃/CSRF, `GET·PATCH /users/me`, 헬스체크, 25개 테이블 엔티티, 시드. 나머지 엔드포인트는 `기획/BE_Endpoint.md` 계약에 맞춰 구현 중이다.
