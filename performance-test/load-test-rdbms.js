import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드 (메모리 효율화)
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 테스트 설정 (가상 유저 10명, 30초 동안 실행)
export const options = {
    vus: 10,
    duration: '30s',
};

// 3. 메인 로직
export default function () {
    // 랜덤하게 레코드 하나 선택
    const record = data[Math.floor(Math.random() * data.length)];

    // 데이터가 비어있거나 좌표가 없으면 스킵
    if (!record.trj_id || !record.rawlat || !record.rawlng) return;

    // 가상 ID 생성 (동시성 테스트를 위해 ID 분산)
    const virtualTrjId = `${record.trj_id}_v${__VU}`; // VU(Virtual User) 번호 붙임

    const rand = Math.random();
    const params = {headers: {'Content-Type': 'application/json'}};
    const BASE_URL = 'http://localhost:8081/api/v1/rdbms';

    if (rand < 0.8) {
        // ==========================================
        // [Write: 80%] 데이터 저장 (POST /update)
        // ==========================================
        const payload = JSON.stringify({
            userId: virtualTrjId,
            serviceType: record.driving_mode === 'car' ? 'TAXI' : 'BIKE', // 매핑 필요
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            speed: parseFloat(record.speed) * 3.6, // m/s -> km/h 변환
            accuracy: parseFloat(record.accuracy),
            timestamp: parseInt(record.pingtimestamp) * 1000 // 초 -> 밀리초
        });

        const res = http.post(`${BASE_URL}/update`, payload, params);
        check(res, {'Write status is 200': (r) => r.status === 200});

    } else {
        // ==========================================
        // [Read: 20%] 공간 검색 (GET Range/KNN/PIP)
        // ==========================================
        const lat = parseFloat(record.rawlat);
        const lng = parseFloat(record.rawlng);

        // 검색 타입 랜덤 선택 (0: Range, 1: KNN, 2: PIP)
        const searchType = Math.floor(Math.random() * 3);

        if (searchType === 0) {
            // 1) 반경 검색 (Range)
            const res = http.get(`${BASE_URL}/search/range?lat=${lat}&lng=${lng}&radius=5000`);
            check(res, {'Range Query status is 200': (r) => r.status === 200});

        } else if (searchType === 1) {
            // 2) 최근접 이웃 (KNN)
            const res = http.get(`${BASE_URL}/search/knn?lat=${lat}&lng=${lng}&k=5`);
            check(res, {'KNN Query status is 200': (r) => r.status === 200});

        } else {
            // 3) 구역 검색 (PIP) - 현재 위치 주변에 작은 사각형을 만듦
            const minLat = lat - 0.01;
            const maxLat = lat + 0.01;
            const minLng = lng - 0.01;
            const maxLng = lng + 0.01;
            // WKT 형식: POLYGON((x1 y1, x2 y2, ..., x1 y1))
            const wkt = `POLYGON((${minLng} ${minLat}, ${maxLng} ${minLat}, ${maxLng} ${maxLat}, ${minLng} ${maxLat}, ${minLng} ${minLat}))`;

            // URL 인코딩 처리
            const res = http.get(`${BASE_URL}/search/pip?wkt=${encodeURIComponent(wkt)}`);
            check(res, {'PIP Query status is 200': (r) => r.status === 200});
        }
    }

    sleep(0.1); // 너무 빠르지 않게 0.1초 대기
}