# S3: team5-transcribed.text.storage 연결

import boto3
import json
import urllib.parse
import os
import pymysql
from botocore.exceptions import ClientError

# S3와 Bedrock 클라이언트 설정
s3_client = boto3.client('s3')
bedrock_runtime = boto3.client('bedrock-runtime', region_name='us-east-1')

INPUT_S3 = "team5-transcribed.text.storage"
OUTPUT_S3 = "team5-analyzed.text.storage"

# RDS 연결 정보 (환경 변수에서 가져오기)
rds_host = os.environ['RDS_HOST']
db_username = os.environ['DB_USERNAME']
db_password = os.environ['DB_PASSWORD']
db_name = os.environ['DB_NAME']

PROMPT = """Please analyze the following text for potential phishing (보이스피싱) risk in Korean.
Provide the response in the following JSON format:
{
    "percent": the percentage of potential voice phishing (number only),
    "reasons": [the first reason, "the second reason", ...]
}

The analysis should include only the JSON object and nothing else.

Text to analyze:
"""

def lambda_handler(event, context):
    connection = None
    try:
        # 입력 버킷, 출력 버킷과 오브젝트 키 설정
        input_bucket_name = INPUT_S3
        output_bucket_name = OUTPUT_S3
        object_key = urllib.parse.unquote_plus(event['Records'][0]['s3']['object']['key'])

        # 파일명에서 전화번호 추출
        phone_number = object_key.split('-')[0]

        # JSON 파일 읽기
        response = s3_client.get_object(Bucket=input_bucket_name, Key=object_key)
        json_content = json.loads(response['Body'].read().decode('utf-8'))

        # "transcript" 부분 추출
        transcript_text = json_content['transcript']

        # Claude 3.5에 텍스트 전달 및 위험도 분석 요청
        model_id = "anthropic.claude-3-5-sonnet-20240620-v1:0"
        payload = {
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 1000,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": f'{PROMPT}\n"{transcript_text}"'
                        }
                    ]
                }
            ]
        }

        # Bedrock 모델 호출
        response = bedrock_runtime.invoke_model(
            modelId=model_id,
            contentType="application/json",
            accept="application/json",
            body=json.dumps(payload)
        )

        # 결과 처리
        result = json.loads(response['body'].read().decode())
        analysis_result = json.loads(result['content'][0]['text'])
        analysis_result_str = json.dumps(analysis_result, ensure_ascii=False)

        # 결과를 새로운 파일로 S3에 저장
        output_key = object_key.replace('-transcription.json', '-analysis.json')
        s3_client.put_object(Bucket=output_bucket_name, Key=output_key, Body=analysis_result_str)

        # RDS에 연결
        connection = pymysql.connect(
            host=rds_host,
            user=db_username,
            password=db_password,
            database=db_name,
            connect_timeout=5
        )
        print("RDS 연결 성공")

        with connection.cursor() as cursor:
            # 분석 결과를 RDS에 저장
            insert_analysis_query = """
            INSERT INTO PhishingAnalysis (transcript_key, percent, reasons)
            VALUES (%s, %s, %s)
            """
            cursor.execute(insert_analysis_query, (object_key, analysis_result['percent'], json.dumps(analysis_result['reasons'])))
            connection.commit()
            print("Analysis result inserted successfully")

            # AnalysisStats 테이블의 total_call_count 값을 증가시키기 위한 UPDATE 쿼리
            update_total_call_count_query = """
            UPDATE AnalysisStats
            SET total_call_count = total_call_count + 1
            WHERE id = 1;
            """
            cursor.execute(update_total_call_count_query)
            connection.commit()
            print("Total call count updated successfully")

            if analysis_result['percent'] >= 70:
                insert_or_update_phone_query = """
                INSERT INTO PhoneNumbers (phone_number, scam_count)
                VALUES (%s, 1)
                ON DUPLICATE KEY UPDATE scam_count = scam_count + 1, last_reported = CURRENT_TIMESTAMP
                """
                cursor.execute(insert_or_update_phone_query, (phone_number,))
                connection.commit()
                print("Scam count updated successfully for phone number:", phone_number)


        return {
            'statusCode': 200,
            'body': json.dumps(f"Analysis completed successfully, result saved to {output_key}")
        }

    except ClientError as e:
        print(f"ClientError: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"Error: {str(e)}")
        }
    except pymysql.MySQLError as e:
        print(f"MySQL Error: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"MySQL Error: {str(e)}")
        }
    except Exception as e:
        print(f"Unhandled exception: {str(e)}")
        return {
            'statusCode': 500,
            'body': json.dumps(f"Unhandled error: {str(e)}")
        }
    finally:
        if connection:
            connection.close()
            print("Connection closed")
