package com.porto.testecnae.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rede de seguranca do {@link GlobalExceptionHandler#handleExceptionInternal}: excecoes padrao do Spring sem
 * tratamento especifico tambem saem com {@code code}, preservando o status HTTP original.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorMapper());

    @Nested
    @DisplayName("rede de seguranca")
    class RedeDeSeguranca {

        @Test
        @DisplayName("4xx sem tratamento especifico (ServletRequestBindingException): GEN-VAL-0009 com status 400")
        void bindingException() throws Exception {
            var problem = problem(handler.handleException(new ServletRequestBindingException("header ausente"), requisicao("/api/x")));

            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0009");
            assertThat(problem.getDetail()).isEqualTo(ErrorCode.GEN_VAL_0009.getMensagem());
            assertThat(problem.getInstance()).hasToString("/api/x");
        }

        @Test
        @DisplayName("4xx diferente de 400 (413 upload grande): GEN-VAL-0009 preservando o status 413")
        void preservaStatusOriginal() throws Exception {
            var resposta = handler.handleException(new MaxUploadSizeExceededException(1024), requisicao("/api/x"));
            var problem = problem(resposta);

            assertThat(resposta.getStatusCode().value()).isEqualTo(413);
            assertThat(problem.getStatus()).isEqualTo(413);
            assertThat(problem.getTitle()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase());
            assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0009");
        }

        @Test
        @DisplayName("ErrorResponseException generica (409): GEN-VAL-0009")
        void errorResponseException() throws Exception {
            var problem = problem(handler.handleException(new ErrorResponseException(HttpStatus.CONFLICT), requisicao("/api/x")));

            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0009");
        }

        @Test
        @DisplayName("5xx sem tratamento especifico (timeout assincrono, 503): GEN-INT-0001 preservando o status")
        void cincoXX() throws Exception {
            var resposta = handler.handleException(new AsyncRequestTimeoutException(), requisicao("/api/x"));
            var problem = problem(resposta);

            assertThat(resposta.getStatusCode().value()).isEqualTo(503);
            assertThat(problem.getProperties()).containsEntry("code", "GEN-INT-0001");
            assertThat(problem.getDetail()).isEqualTo(ErrorCode.GEN_INT_0001.getMensagem());
        }

        @Test
        @DisplayName("log registra codigo, status real e tipo da excecao, sem a mensagem tecnica")
        void log(CapturedOutput output) throws Exception {
            handler.handleException(new MaxUploadSizeExceededException(1024), requisicao("/api/upload"));

            assertThat(output)
                    .contains("errorCode=GEN-VAL-0009 status=413 path=/api/upload detail=\"MaxUploadSizeExceededException\"");
        }

        @Test
        @DisplayName("resposta que ja tem code nao e alterada")
        void respostaComCodeNaoEhAlterada() {
            var original = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            original.setProperty("code", "CNAE-VAL-0006");

            var resposta = handler.handleExceptionInternal(new IllegalStateException("x"), original, new HttpHeaders(),
                    HttpStatus.BAD_REQUEST, requisicao("/api/x"));

            assertThat(resposta.getBody()).isSameAs(original);
            assertThat(original.getProperties()).containsEntry("code", "CNAE-VAL-0006");
        }

        @Test
        @DisplayName("corpo nulo tambem recebe ProblemDetail com code")
        void corpoNulo() {
            var resposta = handler.handleExceptionInternal(new IllegalStateException("x"), null, new HttpHeaders(),
                    HttpStatus.BAD_REQUEST, requisicao("/api/x"));

            assertThat(problem(resposta).getProperties()).containsEntry("code", "GEN-VAL-0009");
        }
    }

    @Nested
    @DisplayName("casos de borda dos tratamentos especificos")
    class Bordas {

        @Test
        @DisplayName("NoHandlerFoundException (rota inexistente sem recurso estatico): GEN-NEG-0001")
        void noHandlerFound() throws Exception {
            var ex = new NoHandlerFoundException("GET", "/api/naoexiste", new HttpHeaders());

            var problem = problem(handler.handleException(ex, requisicao("/api/naoexiste")));

            assertThat(problem.getStatus()).isEqualTo(404);
            assertThat(problem.getProperties()).containsEntry("code", "GEN-NEG-0001");
        }

        @Test
        @DisplayName("405 sem lista de metodos aceitos: detail so com a mensagem do catalogo")
        void metodoNaoSuportadoSemLista() throws Exception {
            var problem = problem(handler.handleException(
                    new HttpRequestMethodNotSupportedException("DELETE"), requisicao("/api/x")));

            assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0006");
            assertThat(problem.getDetail()).isEqualTo(ErrorCode.GEN_VAL_0006.getMensagem());
        }

        @Test
        @DisplayName("405 com lista: detail informa os metodos aceitos em ordem alfabetica")
        void metodoNaoSuportadoComLista() throws Exception {
            var problem = problem(handler.handleException(
                    new HttpRequestMethodNotSupportedException("DELETE", List.of("POST", "GET")), requisicao("/api/x")));

            assertThat(problem.getDetail()).endsWith("Metodos aceitos: GET, POST.");
        }

        @Test
        @DisplayName("ProblemDetail com outras propriedades, mas sem code: recebe code")
        void problemSemCode() {
            var original = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            original.setProperty("outra", "valor");

            var resposta = handler.handleExceptionInternal(new IllegalStateException("x"), original, new HttpHeaders(),
                    HttpStatus.BAD_REQUEST, requisicao("/api/x"));

            assertThat(problem(resposta).getProperties()).containsEntry("code", "GEN-VAL-0009");
        }

        @Test
        @DisplayName("ProblemDetail sem nenhuma propriedade: recebe code")
        void problemSemPropriedades() {
            var resposta = handler.handleExceptionInternal(new IllegalStateException("x"),
                    ProblemDetail.forStatus(HttpStatus.BAD_REQUEST), new HttpHeaders(), HttpStatus.BAD_REQUEST, requisicao("/api/x"));

            assertThat(problem(resposta).getProperties()).containsEntry("code", "GEN-VAL-0009");
        }

        @Test
        @DisplayName("status fora do padrao HTTP (499): code GEN-VAL-0009, status preservado e titulo nulo")
        void statusNaoPadrao() {
            var resposta = handler.handleExceptionInternal(new IllegalStateException("x"), null, new HttpHeaders(),
                    HttpStatusCode.valueOf(499), requisicao("/api/x"));
            var problem = problem(resposta);

            assertThat(problem.getStatus()).isEqualTo(499);
            assertThat(problem.getTitle()).isNull();
            assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0009");
        }
    }

    @Test
    @DisplayName("requisicao fora do contexto de servlet: path vem da descricao da WebRequest")
    void requisicaoNaoServlet() throws Exception {
        var webRequest = mock(WebRequest.class);
        when(webRequest.getDescription(false)).thenReturn("/api/fora-do-servlet");

        var problem = problem(handler.handleException(new ServletRequestBindingException("x"), webRequest));

        assertThat(problem.getInstance()).hasToString("/api/fora-do-servlet");
        assertThat(problem.getProperties()).containsEntry("code", "GEN-VAL-0009");
    }

    private static WebRequest requisicao(String uri) {
        return new ServletWebRequest(new MockHttpServletRequest("GET", uri));
    }

    private static ProblemDetail problem(ResponseEntity<Object> resposta) {
        assertThat(resposta).isNotNull();
        assertThat(resposta.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) resposta.getBody();
    }
}
