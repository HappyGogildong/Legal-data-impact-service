---
title: 운영 명령 매뉴얼 — 접속·배포·배치·DB 점검
status: Living
version: 0.2
date: 2026-11-17
tags: [backend, ops, runbook, aws, batch]
related: ["backend/cloud-infra.md", "backend/cost-policy.md", "components/batch/IngestJob.md", "troubleshooting/008-same-day-promulgations.md"]
---

# 운영 명령 매뉴얼

운영(EC2·RDS)에서 실제로 쓰는 명령을 상황별로 모은다. 설계와 최초 구축 순서는 [[cloud-infra]], 비용 결정은 [[cost-policy]]. 2026-11 첫 구축·백필에서 쓴 명령이 출발점이다.

- **자리값** — `<instance-id>` = EC2 인스턴스 ID(`i-0…`), `<RDS 엔드포인트>` = `lia-db.….ap-northeast-2.rds.amazonaws.com`, `<API 이름>` = `api.<EIP 점→하이픈>.sslip.io`. 실제 값은 콘솔과 `ops/deploy/prod.env` 에 있다.
- **어디서 실행하나** — 🖥 로컬 PowerShell · ☁ EC2 SSM 세션 · 🗄 DB 클라이언트(psql·DBeaver).
- **비용 표시** — 💰 유료 호출(임베딩·Haiku·Opus)이 나가는 명령. 표시가 없으면 무료(법제처 API·DB·AWS 기본 요금만).
- **비밀값은 채팅·레포·화면에 남기지 않는다** — Parameter Store 에서 변수나 클립보드로만 옮긴다.

## 1. 접속

### 1.1 로컬 AWS CLI 준비 (처음 한 번) 🖥

AWS CLI v2 와 Session Manager plugin 을 설치하고, 관리자 IAM 사용자의 액세스 키를 넣는다. 키는 IAM → 사용자 → 보안 자격 증명 → 액세스 키 만들기(사용 사례 "CLI"). **Access Key ID 는 사용자 이름이 아니라 `AKIA…` 키다.**

```bash
aws configure                          # 키 ID · 비밀 키 · ap-northeast-2 · json
aws sts get-caller-identity            # 관리자 사용자 ARN 이 나오면 성공
session-manager-plugin --version       # 플러그인 설치 확인
aws ssm describe-instance-information --region ap-northeast-2 --query "InstanceInformationList[].[InstanceId,PingStatus]" --output text   # i-0… Online
```

키는 `%USERPROFILE%\.aws\credentials` 에 평문으로 남는다. 쓰지 않을 땐 IAM 에서 비활성화한다.

### 1.1.1 더블클릭 터널 스크립트 (로컬 전용) 🖥

§1.3·§1.4 터널을 더블클릭으로 여는 Windows 배치 파일. **레포에 두지 않고** 각자 PC(예: 바탕 화면)에 만든다. 아래 내용을 메모장에 붙여 넣고 **인코딩 UTF-8** 로 `lia-grafana.cmd`·`lia-db.cmd` 로 저장한다.

- 인스턴스 ID 는 파일에 적지 않고 실행할 때 EIP 로 찾는다(`ec2:DescribeInstances` — 관리자 IAM 키면 된다).
- 창을 닫으면 터널이 끊긴다. AWS CLI·plugin 을 설치한 직후라면 탐색기를 새로 열고 실행한다(PATH 반영).
- `lia-db.cmd` 는 시작할 때 `lia_app` 비밀번호를 클립보드에 복사할지 묻는다(10초 뒤 아니오). 값은 화면에 찍히지 않는다.

`lia-grafana.cmd` — Grafana 터널(로컬 3001)을 열고 5초 뒤 브라우저로 `http://localhost:3001`.

