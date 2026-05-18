## 한눈에 보기 — LBS 성능 비교 실험 (RDBMS vs Kafka)

이 프로젝트는 **LBS(Location-Based Service)** 환경에서 자주 문제가 되는  
실시간성(Real-time)과 확장성(Scalability)을 중심으로, 아래 두 아키텍처를 **동일한 환경·동일한 시나리오·동일한 지표**로 비교합니다.

- **RDBMS 방식 (Baseline)**: PostgreSQL + PostGIS  
  → 정합성과 공간 질의 기능이 강력하지만, 대규모 동시 쓰기에서 병목이 발생할 수 있음
- **이벤트 기반 방식 (Event-driven)**: Kafka + Redis (비동기 쓰기 + 인메모리 공간 인덱스)  
  → 쓰기 경로를 분리/비동기화하여 고부하 상황에서 확장성을 확보하는 접근

**주요 관찰 지표**
- 처리량(Throughput)
- 지연 시간(Latency: P95/P99 포함)
- 데이터 신선도(Freshness: End-to-End Lag)

> 전체 실행 절차(도커 실행 → 앱 실행 → k6 리포트 생성)는 아래 가이드에 정리되어 있습니다.
> - **Full Guide**: [`docs/performance-guide.md`](docs/performance-guide.md)

---


</br> 

## 🔬 Experiments: RDBMS vs Kafka

이 저장소는 **LBS(Location-Based Service)** 환경에서 핵심 과제인 **실시간성**과 **확장성**을 중심으로,  
두 가지 아키텍처를 **동일한 조건에서 공정하게 비교**하기 위한 성능 실험 프로젝트입니다.

- **Baseline**: RDBMS 기반 (PostgreSQL + PostGIS)
- **Event-Driven**: Kafka + Redis 기반 (비동기 처리 + 인메모리 공간 인덱스)

> 이 실험의 목적은 단순히 “A가 더 빠르다”를 말하는 것이 아니라, **어떤 상황에서 어떤 병목이 발생하는지**,
> 그리고 **조회 성능/데이터 신선도(Freshness)가 어떻게 달라지는지**를  **재현 가능하게 측정**하는 데 있습니다.

---

</br> 

### 1) 실험 배경

현대 LBS는 수만 명 사용자로부터 들어오는 실시간 위치 업데이트(쓰기)를 지연 없이 처리하면서,  
동시에 근접 차량 탐색(KNN), 특정 구역 내 사용자 필터링(PIP) 같은 공간 연산 쿼리(읽기)도 수행해야 합니다.

전통적인 **RDBMS(PostGIS)** 구조는 정합성과 공간 질의에 강점이 있지만,  
동시 쓰기가 폭증할 경우 인덱스 업데이트/락 경합/디스크 I/O 등으로 인해 **쓰기 병목**이 발생할 수 있습니다.

이에 본 연구는 **Kafka 기반 이벤트 처리 + Redis 공간 인덱스**를 결합한 구조를 구성하고,  
쓰기(업데이트)를 비동기화하여 **쓰기 성능과 확장성**을 확보하는 접근을 RDBMS와 비교합니다.

---


</br> 

### 2) 실험 목적

- **아키텍처별 성능 대조**: 처리량(Throughput) 및 지연 시간(Latency) 비교
- **데이터 신선도(Freshness) 측정**: 이벤트 발생 시점부터 최종 저장소 반영까지 End-to-End 지연 분석
- **공간 쿼리 효율성 검증**: 데이터 규모(10만 건 이상)에서 PIP/Range/KNN 조회의 안정성 평가

---


</br> 

### 3) 실험 환경

- **부하 테스트 도구**: `k6`
- **모니터링 스택**: `Prometheus` + `Grafana`
- **대상 인프라**
  - **RDBMS**: PostgreSQL 16 + PostGIS
  - **Message Broker**: Apache Kafka
  - **In-Memory DB**: Redis (GeoSpatial Index)
- **데이터셋**: Grab Posisi Open Dataset (약 100,000건의 실제 차량 위치 데이터)

---

</br> 

### 4) 실험 시나리오

| Scenario | Description | Metrics |
| --- | --- | --- |
| **Data Seeding** | 10만 건 초기 데이터 적재 | Insert throughput, index overhead |
| **Real-time Update** | 200 VU가 0.1s 간격으로 위치 업데이트 | Write latency, HTTP success rate |
| **Spatial Search** | 업데이트와 동시에 PIP/Range/KNN 조회 혼합 | Query latency (P95/P99), freshness lag |

---


</br> 

### 5) 핵심 지표 정의

- **Write Latency**: 위치 업데이트 요청 후 서버 응답(200 OK / 202 Accepted)까지의 시간
- **Data Freshness (Lag)**: 이벤트 발생 `timestamp` ↔ 최종 저장소(DB/Redis) 반영 시점의 차이
- **Search Response Time**: 공간 연산 쿼리가 처리되어 클라이언트에 도달하는 시간

---


</br> 

## 🛠 How to Run

> 아래는 “빠르게 실행해보는” 최소 절차입니다.  
> RDBMS/Kafka의 컨테이너 구성과 상세 실행 순서는 `docs/performance-guide.md`가 **정답 문서**입니다.
> - **Full Guide**: [`docs/performance-guide.md`](docs/performance-guide.md)

