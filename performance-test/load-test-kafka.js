import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), { header: true }).data;
});

// 2. 부하 테스트 설정 (가상 유저 10명, 30초 실행)
export const options = {
    vus: 10,
    duration: '30s',
};

export default function () {
    // CSV에서 랜덤 레코드 추출
    const record = data[Math.floor(Math.random() * data.length)];

    // 데이터 유효성 검사 (빈 값 스킵)
    if (!record.trj_id || !record.rawlat || !record.rawlng) return;

    // 가상 ID 생성 (동시성 테스트용)
    const virtualTrjId = `${record.trj_id}_v${__VU}`;
    const rand = Math.random();

    // ⚠️ Kafka 모듈 포트는 8082로 설정 (application.yml과 일치시켜야 함)
    const BASE_URL = 'http://localhost:8082/api/v1';
    const params = { headers: { 'Content-Type': 'application/json' } };

    if (rand < 0.8) {
        // ==========================================
        // [Write: 80%] Kafka 비동기 전송 (POST /update/kafka)
        // ==========================================
        const payload = JSON.stringify({
            userId: virtualTrjId,
            // Grab 데이터(car/motorcycle)를 우리 ENUM(TAXI/BIKE)으로 변환
            serviceType: record.driving_mode === 'car' ? 'TAXI' : 'BIKE',
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            // m/s -> km/h 변환
            speed: parseFloat(record.speed) * 3.6,
            accuracy: parseFloat(record.accuracy),
            // Unix Timestamp(초) -> 밀리초 변환
            timestamp: parseInt(record.pingtimestamp) * 1000
        });

        const res = http.post(`${BASE_URL}/update/kafka`, payload, params);

        // Controller에서 ResponseEntity.accepted()를 반환하므로 202 확인
        check(res, { 'Kafka Write status is 202': (r) => r.status === 202 });

    } else {
        // ==========================================
        // [Read: 20%] Redis 메모리 검색 (GET /search/redis/...)
        // ==========================================
        const lat = parseFloat(record.rawlat);
        const lng = parseFloat(record.rawlng);

        // 검색 타입 랜덤 (0:Range, 1:KNN, 2:PIP)
        const searchType = Math.floor(Math.random() * 3);

        if (searchType === 0) {
            // 1) 반경 검색 (Range) - 파라미터명: radiusMeter
            const res = http.get(`${BASE_URL}/search/redis/range?lat=${lat}&lng=${lng}&radiusMeter=5000`);
            check(res, { 'Redis Range status is 200': (r) => r.status === 200 });

        } else if (searchType === 1) {
            // 2) 최근접 이웃 (KNN) - 파라미터명: n
            const res = http.get(`${BASE_URL}/search/redis/knn?lat=${lat}&lng=${lng}&n=5`);
            check(res, { 'Redis KNN status is 200': (r) => r.status === 200 });

        } else {
            // 3) 구역 검색 (PIP) - Redis는 좌표 리스트를 받음
            // 현재 위치 기준으로 가상의 사각형 구역 생성
            const minLat = lat - 0.01;
            const maxLat = lat + 0.01;
            const minLng = lng - 0.01;
            const maxLng = lng + 0.01;

            // URL 쿼리 스트링 생성 (List<Double> 형태)
            const queryParams = `lats=${minLat}&lats=${maxLat}&lats=${maxLat}&lats=${minLat}&lats=${minLat}` +
                `&lngs=${minLng}&lngs=${minLng}&lngs=${maxLng}&lngs=${maxLng}&lngs=${minLng}`;

            const res = http.get(`${BASE_URL}/search/redis/pip?${queryParams}`);
            check(res, { 'Redis PIP status is 200': (r) => r.status === 200 });
        }
    }

    // 너무 빠른 부하 방지를 위해 0.1초 대기 (필요시 조절)
    sleep(0.1);
}