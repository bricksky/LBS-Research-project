import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
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
    const virtualTrjId = `user_${__ITER}`;

    // [수정 핵심] JSON 키 이름을 스프링 DTO(LocationRequest) 필드명과 100% 일치시킴
    const payload = JSON.stringify({
        trj_id: virtualTrjId,         // userId -> trj_id
        rawlat: parseFloat(record.rawlat), // latitude -> rawlat
        rawlng: parseFloat(record.rawlng), // longitude -> rawlng
        pingtimestamp: Date.now(),    // timestamp -> pingtimestamp
        driving_mode: "driving",      // 나머지 필드들도 기본값 채워주기
        osname: "android",
        speed: 0.0,
        bearing: 0,
        accuracy: 0.0
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    // 엔드포인트는 기존대로 유지
    const res = http.post('http://localhost:8080/api/v1/redis/update', payload, params);

    check(res, {'Streams Seeded': (r) => r.status === 200});
}