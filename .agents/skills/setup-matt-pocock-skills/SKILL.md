---
name: setup-matt-pocock-skills
description: "엔지니어링 스킬을 위해 이 저장소를 설정한다: 이슈 트래커, 트리아지 라벨 용어, 도메인 문서 구조를 구성한다. 다른 엔지니어링 스킬을 처음 사용하기 전에 한 번 실행한다."
disable-model-invocation: true
---

# Matt Pocock 스킬 설정

엔지니어링 스킬들이 전제로 하는 저장소별 설정을 만든다:

- **이슈 트래커**: 이슈가 있는 곳 (기본값은 GitHub이며, 로컬 마크다운도 기본 지원한다)
- **트리아지 라벨**: 다섯 가지 표준 트리아지 역할에 쓰이는 문자열
- **도메인 문서**: `GLOSSARY.md`와 ADR이 있는 위치, 그리고 이를 읽는 규칙

이것은 결정론적인 스크립트가 아니라 프롬프트 기반 스킬이다. 탐색하고, 발견한 것을 제시하고, 사용자에게 확인받은 뒤 작성한다.

## 절차

### 1. 탐색

현재 저장소의 시작 상태를 파악한다. 존재하는 것을 직접 읽고, 추측하지 않는다:

- `git remote -v`와 `.git/config`: GitHub 저장소인가? 어느 저장소인가?
- 저장소 루트의 `AGENTS.md`와 `CLAUDE.md`: 둘 중 하나라도 있는가? 이미 `## Agent skills` 섹션이 있는가?
- 저장소 루트의 `GLOSSARY.md`와 `GLOSSARY-MAP.md`
- `docs/adr/` 및 `src/*/docs/adr/` 디렉터리
- `docs/agents/`: 이 스킬의 이전 산출물이 이미 있는가?
- `.scratch/`: 로컬 마크다운 이슈 트래커 관례가 이미 쓰이고 있다는 신호
- `triage` 스킬이 설치되어 있는가? (이 스킬 옆의 `triage` 스킬 폴더, 또는 사용 가능한 스킬 목록의 `triage`.) 이것으로 섹션 B를 실행할지 여부가 정해진다.
- 모노레포 신호: `pnpm-workspace.yaml`, `package.json`의 `workspaces` 필드, 또는 자체 `src/`를 가진 `packages/*`가 채워져 있음. 이런 신호는 정말로 큰 다중 패키지 저장소에만 있다. 신호가 없으면 단일 컨텍스트이며, 거의 모든 저장소가 여기에 해당한다.

### 2. 발견 사항 제시와 질문

무엇이 있고 무엇이 없는지 요약한다. 그런 다음 섹션을 순서대로 다룬다. 섹션 하나에 답 하나, 그리고 다음 섹션.

각 섹션은 사용자가 한마디로 수락할 수 있도록 권장 답안으로 시작한다. 선택지가 실제로 갈릴 때만 한 줄 설명을 붙이고, 탐색으로 이미 결정된 섹션은 통째로 건너뛴다(`triage`가 설치되지 않았으면 섹션 B, 모노레포가 아니면 섹션 C).

**섹션 A: 이슈 트래커.**

> 설명: "이슈 트래커"는 이 저장소의 이슈가 있는 곳이다. `to-tickets`, `triage`, `to-spec` 같은 스킬이 여기서 읽고 여기에 쓴다. 이 스킬들은 `gh issue create`를 호출할지, `.scratch/` 아래에 마크다운 파일을 쓸지, 아니면 설명된 다른 워크플로를 따를지 알아야 한다. 이 저장소의 작업을 실제로 추적하는 곳을 고른다.

기본 자세: 이 스킬들은 GitHub를 염두에 두고 설계되었다. `git remote`가 GitHub를 가리키면 GitHub를 제안한다. `git remote`가 GitLab(`gitlab.com` 또는 자체 호스팅)을 가리키면 GitLab을 제안한다. 그 외의 경우(또는 사용자가 원하면) 다음을 제시한다:

