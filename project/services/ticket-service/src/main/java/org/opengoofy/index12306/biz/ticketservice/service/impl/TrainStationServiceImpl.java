
package org.opengoofy.index12306.biz.ticketservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TrainStationDO;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.TrainStationMapper;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TrainStationQueryRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.TrainStationService;
import org.opengoofy.index12306.biz.ticketservice.toolkit.StationCalculateUtil;
import org.opengoofy.index12306.framework.starter.common.toolkit.BeanUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 列车站点接口实现层
 */
@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl implements TrainStationService {

    private final TrainStationMapper trainStationMapper;

    /**
     * 根据列车ID查询列车途径车站信息。
     *
     * @param trainId 列车ID
     * @return 列车途径车站信息列表
     */
    @Override
    public List<TrainStationQueryRespDTO> listTrainStationQuery(String trainId) {
        // 使用LambdaQueryWrapper构建查询条件，根据列车ID查询
        LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, trainId);
        List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
        // 将查询结果转换为TrainStationQueryRespDTO列表返回
        return BeanUtil.convert(trainStationDOList, TrainStationQueryRespDTO.class);
    }

    /**
     * 根据列车ID、出发站和到达站查询列车途径路线。
     *
     * @param trainId     列车ID
     * @param departure   出发站
     * @param arrival     到达站
     * @return 列车途径路线信息列表
     */
    @Override
    public List<RouteDTO> listTrainStationRoute(String trainId, String departure, String arrival) {
        // 构建查询条件，根据列车ID查询，并只选择出发站信息
        LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, trainId)
                .select(TrainStationDO::getDeparture);
        List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
        // 将查询结果中的出发站信息收集为列表
        List<String> trainStationAllList = trainStationDOList.stream().map(TrainStationDO::getDeparture).collect(Collectors.toList());
        // 使用工具类计算并返回途径路线信息
        return StationCalculateUtil.throughStation(trainStationAllList, departure, arrival);
    }

    /**
     * 根据列车ID、出发站和到达站查询列车经停站路线
     *
     * @param trainId     列车ID
     * @param departure   出发站
     * @param arrival     到达站
     * @return 列车经停站路线信息列表
     */
    @Override
    public List<RouteDTO> listTakeoutTrainStationRoute(String trainId, String departure, String arrival) {
        // 构建查询条件，根据列车ID查询，并只选择出发站信息
        LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, trainId)
                .select(TrainStationDO::getDeparture);
        List<TrainStationDO> trainStationDOList = trainStationMapper.selectList(queryWrapper);
        // 将查询结果中的出发站信息收集为列表
        List<String> trainStationAllList = trainStationDOList.stream().map(TrainStationDO::getDeparture).collect(Collectors.toList());
        // 使用工具类计算并返回订餐服务中的经停站路线信息
        return StationCalculateUtil.takeoutStation(trainStationAllList, departure, arrival);
    }
}