```bat
@echo off
chcp 65001 >nul
setlocal
set REGION=ap-northeast-2
set EIP=15.165.252.201
set LOCAL_PORT=3001
where aws >nul 2>&1 || (echo [오류] AWS CLI 가 없다. & pause & exit /b 1)
for /f "delims=" %%i in ('aws ec2 describe-instances --region %REGION% --filters Name^=ip-address^,Values^=%EIP% --query "Reservations[0].Instances[0].InstanceId" --output text') do set INSTANCE_ID=%%i
if "%INSTANCE_ID%"=="" (echo [오류] 인스턴스를 찾지 못했다 — aws sts get-caller-identity 확인. & pause & exit /b 1)
if "%INSTANCE_ID%"=="None" (echo [오류] EIP %EIP% 인스턴스 없음. & pause & exit /b 1)
echo Grafana 터널 -^> http://localhost:%LOCAL_PORT% (admin / GF_SECURITY_ADMIN_PASSWORD). 끝내려면 창을 닫는다.
start "" /b cmd /c "timeout /t 5 /nobreak >nul & start http://localhost:%LOCAL_PORT%"
aws ssm start-session --region %REGION% --target %INSTANCE_ID% --document-name AWS-StartPortForwardingSession --parameters portNumber=3001,localPortNumber=%LOCAL_PORT%
pause
```

`lia-db.cmd` — DB 터널(로컬 15432). DBeaver 설정은 §1.4.

```bat
@echo off
chcp 65001 >nul
setlocal
set REGION=ap-northeast-2
set EIP=15.165.252.201
set RDS_HOST=lia-db.ctu4qekm26da.ap-northeast-2.rds.amazonaws.com
set LOCAL_PORT=15432
where aws >nul 2>&1 || (echo [오류] AWS CLI 가 없다. & pause & exit /b 1)
for /f "delims=" %%i in ('aws ec2 describe-instances --region %REGION% --filters Name^=ip-address^,Values^=%EIP% --query "Reservations[0].Instances[0].InstanceId" --output text') do set INSTANCE_ID=%%i
if "%INSTANCE_ID%"=="" (echo [오류] 인스턴스를 찾지 못했다 — aws sts get-caller-identity 확인. & pause & exit /b 1)
if "%INSTANCE_ID%"=="None" (echo [오류] EIP %EIP% 인스턴스 없음. & pause & exit /b 1)
choice /c YN /n /t 10 /d N /m "lia_app 비밀번호를 클립보드에 복사할까? [Y/N] (10초 뒤 N) "
if errorlevel 2 goto :tunnel
aws ssm get-parameter --region %REGION% --name /lia/prod/SPRING_DATASOURCE_PASSWORD --with-decryption --query Parameter.Value --output text | powershell -NoProfile -Command "$input | Set-Clipboard"
:tunnel
echo DB 터널 -^> localhost:%LOCAL_PORT% (lia · lia_app · sslmode=require · 읽기 전용). 끝내려면 창을 닫는다.
aws ssm start-session --region %REGION% --target %INSTANCE_ID% --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters host=%RDS_HOST%,portNumber=5432,localPortNumber=%LOCAL_PORT%
pause
```

### 1.2 EC2 셸

- 콘솔: EC2 → 인스턴스 → 연결 → Session Manager.
- 로컬 🖥:
  ```bash
  aws ssm start-session --region ap-northeast-2 --target <instance-id>
  ```

### 1.3 Grafana (EC2 의 127.0.0.1:3001 → 로컬 3001) 🖥

```bash
aws ssm start-session --region ap-northeast-2 --target <instance-id> --document-name AWS-StartPortForwardingSession --parameters portNumber="3001",localPortNumber="3001"
```

`Waiting for connections...` 가 나오면 브라우저에서 `http://localhost:3001`, `admin` / `GF_SECURITY_ADMIN_PASSWORD`. 창을 닫거나 Ctrl+C 면 터널이 끊긴다.

Discord 경보의 링크(`Source`·`Silence`)도 `http://localhost:3001/…` 이다(`GF_SERVER_ROOT_URL`). 이 터널을 연 상태에서 누르면 열린다. Grafana 를 EIP 로 공개하지 않는다 — 보안 그룹은 80·443 만 연다.

