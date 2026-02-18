import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        mixed_complex_rdbms: {
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
    const userIdx = Math.floor(Math.random() * 100000);
    const virtualTrjId = `user_${userIdx}`;
    const params = {headers: {'Content-Type': 'application/json'}};
    const BASE_URL = 'http://localhost:8081/api/v1/rdbms';

    const mainRand = Math.random();

    if (mainRand < 0.9) { // [Write 90%] - Update 실험
        const payload = JSON.stringify({
            userId: virtualTrjId,
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            speed: 0.0,            // 추가
            accuracy: 0.0,         // 추가
            serviceType: "RDBMS",  // 필수 필드
            timestamp: Date.now()  // Long 타입 숫자
        });
        const res = http.post(`${BASE_URL}/update`, payload, params);
        check(res, {'Write OK': (r) => r.status === 200});

    } else { // [Read 10%] - 3가지 쿼리 혼합
        const queryRand = Math.random();

        // 검색용 페이로드 (검색 DTO에도 serviceType이 필요할 수 있어 추가함)
        const searchPayload = JSON.stringify({
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            serviceType: "RDBMS"   // 검색 필터링용
        });

        if (queryRand < 0.33) { // Point Search
            const res = http.post(`${BASE_URL}/search/point`, JSON.stringify({ userId: virtualTrjId, serviceType: "RDBMS" }), params);
            check(res, {'Point OK': (r) => r.status === 200});
        } else if (queryRand < 0.66) { // Range Search
            const radius = (Math.random() * 4500 + 500).toFixed(0);
            const res = http.post(`${BASE_URL}/search/range?radius=${radius}`, searchPayload, params);
            check(res, {'Range OK': (r) => r.status === 200});
        } else { // KNN Search
            const k = Math.floor(Math.random() * 41) + 10;
            const res = http.post(`${BASE_URL}/search/knn?n=${k}`, searchPayload, params);
            check(res, {'KNN OK': (r) => r.status === 200});
        }
    }
    sleep(0.1);
}