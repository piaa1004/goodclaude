package com.example.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.board.support.TestcontainersConfig;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class BoardApplicationTests {

    @Value("${local.server.port}")
    int port;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로딩된다")
    void contextLoads() {
    }

    @Test
    @DisplayName("업로드 한도를 넘는 multipart 요청도 code 없는 413이 아닌 415 UNSUPPORTED_MEDIA_TYPE을 반환한다")
    void returnsUnsupportedMediaTypeForLargeMultipartRequest() throws Exception {
        // given: 기본 업로드 한도(1MB)를 넘는 multipart 본문
        String boundary = "boundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n"
                + "a".repeat(2 * 1024 * 1024) + "\r\n"
                + "--" + boundary + "--\r\n";
        // 테스트 소스의 ErrorTestController(@RequestBody JSON)로 요청한다
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test/errors/body"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        // when
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        // then
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"");
    }

    @Test
    @DisplayName("Tomcat이 애플리케이션 도달 전에 거부한 요청도 HTML이 아닌 400 MALFORMED_REQUEST ProblemDetail로 응답한다")
    void returnsMalformedRequestForRequestRejectedByContainer() throws Exception {
        // given: 잘못된 퍼센트 인코딩 (HttpClient는 이런 URI를 만들 수 없어 소켓으로 직접 보낸다)
        String rawRequest = "GET /a%zz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";

        // when
        String response;
        try (Socket socket = new Socket("localhost", port)) {
            // 서버가 연결을 닫지 않는 회귀가 생겨도 빌드가 멈추지 않도록 한다
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        // then
        assertThat(response).startsWith("HTTP/1.1 400");
        assertThat(response).containsIgnoringCase("Content-Type: application/problem+json");
        assertThat(response).contains("\"code\":\"MALFORMED_REQUEST\"");
        assertThat(response).contains("요청 형식이 올바르지 않습니다.");
    }
}
