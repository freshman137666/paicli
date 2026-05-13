package com.paicli.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class DeepSeekClient extends AbstractOpenAiCompatibleClient {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private final String apiKey;
    private final String model;

    public DeepSeekClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public DeepSeekClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    @Override
    protected String getApiUrl() {
        return API_URL;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    protected boolean shouldSendReasoningContentInRequestHistory() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "deepseek";
    }

    @Override
    protected boolean supportsVision() {
        return false; // DeepSeek 云端 API 目前不支持图片/多模态输入
    }

    @Override
    protected void appendMessageContent(ObjectNode msgNode, Message msg) {
        if (msg.hasImageContent()) {
            int count = msg.imagePartCount();
            StringBuilder sb = new StringBuilder(msg.content());
            sb.append("\n\n[模型限制] DeepSeek 云端 API 当前不支持图片/视觉输入，已省略 ")
                    .append(count).append(" 张图片。");
            sb.append("\n如需图片识别，请使用 /model glm-5v-turbo 切换到智谱 GLM-5V-Turbo 多模态模型。");
            msgNode.put("content", sb.toString());
            return;
        }
        super.appendMessageContent(msgNode, msg);
    }

    @Override
    public int maxContextWindow() {
        return 1_000_000;
    }

    @Override
    public boolean supportsPromptCaching() {
        return true;
    }

    @Override
    public String promptCacheMode() {
        return "automatic-prefix-cache";
    }

}
