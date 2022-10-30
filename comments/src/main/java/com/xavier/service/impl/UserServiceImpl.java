package com.xavier.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.dto.LoginFormDTO;
import com.xavier.dto.Result;
import com.xavier.dto.UserDTO;
import com.xavier.entity.User;
import com.xavier.mapper.UserMapper;
import com.xavier.service.IUserService;
import com.xavier.utils.RegexUtils;
import com.xavier.utils.SystemConstants;
import com.xavier.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xavier.utils.RedisConstants.*;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证法的service方法
     * @param phone 发送验证吗的手机号码
     * @param session 当前用户的会话
     * @return 返回一个Result对象,并携带响应的信息
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 判断手机号是否有效(正则表达式判断)
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 获取6位的随机数
        String code = RandomUtil.randomNumbers(6);
        // 将手机号和验证码保存在redis中便于以后的校验,以手机号为key保证key的唯一性
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("发送短信验证码成功,验证码: " + stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone));
        return Result.ok("发送短信验证码成功,验证码: " + code + ",有效期"+ LOGIN_CODE_TTL +"分钟,请及时使用");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        // 校验手机号是否有效
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("当前输入的手机号码无效");
        }
        // 验证手机号和验证码是否一一对应
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);


        if (redisCode == null || !redisCode.equals(code)){
            return Result.fail("验证码输入错误");
        }
        // 校验成功,根据手机号码去查询响应的用户
        User loginUser = userMapper.getUserByPhone(phone);
        // 用户查询不到,直接创建一个新用户
        if (loginUser == null){
            String randomNickName = RandomUtil.randomString(16);
            loginUser = new User(loginForm.getPhone(),randomNickName);
            userMapper.addNewUser(loginUser);
            return Result.ok("验证码校验成功,检测到该用户还未注册,直接注册一个新用户");
        }
        // 隐藏用户信息,值保留可以辨识的重要信息
        UserDTO userDTO = BeanUtil.copyProperties(loginUser, UserDTO.class);
        // 生成一个token用户黄用户信息保存到redis中
        String token = UUID.randomUUID().toString(true);
        // hutools工具包将Bean装换成map用户保存到redis中
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setFieldValueEditor((oldKey,oldValue) -> oldValue.toString()));
        // 保存user到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userDTOMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    /**
     * 根据用户id查询用户信息
     * @param userId 用户id
     * @return 返回用户信息
     */
    @Override
    public User getUserById(Long userId) {
        return userMapper.getUserByUserId(userId);
    }

    /**
     * 用户签到的函数
     * @return 返回签到结果
     */
    @Override
    public Result userSign() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        // 保存在redis中的key
        String key = USER_SIGN_KEY + userId + ":" + format;
        // 获取当前在天在这个月的天数
        int dayOfMonth = now.getDayOfMonth();
        // 保存到redis中
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    /**
     * 用于计算用户本月到今天为止的连续签到的天数
     * @return 返回连续签到的天数
     */
    @Override
    public Result userSignCount() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        // 保存在redis中的key
        String key = USER_SIGN_KEY + userId + ":" + format;
        // 获取当前在天在这个月的天数
        int dayOfMonth = now.getDayOfMonth();
        // 获取redis中存储的bitMap的这个串
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        // 空值判断
        if (longs == null || longs.isEmpty()) return Result.ok(0);
        Long num = longs.get(0);
        if (num == null || num == 0) return Result.ok(0);
        // 计数器
        int count = 0;
        // 循环遍历并右移,判断是否为1,为1则是签到
        while (true){
            if ((num & 1) == 0){
                break;
            }else {
                count++;
            }
            // 右移并赋值
            num >>>= 1;
        }
        // 返回
        return Result.ok(count);
    }

}
