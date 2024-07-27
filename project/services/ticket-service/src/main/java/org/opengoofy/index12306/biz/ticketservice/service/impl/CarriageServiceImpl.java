
package org.opengoofy.index12306.biz.ticketservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.CarriageDO;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.CarriageMapper;
import org.opengoofy.index12306.biz.ticketservice.service.CarriageService;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.cache.core.CacheLoader;
import org.opengoofy.index12306.framework.starter.cache.toolkit.CacheUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_CARRIAGE;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_QUERY_CARRIAGE_NUMBER_LIST;
/**
 * 列车车厢接口层实现
 */
@Service
@RequiredArgsConstructor
public class CarriageServiceImpl implements CarriageService {

    private final DistributedCache distributedCache;
    private final CarriageMapper carriageMapper;
    private final RedissonClient redissonClient;

    /**
     * 返回车厢类型下车厢号
     * @param trainId      列车 ID
     * @param carriageType 车厢类型
     * @return 车厢号
     */
    @Override
    public List<String> listCarriageNumber(String trainId, Integer carriageType) {
        final String key = TRAIN_CARRIAGE + trainId;
        return safeGetCarriageNumber(
                trainId,
                key,
                carriageType,
                () -> {
                    LambdaQueryWrapper<CarriageDO> queryWrapper = Wrappers.lambdaQuery(CarriageDO.class)
                            .eq(CarriageDO::getTrainId, trainId)
                            .eq(CarriageDO::getCarriageType, carriageType);
                    List<CarriageDO> carriageDOList = carriageMapper.selectList(queryWrapper);
                    List<String> carriageListWithOnlyNumber = carriageDOList.stream().map(CarriageDO::getCarriageNumber).collect(Collectors.toList());
                    return StrUtil.join(StrUtil.COMMA, carriageListWithOnlyNumber);
                });
    }

    private HashOperations<String, Object, Object> getHashOperations() {
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        return stringRedisTemplate.opsForHash();
    }

    private String getCarriageNumber(final String key, Integer carriageType) {
        HashOperations<String, Object, Object> hashOperations = getHashOperations();
        return Optional.ofNullable(hashOperations.get(key, String.valueOf(carriageType))).map(Object::toString).orElse("");
    }

    private List<String> safeGetCarriageNumber(String trainId, final String key, Integer carriageType, CacheLoader<String> loader) {
        String result = getCarriageNumber(key, carriageType);
        if (!CacheUtil.isNullOrBlank(result)) {
            return StrUtil.split(result, StrUtil.COMMA);
        }
        RLock lock = redissonClient.getLock(String.format(LOCK_QUERY_CARRIAGE_NUMBER_LIST, trainId));
        lock.lock();
        try {
            if (CacheUtil.isNullOrBlank(result = getCarriageNumber(key, carriageType))) {
                if (CacheUtil.isNullOrBlank(result = loadAndSet(carriageType, key, loader))) {
                    return Collections.emptyList();
                }
            }
        } finally {
            lock.unlock();
        }
        return StrUtil.split(result, StrUtil.COMMA);
    }

    private String loadAndSet(Integer carriageType, final String key, CacheLoader<String> loader) {
        String result = loader.load();
        if (CacheUtil.isNullOrBlank(result)) {
            return result;
        }
        HashOperations<String, Object, Object> hashOperations = getHashOperations();
        hashOperations.putIfAbsent(key, String.valueOf(carriageType), result);
        return result;
    }
}
