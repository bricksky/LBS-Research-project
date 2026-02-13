import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        read_heavy_mix: {
            executor: 'ramping-vus',
            stages: [
                {duration: '30s', target: 200},
                {duration: '2m', target: 200},
                {duration: '30s', target: 0},
            ],
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    const params = {headers: {'Content-Type': 'application/json'}};
    const BASE_URL = 'http://localhost:8081/api/v1/search'; // RediSearch 기준

    const rand = Math.random();

    // 공통으로 사용할 기본 객체 생성 함수 (NPE 방지용)
    const createBasePayload = (trj_id, lat, lng) => JSON.stringify({
        trj_id: trj_id,
        rawlat: parseFloat(lat),
        rawlng: parseFloat(lng),
        pingtimestamp: Date.now(),
        driving_mode: "searching",
        osname: "test-client",
        speed: 0.0,
        bearing: 0,
        accuracy: 0.0
    });

    if (rand < 0.33) { // [1. Point Query] 특정 기사 현재 위치 조회
        const userIdx = Math.floor(Math.random() * 100000);
        const payload = createBasePayload(`user_${userIdx}`, 0.0, 0.0);
        const res = http.post(`${BASE_URL}/point`, payload, params);
        check(res, {'Point OK': (r) => r.status === 200});

    } else if (rand < 0.66) { // [2. Range Search] 주변 범위 검색
        const radius = (Math.random() * 4 + 1).toFixed(1);
        const payload = createBasePayload(null, record.rawlat, record.rawlng);
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'Range OK': (r) => r.status === 200});

    } else { // [3. KNN Search] 가장 가까운 기사 N명 검색
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = createBasePayload(null, record.rawlat, record.rawlng);
        const res = http.post(`${BASE_URL}/knn?n=${k}`, payload, params);
        check(res, {'KNN OK': (r) => r.status === 200});
    }

    sleep(0.1);
}