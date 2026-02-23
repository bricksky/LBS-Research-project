import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
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
    // Redis 모듈의 통합 경로 사용
    const BASE_URL = 'http://localhost:8083/api/v1/redis/search';

    const rand = Math.random();
    const lat = parseFloat(record.rawlat);
    const lng = parseFloat(record.rawlng);

    const payload = JSON.stringify({
        userId: "search_user",
        latitude: lat,
        longitude: lng
    });

    if (rand < 0.5) {
        // [1. Range Search]
        const radius = (Math.random() * 4 + 1).toFixed(1);
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'Range OK': (r) => r.status === 200});
    } else {
        // [2. KNN Search]
        const k = Math.floor(Math.random() * 41) + 10;
        const res = http.post(`${BASE_URL}/knn?k=${k}`, payload, params);
        check(res, {'KNN OK': (r) => r.status === 200});
    }

    sleep(0.1);
}