# MCP Server - DevSupport

Server MCP per assistere lo sviluppo con riferimenti a documentazioni e repo pubbliche.
Costruito con Spring Boot + Spring AI.

---

## Requisiti

- Java 21+
- Maven 3.9+

---

## Setup

### 1. Configura il percorso della documentazione

Modifica `src/main/resources/application.yml` e imposta il path
dove metterai i tuoi file `.md`:

```yaml
erp:
  docs:
    base-path: C:/Users/TuoNome/erp-docs   # Windows
    # base-path: /Users/TuoNome/erp-docs   # Mac
```

Oppure puoi usare la cartella `docs-example/` inclusa nel progetto come punto di partenza:

```yaml
erp:
  docs:
    base-path: ${user.dir}/docs-example
```

### 2. Configurazione

### Organizzazione Markdown

Organizza i file `.md` per modulo:

```
erp-docs/
├── index.md              ← lista di tutti i moduli
├── vendite/
│   ├── overview.md
│   └── personalizzazioni.md
├── magazzino/
│   └── personalizzazioni.md
└── contabilita/
    └── personalizzazioni.md
```


### Configurazione token (consigliata)

Senza token funziona per `getRecentCommits`, `getCommitDiff` e `getFileContent` (60 req/ora). Per `searchRepoCode` è praticamente necessario un token perché l'API di ricerca ha rate limit severissimi per le richieste non autenticate.

Genera un token su [github.com/settings/tokens](https://github.com/settings/tokens) con scope `public_repo` (read only è sufficiente) e passalo come variabile d'ambiente:

### 3. Build

```bash
mvn clean package -DskipTests
```

Genera il file: `target/mcp-erp-server-1.0.0.jar`

---

## Configurazione in Claude Desktop

Aggiungi in `claude_desktop_config.json`
(su Windows: `%APPDATA%\Claude\claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "erp-assistant": {
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/target/mcp-erp-server-1.0.0.jar"
      ]
    }
  }
}
```

## Configurazione in VSCode (Copilot)

Aggiungi in `.vscode/mcp.json` nella root del tuo progetto:

```json
{
  "servers": {
    "erp-assistant": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "C:/path/to/target/mcp-erp-server-1.0.0.jar"
      ]
    }
  }
}
```

---

## Tool disponibili

| Tool | Descrizione |
|------|-------------|
| `readDoc(module)` | Legge la documentazione di un modulo. Usa `"index"` per l'elenco completo. |
| `searchDocs(query)` | Cerca una keyword in tutta la documentazione. |
| `getRepoInfo` | panoramica della repository (linguaggi, descrizione, stars) |
| `getRecentCommits` | elenca gli ultimi N commit con autore e messaggio |
| `getCommitDiff` | mostra i file modificati e il diff di un commit specifico |
| `searchRepoCode` | cerca una keyword nel codice sorgente della repository |
| `getFileContent` | legge il contenuto di un file specifico |

---
## Run
```bash
GITHUB_TOKEN=ghp_xxxx java -jar target/mcp-erp-server.jar
```
---

## Aggiungere nuovi tool

1. Crea una nuova classe in `src/main/java/.../tools/`
2. Annotala con `@Service`
3. Aggiungi i metodi con `@Tool` e `@ToolParam`
4. Registrala in `McpToolsConfig.java`

---
## Screenshot esempio
![Screenshot1](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot1.png)
![Screenshot2](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot2.png)
![Screenshot3](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot3.png)
![Screenshot4](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot4.png)
![Screenshot5](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot5.png)
![Screenshot6](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot6.png)
![Screenshot7](https://github.com/Roberto89-web/mcp-erp-server/blob/413e9f5380ed97a07af6145e1ff3c190908544b3/images/Screenshot7.png)

---

## TODO

Ecco cosa manca:

---

### 🔐 Autenticazione / Sicurezza

**Attualmente non c'è nessuna protezione.** Chiunque conosca l'endpoint può usare il server MCP. Dipende da come lo esponi:

- **Solo locale (Claude Desktop / VSCode)** → trasporto `stdio`, nessun problema, il server non è raggiungibile dall'esterno per definizione.
- **Se passi a HTTP/SSE** (per condividerlo in rete o con un team) → ti servono:
    - Un **API key** da passare nell'header (soluzione minima)
    - Oppure **OAuth 2.0** se vuoi integrazione con identity provider aziendali
    - **HTTPS obbligatorio** (un reverse proxy Nginx/Caddy)

---

### ⚙️ Configurazione esternalizzata

Path e token GitHub sono in `application.yml`. Manca:

- Un **file di configurazione esterno** documentato (es. `~/.erp-mcp/config.yml`)
- Validazione all'avvio con messaggi chiari se mancano parametri obbligatori (`erp.docs.base-path` che non esiste, ad esempio)
- Eventuale **setup wizard** al primo avvio (anche solo un check che stampa su stderr cosa configurare)

---

### 🛡️ Robustezza

- **Rate limiting** se il modello chiama `getFileContent` in loop su file grandi, puoi esaurire il rate limit GitHub in pochi minuti. Un semplice throttle o cache in memoria aiuterebbe.
- **Timeout HTTP**: `HttpClient` non ha timeout configurati — una repo lenta blocca il thread in modo indefinito
- **Dimensione risposte**: ci sono i limiti (300 righe, 100 righe diff) ma non c'è un limite globale — un `readModule("index")` su una doc enorme potrebbe restituire token eccessivi

---

### 📝 Il minimo indispensabile

| Cosa | Priorità |
|---|---|
| Aggiornare a Spring AI release stabile | 🔴 Alta |
| Aggiungere timeout all'HttpClient | 🟡 Media |
| Validazione parametri all'avvio | 🟡 Media |
| Autenticazione (solo se esposto via HTTP) | 🟡 Media se HTTP, nulla se stdio |
| Cache risposte GitHub (TTL 5 min) | 🟢 Bassa |

