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
    const BASE_URL = 'http://localhost:8081/api/v1/rdbms/search'; // 8081 포트 확인

    const rand = Math.random();
    const lat = parseFloat(record.rawlat);
    const lng = parseFloat(record.rawlng);

    if (rand < 0.33) { // [PIP Search] 🌟 데이터 형식을 리스트로 수정
        const payload = JSON.stringify({
            lats: [lat, lat + 0.02, lat + 0.02, lat], // 사각형 꼭짓점
            lngs: [lng, lng, lng + 0.02, lng + 0.02]
        });
        const res = http.post(`${BASE_URL}/pip`, payload, params);
        check(res, {'RDBMS PIP OK': (r) => r.status === 200});

    } else if (rand < 0.66) { // [Range Search]
        const radius = (Math.random() * 4500 + 500).toFixed(0);
        const payload = JSON.stringify({
            latitude: lat,
            longitude: lng,
            serviceType: "RDBMS"
        });
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'Range OK': (r) => r.status === 200});

    } else { // [KNN Search]
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = JSON.stringify({
            latitude: lat,
            longitude: lng,
            serviceType: "RDBMS"
        });
        // ⚠️ 서버 파라미터가 n인지 k인지 확인하세요 (RDBMS는 보통 k)
        const res = http.post(`${BASE_URL}/knn?k=${k}`, payload, params);
        check(res, {'KNN OK': (r) => r.status === 200});
    }
    sleep(0.1);
}