
package org.opengoofy.index12306.biz.ticketservice.toolkit;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * 匹配剩余的座位工具类
 */
public final class SurplusNeedMatchSeatUtil {


    /**
     * 从空闲座位队列中获取足够数量的座位。
     * 此方法旨在从给定的空闲座位队列中，找出能够满足特定人数需求的座位组合。
     * 它首先尝试找到一个单一的座位列表，其长度至少等于所需座位数，如果找不到，
     * 则通过合并多个座位列表来满足需求。
     *
     * @param chooseSeatSize  需要选择的座位数量。
     * @param vacantSeatQueue 空闲座位的优先队列。
     * @return 包含选定座位的列表。如果无法满足所需座位数，则返回尽可能多的座位。
     */
    public static List<Pair<Integer, Integer>> getSurplusNeedMatchSeat(int chooseSeatSize, PriorityQueue<List<Pair<Integer, Integer>>> vacantSeatQueue) {
        // 尝试找到一个长度至少为chooseSeatSize的座位列表
        Optional<List<Pair<Integer, Integer>>> optionalList = vacantSeatQueue.parallelStream().filter(each -> each.size() >= chooseSeatSize).findFirst();
        if (optionalList.isPresent()) {
            // 如果找到，返回该列表的前chooseSeatSize个座位
            return optionalList.get().subList(0, chooseSeatSize);
        }
        // 如果没有找到满足条件的单个列表，需要合并多个列表来满足需求
        List<Pair<Integer, Integer>> result = new ArrayList<>(chooseSeatSize);
        while (CollUtil.isNotEmpty(vacantSeatQueue)) {
            List<Pair<Integer, Integer>> pairList = vacantSeatQueue.poll();
            if (result.size() + pairList.size() < chooseSeatSize) {
                // 如果当前结果加上新的座位列表仍然不足以满足需求，将新座位列表全部添加到结果中
                result.addAll(pairList);
            } else if (result.size() + pairList.size() >= chooseSeatSize) {
                // 如果当前结果加上新的座位列表可以满足或超过需求，只添加满足需求的额外座位
                int needPairListLen = pairList.size() - (result.size() + pairList.size() - chooseSeatSize);
                result.addAll(pairList.subList(0, needPairListLen));
                // 如果已经满足了所需座位数，结束循环
                if (result.size() == chooseSeatSize) {
                    break;
                }
            }
        }
        return result;
    }

}
