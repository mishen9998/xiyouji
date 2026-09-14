package com.xiyouji.service.room;

import com.xiyouji.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 房间码生成器：8 位去歧义大写字符（无 0/O/1/I/L），冲突时重试，最多 10 次。
 */
@Component
public class RoomCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(RoomCodeGenerator.class);

    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final RoomStore roomStore;

    public RoomCodeGenerator(RoomStore roomStore) {
        this.roomStore = roomStore;
    }

    public String generate() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
            }
            String code = sb.toString();
            if (!roomStore.codeExists(code)) {
                return code;
            }
            log.warn("Room code collision, retrying: {}", code);
        }
        // 极低概率（约 23^8 ≈ 180亿分之一单次，10次冲突后几乎不可能）
        throw new BusinessException("ROOM_CODE_GENERATION_FAILED",
                "房间码生成失败，请重试", 500);
    }
}