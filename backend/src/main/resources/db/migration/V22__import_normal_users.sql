INSERT INTO users (
    username,
    password_hash,
    display_name,
    department_id,
    role,
    disabled,
    must_change_password,
    failed_login_attempts,
    locked_until,
    record_version,
    created_at,
    updated_at
)
SELECT
    imported.username,
    imported.password_hash,
    imported.display_name,
    NULL,
    'USER',
    FALSE,
    TRUE,
    0,
    NULL,
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM (
    SELECT 'yaojun' AS username, '$2a$10$EUiTrEkdprMCF3iHhGK/geZj8SD85lWYk6PRIL6xBQ.WmyDJAfwLC' AS password_hash, '姚俊' AS display_name
    UNION ALL SELECT 'liuquanyou', '$2a$10$qP4u4c0kJXX4M9FeExkKPerDTqGCIQOcP6c5ebFk0H3NccmybMndi', '刘权悠'
    UNION ALL SELECT 'liuhuan', '$2a$10$.ObB87ehgAwFz/B0y1NG1Onig4b2KwlnBDXMy0tanL/HLRf0BLtqq', '刘欢'
    UNION ALL SELECT 'huangrongjuan', '$2a$10$mxBCaZpS/T9S2dPRzMkRLOxDk2UQXJ/lJdGXtIYRHOUCHQvMKAkUy', '黄容娟'
    UNION ALL SELECT 'dingbo', '$2a$10$sS9WwaIwKXRdDbjUz8XzyeDI3c8vk5kqyIwktmGtR0RPOPC4JAYRq', '丁波'
    UNION ALL SELECT 'yangximei', '$2a$10$zqacLqTR5fIqvsPtW8./uOhC2vBtnEMMviBVYDrlFeym3BjWSUEXG', '杨喜梅'
    UNION ALL SELECT 'liuxiangjun', '$2a$10$3EdA5ixWtiyFGEH4jqtaiOq/uGPiUXR9RaXzrpRbwS/qL2AEzyRnq', '刘香君'
    UNION ALL SELECT 'liuwentao', '$2a$10$spMNEQVfzvp2tcKkbNxzR.ZCOFYtXF2Cw5x0Vd.36IQu9lo5bOHrq', '刘文滔'
    UNION ALL SELECT 'liuhaili', '$2a$10$dd9vocZygNX1TC3rzvTJBuibayCz1maG/q2B5hdcjwSgvS41lYxTy', '刘海丽'
    UNION ALL SELECT 'zhangmeilin', '$2a$10$HBytU63jAg15dJFDYTlcI.m36RjLuhX8UHrqw3J6WdYZfVQ0XgXCu', '张美林'
    UNION ALL SELECT 'chenlifen', '$2a$10$09yYD1msy9EpqWcR8k.laO6NVo3wfO82RCBAjhTLAkawTez0fYGhq', '陈礼芬'
    UNION ALL SELECT 'qimingming', '$2a$10$TXdZeNGz9SCfR7dePiGm1.RQjX6TiOToyYVFi7UA56KRwt1E16KUq', '祁明明'
    UNION ALL SELECT 'huangfangfang', '$2a$10$otoee.PmXrabSu5V5Uq9Iupc3WiQOoD2Cp9w5j39kiJLzsrkydOBO', '黄芳芳'
    UNION ALL SELECT 'huanghuihui', '$2a$10$EMl6rGUlzMjmlRn/1Ccc0u36pO64LUYBg4tAbLI1FnNwWxfx2WF7C', '黄慧慧'
    UNION ALL SELECT 'yangjinze', '$2a$10$RmzF.g4LX6BTXYxh2MR8ReBn8ZHMp9BvpSp56pwW.HXOHSpN7Yzeu', '杨金泽'
    UNION ALL SELECT 'chenyiying', '$2a$10$.uuGlPwSaL30QXOAvhytNe3C0R7S3p7wKS31nxBcbw3uyzHXJlQPy', '陈怡盈'
    UNION ALL SELECT 'nieshang', '$2a$10$n9aVJTflL7PpHslOWGHAqOeo.TTttVVACTaB2wXa.7SvdrcdniMC6', '聂上'
    UNION ALL SELECT 'huangzufang', '$2a$10$bUX2F3t/2nWr97JAZEWlJOekP1EhwwB7eeya21PKo7FaxG8Ln37hW', '黄祖芳'
    UNION ALL SELECT 'chenshili', '$2a$10$6ORvUyFtghXQkprfKyaUq.XGQxZEvoPCseI3wDAUKP2.3HKBmln5W', '陈诗丽'
    UNION ALL SELECT 'wangshaoyu', '$2a$10$CrS5SMyNZxGdJNmTuivbS.WpQIpXMhvIotGSEuiAuqRs2ensuACzS', '王绍宇'
    UNION ALL SELECT 'wangjingang', '$2a$10$NAiYz.hwXb83EXzn5.lmK.LUXKLxTshvthy3oXKoeCE9xH/NhoLH.', '王金刚'
    UNION ALL SELECT 'tangjingliang', '$2a$10$bsof0caAGHPU1k46OY5zXuFBtJ8yL454jUtgdGzV0qgHsNQMMhBlq', '唐景靓'
    UNION ALL SELECT 'sushuangrong', '$2a$10$RCO8CKqPmcxx2mPypfu5k.igySbN2WdZMk8svLlhEDL8OQgbU6DW2', '苏双蓉'
    UNION ALL SELECT 'moyongshi', '$2a$10$s6uMCbQibQRkznVMcyggyuNAGd5cGu.lm5tCJdFaMjW2Ysb5ukuV2', '莫泳诗'
    UNION ALL SELECT 'chenyedong', '$2a$10$HSVzUj8QfcnkIwTu9g3zZOyrpAzuuOpBa6wqrX1u0XDrPC9iCCtz2', '陈业东'
    UNION ALL SELECT 'liuxiaoyu', '$2a$10$HLq/AtEtCTVLBpzv2q6.8u0/F6PFil9pAyjFHaKoXZxIq38YgZ/6G', '刘晓玉'
    UNION ALL SELECT 'lichengyu', '$2a$10$gLxUuksy8X/GwAs58eXDleIuoSwg1PaTUhDhPLs.JCRqFQAmlTCVq', '李程钰'
    UNION ALL SELECT 'liuyikun', '$2a$10$FkD5cmZPTL9CiWhC5wlzx.hD0wVh3keJySqNMNPbCudZCIBIrOV6G', '刘艺焜'
    UNION ALL SELECT 'hulianhong', '$2a$10$0M9o5YY1B2sfPS.jGcw0ce5.pRNdTOSr25I3M04YkJMu0okVI0AQi', '胡连鸿'
    UNION ALL SELECT 'xiaojiana', '$2a$10$BeIDsja5xFLXesPRA6t12Ozv6J4G7xChtJzFH9n0HusoAmbm9c0jG', '肖佳娜'
    UNION ALL SELECT 'hanhuiyang', '$2a$10$HXisDWEg/FBGb346S5QpD.mduEF2kngeb3kSCPiq6MKo4s0.G5X1S', '韩辉阳'
    UNION ALL SELECT 'mozicheng', '$2a$10$I3YUV/gh7letfwZtdIlAFemCPgHwcVLuCcYTlVY2/4kdnE9oA6DmS', '莫梓成'
    UNION ALL SELECT 'flh', '$2a$10$lA.UmPwG49vPacExJDLs.uh7WAEIFcIKISXd0r8RAgZYSvjcTsTXG', '冯凌汉'
    UNION ALL SELECT 'sujiang', '$2a$10$F8tzA3XdW3ha.W8jOiU7guXQ4f.uzHmEUyy5afYbpvqqdL5OpJRfy', '苏江'
    UNION ALL SELECT 'yangyuping', '$2a$10$Uoic9JGTx0HqfocwVIiewuZnA8eKBAmz1/qEADvoFxN0T.G8l/dcG', '杨玉萍'
    UNION ALL SELECT 'yangmingjun', '$2a$10$KQ71oGG3XXidDQJ.P/i89uIu48LzuGUS6Fhk.Cm9H00T57C64ysMy', '杨铭俊'
    UNION ALL SELECT 'tangwenchun', '$2a$10$70zxdEqhdKdRNOKuwyAZ9eOOglRSzkwrI4IpBNjvla7jlqTfv857.', '唐文春'
    UNION ALL SELECT 'suzhirong', '$2a$10$WK/wUI5ouuqM7iv0Rdizxu.42NY6VzIDcnp65CkWk1PZp2p3LmS4m', '粟志荣'
    UNION ALL SELECT 'maxueze', '$2a$10$B1Ai2wRD7.nrZZmmwsZH1.v9bS9FFBvDsA2wG6jlB43FKm80AYr3W', '马学泽'
    UNION ALL SELECT 'wangjinrui', '$2a$10$05.IhgPErDaenXctUmmuzOK15fq/HwvA3lOlEHS6/CKcu2sp9bOOS', '王金瑞'
    UNION ALL SELECT 'zhanglinlin', '$2a$10$3gBZHSql4gW1pkzN7gSyVulgFVBb6KZF2i7I8nY/BV19N7DC75.Vy', '张林林'
    UNION ALL SELECT 'yangdongfeng', '$2a$10$RuL/vTsXJ6luri/sM0uPO.bD/.J945GOvdeXm1XER18ypx3qF8hf2', '杨东风'
    UNION ALL SELECT 'liyanqun', '$2a$10$Lc601yq6uGN2MN./NMge6eqSNsfFeVBZl6QsvJBj8pdcXfUdAj0am', '李艳群'
    UNION ALL SELECT 'xiehanming', '$2a$10$WnfQ2s5xGFVh1ni1fPM/Rel39Pq4Hav7R5LS8UjODYiWWwW4KBSWq', '谢汉明'
    UNION ALL SELECT 'mowanxian', '$2a$10$MCnn/IO4eO4ftFM7qo3wTu87TELhI2v97spTAndoA2KNjhHpbEk4i', '莫婉娴'
    UNION ALL SELECT 'gaohan', '$2a$10$o/IfFNXvo8TrGqQDkUhHOukIVcFkPEHS7drhYHhu/xgEccndjs.Re', '高函'
    UNION ALL SELECT 'wl001', '$2a$10$x3Sbx5.vUm.lm4//2KLB2eEOXzZqt2L6hT099E1KPUDpmWCHkFdFm', '刘小姐'
    UNION ALL SELECT 'duanjun', '$2a$10$xZnan5OSUl6zjOjo3b5Y.OpRTtfDvnoAK5GN8ncV/NYm5UUQziJZC', '段军'
    UNION ALL SELECT 'ouyanhua', '$2a$10$kRAvtarO8ormCdvYGE1ADubWMpMzct4IZmkX2QQXmMUDkdAngn4gy', '欧艳华'
    UNION ALL SELECT 'zhanzengjie', '$2a$10$2c1EkBMNr1JUbNxMn6URluVnlzuVWXJYUG.sjWf37QEzd78ZM3Jdi', '詹增杰'
    UNION ALL SELECT 'liyuexin', '$2a$10$Bds4oBYfbqb5MKj5NZwEtO0HV0Hj/moB.TzwL2GhnQiocHz/swV5m', '黎月新'
    UNION ALL SELECT 'wuxianghuan', '$2a$10$npZEuYP4POOFQuB0bnpBuOdjkIjidkos64DmAt/HKnZwoeIRkDPFm', '吴湘环'
    UNION ALL SELECT 'chenjianhua', '$2a$10$2uZQNXPNuhpBNYfW4kd7qe.grA4E2V5eItpg.NQgjtY2sfiX.JINe', '陈建华'
    UNION ALL SELECT 'zhouguangyu', '$2a$10$a57.6F6pnkZh3/e/azWH9e35GagGfQN5DkiBQOMcJvAuQ//U7EnpG', '周光宇'
    UNION ALL SELECT 'chenyujiao', '$2a$10$RIdkK6wUx5Q3dy5OLI3TaO646XkluxV2A0R1seV8pHEwmoIWErbCG', '陈玉娇'
    UNION ALL SELECT 'huangyingxia', '$2a$10$ZZm9EhYzke1jwnz.WabYvONwuYCRMQL9WiPamckAr0gFL6ZWAFsOq', '黄樱霞'
    UNION ALL SELECT 'tangsuping', '$2a$10$jZraAiTglDINohTkAT6Ti.1rjKE5OdbQXd6Ne8hJhUU7YEPAsp5VK', '唐素平'
    UNION ALL SELECT 'jiangyouliang', '$2a$10$jymbqo4o1cau3ewUgch0PON3CiZo2IJiKH/DaHKtpdsFK941TV0Ni', '姜友梁'
    UNION ALL SELECT 'chengwenhao', '$2a$10$7yl.2DWA2DgpyTR.2VpoaezvUgN6v6.35C/KLZ285ye60.Zrexk9a', '程文豪'
) AS imported
LEFT JOIN users AS existing ON existing.username = imported.username
WHERE existing.id IS NULL;
