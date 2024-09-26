# S3: team5-analyzed.text.storage 연결

import json
import boto3

dynamodb = boto3.client('dynamodb')
apigateway_management_api = boto3.client('apigatewaymanagementapi', endpoint_url='https://o4hctkhc90.execute-api.ap-south-1.amazonaws.com/production/')

TABLE_NAME = 'team5-WebSocketConnection'
BUCKET_NAME = 'team5-analyzed.text.storage'

def lambda_handler(event, context):
    # S3 이벤트에서 파일 이름 추출
    s3_info = event['Records'][0]['s3']
    file_name = s3_info['object']['key']
    
    # DynamoDB에서 연결 ID 가져오기
    response = dynamodb.get_item(
        TableName=TABLE_NAME,
        Key={'filename': {'S': file_name.split('-')[0] + '-' + file_name.split('-')[1]}}
    )
    
    
    if response:
        connection_id = response['Item']['connection_id']['S']
        
        # S3에서 파일 가져오기
        s3_client = boto3.client('s3')
        s3_object = s3_client.get_object(Bucket=BUCKET_NAME, Key=file_name)
        json_content = s3_object['Body'].read().decode('utf-8')
        json_content = '{"number": "' + file_name.split('-')[0] + '", ' + json_content[1:]
        print(json_content)

        # WebSocket 클라이언트에 결과 전송
        send_to_client(connection_id, json_content)
        
        # DynamoDB에서 항목 삭제 (선택사항)
        dynamodb.delete_item(
            TableName=TABLE_NAME,
            Key={'filename': {'S': file_name.split('-')[0]}}
        )
        
def send_to_client(connection_id, message):
    response = apigateway_management_api.post_to_connection(
        ConnectionId=connection_id,
        Data=message
    )
    print(message)
    return response
