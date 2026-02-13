import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution'; // 고유 ID 생성을 위해 추가
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        seeding_100k: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 100000,
            maxDuration: '10m',
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];

    // 💥 핵심: 50명의 VU가 작업해도 0~99,999까지 겹치지 않는 고유 번호 추출
    const globalIter = exec.scenario.iterationInTest;
    const virtualTrjId = `user_${globalIter}`;

    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        speed: 0.0,             // 매개변수 추가
        accuracy: 0.0,          // 매개변수 추가
        serviceType: "RDBMS",   // 💥 필수: Validation 에러 해결
        timestamp: Date.now()   // 💥 필수: Long 타입 (숫자) 유지
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    const res = http.post('http://localhost:8081/api/v1/rdbms/update', payload, params);

    check(res, {'RDBMS Seeded': (r) => r.status === 200});
}