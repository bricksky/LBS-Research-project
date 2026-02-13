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
    const BASE_URL = 'http://localhost:8081/api/v1/rdbms/search';

    const rand = Math.random();

    if (rand < 0.33) { // [Point Query] 특정 기사 현재 위치 조회
        const userIdx = Math.floor(Math.random() * 100000);
        const payload = JSON.stringify({
            userId: `user_${userIdx}`,
            serviceType: "RDBMS" // 💥 필수 추가
        });
        const res = http.post(`${BASE_URL}/point`, payload, params);
        check(res, {'Point OK': (r) => r.status === 200});

    } else if (rand < 0.66) { // [Range Search] 주변 0.5~5km 검색
        const radius = (Math.random() * 4500 + 500).toFixed(0);
        const payload = JSON.stringify({
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            serviceType: "RDBMS" // 💥 필수 추가
        });
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'Range OK': (r) => r.status === 200});

    } else { // [KNN Search] 가장 가까운 10~50명 검색
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = JSON.stringify({
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            serviceType: "RDBMS" // 💥 필수 추가
        });
        const res = http.post(`${BASE_URL}/knn?n=${k}`, payload, params);
        check(res, {'KNN OK': (r) => r.status === 200});
    }
    sleep(0.1);
}