### 1.4 DB — DBeaver 등 GUI (RDS 5432 → 로컬 15432) 🖥

```bash
aws ssm start-session --region ap-northeast-2 --target <instance-id> --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters host="<RDS 엔드포인트>",portNumber="5432",localPortNumber="15432"
```

| 항목 | 값 |
|---|---|
| Host · Port | `localhost` · `15432`(로컬 docker Postgres 의 5432 와 겹치지 않게) |
| Database · User | `lia` · `lia_app` |
| Password | `/lia/prod/SPRING_DATASOURCE_PASSWORD` — 아래 명령으로 클립보드에 |
| Driver properties | `sslmode` = `require` |
| General → Security | **Read-only connection** (앱 계정이라 쓰기 권한이 있다) |

```bash
aws ssm get-parameter --region ap-northeast-2 --name /lia/prod/SPRING_DATASOURCE_PASSWORD --with-decryption --query Parameter.Value --output text | Set-Clipboard
```

`grafana_reader` 는 읽기 전용이지만 배치·호출 기록 테이블만 보인다. 계정·프로필 테이블은 열지 않는다(D41).

### 1.5 DB — EC2 안의 psql (최초 설정·일회 점검) ☁

마스터 암호를 화면에 찍지 않고 변수로 넘긴다.

```bash
PW=$(aws ssm get-parameters-by-path --region ap-northeast-2 --path /lia/prod/ --with-decryption --query "Parameters[?Name=='/lia/prod/DB_MASTER_PASSWORD'].Value" --output text)
sudo docker run --rm -it -e PGPASSWORD="$PW" postgres:16 psql "host=<RDS 엔드포인트> port=5432 dbname=lia user=postgres sslmode=require"
```

SQL 여러 개를 한 번에 넘길 땐 `-it` 대신 `-i` 와 heredoc — 붙여 넣기 중 psql 메타 명령이 섞이는 사고가 없다.

```bash
sudo docker run --rm -i -e PGPASSWORD="$PW" postgres:16 psql "host=<RDS 엔드포인트> port=5432 dbname=lia user=postgres sslmode=require" <<'SQL'
SELECT run_id, command, outcome FROM ingest_runs ORDER BY run_id DESC LIMIT 3;
SQL
```

- psql 메타 명령(`\c`·`\du`·`\dx`·`\q`)은 한 줄에 하나씩. `/q` 처럼 잘못 치면 `\r` 로 입력 버퍼를 비운다.
- 앱 계정으로 들어가려면 `DB_MASTER_PASSWORD` 대신 `SPRING_DATASOURCE_PASSWORD`, `user=lia_app`.

## 2. 배포 ☁

```bash
cd /opt/lia && sudo git pull                 # ops/ 설정 파일 갱신 (compose·Caddyfile·대시보드·crontab)
sudo /opt/lia/ops/deploy/lia up <커밋 SHA>    # 이미지 교체 — Parameter Store → 환경변수 → pull → up -d
sudo /opt/lia/ops/deploy/lia ps              # 컨테이너 상태
sudo /opt/lia/ops/deploy/lia logs core       # 최근 로그 200줄 (서비스: core · caddy · grafana …)
```

- **태그는 CI 가 이미지를 만든 커밋 SHA(전체)** — `ops/`·`docs/` 만 바꾼 머지는 이미지가 없다. GitHub Actions 의 `ci-core` 실행에서 고른다.
- `lia up` 은 이미지만 바꾼다. `ops/` 파일은 `git pull` 로만 바뀐다.
- `lia ingest`·`lia eval` 은 마지막 `up` 태그(`ops/deploy/.image-tag`)를 쓴다.
- 롤백 `lia up <이전 SHA>` — Flyway 는 앞으로만이라 스키마가 바뀐 배포는 RDS 스냅샷으로 되돌린다.
- 배치 시간대(04:00~05:30)에는 배포하지 않는다.

**배포 뒤 확인** 🖥

