package com.erp.mcp.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Servizio per leggere e cercare nella documentazione Markdown del framework ERP.
 * I file .md devono essere organizzati in cartelle per modulo:
 *
 *   erp-docs/
 *   ├── index.md              ← panoramica di tutti i moduli
 *   ├── vendite/
 *   │   ├── overview.md
 *   │   └── personalizzazioni.md
 *   ├── magazzino/
 *   │   └── ...
 *   └── contabilita/
 *       └── ...
 */
@Service
public class MarkdownService {

    @Value("${erp.docs.base-path}")
    private String basePath;

    private final Parser parser = Parser.builder().build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    /**
     * Legge il contenuto di un modulo specifico.
     * Se module è "index", restituisce l'elenco di tutti i moduli disponibili.
     */
    public String readModule(String module) {
        if ("index".equalsIgnoreCase(module)) {
            return listAllModules();
        }

        Path modulePath = Paths.get(basePath, module);

        if (!Files.exists(modulePath)) {
            // Prova anche come file diretto
            Path directFile = Paths.get(basePath, module + ".md");
            if (Files.exists(directFile)) {
                return readFile(directFile);
            }
            return "Modulo '" + module + "' non trovato. Usa module='index' per vedere i moduli disponibili.";
        }

        // Legge tutti i .md nella cartella del modulo e li concatena
        StringBuilder result = new StringBuilder();
        result.append("# Documentazione modulo: ").append(module).append("\n\n");

        try (Stream<Path> files = Files.walk(modulePath)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .sorted()
                 .forEach(file -> {
                     result.append("---\n");
                     result.append("## File: ").append(file.getFileName()).append("\n\n");
                     result.append(readFile(file)).append("\n\n");
                 });
        } catch (IOException e) {
            System.err.println("[MCP-ERP] Errore lettura modulo " + module + ": " + e.getMessage());
            return "Errore nella lettura del modulo: " + e.getMessage();
        }

        return result.toString();
    }

    /**
     * Cerca una keyword in tutti i file .md della documentazione.
     * Restituisce i paragrafi rilevanti con il nome del file di provenienza.
     */
    public String searchDocs(String query) {
        String queryLower = query.toLowerCase();
        List<String> results = new ArrayList<>();

        try (Stream<Path> files = Files.walk(Paths.get(basePath))) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .forEach(file -> {
                     try {
                         String content = Files.readString(file);
                         if (content.toLowerCase().contains(queryLower)) {
                             // Estrae le righe rilevanti con contesto
                             String excerpt = extractRelevantExcerpt(content, queryLower);
                             String relativePath = Paths.get(basePath).relativize(file).toString();
                             results.add("📄 **" + relativePath + "**\n" + excerpt);
                         }
                     } catch (IOException e) {
                         System.err.println("[MCP-ERP] Impossibile leggere " + file + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("[MCP-ERP] Errore durante la ricerca: " + e.getMessage());
            return "Errore durante la ricerca: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "Nessun risultato trovato per: '" + query + "'";
        }

        return "Trovati " + results.size() + " file con risultati per '" + query + "':\n\n"
               + String.join("\n\n---\n\n", results);
    }

    /**
     * Elenca tutti i moduli disponibili leggendo l'index.md o le cartelle presenti.
     */
    private String listAllModules() {
        Path indexFile = Paths.get(basePath, "index.md");
        if (Files.exists(indexFile)) {
            return readFile(indexFile);
        }

        // Se non c'è index.md, genera la lista dalle cartelle
        StringBuilder sb = new StringBuilder("# Moduli disponibili\n\n");
        try (Stream<Path> entries = Files.list(Paths.get(basePath))) {
            entries.filter(Files::isDirectory)
                   .map(p -> p.getFileName().toString())
                   .sorted()
                   .forEach(name -> sb.append("- ").append(name).append("\n"));
        } catch (IOException e) {
            return "Errore nella lettura dei moduli: " + e.getMessage();
        }

        return sb.toString();
    }

    /**
     * Legge un file .md e lo converte in testo plain.
     */
    private String readFile(Path file) {
        try {
            String markdown = Files.readString(file);
            Node document = parser.parse(markdown);
            return renderer.render(document);
        } catch (IOException e) {
            System.err.println("[MCP-ERP] Errore lettura file " + file + ": " + e.getMessage());
            return "Errore nella lettura del file: " + e.getMessage();
        }
    }

    /**
     * Estrae le righe che contengono la keyword con qualche riga di contesto.
     */
    private String extractRelevantExcerpt(String content, String query) {
        String[] lines = content.split("\n");
        StringBuilder excerpt = new StringBuilder();
        int contextLines = 2;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(query)) {
                int start = Math.max(0, i - contextLines);
                int end = Math.min(lines.length - 1, i + contextLines);
                for (int j = start; j <= end; j++) {
                    excerpt.append(lines[j]).append("\n");
                }
                excerpt.append("...\n");
            }
        }

        return excerpt.length() > 0 ? excerpt.toString() : "(keyword trovata ma nessun contesto estratto)";
    }
}
