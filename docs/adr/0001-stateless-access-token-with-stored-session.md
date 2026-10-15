# Access Token은 stateless로, 세션만 Redis에 저장한다

Access Token(JWT)은 서명만 확인하고 Redis를 조회하지 않는다. Redis에는 세션, 즉 Refresh Token 계보만 기록해 토큰 교환과 재사용 감지에 쓴다. 매 요청마다 Redis를 조회하는 방식(즉시 로그아웃 가능)과 Redis를 아예 쓰지 않는 방식(재사용 감지 불가)을 검토했고, 요청 속도와 RTR의 도난 감지를 함께 얻기 위해 이 방식을 골랐다.

## 결과

- 로그아웃하거나 세션이 폐기되어도, 이미 발급된 Access Token은 만료될 때까지 유효하다. Access Token 수명을 짧게 두는 것으로 이 위험을 줄인다.
