package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootTest
@EnableAspectJAutoProxy(exposeProxy = true)
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redid(1L,10L);
    }

}
