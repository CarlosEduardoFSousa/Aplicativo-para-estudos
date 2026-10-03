package com.github.carloseduardofsousa.tcc

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ── Modelo ───────────────────────────────────────────────────
data class MateriaItem(val nome: String, val emoji: String)

// ── Adapter do grid de matérias ──────────────────────────────
class MateriaAdapter(
    private val items: List<MateriaItem>,
    private val onClick: (MateriaItem) -> Unit
) : RecyclerView.Adapter<MateriaAdapter.VH>() {

    private var selectedPos = -1

    inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val emoji = TextView(ctx).apply {
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val label = TextView(ctx).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(4, 4, 4, 0)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = EstudoUi.dp(ctx, 80)
            isFocusable = true
            setPadding(12, 20, 12, 20)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }
            addView(emoji)
            addView(label)
        }
        return VH(card)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val isSelected = position == selectedPos

        (h.card.getChildAt(0) as TextView).text = item.emoji
        (h.card.getChildAt(1) as TextView).apply {
            text = item.nome
            setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#333333"))
        }
        h.card.background = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(if (isSelected) Color.parseColor("#5C6BC0") else Color.parseColor("#F0F0F5"))
        }
        h.card.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

            val prev = selectedPos
            selectedPos = pos
            if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onClick(items[pos])
        }
    }

    override fun getItemCount() = items.size
    fun selecionar(posicao: Int) {
        selectedPos = posicao
        if (posicao in items.indices) notifyItemChanged(posicao)
    }
}

// ── Activity ─────────────────────────────────────────────────
class SelecionarMateriaActivity : AppCompatActivity() {

    private var materiaSelecionada: MateriaItem? = null
    private lateinit var btnIniciar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCarregando: TextView
    private var falhou = false
    private var carregando = false
    private var materiaRestaurada: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Sessao.estaLogado(this)) { Sessao.encerrar(this); finish(); return }
        setContentView(R.layout.activity_selecionar_materia)
        EstudoUi.protegerBarras(findViewById(R.id.raizMaterias))

        // Liga as views ao XML pelo ID
        btnIniciar    = findViewById(R.id.btnIniciar)
        progressBar   = findViewById(R.id.progressBar)
        tvCarregando  = findViewById(R.id.tvCarregando)

        val rvMaterias = findViewById<RecyclerView>(R.id.rvMaterias)
        rvMaterias.layoutManager = GridLayoutManager(this, 2)
        materiaRestaurada = savedInstanceState?.getString("materia")

        btnIniciar.text = "Escolher frente"
        btnIniciar.setOnClickListener { if (falhou) carregarMaterias(rvMaterias) else abrirFrentes() }

        carregarMaterias(rvMaterias)
    }

    private fun carregarMaterias(lista: RecyclerView) {
        if (carregando) return
        carregando = true; falhou = false; btnIniciar.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvCarregando.apply { text = "Carregando matérias disponíveis…"; visibility = View.VISIBLE }
        lifecycleScope.launch {
            val resposta = withContext(Dispatchers.IO) {
                ApiClient.postComStatus("listar_materias.php", mapOf("token" to Sessao.token(this@SelecionarMateriaActivity)))
            }
            progressBar.visibility = View.GONE
            carregando = false
            if (resposta.sessaoExpirada) { Sessao.encerrar(this@SelecionarMateriaActivity); finish(); return@launch }
            val json = runCatching { JSONObject(resposta.corpo.orEmpty()) }.getOrNull()
            if (!resposta.sucesso || json?.optString("status") != "sucesso") {
                tvCarregando.text = resposta.mensagemErro("Não foi possível carregar as matérias.")
                falhou = true; btnIniciar.isEnabled = true; btnIniciar.alpha = 1f; btnIniciar.text = "Tentar novamente"
                return@launch
            }
            val array = json.getJSONArray("materias")
            val materias = (0 until array.length()).map { i ->
                val nome = array.getJSONObject(i).getString("nome")
                MateriaItem(nome, emojiDaMateria(nome))
            }
            tvCarregando.text = if (materias.isEmpty()) "Nenhuma matéria possui capítulos revisados ainda." else ""
            tvCarregando.visibility = if (materias.isEmpty()) View.VISIBLE else View.GONE
            lista.adapter = MateriaAdapter(materias) { item ->
                materiaSelecionada = item
                btnIniciar.isEnabled = true
                btnIniciar.alpha = 1f
            }
            btnIniciar.text = "Escolher frente"
            materias.firstOrNull { it.nome == materiaRestaurada }?.let { item ->
                materiaSelecionada = item
                (lista.adapter as MateriaAdapter).selecionar(materias.indexOf(item))
                btnIniciar.isEnabled = true; btnIniciar.alpha = 1f
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putString("materia", materiaSelecionada?.nome ?: materiaRestaurada)
    }

    private fun emojiDaMateria(nome: String): String = when (nome.lowercase()) {
        "matemática", "matematica" -> "📐"; "história", "historia" -> "📜"
        "geografia" -> "🌍"; "ciências", "ciencias" -> "🔬"; "português", "portugues" -> "📖"
        "inglês", "ingles" -> "🇺🇸"; "física", "fisica" -> "⚛️"; "química", "quimica" -> "🧪"
        "biologia" -> "🧬"; "filosofia" -> "🤔"; "sociologia" -> "👥"; "redação", "redacao" -> "✍️"
        else -> "📘"
    }

    private fun abrirFrentes() {
        val materia = materiaSelecionada ?: return
        startActivity(android.content.Intent(this, FrentesActivity::class.java).apply {
            this@SelecionarMateriaActivity.intent.extras?.let { putExtras(it) }
            putExtra("MATERIA", materia.nome)
        })
    }
}
