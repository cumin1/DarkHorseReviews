package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByBlogId(Long id) {
        // 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 查询有关用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        // 查询当前用户是否已点赞
        boolean isLiked = isBlogLiked(id, UserHolder.getUser().getId());
        blog.setIsLike(isLiked);

        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

            boolean isLiked = isBlogLiked(blog.getId(), UserHolder.getUser().getId());
            blog.setIsLike(isLiked);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        boolean isMember = isBlogLiked(id, userId);

        // 如果未点赞 数据库点赞数+1 保存用户到redis的集合
        if(BooleanUtil.isFalse(isMember)){
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else{
            // 如果已点赞 取消点赞 数据库点赞数-1 redis集合中删除当前用户
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result getBlogLikes(Long id) {
        // 查询top5的点赞用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5Ids = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5Ids==null || top5Ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 解析用户id
        List<Long> ids = top5Ids.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 根据用户id查询用户
        List<UserDTO> userDTOList = userService
                .query().in("id",ids)
                .last("order by field(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOList);
    }

    boolean isBlogLiked(Long blogId, Long userId){
        String key = BLOG_LIKED_KEY + blogId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            return false;
        }
        return true;
    }
}
