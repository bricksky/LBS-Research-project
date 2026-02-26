# LBS 성능 테스트 통합 가이드 (RDBMS vs Kafka)

이 문서는 **itrc-api-rdbms**와 **itrc-api-kafka** 모듈의 성능을 비교 테스트하기 위한 전체 과정을 정리한 가이드입니다. 도커 컨테이너 관리부터 애플리케이션 실행, k6 부하 테스트까지의 모든 명령어를 포함하고 있습니다.

---

## 0. 사전 준비 (Prerequisites)

- **Java 17 이상** 및 **Docker / Docker Compose** 설치
- **k6** 설치 ([k6.io](https://k6.io/docs/getting-started/installation/))
- 프로젝트 루트에서 전체 빌드 수행:
  ```bash
  ./gradlew clean build -x test
  ```

---

## 1. RDBMS 테스트 워크플로우 (Port: 8081)

RDBMS(PostGIS) 기반의 성능을 측정하는 단계입니다.

### 1.1 인프라 실행 (PostGIS & Monitoring)
기존 컨테이너와의 충돌을 방지하기 위해 정지 후 필요한 서비스만 실행합니다.
```bash
# 1. 모든 컨테이너 정지
docker-compose stop

# 2. PostGIS 및 모니터링 도구 실행
docker-compose up -d postgis prometheus grafana
```

### 1.2 애플리케이션 실행
```bash
# 별도 터미널에서 실행
java -jar itrc-api-rdbms/build/libs/itrc-api-rdbms-0.0.1-SNAPSHOT.jar
```

### 1.3 k6 부하 테스트 실행
```bash
# 1. 데이터 시딩 (Seeding)
k6 run --out 'web-dashboard=export=docs/results/report_seeding_rdbms.html' performance-test/seeding01.js

# 2. 위치 업데이트 (Update)
k6 run --out 'web-dashboard=export=docs/results/report_update_rdbms.html' performance-test/update01.js

# 3. 공간 검색 (Query)
k6 run --out 'web-dashboard=export=docs/results/report_query_rdbms.html' performance-test/query01.js
```

---

## 2. Kafka 테스트 워크플로우 (Port: 8082)

Kafka를 활용한 비동기 처리 성능을 측정하는 단계입니다.

### 2.1 인프라 실행 (Kafka, Redis 및 모니터링)
Kafka, Zookeeper, Redis, Prometheus, Grafana 컨테이너를 도커로 실행합니다.

```bash
# 1. 모든 컨테이너 정지
docker-compose stop

# 2. Kafka, Zookeeper, Redis, Prometheus, Grafana만 실행
docker-compose up -d zookeeper kafka redis prometheus grafana
```

### 2.2 애플리케이션 실행
```bash
# 별도 터미널에서 실행
java -jar itrc-api-kafka/build/libs/itrc-api-kafka-0.0.1-SNAPSHOT.jar
```

### 2.3 k6 부하 테스트 실행
```bash
# 1. 데이터 시딩 (Seeding) 테스트
k6 run --out 'web-dashboard=export=docs/results/report_seeding_kafka.html' performance-test/seeding02.js

# 2. 위치 업데이트 (Update)
k6 run --out 'web-dashboard=export=docs/results/report_update_kafka.html' performance-test/update02.js

# 3. 공간 검색 (Query)
k6 run --out 'web-dashboard=export=docs/results/report_query_kafka.html' performance-test/query02.js
```

---

## 3. Redis Stream 테스트 워크플로우 (Port: 8083)

Redis Stream 기반 처리(예: XADD → Consumer Group 처리 → 저장/조회)를 측정하는 단계입니다.

### 3.1 인프라 실행 (Redis Stream 전용 Redis & Monitoring)

Redis Stream 비교군을 **Kafka 워크플로우의 Redis와 분리**해서 운영하는 것을 권장합니다.

(이유: 동일 Redis 인스턴스를 공유하면 키/Stream/consumer-group 충돌, 메모리/IO 간섭, 잔존 데이터 영향이 생겨 “공정 비교”가 어려워짐)

### 권장: Stream 전용 Redis 컨테이너를 별도로 실행

- docker-compose.yml에 `redis-stream` 서비스가 있다고 가정합니다.
- 컨테이너명도 기존 Redis와 다르게(예: `lbs-research-redis-stream`) 잡는 것을 권장합니다.

```markdown
# 1. 모든 컨테이너 정지
docker-compose stop

# 2. Redis(Stream 전용) + 모니터링만 실행
docker-compose up -d redis prometheus grafana
```

### 3.2 애플리케이션 실행 (Port 8083)

```
# 별도 터미널에서 실행
java-jar itrc-api-stream/build/libs/itrc-api-stream-0.0.1-SNAPSHOT.jar
```

### 3.3 k6 부하 테스트 실행 (Stream용 스크립트)

```bash
Stream 비교군도 같은 부하 패턴을 맞추기 위해, k6 스크립트를 **Stream 전용**으로 분리해서 실행합니다.


# 1. 데이터 시딩 (Seeding) 테스트
k6 run --out 'web-dashboard=export=docs/results/report_seeding_stream.html' performance-test/seeding03.js

# 2. 위치 업데이트 (Update)
k6 run --out 'web-dashboard=export=docs/results/report_update_stream.html' performance-test/update03.js

# 3. 공간 검색 (Query)
k6 run --out 'web-dashboard=export=docs/results/report_query_stream.html' performance-test/query03.js
```

## 4. 환경 종료 및 관리

테스트가 끝나면 컨테이너를 정지하여 리소스를 확보합니다.

```bash
# 컨테이너 정지 (데이터 유지)
docker-compose stop

# 컨테이너 및 볼륨 완전 삭제 (데이터 초기화 필요 시)
docker-compose down -v
```

---

## 5. 모니터링 및 결과 확인

- **Grafana 접속**: `http://localhost:3000` (ID: `admin` / PW: `password`)
- **HTML 리포트**: 각 테스트 실행 후 생성된 `docs/results/report_*.html` 파일을 브라우저로 확인
- **컨테이너 이름 확인**:
  - DB: `lbs-research-db`
  - Kafka: `lbs-research-kafka`
  - Redis: `lbs-research-redis`
  - Prometheus: `lbs-research-prometheus`
  - Grafana: `lbs-research-grafana`
