# API 게이트웨이: team5-http-p 연결

import json
import boto3
import os
import uuid

s3 = boto3.client('s3')

def lambda_handler(event, context):
    bucket_name = os.environ['BUCKET_NAME']  # S3 버킷 이름 환경 변수에서 가져오기
    # body = json.loads(event.get('query', '{}'))  # HTTP Body에서 파라미터 추출
    query = event.get('queryStringParameters', {})  # URL에서 ? 뒤에 filename=cat&file2name=~~... json으로 추출출
    key = query.get('filename')  # 파라미터로 key 가져오기
    print(query)
    if not key:  # key가 제공되지 않으면 UUID로 생성
        key = str(uuid.uuid4())
    
    presigned_url = s3.generate_presigned_url(
        'put_object',
        Params={'Bucket': bucket_name, 'Key': key},
        ExpiresIn=3600  # URL 유효 기간: 1시간
    )
    
    return {
        'statusCode': 200,
        'body': json.dumps({
            'url': presigned_url,
        }),
        'headers': {
            'Content-Type': 'application/json'
        }
    }
