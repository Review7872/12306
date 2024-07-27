
package org.opengoofy.index12306.biz.ticketservice.toolkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 座位转换工具类
 */
public final class ChooseSeatUtil {

    public static final String TRAIN_BUSINESS = "TRAIN_BUSINESS";
    public static final String TRAIN_FIRST = "TRAIN_FIRST";
    public static final String TRAIN_SECOND = "TRAIN_SECOND";

    interface StrPool {
        String A = "A";
        String B = "B";
        String C = "C";
        String D = "D";
        String F = "F";
    }


    /**
     * 根据座位标记和选座列表，转换为实际选座情况的映射。
     *
     * @param mark 列车座位等级标记，决定如何解析座位列表。
     * @param chooseSeatList 座位选择的列表，每个元素代表一个座位选择。
     * @return 返回一个映射，其中键表示座位类型，值表示该类型座位的选择数量。
     */
    public static HashMap<Integer,Integer> convert(String mark, List<String> chooseSeatList) {
        // 初始化实际选座情况的映射，初始容量为8。
        HashMap<Integer, Integer> actualChooseSeatMap = new HashMap<>(8);

        // 将座位列表按照座位类型的第一字符分组。
        Map<String, List<String>> chooseSeatMap = chooseSeatList
                .stream()
                .collect(Collectors.groupingBy(seat -> seat.substring(0, 1)));

        // 遍历分组后的映射，根据不同的mark值和key值，将座位类型和选择数量映射到实际映射中。
        chooseSeatMap.forEach((key, value) -> {
            switch (mark) {
                case TRAIN_BUSINESS -> {
                    // 商务座的座位类型和映射关系。
                    switch (key) {
                        case StrPool.A -> actualChooseSeatMap.put(0, value.size());
                        case StrPool.C -> actualChooseSeatMap.put(1, value.size());
                        case StrPool.F -> actualChooseSeatMap.put(2, value.size());
                    }
                }
                case TRAIN_FIRST -> {
                    // 一等座的座位类型和映射关系。
                    switch (key) {
                        case StrPool.A -> actualChooseSeatMap.put(0, value.size());
                        case StrPool.C -> actualChooseSeatMap.put(1, value.size());
                        case StrPool.D -> actualChooseSeatMap.put(2, value.size());
                        case StrPool.F -> actualChooseSeatMap.put(3, value.size());
                    }
                }
                case TRAIN_SECOND -> {
                    // 二等座的座位类型和映射关系。
                    switch (key) {
                        case StrPool.A -> actualChooseSeatMap.put(0, value.size());
                        case StrPool.B -> actualChooseSeatMap.put(1, value.size());
                        case StrPool.C -> actualChooseSeatMap.put(2, value.size());
                        case StrPool.D -> actualChooseSeatMap.put(3, value.size());
                        case StrPool.F -> actualChooseSeatMap.put(4, value.size());
                    }
                }
            }
        });
        return actualChooseSeatMap;
    }

}
