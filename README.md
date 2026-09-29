# BudsIA V2

BudsIA é um aplicativo Android local-first para conversas, tradução bidirecional e análise linguística.

## Versão atual

**2.0.0-alpha**

A V2 é uma reconstrução completa do motor de conversa a partir dos testes reais da primeira versão.

## Pipeline principal

```text
AudioRecord 16 kHz
        ↓
janela de conversa
        ↓
Pyannote Segmentation 3.0
        ↓
segmentos por falante
        ↓
Whisper Base multilíngue
        ↓
ERes2Net
        ↓
registro estável A/B/C
        ↓
Whisper lang + ML Kit + histórico do falante
        ↓
PT → EN / EN → PT
        ↓
análise explicável + timeline
```

## Modos

### Conversa V2

Motor recomendado.

Requer download explícito do pacote:

- Whisper Base multilingual;
- Pyannote Segmentation 3.0;
- ERes2Net.

Depois do download, a inferência é local.

### Compatibilidade Android

Usa SpeechRecognizer on-device quando o Android expõe o recurso.

É mais leve, mas não possui separação real de pessoas.

## Tradução

Modos:

- Automático PT ↔ EN
- PT → EN
- EN → PT

O modo automático não inventa rotas para outros idiomas. Idioma incerto fica sem tradução.

## Identidade de falante

Os rótulos A/B/C são temporários por sessão.

Estados:

- ESTÁVEL
- APRENDENDO
- INCERTO

A V2 nunca força uma identidade quando a similaridade não é suficiente.

## Qualidade

Trechos silenciosos, muito curtos ou com repetição anormal podem ser rejeitados antes de contaminar a tradução e a identidade do falante.

## Privacidade

- microfone somente em sessão iniciada pelo usuário;
- áudio bruto não é salvo no histórico;
- perfis A/B/C ficam apenas na sessão atual;
- modelos são baixados explicitamente;
- transcrição pode ser mantida apenas em memória;
- nenhuma alegação de leitura de pensamentos ou intenção mental.

## Banco local

Room preserva o histórico das versões anteriores e adiciona telemetria V2.

## Build

Requisitos:

- JDK 17
- Android SDK 36
- Gradle 9.6

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

O GitHub Actions verifica o SHA-256 do runtime sherpa-onnx antes de compilar.

## Testes de regressão

Existem testes específicos para os erros descobertos na primeira versão:

- não criar falante com trecho curto;
- não forçar perfil após atingir limite;
- rejeitar identidade ambígua;
- manter mesma voz entre janelas;
- impedir rota sv → pt;
- usar consenso PT/EN;
- rejeitar silêncio e repetição anormal.

Consulte `docs/V2_AUDIT.md` para a análise completa.

## V1 arquivada

`archive/v1-first-model`
