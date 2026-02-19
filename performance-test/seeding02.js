import http from 'k6/http';
import {check} from 'k6';
import {SharedArray} from 'k6/data';
import exec from 'k6/execution';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        seeding_100k: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100000,
            maxDuration: '5m',
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];

    // 💥 고유 ID 생성
    const globalIter = exec.scenario.iterationInTest;
    const virtualTrjId = `user_${globalIter}`;

    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        speed: 0.0,
        accuracy: 0.0,
        serviceType: "KAFKA",   // 💥 필수: Validation 에러 해결
        timestamp: Date.now()   // 💥 필수: Long 타입 유지
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    const res = http.post('http://localhost:8082/api/v1/update/kafka', payload, params);

    check(res, {'Kafka Seeded': (r) => r.status === 202});
}