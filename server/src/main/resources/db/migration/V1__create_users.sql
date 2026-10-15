-- 사용자 정보. 로그인 기능은 이 표를 읽기만 한다. 사용자를 만드는 일은 회원가입 spec의 몫이다.
create table users (
    id            uuid         primary key default gen_random_uuid(),
    email         varchar(320) not null unique,
    password_hash varchar(100) not null,
    -- 이메일은 앞뒤 공백 없이 소문자로 저장한다. 로그인할 때도 같은 방식으로 바꿔 비교한다.
    constraint users_email_normalized check (email = lower(btrim(email)))
);
