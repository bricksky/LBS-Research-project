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
    const BASE_URL = 'http://localhost:8082/api/v1/search/redis';

    const rand = Math.random();

    // CSV에서 가져온 기준 좌표
    const lat = parseFloat(record.rawlat);
    const lng = parseFloat(record.rawlng);

    if (rand < 0.33) {
        // 🌟 [PIP Search 수정] 서버의 PipRequest 구조에 맞게 배열(Array) 전송
        const payload = JSON.stringify({
            lats: [lat, lat + 0.05, lat + 0.05, lat],
            lngs: [lng, lng, lng + 0.05, lng + 0.05]
        });
        const res = http.post(`${BASE_URL}/pip`, payload, params);
        // 체크명 변경
        check(res, {'Redis PIP OK': (r) => r.status === 200});

    } else if (rand < 0.66) {
        // [Range Search]
        const radius = (Math.random() * 4500 + 500).toFixed(0);
        const payload = JSON.stringify({
            latitude: lat,
            longitude: lng,
            serviceType: "KAFKA"
        });
        const res = http.post(`${BASE_URL}/range?radiusMeter=${radius}`, payload, params); // 파라미터명 radiusMeter로 주의
        check(res, {'Redis Range OK': (r) => r.status === 200});

    } else {
        // [KNN Search]
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = JSON.stringify({
            latitude: lat,
            longitude: lng,
            serviceType: "KAFKA"
        });
        const res = http.post(`${BASE_URL}/knn?n=${k}`, payload, params);
        check(res, {'Redis KNN OK': (r) => r.status === 200});
    }
    sleep(0.1);
}