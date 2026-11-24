package example.micronaut;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Singleton
final class FixtureAuthentication implements HttpRequestAuthenticationProvider<Object> {
    private final String writerPassword;
    private final String readerPassword;

    FixtureAuthentication(
            @Value("${woge.spike.writer-password:}") String writerPassword,
            @Value("${woge.spike.reader-password:}") String readerPassword
    ) {
        this.writerPassword = writerPassword;
        this.readerPassword = readerPassword;
    }

    @Override
    public AuthenticationResponse authenticate(
            HttpRequest<Object> request,
            AuthenticationRequest<String, String> credentials
    ) {
        String identity = credentials.getIdentity();
        String expected = switch (identity) {
            case "writer" -> writerPassword;
            case "reader" -> readerPassword;
            default -> "";
        };
        if (expected.isEmpty() || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), credentials.getSecret().getBytes(StandardCharsets.UTF_8))) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }
        return AuthenticationResponse.success(identity, identity.equals("writer") ? List.of("todo:write") : List.of());
    }
}
