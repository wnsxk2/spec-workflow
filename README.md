# spec-workflow

로그인 spec(`.scratch/login/spec.md`)을 구현하는 저장소다. 용어는 `GLOSSARY.md`, 설계 결정은 `docs/adr/`를 따른다.

| 폴더 | 내용 |
|---|---|
| `server/` | Spring Boot 서버. 인증 모듈(로그인)과 보호된 요청을 처리한다 |
| `web/` | React(TypeScript) 화면. 화면 인증 모듈(`web/src/auth/authClient.ts`)을 통해서만 로그인을 다룬다 |
| `e2e/` | Playwright(실제 브라우저를 조작하는 테스트 도구)로 화면·서버·PostgreSQL·Redis를 함께 확인하는 E2E 테스트 |
| `compose.yaml` | 로컬 PostgreSQL과 Redis를 띄우는 Docker Compose 구성 |

## 1. 필요한 도구

아래 버전으로 확인했다. PostgreSQL과 Redis는 Docker로 띄우므로 컴퓨터에 따로 설치하지 않는다.

| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 21 | 서버 실행·빌드 |
| Gradle | 9.8.1 | 서버 빌드 도구. 저장소의 `server/gradlew`(Gradle wrapper)가 알아서 받으므로 따로 설치하지 않는다 |
| Node.js | 22 | 화면·E2E |
| npm | 10 | Node.js 패키지 관리자. Node.js와 함께 설치된다 |
| Docker Engine | 29 | 컨테이너 실행 |
| Docker Compose | v2 이상 (`docker compose` 명령) | 여러 컨테이너 서비스를 하나의 구성으로 실행하는 도구. v5.6으로 확인했다 |

컨테이너 이미지 버전은 `compose.yaml`에 고정했다: `postgres:16.4-alpine`, `redis:7.4-alpine`. 서버 테스트(Testcontainers)도 같은 이미지를 쓴다.

## 2. 처음 준비하기

저장소 루트에서 차례로 실행한다.

```bash
# 1) 실행 환경 설정 파일 만들기
cp .env.example .env
#    .env를 열어 POSTGRES_PASSWORD와 AUTH_JWT_SECRET을 채운다.
#    AUTH_JWT_SECRET은 32자 이상이어야 한다. 예: openssl rand -base64 48 의 결과를 붙여 넣는다.

# 2) PostgreSQL과 Redis 시작 (둘 다 준비될 때까지 기다린다)
docker compose up -d --wait

# 3) 화면 의존성 설치
(cd web && npm install)

# 4) E2E 의존성과 브라우저 설치
(cd e2e && npm install && npx playwright install chromium)
```

서버 의존성은 처음 `./gradlew`를 실행할 때 자동으로 받는다.

## 3. 실행하기

터미널 두 개에서 각각 실행한다.

```bash
# 서버: dev 프로필은 개발용 데이터베이스에 개발용 사용자를 준비한다
cd server && ./gradlew bootRun --args=--spring.profiles.active=dev

# 화면: http://localhost:5173
cd web && npm run dev
```

`bootRun`은 저장소 루트의 `.env`를 읽어 실행 환경에 넣는다. 셸에 이미 있는 환경 변수가 우선한다.

### 정상 동작 확인

1. `docker compose ps`에서 `postgres`와 `redis`가 모두 `(healthy)`다.
2. `curl http://localhost:8080/actuator/health`의 결과에서 `"db":{"status":"UP"}`와 `"redis":{"status":"UP"}`가 보인다. Spring Boot가 두 저장소에 실제로 연결됐다는 뜻이다.
3. 브라우저에서 http://localhost:5173 을 열고 개발용 사용자로 로그인하면 "내 정보" 화면에 사용자 식별자가 보인다.
   - 이메일: `dev@example.com`
   - 비밀번호: `dev-password-1234`
4. 로그인한 채로 새로고침해도 "내 정보" 화면이 그대로 보인다. 앱이 시작될 때 토큰 교환으로 로그인 상태를 되살리기 때문이다.
5. "내 정보" 화면의 로그아웃 버튼을 누르면 로그인 화면으로 돌아가고, 새로고침해도 로그인 화면이다.

## 4. 환경 변수

실제 비밀값은 `.env`에만 두고, `.env`는 git에 올리지 않는다. 예시는 `.env.example`에 있다.

| 이름 | 쓰는 곳 | 용도 | 기본값 |
|---|---|---|---|
| `POSTGRES_HOST` | 서버 | Spring Boot가 접속할 PostgreSQL 주소 | `localhost` |
| `POSTGRES_PORT` | Compose, 서버 | 컴퓨터에 열 PostgreSQL 포트, Spring Boot가 접속할 포트 | `5432` |
| `POSTGRES_DB` | Compose, 서버 | 개발용 데이터베이스 이름 | `app` |
| `POSTGRES_USER` | Compose, 서버 | PostgreSQL 사용자 | `app` |
| `POSTGRES_PASSWORD` | Compose, 서버 | PostgreSQL 비밀번호 | 없음 (반드시 넣는다) |
| `REDIS_HOST` | 서버 | Spring Boot가 접속할 Redis 주소 | `localhost` |
| `REDIS_PORT` | Compose, 서버 | 컴퓨터에 열 Redis 포트, Spring Boot가 접속할 포트 | `6379` |
| `AUTH_JWT_SECRET` | 서버 | Access Token 서명용 비밀 키. 32자 이상 | 없음 (없으면 서버가 시작되지 않는다) |
| `SERVER_PORT` | 서버 | 서버 포트 | `8080` |
| `API_PROXY_TARGET` | 화면 개발 서버 | `/api` 요청을 넘길 서버 주소 | `http://localhost:8080` |
| `PLAYWRIGHT_CHROMIUM_EXECUTABLE` | E2E | 미리 설치된 Chromium을 쓸 때 그 실행 파일 경로 | 없음 (`npx playwright install`로 받은 것을 쓴다) |

