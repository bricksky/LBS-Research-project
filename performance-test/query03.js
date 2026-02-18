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
    const BASE_URL = 'http://localhost:8081/api/v1/search';

    const rand = Math.random();
    const lat = parseFloat(record.rawlat);
    const lng = parseFloat(record.rawlng);

    // 🌟 1. Record 구조(trj_id, rawlat 등)에 100% 맞춘 페이로드 생성 함수
    const createPayload = (trjId, lat, lng) => JSON.stringify({
        trj_id: trjId,
        driving_mode: "searching",
        osname: "k6-test",
        pingtimestamp: Date.now(),
        rawlat: lat,
        rawlng: lng,
        speed: 0.0,
        bearing: 0,
        accuracy: 0.0
    });

    if (rand < 0.33) {
        // [1. PIP Search]
        // 서버의 PipRequest가 별도로 위경도 리스트를 받는 구조라면 아래 유지
        // 만약 PIP도 LocationRequest를 받는다면 createPayload 사용
        const pipPayload = JSON.stringify({
            lats: [lat, lat + 0.01, lat + 0.01, lat],
            lngs: [lng, lng, lng + 0.01, lng + 0.01]
        });
        const res = http.post(`${BASE_URL}/pip`, pipPayload, params);
        check(res, {'PIP OK': (r) => r.status === 200});

    } else if (rand < 0.66) {
        // [2. Range Search]
        const radius = (Math.random() * 4 + 1).toFixed(1);
        const payload = createPayload("search_user", lat, lng);
        const res = http.post(`${BASE_URL}/range?radius=${radius}`, payload, params);
        check(res, {'Range OK': (r) => r.status === 200});

    } else {
        // [3. KNN Search]
        const k = Math.floor(Math.random() * 41) + 10;
        const payload = createPayload("search_user", lat, lng);
        // 컨트롤러의 @RequestParam 이름이 'k'인지 'n'인지 확인 후 맞춰주세요.
        const res = http.post(`${BASE_URL}/knn?k=${k}`, payload, params);
        check(res, {'KNN OK': (r) => r.status === 200});
    }

    sleep(0.1);
}