# API 게이트웨이: team5-ApiGateway 연결

import json
import os
import pymysql

# RDS 연결 정보 (환경 변수에서 가져오기)
rds_host = os.environ['RDS_HOST']
db_username = os.environ['DB_USERNAME']
db_password = os.environ['DB_PASSWORD']
db_name = os.environ['DB_NAME']

def lambda_handler(event, context):
    connection = None  # connection 변수를 None으로 초기화
    try:
        # API Gateway로부터 받은 요청의 쿼리 문자열에서 전화번호를 가져옵니다.
        query = event.get('queryStringParameters', {})  # URL에서 ? 뒤에 있는 쿼리 파라미터들
        phone_number = query.get('call')  # ?call=01012345678

        if not phone_number:
            return {
                'statusCode': 400,
                'body': json.dumps('Phone number not provided in the request.', ensure_ascii=False)
            }

        # RDS에 연결
        connection = pymysql.connect(
            host=rds_host,
            user=db_username,
            password=db_password,
            database=db_name,
            connect_timeout=5
        )
        print("RDS 연결 성공")

# DB 접속 수정정
        # PhoneNumbers 테이블에서 해당 번호의 보이스피싱 전적을 확인
        with connection.cursor() as cursor:
            # AnalysisStats 테이블에서 total_call_count 값을 가져옵니다.
            query_total_call = "SELECT total_call_count FROM AnalysisStats WHERE id = 1"
            cursor.execute(query_total_call)
            result_total_call = cursor.fetchone()

            # PhoneNumbers 테이블에서 해당 번호의 보이스피싱 전적을 확인
            query_scam = "SELECT scam_count FROM PhoneNumbers WHERE phone_number = %s"
            cursor.execute(query_scam, (phone_number,))
            result_scam = cursor.fetchone()

# 응답 형식 수정
            # if result:
            #     scam_count = result[0]
            #     response_message = f"보이스피싱 전적이 {scam_count}회 있는 번호입니다."
            # else:
            #     response_message = "보이스피싱 전적이 없습니다."
            if result_scam is None or result_total_call is None:
                result_total_call = result_scam = [0]
            response_message = {"totalCall": result_total_call[0], "numPhishing": result_scam[0]}
            # response_message = json.dumps(result)
# 응답 형식 수정 끝끝
            
        return {
            'statusCode': 200,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps(response_message, ensure_ascii=False)
        }

    except pymysql.MySQLError as e:
        print(f"MySQL Error: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"MySQL Error: {str(e)}", ensure_ascii=False)
        }

    except Exception as e:
        print(f"Unhandled exception: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"Unhandled error: {str(e)}", ensure_ascii=False)
        }

    finally:
        if connection:
            connection.close()
            print("Connection closed")
