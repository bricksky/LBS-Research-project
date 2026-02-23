import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

export const options = {
    scenarios: {
        update_stress: {
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
    const virtualUserId = `user_${userIdx}`;

    const payload = JSON.stringify({
        userId: virtualUserId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        timestamp: Date.now(),
        serviceType: "BIKE",
        heading: Math.floor(Math.random() * 360),
        speed: 60.0,
        accuracy: 1.0,
        status: "STRESS_TEST"
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    // 포트 8083으로 변경
    const res = http.post('http://localhost:8083/api/v1/redis/update', payload, params);

    check(res, {'Update Success': (r) => r.status === 200});
    sleep(0.01);
}