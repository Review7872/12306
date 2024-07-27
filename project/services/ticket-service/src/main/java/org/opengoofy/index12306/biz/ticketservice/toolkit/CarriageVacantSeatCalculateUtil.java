
package org.opengoofy.index12306.biz.ticketservice.toolkit;

import cn.hutool.core.lang.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 座位统计工具类
 */
public final class CarriageVacantSeatCalculateUtil {

    /**
     * 座位统计方法
     * 构建一个优先队列，其中包含火车车厢中空余座位的列表。
     * 该优先队列按照空余座位的数量进行排序，座位越多的列表优先级越高。
     *
     * @param actualSeats 表示火车车厢实际座位占用情况的二维数组，0表示空余座位，1表示占用座位。
     * @param n 表示火车车厢的行数。
     * @param m 表示火车车厢的列数。
     * @return 返回一个优先队列，其中每个元素是一个包含空余座位坐标的列表。空余座位集合小根堆
     */
    public static PriorityQueue<List<Pair<Integer, Integer>>> buildCarriageVacantSeatList(int[][] actualSeats, int n, int m) {
        // 使用优先队列按照空余座位数量对列表进行排序
        PriorityQueue<List<Pair<Integer, Integer>>> vacantSeatQueue = new PriorityQueue<>(Comparator.comparingInt(List::size));

        // 遍历车厢中的每个座位
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                // 当遇到空余座位时
                if (actualSeats[i][j] == 0) {
                    List<Pair<Integer, Integer>> res = new ArrayList<>();
                    // 从当前座位开始，直到遇到占用座位为止，将空余座位的坐标添加到列表中
                    int k = j;
                    for (; k < m; k++) {
                        if (actualSeats[i][k] == 1) break;
                        res.add(new Pair<>(i, k));
                    }
                    // 更新内部循环的索引，以避免重复检查已知占用的座位
                    j = k;
                    // 将包含空余座位坐标的列表添加到优先队列中
                    vacantSeatQueue.add(res);
                }
            }
        }
        // 返回包含所有空余座位列表的优先队列
        return vacantSeatQueue;
    }


    /**
     * 空余座位统计方法
     * 构建一个空余座位列表。
     * 该方法通过遍历给定的座位布局数组，找出所有空闲座位的坐标，并将这些坐标以Pair的形式添加到一个列表中。
     *
     * @param actualSeats 座位布局数组，其中0表示空闲座位，1表示已被占用的座位。
     * @param n 表示座位数组的行数。
     * @param m 表示座位数组的列数。
     * @return 返回一个包含所有空闲座位坐标的Pair列表。空余座位集合
     */
    public static List<Pair<Integer, Integer>> buildCarriageVacantSeatList2(int[][] actualSeats, int n, int m) {
        // 初始化一个空闲座位列表，预计最多包含16个座位。
        List<Pair<Integer, Integer>> vacantSeatList = new ArrayList<>(16);

        // 遍历座位数组的每一行和每一列。
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                // 如果当前位置的座位是空闲的（值为0），则将其坐标添加到空闲座位列表中。
                if (actualSeats[i][j] == 0) {
                    vacantSeatList.add(new Pair<>(i, j));
                }
            }
        }

        // 返回包含所有空闲座位坐标的列表。
        return vacantSeatList;
    }

}
