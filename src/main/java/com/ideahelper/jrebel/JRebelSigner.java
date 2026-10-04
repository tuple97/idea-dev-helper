package com.ideahelper.jrebel;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * JRebel 许可签名（{@code jrebelprivatekey.go} + {@code jrebelsign.go} 的 Java 移植）。
 *
 * <p>两把私钥与上游保持一致：
 * <ul>
 *   <li>{@code privateKey}（SHA1 with RSA）—— 用于 JRebel 2018.1+ 的 leases 接口；</li>
 *   <li>{@code asmPrivateKey}（MD5 with RSA）—— 用于旧版 ping/obtainTicket 等接口。</li>
 * </ul>
 */
final class JRebelSigner {

    private static final String PRIVATE_KEY =
            "-----BEGIN PRIVATE KEY-----\n"
                    + "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAND3cI/pKMSd4OLMIXU/8xoEZ/nz\n"
                    + "a+g00Vy7ygyGB1Nn83qpro7tckOvUVILJoN0pKw8J3E8rtjhSyr9849qzaQKBhxFL+J5uu08QVn/\n"
                    + "tMt+Tf0cu5MSPOjT8I2+NWyBZ6H0FjOcVrEUMvHt8sqoJDrDU4pJyex2rCOlpfBeqK6XAgMBAAEC\n"
                    + "gYBM5C+8FIxWxM1CRuCs1yop0aM82vBC0mSTXdo7/3lknGSAJz2/A+o+s50Vtlqmll4drkjJJw4j\n"
                    + "acsR974OcLtXzQrZ0G1ohCM55lC3kehNEbgQdBpagOHbsFa4miKnlYys537Wp+Q61mhGM1weXzos\n"
                    + "gCH/7e/FjJ5uS6DhQc0Y+QJBAP43hlSSEo1BbuanFfp55yK2Y503ti3Rgf1SbE+JbUvIIRsvB24x\n"
                    + "Ha1/IZ+ttkAuIbOUomLN7fyyEYLWphIy9kUCQQDSbqmxZaJNRa1o4ozGRORxR2KBqVn3EVISXqNc\n"
                    + "UH3gAP52U9LcnmA3NMSZs8tzXhUhYkWQ75Q6umXvvDm4XZ0rAkBoymyWGeyJy8oyS/fUW0G63mIr\n"
                    + "oZZ4Rp+F098P3j9ueJ2k/frbImXwabJrhwjUZe/Afel+PxL2ElUDkQW+BMHdAkEAk/U7W4Aanjpf\n"
                    + "s1+Xm9DUztFicciheRa0njXspvvxhY8tXAWUPYseG7L+iRPh+Twtn0t5nm7VynVFN0shSoCIAQJA\n"
                    + "Ljo7A6bzsvfnJpV+lQiOqD/WCw3A2yPwe+1d0X/13fQkgzcbB3K0K81Euo/fkKKiBv0A7yR7wvrN\n"
                    + "jzefE9sKUw==\n"
                    + "-----END PRIVATE KEY-----";

    private static final String ASM_PRIVATE_KEY =
            "-----BEGIN PRIVATE KEY-----\n"
                    + "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAt5yrcHAAjhglnCEn\n"
                    + "6yecMWPeUXcMyo0+itXrLlkpcKIIyqPw546bGThhlb1ppX1ySX/OUA4jSakHekNP\n"
                    + "5eWPawIDAQABAkBbr9pUPTmpuxkcy9m5LYBrkWk02PQEOV/fyE62SEPPP+GRhv4Q\n"
                    + "Fgsu+V2GCwPQ69E3LzKHPsSNpSosIHSO4g3hAiEA54JCn41fF8GZ90b9L5dtFQB2\n"
                    + "/yIcGX4Xo7bCvl8DaPMCIQDLCUN8YiXppydqQ+uYkTQgvyq+47cW2wcGumRS46dd\n"
                    + "qQIhAKp2v5e8AMj9ROFO5B6m4SsVrIkwFICw17c0WzDRxTEBAiAYDmftk990GLcF\n"
                    + "0zhV4lZvztasuWRXE+p4NJtwasLIyQIgVKzknJe8VOt5a3shCMOyysoNEg+YAt02\n"
                    + "O98RPCU0nJg=\n"
                    + "-----END PRIVATE KEY-----";

    private JRebelSigner() {
    }

    private static PrivateKey parse(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** SHA1 with RSA，用于 leases 签名，返回 base64 */
    static String signSha1Base64(String data) throws Exception {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(parse(PRIVATE_KEY));
        sig.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    /** MD5 with RSA，用于旧接口，返回原始字节的 hex 由调用方处理 */
    static byte[] signMd5(String data) throws Exception {
        Signature sig = Signature.getInstance("MD5withRSA");
        sig.initSign(parse(ASM_PRIVATE_KEY));
        sig.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return sig.sign();
    }
}
