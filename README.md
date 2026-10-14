# 디쇼츠 백엔드 (De-Shorts Backend)

디쇼츠의 기존 Spring Boot 백엔드 프로토타입입니다. 현재 코드는 이전 관심사 기반 카드 묶음 생성 컨셉의 데모 API이며, 새 인물 팔로우형 지식 피드와 연결하는 작업은 아직 남아 있습니다.

## 현재 구현

- Spring Boot 3.4.4, Java 21
- PostgreSQL 연결 설정과 Spring Data JPA 엔티티·저장소
- `/api/health`: API 상태 확인
- `POST /api/batches`: 관심사 입력을 받고 데모 카드 3개와 퀴즈를 반환합니다. 카드가 없으면 샘플 카드를 DB에 저장합니다.
- `GET /api/batches/{requestId}/cards/{cardId}/image`: 이미지 생성 서비스 미연결 상태를 반환합니다.
- `POST /api/cards/{id}/reports`: 카드 신고를 저장합니다.
- 오류 응답에 코드, 사용자 메시지, 재시도 가능 여부를 포함합니다.

현재 AI 모델·웹 검색·이미지 생성 제공자는 연결되어 있지 않습니다. API 응답의 데모 카드가 실시간 AI 생성 결과인 것은 아닙니다. 이 API 계약은 최신 인물 피드 컨셉의 계약이 아니므로, 인물·게시물·슬라이드·출처 구조로 변경해야 합니다.

## 실행 방법

필수 항목: Java 21, Gradle, Docker Compose.

```bash
docker compose up -d
gradle bootRun
```

서버 기본 주소는 `http://localhost:8080`입니다. DB 접속 값은 `src/main/resources/application.properties`와 `docker-compose.yml`을 확인하세요. 현재 Compose 값은 로컬 시연용 기본값이며 공유 서버에 그대로 사용하지 마세요.

## API 예시

상태 확인:

```bash
curl http://localhost:8080/api/health
```

이전 데모의 카드 묶음 요청:

```bash
curl -X POST http://localhost:8080/api/batches \
  -H 'Content-Type: application/json' \
  -d '{"topics":["철학","과학"]}'
```

신고:

```bash
curl -X POST http://localhost:8080/api/cards/1/reports \
  -H 'Content-Type: application/json' \
  -d '{"reason":"출처 확인 필요"}'
```

## 구조

- `controller/`: REST API와 공통 오류 응답
- `model/`: JPA 엔티티
- `repository/`: Spring Data 저장소
- `config/`: CORS 설정
- `src/main/resources/application.properties`: 서버·DB 설정
- `docker-compose.yml`: 로컬 PostgreSQL 16

## 남은 작업

1. 새 MVP 범위에 맞게 Person, Post, Slide, Source, Follow, Saved 관계를 확정합니다.
2. 피드·프로필·탐색 화면에 필요한 읽기 API를 정의하고 프런트엔드와 연결합니다.
3. 출처 자료를 입력으로 받는 AI 초안 생성 서비스는 제공자와 비용을 정한 후 별도 어댑터로 추가합니다.
4. 모델 출력 자체를 사실 검증으로 취급하지 않고, 게시 전 사람의 내용·출처 검토 절차를 둡니다.

## 관련 저장소

- 프런트엔드: https://github.com/dud1111122/DMU_3-2_AIproject_FE
- 백엔드: https://github.com/dud1111122/DMU_3-2_AIproject_BE
