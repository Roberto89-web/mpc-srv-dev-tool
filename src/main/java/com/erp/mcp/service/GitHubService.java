package com.erp.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Servizio per interagire con l'API REST di GitHub.
 *
 * Permette di:
 *  - Recuperare i commit recenti di una repository pubblica
 *  - Ottenere il diff di un singolo commit (file modificati + patch)
 *  - Cercare file/codice all'interno di una repository
 *  - Leggere il contenuto di un file specifico
 *
 * Configurazione opzionale (application.yml):
 *   github.token: ghp_xxxx   # aumenta il rate-limit da 60 a 5000 req/ora
 */
@Service
public class GitHubService {

    private static final String GITHUB_API = "https://api.github.com";

    @Value("${github.token:}")
    private String githubToken;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Commit recenti
    // -------------------------------------------------------------------------

    /**
     * Restituisce gli ultimi N commit di una repository nel formato:
     *   SHA | DATA | AUTORE | MESSAGGIO
     *
     * @param owner     proprietario della repo (es. "spring-projects")
     * @param repo      nome della repo (es. "spring-framework")
     * @param branch    branch da consultare (es. "main", "master"). Null = default branch.
     * @param maxCount  numero massimo di commit da restituire (max 30)
     */
    public String getRecentCommits(String owner, String repo, String branch, int maxCount) {
        int perPage = Math.min(Math.max(maxCount, 1), 30);
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/commits?per_page=" + perPage;
        if (branch != null && !branch.isBlank()) {
            url += "&sha=" + branch;
        }

        try {
            JsonNode commits = fetchJson(url);
            if (!commits.isArray()) {
                return handleApiError(commits, "getRecentCommits");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Ultimi ").append(commits.size()).append(" commit — ")
              .append(owner).append("/").append(repo);
            if (branch != null && !branch.isBlank()) {
                sb.append(" (branch: ").append(branch).append(")");
            }
            sb.append("\n\n");

            for (JsonNode commit : commits) {
                String sha     = commit.path("sha").asText("").substring(0, Math.min(7, commit.path("sha").asText("").length()));
                String message = commit.path("commit").path("message").asText("").split("\n")[0]; // solo prima riga
                String date    = commit.path("commit").path("author").path("date").asText("");
                String author  = commit.path("commit").path("author").path("name").asText("");
                sb.append("- `").append(sha).append("` ")
                  .append(date, 0, Math.min(10, date.length())).append(" ")
                  .append("[").append(author).append("] ")
                  .append(message).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Errore nel recupero dei commit: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Dettaglio di un commit (diff)
    // -------------------------------------------------------------------------

    /**
     * Restituisce i dettagli di un singolo commit: messaggio, file modificati e patch diff.
     *
     * @param owner  proprietario della repo
     * @param repo   nome della repo
     * @param sha    SHA del commit (anche abbreviato a 7 caratteri)
     */
    public String getCommitDiff(String owner, String repo, String sha) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/commits/" + sha;

        try {
            JsonNode commit = fetchJson(url);
            if (commit.has("message") && !commit.has("commit")) {
                return handleApiError(commit, "getCommitDiff");
            }

            StringBuilder sb = new StringBuilder();

            String fullSha  = commit.path("sha").asText("");
            String message  = commit.path("commit").path("message").asText("");
            String date     = commit.path("commit").path("author").path("date").asText("");
            String author   = commit.path("commit").path("author").path("name").asText("");
            String htmlUrl  = commit.path("html_url").asText("");

            sb.append("## Commit ").append(fullSha, 0, Math.min(7, fullSha.length())).append("\n\n");
            sb.append("**Autore:** ").append(author).append("  \n");
            sb.append("**Data:** ").append(date).append("  \n");
            sb.append("**URL:** ").append(htmlUrl).append("  \n\n");
            sb.append("**Messaggio:**\n```\n").append(message).append("\n```\n\n");

            JsonNode files = commit.path("files");
            if (files.isArray() && !files.isEmpty()) {
                sb.append("### File modificati (").append(files.size()).append(")\n\n");
                for (JsonNode file : files) {
                    String filename = file.path("filename").asText();
                    String status   = file.path("status").asText();
                    int additions   = file.path("additions").asInt();
                    int deletions   = file.path("deletions").asInt();

                    sb.append("#### `").append(filename).append("` — ").append(status)
                      .append(" (+").append(additions).append(" / -").append(deletions).append(")\n");

                    String patch = file.path("patch").asText("");
                    if (!patch.isBlank()) {
                        // Limita la patch a 100 righe per non saturare il contesto
                        String[] patchLines = patch.split("\n");
                        int limit = Math.min(patchLines.length, 100);
                        sb.append("```diff\n");
                        for (int i = 0; i < limit; i++) {
                            sb.append(patchLines[i]).append("\n");
                        }
                        if (patchLines.length > 100) {
                            sb.append("... (").append(patchLines.length - 100).append(" righe omesse)\n");
                        }
                        sb.append("```\n\n");
                    }
                }
            }

            return sb.toString();

        } catch (Exception e) {
            return "Errore nel recupero del diff: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Ricerca di codice nella repository (GitHub Code Search)
    // -------------------------------------------------------------------------

    /**
     * Cerca occorrenze di una keyword nel codice sorgente di una repository pubblica.
     * Usa l'API GitHub Code Search.
     *
     * @param owner  proprietario della repo
     * @param repo   nome della repo
     * @param query  keyword o espressione da cercare
     */
    public String searchRepoCode(String owner, String repo, String query) {
        String encodedQuery = (query + " repo:" + owner + "/" + repo)
                .replace(" ", "+").replace(":", "%3A").replace("/", "%2F");
        String url = GITHUB_API + "/search/code?q=" + encodedQuery + "&per_page=10";

        try {
            JsonNode result = fetchJson(url);
            if (result.has("message")) {
                return handleApiError(result, "searchRepoCode");
            }

            int totalCount = result.path("total_count").asInt();
            JsonNode items = result.path("items");

            if (totalCount == 0 || !items.isArray() || items.isEmpty()) {
                return "Nessun risultato trovato per '" + query + "' in " + owner + "/" + repo;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Risultati ricerca '").append(query).append("' in ").append(owner).append("/").append(repo).append("\n\n");
            sb.append("Trovati circa ").append(totalCount).append(" risultati (mostro i primi ").append(items.size()).append("):\n\n");

            for (JsonNode item : items) {
                String name    = item.path("name").asText();
                String path    = item.path("path").asText();
                String htmlUrl = item.path("html_url").asText();
                sb.append("- **").append(path).append("** (`").append(name).append("`)\n");
                sb.append("  ").append(htmlUrl).append("\n");
            }

            sb.append("\nUsa `getFileContent` per leggere il contenuto di uno di questi file.\n");
            return sb.toString();

        } catch (Exception e) {
            return "Errore durante la ricerca: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Lettura di un file specifico
    // -------------------------------------------------------------------------

    /**
     * Legge il contenuto testuale di un file in una repository pubblica.
     *
     * @param owner    proprietario della repo
     * @param repo     nome della repo
     * @param filePath percorso del file all'interno della repo (es. "src/main/java/Foo.java")
     * @param ref      branch o SHA di riferimento (es. "main"). Null = default branch.
     */
    public String getFileContent(String owner, String repo, String filePath, String ref) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + filePath;
        if (ref != null && !ref.isBlank()) {
            url += "?ref=" + ref;
        }

        try {
            JsonNode node = fetchJson(url);
            if (node.has("message") && !node.has("content")) {
                return handleApiError(node, "getFileContent");
            }

            String encoding = node.path("encoding").asText("");
            String content  = node.path("content").asText("");

            if ("base64".equals(encoding)) {
                // Rimuove i newline inseriti da GitHub nel base64
                byte[] decoded = java.util.Base64.getDecoder().decode(content.replace("\n", ""));
                String text = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);

                // Limita a 300 righe per non saturare il contesto
                String[] lines = text.split("\n");
                int limit = Math.min(lines.length, 300);
                StringBuilder sb = new StringBuilder();
                sb.append("## File: ").append(filePath).append("\n");
                sb.append("Repository: ").append(owner).append("/").append(repo).append("\n\n");
                sb.append("```\n");
                for (int i = 0; i < limit; i++) {
                    sb.append(lines[i]).append("\n");
                }
                if (lines.length > 300) {
                    sb.append("\n... (").append(lines.length - 300).append(" righe omesse — file troncato)\n");
                }
                sb.append("```\n");
                return sb.toString();
            }

            return "Formato non supportato: " + encoding;

        } catch (Exception e) {
            return "Errore nella lettura del file: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Info repository
    // -------------------------------------------------------------------------

    /**
     * Restituisce le informazioni principali di una repository (descrizione, linguaggi, stars, ecc.)
     */
    public String getRepoInfo(String owner, String repo) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo;

        try {
            JsonNode node = fetchJson(url);
            if (node.has("message")) {
                return handleApiError(node, "getRepoInfo");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(owner).append("/").append(repo).append("\n\n");
            sb.append("**Descrizione:** ").append(node.path("description").asText("n/a")).append("\n");
            sb.append("**Linguaggio principale:** ").append(node.path("language").asText("n/a")).append("\n");
            sb.append("**Stars:** ").append(node.path("stargazers_count").asInt()).append("\n");
            sb.append("**Forks:** ").append(node.path("forks_count").asInt()).append("\n");
            sb.append("**Branch default:** ").append(node.path("default_branch").asText()).append("\n");
            sb.append("**Ultimo push:** ").append(node.path("pushed_at").asText()).append("\n");
            sb.append("**URL:** ").append(node.path("html_url").asText()).append("\n");

            // Recupera i linguaggi usati
            String langsUrl = GITHUB_API + "/repos/" + owner + "/" + repo + "/languages";
            JsonNode langs = fetchJson(langsUrl);
            if (langs.isObject() && !langs.isEmpty()) {
                List<String> langList = new ArrayList<>();
                langs.fieldNames().forEachRemaining(langList::add);
                sb.append("**Linguaggi:** ").append(String.join(", ", langList)).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Errore nel recupero delle info: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    private JsonNode fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();

        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private String handleApiError(JsonNode node, String operation) {
        String message = node.path("message").asText("Errore sconosciuto");
        if (message.contains("rate limit")) {
            return "Rate limit GitHub raggiunto. Configura `github.token` in application.yml per aumentare il limite a 5000 req/ora.";
        }
        if (message.contains("Not Found")) {
            return "Repository o risorsa non trovata. Verifica owner/repo/percorso.";
        }
        return "Errore GitHub API [" + operation + "]: " + message;
    }
}