```bash
curl -s https://<API 이름>/actuator/health                 # {"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" https://<API 이름>/actuator/prometheus   # 차단(404·403)
curl -s https://<API 이름>/api/v1/auth/providers           # {"providers":["google"]}
```

## 3. 배치 ☁

명령 형식은 `sudo /opt/lia/ops/deploy/lia ingest <명령>`. 종료 코드 0 성공 · 1 실패 · 2 부분 성공.

| 명령 | 하는 일 | 비용 |
|---|---|---|
| `run --dry-run` | 목록만 받아 계획(`planned.SYNC`·`mergedKeys`)을 기록. 저장·색인 없음 | 무료 |
| `run` | 바뀐 버전만 SYNC → 바뀐 시행예정본 INDEX → 알림. cron 매일 04:00 | 💰 임베딩(바뀐 것만) |
| `run --full` | 전 버전 본문을 다시 받는다. revision 이 같으면 저장·색인 생략. cron 일요일 05:00 | 💰 정규화 코드가 바뀐 배포 뒤엔 전체 재색인 |
| `sync <lawId> <yyyy-MM-dd> [mst]` | 한 버전만 다시 받기 | 💰 바뀌면 임베딩 1건 |
| `index` | 쌓인 INDEX 만 처리 | 💰 |
| `reindex-all` | 시행예정 전체를 INDEX 에 다시 등록 | 💰 백필과 같은 규모 |
| `summarize --dry-run` | 요약 호출 수(`planned.SUMMARIZE.calls`)만 계산 | 무료 |
| `summarize` | 요약·변경점이 없거나 낡은 시행예정본을 Opus 로 채움 | 💰💰 Opus — 시행예정 877건이면 약 $155+ 추정([[cost-policy]]) |
| `requeue-dead` | 격리(DEAD) 작업을 READY 로 되돌림 — 원인을 본 뒤에 | 작업에 따라 |
| `notify [--dry-run]` | 구독 알림 생성만 | 무료 |

**백필처럼 오래 걸리는 실행** — SSM 세션이 끊겨도 계속 돌도록 `nohup`:

```bash
sudo nohup /opt/lia/ops/deploy/lia ingest run > /tmp/backfill.log 2>&1 &
sudo tail -f /tmp/backfill.log          # Ctrl+C 는 tail 만 멈춘다. 배치는 계속
sudo tail -n 5 /tmp/backfill.log        # 마지막 줄 '[batch] 실행 N <결과> — {...}' 이 요약
```

**지금 돌고 있는지 · 진행 상황**

```bash
sudo docker ps --filter name=ingest --format "{{.Names}}  {{.Status}}"   # 배치가 떠 있을 때만 보인다
sudo tail -f /var/log/lia/ingest.log | grep --line-buffered "INDEX\|실패\|\[batch\] 실행"   # cron 실행 로그
grep -c "INDEX .* 완료" /var/log/lia/ingest.log                          # INDEX 완료 줄 수
grep "\[batch\] 실행" /var/log/lia/ingest.log | tail -3                  # 실행 요약 줄
```

작업마다 `[batch] <종류> <법령ID>@<시행일> 완료(시도 N)` 한 줄, 실행 끝에 `[batch] 실행 N <결과> — {counts}` 한 줄이 남는다. 실행 결과의 기준은 로그가 아니라 `ingest_runs` 다(§5).

**cron 경로를 무료로 시험** — cron 과 같은 PATH·로그 경로로 dry-run:

```bash
sudo bash -c 'PATH=/usr/local/bin:/usr/bin:/bin /opt/lia/ops/deploy/lia ingest run --dry-run >> /var/log/lia/ingest.log 2>&1'; sudo tail -n 3 /var/log/lia/ingest.log
```

## 4. cron ☁

**백필이 끝난 뒤에 설치한다** — 먼저 설치하면 04:00 `run` 이 유료 백필을 사람 없이 돈다.

