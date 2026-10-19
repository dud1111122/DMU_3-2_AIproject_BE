package com.dmu.dshorts.global.ai;

/** One chat message sent to the AI provider. Build prompts in your own feature package. */
public record AiMessage(Role role, String content) {

    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant");

        private final String wireName;

        Role(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public static AiMessage system(String content) {
        return new AiMessage(Role.SYSTEM, content);
    }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content);
    }
}
