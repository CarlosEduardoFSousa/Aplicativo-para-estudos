package com.github.carloseduardofsousa.tcc

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class TurmaMenu(val id:Int,val nome:String,val serie:String)

class SelecionarRelatorioActivity : AppCompatActivity() {
    private lateinit var serie:Spinner; private lateinit var turma:Spinner; private lateinit var materia:Spinner
    private lateinit var status:TextView; private lateinit var continuar:Button
    private var turmas=emptyList<TurmaMenu>(); private var series=emptyList<String>(); private var materias=emptyList<String>()
    private val modo by lazy { intent.getStringExtra("MODO").orEmpty() }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; val p=(24*resources.displayMetrics.density).toInt();setPadding(p,p,p,p) }
        root.addView(TextView(this).apply { text=if(modo=="RANKING_SERIE") "Ranking por série" else "Desempenho da turma";textSize=26f })
        status=TextView(this).apply { text="Carregando opções…";textSize=16f;setPadding(0,12,0,16) };root.addView(status)
        serie=campo(root,"Série"); turma=campo(root,"Turma"); materia=campo(root,"Matéria")
        continuar=Button(this).apply { text="Continuar";isEnabled=false;setOnClickListener { abrir() } };root.addView(continuar)
        setContentView(ScrollView(this).apply { addView(root) })
        serie.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){ atualizarTurmas() } }
        carregar()
    }
    private fun campo(root:LinearLayout,titulo:String):Spinner {
        root.addView(TextView(this).apply { text=titulo;textSize=15f;setPadding(0,12,0,4) })
        return Spinner(this).also { root.addView(it,LinearLayout.LayoutParams(-1,60)) }
    }
    private fun carregar()=lifecycleScope.launch {
        val r=withContext(Dispatchers.IO){ApiClient.postComStatus("opcoes_menu.php",mapOf("token" to Sessao.token(this@SelecionarRelatorioActivity)))}
        if(r.sessaoExpirada){Sessao.encerrar(this@SelecionarRelatorioActivity);return@launch}
        val j=runCatching{JSONObject(r.corpo.orEmpty())}.getOrNull()
        if(!r.sucesso||j?.optString("status")!="sucesso"){status.text=j?.optString("mensagem")?:"Não foi possível carregar.";return@launch}
        val ts=j.getJSONArray("turmas");turmas=(0 until ts.length()).map{val x=ts.getJSONObject(it);TurmaMenu(x.getInt("id_turma"),x.getString("nome_turma"),x.optString("serie",x.getString("nome_turma")))}
        val ss=j.getJSONArray("series");series=(0 until ss.length()).map{ss.getString(it)}
        val ms=j.getJSONArray("materias");materias=(0 until ms.length()).map{ms.getString(it)}
        serie.adapter=ArrayAdapter(this@SelecionarRelatorioActivity,android.R.layout.simple_spinner_dropdown_item,series)
        materia.adapter=ArrayAdapter(this@SelecionarRelatorioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Todas as matérias")+materias)
        val ranking=modo=="RANKING_SERIE";turma.visibility=if(ranking)View.GONE else View.VISIBLE;materia.visibility=if(ranking)View.GONE else View.VISIBLE
        status.text=if(series.isEmpty())"Nenhuma série vinculada ao seu perfil." else "Selecione os filtros do relatório."
        continuar.isEnabled=series.isNotEmpty()&&(ranking||turmas.isNotEmpty())
    }
    private fun atualizarTurmas(){if(series.isEmpty())return;val filtradas=turmas.filter{it.serie==series[serie.selectedItemPosition]};turma.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,filtradas.map{it.nome});turma.tag=filtradas}
    @Suppress("UNCHECKED_CAST") private fun abrir(){
        val s=series.getOrNull(serie.selectedItemPosition)?:return
        if(modo=="RANKING_SERIE") startActivity(Intent(this,RankingActivity::class.java).putExtra("SERIE",s).putExtra("MES_REF",SimpleDateFormat("yyyy-MM",Locale.getDefault()).format(Date())))
        else { val lista=turma.tag as? List<TurmaMenu>?:return;val t=lista.getOrNull(turma.selectedItemPosition)?:return;startActivity(Intent(this,DesempenhoTopicosActivity::class.java).putExtra("ID_TURMA",t.id).putExtra("NOME_TURMA",t.nome).putExtra("MATERIA",materias.getOrNull(materia.selectedItemPosition-1).orEmpty())) }
    }
}
