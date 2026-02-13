import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 테스트 설정 (총 3분)
export const options = {
    scenarios: {
        stress_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '30s', target: 200},  // 30초 동안 200명까지 증가
                {duration: '2m', target: 200},   // 2분 동안 200명 유지 (메인 실험)
                {duration: '30s', target: 0},    // 30초 동안 종료
            ],
            gracefulRampDown: '30s',
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    if (!record.trj_id || !record.rawlat || !record.rawlng) return;

    const virtualTrjId = `${record.trj_id}_v${__VU}`;
    const rand = Math.random();
    const params = {headers: {'Content-Type': 'application/json'}};
    const BASE_URL = 'http://localhost:8081/api/v1/rdbms';

    if (rand < 0.8) {
        // [Write: 80%] RDBMS 저장
        const payload = JSON.stringify({
            userId: virtualTrjId,
            serviceType: record.driving_mode === 'car' ? 'TAXI' : 'BIKE',
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            speed: parseFloat(record.speed) * 3.6,
            accuracy: parseFloat(record.accuracy),
            timestamp: Date.now() // 실시간 타임스탬프 적용
        });

        const res = http.post(`${BASE_URL}/update`, payload, params);
        check(res, {'RDBMS Write status is 200': (r) => r.status === 200});

    } else {
        // [Read: 20%] 공간 검색
        const lat = parseFloat(record.rawlat);
        const lng = parseFloat(record.rawlng);
        const searchType = Math.floor(Math.random() * 3);

        if (searchType === 0) {
            const res = http.get(`${BASE_URL}/search/range?lat=${lat}&lng=${lng}&radius=5000`);
            check(res, {'Range Query status is 200': (r) => r.status === 200});
        } else if (searchType === 1) {
            const res = http.get(`${BASE_URL}/search/knn?lat=${lat}&lng=${lng}&k=5`);
            check(res, {'KNN Query status is 200': (r) => r.status === 200});
        } else {
            const minLat = lat - 0.01;
            const maxLat = lat + 0.01;
            const minLng = lng - 0.01;
            const maxLng = lng + 0.01;
            const wkt = `POLYGON((${minLng} ${minLat}, ${maxLng} ${minLat}, ${maxLng} ${maxLat}, ${minLng} ${maxLat}, ${minLng} ${minLat}))`;
            const res = http.get(`${BASE_URL}/search/pip?wkt=${encodeURIComponent(wkt)}`);
            check(res, {'PIP Query status is 200': (r) => r.status === 200});
        }
    }

    sleep(1);
}