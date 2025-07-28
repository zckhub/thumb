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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class DailyTaskExecutor {
    // 配置常量
    private static final String TRENDING_API = "https://one.phoenix.global/api/webAIBack/trendingContent";
    private static final String REACT_API = "https://one.phoenix.global/api/webAI/reactToShare";
    private static final String BASE_URL = "https://one.phoenix.global/shr/u?a=";
    private static final String TOKEN_URL = "https://one.phoenix.global/api/user/genToken";
    private static final String GetAllConversation_URL = "https://one.phoenix.global/api/webAI/getAllConversation?limit=22&offset=0";
    private static final String GEN_ARTICLE = "https://one.phoenix.global/api/webAI/generateArticleWithPrompt";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, String> ACCOUNT_MAP = new HashMap<>();
    static {
        ACCOUNT_MAP.put("515588290@qq.com","111");
        ACCOUNT_MAP.put("uestczck@163.com","111");
        // 添加更多账号...
    }
    public static void main(String[] args) {
        executeDailyTask();
//        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//
//        // 计算到凌晨1点的延迟时间（每天执行）
//        long initialDelay = calculateInitialDelay();
//        long period = TimeUnit.DAYS.toSeconds(1); // 24小时周期
//
//        scheduler.scheduleAtFixedRate(
//                DailyTaskExecutor::executeDailyTask,
//                initialDelay,
//                period,
//                TimeUnit.SECONDS
//        );
    }

    private static long calculateInitialDelay() {
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(1, 0); // 凌晨1点执行
        return now.isBefore(target)
                ? Duration.between(now, target).getSeconds()
                : Duration.between(now, target).plusHours(24).getSeconds();
    }

    private static void executeDailyTask() {
        System.out.println("🏁 开始执行每日任务: " + LocalDateTime.now());
        List<String> shareIDList = new ArrayList<>();
        List<String> tokenList = new ArrayList<>();

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
                tokenList.add(tokenStr);


                //给排行榜上内容点赞 1. 获取趋势内容数据
                JsonNode trendingData = fetchTrendingData(httpClient);
                if (trendingData == null) return;
                // 2. 处理数据并发送请求
                processConversationIds(httpClient, trendingData,tokenStr);


                // 对用户的conversation生成新的文章，获取到share_id
                JsonNode conversationAll = getConversation(httpClient,tokenStr);
                System.err.println("🔴 conversationAll: " + conversationAll);
                List<String> tmpstareList = genArticle(httpClient, tokenStr, conversationAll);
                System.err.println("🔴 tmpstareList: " + tmpstareList);
                shareIDList.addAll(tmpstareList);
            }

            for(String token:tokenList){
                //对所有share_id进行点赞TODO 这里可以手动加内容
                List<String> shareRecordList = Arrays.asList("asdfadsf","dsafsda");
                shareIDList.addAll(shareRecordList);
                System.err.println("🔴 shareIDList: " + shareIDList);
                thumbAllShareID(httpClient,shareIDList,token);
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
    //https://one.phoenix.global/api/webAI/getAllConversation?limit=22&offset=0
    private static JsonNode getConversation(CloseableHttpClient httpClient, String tokenStr) {
        HttpGet request = new HttpGet(GetAllConversation_URL);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("token", tokenStr);

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
    //https://one.phoenix.global/api/webAI/generateArticleWithPrompt  res:"share_id": "dk92uIgqM18466"
    private static List<String> genArticle(CloseableHttpClient httpClient, String tokenStr, JsonNode conversationAll){
        List<String> resList = new ArrayList<>();
        for (JsonNode group : conversationAll) {
            String question = "";
            List<String> messageList = new ArrayList<>();
            String answer = "";
            String conversationId = "";
            for(JsonNode item:group){
                if (!item.has("conversation_id")) continue;

                conversationId = item.get("conversation_id").asText();
                messageList.add(item.get("question").asText());

                messageList.add(item.get("answer").asText());
                question = question + item.get("question").asText();
                answer = answer+item.get("answer").asText();

//                System.out.println("\n🔗 question "+question);
//                System.out.println("\n🔗 answer "+answer);
//                System.out.println("\n🔗 messageList "+messageList);
            }
            // 生成文章
            System.out.println("\n🔗 conversationId "+conversationId);

            HttpPost postRequest = new HttpPost(GEN_ARTICLE);
            postRequest.addHeader("Content-Type", "application/json");
            postRequest.addHeader("token", tokenStr);

            try {
//                String jsonBody = String.format(
//                        "{\"conversation_id\":\"%s\",\"messages\":\"%s\",\"prompt_style\":\"default\",\"user_prompt\":\"\"}",
//                        conversationId,messageList
//                );
//                postRequest.setEntity(new StringEntity(jsonBody));
                // 使用Jackson库
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> jsonBodyMap = new HashMap<>();
                jsonBodyMap.put("conversation_id", conversationId);
                jsonBodyMap.put("messages", messageList); // 直接使用String[]
                jsonBodyMap.put("prompt_style", "default");
                jsonBodyMap.put("user_prompt", "");
                String jsonBody = mapper.writeValueAsString(jsonBodyMap);
                postRequest.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

                HttpResponse response = httpClient.execute(postRequest);
                System.out.println("✅ 响应状态: " + response.getStatusLine());
                String jsonResponse = EntityUtils.toString(response.getEntity());
                String share_id = String.valueOf(mapper.readTree(jsonResponse).get("share_id")).replace("\"", "");
                resList.add(share_id);
//                System.out.println("   响应内容: " + EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                System.err.println("⛔ 请求失败: " + e.getMessage());
            }

        }
        return resList;
    }
    private static void thumbAllShareID(CloseableHttpClient httpClient, List<String> shareIDList,String tokenStr) throws InterruptedException {
        for (String result : shareIDList) {
//            String result = item.replace("\"", "");  // 移除所有双引号
            System.out.println("\n🔗 处理会话ID: " + BASE_URL + result);
            // 发送点赞请求
            sendReactionRequest(httpClient, result, tokenStr);
            Thread.sleep(20000); // 20 秒 = 20000 毫秒
        }
    }

//    private static void sendReactionRequest(CloseableHttpClient httpClient, String conversationId,String tokenStr) {
//        HttpPost postRequest = new HttpPost(REACT_API);
//        postRequest.addHeader("Content-Type", "application/json");
//        postRequest.addHeader("token", tokenStr);
//
//        try {
//            String jsonBody = String.format(
//                    "{\"action\":\"thumb\",\"share_id\":\"%s\"}",
//                    conversationId
//            );
//            postRequest.setEntity(new StringEntity(jsonBody));
//
//            HttpResponse response = httpClient.execute(postRequest);
//            System.out.println("✅ 响应状态: " + response.getStatusLine());
//            System.out.println("   响应内容: " + EntityUtils.toString(response.getEntity()));
//        } catch (Exception e) {
//            System.err.println("⛔ 请求失败: " + e.getMessage());
//        }
//    }
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