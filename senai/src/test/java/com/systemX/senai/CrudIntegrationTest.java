package com.systemX.senai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrudIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    @Test
    void deveExecutarCrudDosCadastrosComDependencias() throws Exception {
        Usuario admin = new Usuario();
        admin.setNome("Admin CRUD");
        admin.setEmail("admin-crud@teste.com");
        admin.setSenha(encoder.encode("123456"));
        admin.setTipo(TipoUsuario.ADMINISTRADOR);
        String token = "Bearer " + jwt.generate(usuarios.save(admin));
        long tipo = criar("tipos-consorcio", "{\"nome\":\"Tipo CRUD\"}", token);
        long consorcio = criar("consorcios", "{\"nome\":\"Consorcio CRUD\",\"tipoId\":" + tipo + "}", token);
        long grupo = criar("grupos", "{\"numero\":\"GR-CRUD\",\"consorcioId\":" + consorcio + ",\"quantidadeCotas\":10,\"valorParcela\":100}", token);
        long cota = criar("cotas", "{\"numero\":\"C-CRUD\",\"grupoId\":" + grupo + ",\"valorCredito\":1000}", token);
        long usuario = criar("usuarios", "{\"nome\":\"Cliente CRUD\",\"email\":\"cliente-crud@teste.com\",\"senha\":\"123456\",\"tipo\":\"CLIENTE\"}", token);
        var cliente = usuarios.findById(usuario).orElseThrow();
        String tokenAntigo = "Bearer " + jwt.generate(cliente);
        mvc.perform(put("/api/usuarios/" + usuario + "/senha").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"nova-senha-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/portal/processos").header("Authorization", tokenAntigo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/consorcios/pagina?size=1&q=CRUD").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(consorcio));
        verificar("usuarios", usuario, "{\"nome\":\"Cliente editado\",\"email\":\"editado-crud@teste.com\",\"tipo\":\"CLIENTE\",\"ativo\":true}", "nome", "Cliente editado", token);
        verificar("cotas", cota, "{\"numero\":\"C-EDITADA\",\"grupoId\":" + grupo + ",\"valorCredito\":2000}", "numero", "C-EDITADA", token);
        verificar("grupos", grupo, "{\"numero\":\"GR-EDITADO\",\"consorcioId\":" + consorcio + "}", "numero", "GR-EDITADO", token);
        verificar("consorcios", consorcio, "{\"nome\":\"Consorcio editado\",\"tipoId\":" + tipo + "}", "nome", "Consorcio editado", token);
        verificar("tipos-consorcio", tipo, "{\"nome\":\"Tipo editado\"}", "nome", "Tipo editado", token);
        mvc.perform(post("/api/grupos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cotas")).andExpect(status().isUnauthorized());
    }

    private long criar(String recurso, String body, String token) throws Exception {
        String response = mvc.perform(post("/api/" + recurso).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.senha").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
    }

    private void verificar(String recurso, long id, String body, String campo, String valor, String token) throws Exception {
        String url = "/api/" + recurso + "/" + id;
        mvc.perform(put(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$." + campo).value(valor));
        mvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
            .andExpect(jsonPath("$." + campo).value(valor));
        mvc.perform(get("/api/" + recurso).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(delete(url).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(put(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization", token)).andExpect(status().isNotFound());
    }
}