### ✅ 실행 전 안내
- 애플리케이션 실행은 보통 **별도 터미널**에서 수행하는 것이 편합니다.
- 두 아키텍처는 포트가 다릅니다.
  - RDBMS API: **8081**
  - Kafka API: **8082**
- 이전 컨테이너가 떠 있으면 충돌할 수 있으니, 필요 시 `docker-compose stop`으로 정리 후 진행하세요.

---

### 0) Build (처음 1회 또는 코드 변경 시)
```bash
./gradlew clean build -x test

---

## 🛠 How to Run (Quick Start)

> 전체 절차(도커 실행 → 앱 실행 → k6 리포트 생성)는 아래 문서에 정리되어 있습니다.  
> - **Full Guide**: [`docs/performance-guide.md`](docs/performance-guide.md)

### 0) Build
```bash
./gradlew clean build -x test
```

### 1) Infrastructure (Docker)

> 정확한 컨테이너 조합은 `docs/performance-guide.md`를 따르세요.
>
>
> 여기서는 “빠르게” 띄우는 예시만 제공합니다.
>

```
docker-compose up-d
```

---

### 2) Run APIs (각각 별도 터미널 권장)

### (1) RDBMS API (Port: 8081)

```
java-jar itrc-api-rdbms/build/libs/itrc-api-rdbms-0.0.1-SNAPSHOT.jar
```

### (2) Kafka API (Port: 8082)

```
java-jar itrc-api-kafka/build/libs/itrc-api-kafka-0.0.1-SNAPSHOT.jar
```

### (3) Redis Stream + H3 API (Port: 8083)

```
java-jar itrc-api-stream/build/libs/itrc-api-stream-0.0.1-SNAPSHOT.jar
```

---

### 3) k6 Load Test

> 스크립트는 “비교군별”로 분리되어 있습니다.
>
>
> (Seeding/Update/Query를 각각 독립 실행하여 병목 구간을 더 명확하게 관찰합니다.)
>

```
# RDBMS (8081)
k6 run performance-test/seeding01.js
k6 run performance-test/update01.js
k6 run performance-test/query01.js

# Kafka (8082)
k6 run performance-test/seeding02.js
k6 run performance-test/update02.js
k6 run performance-test/query02.js

# Redis Stream + H3 (8083)
k6 run performance-test/seeding03.js
k6 run performance-test/update03.js
k6 run performance-test/query03.js
```

---

</br>

##  Monitoring & Results

- **Grafana**: `http://localhost:3000` (ID: `admin` / PW: `password`)
- **k6 HTML reports**: `docs/results/report_*.html`

### 컨테이너 이름(기본 예시)

- **DB(PostGIS)**: `lbs-research-db`
- **Kafka**: `lbs-research-kafka`
- **Redis**: `lbs-research-redis`
- **Redis(Stream 전용, 권장)**: `lbs-research-redis-stream`
- **Prometheus**: `lbs-research-prometheus`
- **Grafana**: `lbs-research-grafana`

> 컨테이너 이름은 docker-compose.yml의 `container_name:` 설정에 따라 달라질 수 있습니다.


<img width="893" height="502" alt="image" src="https://github.com/user-attachments/assets/979d0064-8a9f-4555-921d-4d9f0d102a96" />
<img width="897" height="502" alt="image" src="https://github.com/user-attachments/assets/a079de28-2920-4300-b690-746fd8bdfe15" />
<img width="896" height="500" alt="image" src="https://github.com/user-attachments/assets/6bd77403-7c17-4593-a168-a19e683e670d" />
<img width="892" height="496" alt="image" src="https://github.com/user-attachments/assets/64ea99a7-8b76-40e6-9575-b85cc46df50f" />
<img width="898" height="499" alt="image" src="https://github.com/user-attachments/assets/6ed183b6-4cd7-4796-b31a-61aa6deb3332" />
<img width="892" height="499" alt="image" src="https://github.com/user-attachments/assets/bbbe76ec-020f-42f7-be8f-ceb265412776" />
<img width="894" height="503" alt="image" src="https://github.com/user-attachments/assets/846b1855-678c-4fb7-84fe-5d3ae6292e97" />
<img width="892" height="504" alt="image" src="https://github.com/user-attachments/assets/9e23bbd7-0c3c-41e7-ad76-54bdebf4a1c8" />
<img width="894" height="499" alt="image" src="https://github.com/user-attachments/assets/2d03652d-d3ff-400d-8725-a4d367464cac" />
<img width="894" height="501" alt="image" src="https://github.com/user-attachments/assets/4ec1ccd3-9cb6-499e-b5c5-1ac6b3ae1cd8" />
<img width="894" height="500" alt="image" src="https://github.com/user-attachments/assets/58340bea-560a-4c02-91c1-b49ea9e72c77" />
<img width="892" height="497" alt="image" src="https://github.com/user-attachments/assets/495106d2-aee6-4e7e-b511-b9373eb68cd3" />
<img width="894" height="501" alt="image" src="https://github.com/user-attachments/assets/5061bada-bcdf-4a0f-9ae7-18b5904f4b14" />
<img width="893" height="498" alt="image" src="https://github.com/user-attachments/assets/11fcc1fa-97ef-45e0-91dc-091d632e5668" />
<img width="894" height="501" alt="image" src="https://github.com/user-attachments/assets/4ff1c79e-7ff3-44a9-8935-8bbd714ef6e1" />









