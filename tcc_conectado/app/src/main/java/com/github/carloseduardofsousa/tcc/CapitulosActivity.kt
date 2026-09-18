package com.github.carloseduardofsousa.tcc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CapitulosActivity : AppCompatActivity() {
    private lateinit var lista: LinearLayout
    private lateinit var status: TextView
    private lateinit var progresso: ProgressBar
    private var estudo: String? = null
    private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p,p,p,p)
        }
        root.addView(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } })
        root.addView(TextView(this).apply { text = intent.getStringExtra("MATERIA") ?: "Capítulos"; textSize = 26f })
        status = TextView(this).apply { textSize = 17f }
        root.addView(status)
        progresso = ProgressBar(this)
        root.addView(progresso)
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        estudo = savedInstanceState?.getString("estudo")
        if (estudo != null) mostrarEstudo(JSONObject(estudo!!)) else carregar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("estudo", estudo)
    }

    private fun requisitar(endpoint: String, params: Map<String,String>, pronto: (JSONObject) -> Unit) {
        if (ocupado) return
        ocupado = true
        progresso.visibility = View.VISIBLE
        lifecycleScope.launch {
            val resposta = withContext(Dispatchers.IO) {
                ApiClient.postComStatus(endpoint, params + ("token" to Sessao.token(this@CapitulosActivity)), 115000)
            }
            ocupado = false
            progresso.visibility = View.GONE
            if (resposta.sessaoExpirada) {
                startActivity(Intent(this@CapitulosActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                return@launch
            }
            val json = runCatching { JSONObject(resposta.corpo ?: "") }.getOrNull()
            if (!resposta.sucesso || json?.optString("status") != "sucesso") {
                status.text = json?.optString("mensagem") ?: "Não foi possível conectar. Tente novamente."
                if (lista.childCount == 0) lista.addView(Button(this@CapitulosActivity).apply {
                    text = "Tentar novamente"; setOnClickListener { carregar() }
                })
            } else pronto(json)
        }
    }

    private fun carregar() {
        lista.removeAllViews()
        status.text = "Carregando capítulos…"
        requisitar("listar_capitulos.php", mapOf("materia" to (intent.getStringExtra("MATERIA") ?: ""))) { json ->
            val capitulos = json.getJSONArray("capitulos")
            status.text = if (capitulos.length() == 0) "Ainda não há capítulos revisados para esta matéria." else "Escolha o capítulo que deseja estudar"
            for (i in 0 until capitulos.length()) {
                val c = capitulos.getJSONObject(i)
                lista.addView(Button(this).apply {
                    text = "${c.getString("livro")}\n${c.getString("titulo")}"
                    isAllCaps = false
                    setOnClickListener {
                        if (!ocupado) {
                            status.text = "Preparando resumo e quiz… Isso pode levar até dois minutos."
                            intent.putExtra("ID_CAPITULO_SELECIONADO", c.getInt("id_capitulo"))
                            requisitar("gerar_estudo.php", mapOf("id_capitulo" to c.getInt("id_capitulo").toString(), "dificuldade" to (intent.getStringExtra("DIFICULDADE") ?: "MEDIO"))) {
                                estudo = it.toString()
                                mostrarEstudo(it)
                            }
                        }
                    }
                })
            }
        }
    }

    private fun mostrarEstudo(json: JSONObject) {
        progresso.visibility = View.GONE
        lista.removeAllViews()
        status.text = "${json.getString("livro")}\n${json.getString("capitulo")}"
        val dados = json.getJSONObject("estudo")
        val resumo = dados.getJSONArray("resumo")
        for (i in 0 until resumo.length()) {
            val p = resumo.getJSONObject(i)
            lista.addView(TextView(this).apply {
                text = "${p.getString("texto")}\nFonte: página ${p.getInt("pagina")} do PDF\n"
                textSize = 18f
                setTextIsSelectable(true)
            })
        }
        lista.addView(Button(this).apply {
            text = "Abrir livro original"
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(json.getString("fonte_url")))) }
                    .onFailure { Toast.makeText(this@CapitulosActivity,"Nenhum navegador disponível",Toast.LENGTH_SHORT).show() }
            }
        })
        lista.addView(Button(this).apply {
            text = "Iniciar quiz — 5 questões"
            setOnClickListener {
                startActivity(Intent(this@CapitulosActivity, MainActivity::class.java).apply {
                    this@CapitulosActivity.intent.extras?.let { putExtras(it) }
                    putExtra("QUESTOES_JSON", dados.getJSONArray("questoes").toString())
                    putExtra("ID_CAPITULO", intent.getIntExtra("ID_CAPITULO_SELECIONADO", 0))
                    putExtra("MATERIA", intent.getStringExtra("MATERIA"))
                })
            }
        })
        lista.addView(Button(this).apply { text = "Escolher outro capítulo"; setOnClickListener { estudo = null; carregar() } })
    }
}
