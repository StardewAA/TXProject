-- 商城用户端本地测试账号：13653530831 / admin123
-- 幂等：重复执行时只更新该手机号的测试密码和状态。
SET @test_mobile = '13653530831';
SET @test_password = '$2b$04$X8/4wNFajEN8M4K3IxY8SOUmyyCqBaiXehVOF9Rw4SjsDKxEERVOW';

UPDATE member_user
SET password = @test_password,
    status = 0,
    nickname = '商城测试用户',
    tenant_id = 1,
    updater = 'system',
    update_time = NOW()
WHERE mobile = @test_mobile;

INSERT INTO member_user (mobile, password, status, register_ip, nickname, tenant_id)
SELECT @test_mobile, @test_password, 0, '127.0.0.1', '商城测试用户', 1
WHERE NOT EXISTS (
    SELECT 1 FROM member_user WHERE mobile = @test_mobile AND deleted = b'0'
);
