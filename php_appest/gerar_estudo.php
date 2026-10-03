<?php
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/estudo_formato.php';
require_once __DIR__ . '/orientacao_turma.php';
require_once __DIR__ . '/perfis.php';
$aluno=exigirUsuarioLogado($conn);
if (normalizarPerfil($aluno['tipo_perfil'])!=='aluno') responderErro('Estudo exclusivo de alunos.',403);
set_time_limit(120);
$id = inteiroDoPost('id_capitulo');
$nivel = 'MEDIO';
if (!$id) responderErro('Capítulo inválido.');
$idTurma=turmaDoEstudo($conn,$aluno['id_usuario'],inteiroDoPost('id_turma'));
$lock = false;
try {
    $stmt=$conn->prepare('SELECT c.*, l.titulo AS livro, l.materia, l.fonte_url FROM capitulo_livro c JOIN livro_didatico l ON l.id_livro=c.id_livro WHERE c.id_capitulo=? AND c.revisado=1 AND l.ativo=1');
    $stmt->bind_param('i',$id); $stmt->execute();
    $cap=$stmt->get_result()->fetch_assoc();
    if (!$cap) responderErro('Capítulo não disponível para estudo.',404);
    $paginas=json_decode($cap['paginas_json'],true,512,JSON_THROW_ON_ERROR);
    // Nunca truncar o capítulo silenciosamente.
    if (!$paginas || strlen($cap['paginas_json'])>180000) responderErro('Capítulo sem texto ou muito extenso. Solicite a revisão/divisão do conteúdo.',422);
    $model=configuracao('GEMINI_MODEL');
    if (!$model || !preg_match('/^[a-zA-Z0-9.\-]+$/',$model)) responderErro('Configure GEMINI_MODEL no servidor.',503);
    $orientacao=orientacaoDaTurma($conn,$idTurma,$cap['materia']);
    $idPrompt=$orientacao ? (int)$orientacao['id_prompt'] : null;
    // Nova explicação, edição do capítulo ou orientação exigem um novo estudo.
    // O resultado personalizado é compartilhado somente pela turma destinatária.
    $versao=versaoEstudoCompleto($model,$cap['paginas_json'],$idPrompt);
    $lockName='estudo:'.hash('sha256',$id.'|'.$versao);
    $lockName=substr($lockName,0,64);
    $s=$conn->prepare('SELECT id_estudo,conteudo_json FROM estudo_gerado WHERE id_capitulo=? AND dificuldade=? AND versao_gerador=?');
    $s->bind_param('iss',$id,$nivel,$versao); $s->execute(); $cache=$s->get_result()->fetch_assoc();
    if (!$cache) {
        // Vários alunos podem pedir o mesmo capítulo ao mesmo tempo. Quem chega
        // depois aguarda a primeira geração e reutiliza o resultado validado.
        $inicioEspera=microtime(true);
        $s=$conn->prepare('SELECT GET_LOCK(?, 45) AS adquirido'); $s->bind_param('s',$lockName); $s->execute();
        $lock=(int)$s->get_result()->fetch_assoc()['adquirido']===1;
        if (!$lock) responderErro('Este estudo está sendo preparado. Tente novamente em instantes.',409);
        $s=$conn->prepare('SELECT id_estudo,conteudo_json FROM estudo_gerado WHERE id_capitulo=? AND dificuldade=? AND versao_gerador=?');
        $s->bind_param('iss',$id,$nivel,$versao); $s->execute(); $cache=$s->get_result()->fetch_assoc();
        if (!$cache && microtime(true)-$inicioEspera>5) throw new RuntimeException('A geração simultânea não produziu um estudo válido.');
    }
    if ($cache) { $idEstudo=(int)$cache['id_estudo']; $dados=json_decode($cache['conteudo_json'],true,512,JSON_THROW_ON_ERROR); }
    else {
        $key=configuracao('GEMINI_API_KEY');
        if (!$key) throw new RuntimeException('Chave da IA não configurada no servidor.');
        $fontesPreparadas=prepararFontesEstudo($paginas);
        $string=['type'=>'STRING'];
        $ref=['referencia'=>['type'=>'STRING','enum'=>array_keys($fontesPreparadas)]];
        $schema=['type'=>'OBJECT','properties'=>[
            'resumo'=>['type'=>'ARRAY','items'=>['type'=>'OBJECT','properties'=>array_merge(['titulo'=>$string,'texto'=>$string,'topicos'=>['type'=>'ARRAY','items'=>$string]],$ref),'required'=>['titulo','texto','topicos','referencia']]],
            'palavras_chave'=>['type'=>'ARRAY','items'=>$string],
            'questoes'=>['type'=>'ARRAY','items'=>['type'=>'OBJECT','properties'=>array_merge(['enunciado'=>$string,'explicacao'=>$string,'alternativas'=>['type'=>'ARRAY','items'=>['type'=>'OBJECT','properties'=>['texto'=>$string,'ehCorreta'=>['type'=>'BOOLEAN']],'required'=>['texto','ehCorreta']]]],$ref),'required'=>['enunciado','explicacao','alternativas','referencia']]]
        ],'required'=>['resumo','questoes','palavras_chave']];
        $minimo=minimoPalavrasExplicacao($paginas);
        $porBloco=max(45,(int)ceil($minimo/8)+15);
        $instrucao=instrucaoEstudoCompleto().' Para este capítulo, organize a explicação em 8 blocos. Escreva de '.$porBloco.' a '.($porBloco+40).' palavras no campo texto de cada bloco, além dos tópicos. O conjunto precisa de no mínimo '.$minimo.' palavras, mantendo cada parágrafo fundamentado no trecho escolhido. Confira esses limites antes de concluir o JSON; não envie uma síntese curta.';
        $entrada='Matéria: '.$cap['materia']."\nCapítulo selecionado: ".$cap['titulo']."\nTexto completo do capítulo em trechos:\n".json_encode($fontesPreparadas,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
        if ($orientacao) $entrada.="\n\nOrientação de foco do professor, válida apenas quando este capítulo sustentar o assunto: ".mb_substr($orientacao['prompt_final'],0,4000);
        $body=['systemInstruction'=>['parts'=>[['text'=>$instrucao]]],'contents'=>[['role'=>'user','parts'=>[['text'=>$entrada]]]],'generationConfig'=>['temperature'=>0.2,'maxOutputTokens'=>10000,'responseMimeType'=>'application/json','responseSchema'=>$schema]];
        $falha=null;
        $ultimaResposta=null;
        for ($tentativa=0; $tentativa<2; $tentativa++) {
            if ($tentativa>0) usleep(800000);
            if ($tentativa>0 && $ultimaResposta!==null) {
                // Corrige a resposta recebida com o motivo concreto da rejeição.
                // Mantém o capítulo original e as mesmas regras de fonte e qualidade.
                $body['contents'][]=['role'=>'model','parts'=>[['text'=>$ultimaResposta]]];
                $body['contents'][]=['role'=>'user','parts'=>[['text'=>'A validação detectou: '.$falha->getMessage().' Corrija e devolva o JSON completo. Desenvolva 8 blocos com pelo menos '.$porBloco.' palavras em cada texto, usando somente os trechos fornecidos. Preserve as questões válidas, as referências existentes e inclua tópicos e palavras-chave. Não preencha o tamanho com repetições.']]];
            }
            try {
                $ch=curl_init('https://generativelanguage.googleapis.com/v1beta/models/'.$model.':generateContent');
                curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_RETURNTRANSFER=>true,CURLOPT_CONNECTTIMEOUT=>10,CURLOPT_TIMEOUT=>45,CURLOPT_HTTPHEADER=>['Content-Type: application/json','x-goog-api-key: '.$key],CURLOPT_POSTFIELDS=>json_encode($body,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR)]);
                $raw=curl_exec($ch); $http=curl_getinfo($ch,CURLINFO_HTTP_CODE); $curlError=curl_error($ch); curl_close($ch);
                if ($http!==200 || !$raw) {
                    $provider=json_decode($raw ?: '{}',true);
                    $providerError=$provider['error']['status'] ?? '';
                    $providerMessage=mb_substr((string)($provider['error']['message'] ?? ''),0,300);
                    $falha=new RuntimeException('Provedor indisponível (HTTP '.$http.', '.($providerError ?: $curlError).'; '.$providerMessage.').');
                    if ($http>=400 && $http<500 && $http!==429) throw $falha;
                    throw $falha;
                }
                $res=json_decode($raw,true,512,JSON_THROW_ON_ERROR);
                $candidate=$res['candidates'][0] ?? [];
                if (($candidate['finishReason'] ?? '')!=='STOP') throw new RuntimeException('Geração incompleta.');
                $text=''; foreach ($candidate['content']['parts'] ?? [] as $part) if (empty($part['thought'])) $text.=$part['text'] ?? '';
                $ultimaResposta=$text;
                $dados=validarEstudoCompleto(resolverReferenciasEstudo(json_decode($text,true,512,JSON_THROW_ON_ERROR),$fontesPreparadas),$paginas);
                $falha=null;
                break;
            } catch (Throwable $e) {
                $falha=$e;
                if (isset($http) && $http>=400 && $http<500 && $http!==429) break;
            }
        }
        if ($falha) throw $falha;
        $json=json_encode($dados,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
        $turmaDestinataria=$idPrompt ? $idTurma : null;
        $s=$conn->prepare('INSERT INTO estudo_gerado (id_capitulo,dificuldade,versao_gerador,conteudo_json,id_turma_destinataria) VALUES (?,?,?,?,?)');
        $s->bind_param('isssi',$id,$nivel,$versao,$json,$turmaDestinataria); $s->execute();
        $idEstudo=(int)$conn->insert_id;
    }
    validarEstudoCompleto($dados,$paginas);
    $resposta=['status'=>'sucesso','id_estudo'=>$idEstudo,'id_turma'=>$idTurma,'dificuldade'=>$nivel,'estudo'=>$dados,'livro'=>$cap['livro'],'capitulo'=>$cap['titulo'],'fonte_url'=>$cap['fonte_url']];
} catch (Throwable $e) {
    error_log('Falha estudo: '.get_class($e).' — '.$e->getMessage());
    $mensagem=str_contains($e->getMessage(),'HTTP 0,')
        ? 'O servidor não conseguiu se conectar à IA. Verifique a conexão com a internet e tente novamente.'
        : 'Não foi possível gerar um estudo validado com este capítulo. Tente novamente em instantes.';
    $resposta=['status'=>'erro','mensagem'=>$mensagem];
    $codigo=503;
} finally {
    if ($lock) { $s=$conn->prepare('SELECT RELEASE_LOCK(?)'); $s->bind_param('s',$lockName); $s->execute(); }
}
responderJson($resposta,$codigo ?? 200);