```bash
sudo crontab /opt/lia/ops/deploy/crontab                       # 설치(레포 파일로 root 예약을 덮어쓴다)
sudo crontab -l                                                # 0 4 * * * · 0 5 * * 0 두 줄
sudo ls -l --time-style=full-iso /var/spool/cron/root          # 설치 시각 — 그 뒤 첫 04:00 이 첫 실행
systemctl is-active crond                                      # active
date                                                           # KST 여야 한다(bootstrap 이 Asia/Seoul 로 맞춤)
systemctl show crond -p ActiveEnterTimestamp                   # crond 시작 시각 — 시간대를 바꾼 뒤여야 한다(아래)
sudo grep "\[batch\] 실행" /var/log/lia/ingest.log | tail -n 3 # 실행 요약 줄. 로그 시각은 UTC(Z) — 04:00 KST = 전날 19:00Z
ls -la /var/log/lia/                                           # 폴더가 없으면 리다이렉트 실패로 배치가 시작조차 안 된다
sudo crontab -r                                                # 해제(root 예약 전체 삭제)
sudo systemctl restart crond                                   # 시간대를 바꾸기 전에 뜬 crond 를 KST 로
```

**시간대 함정(2026-11-17 실측)** — `bootstrap.sh` 가 crond 를 시간대 설정보다 먼저 띄워, `date` 는 KST 인데 crond 는 UTC 기준으로 예약을 돌렸다. 첫 정기 실행(run 5)이 04:00 KST 가 아니라 **13:00 KST(04:00 UTC)** 에 돌았다. crond 시작 시각이 시간대 변경보다 앞이면 `sudo systemctl restart crond`. 지금 `bootstrap.sh` 는 시간대를 먼저 맞추고 crond 를 재시작한다.

- 시각을 볼 때 단위를 맞춘다 — 배치 로그는 UTC(`…Z`), `ingest_runs.started_at` 도 psql 세션이 UTC 면 `+00` 으로 보인다. KST 로 보려면 `started_at AT TIME ZONE 'Asia/Seoul'`.
- `journalctl --since "today 03:50"` 은 이 서버에서 `Failed to parse timestamp` 로 실패한다. 실행 확인은 위 로그 요약 줄과 `ingest_runs`(§5.1)로 한다.

로그 파일 `/var/log/lia/ingest.log` 는 첫 실행 때 생긴다. logrotate(`/etc/logrotate.d/lia`)는 bootstrap 이 설치했다.

## 5. DB 점검 SQL 🗄

DBeaver(§1.4, 읽기 전용) 또는 EC2 psql(§1.5)에서. 모두 읽기 전용.

### 5.1 배치 실행·작업 큐

```sql
-- 최근 실행: 결과·소요 시간·건수(done·failed·dead·mergedKeys·deferred.*)
SELECT run_id, command, outcome, list_complete, finished_at - started_at AS duration, counts
FROM ingest_runs ORDER BY run_id DESC LIMIT 5;

-- 작업 큐 상태
SELECT kind, status, count(*) FROM ingest_tasks GROUP BY 1, 2 ORDER BY 1, 2;

-- 격리(DEAD) 작업과 오류
SELECT kind, law_id, effective_date, attempts, left(last_error, 160) AS error
FROM ingest_tasks WHERE status = 'DEAD' ORDER BY kind, law_id LIMIT 20;
```

### 5.2 저장된 법령 버전

```sql
SELECT status, count(*) FROM law_versions GROUP BY 1 ORDER BY 1;

-- 같은 시행일 공포본 병합(D85): mst 가 쉼표 목록인 버전과 바뀐 조문 수
SELECT count(*) AS merged, count(*) FILTER (WHERE changed = 0) AS merged_without_changes
FROM (SELECT (SELECT count(*) FROM jsonb_array_elements(payload->'articles') a
              WHERE (a->>'changed')::boolean) AS changed
      FROM law_versions WHERE payload->>'mst' LIKE '%,%') m;

-- 시행예정 버전 목록(바뀐 조문 수 포함)
SELECT law_id, effective_date, title, payload->>'mst' AS mst,
       jsonb_array_length(payload->'articles') AS articles,
       (SELECT count(*) FROM jsonb_array_elements(payload->'articles') a
        WHERE (a->>'changed')::boolean) AS changed
FROM law_versions WHERE status = '시행예정' ORDER BY effective_date LIMIT 50;
```

