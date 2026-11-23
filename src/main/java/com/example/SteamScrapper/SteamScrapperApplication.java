package com.example.SteamScrapper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
public class SteamScrapperApplication {

    record JogoExtraido(String chave, String nome, String preco) {}
	public static void main(String[] args) {
		SpringApplication.run(SteamScrapperApplication.class, args);
        String url = "https://store.steampowered.com/wishlist/profiles/76561198120867657/?sort=discount";
        Map<String, JogoExtraido> jogos = new LinkedHashMap<>();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(true)
             )) {

            Page page = browser.newPage();

            // Monitora respostas JSON/XHR para você identificar a origem dos dados
            page.onResponse(response -> {
                String contentType = response.headers().getOrDefault("content-type", "");
                if (contentType.contains("application/json") && response.status() == 200) {
                    System.out.println("[JSON] " + response.url());
                }
            });

            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            page.waitForSelector("[data-rfd-draggable-id^=WishlistItem-], [data-rfd-drag-handle-draggable-id^=WishlistItem-], .PE-3oq-yIvg-",
                    new Page.WaitForSelectorOptions().setTimeout(15000));

            int semAvanco = 0;
            for (int i = 0; i < 40; i++) {
                int antes = jogos.size();
                coletarDoDom(page.content(), jogos);

                if (jogos.size() > antes) {
                    semAvanco = 0;
                    System.out.println("Passo " + i + " | Itens acumulados: " + jogos.size());
                } else {
                    semAvanco++;
                }

                if (semAvanco >= 5) {
                    break;
                }

                // Tenta rolar a janela e qualquer contêiner com overflow vertical
                page.evaluate("""
            () => {
                window.scrollBy(0, 1200);
                document.querySelectorAll('*').forEach(el => {
                    if (el.scrollHeight > el.clientHeight + 50) {
                        el.scrollTop += 1200;
                    }
                });
            }
            """);
                page.waitForTimeout(1000);
            }

            System.out.println("Itens acumulados: " + jogos.size());
            jogos.values().forEach(j ->
                    System.out.println(j.chave() + " | " + j.nome() + " | " + j.preco())
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void coletarDoDom(String html, Map<String, JogoExtraido> destino) {
        Document doc = Jsoup.parse(html);
        Elements itens = doc.select("[data-rfd-draggable-id^=WishlistItem-], .PE-3oq-yIvg-");

        for (Element item : itens) {
            String dragId = item.attr("data-rfd-draggable-id");
            if (dragId.isBlank()) {
                Element handle = item.selectFirst("[data-rfd-drag-handle-draggable-id]");
                if (handle != null) {
                    dragId = handle.attr("data-rfd-drag-handle-draggable-id");
                }
            }

            Element linkNome = item.select("a[href*=/app/]")
                    .stream()
                    .filter(el -> !el.text().isBlank())
                    .findFirst()
                    .orElse(null);

            String nome = (linkNome != null) ? linkNome.text().trim() : "Nome não encontrado";
            String chave = !dragId.isBlank() ? dragId : (linkNome != null ? linkNome.attr("href") : nome);

            Element precoEl = item.selectFirst("[aria-label*=R$]");
            String preco = (precoEl != null) ? precoEl.attr("aria-label").trim() : "Preço não encontrado";

            destino.putIfAbsent(chave, new JogoExtraido(chave, nome, preco));
        }
    }
}
