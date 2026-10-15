-- E2E용 사용자. E2E용 데이터베이스(spring 프로필 e2e)에만 넣는다.
-- 이메일: e2e-user@example.com / 비밀번호: e2e-password-1234 (아래 값은 그 비밀번호의 bcrypt 값)
insert into users (email, password_hash)
values ('e2e-user@example.com', '$2a$10$OGq8bP7e2YOoiqwRIXZLu.kzDoeAh4662Oj2XZNd41CS82KQFCPn.')
on conflict (email) do update set password_hash = excluded.password_hash;
