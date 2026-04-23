package com.agent.report.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class WeatherTool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(name = "get_weather", description = "获取指定城市的天气信息")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，例如：北京、上海") String city) {
        
        if (city == null || city.isEmpty()) {
            return "请输入有效的城市名称";
        }

        try {
            // 1. 获取城市的经纬度 (使用 Open-Meteo 的 Geocoding API)
            String geoUrl = String.format("https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=zh&format=json", city);
            Map<String, Object> geoResponse = restTemplate.getForObject(geoUrl, Map.class);
            
            if (geoResponse == null || !geoResponse.containsKey("results")) {
                return String.format("未找到城市 '%s' 的位置信息", city);
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) geoResponse.get("results");
            if (results.isEmpty()) {
                return String.format("未找到城市 '%s' 的位置信息", city);
            }

            Map<String, Object> location = results.get(0);
            double lat = (double) location.get("latitude");
            double lon = (double) location.get("longitude");
            String cityName = (String) location.get("name");

            // 2. 根据经纬度获取实时天气 (使用 Open-Meteo Weather API)
            String weatherUrl = String.format("https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true", lat, lon);
            Map<String, Object> weatherResponse = restTemplate.getForObject(weatherUrl, Map.class);

            if (weatherResponse == null || !weatherResponse.containsKey("current_weather")) {
                return String.format("无法获取 '%s' 的天气数据", city);
            }

            Map<String, Object> currentWeather = (Map<String, Object>) weatherResponse.get("current_weather");
            double temperature = (double) currentWeather.get("temperature");
            double windspeed = (double) currentWeather.get("windspeed");
            int weatherCode = (int) currentWeather.get("weathercode");

            // 简单的天气代码转换
            String weatherDesc = translateWeatherCode(weatherCode);

            return String.format("%s当前天气：%s，气温 %.1f 摄氏度，风速 %.1f km/h。", 
                    cityName, weatherDesc, temperature, windspeed);

        } catch (Exception e) {
            return "获取天气信息时发生错误：" + e.getMessage();
        }
    }

    private String translateWeatherCode(int code) {
        return switch (code) {
            case 0 -> "晴朗";
            case 1, 2, 3 -> "多云";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 61, 63, 65 -> "阵雨";
            case 71, 73, 75 -> "降雪";
            case 80, 81, 82 -> "强阵雨";
            case 95, 96, 99 -> "雷阵雨";
            default -> "未知天气状态";
        };
    }
}
