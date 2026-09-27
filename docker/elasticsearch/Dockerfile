# 한국어 형태소 분석(nori) 플러그인을 설치한 ES - 버전은 search-service가 쓰는 ES 클라이언트(Spring Boot BOM)와 맞춘다
# Docker Hub 공식 이미지를 사용 - docker.elastic.co와 같은 이미지이며, 로컬에서 elastic 레지스트리 다운로드가 실패해 변경
FROM elasticsearch:9.4.5
RUN bin/elasticsearch-plugin install --batch analysis-nori
