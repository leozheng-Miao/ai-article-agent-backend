package com.leo.ai.article.agent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.ai.article.agent.exception.BusinessException;
import com.leo.ai.article.agent.exception.ErrorCode;
import com.leo.ai.article.agent.model.dto.user.UserQueryRequest;
import com.leo.ai.article.agent.model.entity.User;
import com.leo.ai.article.agent.model.enums.UserRoleEnum;
import com.leo.ai.article.agent.model.vo.LoginUserVO;
import com.leo.ai.article.agent.model.vo.UserVO;
import com.leo.ai.article.agent.mapper.UserMapper;
import com.leo.ai.article.agent.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.leo.ai.article.agent.constant.UserConstant.USER_LOGIN_STATE;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度不能小于4");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }
        String encryptedPassword = getEncryptedPassword(userPassword);
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptedPassword);
        user.setUserName("用户" + userAccount);
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户注册失败");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度不能小于4");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8");
        }
        String encryptedPassword = getEncryptedPassword(userPassword);
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userAccount", userAccount)
                .eq("userPassword", encryptedPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不存在或密码错误");
        }
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO userLoginByEmail(String email, HttpServletRequest request) {
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userEmail", email);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            user = new User();
            user.setUserEmail(email);
            String prefix = email.substring(0, email.indexOf('@'));
            user.setUserAccount("email_" + prefix + "_" + RandomUtil.randomNumbers(4));
            user.setUserName("用户" + RandomUtil.randomNumbers(6));
            user.setUserRole(UserRoleEnum.USER.getValue());
            boolean save = this.save(user);
            if (!save) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "账号创建失败");
            }
        }
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public long userRegisterByEmail(String email, String code, String userPassword, String checkPassword) {
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8位");
        }
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userEmail", email);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该邮箱已注册");
        }
        String prefix = email.substring(0, email.indexOf('@'));
        User user = new User();
        user.setUserEmail(email);
        user.setUserAccount("email_" + prefix + "_" + RandomUtil.randomNumbers(4));
        user.setUserPassword(getEncryptedPassword(userPassword));
        user.setUserName("用户" + RandomUtil.randomNumbers(6));
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败");
        }
        return user.getId();
    }

    @Override
    public boolean resetPassword(String email, String newPassword, String checkPassword) {
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8位");
        }
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userEmail", email);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该邮箱未注册");
        }
        user.setUserPassword(getEncryptedPassword(newPassword));
        return this.updateById(user);
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) return null;
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currUser = (User) userObj;
        if (userObj == null || currUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        currUser = this.getById(currUser.getId());
        if (currUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        return currUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) return null;
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollectionUtil.isEmpty(userList)) return new ArrayList<>();
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public String getEncryptedPassword(String password) {
        final String SALT = "cecilia";
        return DigestUtils.md5DigestAsHex((SALT + password).getBytes(StandardCharsets.UTF_8));
    }
}