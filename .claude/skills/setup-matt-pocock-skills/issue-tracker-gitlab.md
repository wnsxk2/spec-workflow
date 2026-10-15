# 이슈 트래커: GitLab

이 저장소의 이슈와 스펙은 GitLab 이슈로 관리한다. 모든 작업에 [`glab`](https://gitlab.com/gitlab-org/cli) CLI를 사용한다.

## 규칙

- **이슈 생성**: `glab issue create --title "..." --description "..."`. 여러 줄 설명에는 heredoc을 사용한다. 편집기를 열려면 `--description -`를 넘긴다.
- **이슈 읽기**: `glab issue view <number> --comments`. 기계가 읽을 출력에는 `-F json`을 사용한다.
- **이슈 목록**: `glab issue list -O json`에 적절한 `--label` 필터를 붙인다.
- **이슈에 댓글 달기**: `glab issue note <number> --message "..."`. GitLab은 댓글을 "notes"라고 부른다.
- **라벨 적용 / 제거**: `glab issue update <number> --label "..."` / `--unlabel "..."`. 여러 라벨은 쉼표로 구분하거나 플래그를 반복한다.
- **닫기**: `glab issue close <number>`. `glab issue close`는 닫기 댓글을 받지 않으므로, 먼저 `glab issue note <number> --message "..."`로 설명을 남긴 뒤 닫는다.
- **머지 리퀘스트**: GitLab은 PR을 "merge request"라고 부른다. `glab mr create`, `glab mr view`, `glab mr note` 등을 사용한다. `gh pr ...`과 같은 형태이며, `pr` 대신 `mr`, `comment`/`--body` 대신 `note`/`--message`를 쓴다.

저장소는 `git remote -v`로 추론한다. 클론 안에서 실행하면 `glab`이 자동으로 처리한다.

## 트리아지 창구로서의 머지 리퀘스트

**요청 창구로서의 MR: 아니오.** _(이 저장소가 외부 머지 리퀘스트를 기능 요청으로 취급한다면 `예`로 설정한다. `/triage`가 이 플래그를 읽는다.)_

`예`로 설정하면 MR도 이슈와 같은 라벨과 상태를 거치며, `glab mr` 대응 명령을 사용한다:

- **MR 읽기**: `glab mr view <number> --comments`, diff는 `glab mr diff <number>`.
- **트리아지할 외부 MR 목록**: `glab mr list -F json`을 실행한 뒤, 작성자가 프로젝트 멤버/소유자가 아닌 MR만 남긴다(유지보수자의 진행 중 작업이 아니라 기여자의 MR).
- **댓글 / 라벨 / 닫기**: `glab mr note`, `glab mr update --label`/`--unlabel`, `glab mr close`.

GitHub와 달리 GitLab은 이슈와 MR에 번호를 따로 매기므로, 유지보수자가 어느 쪽을 뜻하는지만 알면 `#42`는 모호하지 않다.

## 스킬이 "이슈 트래커에 게시하라"고 할 때

GitLab 이슈를 생성한다.

## 스킬이 "관련 티켓을 가져오라"고 할 때

`glab issue view <number> --comments`를 실행한다.

## 길잡이(Wayfinding) 작업

`/wayfinder`가 사용한다. **맵**은 하나의 이슈이며, **하위** 이슈들이 티켓이다.

- **맵**: `wayfinder:map` 라벨이 붙은 이슈 하나로, Notes / Decisions-so-far / Fog 본문을 담는다. `glab issue create --label wayfinder:map`. (네이티브 에픽이 있는 GitLab 요금제에서는 에픽이 맵을 담을 수도 있지만, 라벨 붙은 이슈는 어디서나 동작한다.)
- **하위 티켓**: 설명 맨 위에 `Part of #<map>`이 있고 `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`) 라벨이 붙은 이슈. 티켓을 맡으면 작업을 진행하는 개발자에게 할당한다.
- **차단 관계**: GitLab의 **네이티브 차단 링크**가 표준이며 UI에 표시된다. `/blocked_by #<n>` 빠른 작업을 노트로 게시해 추가한다(`glab issue note <child> --message "/blocked_by #<blocker>"`). 네이티브 차단 링크는 Premium/Ultimate 기능이다. 무료 요금제(또는 사용할 수 없는 곳)에서는 설명 맨 위의 `Blocked by: #<n>, #<n>` 줄로 대신한다. 모든 차단 이슈가 닫히면 티켓의 차단이 풀린다.
- **프런티어 조회**: 맵의 하위 이슈로 범위를 한정한 `glab issue list -O json`에서 열린 차단 이슈가 있는 것(열린 이슈로의 네이티브 `blocked_by` 링크 — `glab api projects/:id/issues/<child-iid>/links` — 또는 `Blocked by` 줄의 열린 이슈)이나 담당자가 있는 것을 제외한다. 맵 순서상 첫 번째가 선택된다.
- **맡기(Claim)**: `glab issue update <n> --assignee @me`, 세션의 첫 번째 쓰기 작업.
- **해결**: `glab issue note <n> --message "<answer>"`, 이어서 `glab issue close <n>`, 그런 다음 맵의 Decisions-so-far에 컨텍스트 포인터(요지 + 링크)를 덧붙인다.
