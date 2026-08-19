# 배포 가이드

구성: **Vercel(프론트) + EC2 프리티어(백엔드) + RDS(DB)**

```
브라우저 ──HTTPS──> Vercel ──HTTP──> EC2 ──> RDS
                    (Next rewrites)
```

Next가 서버에서 프록시하므로 **백엔드에 도메인·인증서·nginx가 필요 없고 CORS도 필요 없다.**
브라우저는 Vercel(HTTPS)하고만 통신한다.

배포는 GitHub Actions가 한다. **EC2에서 빌드하지 않는다** — 1GB 메모리로는 Gradle 빌드가
버겁다. 러너가 JAR를 만들어 보내고 EC2는 실행만 한다.

---

## 1. RDS 생성 (콘솔)

| 항목 | 값 |
|---|---|
| 엔진 | PostgreSQL 17 |
| 템플릿 | **프리 티어** |
| 인스턴스 | db.t4g.micro |
| 스토리지 | 20GB gp3 (자동 확장 끄기 — 프리티어 초과 방지) |
| DB 이름 | `fundcompass` |
| 마스터 사용자 | `fundcompass` |
| 퍼블릭 액세스 | **예** (로컬에서 데이터를 넣어야 한다. 넣은 뒤 아니오로 바꿔도 된다) |

보안 그룹 인바운드에 5432를 열되 **내 IP와 EC2 보안 그룹만** 허용한다. 0.0.0.0/0 은 쓰지 않는다.

## 2. 데이터 이전

공고 1,514건 · 자격요건 1,447건이 들어 있다. **재수집·재추출하면 며칠과 API 비용이 다시 든다.**

```bash
./deploy/dump-db.sh deploy/fundcompass-data.sql
psql -h <rds-endpoint> -U fundcompass -d fundcompass -f deploy/fundcompass-data.sql
```

확인:

```bash
psql -h <rds-endpoint> -U fundcompass -d fundcompass \
  -c "SELECT count(*) FROM program_eligibility;"   # 1447 이어야 한다
```

## 3. EC2 생성 (콘솔)

| 항목 | 값 |
|---|---|
| AMI | Amazon Linux 2023 |
| 인스턴스 | **t3.micro** (프리티어) |
| 키 페어 | 새로 생성하고 .pem 을 안전하게 보관 |
| 보안 그룹 인바운드 | SSH 22 (내 IP만) / TCP 8080 (전체 — Vercel이 부른다) |

접속 후 준비:

```bash
sudo dnf install -y java-21-amazon-corretto-headless
sudo mkdir -p /opt/fundcompass/incoming && sudo chown -R ec2-user:ec2-user /opt/fundcompass
sudo touch /var/log/fundcompass.log && sudo chown ec2-user /var/log/fundcompass.log
```

**스왑을 반드시 만든다.** 1GB로는 JVM이 순간적으로 부족해 죽는다.

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

비밀값과 서비스 등록:

```bash
# deploy/env.template 을 복사해 값을 채운다
vi /opt/fundcompass/.env
chmod 600 /opt/fundcompass/.env

sudo cp fundcompass.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable fundcompass
```

## 4. GitHub Secrets 등록

저장소 → Settings → Secrets and variables → Actions

| 이름 | 값 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | .pem 파일 **내용 전체** (`-----BEGIN` 부터 `-----END` 까지) |

## 5. Vercel 연결

1. Vercel에서 GitHub 저장소를 import
2. **Root Directory 를 `frontend` 로 지정** (모노레포라 반드시 필요)
3. 환경변수 `BACKEND_ORIGIN` = `http://<EC2 퍼블릭 IP>:8080`

`BACKEND_ORIGIN` 은 `NEXT_PUBLIC_` 접두사가 없어 서버에서만 읽힌다 —
백엔드 주소가 브라우저 번들에 노출되지 않는다.

## 6. 배포

`main` 에 푸쉬하면 자동으로 돈다. 백엔드는 Actions, 프론트는 Vercel이 각각 처리한다.

확인:

```bash
curl -X POST http://<EC2-IP>:8080/api/matches -H 'Content-Type: application/json' -d '{}'
```

---

## 주의

- **`PROGRAM_SYNC_ENABLED` 는 처음에 `false`** 로 둔다. 켜면 매일 04:30에 기업마당을 긁는다.
  배포 직후엔 수동으로 한 번 확인하고 켠다.
- **`GEMINI_EXPLAIN_API_KEY` 는 유료 키를 쓴다.** 이 경로로 사용자의 사업자 정보가 나간다.
  무료 티어는 입력이 모델 개선에 사용된다.
- 재시작 시 20~30초 다운타임이 있다. 1GB에서 두 프로세스를 띄울 수 없어 무중단은 포기했다.
- EC2 퍼블릭 IP는 재시작하면 바뀐다. 고정하려면 Elastic IP를 붙인다(인스턴스에 연결돼 있으면 무료).
