import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 시나리오 설정 (200 VU 유지)
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

    // [핵심 차이] 0번부터 99,999번 사이의 유저를 무작위로 선택
    // 적재 때 쓴 __ITER 대신 랜덤 함수를 사용합니다.
    const userIdx = Math.floor(Math.random() * 100000);
    const virtualTrjId = `user_${userIdx}`;

    const payload = JSON.stringify({
        trj_id: virtualTrjId,
        rawlat: parseFloat(record.rawlat),
        rawlng: parseFloat(record.rawlng),
        pingtimestamp: Date.now(),
        driving_mode: "stress-test", // 모드 이름만 바꿔서 로그 구분
        osname: "android",
        speed: 50.0, // 실제 주행 중인 것처럼 값 조정 가능
        bearing: Math.floor(Math.random() * 360),
        accuracy: 1.0
    });

    const params = {headers: {'Content-Type': 'application/json'}};
    const res = http.post('http://localhost:8080/api/v1/redis/update', payload, params);

    check(res, {'Update Success': (r) => r.status === 200});
    sleep(0.01); // 스트레스 테스트니까 간격을 더 좁혀서 쏴보세요!
}