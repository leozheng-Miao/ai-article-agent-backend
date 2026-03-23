package com.leo.ai.article.agent.controller;

import cn.hutool.core.bean.BeanUtil;
import com.leo.ai.article.agent.annotation.AuthCheck;
import com.leo.ai.article.agent.common.*;
import com.leo.ai.article.agent.constant.UserConstant;
import com.leo.ai.article.agent.exception.BusinessException;
import com.leo.ai.article.agent.exception.ErrorCode;
import com.leo.ai.article.agent.model.dto.user.*;
import com.leo.ai.article.agent.model.dto.user.email.ResetPasswordRequest;
import com.leo.ai.article.agent.model.dto.user.email.SendEmailCodeRequest;
import com.leo.ai.article.agent.model.dto.user.email.UserEmailLoginRequest;
import com.leo.ai.article.agent.model.dto.user.email.UserEmailRegisterRequest;
import com.leo.ai.article.agent.model.entity.User;
import com.leo.ai.article.agent.model.vo.LoginUserVO;
import com.leo.ai.article.agent.model.vo.UserVO;
import com.leo.ai.article.agent.service.UserService;
import com.leo.ai.article.agent.service.impl.EmailService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_LOGIN;
import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_REGISTER;
import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_RESET;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private EmailService emailService;

    // ───────────────── 账号密码 登录/注册 ─────────────────

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        long id = userService.userRegister(
                userRegisterRequest.getUserAccount(),
                userRegisterRequest.getUserPassword(),
                userRegisterRequest.getCheckPassword());
        return ResultUtils.success(id);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(
            @RequestBody UserLoginRequest req,
            HttpServletRequest request) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        LoginUserVO loginUserVO = userService.userLogin(
                req.getUserAccount(), req.getUserPassword(), request);
        return ResultUtils.success(loginUserVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        return ResultUtils.success(userService.getLoginUserVO(userService.getLoginUser(request)));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    // ───────────────── 邮箱验证码 ─────────────────

    @PostMapping("/email/send-code")
    public BaseResponse<Boolean> sendEmailCode(
            @RequestBody @Validated SendEmailCodeRequest request) {
        emailService.sendVerifyCode(request.getEmail(), request.getScene());
        return ResultUtils.success(true);
    }

    @PostMapping("/email/login")
    public BaseResponse<LoginUserVO> userLoginByEmail(
            @RequestBody @Validated UserEmailLoginRequest request,
            HttpServletRequest httpRequest) {
        emailService.verifyCode(request.getEmail(), request.getCode(), SCENE_LOGIN);
        LoginUserVO vo = userService.userLoginByEmail(request.getEmail(), httpRequest);
        return ResultUtils.success(vo);
    }

    @PostMapping("/email/register")
    public BaseResponse<Long> userRegisterByEmail(
            @RequestBody @Validated UserEmailRegisterRequest request) {
        emailService.verifyCode(request.getEmail(), request.getCode(), SCENE_REGISTER);
        long id = userService.userRegisterByEmail(
                request.getEmail(), request.getCode(),
                request.getUserPassword(), request.getCheckPassword());
        return ResultUtils.success(id);
    }

    @PostMapping("/email/reset-password")
    public BaseResponse<Boolean> resetPassword(
            @RequestBody @Validated ResetPasswordRequest request) {
        emailService.verifyCode(request.getEmail(), request.getCode(), SCENE_RESET);
        boolean result = userService.resetPassword(
                request.getEmail(), request.getNewPassword(), request.getCheckPassword());
        return ResultUtils.success(result);
    }

    // ───────────────── 管理员接口 ─────────────────

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(Long id) {
        if (id <= 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        User user = userService.getById(id);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return ResultUtils.success(user);
    }

    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(Long id) {
        User user = getUserById(id).getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        if (userAddRequest == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        user.setUserPassword(userService.getEncryptedPassword("12345678"));
        boolean save = userService.save(user);
        if (!save) throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加用户失败");
        return ResultUtils.success(user.getId());
    }

    @DeleteMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        return ResultUtils.success(userService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean b = userService.updateById(user);
        if (!b) throw new BusinessException(ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(b);
    }

    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        long pageSize = userQueryRequest.getPageSize();
        long pageNum = userQueryRequest.getPageNum();
        Page<User> userPage = userService.page(
                Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        userVOPage.setRecords(userService.getUserVOList(userPage.getRecords()));
        return ResultUtils.success(userVOPage);
    }
}