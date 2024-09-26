# S3: team5-audio.storge 연결

import json
import boto3
import urllib.parse
from pydub import AudioSegment
import io
import time
import os

os.environ["PATH"] += os.pathsep + "/opt/python/ffmpeg/"
AudioSegment.converter = "/opt/python/ffmpeg/ffmpeg"

s3_client = boto3.client('s3')
transcribe_client = boto3.client('transcribe')

INPUT_S3 = "team5-audio.storge"  # 원본 음성 파일이 저장된 버킷
PROCESSING_S3 = "team5-audio.chunk.storage"  # 분할된 파일과 Transcribe 결과를 저장할 중간 버킷
OUTPUT_S3 = "team5-transcribed.text.storage"  # 최종 결합된 Transcribe 결과를 저장할 버킷

def get_segment_length(audio_length_ms):
    """음성 파일 길이에 따라 분할 시간을 결정합니다."""
    if audio_length_ms <= 30 * 60 * 1000:  # 30분 이하
        return 15 * 1000  # 15초
    elif audio_length_ms <= 45 * 60 * 1000:  # 45분 이하
        return 20 * 1000  # 20초
    elif audio_length_ms <= 60 * 60 * 1000:  # 60분 이하
        return 30 * 1000  # 30초
    else:
        # 60분 초과: 100개의 파일로 나눌 수 있는 시간 계산
        return audio_length_ms // 100

def split_audio(audio, segment_length_ms):
    return [audio[i:i + segment_length_ms] for i in range(0, len(audio), segment_length_ms)]

def delete_s3_object(bucket_name, key):
    s3_client.delete_object(Bucket=bucket_name, Key=key)

def lambda_handler(event, context):
    input_bucket_name = INPUT_S3
    processing_bucket_name = PROCESSING_S3
    output_bucket_name = OUTPUT_S3
    
    # S3 이벤트에서 버킷 이름과 파일 이름을 추출
    bucket_name = event['Records'][0]['s3']['bucket']['name']
    object_key = urllib.parse.unquote_plus(event['Records'][0]['s3']['object']['key'])
    
    # 음성 파일이 입력 버킷에 존재하는지 확인
    if bucket_name != input_bucket_name:
        return {
            'statusCode': 400,
            'body': json.dumps('Error: Event from unexpected bucket')
        }
    
    # S3에서 음성 파일 다운로드
    audio_file = io.BytesIO()
    s3_client.download_fileobj(input_bucket_name, object_key, audio_file)
    audio_file.seek(0)
    
    # 음성 파일 로드
    audio = AudioSegment.from_file(audio_file)
    audio_length_ms = len(audio)
    
    # 음성 파일의 길이에 따라 분할 시간 결정
    segment_length_ms = get_segment_length(audio_length_ms)
    
    # 음성 파일을 분할
    segments = split_audio(audio, segment_length_ms)
    
    transcribe_job_names = []
    for segment_index, segment in enumerate(segments):
        # 각 분할된 파일을 메모리에 저장
        buffer = io.BytesIO()
        segment.export(buffer, format="mp3")
        buffer.seek(0)
        
        # 분할된 파일을 새로운 S3 버킷에 업로드
        segment_key = f"{object_key.split('.')[0]}_part{segment_index}.mp3"
        s3_client.upload_fileobj(buffer, processing_bucket_name, segment_key)
        
        # Transcribe 작업 이름 설정
        transcribe_job_name = f"{object_key.split('.')[0]}-part{segment_index}-transcription"
        transcribe_job_names.append(transcribe_job_name)
        
        # Transcribe 작업 시작
        transcribe_client.start_transcription_job(
            TranscriptionJobName=transcribe_job_name,
            Media={'MediaFileUri': f"s3://{processing_bucket_name}/{segment_key}"},
            MediaFormat='mp3',
            LanguageCode='ko-KR',
            OutputBucketName=processing_bucket_name  # Transcribe 결과를 처리 버킷에 저장
        )

    # Transcribe 작업이 완료될 때까지 대기하고 결과 결합
    all_texts = []
    for job_name in transcribe_job_names:
        while True:
            status = transcribe_client.get_transcription_job(TranscriptionJobName=job_name)
            if status['TranscriptionJob']['TranscriptionJobStatus'] in ['COMPLETED', 'FAILED']:
                break
            time.sleep(5)
        
        if status['TranscriptionJob']['TranscriptionJobStatus'] == 'COMPLETED':
            transcript_file_uri = status['TranscriptionJob']['Transcript']['TranscriptFileUri']
            transcript_file_key = transcript_file_uri.split('/')[-1]
            
            # 결과 JSON 파일 다운로드
            transcript_file = s3_client.get_object(Bucket=processing_bucket_name, Key=transcript_file_key)
            transcript_data = json.loads(transcript_file['Body'].read().decode('utf-8'))
            all_texts.append(transcript_data['results']['transcripts'][0]['transcript'])
            
            # 개별 JSON 파일 삭제
            delete_s3_object(processing_bucket_name, transcript_file_key)
        else:
            return {
                'statusCode': 500,
                'body': json.dumps(f"Error: Transcription job {job_name} failed")
            }

    # 결합된 텍스트 생성
    combined_text = " ".join(all_texts)
    
    # 결합된 텍스트를 JSON 파일로 S3에 저장
    combined_transcript_key = f"{object_key.split('.')[0]}-transcription.json"
    s3_client.put_object(
        Bucket=output_bucket_name,
        Key=combined_transcript_key,
        Body=json.dumps({"transcript": combined_text}, ensure_ascii=False),
        ContentType='application/json'
    )
    
    # 분할된 파일 삭제
    for segment_index in range(len(segments)):
        segment_key = f"{object_key.split('.')[0]}_part{segment_index}.mp3"
        delete_s3_object(processing_bucket_name, segment_key)

    return {
        'statusCode': 200,
        'body': json.dumps(f"Transcription jobs completed successfully, combined transcript saved as {combined_transcript_key}")
    }