import json
import os
import pandas as pd
import requests
import time

# ==========================================
# 환경 설정 (Configuration)
# ==========================================
# Target API 엔드포인트 (Spring Boot Controller)
API_URL = "http://localhost:8080/api/v1/update/kafka"

# 데이터 파일 경로 (스크립트와 동일 위치 권장)
DATA_FILE = "grab_posisi_data.csv"

# 전송 딜레이 설정 (0.05s = 약 20 TPS)
DELAY_SECONDS = 0.05


def send_location(row):
    try:
        # Java DTO (LocationRequest) 규격에 맞춰 JSON 매핑
        payload = {
            "userId": str(row['trj_id']).strip().replace('"', '').replace("'", ""),
            "serviceType": str(row['driving_mode']),  # car / motorcycle
            "latitude": float(row['rawlat']),
            "longitude": float(row['rawlng']),
            "heading": float(row['bearing']),
            "speed": float(row['speed']),  # 단위: m/s
            "accuracy": float(row['accuracy']),
            # [중요] Grab 데이터는 초(s) 단위이므로, Java(ms) 기준에 맞춰 * 1000
            "timestamp": int(row['pingtimestamp']) * 1000,
            "status": "ON_TASK"
        }

        headers = {'Content-Type': 'application/json'}
        response = requests.post(API_URL, data=json.dumps(payload), headers=headers)

        # Kafka 비동기 처리 응답(202 Accepted) 확인
        if response.status_code == 202:
            print(".", end="", flush=True)  # 진행 상황 시각화
        else:
            print(f"\n❌ 전송 실패: {response.status_code} - {response.text}")

    except Exception as e:
        print(f"\n⚠️ 연결 에러: {e}")


def main():
    if not os.path.exists(DATA_FILE):
        print(f"❌ '{DATA_FILE}' 파일이 없습니다.")
        return

    print(f"📂 데이터 로드 중: {DATA_FILE}")
    df = pd.read_csv(DATA_FILE)
    print(f"🚀 전송 시작 | 총 {len(df)}건 | Target: {API_URL}")

    count = 0
    start_time = time.time()

    for index, row in df.iterrows():
        send_location(row)
        count += 1

        # 50건 단위로 처리율(TPS) 로그 출력
        if count % 50 == 0:
            elapsed = time.time() - start_time
            print(f" [ {count}건 전송 | {count / elapsed:.1f} req/sec ]")

        time.sleep(DELAY_SECONDS)

    print(f"\n🏁 전송 완료 ({count}건)")


if __name__ == "__main__":
    main()
