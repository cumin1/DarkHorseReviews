package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果手机号不符合要求返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 生成一个验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到redis 设置超时时间为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.debug("发送短信验证码成功: {}",code);
        return Result.ok();
    }


    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 提交手机号
        String userCode = loginForm.getCode();
        String phone = loginForm.getPhone();
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果手机号不符合要求返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 校验验证码
        if (RegexUtils.isCodeInvalid(userCode)) {
            return Result.fail("无效验证码!");
        }
        // 将验证码和redis中的验证码进行比较
        Object sessionCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if (!sessionCode.toString().equals(userCode)){
            return Result.fail("验证码错误!");
        }
        // 根据手机号查询用户(有就登录没有就注册)
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = CreateUserByPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 转换UserDTO为Map
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());
        // 保存用户到redis
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.SECONDS); // 设置有效期
        // 返回token给客户端
        return Result.ok(token);
    }

    /**
     * 根据电话号码插入新用户
     * @param phone
     * @return
     */
    private User CreateUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user); // 保存user到user表 mp提供的功能
        return user;
    }
}
