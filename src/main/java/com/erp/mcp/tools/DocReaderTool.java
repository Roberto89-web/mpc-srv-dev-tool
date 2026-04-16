package com.erp.mcp.tools;

import com.erp.mcp.service.MarkdownService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Tool MCP per leggere la documentazione del framework ERP.
 *
 * Espone due funzioni al modello AI:
 *  - readDoc:    legge la documentazione di un modulo specifico
 *  - searchDocs: cerca una keyword in tutta la documentazione
 */
@Service
public class DocReaderTool {

    private final MarkdownService markdownService;

    public DocReaderTool(MarkdownService markdownService) {
        this.markdownService = markdownService;
    }

    @Tool(description = """
            Legge la documentazione del framework ERP per un modulo specifico.
            
            Usare quando lo sviluppatore chiede:
            - Come funziona una parte del gestionale
            - Come si fa un tipo di personalizzazione
            - Quali sono le convenzioni da seguire in un modulo
            
            Parametro 'module': nome del modulo, es. 'vendite', 'magazzino', 'contabilita'.
            Passare 'index' per ottenere la lista di tutti i moduli disponibili.
            
            NON usare per cercare esempi di codice specifici: usare searchDocs invece.
            """)
    public String readDoc(
            @ToolParam(description = "Nome del modulo da leggere. Es: 'vendite', 'magazzino'. Usa 'index' per l'elenco completo.")
            String module) {
        return markdownService.readModule(module);
    }

    @Tool(description = """
            Cerca una keyword o concetto in tutta la documentazione del framework ERP.
            
            Usare quando lo sviluppatore chiede:
            - Come si fa una cosa specifica senza sapere in quale modulo si trova
            - Se esiste documentazione su un argomento
            - Tutte le occorrenze di un concetto nella documentazione
            
            Parametro 'query': parola chiave o frase da cercare.
            Es: 'campo custom', 'override', 'validazione', 'trigger db'.
            
            Restituisce i paragrafi rilevanti con il nome del file di provenienza.
            """)
    public String searchDocs(
            @ToolParam(description = "Keyword o frase da cercare nella documentazione. Es: 'campo custom', 'override metodo'.")
            String query) {
        return markdownService.searchDocs(query);
    }
}
