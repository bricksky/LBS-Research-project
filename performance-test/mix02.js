import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        mixed_complex_kafka: {
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
    const BASE_URL = 'http://localhost:8082/api/v1'; // Kafka API 서버

    const mainRand = Math.random();

    if (mainRand < 0.9) { // [Write 90%] - Kafka 비동기 입력
        const payload = JSON.stringify({
            userId: virtualTrjId,
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            speed: 0.0,
            accuracy: 0.0,
            serviceType: "KAFKA",
            timestamp: Date.now()
        });
        const res = http.post(`${BASE_URL}/update/kafka`, payload, params);
        check(res, {'Kafka Accepted': (r) => r.status === 202});

    } else { // [Read 10%] - Redis에서 읽기
        const queryRand = Math.random();
        const searchPayload = JSON.stringify({
            latitude: parseFloat(record.rawlat),
            longitude: parseFloat(record.rawlng),
            serviceType: "KAFKA"
        });

        if (queryRand < 0.33) {
            const res = http.post(`${BASE_URL}/search/redis/point`, JSON.stringify({ userId: virtualTrjId, serviceType: "KAFKA" }), params);
            check(res, {'Redis Point OK': (r) => r.status === 200});
        } else if (queryRand < 0.66) {
            const radius = (Math.random() * 4500 + 500).toFixed(0);
            const res = http.post(`${BASE_URL}/search/redis/range?radius=${radius}`, searchPayload, params);
            check(res, {'Redis Range OK': (r) => r.status === 200});
        } else {
            const k = Math.floor(Math.random() * 41) + 10;
            const res = http.post(`${BASE_URL}/search/redis/knn?n=${k}`, searchPayload, params);
            check(res, {'Redis KNN OK': (r) => r.status === 200});
        }
    }
    sleep(0.1);
}