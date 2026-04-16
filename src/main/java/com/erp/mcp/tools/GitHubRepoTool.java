package com.erp.mcp.tools;

import com.erp.mcp.service.GitHubService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Tool MCP per accedere a repository GitHub pubbliche.
 *
 * Espone cinque funzioni al modello AI:
 *  - getRepoInfo:        panoramica della repository (linguaggi, descrizione, stars)
 *  - getRecentCommits:   elenca gli ultimi N commit con autore e messaggio
 *  - getCommitDiff:      mostra i file modificati e il diff di un commit specifico
 *  - searchRepoCode:     cerca una keyword nel codice sorgente della repository
 *  - getFileContent:     legge il contenuto di un file specifico
 *
 * Casi d'uso tipici:
 *  - "Mostrami come viene implementata la validazione nel progetto X su GitHub"
 *  - "Cerca esempi di utilizzo dell'annotazione @Override nella repo Y"
 *  - "Cosa è cambiato nell'ultimo commit di x/y?"
 */
@Service
public class GitHubRepoTool {

    private final GitHubService gitHubService;

    public GitHubRepoTool(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Tool(description = """
            Recupera le informazioni generali di una repository GitHub pubblica.
            
            Usare quando lo sviluppatore chiede:
            - Informazioni su un progetto open source di riferimento
            - Quale linguaggio usa una certa libreria/framework
            - Dettagli generali prima di esplorare il codice
            
            Parametri: owner (es. 'spring-projects') e repo (es. 'spring-framework').
            
            Restituisce: descrizione, linguaggi, stars, forks, branch default, data ultimo push.
            """)
    public String getRepoInfo(
            @ToolParam(description = "Proprietario della repository GitHub. Es: 'spring-projects', 'apache', 'google'.")
            String owner,
            @ToolParam(description = "Nome della repository. Es: 'spring-framework', 'kafka', 'guava'.")
            String repo) {
        return gitHubService.getRepoInfo(owner, repo);
    }

    @Tool(description = """
            Recupera i commit recenti di una repository GitHub pubblica.
            
            Usare quando lo sviluppatore chiede:
            - Cosa è cambiato di recente in un progetto di riferimento
            - La storia dei cambiamenti di una libreria/framework
            - Esempi di commit message per ispirarsi
            
            Parametri: owner, repo, branch (opzionale), maxCount (1-30, default 10).
            
            Restituisce: lista di commit con SHA, data, autore e prima riga del messaggio.
            Usa getCommitDiff per vedere i dettagli di un commit specifico.
            """)
    public String getRecentCommits(
            @ToolParam(description = "Proprietario della repository. Es: 'torvalds'.")
            String owner,
            @ToolParam(description = "Nome della repository. Es: 'linux'.")
            String repo,
            @ToolParam(description = "Branch da consultare. Es: 'main', 'master', 'develop'. Lascia vuoto per il branch di default.")
            String branch,
            @ToolParam(description = "Numero di commit da recuperare. Valore tra 1 e 30. Default consigliato: 10.")
            int maxCount) {
        return gitHubService.getRecentCommits(owner, repo, branch, maxCount);
    }

    @Tool(description = """
            Mostra i dettagli e il diff di un commit specifico di una repository GitHub pubblica.
            
            Usare quando lo sviluppatore chiede:
            - Quali file sono stati modificati in un certo commit
            - Come è stata implementata una feature specifica (vedere le modifiche)
            - Esempi concreti di come un progetto open source ha risolto un problema
            
            Parametri: owner, repo, sha (SHA del commit, anche abbreviato a 7 caratteri).
            
            Restituisce: messaggio completo, lista dei file modificati con patch diff (max 100 righe per file).
            Ottieni i SHA dalla funzione getRecentCommits.
            """)
    public String getCommitDiff(
            @ToolParam(description = "Proprietario della repository. Es: 'spring-projects'.")
            String owner,
            @ToolParam(description = "Nome della repository. Es: 'spring-boot'.")
            String repo,
            @ToolParam(description = "SHA del commit da analizzare. Può essere il full SHA o abbreviato (7 caratteri). Recuperalo con getRecentCommits.")
            String sha) {
        return gitHubService.getCommitDiff(owner, repo, sha);
    }

    @Tool(description = """
            Cerca una keyword o pattern nel codice sorgente di una repository GitHub pubblica.
            
            Usare quando lo sviluppatore chiede:
            - Esempi di utilizzo di una certa API, annotazione o pattern in un progetto reale
            - Come viene usata una specifica classe/metodo in un codebase open source
            - Se un certo pattern esiste nella codebase di riferimento
            
            Parametri: owner, repo, query (keyword o frase).
            
            Restituisce: lista di file che contengono la keyword con il loro path e URL.
            Usa getFileContent per leggere il contenuto di un file trovato.
            
            NOTA: richiede un github.token configurato per funzionare in modo affidabile
            (l'API di ricerca GitHub ha un rate limit molto basso per richieste non autenticate).
            """)
    public String searchRepoCode(
            @ToolParam(description = "Proprietario della repository. Es: 'hibernate'.")
            String owner,
            @ToolParam(description = "Nome della repository. Es: 'hibernate-orm'.")
            String repo,
            @ToolParam(description = "Keyword o pattern da cercare nel codice. Es: '@Transactional', 'implements Serializable', 'TODO'.")
            String query) {
        return gitHubService.searchRepoCode(owner, repo, query);
    }

    @Tool(description = """
            Legge il contenuto di un file specifico in una repository GitHub pubblica.
            
            Usare quando lo sviluppatore chiede:
            - Di vedere il codice di una classe/file specifico in un progetto di riferimento
            - Di leggere la configurazione di un progetto open source
            - Di analizzare come è strutturato un file in una codebase reale
            
            Parametri: owner, repo, filePath (percorso relativo nella repo), ref (branch/SHA, opzionale).
            
            Restituisce: contenuto testuale del file (max 300 righe, poi troncato).
            
            Esempio filePath: 'src/main/java/org/springframework/boot/SpringApplication.java'
            Ottieni i path dei file tramite searchRepoCode o da URL GitHub.
            """)
    public String getFileContent(
            @ToolParam(description = "Proprietario della repository. Es: 'spring-projects'.")
            String owner,
            @ToolParam(description = "Nome della repository. Es: 'spring-framework'.")
            String repo,
            @ToolParam(description = "Percorso del file nella repository. Es: 'src/main/java/org/springframework/context/annotation/Configuration.java'.")
            String filePath,
            @ToolParam(description = "Branch o SHA di riferimento. Es: 'main', 'v6.1.0'. Lascia vuoto per il branch di default.")
            String ref) {
        return gitHubService.getFileContent(owner, repo, filePath, ref);
    }
}
