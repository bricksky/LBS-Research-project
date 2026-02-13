import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. CSV 데이터 로드
const data = new SharedArray('posisi_data', function () {
    return papaparse.parse(open('./grab_posisi_data.csv'), {header: true}).data;
});

// 2. 부하 시나리오 설정 (Ramping VUs)
export const options = {
    scenarios: {
        update_stress: {
            executor: 'ramping-vus',
            stages: [
                {duration: '30s', target: 200}, // 200명까지 증가
                {duration: '2m', target: 200},  // 유지
                {duration: '30s', target: 0},   // 종료
            ],
        },
    },
};

export default function () {
    const record = data[Math.floor(Math.random() * data.length)];
    const userIdx = Math.floor(Math.random() * 100000);
    const virtualTrjId = `user_${userIdx}`;

    // 3. 수정된 페이로드 (Kafka 전용 serviceType 설정)
    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        speed: 0.0,
        accuracy: 0.0,
        serviceType: "KAFKA",         // 💥 핵심: "KAFKA"로 지정하여 유효성 통과 및 구분
        timestamp: Date.now()
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 4. API 호출 (Kafka 전용 엔드포인트)
    const res = http.post('http://localhost:8082/api/v1/update/kafka', payload, params);

    // 5. 결과 검증 (Kafka는 비동기 처리를 위해 202 Accepted를 반환함)
    check(res, {
        'Kafka Accepted (202)': (r) => r.status === 202,
    });

    sleep(0.1);
}