# BudsIA V2 — Auditoria da primeira versão

A primeira implementação cumpriu o objetivo de provar que tradução local, reconhecimento de voz e modelos sherpa-onnx podiam funcionar no aparelho real. Os testes de campo também mostraram limitações importantes que motivaram uma reconstrução completa.

## Problemas observados na V1

### 1. Explosão de identidades A → H

**Sintoma real:** a mesma pessoa aparecia como Falante A, depois novos falantes eram criados até Falante H.

**Causa:** a V1 comparava um embedding por frase contra um centroide usando um limiar fixo. Frases curtas produziam embeddings instáveis. Quando ficavam abaixo do limiar, um novo perfil era criado.

**Problema adicional:** ao atingir o limite máximo de perfis, a V1 aceitava o melhor perfil mesmo se a similaridade fosse baixa. Isso explica rótulos como Falante H com 44–52%.

**V2:** esse caminho foi removido. A V2 usa diarização Pyannote antes da identidade de sessão, rejeita resultados ambíguos e nunca força uma identidade ao atingir o limite.

## 2. Similaridade baixa exibida como se fosse confiança

**Sintoma:** 44%, 48% ou 52% apareciam em verde junto de um falante nomeado.

**Causa:** similaridade de embedding e confiança de identidade eram tratadas como a mesma coisa.

**V2:** identidade possui estados separados:

- ESTÁVEL
- APRENDENDO
- INCERTO

Uma similaridade baixa não gera identidade estável.

## 3. Segmentos curtos criavam novos falantes

**Sintoma:** palavras ou frases muito curtas causavam troca de pessoa.

**Causa:** não havia duração mínima para cadastrar uma nova voz.

**V2:** trechos curtos podem ser transcritos, mas não podem criar perfil de falante. Cadastro temporário exige duração e confiança mínimas.

## 4. Idiomas falsos, como sv e hi-Latn

**Sintoma:** uma fala em português podia ser classificada como sueco ou outro idioma, levando a erro de modelo de tradução.

**Causa:** a decisão dependia demais de um único detector em trechos pequenos.

**V2:** a decisão é limitada a PT/EN e combina:

1. idioma acústico do Whisper;
2. identificação textual do ML Kit;
3. histórico recente do mesmo falante.

Se não houver evidência suficiente, o resultado é **idioma incerto** e nenhuma tradução é forçada.

## 5. AUTO traduzia idiomas inesperados para português

**Sintoma:** uma classificação errada como sv tentava executar sv → pt.

**Causa:** TranslationRouter da V1 enviava qualquer idioma diferente de PT/EN para português.

**V2:** AUTO_PT_EN aceita somente:

- PT → EN
- EN → PT

Todo outro idioma vira rota desconhecida.

## 6. Whisper Tiny produzia transcrições frágeis

**Sintomas observados:** frases truncadas, repetições como "iria iria iria" e palavras incorretas.

**Causa:** modelo Tiny priorizava velocidade e o pipeline aceitava texto sem avaliação de qualidade.

**V2:** pacote de qualidade usa Whisper Base multilíngue e uma camada de qualidade que rejeita:

- silêncio;
- trechos curtos demais;
- repetição anormal;
- texto pouco informativo.

## 7. Ausência de diarização real antes da identificação

**V1:** VAD → transcrição → embedding da frase.

**V2:** áudio contínuo → janela → Pyannote Segmentation → trechos por falante → Whisper Base → ERes2Net → registro de identidade de sessão.

A ordem foi invertida para evitar contaminar um embedding com mais de uma pessoa.

## 8. Sem controle do número esperado de pessoas

**V2:** permite Auto, 2, 3 ou 4 falantes. Para uma conversa conhecida de duas pessoas, o usuário pode fornecer essa informação ao diarizador.

## 9. Backlog de áudio não era observável

**V2:** a fila é limitada. Se o processamento não acompanhar o tempo real, o app contabiliza janelas perdidas em vez de deixar a latência crescer indefinidamente.

## 10. Histórico sem telemetria suficiente

O banco V2 armazena também:

- sessionId;
- motor utilizado;
- estabilidade do falante;
- similaridade;
- idioma e motivo da decisão;
- qualidade da transcrição;
- duração;
- status da tradução.

Isso permite comparar versões com dados objetivos.

# Stack V2

- Kotlin
- Jetpack Compose / Material 3
- Hilt
- Room 4
- DataStore
- Coroutines / Flow
- Android AudioRecord 16 kHz mono
- sherpa-onnx 1.13.8
- Pyannote Segmentation 3.0
- ERes2Net speaker embeddings
- Whisper Base multilingual
- ML Kit Language ID
- ML Kit Translation

# Regras de transparência V2

A aplicação nunca transforma ausência de evidência em certeza.

- voz ambígua → Falante ?
- idioma ambíguo → ?
- transcrição ruim → marcada/suspensa
- modelo ausente → aviso explícito
- intenção discursiva → hipótese, não leitura mental

# Preservação da V1

A primeira versão está preservada em:

`archive/v1-first-model`
