package com.paicli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OllamaClient extends AbstractOpenAiCompatibleClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llava:13b";
    private final String model;
    private final String apiUrl;

    public OllamaClient() {
        this(null, null);
    }

    public OllamaClient(String model, String baseUrl) {
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.apiUrl = toNativeChatUrl(baseUrl);
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getApiKey() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    protected boolean supportsVision() {
        return true;
    }

    @Override
    public int maxContextWindow() {
        return 4_096;
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
        return chat(messages, tools, StreamListener.NO_OP);
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
        StreamListener streamListener = listener == null ? StreamListener.NO_OP : listener;
        String json = buildOllamaRequestBody(messages);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = SHARED_HTTP_CLIENT.newCall(request).execute()) {
            ResponseBody responseBodyObj = response.body();
            if (!response.isSuccessful()) {
                String errorBody = responseBodyObj != null ? responseBodyObj.string() : "无响应体";
                throw new IOException("Ollama API请求失败: " + response.code() + " - " + errorBody);
            }
            if (responseBodyObj == null) {
                throw new IOException("Ollama API返回空响应体");
            }

            BufferedSource source = responseBodyObj.source();
            StringBuilder content = new StringBuilder();
            int promptEvalCount = 0;
            int evalCount = 0;

            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) break;
                if (line.isBlank()) continue;

                JsonNode root = mapper.readTree(line);

                if (root.has("prompt_eval_count")) {
                    promptEvalCount = root.get("prompt_eval_count").asInt();
                }
                if (root.has("eval_count")) {
                    evalCount = root.get("eval_count").asInt();
                }

                JsonNode message = root.get("message");
                if (message != null && message.has("content")) {
                    String delta = message.get("content").asText();
                    if (!delta.isEmpty()) {
                        content.append(delta);
                        streamListener.onContentDelta(delta);
                    }
                }

                if (root.has("done") && root.get("done").asBoolean()) {
                    break;
                }
            }

            return new ChatResponse("assistant", content.toString(), null, promptEvalCount, evalCount);
        }
    }

    private String buildOllamaRequestBody(List<Message> messages) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        ArrayNode msgs = body.putArray("messages");
        for (Message msg : messages) {
            ObjectNode msgNode = msgs.addObject();
            msgNode.put("role", msg.role());

            if (msg.hasContentParts()) {
                StringBuilder text = new StringBuilder();
                List<String> images = new ArrayList<>();
                for (ContentPart part : msg.contentParts()) {
                    if (part == null) continue;
                    if (part.isText() && part.text() != null) {
                        text.append(part.text());
                    } else if (part.isImage() && "image_base64".equals(part.type()) && part.imageBase64() != null) {
                        images.add(part.imageBase64());
                    }
                }
                msgNode.put("content", text.toString());
                if (!images.isEmpty()) {
                    ArrayNode imgArray = msgNode.putArray("images");
                    for (String img : images) {
                        imgArray.add(img);
                    }
                }
            } else {
                msgNode.put("content", msg.content() != null ? msg.content() : "");
            }
        }
        return body.toString();
    }

    private static String toNativeChatUrl(String baseUrl) {
        String normalized = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : DEFAULT_BASE_URL;
        String withoutSlash = normalized.replaceAll("/+$", "");
        if (withoutSlash.endsWith("/api/chat")) return withoutSlash;
        if (withoutSlash.endsWith("/api")) return withoutSlash + "/chat";
        return withoutSlash + "/api/chat";
    }
}
