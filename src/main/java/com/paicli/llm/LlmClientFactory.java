package com.paicli.llm;

import com.paicli.config.PaiCliConfig;

public class LlmClientFactory {

    private LlmClientFactory() {}

    public static LlmClient create(String provider, PaiCliConfig config) {
        if (provider == null) return null;

        String normalized = normalizeProvider(provider);
        String configuredProvider = provider.trim().toLowerCase();

        // llava / ollama 走本地 OllamaClient，无需 API Key
        if ("llava".equals(normalized) || "ollama".equals(normalized)) {
            String model = firstConfigured(config.getModel("llava"),
                    config.getModel("ollama"));
            String baseUrl = firstConfigured(config.getBaseUrl("llava"),
                    config.getBaseUrl("ollama"));
            return new OllamaClient(model, baseUrl);
        }

        String apiKey = config.getApiKey(normalized);
        if ((apiKey == null || apiKey.isBlank()) && !configuredProvider.equals(normalized)) {
            apiKey = config.getApiKey(configuredProvider);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String model = firstConfigured(config.getModel(normalized),
                configuredProvider.equals(normalized) ? null : config.getModel(configuredProvider));
        String baseUrl = firstConfigured(config.getBaseUrl(normalized),
                configuredProvider.equals(normalized) ? null : config.getBaseUrl(configuredProvider));

        return switch (normalized) {
            case "glm" -> new GLMClient(apiKey, model);
            case "deepseek" -> new DeepSeekClient(apiKey, model);
            case "step" -> new StepClient(apiKey, model, baseUrl);
            case "kimi" -> new KimiClient(apiKey, model, baseUrl);
            default -> null;
        };
    }

    public static LlmClient createFromConfig(PaiCliConfig config) {
        LlmClient client = create(config.getDefaultProvider(), config);

        // 本地 provider (llava/ollama) 不需要 API Key，总能创建成功；
        // 如果用户配置了云端 API Key，应优先使用云端 provider。
        if (client != null && !isLocalOnly(config.getDefaultProvider())) {
            return client;
        }

        // 已保存默认是本地 provider 或无默认 → 优先探测云端 provider
        for (String provider : new String[]{"deepseek", "glm", "step", "kimi"}) {
            LlmClient cloudClient = create(provider, config);
            if (cloudClient != null) {
                config.setDefaultProvider(provider);
                config.save();
                return cloudClient;
            }
        }

        // 没有可用云端 provider → 回退本地 llava
        if (client != null) return client;

        // 兜底：llava 也没保存过，显式创建
        client = create("llava", config);
        if (client != null) {
            return client;
        }

        return null;
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider.trim().toLowerCase();
        return switch (normalized) {
            case "stepfun", "step-fun" -> "step";
            case "moonshot", "moonshotai", "moonshot-ai" -> "kimi";
            case "ollama" -> "llava";
            default -> normalized;
        };
    }

    private static boolean isLocalOnly(String provider) {
        String normalized = normalizeProvider(provider);
        return "llava".equals(normalized);
    }

    private static String firstConfigured(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