화면과 서버는 같은 사이트에서 서비스한다. 개발 중에는 화면 개발 서버(Vite)의 프록시(화면의 서버 요청을 Spring Boot로 전달하는 연결)가 `/api` 요청을 서버로 넘긴다. 그래서 브라우저 입장에서는 화면과 서버가 같은 주소다. Refresh Token 쿠키는 `Secure`지만, 브라우저는 `localhost`를 안전한 연결로 취급하므로 개발 중에도 저장된다.

## 5. 데이터: 개발, 서버 테스트, E2E 구분

| 용도 | PostgreSQL | Redis | 사용자 준비 |
|---|---|---|---|
| 개발 (`dev` 프로필) | Compose의 `app` 데이터베이스 | Compose의 0번 데이터베이스 | `server/src/main/resources/db/seed/dev` |
| E2E (`e2e` 프로필) | Compose의 `app_e2e` 데이터베이스 | Compose의 1번 데이터베이스 | `server/src/main/resources/db/seed/e2e` (`e2e-user@example.com`, 로그인 차단 시나리오 전용 `e2e-lockout@example.com`, 비밀번호는 둘 다 `e2e-password-1234`) |
| 서버 HTTP API 테스트 | Testcontainers가 띄우는 별도 컨테이너 | Testcontainers가 띄우는 별도 컨테이너 | 테스트마다 무작위 이메일의 사용자를 만든다 |

- 사용자 표의 구조는 `server/src/main/resources/db/migration`에 있고, 서버가 시작할 때 Flyway(데이터베이스 구조를 버전별로 적용하는 도구)가 적용한다.
- 개발용·E2E용 사용자는 서버가 시작할 때마다 같은 값으로 다시 맞춰진다. 그래서 몇 번을 실행해도 결과가 같다.
- `app_e2e` 데이터베이스는 PostgreSQL 볼륨이 처음 만들어질 때 `docker/postgres/init`의 스크립트가 만든다.
- 로그인 기능은 사용자 정보를 읽기만 한다. 사용자를 만드는 일은 회원가입 spec의 몫이다.

## 6. Docker Compose 명령

| 하고 싶은 일 | 명령 | 데이터 |
|---|---|---|
| 시작 (준비될 때까지 기다림) | `docker compose up -d --wait` | 유지 |
| 상태 확인 (healthcheck 결과 포함) | `docker compose ps` | 유지 |
| 로그 보기 | `docker compose logs -f postgres` / `docker compose logs -f redis` | 유지 |
| 중지 | `docker compose stop` | 유지 |
| 다시 시작 | `docker compose restart` 또는 `docker compose start` | 유지 |
| 컨테이너 지우기 | `docker compose down` | PostgreSQL 데이터는 유지 (Docker 볼륨에 남는다). Redis의 세션은 사라진다 |
| **초기화** | `docker compose down -v` 다음 `docker compose up -d --wait` | **PostgreSQL 데이터까지 모두 지운다.** 다음 시작 때 빈 데이터베이스에서 다시 준비한다 |

PostgreSQL의 개발 데이터는 Docker 볼륨(컨테이너를 다시 만들어도 데이터를 보관하는 저장 공간) `spec-workflow_postgres-data`에 있다. Redis는 세션과 같은 짧게 쓰는 기록만 두므로 볼륨을 두지 않는다.

## 7. 테스트 실행

| 테스트 | 명령 | 필요한 것 |
|---|---|---|
| 서버 HTTP API 테스트 | `cd server && ./gradlew test` | Docker (Testcontainers가 PostgreSQL·Redis를 직접 띄운다) |
| 화면 인증 모듈 테스트 | `cd web && npm test` | 없음 (가짜 서버 응답을 쓴다) |
| 화면 타입 검사·린트 | `cd web && npm run typecheck && npm run lint` | 없음 |
| E2E | `cd e2e && npm test` | `docker compose up -d --wait`로 PostgreSQL·Redis를 켜 둔다 |

- 서버 테스트는 시계(서버가 지금 시각을 아는 곳)를 테스트용으로 바꿔 끼워, 15분 같은 시간 규칙을 실제로 기다리지 않고 확인한다. Redis 장애는 테스트용 Redis 컨테이너를 일시 정지해 만든다.
- E2E는 실행할 때 E2E용 서버(포트 18080, `e2e` 프로필)와 화면 개발 서버(포트 15173)를 직접 띄우고, 끝나면 끈다. 개발용 서버(8080)·화면(5173)과 동시에 켜 두어도 된다. E2E에서는 시간을 앞당기지 않는다.
- E2E는 시작할 때 E2E용 Redis(1번 데이터베이스)를 비운다(`e2e/global-setup.ts`). 앞선 실행이 남긴 로그인 차단 기록 때문에 15분 안에 다시 돌린 E2E가 실패하지 않게 하려는 것이다. 개발용 Redis(0번)는 건드리지 않는다.
- E2E 결과 보고서는 `e2e/playwright-report/`에 생긴다 (`npx playwright show-report`).

## 8. 끝내기

```bash
# 서버·화면: 각 터미널에서 Ctrl+C
docker compose stop        # 데이터를 남기고 중지
```
