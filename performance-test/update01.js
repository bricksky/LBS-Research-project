import http from 'k6/http';
import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
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
                {duration: '30s', target: 200}, // 30초 동안 200명까지 증가
                {duration: '2m', target: 200},  // 2분 동안 200명 유지
                {duration: '30s', target: 0},   // 30초 동안 종료
            ],
        },
    },
};

export default function () {
    // 랜덤 데이터 선택
    const record = data[Math.floor(Math.random() * data.length)];
    const userIdx = Math.floor(Math.random() * 100000);
    const virtualTrjId = `user_${userIdx}`;

    // 3. 수정된 페이로드 (DTO 필드 매칭)
    const payload = JSON.stringify({
        userId: virtualTrjId,
        latitude: parseFloat(record.rawlat),
        longitude: parseFloat(record.rawlng),
        speed: 0.0,                   // 추가: DTO 기본값
        accuracy: 0.0,                // 추가: DTO 기본값
        serviceType: "RDBMS",         // 💥 핵심: 아까 에러 났던 필수 필드 추가
        timestamp: Date.now() // ISO 8601 형식으로 변경
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 4. API 호출
    const res = http.post('http://localhost:8081/api/v1/rdbms/update', payload, params);

    // 5. 결과 검증
    check(res, {
        'RDBMS Update Status 200': (r) => r.status === 200,
    });

    // 0.1초 대기 (초당 약 10회 요청 조절)
    sleep(0.1);
}