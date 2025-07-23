package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DailyTaskExecutor {
    // 配置常量
    private static final String TRENDING_API = "https://one.phoenix.global/api/webAIBack/trendingContent";
    private static final String REACT_API = "https://one.phoenix.global/api/webAI/reactToShare";
    private static final String BASE_URL = "https://one.phoenix.global/shr/u?a=";
    private static final String TOKEN_URL = "https://one.phoenix.global/api/user/genToken";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, String> ACCOUNT_MAP = new HashMap<>();
    static {
        ACCOUNT_MAP.put("515588290@qq.com","111");
        ACCOUNT_MAP.put("uestczck@163.com","111");
        // 添加更多账号...
    }
    public static void main(String[] args) {
        executeDailyTask();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // 计算到凌晨1点的延迟时间（每天执行）
        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toSeconds(1); // 24小时周期

        scheduler.scheduleAtFixedRate(
                DailyTaskExecutor::executeDailyTask,
                initialDelay,
                period,
                TimeUnit.SECONDS
        );
    }

    private static long calculateInitialDelay() {
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(1, 0); // 凌晨1点执行
        return now.isBefore(target)
                ? Duration.between(now, target).getSeconds()
                : Duration.between(now, target).plusHours(24).getSeconds();
    }

    private static void executeDailyTask() {
        System.out.println("🏁 开始执行每日任务: " + java.time.LocalDateTime.now());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//            JsonNode tokenData = getToken(httpClient,"515588290@qq.com","111");
//            System.out.println("tokenData " + tokenData);
//            String tokenStr = tokenData.asText();
//            System.out.println("tokenStr " + tokenStr);

            for (Map.Entry<String, String> entry : ACCOUNT_MAP.entrySet()) {
                String email = entry.getKey();
                String password = entry.getValue();
                JsonNode tokenData = getToken(httpClient, email, password);
                System.out.println("email " + email +"tokenData:"+tokenData);
                String tokenStr = tokenData.asText();

                // 1. 获取趋势内容数据
                JsonNode trendingData = fetchTrendingData(httpClient);
                if (trendingData == null) return;

                // 2. 处理数据并发送请求
                processConversationIds(httpClient, trendingData,tokenStr);
            }
        } catch (Exception e) {
            System.err.println("⚠️ 任务执行异常: " + e.getMessage());
        }
    }

    private static JsonNode fetchTrendingData(CloseableHttpClient httpClient) {
        HttpGet request = new HttpGet(TRENDING_API);
        try {
            HttpResponse response = httpClient.execute(request);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            JsonNode rootNode = mapper.readTree(jsonResponse);

            if (!rootNode.has("data")) {
                System.err.println("❌ JSON缺少data字段");
                return null;
            }
            return rootNode.get("data");
        } catch (Exception e) {
            System.err.println("🔴 获取趋势数据失败: " + e.getMessage());
            return null;
        }
    }

    private static void processConversationIds(CloseableHttpClient httpClient, JsonNode dataArray,String tokenStr) throws InterruptedException {
        for (JsonNode item : dataArray) {
            if (!item.has("share_id")) continue;

            String conversationId = item.get("share_id").asText();
            System.out.println("\n🔗 处理会话ID: " + BASE_URL + conversationId);

            // 发送点赞请求
            sendReactionRequest(httpClient, conversationId,tokenStr);
            Thread.sleep(20000); // 20 秒 = 20000 毫秒
        }
    }

    private static void sendReactionRequest(CloseableHttpClient httpClient, String conversationId,String tokenStr) {
        HttpPost postRequest = new HttpPost(REACT_API);
        postRequest.addHeader("Content-Type", "application/json");
        postRequest.addHeader("token", tokenStr);

        try {
            String jsonBody = String.format(
                    "{\"action\":\"thumb\",\"share_id\":\"%s\"}",
                    conversationId
            );
            postRequest.setEntity(new StringEntity(jsonBody));

            HttpResponse response = httpClient.execute(postRequest);
            System.out.println("✅ 响应状态: " + response.getStatusLine());
            System.out.println("   响应内容: " + EntityUtils.toString(response.getEntity()));
        } catch (Exception e) {
            System.err.println("⛔ 请求失败: " + e.getMessage());
        }
    }

    private static JsonNode getToken(CloseableHttpClient httpClient, String userName,String passWd) {
        HttpPost postRequest = new HttpPost(TOKEN_URL);
        postRequest.addHeader("Content-Type", "application/json");

        JsonNode rootNode = null;
        try {
            String jsonBody = String.format(
                    "{\"email\":\"%s\",\"passwd\":\"%s\"}",
                    userName, passWd
            );
            postRequest.setEntity(new StringEntity(jsonBody));

            HttpResponse response = httpClient.execute(postRequest);
            String jsonResponse = EntityUtils.toString(response.getEntity());

//            System.out.println("✅ getToken响应状态: " + response.getStatusLine());

//            System.out.println("   getToken响应内容: " + EntityUtils.toString(response.getEntity()));
            System.out.println("   jsonResponse: " + jsonResponse);
            rootNode = mapper.readTree(jsonResponse);
            System.out.println("   rootNode: " + rootNode);

            if (!rootNode.has("token")) {
                System.err.println("❌ JSON缺少token字段");
                return null;
            }
            return rootNode.get("token");
        } catch (Exception e) {
            System.err.println("⛔ getToken请求失败: " + e.getMessage());
            return null;
        }
    }
}