### 5.3 유료 호출·비용

```sql
-- 목적·모델별 호출 수 (배치 임베딩 = INDEX_EMBED, 웹 법령 발견 = DISCOVERY_EMBED)
SELECT purpose, model, count(*) FROM llm_calls GROUP BY 1, 2;

-- 임베딩 토큰
SELECT model, count(*) AS calls, count(*) FILTER (WHERE NOT succeeded) AS failed,
       sum(input_tokens) AS tokens, min(called_at) AS first_call, max(called_at) AS last_call
FROM llm_calls WHERE purpose = 'INDEX_EMBED' GROUP BY 1;
```

달러 금액은 `llm_prices` 에 단가를 넣은 뒤 `llm_call_costs` 뷰와 `LIA · Token & Cost` 대시보드로 본다. 단가 입력은 쓰기라 읽기 전용 연결을 잠시 끄고 마스터·앱 계정으로([[cost-policy]] "단가 넣는 법").

### 5.4 색인(벡터) 상태

```sql
-- 청크 수·법령 수
SELECT count(*) AS chunks, count(DISTINCT metadata->>'lawId') AS laws FROM vector_store;

-- 색인돼야 할 버전(바뀐 조문이나 제개정이유가 있음)에 청크가 있는가 — true/true · false/false 만 정상
WITH v AS (
  SELECT law_id, effective_date::text AS efyd,
         (SELECT count(*) FROM jsonb_array_elements(payload->'articles') a
          WHERE (a->>'changed')::boolean) > 0
         OR coalesce(payload->>'amendReason', '') <> '' AS should_index
  FROM law_versions WHERE status = '시행예정'),
c AS (SELECT metadata->>'lawId' AS law_id, metadata->>'efYd' AS efyd, count(*) AS chunks
      FROM vector_store GROUP BY 1, 2)
SELECT should_index, c.chunks IS NOT NULL AS indexed, count(*)
FROM v LEFT JOIN c USING (law_id, efyd) GROUP BY 1, 2;

-- 시행예정 버전 없이 남은 청크 — 0 이어야
SELECT count(*) AS orphan_chunks FROM vector_store c
WHERE NOT EXISTS (SELECT 1 FROM law_versions v
                  WHERE v.law_id = c.metadata->>'lawId'
                    AND v.effective_date::text = c.metadata->>'efYd'
                    AND v.status = '시행예정');

-- 벡터 무결성 — 벡터 없음 0, 차원 1536, 길이 ≈ 1.000
SELECT count(*) FILTER (WHERE embedding IS NULL) AS no_vector,
       min(vector_dims(embedding)) AS min_dim, max(vector_dims(embedding)) AS max_dim,
       round(min(vector_norm(embedding))::numeric, 3) AS min_norm,
       round(max(vector_norm(embedding))::numeric, 3) AS max_norm
FROM vector_store;

-- 청크 종류·길이 — max_len ≤ 2500
SELECT metadata->>'kind' AS kind, count(*), avg(length(content))::int AS avg_len, max(length(content)) AS max_len
FROM vector_store GROUP BY 1;

-- 무료 검색 시험: 저장된 벡터를 검색어로 가장 가까운 청크 5개(1위는 자기 자신, 거리 0)
SELECT metadata->>'title' AS title, metadata->>'kind' AS kind, left(content, 60) AS content,
       round((embedding <=> q.e)::numeric, 3) AS distance
FROM vector_store,
     (SELECT embedding AS e FROM vector_store
      WHERE metadata->>'lawId' = '000851' AND metadata->>'kind' = 'summary' LIMIT 1) q
ORDER BY embedding <=> q.e LIMIT 5;
```

## 6. 비밀값 ☁ 🖥

