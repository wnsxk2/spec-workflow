# 이슈 트래커: 로컬 마크다운

이 저장소의 이슈와 스펙은 `.scratch/`의 마크다운 파일로 관리한다.

## 규칙

- 기능 하나당 디렉터리 하나: `.scratch/<feature-slug>/`
- 스펙은 `.scratch/<feature-slug>/spec.md`
- 구현 이슈는 티켓마다 파일 하나로 `.scratch/<feature-slug>/issues/<NN>-<slug>.md`에 두며, `01`부터 번호를 매긴다. 티켓을 하나의 파일에 몰아 두지 않는다
- 트리아지 상태는 각 이슈 파일 상단 근처의 `Status:` 줄에 기록한다 (역할 문자열은 `triage-labels.md` 참고)
- 댓글과 대화 기록은 파일 맨 아래 `## Comments` 제목 밑에 덧붙인다

## 스킬이 "이슈 트래커에 게시하라"고 할 때

`.scratch/<feature-slug>/` 아래에 새 파일을 만든다 (필요하면 디렉터리도 만든다).

## 스킬이 "관련 티켓을 가져오라"고 할 때

참조된 경로의 파일을 읽는다. 보통 사용자가 경로나 이슈 번호를 직접 넘긴다.

## 길잡이(Wayfinding) 작업

`/wayfinder`가 사용한다. **맵**은 하나의 파일이며, 티켓마다 **하위** 파일이 하나씩 있다.

- **맵**: `.scratch/<effort>/map.md` (Notes / Decisions-so-far / Fog 본문).
- **하위 티켓**: `.scratch/<effort>/issues/NN-<slug>.md`, `01`부터 번호를 매기며 본문에 질문을 담는다. `Type:` 줄에 티켓 유형(`research`/`prototype`/`grilling`/`task`)을, `Status:` 줄에 `claimed`/`resolved`를 기록한다.
- **차단 관계**: 상단 근처의 `Blocked by: NN, NN` 줄. 나열된 모든 파일이 `resolved`이면 티켓의 차단이 풀린다.
- **프런티어**: `.scratch/<effort>/issues/`에서 열려 있고, 차단되지 않았고, 아무도 맡지 않은 파일을 찾는다. 번호가 가장 앞선 것이 선택된다.
- **맡기(Claim)**: 작업 전에 `Status: claimed`로 설정하고 저장한다.
- **해결**: `## Answer` 제목 밑에 답을 덧붙이고, `Status: resolved`로 설정한 뒤, `map.md`의 Decisions-so-far에 컨텍스트 포인터(요지 + 링크)를 덧붙인다.
