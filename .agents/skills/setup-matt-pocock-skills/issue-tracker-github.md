# 이슈 트래커: GitHub

이 저장소의 이슈와 스펙은 GitHub 이슈로 관리한다. 모든 작업에 `gh` CLI를 사용한다.

## 규칙

- **이슈 생성**: `gh issue create --title "..." --body "..."`. 여러 줄 본문에는 heredoc을 사용한다.
- **이슈 읽기**: `gh issue view <number> --json number,title,body,labels,comments`.
- **이슈 목록**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`에 적절한 `--label`, `--state` 필터를 붙인다.
- **이슈를 상위 이슈의 하위 이슈로 만들기**: `gh issue create --parent <parent> ...`, 또는 생성 후 `gh issue edit <parent> --add-sub-issue <child>` (`gh` 2.94 이상). 이전 버전 `gh`: `gh api --method POST repos/<owner>/<repo>/issues/<parent>/sub_issues -F sub_issue_id=<child-db-id>` (아래 **차단 관계**와 같은 데이터베이스 id). 하위 이슈를 쓸 수 없으면 하위 이슈 본문 맨 위에 `Part of #<parent>`를 적는다.
- **이슈에 댓글 달기**: `gh issue comment <number> --body "..."`
- **라벨 적용 / 제거**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **닫기**: `gh issue close <number> --comment "..."`

저장소는 `git remote -v`로 추론한다. 클론 안에서 실행하면 `gh`가 자동으로 처리한다.

## 트리아지 창구로서의 풀 리퀘스트

**요청 창구로서의 PR: 아니오.** _(이 저장소가 외부 PR을 기능 요청으로 취급한다면 `예`로 설정한다. `/triage`가 이 플래그를 읽는다.)_

`예`로 설정하면 PR도 이슈와 같은 라벨과 상태를 거치며, `gh pr` 대응 명령을 사용한다:

- **PR 읽기**: `gh pr view <number> --comments`, diff는 `gh pr diff <number>`.
- **트리아지할 외부 PR 목록**: `gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, author: .user.login, author_association, labels: [.labels[].name]}'`.
- **댓글 / 라벨 / 닫기**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub는 이슈와 PR이 하나의 번호 공간을 공유하므로 `#42`만으로는 어느 쪽인지 알 수 없다. `gh pr view 42`로 확인하고, 실패하면 `gh issue view 42`로 넘어간다.

## 스킬이 "이슈 트래커에 게시하라"고 할 때

GitHub 이슈를 생성한다.

## 스킬이 "관련 티켓을 가져오라"고 할 때

위의 **이슈 읽기**대로 읽는다.

## 길잡이(Wayfinding) 작업

`/wayfinder`가 사용한다. **맵**은 하나의 이슈이며, **하위** 이슈들이 티켓이다.

- **맵**: `wayfinder:map` 라벨이 붙은 이슈 하나로, Notes / Decisions-so-far / Fog 본문을 담는다. `gh issue create --label wayfinder:map`.
- **하위 티켓**: GitHub 하위 이슈로 맵에 연결된 이슈(**이슈를 상위 이슈의 하위 이슈로 만들기** 참고). 하위 이슈를 쓸 수 없는 곳에서는 맵 본문의 작업 목록에 하위 이슈를 추가하고 하위 이슈 본문 맨 위에 `Part of #<map>`을 적는다. 라벨: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). 티켓을 맡으면 작업을 진행하는 개발자에게 할당한다.
- **차단 관계**: GitHub의 **네이티브 이슈 의존성**이 표준이며 UI에 표시된다. `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`로 관계를 추가한다. 여기서 `<blocker-db-id>`는 차단 이슈의 숫자형 **데이터베이스 id**다(`gh api repos/<owner>/<repo>/issues/<n> --jq .id`. `#number`나 `node_id`가 _아니다_). GitHub는 `issue_dependencies_summary.blocked_by`(열린 차단 이슈만 집계하는 실시간 관문)를 보고한다. 의존성 기능을 쓸 수 없는 곳에서는 하위 이슈 본문 맨 위의 `Blocked by: #<n>, #<n>` 줄로 대신한다. 모든 차단 이슈가 닫히면 티켓의 차단이 풀린다.
- **프런티어 조회**: 맵의 열린 하위 이슈 목록을 가져와(`gh issue list --state open`, 맵의 하위 이슈 / 작업 목록으로 범위 한정) 열린 차단 이슈가 있는 것(`issue_dependencies_summary.blocked_by > 0`, 또는 `Blocked by` 줄에 열린 이슈가 있음)이나 담당자가 있는 것을 제외한다. 맵 순서상 첫 번째가 선택된다.
- **맡기(Claim)**: `gh issue edit <n> --add-assignee @me`, 세션의 첫 번째 쓰기 작업.
- **해결**: `gh issue comment <n> --body "<answer>"`, 이어서 `gh issue close <n>`, 그런 다음 맵의 Decisions-so-far에 컨텍스트 포인터(요지 + 링크)를 덧붙인다.
