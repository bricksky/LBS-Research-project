import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 테스트 설정 (3분 시나리오)
export const options = {
    scenarios: {
        lbs_integrated_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '30s', target: 200},  // 30초 동안 200명까지 증가
                {duration: '2m', target: 200},   // 2분 동안 200명 유지
                {duration: '30s', target: 0},    // 30초 동안 종료
            ],
            gracefulRampDown: '30s',
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    if (!record.trj_id || !record.rawlat || !record.rawlng) return;

    const params = {headers: {'Content-Type': 'application/json'}};
    const PRODUCER_URL = 'http://localhost:8080/api/v1/locations';
    const CONSUMER_URL = 'http://localhost:8081/api/v1/search';

    const rand = Math.random();

    // 공통 데이터 객체 생성 (LocationRequest DTO 대응)
    const locationPayload = {
        userId: `${record.trj_id}_v${__VU}`,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        timestamp: new Date().toISOString()
    };

    if (rand < 0.8) {
        // [Write: 80%] Producer - Redis Streams 전송
        const res = http.post(PRODUCER_URL, JSON.stringify(locationPayload), params);
        check(res, {'Producer Write status is 200': (r) => r.status === 200});

    } else {
        // [Read: 20%] Consumer - Redis Search 검색
        const searchType = Math.floor(Math.random() * 3);
        const body = JSON.stringify(locationPayload);

        if (searchType === 0) {
            // 1. Range 검색 (radius=5.0km)
            const res = http.post(`${CONSUMER_URL}/range?radius=5.0`, body, params);
            check(res, {'Redis Range status is 200': (r) => r.status === 200});
        } else if (searchType === 1) {
            // 2. KNN 검색 (k=5)
            const res = http.post(`${CONSUMER_URL}/knn?k=5`, body, params);
            check(res, {'Redis KNN status is 200': (r) => r.status === 200});
        } else {
            // 3. H3 기반 PIP 검색
            const res = http.post(`${CONSUMER_URL}/pip`, body, params);
            check(res, {'Redis PIP status is 200': (r) => r.status === 200});
        }
    }

    sleep(1);
}