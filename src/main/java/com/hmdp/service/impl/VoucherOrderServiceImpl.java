package com.hmdp.service.impl;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimplyRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //根据id查询数据库，库存和时间,状态
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //判断是否符合条件
        //不符合，返回错误信息
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //结束了
            return Result.fail("秒杀以及结束了");
        }
        if (voucher.getStock() < 1) {
            //没有库存了
            return Result.fail("已经没有票了");
        }

        //一人一单
        //查询订单
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        SimplyRedisLock lock = new SimplyRedisLock(stringRedisTemplate,"order:" + userId);
        boolean tyeLock = lock.tyeLock(1200L);
        if (!tyeLock) {
            return Result.fail("不允许创建多个锁");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            //用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }
        //符合，修改数据库
        boolean success = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            return Result.fail("已经没有票了");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id   用id生成器
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //写入数据库
        save(voucherOrder);
        //返回id
        return Result.ok(orderId);

    }
}
