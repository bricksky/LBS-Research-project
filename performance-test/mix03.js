import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        mixed_complex_workload: {
            executor: 'ramping-vus',
            stages: [
                {duration: '30s', target: 200}, // 200명까지 서서히 증가
                {duration: '2m', target: 200},  // 200명 유지
                {duration: '30s', target: 0},   // 종료
            ],
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    const userIdx = Math.floor(Math.random() * 100000); // Seeding된 10만 건 범위 내
    const virtualTrjId = `user_${userIdx}`;
    const params = {headers: {'Content-Type': 'application/json'}};

    // 스프링 DTO(LocationRequest) 규격에 맞춘 공통 페이로드 생성
    const createPayload = (id, lat, lng) => JSON.stringify({
        trj_id: id,
        rawlat: parseFloat(lat),
        rawlng: parseFloat(lng),
        pingtimestamp: Date.now(),
        driving_mode: "mixed-test",
        osname: "android",
        speed: 0.0,
        bearing: 0,
        accuracy: 0.0
    });

    const mainRand = Math.random();

    if (mainRand < 0.9) { // [1. Write 90%] 위치 업데이트 수행
        const payload = createPayload(virtualTrjId, record.rawlat, record.rawlng);
        const res = http.post('http://localhost:8080/api/v1/redis/update', payload, params);
        check(res, {'Write OK': (r) => r.status === 200});

    } else { // [2. Read 10%] 3가지 검색 쿼리 수행
        const queryRand = Math.random();
        const SEARCH_BASE_URL = 'http://localhost:8081/api/v1/search'; // RediSearch API 기준

        if (queryRand < 0.33) { // Point Query
            const payload = createPayload(virtualTrjId, 0.0, 0.0);
            const res = http.post(`${SEARCH_BASE_URL}/point`, payload, params);
            check(res, {'Point OK': (r) => r.status === 200});

        } else if (queryRand < 0.66) { // Range Search
            const radius = (Math.random() * 9 + 1).toFixed(1); // 1km ~ 10km 랜덤 반경
            const payload = createPayload(null, record.rawlat, record.rawlng);
            const res = http.post(`${SEARCH_BASE_URL}/range?radius=${radius}`, payload, params);
            check(res, {'Range OK': (r) => r.status === 200});

        } else { // KNN Search
            const k = Math.floor(Math.random() * 41) + 10;
            const payload = createPayload(null, record.rawlat, record.rawlng);
            const res = http.post(`${SEARCH_BASE_URL}/knn?n=${k}`, payload, params);
            check(res, {'KNN OK': (r) => r.status === 200});
        }
    }

    sleep(0.1); // 부하 간격 조절
}