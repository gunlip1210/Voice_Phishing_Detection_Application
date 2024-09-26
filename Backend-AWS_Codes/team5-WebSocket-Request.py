# API 게이트웨이: team5-websocket-separated 연결

import json
import boto3
import datetime

dynamodb = boto3.client('dynamodb')
s3 = boto3.client('s3')
apigatewaymanagementapi = boto3.client('apigatewaymanagementapi', endpoint_url='https://o4hctkhc90.execute-api.ap-south-1.amazonaws.com/production/')
TABLE_NAME = 'team5-WebSocketConnection'
S3_BUCKET_NAME = 'team5-analyzed.text.storage'  # S3 버킷 이름을 여기에 입력하세요

def lambda_handler(event, context):
    connection_id = event['requestContext']['connectionId']
    file_name = event['body']
    print(file_name)
    
    # S3에 file_name과 동일한 객체가 있는지 확인
    try:
        s3_response = s3.get_object(Bucket=S3_BUCKET_NAME, Key=file_name.split('.')[0] + '-analysis.json')
        file_content = s3_response['Body'].read().decode('utf-8')
        
        
        # 파일 내용이 있다면 WebSocket API를 통해 json 내용을 응답
        response = apigatewaymanagementapi.post_to_connection(
            ConnectionId=connection_id,
            Data=file_content
            # Data=json.dumps({"message": "File found in S3", "content": file_content})
        )
        
        return {'statusCode': 200, 'body': 'File found and content sent via WebSocket.'}
    
    except s3.exceptions.NoSuchKey:
        # S3에 객체가 없는 경우 기존 코드와 동일한 동작 수행
        dynamodb.put_item(
            TableName=TABLE_NAME,
            Item={
                'filename': {'S': file_name.split('.')[0]},
                'connection_id': {'S': connection_id},
                'status': {'S': 'pending'},
                'created_at': {'S': str(datetime.datetime.now())}
            }
        )
        
        return {'statusCode': 200, 'body': 'Request received, waiting for analysis.'}
