-- E2E용 사용자. E2E용 데이터베이스(spring 프로필 e2e)에만 넣는다.
-- 두 사용자의 비밀번호는 모두 e2e-password-1234 (아래 값은 그 비밀번호의 bcrypt 값)
-- e2e-user@example.com: 로그인·새로고침·로그아웃 시나리오용
-- e2e-lockout@example.com: 로그인 차단 시나리오 전용. 다른 시나리오의 사용자를 차단하지 않기 위해 따로 둔다.
insert into users (email, password_hash)
values ('e2e-user@example.com', '$2a$10$OGq8bP7e2YOoiqwRIXZLu.kzDoeAh4662Oj2XZNd41CS82KQFCPn.'),
       ('e2e-lockout@example.com', '$2a$10$OGq8bP7e2YOoiqwRIXZLu.kzDoeAh4662Oj2XZNd41CS82KQFCPn.')
on conflict (email) do update set password_hash = excluded.password_hash;
