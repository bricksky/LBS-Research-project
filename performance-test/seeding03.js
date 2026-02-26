import http from 'k6/http';
import {check} from 'k6';
import {SharedArray} from 'k6/data';
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
    const virtualUserId = `user_${__ITER}`;

    const payload = JSON.stringify({
        userId: virtualUserId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        timestamp: Date.now(),
        serviceType: "TAXI", // driving_mode 대신 사용
        heading: parseFloat(record.bearing || 0),
        speed: parseFloat(record.speed || 0),
        accuracy: parseFloat(record.accuracy || 1.0),
        status: "ON_TASK"
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    const res = http.post('http://localhost:8083/api/v1/redis/update', payload, params);

    check(res, {'Seeding Success': (r) => r.status === 200});
}