import requests
import time
import random
import json

# ✅ RDBMS 서버 주소 (8081 포트)
API_URL = "http://localhost:8081/api/v1/rdbms/update"

# 싱가포르 중심 좌표
CENTER_LAT, CENTER_LNG = 1.3521, 103.8198

def generate_movement(lat, lng):
    # 랜덤 이동 (0.001도 ≈ 100m)
    lat += random.uniform(-0.001, 0.001)
    lng += random.uniform(-0.001, 0.001)
    return lat, lng

def run():
    # 가상의 차량 5대 생성
    cars = [{"id": f"rdbms_car_{i}", "lat": CENTER_LAT, "lng": CENTER_LNG} for i in range(1, 6)]

    print(f"🚀 RDBMS 데이터 전송 시작... (Target: {API_URL})")

    while True:
        for car in cars:
            # 1. 위치 이동
            car["lat"], car["lng"] = generate_movement(car["lat"], car["lng"])

            # 2. 데이터 생성
            data = {
                "userId": car["id"],
                "serviceType": "TAXI",
                "latitude": car["lat"],
                "longitude": car["lng"],
                "speed": random.uniform(10, 60),
                "accuracy": 5.0,
                "timestamp": int(time.time() * 1000)
            }

            # 3. API 전송 (Spring Boot -> H2 DB 저장)
            try:
                headers = {'Content-Type': 'application/json'}
                res = requests.post(API_URL, data=json.dumps(data), headers=headers)
                if res.status_code == 200:
                    print(f"✅ 저장 완료: {car['id']}")
                else:
                    print(f"❌ 실패 ({res.status_code}): {res.text}")
            except Exception as e:
                print(f"⚠️ 연결 에러: {e}")

        time.sleep(1) # 1초마다 전송

if __name__ == "__main__":
    run()