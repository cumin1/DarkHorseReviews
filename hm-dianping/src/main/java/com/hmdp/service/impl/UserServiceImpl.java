package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
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
    private UserMapper userMapper;

    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果手机号不符合要求返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 生成一个验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到session
        session.setAttribute("code", code);
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
        // 将验证码和session中的验证码进行比较
        Object sessionCode = session.getAttribute("code");
        if (!sessionCode.toString().equals(userCode)){
            return Result.fail("验证码错误!");
        }
        // 根据手机号查询用户(有就登录没有就注册)
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = CreateUserByPhone(phone);
        }
        // 保存用户到session
        session.setAttribute("user", user);
        return Result.ok();
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
