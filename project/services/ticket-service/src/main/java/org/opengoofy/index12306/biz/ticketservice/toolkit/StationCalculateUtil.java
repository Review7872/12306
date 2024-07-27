
package org.opengoofy.index12306.biz.ticketservice.toolkit;

import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 站点计算工具
 */
public final class StationCalculateUtil {


    /**
     * 根据起始站和终点站，从给定的车站列表中找出所有可能的通过站路线。
     * 通过站路线由起始站和一个或多个中间站组成，直到终点站。
     *
     * @param stations 车站名称的列表。
     * @param startStation 起始车站名称。
     * @param endStation 终点车站名称。
     * @return 包含所有可能通过站路线的列表。如果不存在这样的路线，返回空列表。
     */
    public static List<RouteDTO> throughStation(List<String> stations, String startStation, String endStation) {
        // 初始化一个列表，用于存储所有可能的通过站路线。
        List<RouteDTO> routesToDeduct = new ArrayList<>();
        // 查找起始站和终点站在车站列表中的索引。
        int startIndex = stations.indexOf(startStation);
        int endIndex = stations.indexOf(endStation);
        // 如果起始站或终点站不存在于车站列表中，或者起始站的索引大于等于终点站的索引，则不存在通过站路线，直接返回空列表。
        if (startIndex < 0 || endIndex < 0 || startIndex >= endIndex) {
            return routesToDeduct;
        }
        // 遍历起始站到终点站之间的车站，找出所有可能的通过站路线。
        for (int i = startIndex; i < endIndex; i++) {
            for (int j = i + 1; j <= endIndex; j++) {
                // 获取当前站和下一个站的名称。
                String currentStation = stations.get(i);
                String nextStation = stations.get(j);
                // 创建一个表示当前路线的DTO对象，并添加到结果列表中。
                RouteDTO routeDTO = new RouteDTO(currentStation, nextStation);
                routesToDeduct.add(routeDTO);
            }
        }
        // 返回包含所有可能通过站路线的列表。
        return routesToDeduct;
    }


    /**
     * 计算出发站和终点站需要扣减余票的站点（包含出发站和终点站）
     * 该方法通过遍历站点列表，找出所有包含起始站和终点站的子路线。如果起始站或终点站不存在于列表中，或者起始站位于终点站之后，则返回空列表。
     * 方法首先处理从列表开始到起始站的路线，然后处理从起始站到终点站的路线。
     *
     * @param stations 站点列表，代表所有路线节点。
     * @param startStation 起始站。
     * @param endStation 终点站。
     * @return 需要扣减余票的站点
     */
    public static List<RouteDTO> takeoutStation(List<String> stations, String startStation, String endStation) {
        List<RouteDTO> takeoutStationList = new ArrayList<>();
        int startIndex = stations.indexOf(startStation);
        int endIndex = stations.indexOf(endStation);

        // 检查起始站和终点站是否存在，以及起始站是否在终点站之前
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return takeoutStationList;
        }

        // 如果起始站不是列表的第一个站点，则处理从列表开始到起始站的路线
        if (startIndex != 0) {
            for (int i = 0; i < startIndex; i++) {
                for (int j = 1; j < stations.size() - startIndex; j++) {
                    takeoutStationList.add(new RouteDTO(stations.get(i), stations.get(startIndex + j)));
                }
            }
        }

        // 处理从起始站到终点站的路线
        for (int i = startIndex; i <= endIndex; i++) {
            for (int j = i + 1; j < stations.size() && i < endIndex; j++) {
                takeoutStationList.add(new RouteDTO(stations.get(i), stations.get(j)));
            }
        }

        return takeoutStationList;
    }

    public static void main(String[] args) {
        List<String> stations = Arrays.asList("北京南", "济南西", "南京南", "杭州东", "宁波");
        String startStation = "济南西";
        String endStation = "南京南";
        StationCalculateUtil.takeoutStation(stations, startStation, endStation).forEach(System.out::println);
    }
}
