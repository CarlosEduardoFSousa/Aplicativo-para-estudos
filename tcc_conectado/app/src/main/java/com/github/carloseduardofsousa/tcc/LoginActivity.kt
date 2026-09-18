package com.github.carloseduardofsousa.tcc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var seletor: AutoCompleteTextView
    private lateinit var cadastro: Button
    private lateinit var status: TextView
    private val perfis = listOf("aluno", "professor", "admin")
    private val rotulos = listOf("Aluno", "Professor", "Coordenação")
    private var perfil = "aluno"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail    = findViewById(R.id.etEmail)
        etSenha    = findViewById(R.id.etSenha)
        btnEntrar  = findViewById(R.id.btnEntrar)
        progressBar = findViewById(R.id.progressBarLogin)
        seletor = findViewById(R.id.seletorPerfil)
        cadastro = findViewById(R.id.btnIrCadastroProfessor)
        status = findViewById(R.id.statusLogin)
        perfil = savedInstanceState?.getString("perfil")?.takeIf { it in perfis } ?: "aluno"
        seletor.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, rotulos))
        seletor.setText(rotulos[perfis.indexOf(perfil)], false)
        seletor.setOnItemClickListener { _, _, pos, _ ->
            perfil = perfis[pos]
            atualizarPerfil()
        }
        atualizarPerfil()

        btnEntrar.setOnClickListener { fazerLogin() }

        findViewById<Button>(R.id.btnIrCadastroProfessor).setOnClickListener {
            startActivity(Intent(this, CadastroProfessorActivity::class.java).putExtra("PERFIL_CADASTRO", perfil))
        }
    }

    private fun atualizarPerfil() {
        btnEntrar.text = if (perfil == "admin") "Entrar na coordenação" else "Entrar como $perfil"
        cadastro.visibility = if (perfil == "admin") View.GONE else View.VISIBLE
        cadastro.text = "Criar conta de $perfil"
        status.text = if (perfil == "admin") "Use a conta fornecida pela administração." else "Use o e-mail e a senha da sua conta de $perfil."
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("perfil", perfil)
    }

    private fun fazerLogin() {
        val email = etEmail.text.toString().trim()
        val senha = etSenha.text.toString()
        val perfilSolicitado = perfil

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show()
            return
        }

        btnEntrar.isEnabled = false
        seletor.isEnabled = false
        cadastro.isEnabled = false
        status.text = "Entrando…"
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resposta = withContext(Dispatchers.IO) {
                ApiClient.postComStatus("login.php", mapOf("email" to email, "senha" to senha, "tipo_perfil" to perfilSolicitado))
            }

                progressBar.visibility = View.GONE
                btnEntrar.isEnabled = true
                seletor.isEnabled = true
                cadastro.isEnabled = true

                if (resposta.corpo == null) {
                    status.text = "Não foi possível conectar à API. Execute o app pelo Android Studio para iniciar o servidor local."
                    return@launch
                }

                try {
                    val json = JSONObject(resposta.corpo)
                    if (resposta.sucesso && json.getString("status") == "sucesso") {
                        val idUsuario   = json.getInt("id_usuario")
                        val nomeUsuario = json.getString("nome")
                        val tipoPerfil  = json.getString("tipo_perfil")
                        val token       = json.optString("token")
                        val idTurma     = json.optInt("id_turma", 0)
                        if (tipoPerfil != perfilSolicitado || token.isBlank()) {
                            status.text = "O servidor devolveu um perfil diferente do selecionado."
                            return@launch
                        }

                        // O token é o que autentica as chamadas seguintes ao
                        // servidor; sem ele, o dashboard do professor não abre.
                        Sessao.salvar(this@LoginActivity, token, idUsuario, nomeUsuario, tipoPerfil, idTurma)

                        val destino = MenuActivity::class.java

                        val intent = Intent(this@LoginActivity, destino)
                        intent.putExtra("ID_USUARIO",  idUsuario)
                        intent.putExtra("NOME_USUARIO", nomeUsuario)
                        intent.putExtra("TIPO_PERFIL",  tipoPerfil)
                        intent.putExtra("ID_TURMA",     idTurma)
                        startActivity(intent)
                        finish()
                    } else {
                        status.text = json.optString("mensagem", "Login inválido.")
                    }
                } catch (e: Exception) {
                    status.text = "Resposta inesperada do servidor."
                }
        }
    }
}
