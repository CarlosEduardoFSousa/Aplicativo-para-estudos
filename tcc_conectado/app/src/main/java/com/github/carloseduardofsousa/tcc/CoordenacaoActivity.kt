package com.github.carloseduardofsousa.tcc

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CoordenacaoActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var convite: TextView
    private lateinit var emitir: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val p=(24 * resources.displayMetrics.density).toInt()
            setPadding(p,p,p,p)
        }
        root.addView(TextView(this).apply { text="Coordenação"; textSize=28f })
        root.addView(TextView(this).apply { text="Olá, ${Sessao.nome(this@CoordenacaoActivity)}"; textSize=18f })
        status=TextView(this).apply { text="Carregando…"; textSize=18f; setPadding(0,24,0,24) }
        root.addView(status)
        root.addView(Button(this).apply { text="Atualizar painel"; setOnClickListener { carregar() } })
        convite=TextView(this).apply { textSize=18f; setTextIsSelectable(true); text=savedInstanceState?.getString("convite") ?: "" }
        emitir=Button(this).apply {
            text="Gerar convite para professor"
            isEnabled=false
            setOnClickListener {
                isEnabled=false
                chamar("convite_professor") { json ->
                    convite.text="${json.getString("codigo")}\n${json.getString("mensagem")}"
                }
            }
        }
        root.addView(emitir)
        root.addView(convite)
        root.addView(Button(this).apply { text="Sair"; setOnClickListener { Sessao.encerrar(this@CoordenacaoActivity) } })
        setContentView(ScrollView(this).apply { addView(root) })
        carregar()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("convite",convite.text.toString())
    }
    private fun carregar() = chamar("painel") { json ->
        val c=json.getJSONObject("contas")
        status.text="${c.getInt("aluno")} alunos\n${c.getInt("professor")} professores\n${c.getInt("admin")} contas de coordenação\n\n${json.getInt("livros")} livros ativos\n${json.getInt("capitulos")} capítulos liberados"
    }
    private fun chamar(acao: String, pronto: (JSONObject)->Unit) {
        lifecycleScope.launch {
            val r=withContext(Dispatchers.IO) { ApiClient.postComStatus("admin.php",mapOf("token" to Sessao.token(this@CoordenacaoActivity),"acao" to acao)) }
            if (r.sessaoExpirada) { Sessao.encerrar(this@CoordenacaoActivity); return@launch }
            val json=runCatching { JSONObject(r.corpo ?: "") }.getOrNull()
            emitir.isEnabled=r.sucesso
            if (!r.sucesso || json?.optString("status")!="sucesso") status.text=json?.optString("mensagem") ?: "Não foi possível conectar. Tente atualizar."
            else pronto(json)
        }
    }
}
