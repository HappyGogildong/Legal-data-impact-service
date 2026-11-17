# ops/deploy — 운영 배포 파일 (EC2 + RDS)

설계와 AWS 구축 런북은 `docs/backend/cloud-infra.md` §5, 접속·배포·배치·DB 점검 명령 모음은 `docs/backend/ops-manual.md`.

| 파일 | 역할 |
|---|---|
| `lia` | 배포 래퍼 — Parameter Store 비밀값을 환경변수로 넘겨 compose 실행(`up <SHA>` · `ingest <인자>` · `eval` · `ps` · `logs <서비스>`) |
| `docker-compose.prod.yml` | 운영 구성 — caddy · core · ingest(batch 프로파일) · eval(eval 프로파일, 수동 전용) · prometheus · tempo · grafana |
| `prod.env` | 비밀 아닌 설정(주소·DB 호스트·배치 스위치). 런북 단계에서 `CHANGE-ME` 를 채운다 |
| `Caddyfile` | TLS + actuator 차단(health 만 허용) |
| `prometheus.prod.yml` · `tempo.prod.yaml` | 운영 관측 설정(보존 15일 · 72시간) |
| `bootstrap.sh` | EC2 최초 준비(docker·compose·스왑·시간대·clone) |
| `crontab` · `logrotate.conf` | 정기 배치(매일 04:00 `run`, 일요일 05:00 `run --full`)와 로그 정리 |
| `cd-deploy.sh` | 자동 배포 서버 단계(D86) — 배치 대기(최대 60분) → `lia up <tag>` → crontab 차이 경고 → `DEPLOYED` 줄. SSM 문서 `LIA-Deploy` 가 `git pull` 뒤 실행 |
| `cd/` | 자동 배포 정의 — SSM 문서 `LIA-Deploy.yaml`, 역할 신뢰·권한 정책(자리값), 태그 결정 `resolve-tag.sh`, 시험 `*.test.sh` |

`.image-tag` 는 래퍼가 쓰는 현재 이미지 태그다(비밀 아님, 커밋하지 않는다).