- **GitHub**: 이슈는 저장소의 GitHub Issues에 있다 (`gh` CLI 사용)
- **GitLab**: 이슈는 저장소의 GitLab Issues에 있다 ([`glab`](https://gitlab.com/gitlab-org/cli) CLI 사용)
- **로컬 마크다운**: 이슈는 이 저장소의 `.scratch/<feature>/` 아래 파일로 있다 (1인 프로젝트나 원격이 없는 저장소에 적합)
- **기타** (Jira, Linear 등): 사용자에게 워크플로를 한 문단으로 설명해 달라고 한다. 스킬은 이를 자유 서술로 기록한다

선택을 `docs/agents/issue-tracker.md`에 기록한다. GitHub와 GitLab 템플릿에는 "요청 창구로서의 PR" 플래그가 있으며 기본값은 **꺼짐**이다. 꺼 둔 채로 두고 언급하지 않는다. 외부 PR을 트리아지 대기열에 넣고 싶은 사용자는 나중에 파일에서 플래그를 바꿀 수 있다.

**섹션 B: 트리아지 라벨 용어.** `triage` 스킬이 설치되어 있지 않으면(탐색에서 확인됨) 이 섹션은 통째로 건너뛴다. 설치되지 않은 스킬에는 라벨이 필요 없기 때문이다.

설치되어 있다면 질문은 정확히 하나만 한다:

> 기본 트리아지 라벨을 유지하시겠습니까? (권장: **예**)

기본값은 다섯 가지 표준 역할이며, 각 라벨 문자열은 역할 이름과 같다: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. **예**라면 그대로 기록한다. 사용자가 아니라고 할 때만(보통 트래커가 이미 다른 이름, 예컨대 `needs-triage` 대신 `bug:triage`를 쓰고 있어서) 재정의할 이름을 수집해, `triage`가 중복 라벨을 만들지 않고 기존 라벨을 적용하도록 한다.

**섹션 C: 도메인 문서.** 기본값은 **단일 컨텍스트**(저장소 루트에 `GLOSSARY.md` 하나 + `docs/adr/`)다. 거의 모든 저장소에 맞으므로 묻지 않고 작성한다.

탐색에서 모노레포 신호가 발견된 경우에만 **다중 컨텍스트**(루트의 `GLOSSARY-MAP.md`가 컨텍스트별 `GLOSSARY.md`를 가리킴)를 제안한다. 그런 다음 어떤 구조를 원하는지 확인한다.

### 3. 확인과 수정

사용자에게 다음 초안을 보여 준다:

- 편집할 `CLAUDE.md` / `AGENTS.md`에 추가할 `## Agent skills` 블록 (선택 규칙은 4단계 참고)
- `docs/agents/issue-tracker.md`, `docs/agents/domain.md`, `docs/agents/triage-labels.md`의 내용 (마지막 파일은 `triage`가 설치된 경우에만)

작성하기 전에 사용자가 수정할 수 있게 한다.

### 4. 작성

**편집할 파일 고르기:**

- `CLAUDE.md`가 있으면 그것을 편집한다.
- 없고 `AGENTS.md`가 있으면 그것을 편집한다.
- 둘 다 없으면 어느 것을 만들지 사용자에게 묻는다. 대신 고르지 않는다.

`CLAUDE.md`가 이미 있으면 `AGENTS.md`를 만들지 않는다(반대도 마찬가지). 항상 이미 있는 파일을 편집한다.

선택한 파일에 `## Agent skills` 블록이 이미 있으면 중복으로 덧붙이지 말고 그 자리에서 내용을 갱신한다. 주변 섹션에 대한 사용자의 수정은 덮어쓰지 않는다.

블록:

```markdown
## Agent skills

### Issue tracker

[이슈를 추적하는 곳에 대한 한 줄 요약]. `docs/agents/issue-tracker.md` 참고.

### Triage labels

[라벨 용어에 대한 한 줄 요약]. `docs/agents/triage-labels.md` 참고.

### Domain docs

[구조에 대한 한 줄 요약: "단일 컨텍스트" 또는 "다중 컨텍스트"]. `docs/agents/domain.md` 참고.
```

`### Triage labels` 하위 블록과 `docs/agents/triage-labels.md`는 `triage`가 설치되어 섹션 B를 실행한 경우에만 포함/작성한다. 그렇지 않으면 둘 다 생략한다.

GitHub 또는 GitLab에서 섹션 B를 실행했다면, 트래커에 없는 설정된 라벨을 각각 생성한다(`gh label create` / `glab label create`).

그런 다음 이 스킬 폴더의 시드 템플릿을 출발점으로 삼아 문서 파일을 작성한다:

- [issue-tracker-github.md](./issue-tracker-github.md): GitHub 이슈 트래커
- [issue-tracker-gitlab.md](./issue-tracker-gitlab.md): GitLab 이슈 트래커
- [issue-tracker-local.md](./issue-tracker-local.md): 로컬 마크다운 이슈 트래커
- [triage-labels.md](./triage-labels.md): 라벨 매핑 (`triage`가 설치된 경우에만)
- [domain.md](./domain.md): 도메인 문서를 읽는 규칙 + 구조

"기타" 이슈 트래커라면 사용자의 설명을 바탕으로 `docs/agents/issue-tracker.md`를 처음부터 작성한다.

### 5. 완료

설정이 완료되었다는 것과, 어떤 엔지니어링 스킬이 이제 이 파일들을 읽게 되는지 사용자에게 알린다. 나중에 `docs/agents/*.md`를 직접 수정할 수 있으며, 이 스킬을 다시 실행할 필요는 이슈 트래커를 바꾸거나 처음부터 다시 시작하고 싶을 때뿐이라고 언급한다.