| 하고 싶은 것 | 명령 |
|---|---|
| EC2 에서 값 하나를 변수로 | `PW=$(aws ssm get-parameters-by-path --region ap-northeast-2 --path /lia/prod/ --with-decryption --query "Parameters[?Name=='/lia/prod/<이름>'].Value" --output text)` |
| 로컬에서 값 하나를 클립보드로 | `aws ssm get-parameter --region ap-northeast-2 --name /lia/prod/<이름> --with-decryption --query Parameter.Value --output text \| Set-Clipboard` |
| 콘솔에서 보기 | Systems Manager → Parameter Store → 이름 → 값 표시 |

EC2 역할은 `GetParametersByPath` 만 허용하므로 EC2 에서는 `get-parameter` 대신 경로 조회 + `--query` 로 고른다. 값을 바꾼 뒤에는 `lia up <현재 SHA>` 로 컨테이너를 다시 띄워야 반영된다(래퍼가 실행할 때 읽는다). Vercel 환경변수는 바꾼 뒤 재배포해야 반영된다.

## 7. 증상별 진단 (2026-11 구축 기준)

| 증상 | 원인 | 조치 |
|---|---|---|
| psql `Connection timed out` | 보안 그룹 `lia-rds` 가 `lia-ec2` 를 허용하지 않음 | 5432 인바운드 소스를 `lia-ec2` 보안 그룹으로 |
| psql `password authentication failed` | 붙여 넣은 암호에 공백·줄바꿈, 또는 다른 계정 암호 | §1.5 처럼 Parameter Store 에서 변수로 |
| `database "lia" does not exist` | RDS 초기 DB 이름(콘솔 "추가 구성")을 비워 둠 | `dbname=postgres` 로 접속해 `CREATE DATABASE lia;` |
| `ALTER DATABASE/SCHEMA … OWNER TO lia_app` 권한 오류 | RDS 마스터는 superuser 가 아니라 받는 역할의 멤버여야 한다(PG16) — 런북에 미리 넣어 실제로는 겪지 않음 | `GRANT lia_app TO postgres;` 후 다시 |
| `invalid integer value "IF" for connection option "port"` | `\c lia` 와 SQL 을 한꺼번에 붙여 넣음 | 메타 명령은 한 줄씩, 또는 heredoc |
| 배치가 JSON 파싱 오류 `Unexpected character ('<' (code 60))` | 법제처 OPEN API 에 서버 IP 미등록 — HTML("사용자 정보 검증에 실패")이 옴 | open.law.go.kr 신청 정보에 EIP 추가 |
| `lia up` 의 pull 실패(manifest unknown) | 그 SHA 에 CI 이미지가 없음(문서·ops 만 바꾼 머지) | `ci-core` 가 돈 마지막 커밋 SHA 로 |
| 웹 `/api` 가 502 | Vercel `LIA_API_ORIGIN` 변경이 재배포 전 | Vercel 에서 Redeploy |
| 구글 로그인 `invalid_client` | `GOOGLE_CLIENT_ID` 오타 | Parameter Store 수정 후 `lia up <현재 SHA>` |
| 로컬 `StartSession` 이 `403 UnauthorizedRequest: Forbidden` | `aws configure` 에 사용자 이름 등 잘못된 키 | 액세스 키 다시 발급·입력, `aws sts get-caller-identity` |
| DBeaver `password authentication failed`(값이 맞는데) | 포트가 5432 라 로컬 docker DB 로 감 | 포트 `15432`, 터널 창이 열려 있는지 |
| cron 이 돌지 않은 것 같음 | 설치 시각이 그날 04:00 이후 / 로그 폴더 없음 / crond 가 UTC 로 예약 계산 | §4 의 설치 시각·crond 시작 시각·실행 요약 줄·`ls /var/log/lia`·`date` |
| cron 이 04:00 이 아니라 13:00 KST 에 돈다 | crond 가 시간대 변경 전에 떠서 UTC 기준으로 돈다(bootstrap 순서, 2026-11-17 수정) | `sudo systemctl restart crond` — §4 시간대 함정 |
