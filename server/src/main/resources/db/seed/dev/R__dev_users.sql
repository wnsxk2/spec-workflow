-- 개발용 사용자. 개발용 데이터베이스(spring 프로필 dev)에만 넣는다.
-- 이메일: dev@example.com / 비밀번호: dev-password-1234 (아래 값은 그 비밀번호의 bcrypt 값)
insert into users (email, password_hash)
values ('dev@example.com', '$2a$10$7gMfLlx3kFkduEh9tFy3juT3bSDjTe.uby.em227stM9FWBanQY9a')
on conflict (email) do update set password_hash = excluded.password_hash;
