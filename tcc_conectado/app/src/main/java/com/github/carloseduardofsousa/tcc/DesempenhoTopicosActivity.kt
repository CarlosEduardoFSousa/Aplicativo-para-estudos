package com.github.carloseduardofsousa.tcc

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DesempenhoTopicosActivity : AppCompatActivity() {
    private lateinit var status:TextView; private lateinit var conteudo:LinearLayout; private lateinit var progresso:ProgressBar
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val professor=Sessao.ehProfessor(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
        root.addView(TextView(this).apply{text=if(professor)"Dificuldades da turma" else "Meu desempenho";textSize=27f;setTextColor(Color.parseColor("#1A237E"))})
        root.addView(TextView(this).apply{text=if(professor)intent.getStringExtra("NOME_TURMA").orEmpty() else Sessao.nome(this@DesempenhoTopicosActivity);textSize=17f})
        progresso=ProgressBar(this);root.addView(progresso)
        status=TextView(this).apply{text="Carregando…";textSize=16f;setPadding(0,12,0,12)};root.addView(status)
        conteudo=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(ScrollView(this).apply{addView(conteudo)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);carregar()
    }
    private fun carregar()=lifecycleScope.launch {
        val params=mutableMapOf("token" to Sessao.token(this@DesempenhoTopicosActivity))
        if(Sessao.ehProfessor(this@DesempenhoTopicosActivity)){params["id_turma"]=intent.getIntExtra("ID_TURMA",0).toString();params["materia"]=intent.getStringExtra("MATERIA").orEmpty()}
        val r=withContext(Dispatchers.IO){ApiClient.postComStatus("desempenho_estudos.php",params)}
        progresso.visibility=View.GONE
        if(r.sessaoExpirada){Sessao.encerrar(this@DesempenhoTopicosActivity);return@launch}
        val j=runCatching{JSONObject(r.corpo.orEmpty())}.getOrNull()
        if(!r.sucesso||j?.optString("status")!="sucesso"){status.text=j?.optString("mensagem")?:"Não foi possível carregar o relatório.";return@launch}
        val arr=j.getJSONArray("topicos")
        if(arr.length()==0){status.text="Ainda não há respostas de estudos registradas para estes filtros.";return@launch}
        status.text="Quanto maior a barra, maior o percentual de acertos. Toque em uma barra para ver os detalhes."
        val grupos=(0 until arr.length()).map{arr.getJSONObject(it)}.groupBy{it.getString("materia")}
        grupos.forEach{(materia,itens)->
            conteudo.addView(TextView(this@DesempenhoTopicosActivity).apply{text=materia;textSize=21f;setTextColor(Color.parseColor("#1A237E"));setPadding(0,20,0,6)})
            val chart=BarChart(this@DesempenhoTopicosActivity).apply{layoutParams=LinearLayout.LayoutParams(-1,430);description.isEnabled=false;axisRight.isEnabled=false;axisLeft.axisMinimum=0f;axisLeft.axisMaximum=100f;legend.isEnabled=false;xAxis.position=XAxis.XAxisPosition.BOTTOM;xAxis.granularity=1f;xAxis.setDrawGridLines(false);setFitBars(true)}
            val labels=itens.map{it.getString("topico")};chart.xAxis.valueFormatter=IndexAxisValueFormatter(labels);chart.xAxis.labelRotationAngle=-25f
            val set=BarDataSet(itens.mapIndexed{i,x->BarEntry(i.toFloat(),x.getDouble("percentual").toFloat())},"Acertos").apply{color=Color.parseColor("#5C6BC0");valueTextSize=11f}
            chart.data=BarData(set).apply{barWidth=.6f};chart.setOnChartValueSelectedListener(object:com.github.mikephil.charting.listener.OnChartValueSelectedListener{override fun onNothingSelected(){};override fun onValueSelected(e:com.github.mikephil.charting.data.Entry?,h:com.github.mikephil.charting.highlight.Highlight?){val x=itens.getOrNull(e?.x?.toInt()?:-1)?:return;status.text="${x.getString("topico")}: ${x.getDouble("percentual")}% de acertos em ${x.getInt("respostas")} respostas."}});chart.invalidate();conteudo.addView(chart)
        }
    }
}
