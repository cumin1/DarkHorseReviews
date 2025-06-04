package com.heima;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;

import java.util.Map;

@SpringBootTest
public class myTests {

    @Test
    void test1(){
        // 使用jedis连接redis并设置一个String类型的数据
        Jedis jedis = new Jedis("192.168.114.128", 6379);
        jedis.auth("123456");
        jedis.select(0);

        String result = jedis.set("name1", "xiaoming");
        System.out.println("result = " + result);
        String name1 = jedis.get("name1");
        System.out.println("name1 = " + name1);

        jedis.del("name1");
        jedis.hset("user:1","name","xiaoming");
        jedis.hset("user:1","age","18");
        Map<String, String> stringStringMap = jedis.hgetAll("user:1");
        System.out.println("user_1 = " + stringStringMap);

        if (jedis != null){
            // 释放资源
            jedis.close();
        }
    }


}
