import pymysql
import os
import json
from datetime import datetime

# RDS 연결 정보 (환경 변수에서 가져오기)
rds_host = os.environ['RDS_HOST']
db_username = os.environ['DB_USERNAME']
db_password = os.environ['DB_PASSWORD']
db_name = os.environ['DB_NAME']

def datetime_handler(obj):
    """JSON 직렬화 시 datetime 객체를 문자열로 변환"""
    if isinstance(obj, datetime):
        return obj.strftime('%Y-%m-%d %H:%M:%S')
    raise TypeError("Type not serializable")

def lambda_handler(event, context):
    connection = None
    try:
        # RDS에 연결
        connection = pymysql.connect(
            host=rds_host,
            user=db_username,
            password=db_password,
            database=db_name,
            connect_timeout=5
        )

        with connection.cursor(pymysql.cursors.DictCursor) as cursor:
            # PhoneNumbers 테이블의 데이터 조회
            cursor.execute("SELECT * FROM PhoneNumbers;")
            phone_numbers_data = cursor.fetchall()

            # AnalysisStats 테이블의 데이터 조회
            cursor.execute("SELECT * FROM AnalysisStats;")
            analysis_stats_data = cursor.fetchall()

            # 결과를 JSON으로 직렬화
            result = {
                'PhoneNumbers': phone_numbers_data,
                'AnalysisStats': analysis_stats_data
            }

            return {
                'statusCode': 200,
                'body': json.dumps(result, ensure_ascii=False, default=datetime_handler)  # datetime 객체를 문자열로 변환
            }

    except pymysql.MySQLError as e:
        print(f"MySQL Error: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"MySQL Error: {str(e)}")
        }
    except Exception as e:
        print(f"Unhandled exception: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"Unhandled error: {str(e)}")
        }
    finally:
        if connection:
            connection.close()
            print("Connection closed")
