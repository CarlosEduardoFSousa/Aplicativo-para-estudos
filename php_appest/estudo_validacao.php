<?php
// Sem dependências de HTTP/banco: usado também nos testes.
function validarEstudo($dados, $paginas) {
    if (!is_array($dados) || count($dados['resumo'] ?? []) < 1 || count($dados['questoes'] ?? []) !== 5) {
        throw new RuntimeException('Resumo ou quantidade de questões inválidos.');
    }
    $fontes = [];
    foreach ($paginas as $p) $fontes[$p['pagina']] = $p['texto'];
    $normalizar = function ($s) { return preg_replace('/\s+/u', ' ', trim($s)); };
    foreach (array_merge($dados['resumo'], $dados['questoes']) as $item) {
        $texto = $item['texto'] ?? $item['enunciado'] ?? null;
        $pagina = $item['pagina'] ?? null;
        $trecho = $item['trecho'] ?? null;
        if (!is_string($texto) || trim($texto)==='' || !is_int($pagina) || !isset($fontes[$pagina]) || !is_string($trecho) || strlen(trim($trecho))<20 || strpos($normalizar($fontes[$pagina]), $normalizar($trecho)) === false) {
            throw new RuntimeException('Referência não encontrada no capítulo.');
        }
    }
    $enunciados = [];
    foreach ($dados['questoes'] as $q) {
        if (!is_array($q['alternativas'] ?? null) || count($q['alternativas'])!==4 || !is_string($q['explicacao'] ?? null) || trim($q['explicacao'])==='') throw new RuntimeException('Questão incompleta.');
        $corretas=0; $textos=[];
        foreach ($q['alternativas'] as $a) {
            if (!is_string($a['texto'] ?? null) || trim($a['texto'])==='' || !is_bool($a['ehCorreta'] ?? null)) throw new RuntimeException('Alternativa inválida.');
            $textos[]=$normalizar($a['texto']);
            if ($a['ehCorreta']) $corretas++;
        }
        if ($corretas!==1 || count(array_unique($textos))!==4) throw new RuntimeException('Gabarito inválido.');
        $enunciados[]=$normalizar($q['enunciado']);
    }
    if (count(array_unique($enunciados))!==5) throw new RuntimeException('Questões repetidas.');
    return $dados;
}
