package com.github.carloseduardofsousa.tcc

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Sessao.estaLogado(this)) { voltarLogin(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p=(24*resources.displayMetrics.density).toInt(); setPadding(p,p,p,p)
        }
        root.addView(TextView(this).apply {
            text="Academia de Gênios"; textSize=29f; setTextColor(Color.parseColor("#1A237E")); typeface=Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text="Olá, ${Sessao.nome(this@MenuActivity)}\nO que você deseja fazer?"; textSize=18f
            setTextColor(Color.parseColor("#4D5066")); setPadding(0,12,0,26)
        })
        when (Sessao.tipoPerfil(this)) {
            "aluno" -> {
                opcao(root,"📚","Estudar","Escolha uma matéria e um capítulo") { abrir(SelecionarMateriaActivity::class.java) }
                opcao(root,"📊","Meu desempenho","Veja seus acertos por matéria e capítulo") { abrir(DesempenhoTopicosActivity::class.java) }
                opcao(root,"🏆","Ranking da minha série","Compare sua pontuação mensal") { abrirSelecao("RANKING_SERIE") }
            }
            "professor" -> {
                opcao(root,"📈","Gráficos das turmas","Compare dificuldades por matéria e capítulo") { abrirSelecao("GRAFICOS_TURMA") }
                opcao(root,"🏆","Ranking da escola","Selecione a série e veja a classificação") { abrirSelecao("RANKING_SERIE") }
                opcao(root,"🎯","Personalização de estudo","Escolha turma e aluno para orientar o estudo") { abrir(ProfessorActivity::class.java) }
            }
            "admin" -> opcao(root,"⚙️","Coordenação","Acesse as funções administrativas atuais") { abrir(CoordenacaoActivity::class.java) }
            else -> { Sessao.limpar(this); voltarLogin(); return }
        }
        root.addView(Button(this).apply { text="Sair"; setOnClickListener { Sessao.encerrar(this@MenuActivity); finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun opcao(root:LinearLayout, emoji:String, titulo:String, descricao:String, click:()->Unit) {
        root.addView(LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(22,22,22,22)
            background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=22f; setStroke(2,Color.parseColor("#E4E6F2")) }
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,16) }
            addView(TextView(this@MenuActivity).apply { text=emoji; textSize=30f; gravity=Gravity.CENTER },LinearLayout.LayoutParams(70,70))
            addView(LinearLayout(this@MenuActivity).apply {
                orientation=LinearLayout.VERTICAL
                addView(TextView(this@MenuActivity).apply { text=titulo; textSize=18f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#1A237E")) })
                addView(TextView(this@MenuActivity).apply { text=descricao; textSize=14f; setTextColor(Color.parseColor("#666680")); setPadding(0,5,0,0) })
            },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            setOnClickListener { click() }; isClickable=true; isFocusable=true
        })
    }
    private fun abrir(clazz:Class<*>) = startActivity(Intent(this,clazz).apply {
        putExtra("ID_USUARIO",Sessao.idUsuario(this@MenuActivity)); putExtra("NOME_USUARIO",Sessao.nome(this@MenuActivity)); putExtra("ID_TURMA",Sessao.idTurma(this@MenuActivity))
    })
    private fun abrirSelecao(modo:String)=startActivity(Intent(this,SelecionarRelatorioActivity::class.java).putExtra("MODO",modo))
    private fun voltarLogin() { startActivity(Intent(this,LoginActivity::class.java).apply { flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() }
}
